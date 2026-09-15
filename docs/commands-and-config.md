# 명령어와 설정

## 마을 등록

V1에는 축2(앵커 블록)가 없다. 성능 상한은 **플레이어의 명시적 등록**이 만든다.

> **마을 종을 우클릭 → 등록**

- 한 번의 상호작용이라 부담이 없다
- 플레이어가 어느 마을을 키울지 고른다
- 등록되지 않은 바닐라 마을은 코드를 한 줄도 타지 않는다 (비용 0, 기존 세이브 안전)
- **V2의 앵커 블록으로 가는 길이 열린다.** 등록된 마을이 규모 조건을 채우면 "영주의 탁자를 지을 수 있다"는 안내가 뜬다. 교체가 아니라 추가라서 승격처럼 느껴진다

등록 조건: 반경 안에 종 1개, 침대 4개 이상, 주민 3명 이상.

## 명령어

전부 `/placitum` 하위. 기본 권한 레벨 2(OP).

예외는 조회 명령 셋이다. `info`, `chronicle`, `debug growth`는 **레벨 0**으로 연다. 싱글플레이 OP가 아닌 플레이어나 멀티플레이 일반 플레이어도 자기 마을 상태를 봐야 한다. 등록 자체는 명령어가 아니라 종 우클릭이므로 권한이 필요 없다.

### 마을 관리

| 명령어 | 설명 |
|---|---|
| `/placitum register` | 가장 가까운 종 기준으로 마을 등록 |
| `/placitum unregister <id>` | 등록 해제. 데이터 삭제 여부 확인 |
| `/placitum list` | 등록된 마을 목록 (ID, 이름, 규모, 인구, 좌표) |
| `/placitum info [id]` | 마을 상세. `catchUp` 후 표시 |
| `/placitum rename <id> <name>` | |

### 시뮬레이션 디버그

| 명령어 | 설명 |
|---|---|
| `/placitum tick <id> <ticks>` | 강제 정산. 인자는 **틱**이며 `ticks / 200`스텝이 돈다. 튜닝의 핵심 도구 |
| `/placitum promote <id>` | 수동 promote. 가상 고정을 해제한다 |
| `/placitum demote <id>` | 수동 demote **+ 가상 고정**. 아래 참조 |
| `/placitum debug growth <id>` | **수용력 3항목과 병목 표시.** 가장 자주 쓰게 된다 |
| `/placitum debug sim <id>` | 각 `SimModule`의 마지막 스텝 입출력 |
| `/placitum simulate raid <id> <threat> <n>` | 습격을 n회 굴려 생존율 통계. L0/L2 보정용 |

**`demote`는 고정까지 한다.** 안 그러면 다음 틱에 취소된다 — 명령어를 친 플레이어가 마을에 서 있으므로 거리 판정이 즉시 다시 promote하기 때문이다. 그러면 이 명령어는 아무 일도 안 하는 것처럼 보이고, 정작 존재 이유인 "가상 공식이 도는 것을 관찰하기"에 쓸 수 없다.

고정은 런타임 전용이다. `promote`, `unregister`, 서버 재시작이 해제한다. `info`에 `[held virtual by /placitum demote]`로 표시된다.

`debug growth` 출력 예:

```
Hearthwood (VILLAGE, 인구 16)
  수용력 18  ← 침대 24 / 식량 18 ▲병목 / 안전 31
  식량 재고 340 (소비 16/스텝, 생산 18/스텝)
  다음 출산 확률 0.014/스텝
```

### 플롯과 건설

| 명령어 | 설명 |
|---|---|
| `/placitum plot list <id>` | 셀 상태 요약 (FREE/ROAD/BUILT/BLOCKED 개수) |
| `/placitum plot show <id>` | 격자를 파티클로 월드에 표시 |
| `/placitum plot block <id> <gx> <gz>` | 셀 수동 금지 |
| `/placitum plot register <id>` | 서 있는 위치의 건물을 수동 등록 |
| `/placitum build status <id>` | BuildJob 큐, 진행도, 대기 자재 |
| `/placitum build force <id>` | 현재 job을 즉시 완료 (디버그) |

### 기타

| 명령어 | 설명 |
|---|---|
| `/placitum chronicle <id> [n]` | 연대기 최근 n줄 (기본 10) |
| `/placitum resident list <id>` | 주민 목록 (이름, 직업, 상태, 나이) |
| `/placitum resident info <uuid>` | 주민 상세 |
| `/placitum reload` | config 재로드 |
| `/placitum verify <id>` | 데이터 정합성 검사 (고아 Plot, 범위 밖 progress, 중복 바인딩) |

