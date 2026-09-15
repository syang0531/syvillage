# 데이터 모델

모든 상태 객체는 **불변 record + Codec**이다. 1.21.5부터 `SavedData`가 Codec 기반으로 바뀌었으므로 record로 짜면 직렬화가 사실상 공짜다.

가변이 필요한 경로(시뮬레이션 한 스텝 동안의 누적 계산 등)는 `store` 패키지 안의 빌더나 mutable 미러에서만 처리하고, 스텝이 끝나면 불변 record로 되돌린다.

## Settlement

```java
public record Settlement(
    SettlementId identity,        // id, name, dimension, center, claimRadiusChunks

    ScaleTier scale,              // 축1: 규모
    int scaleHoldSteps,           // 승격/강등 히스테리시스 카운터
    List<Resident> residents,     // id 오름차순 정렬 유지
    Map<UUID, Plot> plots,        // BUILT 셀이 가리키는 실체
    PlotGrid grid,
    Map<Item, Integer> stock,     // 창고. 물리 인벤토리 없음
    List<BuildJob> buildQueue,    // 선두가 head
    List<BuildOp> pendingOps,     // 미적용 월드 변경

    DefenseState defense,         // alert, alertSince, wall, lightingScore, recentCasualties
    Chronicle chronicle,
    SimClock clock,               // lastSimTick, simStep

    Ruler ruler,                  // V2 훅. V1에서는 항상 Npc(촌장)
    UUID parentId,                // V2 훅. V1에서는 항상 null
    boolean forceLoadCore         // opt-in 강제 로딩
) {
    // 위임 접근자. s.id()가 s.identity().id()보다 훨씬 자주 불린다.
    public UUID id()        { return identity.id(); }
    public BlockPos center(){ return identity.center(); }
    public AlertState alert(){ return defense.alert(); }
    public long lastSimTick(){ return clock.lastSimTick(); }
    public long simStep()   { return clock.simStep(); }
}

public record SettlementId(
    UUID id,
    String name,
    ResourceKey<Level> dimension,
    BlockPos center,              // 종의 위치. 플롯 격자의 원점
    int claimRadiusChunks
) {}

public record DefenseState(
    AlertState alert,
    long alertSince,
    WallState wall,
    int lightingScore,            // 0~100. 위협 계산에 들어간다
    int[] recentCasualties        // 게임일별 전투 사망자 링버퍼 (safetyCap용)
) {}

public record SimClock(
    long lastSimTick,             // 마지막으로 완료된 스텝의 경계
    long simStep                  // 결정적 RNG 시드에 쓰인다
) {}
```

### 왜 쪼개는가 — 16필드 상한

26.2는 DataFixerUpper 10.0.21을 쓰고, `Products$P16`이 최대다. 따라서 `RecordCodecBuilder.group()`은 **16개 필드까지만** 받는다. 플랫하게 두면 `Settlement`는 24개, `Resident`는 20개로 **Codec을 아예 작성할 수 없다.**

쪼개는 김에 경계를 도메인에 맞춘다. `DefenseState`는 `docs/defense.md`가, `SimClock`은 `docs/simulation.md`가 통째로 소유한다. 왕복 테스트도 서브레코드 단위로 비교할 수 있어 새는 필드를 더 빨리 찾는다.

현재 15필드이므로 여유가 한 칸뿐이다. **새 필드는 플랫하게 붙이지 말고 해당 서브레코드에 넣는다.**

`bedCount()`, `foodProduction()`, `population()`, `defenseRating()`은 **필드가 아니라 파생 메서드**다. 저장하지 않는다. 반대로 `lightingScore`와 `recentCasualties`는 월드를 스캔해야만 알 수 있거나 시간 창을 갖는 값이므로 반드시 저장한다.

### 저장 포맷은 앞으로만 넓힌다

**이미 존재하는 포맷에 필드를 추가할 때는 반드시 `optionalFieldOf`에 기본값을 준다.** 이름을 바꾸는 것은 추가 + 삭제이므로 같은 규칙을 두 번 어긴다.

`SavedDataStorage`는 `resultOrPartial`로 읽는다. **코덱 오류가 로드를 멈추지 않는다.** DFU의 리스트 코덱은 읽지 못한 원소를 버리므로, `Resident`의 필드 하나가 틀리면 **주민 명부가 통째로 빈 마을이 정상처럼 로드된다.** 한 번 건드리면 다음 저장이 그 빈 상태를 진짜 파일 위에 덮어쓴다.

M0 테스트 중 실제로 일어났다. 필드 이름을 하나 바꿨더니 주민 3명이 이름·거래·역사와 함께 증발했고, 남은 것은 ERROR 한 줄뿐이었다.

