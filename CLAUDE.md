# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 저장소 현황

- **골격 단계다(Phase 1 진행 중).** `backend/`(Spring Boot 골격)·`frontend/`(Vite 골격 + 개발 도구 구성)·`docs/api/`·`infra/`·`.env.example`은 있다. `.github/workflows/`(Task 010)와 `frontend/vercel.json`(Task 009)도 있다. 백엔드는 Task 016에서 의존성 전체·프로필(`local`·`prod`)·임시 `SecurityConfig`(전 경로 `permitAll`)·Testcontainers 테스트 베이스를, Task 023에서 `CommonResponse`·`ProblemDetail` 예외 처리·계약 대조 테스트(`OpenApiContractTest`)를 구성했다. Task 019에서 첫 도메인(`provider`: `ott_service` Flyway V1, 서비스 목록 API `GET /api/public/ott-services`)과 오리진 비밀 헤더 필터(`app.origin-secret.enabled`, local만 꺼짐)·진입 IP 로그가 생겼다. 다른 도메인 패키지는 아직 없다. 백엔드는 Task 020에서 Cloudtype 서비스 `ottnavi`(프리티어 1GB)에 올라가 있다(Task 021에서 `develop` → `main` 병합으로 첫 배포를 마쳤고 배포 브랜치는 `main`이다. 운영 프론트는 `ottnavi.vercel.app`. 배포는 콘솔 `배포하기`로 수동이며 GitHub Actions 배포 파일은 없다. 방문자 IP는 `remoteAddr`로 본다(`x-real-ip`는 Vercel IP). 런타임 환경 변수는 `Environment variables`, 프로필은 Start Command의 `-D`로 지정, TECH 6절).
- 진행 상황과 다음 Task는 `docs/ROADMAP.md`(5단계 Phase, 105개 Task)가 기준이다. 새 작업 전에 해당 Task의 선행·완료 기준을 확인한다. 사용자가 직접 하는 `[H]` Task는 `docs/PHASE1_HUMAN_TASKS.md`에 체크리스트로 따로 있다.
- Task 진행은 쉬림프 태스크 매니저(MCP)로 추적한다. 데이터는 `shrimp_data/`(git 제외)에 있고, 경로는 `.claude/settings.local.json`의 `SHRIMP_DATA_DIR`이 정한다(`.mcp.json`이 `${SHRIMP_DATA_DIR}`로 참조). 로드맵이 기준이고 쉬림프는 보조다.
- 사용자 전역 규칙(`~/.claude/CLAUDE.md`)이 적용된다: 주석·로그·에러 메시지·UI 문자열은 한국어, 식별자는 영어, 커밋은 한국어 Conventional Commits, 터미널 명령은 PowerShell 기준(Windows 11), 탐색 → 계획 제시 → 승인 → 구현 순서.

## 제품 개요

OTT내비(ottnavi): 찜한 작품 + 월 예산·월 시청 시간을 바탕으로 국내 OTT 7곳 중 어느 달에 어디를 구독할지 **3개월 롤링 플랜**을 계산하고, 매월 재계산 결과를 이메일로 알리는 비영리 서비스. 데이터 출처는 TMDB(JustWatch 기반)이며 화면마다 출처를 표기한다. 백엔드는 사용자가 직접 작성하고, 프론트는 Claude가 작성한다(사용자는 흐름·디자인·인증 코드를 검토).

## 문서 체계 (근거 우선순위)

PRD 11절 확정 → PRD 본문 → ERD → rules. 문서끼리 어긋나면 임의로 고르지 말고 사용자에게 올린다.

