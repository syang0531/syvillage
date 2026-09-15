# 시뮬레이션

## 시간은 틱이 아니라 델타로 흐른다

매 틱 도는 가상 시뮬은 마을이 늘어나는 순간 죽는다. 대신 **마지막 계산 시점만 저장하고 필요할 때 몰아서 정산**한다.

```java
public static final int STEP = 200;              // 10초 = 1 시뮬 스텝
public static final long MAX_CATCHUP = 72_000;   // 3 게임일. config

public void catchUp(SettlementMut s, long now) {
    long elapsed = now - s.lastSimTick();
    if (elapsed < STEP) return;                  // 스텝 하나도 못 채우면 아무것도 하지 않는다

    long skipped = 0;
    if (elapsed > MAX_CATCHUP) {
        skipped = elapsed - MAX_CATCHUP;
        s.lastSimTick += skipped;                // 잘린 구간은 시뮬 없이 시간만 흘린다
        elapsed = MAX_CATCHUP;
    }

    int steps = (int) (elapsed / STEP);
    for (int i = 0; i < steps; i++) {
        simStep(s, rngFor(s.id(), s.simStep() + i));
    }

    s.simStep += steps;
    s.lastSimTick += (long) steps * STEP;        // ← now가 아니다
    // elapsed % STEP 의 나머지 틱은 다음 호출로 넘긴다

    if (skipped > 0) summarizeGap(s, skipped);
}
```

### 나머지 틱을 버리면 등가성이 깨진다

`lastSimTick = now`로 두면 `elapsed % STEP`만큼의 시간이 매 호출마다 증발한다. 그러면 원칙 3의 기준이 **구현상 성립할 수 없다.**

| | 계산 | 결과 |
|---|---|---|
| `catchUp(+1000)` 1회 | `1000 / 200` | 5스텝 |
| `catchUp(+100)` 10회, `lastSimTick = now` | 매번 `100 / 200 = 0`, 시계는 전진 | **0스텝** |
| `catchUp(+100)` 10회, `lastSimTick += steps * STEP` | 잔여가 누적되어 200을 채울 때마다 1스텝 | 5스텝 ✓ |

`lastSimTick`은 **마지막으로 완료된 스텝의 경계**이지 마지막 호출 시각이 아니다. 잘라낸 구간(`skipped`)만 예외적으로 시계를 건너뛰며, 이때도 `STEP` 경계에 맞춰 잘라 잔여가 섞이지 않게 한다.

`simStep`은 스텝 수만큼만 증가한다. 호출 횟수에 연동시키면 RNG 스트림이 호출 패턴을 타게 되어 같은 이유로 깨진다.

### 가드는 모듈별이다 — 전면 중단이 아니다

위 논거는 **주민 단위 회계**에 대한 것이다. 몸이 있는 주민은 실제로 농사짓고 먹고 죽으므로, 공식으로 또 돌리면 이중 계산이 된다.

그런데 주민 단위 회계를 하지 않는 모듈에는 그 논거가 닿지 않는다. **"성벽이 없다"를 알아채는 데 이중 계산될 것이 없다.**

전면 중단으로 구현했더니 M3에서 교착이 났다.

| | 필요 조건 |
|---|---|
| `NeedsModule` (주문) | 아무도 없어야 함 (L2) |
| `ConstructionTick` (recipe 동결) | 누군가 있어야 함 (청크 로드) |

마을은 **아무도 안 볼 때만 성벽을 원할 수 있고, 누군가 볼 때만 그걸 실행할 수 있었다.**

그래서 `SimModule.runsWhileEmbodied()`로 모듈이 직접 선언한다. 기본값은 `false`.

| 모듈 | 실체화 중 실행 | 이유 |
|---|---|---|
| production / consumption / threat / population | ✗ | 이중 계산 |
| needs | ✓ | 셀 상태만 본다. 셀 수 있는 것이 없다 |
| construction | ✗ | L0 배치는 `BuilderEntity`의 일 (M3 5단계) |

**모듈별로 갈라도 결정성이 유지되는 이유:** 각 모듈은 `(worldSeed, settlementId, step*100 + order)`로 자기 스트림을 받는다. 하나를 건너뛰어도 다른 모듈의 스트림이 밀리지 않는다.

시계는 어느 쪽이든 전진한다. 누군가 서 있는 마을에도 시간은 흐른다.

## 실체화된 동안은 가상 시간이 아니다

`catchUp`의 첫 번째 관문이다.

```java
if (settlement.anyMaterialized()) {
    skipWholeSteps(settlement, now, stepTicks);
    return true;
}
```