> "다들 사라졌는데 이유를 모른다"는 이 모드가 끝내려는 실패 양식이다. **모드 자신이 그렇게 실패해서는 안 된다.**

방어는 둘이다.

1. 새 필드는 전부 `optionalFieldOf(..., 기본값)`. 그러면 옛 세이브가 그냥 읽힌다
2. `SettlementData`가 **부분 디코딩을 거부한다.** 부분값 없는 에러를 반환해 저장소가 `null`을 돌려주게 하고, 마을을 메모리에 올리지 않는다. 디스크 파일은 그대로 남아 코덱을 고치면 복구된다

읽을 수 없는 마을과 비어 있는 마을은 다르다. 같게 취급하는 순간 파일이 지워진다.

### 컬렉션은 순회 순서가 결정적이어야 한다

`HashMap`/`HashSet` 순회 순서는 JVM 구현과 삽입 이력에 의존한다. 시뮬레이션이 그 순서를 타면 원칙 3이 조용히 깨지고, `docs/testing.md`의 등가성 테스트에서 "가끔 다름"으로 나타난다.

| 컬렉션 | 규칙 |
|---|---|
| `List<Resident>` | `Resident.id` 오름차순 유지. 추가 시 삽입 정렬 |
| `Map<UUID, Plot>` | `TreeMap` 또는 순회 직전 키 정렬 |
| `Map<Item, Integer> stock` | 순회는 `Identifier` 문자열 정렬 기준. Codec은 `unboundedMap(ITEM.byNameCodec(), INT)` |
| `PlotGrid.cells` | `CellPos` 정렬 (gz, gx 순) |

`Deque`는 Codec이 없다. 저장은 `List`로 하고 큐 조작이 필요하면 `store` 패키지의 가변 미러에서만 `ArrayDeque`로 감싼다.

### SettlementMut

`SimModule.step()`이 받는 가변 미러다. 한 스텝 동안만 존재한다.

```java
// store 패키지 밖으로 나가지 않는다
public final class SettlementMut {
    public long lastSimTick, simStep;
    public int lightingScore, scaleHoldSteps;
    public ScaleTier scale;
    public AlertState alert;
    public final List<Resident> residents;      // 정렬 불변식 유지
    public final Object2IntMap<Item> stock;
    public final Deque<BuildJob> buildQueue;
    public final List<ChronicleEntry> newEntries;   // 스텝 중 발생한 사건

    public static SettlementMut of(Settlement s);
    public Settlement freeze();                     // 스텝 끝에 record로 복귀
}
```

레코드를 스텝마다 재생성하면 주민 수천 명 규모에서 할당이 폭증한다. 가변 미러는 **성능 타협이 아니라 할당 회피**이며, 대신 `store` 밖으로 새어나가지 않게 강제한다. `freeze()`가 유일한 출구다.

### ScaleTier (축1)

플레이어 개입 없이 **자동 성장**한다. 인구가 유일한 승격 조건이다.

```java
public enum ScaleTier {
    OUTPOST (1,  4,  3),   // 개척지 — 오두막만
    HAMLET  (5,  11, 5),   // 촌락  — 기본 생산 건물
    VILLAGE (12, 24, 9),   // 마을  — 전문 직업, 시장, 무기고
    TOWN    (25, 44, 13),  // 읍    — 병영, 전업 경비병
    CITY    (45, 999, 17); // 도시  — 전문 구역

    final int minPop, maxPop, gridSize;  // gridSize = 플롯 격자 한 변(셀 수)
}
```

강등도 가능하다. 인구가 하한 아래로 내려가면 한 단계 내려간다. 마을은 쇠퇴할 수 있어야 한다.

**단, 경계에서 떨리면 안 된다.** 인구 11↔12를 오가는 마을이 매번 `VILLAGE`↔`HAMLET`을 왕복하면 `gridSize`가 바뀌고 성벽이 철거·재건된다. 자재가 무한히 타고 마을이 영원히 공사 중이 된다.

promote/demote와 같은 해법을 쓴다. 비대칭 임계값 + 지연.

- 승격은 `minPop`, 강등은 `minPop - scaleDemoteMargin`(기본 2) 아래에서만
- 조건을 연속 `scaleHoldSteps ≥ scaleHoldRequired`(기본 360스텝 = 1 게임시간) 만족해야 발동
- 조건이 깨지면 카운터는 0으로 리셋

`scaleHoldSteps`를 `Settlement`에 저장하는 이유가 이것이다. 판정이 시간 창을 갖는 순간 상태가 된다.

