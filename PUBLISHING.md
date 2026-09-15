# Placitum 배포 가이드 (CurseForge)

`yame`·`sian`과 동일한 구조입니다. **한 번만 준비(A)** 해두면 이후에는 **태그만 push하면 자동 배포(B)** 됩니다.

버전 정보: Minecraft **26.2** / NeoForge **26.2.0.88** / **Java 25**. 형제 모드(1.21.1 / Java 21)와 다르므로 워크플로의 JDK 버전을 따라 복사하지 마세요.

---

## A. 최초 1회 준비 (웹에서 직접)

### A-1. CurseForge 프로젝트 생성

폼에 넣을 내용은 [`docs/curseforge/등록정보.md`](docs/curseforge/등록정보.md)에 필드별로 정리되어 있습니다. 로고는 [`docs/curseforge/logo.png`](docs/curseforge/logo.png).

1. https://console.curseforge.com → **Create Project**
2. **Game**: Minecraft / **Project Type**: Mods / **Name**: Placitum
3. **License**: MIT (`LICENSE`, `gradle.properties`의 `mod_license`와 일치시킬 것)
4. Summary·Description·카테고리는 등록정보 문서에서 복붙
5. 생성 후 운영진 **승인 대기** 상태가 됩니다 (몇 시간~며칠). 승인 전에도 아래 단계는 진행 가능하므로 **지금 신청해두는 편이 낫습니다**
6. 프로젝트 페이지에서 **숫자 Project ID** 확인

> 등록정보 문서의 본문은 **V1 계획 기준**입니다. 첫 파일 업로드 직전에 실제 출시 범위와 대조해 고치세요.

### A-2. `gradle.properties` 채우기
```properties
curseforge_project_id=1234567
mod_homepage=https://www.curseforge.com/minecraft/mc-mods/<실제-slug>
```
`curseforge_project_id`가 `000000`인 채로 태그를 밀면 업로드 단계에서 실패합니다.

### A-3. API 토큰 발급
https://console.curseforge.com → 계정 메뉴 → **API Tokens** → 새 토큰 생성 후 값 복사 (한 번만 보여줍니다).

### A-4. GitHub Secret 등록
`syang0531/placitum` → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**

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

`docs/roadmap.md` M5의 완료 기준과 중복되지만, 배포 직전에 반드시 확인할 것만 추립니다.

- [ ] `curseforge_project_id`가 실제 값인가
- [ ] `mod_version`과 태그가 일치하는가
- [ ] **모드를 제거했을 때 남는 것**을 CurseForge 설명에 적었는가 — `MilitiaEntity`가 남은 상태로 제거하면 그 주민은 사라집니다. 제거 전 `/placitum unregister` 권장 (`docs/vanilla-interop.md`)
- [ ] 등록되지 않은 바닐라 마을이 완전히 바닐라로 동작하는가
- [ ] 전용 서버에서 클라이언트 크래시가 없는가
- [ ] `logo.png`를 `src/main/resources/`에 넣고 `neoforge.mods.toml`의 `logoFile`을 되살렸는가
