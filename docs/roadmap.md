# 로드맵

**순서대로 진행한다. 건너뛰지 않는다.** 각 마일스톤의 완료 기준을 통과하기 전에 다음으로 넘어가지 않는다.

---

## M0 — 데이터 레이어

**엔티티를 하나도 추가하지 않는다.** 지루하지만 여기가 척추다.

- [x] `Settlement`, `Resident`, `PlotGrid`, `Plot`, `BuildJob` record + Codec
      (서브레코드 분할 — `group()` 16필드 상한. `docs/data-model.md`)
- [x] SavedData 분할 (`placitum_index` + `placitum_village_<id>`)
- [x] `SettlementManager` (인덱스 메모리 유지, 개별 마을 lazy 로드)
- [x] 종 Shift+우클릭 → 마을 등록 + **기존 바닐라 주민 흡수** (`docs/vanilla-interop.md`)
- [x] `catchUp()` + 결정적 RNG (`mix64` 시드, 모듈별 스트림 분리)
- [x] 더미 `SimModule` (생산 order 10 / 소비 order 20)
- [x] promote / demote (바닐라 `Villager` 엔티티로)
- [x] 강제 demote 경로 (청크 언로드, 서버 종료) + 부팅 시 재동기화
- [x] `/placitum register|unregister|list|info|tick|promote|demote|resident list`
- [x] V2 훅 자리 예약 (`ruler`, `parentId`) — Codec에 영향을 주므로 지금 넣는다
- [x] **왕복 무결성 테스트**, **정산 등가성 테스트**, **크래시 복구 테스트** (`docs/testing.md`)

### 설계에서 벗어난 곳

전부 의도적이며 해당 문서에 근거를 적었다.

| 항목 | 상태 | 이유 |
|---|---|---|
| `Assignment.job`이 `Identifier` | `ResourceKey<JobDef>` 대신 | JobDef 레지스트리는 M4. 직렬화 형태가 같아 마이그레이션 없음 |
| 이름 풀이 코드 내장 | 데이터팩 대신 | 데이터팩 이관은 M2. 등록이 이름을 필요로 함 |
| `SimModule.step(s, params, rng)` | 문서에 없던 인자 | config 핸들은 설정 로드 전에 못 읽어 단위 테스트가 불가능해진다 |
| `Resident.offers`가 `CompoundTag` | 타입 없는 blob | 26.2는 아이템 컴포넌트를 데이터팩 리로드 때 바인딩 → 테스트에서 `ItemStack` 생성 불가 |

### 완료 기준

| # | 기준 | 검증 방법 | 상태 |
|---|---|---|---|
| 1 | 마을 등록 후 `/placitum tick`으로 식량 재고가 변한다 | 게임 내 수동 | ⏳ |
| 2 | promote → demote → promote 후 `Resident`가 동일하다 | `RoundTripTest` + 게임 내 로그 | ✅ |
| 3 | `catchUp(1000틱 1회)` == `catchUp(100틱 10회)` | `CatchUpEquivalenceTest` | ✅ |
| 4 | 서버 재시작 후 마을 상태가 보존된다 | 게임 내 로그 | ✅ |
| 5 | 기존 주민이 전원 이름을 갖고 `/placitum resident list`에 나온다 | 게임 내 수동 | ✅ |
| 6 | `/placitum unregister` 후 마을이 깨끗이 해제된다 | 게임 내 수동 | ✅ |

2번의 엔티티 왕복은 단위 테스트로 덮을 수 없다 — `Lifecycle.writeBack`이 실제 `Villager`를 읽기 때문이다. 대신 **라이프사이클 전환을 전부 로깅**해 게임 내에서 증거를 남기게 했다.

```
wrote back 7 resident(s) of 'Steinerstead'
PROMOTE done: 'Steinerstead' - 7 of 7 resident(s) materialized
Loaded settlement bc981568 from disk - pop 7, sim step 14
```

26.2에서 GameTest가 데이터팩 주도 모델로 바뀌어 비용이 커졌다. 로깅으로 충분히 관찰 가능하므로 M0에서는 이 방식을 쓰고, GameTest 도입 여부는 M1에서 다시 본다.

### 게임 내 테스트에서 발견된 버그

문서가 예측하지 못한 것들이라 기록해 둔다. 둘 다 로그가 없었으면 못 찾았다.

| 증상 | 원인 | 고침 |
|---|---|---|
| 주민 직업이 전부 `none` | 바닐라 주민은 **작업대를 점유해야** 직업이 생긴다. 갓 생성된 마을은 등록 순간 대부분 무직인데 그때 한 번 읽고 얼려버렸다 | `writeBack`에서 직업 갱신 (엔티티 → 데이터는 demote에서만 흐른다는 원칙 안에 있다) |
| 같은 주민이 `Rebound` 직후 `Promoted` | 재시작 후 promote가 시작된 뒤 저장된 엔티티가 청크에서 올라와 스스로 재바인딩. 진행 중이던 promote가 같은 주민을 또 스폰 | `promote`가 살아 있는 바인딩을 먼저 확인 → 멱등 |

> 2번에서 새는 필드가 반드시 하나는 나온다. 그것을 찾는 것이 이 마일스톤의 목적이다.

---

## M1 — 안전

원래 불만 중 하나가 여기서 해결된다. **이 시점에 이미 배포 가능한 모드가 된다.** "주민이 안 죽는 마인크래프트"만으로도 쓸 사람이 많다.

구현 순서: 야간 대피 → 경보 상태 → 민병대 → L2 습격 공식.

