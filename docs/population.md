# 인구

원래 문제: **인구가 늘지 않고, 늘지 않는 이유를 알 수 없다.**

## 바닐라 번식을 끈다

등록된 마을에서는 바닐라 번식 메커니즘을 차단하고, 출산은 언제나 데이터 레이어가 결정한다.

차단은 Mixin이 아니라 이벤트로 한다. `BabyEntitySpawnEvent`(또는 26.2의 대응 이벤트)에서 부모가 등록된 마을 소속이면 취소한다. 등록되지 않은 마을은 이 판정에서 즉시 빠져나가므로 비용이 0이다.

`willingToMate` 상태 자체는 건드리지 않는다. 주민이 빵을 나눠주는 연출은 남겨두고 결과만 우리가 정한다.

전투와 달리 출산은 플레이어에게 보여줄 필요가 없다. 따라서 L0/L2 등가성 문제가 **아예 발생하지 않는다.** 언제나 공식이다.

바닐라 번식은 농부가 빵을 던져야 하고, 침대 판정이 까다롭고, 조금만 어긋나면 영원히 멈춘다. 공식으로 바꾸는 것만으로 최소한 **왜 안 늘어나는지 플레이어가 알 수 있게** 된다.

## 수용력에 수렴한다

지수 성장으로 두면 주민 500명이 되고 서버가 죽는다. 로지스틱으로 간다.

```java
int carryingCapacity(Settlement s) {
    return min(
        s.bedCount(),                                    // 주거
        s.foodProduction() / CONSUMPTION_PER_HEAD,       // 식량
        s.safetyCap()                                    // 안전
    );
}

double birthChance(Settlement s) {
    int pop = s.population(), K = carryingCapacity(s);
    if (pop >= K || fertilePairs(s) == 0) return 0;
    return BASE_BIRTH_RATE * (1.0 - (double) pop / K) * moraleFactor(s);
}
```

### 최솟값이라는 것이 핵심

침대가 남아돌아도 식량이 부족하면 안 늘고, 식량이 넘쳐도 자꾸 털리면 안 는다.

플레이어가 "우리 마을은 지금 뭐가 병목이지"를 묻게 되고, 그 답이 명확하다. **종 UI에 세 값을 나란히 보여주고 가장 낮은 것을 강조한다.** 그것만으로 충분한 안내가 된다.

```
수용력 18  ←  침대 24 / 식량 18 / 안전 31
현재 인구 16
```

### safetyCap

최근 `safetyWindowDays`(기본 7 게임일) 동안의 사상자 수에서 나온다.

```java
int safetyCap(Settlement s) {
    int recentDeaths = IntStream.of(s.recentCasualties()).sum();   // 링버퍼. 연대기가 아니다
    return Math.max(4, baseSafety(s) - recentDeaths * safetyDeathPenalty);
}
```

**연대기에서 세지 않는다.** `Chronicle`은 200줄에서 잘리는 손실 가능한 로그라, 사건이 잦은 마을일수록 7일치 사망 기록이 먼저 밀려난다. 그러면 *많이 털릴수록 안전해 보이는* 역전이 생긴다. 전투 사망자는 `Settlement.recentCasualties` 링버퍼로 따로 센다 (`docs/data-model.md`).

계속 털리는 마을은 사람이 늘지 않는다. 현실적이면서 `docs/defense.md`의 민병대·성벽에 동기를 만든다.

### baseSafety

```java
int baseSafety(Settlement s) {
    return 4 + defenseRating(s) / 4;   // defenseRating은 docs/defense.md
}
```

수용력 세 항목 중 **안전만이 방어 시스템과 물린다.** 이 한 줄이 `docs/defense.md`의 성벽·민병대·감시탑을 인구 성장의 전제조건으로 만든다. 계수는 튜닝 대상이며 config로 뺀다.

## 식량을 실제로 소비시킨다

이게 없으면 식량 재고가 장식이 된다.

```
매 스텝:
  stock.food -= population * CONSUMPTION_RATE
  stock.food += farmerCount * fieldPlots * YIELD_RATE
```

그러면 연쇄가 생긴다.

> 인구 증가 → 소비 증가 → 농부가 더 필요 → 밭 플롯이 더 필요 → **건설이 필요**

`docs/construction.md`와 자연스럽게 물리고, 마을이 자기 힘으로 커져야 할 이유가 생긴다.

### 기아

`stock.food == 0`이 `famineGraceSteps`(기본 18 = 3분) 넘게 지속되면 기아 상태다.

1. 사기 급락 (`morale -= famineMoralePenalty` 매 스텝)
2. 지속되면 확률적 사망 시작
3. 심화되면 이주 또는 마을 해체

**경고가 먼저 가야 한다.** `stock.food`가 `population * CONSUMPTION_RATE * warningSteps` 아래로 내려가면 즉시 알림.

```
⚠ 식량이 3일치 남았습니다 — 농부 2명, 밭 3구획
```

경고가 없으면 원래 문제가 형태만 바꿔 재발한다.

