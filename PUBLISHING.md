# SY Village 배포 가이드 (CurseForge)

**한 번만 준비(A)** 해두면 이후에는 **태그만 push하면 자동 배포(B)** 됩니다.

버전 정보: Minecraft **26.2** / NeoForge **26.2.0.88** / **Java 25**. 다른 마인크래프트 버전을 쓰는 워크플로에서 JDK 설정을 그대로 복사하지 마세요.

---

## A. 최초 1회 준비 (웹에서 직접)

### A-1. CurseForge 프로젝트 생성

폼에 넣을 내용은 [`docs/curseforge/등록정보.md`](docs/curseforge/등록정보.md)에 필드별로 정리되어 있습니다. 로고는 [`docs/curseforge/logo.png`](docs/curseforge/logo.png).

1. https://console.curseforge.com → **Create Project**
2. **Game**: Minecraft / **Project Type**: Mods / **Name**: SY Village
3. **License**: MIT (`LICENSE`, `gradle.properties`의 `mod_license`와 일치시킬 것)
4. Summary·Description·카테고리는 등록정보 문서에서 복붙
5. 생성 후 운영진 **승인 대기** 상태가 됩니다 (몇 시간~며칠). 승인 전에도 아래 단계는 진행 가능하므로 **지금 신청해두는 편이 낫습니다**
6. 프로젝트 페이지에서 **숫자 Project ID** 확인

> 등록정보 문서의 본문은 2026-09-18에 출시 범위(M1~M6)에 맞춰 다시 썼습니다.

### A-2. `gradle.properties` 채우기
```properties
curseforge_project_id=1234567
mod_homepage=https://www.curseforge.com/minecraft/mc-mods/<실제-slug>
```
`curseforge_project_id`가 `000000`인 채로 태그를 밀면 업로드 단계에서 실패합니다.

### A-3. API 토큰 발급
https://console.curseforge.com → 계정 메뉴 → **API Tokens** → 새 토큰 생성 후 값 복사 (한 번만 보여줍니다).

### A-4. GitHub Secret 등록
`syang0531/syvillage` → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**

- **Name**: `CURSEFORGE_TOKEN`
- **Secret**: A-3의 토큰 값

---

## B. 새 버전 배포

```bash
# 1) CHANGELOG.md 맨 위에 "## 0.1.1" 구간 추가
# 2) gradle.properties의 mod_version 갱신 (CI가 태그에서 다시 뽑으므로 표시용)
git add -A
git commit -m "Release 0.1.1"
git push
git tag v0.1.1
git push origin v0.1.1
```

`v*` 태그가 올라가면 `.github/workflows/release.yml`이 돕니다.

1. 태그에서 버전(`0.1.1`)을 뽑아 `./gradlew build -Pmod_version=0.1.1`
2. `CHANGELOG.md`에서 `## 0.1.1` 구간을 뽑아 변경 내역으로 사용 (구간이 없으면 기본 문구로 대체)
3. `./gradlew publishCurseForge`로 업로드
4. jar와 변경 내역이 담긴 GitHub Release 생성

진행 상황은 저장소 **Actions** 탭에서 볼 수 있습니다.

### 로컬에서 업로드 테스트
```bash
CURSEFORGE_TOKEN=xxxx RELEASE_TYPE=beta ./gradlew publishCurseForge --no-configuration-cache
```

---

## 배포 전 점검

`docs/roadmap.md` M6의 완료 기준과 중복되지만, 배포 직전에 반드시 확인할 것만 추립니다.

- [x] `curseforge_project_id`가 실제 값인가 — `1696659`
- [x] CurseForge에 게임 버전 `26.2`가 있는가 — 있다 (2026-09-18 확인). 없으면 `addGameVersion`이 실패한다
- [x] `mod_version`과 태그가 일치하는가 — CI가 태그에서 뽑으므로 어긋날 수 없다
- [x] **모드를 제거했을 때 남는 것**을 CurseForge 설명에 적었는가 — 마을이 지은 것은 전부 평범한 블록이라 그대로 남고, 이 모드의 블록은 탁자 둘·수호상뿐. 주민은 바닐라 주민이라 아무 일도 없다
- [x] 등록되지 않은 바닐라 마을이 완전히 바닐라로 동작하는가 — 등록된 정주지만 틱을 탄다
- [ ] 전용 서버에서 클라이언트 크래시가 없는가 — 아직 안 해봤다. 서버 전용 코드가 없어 위험은 낮지만, 첫 릴리스 뒤 한 번 볼 것
- [x] `logo.png`를 `src/main/resources/`에 넣고 `neoforge.mods.toml`의 `logoFile`을 되살렸는가
- [x] `CURSEFORGE_TOKEN` 시크릿이 등록돼 있는가 — 저장소 소유자가 직접. 평문으로 노출된 적 있는 토큰은 재발급. **값이 비어 있으면** 실행 로그의 env에 `***` 대신 공백이 찍히고 CurseForge가 401을 낸다 — 첫 업로드가 그렇게 두 번 실패했다
- [x] 파일에 환경 태그(Client/Server)가 붙는가 — `build.gradle`의 `addEnvironment`. 없으면 CurseForge가 error 1021로 거부한다