### AlertState

`docs/defense.md` 참조.

```java
public enum AlertState { PEACE, ALERT, COMBAT, ROUT }
```

### Ruler — V2 훅

**V1에서는 아무 일도 하지 않는다.** 필드 자리만 예약한다. 나중에 뚫는 것과 처음부터 있는 것은 차이가 크다.

```java
public sealed interface Ruler {
    record Npc(UUID residentId) implements Ruler {}
    record Player(UUID profileId, UUID regentId) implements Ruler {}
}
```

## Resident

```java
public record Resident(
    UUID id,
    Lineage lineage,          // givenName, familyName, motherId, fatherId

    LifeStage stage,          // INFANT, CHILD, ADULT, ELDER
    int ageDays,              // 게임일

    Assignment assignment,    // job, homePlot, workPlot
    Vitals vitals,            // health, morale, hunger

    boolean militiaEligible,
    boolean zombified,        // 좀비 주민. 집계 제외, 치료 시 복귀
    GearSet gear,             // 착용 장비만. 잡템은 stock으로

    ResidentTask task,        // 현재 작업 + 진행도
    BlockPos coarsePos,       // L2에서는 "어느 건물" 수준이면 충분
    ResidentState state,      // VIRTUAL | MATERIALIZED

    CompoundTag vanillaState  // 원칙 1의 유일한 예외. 아래 참조
) {}

public record Lineage(
    String givenName,
    String familyName,        // 가문 단위로 상속된다
    UUID motherId,            // null 가능
    UUID fatherId
) {}

public record Assignment(
    ResourceKey<JobDef> job,
    UUID homePlot,
    UUID workPlot
) {}

public record Vitals(
    int health,               // 0~20
    int morale,               // 0~100
    int hunger                // 0~100
) {}
```

`INFANT`는 **엔티티로 스폰하지 않는다.** 레코드로만 존재한다. 바닐라 아기 주민이 밖으로 돌아다니다 죽는 것이 몰살의 흔한 원인이므로 아예 제거한다. `CHILD`부터 스폰하되 집 근처를 벗어나지 못하게 한다.

### JobDef

데이터팩에서 로드되는 레지스트리 객체.

```java
public record JobDef(
    Identifier id,
    ScaleTier minScale,           // 이 규모부터 해금
    Block workstation,            // POI 블록
    boolean militiaEligible,      // 농부 true, 사서 false
    boolean producesFood,
    int productionPerStep,
    Identifier output        // 생산물
) {}
```

V1의 직업은 여섯 개다. `farmer`, `woodcutter`, `builder`, `smith`, `scholar`, `none`. 여기에 `guard`(`TOWN`부터)를 더한다. 바닐라 직업 열세 개와의 매핑은 `docs/vanilla-interop.md`.

바닐라 직업을 그대로 옮기지 않는 이유는 `JobDef` 하나마다 생산 공식과 밸런스 곡선이 따라붙기 때문이다. **거래는 계속 바닐라 직업이 결정한다.** `JobDef`는 시뮬레이션 전용이다.

### vanillaState — 예외를 명시한다

promote는 **새 엔티티를 만든다.** 여기서 옮기지 않은 것은 왕복마다 전부 파괴된다.

거래 목록이 눈에 띄는 예지만, 실제로 물린 것은 `VillagerData`(직업·타입·레벨)였다. 복원하지 않으면 농부가 무직으로 돌아오고, demote가 직업을 다시 읽으므로 **플레이어가 떠났다 올 때마다 마을이 농부를 하나씩 잃는다.** 게임 안에서만 드러났다.

이것들을 `Resident`의 정규 필드로 **분해해서** 옮기면 원칙 1이 `MerchantOffer` 전체(아이템, 가격, 사용 횟수, 수요, 경험치)와 `VillagerData`에까지 적용되어야 해서 범위가 폭발한다.

타협은 **불투명한 태그 하나**다.

26.2에 `MerchantOffers.CODEC`이 있으니 타입 있는 필드로 둘 수도 있다. 그런데 그러면 **왕복 테스트가 이 필드를 덮을 수 없다.** 26.2는 아이템 데이터 컴포넌트를 데이터팩 리로드 때 바인딩하므로 단위 테스트에서 `ItemStack`을, 따라서 `MerchantOffer`를 만들 수 없다 (`docs/architecture.md`).

검증할 수 없는 필드는 조용히 썩는다. 불투명한 blob이 설계 의도에도 맞고 테스트도 된다.