## 설정

`config/placitum-common.toml`. **밸런스에 관여하는 숫자는 전부 여기 있어야 한다.** 매직 넘버 금지.

### 확률은 전부 스텝 단위다

`...PerStep` 키는 **10초당 확률**이다. 1 게임일 = 120스텝이므로 감이 크게 어긋난다. 0.02는 하루 2.4회다.

새 확률 키를 추가할 때는 **주석에 게임일 환산값을 함께 적는다.** 이 함정은 반복해서 밟게 된다.

### [settlement]

```toml
claimRadiusChunks = 5
registerMinBeds = 4
registerMinResidents = 3
minSettlementDistance = 96   # 클레임 겹침 방지. 블록
```

### [lifecycle]

```toml
promoteRadius = 96          # 블록
demoteRadius = 144          # promoteRadius보다 반드시 커야 함 (히스테리시스)
demoteDelayTicks = 200
replayOpsPerTick = 4
maxMaterializedResidents = 60
```

### [simulation]

```toml
stepTicks = 200             # 1 시뮬 스텝 = 10초
maxCatchupTicks = 72000     # 3 게임일
tickBudgetNanos = 500000    # 0.5ms
heartbeatIntervalTicks = 24000
```

### [chunks]

```toml
maxForcedChunks = 8         # 전역 상한. 기본 동작은 강제 로딩 0
```

### [population]

```toml
baseBirthRate = 0.02
consumptionPerHead = 1
yieldRate = 3
famineGraceSteps = 18
famineMoralePenalty = 4
famineDeathChancePerStep = 0.01
foodWarningSteps = 180
safetyWindowDays = 7
safetyDeathPenalty = 3
enableAging = true
elderThresholdDays = 90
elderDeathChancePerStep = 0.0008   # 게임일당 약 9%
infantDays = 3
childDays = 20
immigrationRenownThreshold = 40
immigrationChancePerStep = 0.001   # 게임일당 약 0.12회
baseSafetyDefenseDivisor = 4       # baseSafety = 4 + defenseRating / 이 값
```

`baseBirthRate = 0.02`는 로지스틱 감쇠가 붙은 뒤의 값이라 초기 인구가 적을 때 하루 2회 남짓이 된다. 빠르다고 느껴지면 가장 먼저 내려볼 값이다.

### [defense]

```toml
curfewLeadTicks = 1200
alertCooldownTicks = 1200
routThreshold = 0.5
watchRadius = 32
watchIntervalTicks = 20
militiaRatioCap = 0.3
raidChancePerStep = 0.002    # 게임일당 약 0.24회 = 4일에 한 번
golemDefenseWeight = 12
```

### [construction]

```toml
buildOpIntervalTicks = 10
builderReach = 5.5
verifySampleEvery = 16
maxRebuildAttempts = 3
wallSalvageRatio = 0.5
recentTemplatePenalty = 0.4
opsPerStepPerBuilder = 6    # L2 진행 속도
```

### [scale]

```toml
scaleDemoteMargin = 2       # minPop - 이 값 아래에서만 강등
scaleHoldRequired = 360     # 조건을 연속 이만큼 유지해야 발동 (스텝)
```

`docs/data-model.md`의 규모 히스테리시스. 이게 없으면 경계 인구에서 성벽이 무한히 철거·재건된다.

### [debug]

```toml
logSimSteps = false
showPlotParticles = false
strictDeterminism = false   # 켜면 sim 안의 비결정 호출을 예외로 터뜨린다
```

`strictDeterminism`은 개발용이다. 시뮬레이션 진입 시 스레드 로컬 플래그를 세우고, `level.random` 접근이나 `setBlock` 호출을 감싼 지점에서 검사한다. `docs/testing.md`의 grep 검사가 잡지 못하는 간접 호출을 잡는다.

## 데이터팩 확장 지점

모드 없이 애드온을 만들 수 있게 열어둔다.

| 경로 | 내용 |
|---|---|
| `data/placitum/jobs/*.json` | `JobDef` |
| `data/placitum/structures/**/*.nbt` | 건물 템플릿 |
| `data/placitum/biome_palette/*.json` | 바이옴별 블록 치환 |
| `data/placitum/names/given.json`, `family.json` | 이름 풀 |
| `data/placitum/wall/*.json` | 성벽 등급별 재질 정의 |