| 문서 | 역할 |
|---|---|
| `docs/PRD.md` | 요구사항·계산 규칙(5.4)·개발 단계·결정 현황(11절, `R11-N`)의 단일 기준 |
| `docs/ERD.md` | 스키마·상태값·삭제 정책 |
| `docs/TECH.md` | rules에 없는 설계 결정과 함정(`T-N`, 계산 엔진·플랜 저장·배치·인증·outbox·호환성) |
| `docs/ROADMAP.md` | Phase/Task 진행표, 검증 절차(V-F, V-B, V-API, V-FS, V-DEPLOY, V-H) |
| `docs/TASK003_POLICY_PRICING.md` | Task 003 조사 기록: TMDB 약관, 서비스 해지 정책(R11-27), Cloudtype·Supabase·Redis Cloud 무료 플랜 한도와 요금. 출처·확인 수준 표시 |
| `docs/proposal_v6.md` | 요약본. 근거로 쓰지 않는다 |
| `docs/api/openapi.yaml`, `docs/api/error-codes.md` | API 계약과 오류 코드표. operation은 서비스 목록(`listOttServices`, Task 019) 하나이고 나머지는 Task 031부터 추가된다 |
| `docs/PHASE1_HUMAN_TASKS.md` | Phase 1의 사람 작업 `[H]` 체크리스트와 완료 기록 |
| `.claude/rules/backend.md` | `backend/**` 작업 시 자동 로드. Java/Spring 스타일·스택·패키지 구조 |
| `.claude/rules/frontend.md` | `frontend/**` 작업 시 자동 로드. React/TS 스타일·상태 관리·인증 규칙 |
| `.claude/rules/api-contract.md` | OpenAPI 계약 변경 절차, 응답·오류·페이지네이션 형식 |
| `.claude/rules/git.md` | 브랜치·커밋·병합 규칙 |

`.claude/agents/`에는 문서 작성용 에이전트(prd-writer, tech-writer, roadmap-writer, roadmap-reviewer)와 PR 코드래빗 지적 정리용 읽기 전용 에이전트(coderabbit-triage)가, `.claude/skills/`에는 git 작업 스킬(git-branch, git-commit, git-pr, git-merge, git-review)이 있다. MCP는 `.mcp.json`에 shadcn, shrimp-task-manager가 설정돼 있고, 로드맵은 쉬림프 태스크 매니저가 더 세분화할 것을 전제로 한다.

## 목표 아키텍처 (big picture)

모노레포: `backend/`(Spring Boot) + `frontend/`(Vite SPA) + `infra/`(docker-compose). 배포는 **Vercel → `/api` rewrite → Cloudtype(Spring) → Supabase(PostgreSQL)**, Redis Cloud, TMDB.

**API 계약이 두 쪽을 묶는다.** `docs/api/openapi.yaml`이 유일한 계약이다. 변경 순서는 계약 수정 → 프론트 `npm run api:generate`(orval이 타입·TanStack Query 훅·MSW 목업 생성, `src/api/generated/`는 수정 금지) → 백엔드 구현. springdoc 생성 명세와 `openapi.yaml`이 다르면 CI 실패(`x-planned: true`인 미구현 operation은 제외). 계약 변경과 코드 변경은 같은 PR에 담는다. UI를 먼저 목업(MSW)으로 세우고 API를 하나씩 실제로 교체하는 UI 선행 방식이다.

**백엔드 패키지(`com.ottnavi`)**: `global`(config·security·ratelimit·error·common) / `infra`(tmdb·mail·llm 외부 연동) / 도메인별(`catalog`, `provider`, `product`, `user`, `wishlist`, `plan`, `notification` — 각각 `api` → `application` → `domain` 단방향) / `engine` / `collect` / `admin`. 다른 도메인의 리포지토리를 직접 쓰지 않고 서비스를 호출한다.

**계산 엔진(`engine`)**: 순수 Java(프레임워크 의존 금지). `plan` 도메인만 호출한다. `ExactSolver`(가지치기 완전탐색)와 `GreedySolver`가 `PlanSolver` 인터페이스를 공유하고, 후보 조합 수가 상한을 넘으면 그리디로 전환한다. 목적함수는 사전식(꼭 볼 작품 완료 수 → 점수 → 총비용 → 상품 ID 순). 규칙 구현부에는 PRD 5.4의 어느 규칙인지 주석을 남긴다. 상세는 TECH 1절.

