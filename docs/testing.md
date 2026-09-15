# 검증

두 개의 핵심 테스트가 **M0에서 만들어지고 이후 모든 커밋에서 통과해야 한다.** 이 둘이 초록불이면 나머지는 살 붙이기다.

---

## 1. 왕복 무결성

> promote → demote → promote 후 데이터가 동일한가

원칙 1("데이터가 원본")이 실제로 지켜지는지 검증한다. 필드를 하나 추가할 때마다 이 테스트가 그것을 잡는다.

```java
@Test
void residentSurvivesRoundTrip() {
    Settlement before = fixture.withResidents(12).withBuildJob().build();
    clock.freezeAt(before.lastSimTick());        // ← 필수. 아래 참조
    String snapshot = serialize(before);

    lifecycle.promoteFully(before, level);       // 상태 머신을 끝까지 돌린다
    lifecycle.demoteAll(before, level);

    assertEquals(snapshot, serialize(before));
}
```

### 시간을 멈추지 않으면 이 테스트는 항상 실패한다

promote의 첫 단계가 `catchUp`이다(`docs/architecture.md`). `now > lastSimTick`이면 시뮬레이션이 정상적으로 상태를 바꾸므로, 왕복 전후가 다른 것이 **올바른 동작**이 된다. 그러면 테스트가 무엇을 검증하는지 알 수 없어진다.

`now == lastSimTick`으로 고정해 `catchUp`을 무동작으로 만들고, **오직 직렬화 왕복만** 남긴다. 정산이 섞이면 이 테스트의 의미가 사라진다.

같은 이유로 promote는 상태 머신이므로 테스트 헬퍼가 `DONE`까지 돌려야 한다. 한 틱만 돌리면 `CATCHING_UP`에서 멈춘 채 통과해버린다.

### 반드시 덮어야 할 필드

`docs/data-model.md`의 **모든 필드**. 특히 놓치기 쉬운 것들:

- `Resident.task` 진행도
- `Resident.gear` 내구도
- `Resident.vitals` 전체 (`health`, `morale`, `hunger`)
- `Resident.offers` (거래 목록이 왕복에서 사라지는 것이 가장 티 나는 회귀다)
- `Resident.zombified`
- `BuildJob.progress`, `BuildJob.recipe.groundProfile`
- `PlotGrid.cells` 상태
- `WallState.gates[].open`
- `Settlement.defense.recentCasualties` 링버퍼 (int[] — Codec에서 리스트 왕복 시 길이가 보존되는지)
- `Chronicle` 항목 수

### 실행 시점

M0 완료 시. 이후 새 필드를 추가할 때마다 픽스처를 갱신한다.

> 이 테스트는 M0에서 **반드시 한 번은 실패한다.** 새는 필드가 항상 하나는 있다. 그것을 찾는 것이 목적이다.

---

## 2. 정산 등가성

> `catchUp(1000틱 1회)` == `catchUp(100틱 10회)`

원칙 3("시뮬레이션은 결정적")을 검증한다. 이게 성립하면 예산 초과 시 정산을 여러 틱에 쪼개 넣어도 안전하다. 성립하지 않으면 서버 TPS에 따라 마을 역사가 달라진다.

```java
@Test
void catchUpIsSliceIndependent() {
    Settlement a = fixture.standard().build();
    Settlement b = fixture.standard().build();   // 동일 시드, 동일 초기 상태

    sim.catchUp(a, START + 1000);

    long t = START;
    for (int i = 0; i < 10; i++) sim.catchUp(b, t += 100);

    assertEquals(serialize(a), serialize(b));
}
```

### 흔한 실패 원인

| 증상 | 원인 |
|---|---|
| 매번 결과가 다름 | 시뮬레이션 안에서 `level.random` / `Math.random()` 사용 |
| 쪼갤 때만 다름 | RNG를 스텝 번호가 아니라 호출 횟수로 시드 |
| 가끔 다름 | `HashMap` / `HashSet` 순회 순서 의존 → `LinkedHashMap` 또는 정렬 후 순회 |
| 미세하게 다름 | 부동소수점 누적. 가능하면 정수 연산으로 |

마지막 세 개가 특히 자주 나온다. 컬렉션 순회 순서는 반드시 결정적이어야 한다.

---

## 3. 크래시 복구 (M0)

> 저장 시점에 `MATERIALIZED`였던 마을이 다음 부팅에서 정상 상태로 돌아오는가

크래시는 테스트하기 어렵지만 **크래시 이후의 상태는 만들 수 있다.** 세이브를 직접 조작한다.