- [ ] 야간 대피 (귀가, 문 닫기, 실외 주민 회수)
- [ ] 조명 유지 (`BuildOp`로 횃불 배치)
- [ ] `AlertState` 상태 머신 + 종 울림 수동 경보
- [ ] 감시 지점 기반 위협 감지 (전방위 스캔 금지)
- [ ] `MilitiaEntity` + 엔티티 교체 징집
- [ ] 무기고 플롯, `stock`에서 장비 수령/반납
- [ ] `defenseRating` + L2 습격 공식
- [ ] 바닐라 습격·철 골렘 연동 (`docs/vanilla-interop.md`)
- [ ] 사망 원인 기록
- [ ] `/placitum simulate raid`

### 완료 기준

1. 밤에 주민이 전원 실내에 있고 문이 닫힌다
2. 종을 울리면 민병대가 무기고를 거쳐 무장하고 나온다
3. 장비가 없으면 징집되지 않고 은신한다
4. `/placitum simulate raid`로 L2 생존율을 뽑을 수 있고, L0 실측과 오차 15% 이내로 보정되었다
5. 전투 중 로그아웃해도 습격 결과가 난다

---

## M2 — 인구

- [ ] 바닐라 번식 차단
- [ ] `carryingCapacity` 3항목 (침대/식량/안전)
- [ ] 로지스틱 출산
- [ ] 식량 실제 소비 + 기아 + 경고
- [ ] 사망 4종 (`COMBAT`, `FAMINE`, `ACCIDENT`, `OLD_AGE`)
- [ ] 좀비 주민 / 치료 복귀 (`zombified`)
- [ ] 생애 단계 (`INFANT`는 엔티티 없음)
- [ ] 이름·가문 생성기
- [ ] `Chronicle` + 종 UI
- [ ] `/placitum debug growth`

### 완료 기준

1. 인구가 수용력에 수렴하고 그 이상 늘지 않는다
2. `/placitum debug growth`가 병목을 정확히 지목한다
3. 식량 부족 시 경고가 먼저 뜨고, 그 후 기아가 시작된다
4. 종 UI에 최근 사망자와 원인이 보인다

---

## M3 — 건설

물량이 제일 크다. **성벽 → 집 → 밭 순서를 지킨다.** 성벽이 파이프라인 검증용이다.

- [ ] 6단계 파이프라인 (`BuildJob` 상태 머신)
- [ ] `PlotGrid` 셀 판정 (heightmap 1차 필터)
- [ ] 성벽: 둘레 추적, 지형 규칙, 문, `GateNode` 길찾기 등록
- [ ] `BuilderEntity` (Y 오름차순 배치, 도달 범위)
- [ ] replay (`replayOpsPerTick`)
- [ ] 자재 예약 + `WAITING_MATERIALS` + 알림
- [ ] 집: `.nbt` 템플릿, 부지 선정, 회전, 바이옴 치환
- [ ] 밭: 절차 생성, 성벽 안/밖 배치
- [ ] 묘지
- [ ] 단순 도로 (중심 십자 + 건물까지 연장)
- [ ] 플레이어 충돌 처리 (샘플 검증, 포기)

### 완료 기준

1. 인구가 늘면 집이 자동으로 지어지고 수용력이 올라간다 — **고리가 닫힌다**
2. L2에서 진행된 건설이 promote 시 자연스럽게 재생된다
3. 성벽이 경사지에서 깨지지 않고, 5블록 이상 단차에서는 생략된다
4. 주민이 성문을 통과해 길찾기한다
5. 플레이어가 짓다 만 건물을 부숴도 무한 루프가 없다

---

## M4 — 규모 단계

- [ ] `ScaleTier` 승격/강등 + 히스테리시스 (`scaleHoldSteps`)
- [ ] 티어별 `gridSize` 확장 + 성벽 재건
- [ ] 티어별 건물/직업 해금
- [ ] `guard` 전업 직업 (`TOWN`부터)
- [ ] 성벽 `STONE`, `RAMPART` 등급
- [ ] 감시탑
- [ ] 이주

### 완료 기준

1. 개척지에서 도시까지 자연 성장하는 것을 관찰할 수 있다
2. 성벽 확장 시 옛 성벽이 철거되고 자재가 일부 회수된다
3. 인구가 줄면 강등된다
4. 경계 인구(예: 11↔12)에서 티어가 왕복하지 않는다

---

## M5 — 정리와 배포

- [ ] config 전체 노출 확인 (매직 넘버 잔존 검사)
- [ ] 데이터팩 확장 지점 문서화
- [ ] 성능 측정 (`docs/architecture.md` 목표 수치 대비)
- [ ] 멀티플레이 테스트 (전용 서버)
- [ ] 기존 세이브 호환 테스트
- [ ] 한국어/영어 번역 파일
- [ ] 모드 제거 시 남는 것 문서화 (`MilitiaEntity` 경고)
- [ ] README, 배포

### 완료 기준

1. 성능 목표(`docs/architecture.md`) 네 항목을 전부 만족한다
2. 모드를 넣기 전 세이브를 열었을 때 아무 변화가 없다
3. 전용 서버에서 두 플레이어가 같은 마을에 들어가고 나가도 promote/demote가 꼬이지 않는다

---

## V2는 이 로드맵에 없다

왕국·봉신·세금·전쟁은 `docs/v2-deferred.md`에 있고 **구현 대상이 아니다.**

단, 그 문서에 명시된 훅은 M0에 미리 넣는다. 자리를 비워두는 것과 나중에 뚫는 것은 차이가 크다.