- 시뮬레이션은 이 필드를 절대 읽지 않는다. 우리에게는 불투명한 blob이다
- demote 시 `MerchantOffers.CODEC` / `VillagerData.CODEC`으로 인코딩해 넣고, promote 시 디코딩해 되돌리는 것이 전부다
- 인코딩·디코딩 지점은 `lifecycle/Lifecycle.java`의 `writeVanillaState` / `applyVanillaState` 둘뿐이다
- 이것이 원칙 1의 유일한 예외이며, 필드 주석에 그 사실을 적는다

예외를 늘리지 않기 위해 예외임을 명시한다. 자세한 배경은 `docs/vanilla-interop.md`.

## PlotGrid

**청크에 정렬하지 않는다.** 마을 중심 기준 8블록 격자다. 청크 경계에 걸리는 공간이 버려지지 않고, 마을이 청크 모양으로 자라지 않는다.

```java
public record PlotGrid(
    BlockPos origin,          // = settlement.center
    int size,                 // ScaleTier.gridSize, 한 변 셀 수 (홀수)
    Map<CellPos, CellState> cells
) {}

public record CellPos(int gx, int gz) {}   // origin 기준 상대 좌표

public enum CellState {
    FREE,       // 평탄하고 비어 있음
    ROAD,       // 도로. 건물은 반드시 ROAD 인접 셀에만
    RESERVED,   // 건설 큐에 잡힘
    BUILT,      // 건물 있음 (plotId 연결)
    BLOCKED     // 경사/물/플레이어 건축물/동굴
}
```

**셀 상태는 반드시 데이터에 저장한다.** 매번 월드를 스캔하면 청크 로드가 필요해지고 원칙 2가 깨진다. 최초 1회 스캔 + 주기적 재검증만 한다.

1차 필터는 청크 풀 로드 없이 `Heightmap.Types.WORLD_SURFACE`만으로 한다. 셀당 8×8 = 64개 높이값의 표준편차가 임계값을 넘으면 즉시 `BLOCKED`.

## Plot

```java
public record Plot(
    UUID id,
    CellPos anchor,
    int cellW, int cellH,      // 1×1, 2×1, 2×2
    Rotation rotation,         // 현관이 도로를 향하도록
    Identifier template, // .nbt 구조물
    PlotKind kind,             // HOUSE, FARM, WORKSHOP, ARMORY, WATCHTOWER, GRAVEYARD
    int bedCount,
    List<UUID> occupants
) {}
```

## BuildJob / BuildOp

```java
public record BuildOp(BlockPos pos, BlockState state) {}

public record BuildJob(
    UUID id,
    UUID plotId,
    BuildRecipe recipe,         // ops를 만드는 입력. ops 자체는 저장하지 않는다
    int progress,               // ops[0..progress) 까지 적용됨
    Map<Item, Integer> cost,
    BuildStage stage
) {}

public enum BuildStage { PLANNED, RESERVED, QUEUED, EXECUTING, WAITING_MATERIALS, COMPLETE }
```

**`progress` 정수 하나가 L0와 L2를 잇는다.** L2에서는 스텝마다 숫자가 오르고, L0에서는 건축가가 블록을 놓을 때마다 오른다. promote 시 replay는 `ops[0..progress)`를 월드에 적용하는 것뿐이다.

### ops는 저장하지 않고 재생성한다

집 한 채가 대략 1500~3000 op다. `BlockState`를 op마다 직렬화하면 마을 하나의 세이브가 수 MB로 불어나고, `SavedData`는 마을 단위로 통째로 다시 쓰므로 그 비용을 매번 낸다.

`ops`는 **순수 함수의 출력**이므로 저장할 필요가 없다.

```java
List<BuildOp> ops = BuildPlanner.expand(job.recipe(), heightSample);
```

```java
public record BuildRecipe(
    Identifier template,   // 또는 절차 생성기 id (밭, 성벽 세그먼트)
    BlockPos anchor,
    Rotation rotation,
    Identifier palette,    // 바이옴 치환 맵
    int[] groundProfile          // QUEUE 시점에 확정한 지면 높이. 지형 정리 op의 입력
) {}
```

`groundProfile`이 핵심이다. 지형은 나중에 변할 수 있으므로 **QUEUE 단계에서 샘플링한 높이값을 박제**해 둔다. 그래야 같은 recipe가 몇 주 뒤 promote 때도 같은 op 목록으로 전개된다. 이것이 없으면 `ops`는 순수 함수가 아니게 되고 replay가 어긋난다.

| 저장 | 재생성 |
|---|---|
| `recipe`, `progress`, `cost`, `stage` | `ops` 리스트 전체 |

