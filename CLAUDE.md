# Placitum

마인크래프트 자바 에디션 모드. 바닐라 마을을 **스스로 성장하고 스스로 방어하는 정주지**로 바꾼다.

> *placitum* — 카롤링거 시대의 공개 집회. 영주와 자유민이 모여 안건을 다루던 자리.

## 해결하려는 문제

바닐라 마을의 세 가지 결함. 이 셋이 V1의 전체 범위다.

1. 주민이 어느 순간 전멸해 있고, 왜 죽었는지 알 수 없다
2. 인구가 늘지 않는다
3. 마을 규모(집 수)가 늘지 않는다

이 셋은 하나의 고리다. **안전 → 인구 → 건물 → 수용력 → 인구.** 하나라도 빠지면 고리가 돌지 않는다.

## 환경

| 항목 | 값 |
|---|---|
| Minecraft | 26.2 (Java Edition) |
| 모드로더 | NeoForge 26.2.0.88 |
| Java | 25 (26.2의 요구 버전) |
| modid | `placitum` |
| 패키지 루트 | `com.syang.placitum` |
| 빌드 | `./gradlew build` / 실행 `./gradlew runClient` |
| 배포 | CurseForge. `PUBLISHING.md` |

Parchment 매핑은 26.2용이 아직 없다. 파라미터 이름이 `p_123456_` 형태로 나오는 것이 정상이며, 공개되면 `build.gradle`의 주석 처리된 블록을 되살린다.

> **주의:** `docs/architecture.md`의 "Mojang / NeoForge 접점" 표는 26.2 소스에서 직접 확인한 값이다. **그 표 밖의 클래스명은 1.21.x 기준 참고값**이므로 쓰기 전에 확인할 것. 문서와 실제가 다르면 실제를 따르고, 문서를 고쳐라.
>
> 26.2에서 개명된 것: `ResourceLocation` → **`Identifier`**, `DimensionDataStorage` → **`SavedDataStorage`**, `Villager`는 `npc.villager` 패키지로 이동, `VillagerProfession`은 레지스트리 record.
>
> `RecordCodecBuilder.group()`은 **16필드가 상한**이다 (DFU 10.0.21). record가 그보다 크면 서브레코드로 쪼갠다.

## 불변 원칙 — 어길 수 없다

### 1. 데이터가 원본, 엔티티는 뷰

주민의 진실은 `Resident` 레코드에 있다. 엔티티는 그것을 잠깐 그려주는 껍데기다.
상태를 엔티티 NBT나 `AttachmentType`에 저장하지 않는다. 엔티티에 붙이는 것은 `UUID residentId` 하나뿐이다.

동기화는 단방향이다. promote 시 `데이터 → 엔티티`, demote 시 `엔티티 → 데이터`. 양방향 동기화 코드를 작성하지 않는다.

예외는 하나뿐이다. `Resident.offersSnapshot`(바닐라 거래 목록)은 불투명한 blob으로 왕복한다. 시뮬레이션은 이 필드를 읽지 않는다. **예외를 늘리지 않는다.**

### 2. 가상 단계에서 월드를 건드리지 않는다

플레이어가 없는 마을(L2)은 블록을 배치하지 않는다. 모든 월드 변경은 `BuildOp` 큐에 쌓고, 청크가 살아날 때 재생한다.

### 3. 시뮬레이션은 결정적이다

`RandomSource`는 항상 `(worldSeed, settlementId, simStep)`으로 시드한다. 절대 `level.random`이나 `Math.random()`을 시뮬레이션 안에서 쓰지 않는다.

기준: `catchUp(1000틱 한 번)`과 `catchUp(100틱 × 10회)`의 결과가 **동일해야 한다.**

`lastSimTick`은 마지막 호출 시각이 아니라 **마지막으로 완료된 스텝의 경계**다. 잔여 틱을 버리면 이 기준이 성립할 수 없다.

### 4. 강제 청크 로딩은 기본값 0

티켓은 설정으로 명시적으로 켠 마을에서만, 전역 상한 안에서만 발급한다. 티어가 올라간다고 로딩 범위를 늘리지 않는다.

### 5. V2는 구현하지 않는다

왕국·봉신·세금·전쟁은 `docs/v2-deferred.md`에 있고 **구현 대상이 아니다.** 단, 그 문서에 명시된 훅(빈 필드·빈 함수)은 V1에 미리 넣는다.

