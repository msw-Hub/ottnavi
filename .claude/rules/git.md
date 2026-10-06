> ottnavi의 브랜치 전략과 커밋·병합 규칙이다. Git Flow를 1인 개발에 맞게 줄였다(release 브랜치 없음).
> 커밋·푸시·병합·브랜치 삭제는 사용자가 요청할 때만 한다.

## 브랜치

| 브랜치 | 역할 | 만드는 곳 | 합치는 곳 | 배포 |
|---|---|---|---|---|
| `main` | 배포된 상태. 초기 세팅 이후에는 직접 커밋하지 않는다 | — | — | 병합 시 운영 배포(Cloudtype, Vercel Production) |
| `develop` | 개발 통합. 항상 로컬에서 문제없이 기동되는 상태를 유지한다 | `main` (초기 세팅 직후 1회) | `main` (배포할 때) | 배포하지 않음. Vercel은 미리보기만 생긴다 |
| `feature/*` | 기능 개발 | `develop` | `develop` | 없음 |
| `hotfix/*` | 운영 장애·긴급 수정 | `main` | `main`과 `develop` 둘 다 | `main` 병합 시 운영 배포 |

- **초기 세팅**: 저장소 생성, 폴더 골격, 문서(`docs/`, `.claude/`), CI 골격까지는 `main`에 바로 커밋한다. 그 직후 `main`에서 `develop`을 만들고, 이후 모든 개발은 `develop` 기준으로 진행한다.
- `main`과 `develop`에는 직접 커밋하지 않는다. 항상 작업 브랜치에서 PR로 합친다.

## 브랜치 이름

- 형식: `{종류}/{작업ID}-{짧은-설명}` (소문자 kebab-case, 영문)
- 작업 ID는 로드맵의 큰 일 ID(B3, F5 등) 또는 태스크 매니저 ID를 쓴다. 없으면 생략한다.
- 예: `feature/b3-tmdb-collect-job`, `feature/f1-screen-skeleton`, `hotfix/oauth-redirect-uri`
- 문서만 고치는 작업도 `feature/`를 쓴다 (예: `feature/docs-tech-decisions`).

## 작업 흐름

**기능 개발 (feature)**
1. `develop`을 최신으로 받은 뒤 `feature/*`를 만든다.
2. 작업 단위로 커밋한다.
3. `develop` 대상 PR을 연다. CI(빌드·테스트)가 통과해야 병합한다.
4. **Squash merge**로 병합한다. 브랜치 삭제는 별도로 요청을 받은 경우에만 한다(아래 Claude 작업 규칙). `develop` 이력은 기능 단위 커밋 하나씩 남는다.

**배포 (develop → main)**
1. `develop`이 로컬 기동, CI 통과, 해당 단계 완료 기준을 만족하면 `main` 대상 PR을 연다.
2. **Merge commit**으로 병합한다(이력 보존). 병합 직후 운영 배포가 자동으로 실행된다.
3. 배포 후 스모크 테스트(로그인, 검색, 계산·저장)를 확인하고 `main`에 태그를 단다.

- **1주차 얇은 배포(B1)도 같은 흐름이다.** 얇은 배포 Task를 `feature/*` → `develop` PR로 합친 뒤 `develop` → `main` PR(merge commit)을 열어 병합하면 그것이 첫 배포다. `backend-deploy.yml`은 `main` push에서 실행되고, 이 병합 때 Vercel Production도 함께 배포된다. 이 용도로 `main` 직접 커밋이나 `develop` 수동 배포 예외를 두지 않는다. 이때는 위 1번의 "해당 단계 완료 기준"을 "얇은 배포 Task의 완료 기준"으로 읽는다.

**긴급 수정 (hotfix)**
1. `main`에서 `hotfix/*`를 만든다.
2. 수정 후 `main` 대상 PR을 열어 병합한다(Merge commit). 운영 배포가 실행된다.
3. 같은 수정을 `develop`에도 반영한다(`hotfix/*` → `develop` PR 또는 `main` → `develop` 병합). 빠뜨리면 다음 배포 때 수정이 사라진다.
4. 패치 버전 태그를 단다.

## 태그