```java
@Test
void materializedStateIsResetOnBoot() {
    Settlement s = fixture.withResidents(5).allMaterialized().build();
    manager.saveWithoutDemote(s);              // 크래시 흉내

    server.restart();

    assertTrue(manager.get(s.id()).residents().stream()
        .allMatch(r -> r.state() == VIRTUAL));
    assertEquals(5, manager.get(s.id()).population());   // 아무도 사라지지 않는다
}
```

추가로 확인할 것.

- 부착물을 가진 엔티티가 로드되면 재바인딩된다
- 같은 `residentId`를 가진 엔티티가 둘이면 나중 것이 `discard`된다
- `Resident`가 없는 고아 엔티티는 **죽이지 않고** 부착물만 떼어 방생한다

셋 다 `docs/architecture.md`의 재동기화 표에 대응한다.

---

## 4. L0 / L2 전투 보정 (M1)

테스트가 아니라 **계측 절차**다. 자동화된 assert가 아니라 사람이 돌려보고 계수를 조정한다.

1. `/placitum simulate raid <id> <threat> 500` → L2 생존율 통계
2. 같은 조건으로 L0 습격을 20회 수동 관찰 → 실측 생존율
3. 두 값의 오차가 15% 이내가 되도록 `defenseRating` 계수를 조정

**공식을 유도하려 하지 말 것.** 튜닝해야 하는 종류의 문제다.

조건 매트릭스 (최소):

| militia | gearTier | wallTier | threat |
|---|---|---|---|
| 2 | 1 | NONE | 5 |
| 4 | 2 | PALISADE | 10 |
| 8 | 3 | PALISADE | 20 |

---

## 5. 성능 (M5, 상시 관찰)

`docs/architecture.md`의 목표 수치 대비.

| 항목 | 목표 | 측정 방법 |
|---|---|---|
| 가상 주민 2000명 heartbeat | < 1ms | 마을 100개 × 주민 20명 픽스처 |
| 틱당 시뮬 | < 0.5ms | `tickBudgetNanos` 초과 이월 횟수 로그 |
| 강제 로딩 청크 | 기본 0 | `/placitum list`에 표시 |
| MATERIALIZED 주민 | ≤ 60 | 상한 도달 시 원거리부터 demote |

목표를 못 지키면 **최적화가 아니라 설계를 고친다.**

---

## 6. 회귀 체크리스트 (수동)

각 마일스톤 완료 시 게임 내에서 확인한다.

### 공통
- [ ] 등록되지 않은 바닐라 마을이 완전히 바닐라로 동작한다
- [ ] 서버 재시작 후 마을 상태 보존
- [ ] 전용 서버에서 클라이언트 크래시 없음
- [ ] 두 플레이어가 마을 경계를 반대로 드나들어도 promote/demote가 꼬이지 않는다
- [ ] 거래 목록이 promote → demote → promote 후에도 남아 있다
- [ ] `/placitum unregister` 후 모드를 빼고 세이브를 열 수 있다

### M1 이후
- [ ] 밤에 주민이 전원 실내
- [ ] 민병대가 무기고를 거쳐 무장
- [ ] 장비 0일 때 은신
- [ ] 전투 중 로그아웃 → 결과가 남

### M3 이후
- [ ] 주민이 성문을 통과해 길찾기 (닫힌 문 앞 정지 없음)
- [ ] 건설 중 플레이어가 블록을 부숴도 무한 루프 없음
- [ ] 경사지 성벽이 깨지지 않음
- [ ] promote 시 밀린 건설이 자연스럽게 재생

---

## 안티패턴 감시

코드 리뷰에서 최우선으로 확인한다. 전부 `CLAUDE.md`의 "절대 금지"에 대응한다.

```
grep -r "Math.random()\|new Random(\|level.random" src/main/java/com/syang/placitum/sim/
grep -r "getEntitiesOfClass" src/main/java/com/syang/placitum/
grep -rn "setBlock" src/main/java/com/syang/placitum/sim/
```

- `sim/` 패키지 안에 비결정적 난수 → 즉시 수정
- 클레임 전체 범위 `getEntitiesOfClass` → 감시 지점 스캔으로 대체
- `sim/` 안에 `setBlock` / `getBlockState` → `BuildOp` 큐로 대체

grep으로는 간접 호출을 못 잡는다. `strictDeterminism = true`(`docs/commands-and-config.md`)를 개발 중 상시 켜둔다. 시뮬레이션 진입 시 스레드 로컬 플래그를 세우고 위반 지점에서 예외를 던지므로, 서너 단계 아래에서 `level.random`을 건드리는 코드도 걸린다.

### 밸런스 감시

```
grep -rn "PerStep" src/main/resources/ src/main/java/com/syang/placitum/config/
```

`...PerStep` 값을 보면 **반드시 120을 곱해 게임일 환산값을 확인한다.** 0.02는 하루 2.4회다. 이 단위 착각은 코드가 아니라 밸런스를 조용히 망가뜨린다.