**플랜 상태**: DRAFT(계산 결과) → ACTIVE(저장) → ARCHIVED. 사용자당 DRAFT·ACTIVE는 각각 최대 1개(부분 유니크 인덱스). 활성화 시 **기존 ACTIVE를 ARCHIVED로 바꾸고 명시적 flush한 뒤** 새 ACTIVE로 전환해야 인덱스 위반이 나지 않는다(TECH 2절). 삭제는 FK `ON DELETE CASCADE` + 벌크 JPQL(JPA cascade 삭제 미사용, TECH T-1).

**수집 배치(`collect`)**: Spring Batch Job을 GitHub Actions cron이 관리자 API로 호출(202 비동기, 같은 `targetDate`+`tier`는 409). cron은 Vercel을 거치지 않고 Cloudtype에 직접 호출하며 `x-origin-secret` + 관리자 배치 토큰을 보낸다. Cloudtype 무료 기간에는 cron을 끈다(TECH 3절).

**인증**: Google OAuth2 → JWT. Access Token은 프론트 메모리(Zustand)에만, Refresh Token은 HttpOnly 쿠키(`Path=/api/auth`)로 회전한다. OAuth 경로를 `/api/oauth2/**`, `/api/login/**` 아래로 옮기고 인가 요청은 쿠키 저장소를 쓴다(TECH 4절).

**API 경로 3분류**: `/api/public/**`(비로그인, 요청 제한) / `/api/**`(로그인) / `/api/admin/**`(ADMIN). 예외로 `/api/auth/**`, `/api/oauth2/**`, `/api/login/**`. 본인 데이터는 `/api/me/**`. 성공은 `CommonResponse` 래퍼, 오류는 `ProblemDetail`(`errorCode` 필드, SCREAMING_SNAKE_CASE).

**프론트 구조**: feature 단위(`src/features/{기능}`), `pages/`는 얇게, 기능 폴더끼리 import 금지, 기능별 `api/` 폴더 없음. 서버 상태는 TanStack Query, Zustand는 인증만, 디자인 값은 `src/styles` 토큰만 쓴다.

## 개발 명령 (ROADMAP 4.1·4.5 기준)

```powershell
docker compose -f infra/docker-compose.yml up -d                      # 로컬 PostgreSQL 17.6·Redis 8.6 (포트 127.0.0.1만)
cd backend; .\gradlew.bat build                                        # 백엔드 빌드·테스트 (Testcontainers라 Docker Desktop 필요)
cd backend; .\gradlew.bat test --tests "com.ottnavi.OttnaviApplicationTests"   # 단일 테스트 (클래스·메서드 이름으로 바꿔 쓴다)
cd backend; .\gradlew.bat bootRun --args="--spring.profiles.active=local"   # 실행 (localhost:8080). local이 기본 프로필이라 `--args` 없이도 되며 compose의 DB·Redis가 떠 있어야 한다
cd frontend; npm run dev                                               # 프론트 (VITE_USE_MOCK=true면 MSW 목업, frontend/.env.local에 둔다)
cd frontend; npm run lint                                              # 프론트 검증 4종은 각각 따로 실행해 종료 코드를 확인한다(모두 통과 상태 유지).
cd frontend; npm run format:check                                      # PowerShell에서 ;로 이으면 앞 명령의 실패가 뒤 명령 성공에 가려진다
cd frontend; npm run test
cd frontend; npm run build
cd frontend; npx vitest run src/path/to.test.tsx                       # 프론트 단일 테스트 파일
cd frontend; npm run api:generate                                      # openapi.yaml → orval 생성
```