## 절대 금지

- 주민 상태를 엔티티에 저장
- 바닐라 `Villager` 브레인을 Mixin으로 개조 → 대신 엔티티 교체(`docs/defense.md`)
- 매 틱 전체 마을 순회
- 클레임 전체 범위 AABB 엔티티 스캔 → 감시 지점만 스캔
- 시뮬레이션 안에서 비결정적 난수
- 시드를 XOR로 합치기 → `mix64`로 섞는다
- `Chronicle`을 시뮬레이션 입력으로 사용 (200줄에서 잘리는 손실 로그다)
- 기존 저장 포맷에 **필수** 필드 추가하거나 필드 이름 바꾸기 → `optionalFieldOf` + 기본값. `SavedDataStorage`는 부분 디코딩을 삼키므로 주민 명부가 조용히 비워진다
- 저장된 필드의 **타입 바꾸기** → `Codec.withAlternative`로 옛 모양도 읽는다. 이름 변경과 똑같이 세이브를 깬다 (`ScaleTier` → `ScaleState`로 실제로 깼다)
- `HashMap`/`HashSet` 순회 순서에 의존
- 하드코딩된 밸런스 상수 → 전부 config로

## 코드 규약

- 상태 객체는 **불변 record + Codec**. 가변이 꼭 필요하면 `store` 패키지 안에서만.
- 식별자·주석·커밋 메시지는 영어. 설계 문서는 한국어.
- 밸런스에 관여하는 숫자는 전부 `PlacitumConfig`로. 매직 넘버 금지.
- 새 시뮬레이션 모듈은 `SimModule` 인터페이스를 구현하고 `simStep`에 등록한다.
- 로깅은 `Placitum.LOGGER`. 시뮬레이션 내부 로그는 `DEBUG` 이하.

## 패키지 구조

```
com.syang.placitum
├─ Placitum.java          @Mod 진입점
├─ registry/              ModBlocks, ModItems, ModEntities, ModAttachments
├─ data/                  record + Codec (Settlement, Resident, Plot, BuildJob, ...)
├─ store/                 SavedData, SettlementManager
├─ sim/                   catchUp, SimModule 구현체 (population, defense, construction)
├─ lifecycle/             promote / demote
├─ entity/                MilitiaEntity 등 커스텀 엔티티
├─ build/                 Blueprint, BuildOp, 각 Planner
├─ command/               디버그 명령어
└─ config/                PlacitumConfig
```

## 문서 색인

| 문서 | 내용 |
|---|---|
| `docs/architecture.md` | LOD 구조, promote/demote, 부분 로딩 |
| `docs/data-model.md` | 전체 record 정의, Codec, SavedData 분할 |
| `docs/simulation.md` | catchUp, simStep 계약, 결정성 |
| `docs/defense.md` | 경보 상태 머신, 민병대, 습격 판정 |
| `docs/population.md` | 수용력, 출산·사망, 연대기 |
| `docs/construction.md` | 건설 파이프라인, 플롯 격자, 성벽·집·밭 |
| `docs/commands-and-config.md` | 명령어 사양, 설정 키 |
| `docs/vanilla-interop.md` | 등록 시 주민 흡수, 거래·골렘·좀비 주민, 세이브 호환 |
| `docs/open-questions.md` | 미해결 문제. **코드 쓰기 전에 본다** |
| `docs/roadmap.md` | 마일스톤과 완료 기준 |
| `docs/testing.md` | 검증 항목 |
| `docs/v2-deferred.md` | 축2 설계 (구현 금지, 훅만) |

## 작업 방식

- 마일스톤은 `docs/roadmap.md` 순서대로. 건너뛰지 않는다.
- 각 마일스톤의 **완료 기준을 통과하기 전에 다음으로 넘어가지 않는다.**
- `docs/testing.md`의 왕복 무결성·정산 등가성 테스트는 M0에서 만들고 이후 계속 통과해야 한다.
- 설계에 의문이 생기면 코드를 먼저 쓰지 말고 질문할 것. 이 문서들은 합의된 설계다.
- `...PerStep` 확률을 다룰 때는 **120을 곱해 게임일 환산값을 확인한다.** 1 게임일 = 120스텝.
