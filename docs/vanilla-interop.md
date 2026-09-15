# 바닐라 상호작용

설계 문서 대부분은 "마을이 등록된 뒤"를 다룬다. 이 문서는 **그 경계**를 다룬다. 등록되지 않은 마을, 등록되는 순간, 그리고 바닐라 메커니즘과 겹치는 지점.

원칙은 하나다. **등록되지 않은 것은 코드를 한 줄도 타지 않는다.** 기존 세이브가 안전해야 하고, 플레이어가 고른 마을만 비용을 낸다.

---

## 등록 — 기존 주민을 흡수한다

`docs/commands-and-config.md`가 등록 조건을 정의하지만, **이미 살고 있던 바닐라 주민을 어떻게 `Resident`로 바꾸는지**가 빠져 있었다. M0의 실제 첫 관문이다.

```
1. 종 기준 claimRadiusChunks 안의 Villager 수집
2. 각각에 대해 Resident 생성
3. 엔티티에 RESIDENT_ID 부착. 엔티티는 버리지 않는다 (교체 없음)
4. PlotGrid 초기 스캔 → 기존 건물을 BUILT로 표시
5. 침대·작업대 POI를 Plot으로 역등록
```

### 필드 초기값

| 필드 | 출처 |
|---|---|
| `givenName`, `familyName` | 이름 풀에서 신규 생성. 가문은 **주민마다 새로** 부여 (기존 관계를 알 수 없다) |
| `stage`, `ageDays` | `isBaby()`면 `CHILD`(3일), 아니면 `ADULT`(25일) |
| `job` | 바닐라 직업 → `JobDef` 매핑 테이블. 26.2에서 `VillagerProfession`은 enum이 아니라 레지스트리 record이므로 키는 `ResourceKey<VillagerProfession>`이다 |
| `health` | 엔티티의 현재 체력 |
| `morale`, `hunger` | 50, 50 (중립) |
| `homePlot`, `workPlot` | POI 역등록 결과. 없으면 null |
| `gear` | 비어 있음 |
| `motherId`, `fatherId` | null. 1세대는 뿌리가 없다 |

`INFANT`는 만들지 않는다. 등록 시점에 바닐라 아기 주민이 있으면 `CHILD`로 올린다. 기록상 존재하지 않던 3일을 소급하지 않는다.

### 직업 매핑

`VillagerProfession.FARMER` 같은 상수는 26.2에서 `ResourceKey<VillagerProfession>`이다. 매핑 테이블의 키로 그대로 쓸 수 있다.

| 바닐라 | `JobDef` |
|---|---|
| `farmer` | `placitum:farmer` |
| `fisherman`, `shepherd`, `butcher` | `placitum:farmer` (V1에서는 식량 생산으로 통합) |
| `toolsmith`, `weaponsmith`, `armorer` | `placitum:smith` |
| `mason`, `cartographer` | `placitum:builder` |
| `fletcher`, `leatherworker` | `placitum:woodcutter` |
| `librarian`, `cleric` | `placitum:scholar` (`militiaEligible = false`) |
| `nitwit`, `none` | `placitum:none`. 나중에 배정된다 |

V1의 직업은 여섯 개면 충분하다. 바닐라 직업 열세 개를 그대로 옮기면 `JobDef`마다 생산 공식과 밸런스가 필요해지는데, 그건 M4 이후의 일이다. **거래는 바닐라 직업이 계속 결정한다** — 매핑은 시뮬레이션 전용이다.

### 되돌릴 수 있어야 한다

`/placitum unregister <id>`는 되돌리기다. 실수로 등록했을 때 세이브가 망가지면 안 된다.

1. **demote가 아니라 release.** 엔티티를 살려둔 채 부착물과 이름표만 떼고 바인딩을 끊는다
2. `MilitiaEntity`는 `Villager`로 되돌린다
3. 마을 데이터 파일은 남지만 인덱스에서 빠진다. 같은 종을 다시 등록하면 **새 마을**이 생긴다
4. 마을이 지은 블록은 **그대로 둔다.** 철거하지 않는다

> **1번을 demote로 구현하면 안 된다.** demote는 `entity.discard()`를 한다 — 레코드가 남아 나중에 다시 만들 것이기 때문이다. unregister는 **레코드 쪽을 버리는 것**이므로 여기서 discard하면 주민이 월드에서 사라진다. 실수를 되돌리라고 만든 명령어가 주민을 죽이는 셈이다.
>
> 실제로 이 실수를 저질렀고 게임 테스트에서 잡혔다. unregister 직후 재등록하니 `Not enough villagers: 0 of 2`가 떴다.

---

## 거래

**건드리지 않는다.** `Villager`의 거래·평판·가십은 전부 바닐라 그대로다.