- `docker compose`는 `.env`를 `infra/` 기준으로 찾아 루트 `.env`를 읽지 않는다. 접속 정보는 환경 변수 + 로컬 전용 기본값이라 `.env` 없이 동작하고, 필요하면 `--env-file .env`를 붙인다.
- 백엔드 통합 테스트는 Docker Desktop이 켜져 있어야 한다(Testcontainers, Task 016부터). 테스트에서 H2를 쓰지 않는다(PostgreSQL 전용 기능 검증 불가). TMDB는 WireMock으로만 호출한다.
- 프론트 UI 변경은 Playwright MCP로 브라우저 확인(V-F, 콘솔 에러 0건)까지 해야 완료다.
- 프론트 린트·포맷 제외: `src/components/ui`(shadcn 원본)는 `react-refresh/only-export-components` 규칙을 끄고 Prettier 대상에서 뺀다. `src/api/generated/`, `public/mockServiceWorker.js`, `src/lib/utils.ts`, `src/index.css`도 Prettier 대상이 아니다(`frontend/.prettierignore`). 생성물은 직접 고치지 않는다.

## 작업 시 지킬 것

- **Git**: `main`·`develop`에는 직접 커밋하지 않는다(`develop`은 2026-10-04에 생성됐고, 초기 세팅 Task 006·007·011·017 일부만 그 전에 `main`에 직접 커밋했다). 새 작업은 브랜치부터 확인하고, feature는 `develop`에서, hotfix는 `main`에서 `{종류}/{작업ID}-{설명}`으로 만든다(문서만 고쳐도 `feature/docs-*`). **커밋·푸시·PR·병합·태그·브랜치 삭제는 사용자가 요청할 때만** 한다. 커밋 scope는 `backend|frontend|docs|infra|ci`. 커밋에 Claude 서명(`Co-Authored-By`)은 넣지 않는다(`/git-commit` 스킬 규칙).
- **PR**: `gh pr create --base develop`으로 만든다(base를 안 쓰면 `main`이 잡힌다). 병합은 Squash. `/git-pr`·`/git-review` 같은 일부 스킬은 `disable-model-invocation`이라 사용자가 직접 호출해야 한다. PR마다 CodeRabbit이 자동 리뷰하며(`.coderabbit.yaml`), **Autofix·Autopilot은 누르지 않는다**.
- 보호 규칙(ruleset `protect-main-develop`)이 `main`·`develop`에 걸려 있다(Task 012): 직접 푸시·삭제·강제 푸시 금지, PR 필수(승인 0), 필수 검사 `backend-ci`·`frontend-ci`. 문서만 고쳐도 PR이 필요하다.
- **Flyway**: 스키마는 마이그레이션으로만 바꾼다. 병합된 파일은 고치지 않고 새 버전을 추가한다(MVP는 V1~V7 번호가 Task에 배정됨, ROADMAP 4.2).
- **외부 호출(TMDB·메일·LLM)은 트랜잭션 밖**에서 한다(수집 배치만 예외, TECH T-2).
- **버전 함정**: Spring Boot 4.1은 Jackson 3(`tools.jackson.*`), `@MockitoBean`, `spring-boot-starter-flyway`·`spring-boot-starter-batch-jdbc` 필요, orval은 `httpClient: 'axios'` 명시 필요, msw 3은 `onUnhandledRequest`가 아니라 `onUnhandledFrame`, TypeScript 6은 `baseUrl` 없이 `paths`만 쓴다. 전체 목록은 TECH 6절.
- **설정 파일은 YAML로 통일**한다(`application.yml`, `-local.yml`, `-prod.yml`). `.properties`와 섞지 않는다.
- **화면 표시**: 제공 상태는 있음/없음/모름 3가지를 색만으로 구분하지 않고, 제공처 화면엔 JustWatch 출처, About엔 TMDB 로고·고지문을 반드시 넣는다. 결과는 "확정"이 아닌 "확인된 범위"로 표현하고 데이터 기준일을 보여준다.
