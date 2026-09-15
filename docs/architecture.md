# 아키텍처

## 왜 이 구조인가

플레이어가 없어도 마을이 살아 있어야 한다. 순진한 해법은 마을 주변 청크를 강제 로딩하는 것인데, 이건 실패한다.

- 반경 5청크 = 마을당 121청크. 마을 10개면 1210청크가 상시 풀 틱 → 서버 붕괴
- 바닐라 티켓으로 강제 로딩된 청크에는 자연 몹 스폰이 오지 않는다. 스폰 후보 청크 집합이 플레이어 위치 기준으로 만들어지기 때문

따라서 강제 로딩이 아니라 **해상도를 낮춘 시뮬레이션(LOD)** 으로 간다.

> **정정 (26.2 실사):** 두 번째 이유는 절대적이지 않다. NeoForge의 `TicketController.forceChunk(..., boolean forceNaturalSpawning)`은 플레이어가 없어도 자연 스폰을 허용하는 플래그를 제공한다. 즉 "강제 로딩하면 스폰이 안 온다"는 **바닐라 티켓 한정**이다.
>
> 그래도 결론은 바뀌지 않는다. 강제 로딩을 포기하는 진짜 이유는 **첫 번째 줄의 비용**이며, 그건 플래그로 해결되지 않는다.

## 두 단계

| 티어 | 저장하는 것 | 실행 |
|---|---|---|
| **L0 실제** | 엔티티 + 데이터 | 바닐라 틱 |
| **L2 가상** | 데이터만 | 지연 정산 (`catchUp`) |

L1(코어 청크 강제 로딩)은 **기본적으로 존재하지 않는다.** 마을 데이터를 블록엔티티가 아니라 `SavedData`에 두기 때문에 마을 상태를 읽고 쓰는 데 청크가 전혀 필요 없다.

강제 로딩이 필요한 경우는 하나뿐이다: 플레이어가 호퍼·레드스톤으로 마을 창고에 자동 공급하는 연동. 이건 마을별 opt-in 토글(`forceLoadCore`)로 빼고, 전역 상한(`maxForcedChunks`, 기본 8) 안에서 LRU로 관리한다.

## LOD는 마을이 아니라 주민 단위다

강제 로딩을 포기하면 **부분 로딩**을 처리해야 한다. 마을이 5×5 청크인데 플레이어가 가장자리에 있으면 절반만 엔티티 틱 범위에 들어온다. 이때 마을 전체를 promote하면 언로드된 청크에 엔티티를 소환하게 된다.

그래서 티어 플래그를 `Settlement`가 아니라 개별 주민이 갖는다.

```java
// Settlement에는 tier 필드가 없다. 정산 시점만 갖는다.
class Settlement {
    long lastSimTick;
}

// 주민 개인이 자기 상태를 갖는다
enum ResidentState { VIRTUAL, MATERIALIZED }
```

마을 절반은 실제 엔티티, 절반은 가상인 상태가 정상이다. 정산은 마을 단위(`lastSimTick`)로 하되, `simStep()`은 `MATERIALIZED` 주민을 건너뛴다. 이중 계산을 막는 유일한 장치다.

## Promote / Demote 프로토콜

### Promote (VIRTUAL → MATERIALIZED)

한 틱에 끝나지 않는다. **상태 머신으로 만든다.** 3 게임일치 정산은 360스텝이고, 밀린 `BuildOp`는 수천 개일 수 있다.

```
IDLE ──▶ CATCHING_UP ──▶ REPLAYING ──▶ SPAWNING ──▶ DONE
```

| 단계 | 하는 일 | 종료 조건 |
|---|---|---|
| `CATCHING_UP` | `catchUpSliced()` 반복 호출 | 잔여 elapsed < STEP |
| `REPLAYING` | `pendingOps`를 틱당 `replayOpsPerTick`개 적용 | 큐 소진 |
| `SPAWNING` | 주민을 틱당 몇 명씩 스폰 | 범위 내 주민 소진 |

```
SPAWNING 한 명:
  if (!level.isPositionEntityTicking(r.coarsePos())) continue;   // 보류, 다음 순회에 재시도
  if (materializedCount >= maxMaterializedResidents) break;      // 상한
  spawn(r)                          데이터에서 상태 복원
  entity.setData(ModAttachments.RESIDENT_ID, r.id())
  r.state = MATERIALIZED
```