주민에게 몸이 있는 동안 흐른 시간은 **월드에서 실제로 살아진 시간**이고, 그 결과는 writeBack이 회수한다. 그걸 가상 공식으로 또 돌리면 생산 모듈은 `MATERIALIZED` 주민을 건너뛰는데 마을 단위 소비는 전원을 세므로 **재고만 순수하게 깎인다.**

**이 판정은 호출 지점이 아니라 `catchUp` 안에 있어야 한다.** 틀린 것은 매번 다른 경로였기 때문이다. promote는 처음부터 순서가 맞았고, 피해는 재바인딩·청크 언로드·`/placitum info` 세 곳에서 나왔다 — 아무도 "시뮬레이션 진입점"이라고 생각하지 않던 곳들이다.

게임 테스트 로그:

```
step 69: 4 farmer(s) of 6 resident(s) produced 12 wheat
step 70: 0 farmer(s) of 6 resident(s) produced 0 wheat   ← 실체화 이후
```

## catchUp 호출 지점

세 군데뿐이다. 매 틱 순회는 존재하지 않는다.

1. **promote 직전** — 플레이어가 마을 근처에 들어올 때
2. **조회 시** — 종 UI를 열거나 `/placitum info`를 실행할 때
3. **heartbeat** — 게임일 1회. 다른 마을과 상호작용하는 마을만 (V1에서는 이주만)

## 결정성 — 타협 불가

`RandomSource`는 항상 `(worldSeed, settlementId, simStep)`으로 시드한다.

`level.random`, `Math.random()`, `new Random()`을 시뮬레이션 안에서 쓰면 안 된다. 코드 리뷰에서 최우선으로 확인할 항목이다.

### 시드를 XOR로 합치지 않는다

```java
// 나쁨 — 인접 마을·인접 스텝이 같은 시드로 충돌한다
RandomSource.create(worldSeed ^ id.hashCode() ^ simStep);
```

XOR은 정보를 섞지 않고 겹친다. `simStep`이 1씩 오르는 동안 상위 비트는 고정이므로, 마을 A의 스텝 7과 마을 B의 스텝 9가 같은 시드를 갖는 일이 실제로 생긴다. 반드시 혼합 함수를 거친다.

```java
static long mix64(long z) {                       // SplitMix64 finalizer
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
}

RandomSource rngFor(UUID id, long step) {
    long h = mix64(worldSeed) ^ mix64(id.getMostSignificantBits())
           ^ mix64(id.getLeastSignificantBits() * 31 + 17);
    return RandomSource.create(mix64(h + step * 0x9E3779B97F4A7C15L));
}
```

`UUID.hashCode()`는 128비트를 32비트로 뭉갠 값이라 시드 재료로 쓰지 않는다. 두 워드를 각각 섞는다.

### 모듈마다 스트림을 분리한다

한 스텝 안에서 모듈들이 `rng` 하나를 공유하면, 모듈 하나가 난수를 한 번 더 뽑는 사소한 변경이 **뒤따르는 모든 모듈의 결과를 밀어버린다.** 밸런스 패치 하나가 마을 역사 전체를 바꾼다.

```java
// simStep 내부
for (SimModule m : modules) {
    m.step(s, rngFor(s.id(), s.simStep() * 100L + m.order()));
}
```

`order()`가 고정되어 있으므로 모듈별 스트림도 고정된다. 모듈을 추가해도 기존 모듈의 스트림이 움직이지 않는다.
기준은 하나다.

> `catchUp(1000틱 한 번)` == `catchUp(100틱 × 10회)`

이게 성립하면 서버 부하에 따라 정산을 여러 틱에 쪼개 넣어도 안전하다. 성립하지 않으면 TPS에 따라 마을 역사가 달라진다. `docs/testing.md`의 **정산 등가성 테스트**로 매 커밋 검증한다.

## MAX_CATCHUP과 요약

플레이어가 한 달 만에 접속하면 `elapsed`가 수백만 틱이 된다. 상한으로 자르고, 잘린 구간은 시뮬레이션하지 않는다.

대신 `summarizeGap()`이 경과 시간에 비례하는 요약 이벤트를 연대기에 남긴다.

```
"긴 부재 동안 마을은 조용히 지냈다. 주민 2명이 노령으로 세상을 떠났다."
```

계산이 아니라 서사다. 게임적으로도 이쪽이 낫다.

## simStep 계약

```java
public interface SimModule {
    /** 한 스텝(10초)을 전진시킨다. rng 외의 난수를 쓰지 않는다. */
    void step(SettlementMut s, RandomSource rng);

    /** 낮을수록 먼저 실행된다. */
    int order();
}
```

