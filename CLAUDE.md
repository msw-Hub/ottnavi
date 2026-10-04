# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 저장소 현황

- **아직 코드가 없다.** 현재 저장소에는 문서(`docs/`)와 Claude 설정(`.claude/`)만 있다. `backend/`, `frontend/`, `infra/`, `.github/`, `docs/api/`, `.env.example`, `vercel.json`은 모두 **미생성**이며 `docs/ROADMAP.md`의 Task(Phase 1)에서 만든다. 따라서 빌드·린트·테스트 명령은 아직 없다.
- 진행 상황과 다음 Task는 `docs/ROADMAP.md`(5단계 Phase, 105개 Task)가 기준이다. 새 작업 전에 해당 Task의 선행·완료 기준을 확인한다.
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
| `docs/proposal_v6.md` | 요약본. 근거로 쓰지 않는다 |
| `.claude/rules/backend.md` | `backend/**` 작업 시 자동 로드. Java/Spring 스타일·스택·패키지 구조 |
| `.claude/rules/frontend.md` | `frontend/**` 작업 시 자동 로드. React/TS 스타일·상태 관리·인증 규칙 |
| `.claude/rules/api-contract.md` | OpenAPI 계약 변경 절차, 응답·오류·페이지네이션 형식 |
| `.claude/rules/git.md` | 브랜치·커밋·병합 규칙 |

`.claude/agents/`에는 문서 작성용 에이전트(prd-writer, tech-writer, roadmap-writer, roadmap-reviewer)가, `.claude/skills/`에는 git 작업 스킬(git-branch, git-commit, git-pr, git-merge, git-review)이 있다. MCP는 `.mcp.json`에 shadcn, shrimp-task-manager가 설정돼 있고, 로드맵은 쉬림프 태스크 매니저가 더 세분화할 것을 전제로 한다.

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

## 예정된 개발 명령 (파일 생성 후 사용, ROADMAP 4.1·4.5 기준)

```powershell
docker compose -f infra/docker-compose.yml up -d                      # 로컬 PostgreSQL·Redis
cd backend; .\gradlew.bat bootRun --args="--spring.profiles.active=local"   # 백엔드 (localhost:8080)
cd backend; .\gradlew.bat test                                         # 테스트 (Docker Desktop 필요, Testcontainers)
cd backend; .\gradlew.bat test --tests "com.ottnavi.…"                 # 단일 테스트
cd frontend; npm run dev                                               # 프론트 (VITE_USE_MOCK=true면 MSW 목업)
cd frontend; npm run api:generate                                      # openapi.yaml → orval 생성
```

- 테스트에서 H2를 쓰지 않는다(PostgreSQL 전용 기능 검증 불가). TMDB는 WireMock으로만 호출한다.
- 프론트 UI 변경은 Playwright MCP로 브라우저 확인(V-F, 콘솔 에러 0건)까지 해야 완료다.

## 작업 시 지킬 것

- **Git**: `main`·`develop`에는 직접 커밋하지 않는다(초기 세팅 Task 006~011 제외). 새 작업은 브랜치부터 확인하고, feature는 `develop`에서, hotfix는 `main`에서 `{종류}/{작업ID}-{설명}`으로 만든다. **커밋·푸시·PR·병합·태그·브랜치 삭제는 사용자가 요청할 때만** 한다. 커밋 scope는 `backend|frontend|docs|infra|ci`.
- 현재 `develop` 브랜치가 아직 없다(Task 012에서 생성). 그 전의 초기 세팅·문서 커밋은 `main`에 직접 한다.
- **Flyway**: 스키마는 마이그레이션으로만 바꾼다. 병합된 파일은 고치지 않고 새 버전을 추가한다(MVP는 V1~V7 번호가 Task에 배정됨, ROADMAP 4.2).
- **외부 호출(TMDB·메일·LLM)은 트랜잭션 밖**에서 한다(수집 배치만 예외, TECH T-2).
- **버전 함정**: Spring Boot 4.1은 Jackson 3(`tools.jackson.*`), `@MockitoBean`, `spring-boot-starter-flyway`·`spring-boot-starter-batch-jdbc` 필요, orval은 `httpClient: 'axios'` 명시 필요. 전체 목록은 TECH 6절.
- **화면 표시**: 제공 상태는 있음/없음/모름 3가지를 색만으로 구분하지 않고, 제공처 화면엔 JustWatch 출처, About엔 TMDB 로고·고지문을 반드시 넣는다. 결과는 "확정"이 아닌 "확인된 범위"로 표현하고 데이터 기준일을 보여준다.