정산이 끝나기 전에 스폰하면 **아직 죽지 않은 주민이 먼저 나타났다가 사라진다.** 순서는 협상 대상이 아니다.

### MATERIALIZED 상한에 걸리면

`maxMaterializedResidents`(기본 60)는 성능 상한이자 **보이는 것과 보이지 않는 것을 가르는 선**이다. 그냥 자르면 플레이어 눈앞에서 마을 절반이 비어 보인다.

정책은 셋이다.

1. 플레이어와 **가까운 순서**로 스폰한다 (`coarsePos` 거리)
2. 상한을 넘겨 못 올라온 주민은 `VIRTUAL`로 남아 `simStep`이 계속 돌린다 — 존재는 유지된다
3. 상한 도달은 `/placitum info`와 로그에 표시한다. 조용히 사라지면 버그로 오인된다

인구 60명을 넘는 `CITY`가 나오면 상한을 올리는 게 아니라 **주민이 실내에 있을 때 스폰을 보류**하는 쪽을 먼저 본다. 집 안에 있는 주민은 보이지 않으므로 엔티티일 필요가 없다.

### Demote (MATERIALIZED → VIRTUAL)

```
1. writeBack(entity → resident)        체력, 장비, 작업 진행도, 좌표
2. entity.discard()                    RemovalReason는 저장되지 않는 값으로
3. r.state = VIRTUAL
4. settlement.lastSimTick = now        (마을의 마지막 주민이 내려갈 때만)
```

### 강제 demote — 데이터 유실 방지

정상 경로(플레이어가 멀어짐) 말고도 엔티티가 사라지는 경로가 있다. **전부 `writeBack`을 거쳐야 한다.** 하나라도 새면 원칙 1이 무너진다.

| 이벤트 | 처리 |
|---|---|
| `ChunkEvent.Unload` | 그 청크의 MATERIALIZED 주민 즉시 demote (지연 없음) |
| `ServerStoppingEvent` | 전 차원 전 마을 강제 demote 후 저장 |
| 차원 언로드 / 플레이어 없는 차원 | 위와 동일 |
| 엔티티 사망 | demote가 아니라 **사망 처리**. `Resident` 제거 + 연대기 |
| 엔티티가 원인 불명으로 소실 | 아래 참조 |

청크 언로드는 지연을 두면 안 된다. 언로드가 끝나면 엔티티에 접근할 수 없고, 그 시점의 체력·진행도는 영영 사라진다.

### 서버 시작 시 재동기화

크래시로 `MATERIALIZED` 상태가 저장된 채 죽으면, 다음 부팅에서 데이터는 "엔티티가 있다"고 믿는데 엔티티는 없거나(청크가 저장 전이었음) 둘로 늘어난다(엔티티는 저장됐는데 마을 데이터는 아님).

**부팅 시 무조건 전원을 `VIRTUAL`로 되돌린다.**

```java
// ServerStartedEvent
for (Settlement s : manager.all())
    s.residents().forEach(r -> r.state = VIRTUAL);   // 엔티티는 아직 로드조차 안 됐다
```

그 뒤 `EntityJoinLevelEvent`에서 `RESIDENT_ID` 부착물을 가진 엔티티가 올라오면 판정한다.

| 상황 | 처리 |
|---|---|
| 해당 `Resident`가 존재하고 `VIRTUAL` | 재바인딩 → `MATERIALIZED` |
| 해당 `Resident`가 존재하고 이미 `MATERIALIZED` | **중복.** 나중에 온 엔티티를 `discard()` |
| 해당 `Resident`가 없음 (마을 해체 등) | 부착물 제거 후 평범한 바닐라 주민으로 방생 |

마지막 줄이 중요하다. 고아 엔티티를 죽이지 않는다. 플레이어에게는 주민이 이유 없이 증발한 것으로 보인다.

### 히스테리시스

경계에서 플레이어가 왔다 갔다 하면 스폰/디스폰이 초당 몇 번씩 돈다. 반드시 비대칭 임계값과 지연을 둔다.

| 파라미터 | 기본값 |
|---|---|
| `promoteRadius` (R_in) | 96 블록 |
| `demoteRadius` (R_out) | 144 블록 |
| `demoteDelayTicks` | 200 (10초) |