이유는 셋이다. 거래는 이미 잘 동작하고, 플레이어가 정확히 기대하는 동작이며, 우리 문제가 아니다. 그리고 거래 데이터를 `Resident`로 옮기는 순간 원칙 1("데이터가 원본")이 `MerchantOffers`에까지 적용되어야 해서 범위가 폭발한다.

예외 하나. **promote는 새 엔티티를 만든다.** 여기서 옮기지 않은 것은 왕복마다 전부 파괴된다.

- `Resident`에 `CompoundTag vanillaState` **하나만** 예외적으로 들고 있는다
- 내용은 **주민 엔티티의 NBT 전체**다. `villager.saveWithoutId()`로 뜨고 `villager.load()`로 되돌린다 — 청크 언로드/로드가 하는 것과 같다
- 이것은 상태가 아니라 **불투명한 blob**이다. 시뮬레이션이 열어보지 않는다
- 복원 후 시뮬레이션이 소유한 값(체력, 좌표)만 레코드에서 덮어쓴다

### 필드를 손으로 옮기지 않는다

이 결론은 비싸게 얻었다. 처음엔 체력과 거래만 옮겼다. 그다음 `VillagerData`가 필요했고, 그다음 작업장과 XP가 필요했다. 매 라운드마다 게임 테스트가 빠뜨린 것을 하나씩 찾아냈다.

| 빠뜨린 것 | 증상 |
|---|---|
| `VillagerData` | 농부가 무직으로 돌아온다 |
| 순서 (`setVillagerData`가 거래를 지움) | 복원한 거래가 그 자리에서 삭제된다 |
| `JOB_SITE` 기억 + XP | 바닐라 `ResetProfession`이 **몇 초 만에 해고한다** |

마지막이 결정적이다. 바닐라 주민은 필드 몇 개가 아니라 **기억·POI 점유·가십·인벤토리가 얽힌 브레인**이다. 손으로 재현하는 것은 청크 로딩을 조잡하게 다시 만드는 일이다.

**전부 저장하는 편이 안전하다.** 동기화할 필드 목록이 없으면 빠뜨릴 필드도 없다.

원칙 1의 유일한 예외이며, 그 사실을 코드 주석에 명시한다. 예외를 늘리지 않기 위해 명시가 필요하다.

---

## 철 골렘

유지한다. 제거하면 플레이어가 즉시 알아차리고 방어가 오히려 약해진다.

- 바닐라 스폰 조건(주민 수, 침대, 시간)을 그대로 둔다
- `defenseRating`에 `golemCount * 12`로 포함 (`docs/defense.md`)
- 골렘은 `Resident`가 아니다. 죽어도 연대기에 남지 않는다 — 다만 `BUILD` 대신 별도 항목을 둘지는 M4에서 본다

`TOWN` 이후 골렘이 과해지면 제한을 거는 게 아니라 **`defenseRating` 계수를 낮춘다.** 바닐라 동작을 바꾸지 않고 밸런스만 조정하는 쪽이 항상 낫다.

---

## 좀비 주민

`docs/population.md` 참조. 요약: `Resident`는 남고 `zombified = true`가 된다. 치료하면 이름과 가족을 그대로 갖고 돌아온다.

---

## 플레이어가 주민을 죽이면

특별 취급하지 않는다. 사망 이벤트를 받아 연대기에 원인과 함께 기록한다.

```
밀렌 가의 아나 — Steve에게 살해됨 (방금)
```

`morale`에 마을 전체 페널티를 주고 싶어지는데, V1에서는 **하지 않는다.** 바닐라 가십 시스템이 이미 평판 하락을 처리하고 있고, 두 시스템이 겹치면 플레이어가 무엇 때문에 벌을 받는지 알 수 없게 된다. 원래 문제("왜인지 알 수 없다")를 우리가 다시 만드는 셈이다.

---

## 마을 경계 겹침

등록 시 **기존 클레임과 겹치면 거부한다.** 메시지에 겹치는 마을 이름을 띄운다.

```
등록 실패 — Hearthwood의 클레임과 겹칩니다 (거리 34블록, 최소 필요 96블록)
```

이 규칙 하나가 마을 간 최소 간격을 보장하고, V2의 앵커 블록 중복 방지(`docs/v2-deferred.md`)와 같은 코드를 쓴다.

---

## 기존 세이브 호환

M5의 검증 항목이지만 설계 제약이므로 여기 적는다.

- 모드를 넣기 전 세이브를 열었을 때 **아무 일도 일어나지 않아야 한다.** 등록 전에는 `SavedData`조차 생성되지 않는다
- 모드를 제거했을 때 남는 것: 마을이 지은 블록, `MilitiaEntity`(알 수 없는 엔티티로 사라짐), 부착물(무시됨)
- `MilitiaEntity`가 남아 있는 상태로 모드를 제거하면 그 주민은 사라진다. **README에 명시한다.** 제거 전 `/placitum unregister`를 권장