### 실행 순서

순서가 밸런스에 영향을 준다. 고정한다.

| order | 모듈 | 하는 일 |
|---|---|---|
| 10 | `ProductionModule` | 생산 → stock 증가 |
| 20 | `ConsumptionModule` | 인구 × 소비량 → stock 감소, 기아 판정 |
| 30 | `ThreatModule` | 위협 굴리기, 습격 판정 |
| 40 | `PopulationModule` | 출산, 사망, 노령, 생애 단계 전이 |
| 50 | `ConstructionModule` | BuildJob progress 증가, 완공 처리 |
| 60 | `NeedsModule` | 부족 감지 → 새 BuildJob 생성 |
| 70 | `ScaleModule` | ScaleTier 승격/강등 판정 |

`NeedsModule`이 `ConstructionModule`보다 뒤인 것이 중요하다. 이번 스텝에 완공된 건물이 수용력에 반영된 다음에 부족을 판정해야 중복 발주가 없다.

### MATERIALIZED 주민 제외

모든 모듈은 `MATERIALIZED` 상태의 주민을 건너뛴다. 그들은 실제 엔티티로 행동하고 있으므로 이중 계산이 된다.

```java
for (Resident r : s.residents()) {
    if (r.state() == MATERIALIZED) continue;
    // ...
}
```

단 **마을 단위 집계(인구 수, 수용력, defenseRating)는 전원을 센다.** 존재 자체는 유효하다.

## 휘발성 플래그는 존재하지 않는다

한 스텝의 출력에 영향을 주는 값은 **전부 저장돼야 한다.** 예외는 없다.

M2에서 저공급 경고 플래그를 "휘발성으로 둬도 된다"고 판단했다. 세이브를 넘어가며 잃어봐야 경고가 한 번 더 나갈 뿐이라고 생각했기 때문이다. 틀렸다. `SettlementMut`은 **catchUp마다 새로 만들어지므로**, 1000틱 한 번은 경고를 한 번 쓰고 100틱 열 번은 열 번 쓴다.

정산 등가성 테스트가 즉시 잡았다. M0에서 만든 테스트가 두 마일스톤 뒤의 코드에서 제 값을 한 순간이다.

> "이건 사소하니까 저장 안 해도 된다"는 판단이 나오면, **그 값이 출력에 나타나는지만 보면 된다.** 나타나면 상태다.

## 틱 예산

`ServerTickEvent.Post`에서 예산 안에서만 일한다.

```java
long deadline = System.nanoTime() + BUDGET_NANOS;   // 기본 0.5ms
while (!queue.isEmpty() && System.nanoTime() < deadline) {
    queue.poll().run();
}
```

정산이 예산을 넘으면 다음 틱으로 이월한다. 결정성이 보장되므로 쪼개도 결과가 같다.

### catchUp도 예산을 지킨다

`MAX_CATCHUP` = 3 게임일이면 최대 360스텝이다. promote 직전에 이것을 한 틱에 다 돌리면 예산을 수십 배 넘긴다. **catchUp은 반환 가능한 루프로 만든다.**

```java
/** 예산이 남아 있는 동안만 전진한다. 끝났으면 true. */
boolean catchUpSliced(SettlementMut s, long now, long deadline) {
    while (now - s.lastSimTick() >= STEP) {
        if (System.nanoTime() >= deadline) return false;   // 다음 틱에 이어서
        simStep(s, rngFor(s.id(), s.simStep()));
        s.simStep++;
        s.lastSimTick += STEP;
    }
    return true;
}
```

스텝 경계에서만 빠져나오므로 중간 상태가 저장되는 일이 없다. promote는 이 함수가 `true`를 반환한 뒤에야 다음 단계로 간다 (`docs/architecture.md`의 promote 상태 머신).

`/placitum info` 같은 조회는 예산을 무시하고 끝까지 돌려도 된다. 명령어는 프레임이 한 번 튀어도 상관없다.
## 스레드

**메인 스레드에서 실행한다.** 레벨을 조금이라도 만지는 순간 메인 스레드 전용이 된다.

L2 시뮬이 순수 데이터라 이론적으로는 비동기가 가능하지만, V1에서는 하지 않는다. 예산 + 이월로 충분하다.

## 하지 말 것

- 매 틱 전체 마을 순회
- 시뮬레이션 안에서 `level.getBlockState()` 호출 (청크 로드 유발)
- 스텝 안에서 엔티티 스폰/제거
- 모듈 간 직접 호출 (순서에만 의존할 것)