- `main`에만 단다. 형식: `vMAJOR.MINOR.PATCH`
- MVP 공개 `v0.1.0`, 2단계 완료 `v0.2.0`, 3단계 완료 `v0.3.0`, hotfix는 PATCH를 올린다(`v0.1.1`).

## 커밋 메시지

- 한국어, Conventional Commits, 이모지 없음.
- 형식: `{type}({scope}): {요약}` — scope는 `backend`, `frontend`, `docs`, `infra`, `ci` 중 하나 또는 생략.
- type: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `ci`, `build`, `perf`, `style`
- 예: `feat(backend): TMDB 후보 수집 Job 추가`, `fix(frontend): 토큰 재발급 중복 요청 수정`
- Squash merge 커밋 메시지도 같은 형식으로 쓰고, PR 번호를 끝에 붙인다(예: `(#12)`).

## 보호 규칙 (GitHub 설정)

- `main`, `develop`: 직접 푸시 금지, PR 필수, CI 통과 필수, 강제 푸시·삭제 금지.
- 1인 개발이라 리뷰 승인 필수는 두지 않는다.

## PR과 자동 리뷰

- PR은 `gh` CLI로 만든다(`gh pr create --base develop`). base를 지정하지 않으면 기본 브랜치(`main`)가 잡히므로 반드시 `--base develop`을 쓴다.
- PR 자동 리뷰는 CodeRabbit을 쓴다. 설정은 루트 `.coderabbit.yaml`이다: `develop` 대상 PR 리뷰(`base_branches`), 한국어(`ko-KR`), 생성 파일 제외(`path_filters`). 코멘트의 제목·안내문은 영어로 나온다.
- CodeRabbit의 **Autofix·Autopilot 체크박스는 누르지 않는다.** PR 브랜치에 자동으로 커밋을 올려 "커밋·푸시는 사용자가 요청할 때만" 규칙과 어긋난다. 지적은 사람이 판단해 직접 반영한다.
- 코드래빗 지적을 읽을 때는 `coderabbit-triage` 에이전트에 PR 번호를 넘겨 표로 받는다(읽기 전용, 원문 로그를 메인 컨텍스트에 쌓지 않기 위함). 한 코멘트에 지적이 여러 개 들어갈 수 있고 "Addressed" 표시와 해결 표시는 실제와 다를 수 있으니, 에이전트도 메인도 현재 코드로 확인한다. 에이전트 파일은 **체크아웃한 브랜치의 `.claude/agents/`** 에서 로드되므로, 에이전트가 추가된 커밋을 포함하지 않는 오래된 브랜치에서는 호출할 수 없다. `develop`을 받은 브랜치에서 호출한다.
- 푸시할 때마다 재리뷰가 돌고 시간당 포함된 리뷰 수에 한도가 있으므로(확인 시점 10건), 커밋을 모아서 푸시한다.
- 병합은 사용자가 요청할 때 Squash로 한다(`gh pr merge --squash`). 병합 후 브랜치 삭제는 별도로 요청받았을 때만 한다.
- 문서만 고치는 작업도 `feature/docs-*` 브랜치와 PR로 한다.

## 배포·CI와의 연결

- `backend-ci.yml`, `frontend-ci.yml`: `develop`·`main` 대상 PR과 두 브랜치 push에서 실행한다.
- `backend-deploy.yml`(Cloudtype): `main` push에서만 실행한다.
- Vercel: Production Branch를 `main`으로 설정한다. `develop`과 PR은 미리보기 배포만 생긴다.
- 수집·알림 cron 워크플로는 기본 브랜치(`main`)의 워크플로 파일 기준으로 실행된다. cron 설정을 바꾸면 `main`까지 반영돼야 적용된다.

## Claude 작업 규칙

- 새 작업을 시작할 때 현재 브랜치를 확인하고, `main`이나 `develop`이면 위 규칙대로 작업 브랜치를 먼저 만든다.
- feature는 `develop`에서, hotfix는 `main`에서 만든다.
- 커밋, 푸시, PR 생성, 병합, 브랜치 삭제, 태그는 사용자가 요청할 때만 한다.
- hotfix를 `main`에 병합했다면 `develop` 반영이 남아 있는지 사용자에게 알린다.