멀티플레이에서는 **플레이어 한 명이라도** `promoteRadius` 안에 있으면 promote, **전원이** `demoteRadius` 밖으로 나가고 `demoteDelayTicks`가 지나야 demote다. 가장 가까운 플레이어 기준으로 판정하면 된다.

**전투 중에는 demote를 지연하지 않는다.** 플레이어가 도망쳐도 습격은 진행되어야 한다. 대신 L0 전투 중 demote가 발생하면 습격을 취소하지 말고 현재 상태를 그대로 L2 공식에 넘겨 결말을 낸다 (`docs/defense.md` 참조).

## replayWorldOps

밀린 블록 변경은 한 틱에 쏟아붓지 않는다. `replayOpsPerTick`(기본 4)씩 흘려보낸다.

플레이어 눈에는 "도착했더니 주민들이 열심히 짓고 있다"로 보인다. 버그가 아니라 의도된 연출이다.

## 정합성 정책

가상 시뮬이 "창고에 밀 400"이라고 하는데 실제 상자는 비어 있는 상황이 이런 시스템이 무너지는 전형적 방식이다. 정책은 하나다.

**창고를 진짜 상자로 만들지 않는다.**

커스텀 블록엔티티(`WarehouseBlockEntity`)가 `Settlement.stock` 맵을 보여주는 UI로만 동작한다. 물리 인벤토리는 존재하지 않는다. 플레이어가 꺼내면 맵에서 차감, 넣으면 증가. 불일치가 발생할 수 없다.

같은 논리를 모든 곳에 적용한다.

| 대상 | L0 | L2 |
|---|---|---|
| 창고 | 맵의 UI | 맵 |
| 밭 | 실제 작물 블록 | `plot.farmYield` 숫자 |
| 건설 | 실제 블록 배치 | `BuildJob.progress` 정수 |
| 사망 | 엔티티 사망 이벤트 → 데이터 반영 | 공식 결과 → 데이터 반영 |

밭은 promote 시 `farmYield` 숫자를 보고 작물 성장 단계를 역산해 블록에 반영한다.

**엔티티 쪽에서 데이터로 정보가 흐르는 유일한 경로는 demote와 사망 이벤트다.** 그 외에 엔티티가 데이터를 직접 수정하는 코드를 만들지 않는다.

## Mojang / NeoForge 접점 — 26.2 실사 완료

아래는 **26.2 디컴파일 소스와 NeoForge 26.2.0.88 소스에서 직접 확인한 값**이다. 참고값이 아니다.

| 용도 | 26.2 API |
|---|---|
| 마을 저장 | `net.minecraft.world.level.saveddata.SavedData` (dirty 플래그만 가진 추상 클래스) |
| 저장 타입 | `SavedDataType<T>` = record `(Identifier id, Supplier<T> constructor, Codec<T> codec, DataFixTypes dataFixType)` |
| 저장소 | `net.minecraft.world.level.storage.SavedDataStorage`, `serverLevel.getDataStorage()` |
| 저장소 조작 | `computeIfAbsent(type)` / `get(type)` / `set(type, data)` |
| 엔티티↔주민 연결 | `AttachmentType<UUID>` — `AttachmentType.builder(...)`, `.serialize(...)`, `.copyOnDeath()` |
| 부착물 접근 | `entity.getData/setData/hasData/removeData/getExistingDataOrNull` |
| 틱 훅 | `ServerTickEvent.Post` |
| 청크 티켓 | `RegisterTicketControllersEvent.register(controller)` → `TicketController` = record `(Identifier id, callback)` |
| 청크 강제 로딩 | `controller.forceChunk(level, owner, cx, cz, add, forceNaturalSpawning)` |
| 주민에 공격력 부여 | `EntityAttributeModificationEvent` |
| 바닐라 번식 차단 | `BabyEntitySpawnEvent` — `ICancellableEvent` 구현. 취소 가능 |
| 청크 언로드 훅 | `ChunkEvent.Unload` |
| 엔티티 재바인딩 | `EntityJoinLevelEvent` |
| 종료 시 강제 demote | `ServerStoppingEvent` |
| 엔티티 틱 범위 판정 | `serverLevel.isPositionEntityTicking(BlockPos)` |
| 임시 청크 접근 | `level.getChunk(x, z, ChunkStatus.FULL, true)` — `ChunkStatus`는 `world.level.chunk.status` |
| 엔티티 조회 | `serverLevel.getEntity(UUID)` |

