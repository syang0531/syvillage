# Placitum

마인크래프트 자바 에디션 모드. 바닐라 마을을 **스스로 성장하고 스스로 방어하는 정주지**로 바꾼다.

> *placitum* — 카롤링거 시대의 공개 집회. 영주와 자유민이 모여 안건을 다루던 자리.

현재 상태: **M0 착수 전.** 빌드 스캐폴딩과 배포 파이프라인만 있고 게임 내 기능은 아직 없다.

---

## 해결하려는 문제

바닐라 마을의 세 가지 결함. 이 셋이 V1의 전체 범위다.

1. 주민이 어느 순간 전멸해 있고, 왜 죽었는지 알 수 없다
2. 인구가 늘지 않는다
3. 마을 규모(집 수)가 늘지 않는다

이 셋은 하나의 고리다. **안전 → 인구 → 건물 → 수용력 → 인구.** 하나라도 빠지면 고리가 돌지 않는다.

## 어떻게

핵심은 **LOD 시뮬레이션**이다. 플레이어가 없는 마을은 엔티티 없이 데이터로만 존재하고, 필요할 때 몰아서 정산한다(`catchUp`). 강제 청크 로딩은 기본값 0이다.

마을 등록은 **종 우클릭 한 번**. 등록되지 않은 바닐라 마을은 코드를 한 줄도 타지 않으므로 기존 세이브가 안전하고 성능 상한이 플레이어 손에 있다.

| 항목 | 값 |
|---|---|
| Minecraft | 26.2 (Java Edition) |
| 모드로더 | NeoForge 26.2.0.88 |
| Java | 25 |
| modid | `placitum` |
| 배포 | CurseForge — [PUBLISHING.md](PUBLISHING.md) |

```bash
./gradlew build        # 빌드
./gradlew runClient    # 클라이언트 실행
./gradlew test         # 왕복 무결성 / 정산 등가성 테스트
```

---

## 문서

작업을 시작하기 전에 `CLAUDE.md`와 `docs/roadmap.md`를 먼저 읽는다.

| 문서 | 내용 |
|---|---|
| [CLAUDE.md](CLAUDE.md) | 불변 원칙, 절대 금지, 코드 규약. **항상 컨텍스트에 들어간다** |
| [docs/architecture.md](docs/architecture.md) | LOD 구조, promote/demote, 부분 로딩, 크래시 복구 |
| [docs/data-model.md](docs/data-model.md) | record 정의, Codec, SavedData 분할 |
| [docs/simulation.md](docs/simulation.md) | `catchUp`, `simStep` 계약, 결정성 |
| [docs/defense.md](docs/defense.md) | 경보 상태 머신, 민병대, 습격 판정 |
| [docs/population.md](docs/population.md) | 수용력, 출산·사망, 연대기 |
| [docs/construction.md](docs/construction.md) | 건설 파이프라인, 플롯 격자, 성벽·집·밭 |
| [docs/vanilla-interop.md](docs/vanilla-interop.md) | 등록 시 주민 흡수, 거래·골렘·좀비 주민, 세이브 호환 |
| [docs/commands-and-config.md](docs/commands-and-config.md) | 명령어 사양, 설정 키 |
| [docs/roadmap.md](docs/roadmap.md) | 마일스톤과 완료 기준 |
| [docs/testing.md](docs/testing.md) | 검증 항목 |
| [docs/open-questions.md](docs/open-questions.md) | 미해결 문제 |
| [docs/v2-deferred.md](docs/v2-deferred.md) | 축2 설계 — **구현 금지**, 훅만 |

`CLAUDE.md`는 매 요청마다 컨텍스트에 들어가므로 **의도적으로 짧게** 유지한다. 길어지면 지시가 희석된다. 상세 내용은 `docs/` 아래에 넣고 색인에 한 줄만 추가한다.

---

## 주의

핵심 API는 **26.2 소스에서 직접 확인했고** 결과가 `docs/architecture.md`의 "Mojang / NeoForge 접점" 절에 있다. 그 표 밖의 클래스명은 아직 1.21.x 기준 참고값이므로 쓰기 전에 확인한다. 문서와 실제가 다르면 **실제를 따르고 문서를 고친다.**

`docs/v2-deferred.md`(왕국·봉신·세금·전쟁)는 구현 대상이 아니다. V1 작업 중 왕국 관련 코드를 만들지 않도록 명시해 둔 문서이며, 동시에 V1에 미리 넣어야 할 훅 목록이기도 하다.

## 진행 상황

- [ ] **M0** 데이터 레이어 — record + Codec, SavedData, 등록, `catchUp`, promote/demote
- [ ] **M1** 안전 — 야간 대피, 경보 상태, 민병대, L2 습격 공식
- [ ] **M2** 인구 — 수용력, 출산, 기아, 연대기
- [ ] **M3** 건설 — 파이프라인, 성벽 → 집 → 밭
- [ ] **M4** 규모 단계 — 승격/강등, 티어별 해금, 이주
- [ ] **M5** 정리와 배포

완료 기준은 `docs/roadmap.md`에 있다. **기준을 통과하기 전에 다음으로 넘어가지 않는다.**
