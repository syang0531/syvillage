# Placitum

마인크래프트 자바 에디션 모드. **종을 울려 지정한 마을이 스스로 인프라를 짓는다.**

> *placitum* — 카롤링거 시대의 공개 집회. 영주와 자유민이 모여 안건을 다루던 자리.

## 해결하려는 문제

**밤에 몬스터가 주민을 죽인다.**

이게 처음부터 유일한 문제였다. 이전 설계는 이걸 습격 시뮬레이션으로 옮겨서 풀었고, 그동안 실제 몹은 성벽 안에서 계속 스폰됐다. 갈아엎은 이유는 `docs/why-the-reset.md`.

답은 **빛과 벽**이다. 빛이 스폰을 막고, 벽이 걸어 들어오는 것을 막는다.

## 환경

| 항목 | 값 |
|---|---|
| Minecraft | 26.2 (Java Edition) |
| 모드로더 | NeoForge 26.2.0.88 |
| Java | 25 |
| modid | `placitum` |
| 패키지 루트 | `com.syang.placitum` |
| 빌드 | `./gradlew build` / 실행 `./gradlew runClient` |
| 배포 | CurseForge. `PUBLISHING.md` |

> **26.2 개명:** `ResourceLocation` → `Identifier`, `DimensionDataStorage` → `SavedDataStorage`, `Villager`는 `npc.villager` 패키지, 침대는 `Blocks.BED.pick(DyeColor)`. `RecordCodecBuilder.group()`은 **16필드 상한**.

## 불변 원칙

### 1. 바닐라를 건드리지 않는다

직업은 작업대가 정한다. 번식은 침대와 음식이 정한다. 죽음은 몹이 정한다. **그중 무엇도 대신 계산하지 않는다.**

주민 기록이 없다. 주민 상태를 엔티티에 저장하지도, 엔티티에서 읽어오지도 않는다. **주민은 그냥 바닐라 주민이다.**

### 2. 우리가 하는 일은 건물을 짓는 것뿐이다

```
도로  →  가로등  →  집  →  성벽
```

자원도 재고도 비용도 없다. **건설이 시간을 쓰는 것으로 충분하다.**

### 3. 청크가 로드된 동안에만 짓는다

LOD 없음. promote/demote 없음. replay 없음. 안 가본 마을은 자라지 않는다.

### 4. `expand(recipe)`는 순수 함수다

지면은 QUEUE 단계에서 한 번 읽어 `groundProfile`에 박제한다. 같은 recipe는 항상 같은 블록 목록을 낸다 — **그래야 테스트할 수 있다.**

### 5. 이미 서 있는 것을 파괴하지 않는다

건설 전에 월드에 묻는다(`GridSurvey.builtOn`). 우리 성벽은 통나무라 블록으로는 안 보이므로 **기록에 묻는다**(`Settlement.onWall`).

## 절대 금지

- 주민을 시뮬레이션하기 (인구·식량·사기·직업·나이 — 전부 한 번 해봤고 실패했다)
- 매 틱 전체 마을 순회
- 클레임 전체 범위 AABB 엔티티 스캔
- 하드코딩된 밸런스 상수 → 전부 config로
- `HashMap` 순회 순서에 의존
- 기존 저장 포맷에 **필수** 필드 추가 → `optionalFieldOf` + 기본값

## 코드 규약

- 상태 객체는 **불변 record + Codec**
- 식별자·주석·커밋 메시지는 영어. 설계 문서는 한국어
- 밸런스 숫자는 전부 `PlacitumConfig`로
- 로깅은 `Placitum.LOGGER`

## 패키지 구조

```
com.syang.placitum
├─ Placitum.java      @Mod 진입점
├─ data/              record + Codec
├─ store/             SavedData, SettlementManager
├─ build/             측량, 계획, 전개, 배치
├─ settlement/        등록, 클레임, 이름
├─ command/           디버그 명령어
└─ config/            PlacitumConfig
```

## 작업 방식

- 설계는 `docs/design.md`. 의문이 생기면 코드를 먼저 쓰지 말고 질문할 것
- **이 프로젝트에서 가장 비싸게 배운 것:** "아무 일도 안 일어난다"는 관측에는 늘 설명이 둘 이상 있다. 도구가 자기가 **무엇을 안 했는지** 말하게 만들 것. 여덟 번 반복됐다 (`docs/why-the-reset.md`)
- 확률을 다룰 때는 **눈으로 확인할 수 없다.** 충분히 길게 돌려 큰 수의 법칙에게 물을 것
