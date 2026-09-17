# 명령어와 설정

> 이 문서는 2026-09-17에 다시 썼다. 이전 판은 삭제된 시뮬레이션의 명령어(`tick`, `promote`, `demote`, `debug growth`, `debug sim`)와 설정 키를 설명하고 있었다. 설계는 `docs/design.md`.

## 마을 등록

**종에 시프트 + 우클릭.** 그게 전부고, 이 모드가 월드에 추가하는 유일한 상호작용이다.

- 그냥 우클릭은 건드리지 않는다. 종이 울린다. 원래 하던 그대로
- 이미 등록된 종에 시프트 + 우클릭하면 "두 번째 등록 실패"가 아니라 **그 마을이 지금 뭘 하고 있는지** 보여준다. 마을 id를 외워서 명령어를 칠 필요가 없다
- 등록되지 않은 바닐라 마을은 코드를 한 줄도 타지 않는다

거절되는 경우는 하나뿐이다: 다른 마을의 클레임 안(`minSettlementDistance`).

## 명령어

전부 `/placitum` 하위.

| 명령어 | 권한 | 설명 |
|---|---|---|
| `/placitum register` | OP | 32블록 안의 가장 가까운 종으로 등록 |
| `/placitum unregister <id>` | OP | 등록 해제. **지어진 것은 그대로 남는다** |
| `/placitum list` | 전체 | 등록된 마을 (짧은 id, 이름, 좌표) |
| `/placitum info <id>` | 전체 | 종 시프트+우클릭과 같은 내용 |
| `/placitum build <id>` | 전체 | 지금 짓는 것, 또는 **왜 안 짓는지** |
| `/placitum light <id>` | 전체 | 아직 몹이 스폰될 수 있는 자리 |
| `/placitum plot show <id>` | 전체 | 부지 지도 |
| `/placitum plot survey <id>` | OP | 지금 측량 |
| `/placitum plot block <id> <gx> <gz>` | OP | 그 부지에 짓지 말 것 (플레이어 거부권) |

### 아무것도 안 지을 때 물어볼 것

`/placitum build`가 이 모드에서 가장 중요한 명령어다. "아무 일도 안 일어난다"는 관측에는 늘 설명이 둘 이상 있고, 이게 그중 어느 것인지 말해준다.

```
Steinerstead is not building anything
  lots out to phase 3, street to 99 blocks: 251 steep, 3 ok, 1 taken, 1 water
```

부지마다 이유가 하나씩 붙는다.

| | 뜻 | 플레이어가 할 수 있는 것 |
|---|---|---|
| `ok` | 지을 수 있다 | (곧 지어진다) |
| `taken` | 이미 지었다 | |
| `forbidden` | `plot block`으로 막았다 | |
| `unloaded` | 청크가 안 떠 있다 | 가보면 된다 |
| `built_on` | 누가 이미 서 있다 | |
| `water` | 물 | 메우면 된다 |
| `steep` | 평지가 아니다 | **평탄화하면 다음 패스에 집이 선다** |
| `unreachable` | 종에서 걸어갈 수 없다 | 다리를 놓거나 경사를 만들면 이어진다 |

`steep`이 251개인 것은 **고칠 문제가 아니라 초대장이다.** 이 모드는 혼자 마을을 만들지 않는다 — `docs/design.md`.

## 설정

`config/placitum-common.toml`. 키는 **14개뿐이고, 전부 실제로 읽힌다.**

예전에는 70개였고 그중 14개만 읽혔다. 나머지는 삭제된 시뮬레이션의 설정이 남은 것이었는데, 플레이어가 편집하는 파일에서 죽은 키는 **거짓말**이다 — `raidChancePerStep`을 보면 습격 빈도를 조절할 수 있다고 믿게 된다. 여기 있는 키는 전부 돌리면 게임이 바뀐다는 약속이다.

### `[settlement]`

| 키 | 기본값 | |
|---|---|---|
| `claimRadiusChunks` | 5 | 클레임 반경. **마을의 한계를 정하는 것** |
| `minSettlementDistance` | 96 | 이보다 가까우면 등록 거절 |

### `[plan]`

| 키 | 기본값 | |
|---|---|---|
| `buildMaxPhases` | 8 | 단계 수 천장. 보통은 클레임이 먼저 막는다 |
| `maxCellSlope` | 0 | 부지 기복 허용치. **0 = 완전 평지** |
| `maxRoadClimb` | 3 | 도로가 연속으로 높이를 바꿀 수 있는 걸음 수 |

도시계획의 치수(도로 3, 여유 1, 부지 7, 건축물 5, 주기 20)는 **설정이 아니다.** 그게 "여기 집 지을 자리가 있나"를 판단이 아니게 만드는 것이기 때문이다.

### `[light]`

| 키 | 기본값 | |
|---|---|---|
| `minLightLevel` | 8 | `/placitum light`가 어둡다고 보고하는 기준 |
| `lampBlocksBeyondStreet` | 1 | 마지막 도로에서 몇 블록 더 밝힐지 |

`minLightLevel`은 **보고 기준이지 배치 기준이 아니다.** 가로등 자리는 계획이 정한다. 밝다고 건너뛰게 했더니 도로 양쪽 기둥이 4블록 간격이라 한쪽이 다른 쪽을 밝혀서, 블록마다 있다 없다 하는 배치가 됐다.

### `[building]`

| 키 | 기본값 | |
|---|---|---|
| `surveyScanHeight` | 6 | 기존 건물을 찾을 때 지면 위로 보는 높이 |
| `surveyIntervalTicks` | 600 | 지면 재측량 주기 (30초) |
| `planIntervalTicks` | 20 | 유휴 마을이 할 일을 찾는 주기 (1초) |
| `clearHeight` | 16 | 건설 자리에서 베어낼 높이 |
| `buildBlocksPerTick` | 10 | 매 틱 놓는 블록 수. **속도 손잡이는 이거 하나뿐** |
| `roadBlocksPerJob` | 64 | 도로 한 작업의 기둥 수 |
| `lampsPerJob` | 8 | 한 작업에 세우는 가로등 수 |

`buildBlocksPerTick`이 하나인 데는 이유가 있다. 예전에는 간격과 배치 크기 두 개였고, 예전 월드에서 남은 config가 간격을 10으로 되돌려서 **10틱마다 10블록 = 정확히 원래 속도**가 나왔다. 10배 가속이 상쇄된 채로 "고쳤다"고 보고됐다. 손잡이 하나는 자기 자신과 어긋날 수 없다.

배포용으로는 `buildBlocksPerTick = 1`이 맞다. 10은 개발 속도다.