## 사망에는 이유가 있어야 한다

| 원인 | 발생 조건 |
|---|---|
| `COMBAT` | 습격 결과 (L0 실제 전투 또는 L2 공식) |
| `FAMINE` | 식량 재고 0 지속 |
| `ACCIDENT` | L0에서의 낙사·용암 등. 바닐라 그대로 |
| `OLD_AGE` | `ageDays > elderThreshold` 후 확률적 |

노령은 기본값으로 켜되 매우 느리게 하고, **설정으로 끌 수 있게 한다**(`enableAging`). 애착이 생긴 주민이 늙어 죽는 것을 원치 않는 플레이어가 반드시 있다.

### 생애 단계

| 단계 | 기간 | 엔티티 |
|---|---|---|
| `INFANT` | 0 ~ 3 게임일 | **스폰하지 않음.** 레코드로만 |
| `CHILD` | 3 ~ 20 게임일 | 스폰하되 집 반경 밖으로 못 나감 |
| `ADULT` | 20 ~ `elderThreshold` | 정상 |
| `ELDER` | 이후 | 생산량 감소, 확률적 사망 |

`INFANT`를 엔티티로 만들지 않는 것이 중요하다. 바닐라 아기 주민이 밖으로 돌아다니다 죽는 것이 몰살의 흔한 원인이다. 안전하고, 비용도 줄고, 관리도 쉬워진다.

## 연대기

```java
record ChronicleEntry(long gameTime, EntryType type, String subject, String detail) {}
```

기록 대상: 출생, 사망(원인 포함), 건물 완공, 습격 격퇴/패배, 규모 승격/강등, 기아.

종을 우클릭하면 마을 현황 + 최근 10줄이 뜬다. V1에서는 GUI 하나면 충분하다.

```
베른하르트 가의 요한 — 성문에서 전사 (3일 전)
밀렌 가의 아나 — 출생 (5일 전)
대장간 완공 (6일 전)
```

## 묘지 — 로그 백 줄보다 강하다

사망자가 생기면 `GRAVEYARD` 플롯에 무덤 하나가 놓이고 이름표가 붙는다.

구현은 `BuildOp` 큐에 항목 하나 넣는 게 전부다. 그런데 플레이어가 몇 주 만에 돌아와 **묘지가 늘어난 것을 발견하는 경험**은 로그 창 백 줄보다 강하다. 마을 규모를 무덤 수로도 읽을 수 있게 된다.

묘지는 `HAMLET` 단계에서 자동 배치되며, 성벽 안쪽 외곽 셀을 선호한다.

## 이름과 가문

바닐라 주민에게 정이 안 가는 이유 중 하나가 이름이 없어서다.

- 성(姓)은 **부계 상속**. `Resident.familyName`
- 이름은 데이터팩 풀에서 뽑되 마을 내 중복 회피
- 연대기·이름표는 `"베른하르트 가의 요한"` 형태

복잡한 가계도는 V2로 미룬다. V1에서는 `motherId`, `fatherId` 두 필드만 저장한다.

## 좀비 주민

바닐라에서 주민이 좀비에게 죽으면 난이도에 따라 **좀비 주민으로 변한다.** 이때 `Resident`를 지워버리면 플레이어가 치료해도 돌아올 사람이 없다.

| 사건 | 처리 |
|---|---|
| 변이 | `Resident`를 남기고 `stage`는 유지, 새 필드 `zombified = true`. 인구·수용력 집계에서 제외 |
| 치료 완료 | `zombified = false`. 연대기에 `CURED` 기록 |
| 좀비 상태로 사망 | 그때 `Resident` 제거. 연대기 `DEATH(COMBAT)` |
| 좀비 상태에서 마을이 L2로 내려감 | 엔티티가 사라지므로 `zombified`인 채 `VIRTUAL`. 치료 기회는 사라지지 않는다 |

치료한 주민이 이름과 가족을 그대로 갖고 돌아오는 것은 **연대기가 있어서 가능한 연출**이다. 비용은 `boolean` 하나다.

## 이주 — V2로 가는 다리

출산 말고 두 번째 성장 경로다.

- 마을 명성(`renown` = 규모 + 사기 + 방어 등급 - 최근 사망자)이 임계값을 넘으면 외부 이주민이 유입
- 초기 인구가 적어 출산이 느릴 때 부트스트랩 역할
- *좋은 마을을 만들면 사람이 모인다*는 직관적 피드백

그리고 이것이 V2로 가는 다리다. 인접 마을 간 인구 이동은 이미 "정주지 간 관계"이므로, V1에 이주 한 줄을 넣어두면 V2의 물류·징집이 같은 경로 위에 얹힌다.

## 파라미터는 전부 밖으로

출산율, 소비량, 수확량, 노령 임계값, 수용력 계수. **이런 건 처음에 절대 맞출 수 없다.**

전부 config로 노출하고, `/placitum debug growth`로 현재 수용력과 병목을 즉시 볼 수 있게 한다. 튜닝 시간이 몇 배로 줄어든다.