`pendingOps`(플롯 밖 단발 변경 — 횃불, 무덤, 성벽 철거)는 개수가 적으므로 그대로 저장한다.

`ops`는 반드시 **Y 오름차순**으로 정렬한다. 건축가가 이미 쌓인 부분 위에 설 수 있고, 시각적으로도 자연스럽다.

지형 정리(기초 채우기, 나무 제거, 평탄화)도 전부 `BuildOp`로 만들어 `ops`에 포함시킨다. 그러면 EXECUTE 단계는 리스트를 순회하는 것 외에 아무 판단도 하지 않고, 예외 처리가 전부 PLAN 단계에 모인다.

## WallState

```java
public record WallState(
    WallTier tier,              // NONE, FENCE, PALISADE, STONE, RAMPART
    List<BlockPos> ring,        // 성벽 경로
    List<GateNode> gates,       // 길찾기에 등록되어야 한다
    boolean intact
) {}

public record GateNode(BlockPos pos, Direction facing, boolean open) {}
```

`gates`를 길찾기 데이터에 등록하지 않으면 닫힌 철문 앞에서 농부가 영원히 서 있게 된다. 반드시 처리한다.

## Chronicle

```java
public record ChronicleEntry(
    long gameTime,
    EntryType type,        // BIRTH, DEATH, BUILD, RAID_REPELLED, RAID_LOST,
                           // SCALE_UP, SCALE_DOWN, FAMINE, ZOMBIFIED, CURED, IMMIGRATION
    String subject,        // 주민 전체 이름 또는 건물 이름
    String detail          // 사망 원인 등
) {}

public record Chronicle(List<ChronicleEntry> entries) {
    static final int MAX = 200;   // 초과분은 오래된 것부터 버린다
}
```

## SavedData 분할

**마을 전체를 SavedData 하나에 넣지 않는다.** 더티 플래그가 파일 단위라 마을 하나만 바뀌어도 전체를 다시 쓴다.

```
placitum_index        마을 ID, 이름, 중심 좌표, 차원만
placitum_village_<id> 마을 하나의 전체 상태
```

`SettlementManager`가 인덱스를 메모리에 유지하고, 개별 마을은 필요할 때 로드한다. `setDirty()`는 실제로 바뀐 마을에만 호출한다.

## 엔티티 바인딩은 저장하지 않는다

`Resident`에 `entityId` 필드를 두고 싶은 유혹이 생기는데, 두면 안 된다. 엔티티 UUID는 세션마다 바뀔 수 있고, 저장하면 원칙 1이 뒤집혀 데이터가 엔티티를 참조하게 된다.

대신 `SettlementManager`가 **런타임 전용 양방향 맵**을 들고 있는다.

```java
// 저장되지 않는다. 서버 시작 시 비어 있다.
Map<UUID, UUID> residentToEntity;   // residentId -> entityUuid
```

엔티티 → 주민 방향은 `AttachmentType<UUID> RESIDENT_ID` 하나로 충분하다. 반대 방향이 필요한 곳은 징집(`docs/defense.md`) 정도뿐이다.

## Chronicle에 게임 수치를 의존시키지 않는다

`Chronicle`은 `MAX = 200`에서 오래된 것부터 버린다. **손실 가능한 로그다.** 여기서 `safetyCap`을 세면 사건이 잦은 마을일수록 7일치 사망자가 조기에 잘려나가 **털릴수록 안전해 보이는** 역전이 생긴다.

전투 사망자는 별도 링버퍼로 센다.

```java
int[] recentCasualties;   // 길이 = safetyWindowDays. index = gameDay % length
```

게임일이 바뀔 때 다음 칸을 0으로 밀고, 사망 시 현재 칸을 올린다. 합이 곧 창 안의 사망자 수다. 고정 크기라 저장 비용도 없다.

일반화하면 이렇다. **연대기는 플레이어에게 보여주기 위한 것이고, 시뮬레이션 입력이 되어서는 안 된다.**

## 이름 생성

성(姓)이 가문 단위로 상속되면 애착이 생긴다. 이것만으로 체감이 크게 달라진다.

- 이름 풀은 데이터팩 JSON (`data/placitum/names/given.json`, `family.json`)
- 출생 시 성은 부계 상속, 이름은 풀에서 뽑되 마을 내 중복 회피
- 연대기에는 `"베른하르트 가의 요한"` 형태로 기록

## 검증

`docs/testing.md`의 **왕복 무결성 테스트**가 이 문서의 모든 필드를 덮어야 한다. promote → demote → promote 후 레코드가 동일해야 하며, 여기서 새는 필드가 반드시 하나는 나온다.