### 1.21.x에서 바뀐 것 — 문서 전체에 영향

| 1.21.x | 26.2 |
|---|---|
| `ResourceLocation` | **`Identifier`** (`net.minecraft.resources.Identifier`) |
| `DimensionDataStorage` | **`SavedDataStorage`** (`net.minecraft.world.level.storage`) |
| `net.minecraft.world.entity.npc.Villager` | **`net.minecraft.world.entity.npc.villager.Villager`** |
| `VillagerProfession` (enum 성격) | **레지스트리 record.** 직업은 `ResourceKey<VillagerProfession>` |
| `EntityType.VILLAGER` | **`EntityTypes.VILLAGER`** — 엔티티 타입 상수가 `EntityTypes`로 분리 |
| `src -> src.hasPermission(2)` | **`Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)`** |
| `ResourceKey.location()` | **`identifier()`** |
| `ChunkPos.x` / `.z` (필드) | **record.** `x()` / `z()` |
| `AttachmentType.Builder.serialize(Codec)` | **`serialize(MapCodec)`** — `codec.fieldOf("...")` 필요 |

`Identifier` 개명은 878개 MC 소스 파일에 걸쳐 있다. 설계 문서의 `ResourceLocation`은 전부 갈았다.

### NeoForge가 패치한 것을 봐야 한다

`SavedDataType`은 바닐라에서 `DataFixTypes`를 요구하는데, 모드에는 맞는 값이 없다. NeoForge가 이것을 **nullable로 패치하고 3인자 생성자를 추가**해 뒀다.

```java
new SavedDataType<>(Identifier id, Supplier<T> constructor, Codec<T> codec)   // 모드용
```

교훈은 API 하나가 아니다. **원본 디컴파일 소스가 아니라 `build/moddev/artifacts/minecraft-patched-*-sources.jar`를 봐야 한다.** 둘의 시그니처가 다른 지점이 있고, 원본만 보면 컴파일되지 않는 코드를 쓰게 된다.

### 아이템 데이터 컴포넌트는 부트스트랩 때 바인딩되지 않는다

26.2에서 `Item`의 데이터 컴포넌트는 **데이터팩 리로드**(`ReloadableServerResources`) 시점에 바인딩된다. `Bootstrap.bootStrap()`만 돌린 환경에서 `new ItemStack(item)`을 만들면 이렇게 터진다.

```
NullPointerException: Components not bound yet
	at net.minecraft.core.Holder$Reference.components
```

설계에 영향을 준다. `Resident.offers`를 타입 있는 `MerchantOffers`로 두면 **왕복 테스트에서 그 필드를 덮을 수 없다.** 그래서 원래 설계대로 불투명한 `CompoundTag`로 되돌렸다 (`docs/data-model.md`). 검증할 수 없는 필드는 조용히 썩는다.

`Item` 자체를 레지스트리에서 꺼내 쓰는 것(`GearSet`, `stock` 키)은 문제없다. 막히는 것은 `ItemStack` 생성뿐이다.

### 그대로인 것

`Heightmap.Types.WORLD_SURFACE`, `StructureTemplate`, `Rotation`, `RandomSource.create(long)`, `Mth.nextInt(rng, min, max)`, `BlockPos.CODEC`, `BlockState.CODEC`, `Registry.byNameCodec()`, `MerchantOffers`, `Activity.PANIC` 브레인 패키지.

### Codec 16필드 상한 — 설계에 영향

26.2는 DataFixerUpper **10.0.21**을 쓴다. `Products$P16`이 최대이므로 `RecordCodecBuilder.group()`은 **16개 필드까지만** 받는다.

| record | 현재 필드 수 | 상태 |
|---|---|---|
| `Settlement` | 24 | **초과** |
| `Resident` | 20 | **초과** |

둘 다 서브레코드로 쪼개야 Codec을 쓸 수 있다. `docs/data-model.md` 참조.

## 성능 목표

설계가 맞는지 판단할 기준. 이 숫자를 못 지키면 최적화가 아니라 설계를 고쳐야 한다.

| 항목 | 목표 |
|---|---|
| 가상 주민 2000명 heartbeat | 1ms 미만 |
| 동시 MATERIALIZED 주민 | 60명 상한 |
| 강제 로딩 청크 | 8개 상한 (기본은 0) |
| 틱당 시뮬 예산 | 0.5ms, 초과 시 다음 틱으로 이월 |
