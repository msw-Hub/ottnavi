# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 저장소 현황

- **MVP 구현 중이다(Phase 3 병합 완료, 실서버 확인 대기).** 진행 숫자는 `docs/ROADMAP.md`가 기준이다(2026-10-16: Phase 2 완료, Phase 3는 038~051 병합, 053·054 인증 병합, 055·056 회원 입력 API(내 설정·찜) 병합, 059·060 요금 API 병합, 062·063 플랜 엔티티·입력 조립 병합, 064 플랜 계산 API 병합(PR #71), 065 비교 측정 API 병합(PR #72), 066 플랜 저장·초안 조회·현재 플랜 조회 병합(PR #73), 프론트 실제 API 연결 052·057·058·061·067은 구현·테스트를 마쳐 PR #74로 병합했고(실서버 확인 전) Phase 3은 30/30, Phase 3b 2/3, Phase 4 9/24, 합계 77/108(Task 106은 PR #75, 068은 PR #76, 075는 PR #78, 069는 PR #79, 076은 PR #80, 070은 PR #81, 081은 PR #83, 083은 PR #82, 107은 PR #84로 병합. 082 탈퇴 API는 구현·테스트 완료·병합 전). 실서버·실제 Google 로그인 확인은 사용자 몫. **2026-10-17 범위 개정**: 첫 공개 `v0.1.0`에 변경 내역(068~070)·번들·독점(075~079)·탈퇴(080~084)·정리(089·090·101)와 홈·랭킹·탐색(신설 Task 106~108, Phase 3b)을 포함하고 알림·LLM·관측·성능·정확도 보강은 공개 뒤 3단계로 이월했다. 107(홈·탐색 API, V9 `popularity_snapshot`, 인기 순위 수집 Job `popularityJob`과 공개 조회 6개)은 구현을 마쳤고 다음은 Task 108(프론트 연결)이다(V8은 076이 사용함). 상세는 ROADMAP 3.2, PRD 11절 42번, HANDOFF 0절). 프론트는 목업(MSW) 화면 위에 실제 API 연결(인증·온보딩·찜·관리자·플랜 흐름·공개 화면)을 `feature/f-api-connect`에 구현했다. 백엔드는 스키마(Flyway V1~V9), 엔티티, 계산 엔진, TMDB 클라이언트, 제공 상태 판정·러닝타임 규칙, 수집 Job의 후보 단계와 상세·시즌·제공 상태 판정 단계(`collect`), 수집 실행·복구·재시작·이력·변경 조회 관리자 API(`admin`, ADMIN JWT 또는 배치 토큰으로 보호), 제공처 매핑 관리 API(`provider`), 공개 요청 제한, 작품 검색·상세 API(`catalog`, DB 조회만 하고 단건 수집은 이월, 상세는 Redis `titleDetail` 캐시)까지 있고, 인증(Task 053·054: Google OAuth2 로그인, JWT AT 30분, Redis RT 회전·재사용 탐지, `user`·`global/security`)도 구현됐으며 수집 cron 워크플로(`collect.yml`)는 비활성 상태로 작성돼 있다. 구현된 API는 44개(회원 탈퇴 `DELETE /api/me` 1개(Task 082), 홈·탐색 공개 조회 6개(인기 순위·찜 많은 작품·새로 볼 수 있게 된 작품·장르 목록·장르별 작품·인물별 작품)와 관리자 인기 수집·데이터 정리 시작 2개 포함, 서비스 목록 `GET /api/public/ott-services`, 작품 검색·상세 2개, 수집 관리자 API 5개, 제공처 매핑 관리 API 2개, 토큰 재발급·로그아웃 2개, 내 설정 2개, 찜 4개, 관리자 상품·가격 7개(번들 등록, 저가형 분류 `isLowCost`), 내 서비스 요금 3개(번들·저가형 표시), 플랜 계산·초안 조회·저장·현재 플랜 조회 4개, 변경 내역 조회 1개, 관리자 재계산 실행 1개, 관리자 플랜 비교 측정 1개. 내 설정에는 "가장 저렴한 요금제로 계산" 모드 `isLowestPriceMode`가 들어간다)이고 `x-planned` operation은 없다. 오리진 비밀 헤더 필터(`app.origin-secret.enabled`, local만 꺼짐)·진입 IP 로그가 있다. 백엔드는 Cloudtype 서비스 `ottnavi`(프리티어 1GB)에 올라가 있다(`develop` → `main` 병합으로 첫 배포를 마쳤고 배포 브랜치는 `main`이다. 운영 프론트는 `ottnavi.vercel.app`. 배포는 콘솔 `배포하기`로 수동이며 GitHub Actions 배포 파일은 없다. 방문자 IP는 `remoteAddr`로 본다(`x-real-ip`는 Vercel IP). 런타임 환경 변수는 `Environment variables`, 프로필은 Start Command의 `-D`로 지정, TECH 6절). Supabase Data API는 꺼 둔 상태다. 세션이 바뀌었을 때 이어가는 방법과 미결 항목은 `docs/HANDOFF.md`에 있다.
- 진행 상황과 다음 Task는 `docs/ROADMAP.md`(5단계 Phase와 3b, 108개 Task)가 기준이다. 새 작업 전에 해당 Task의 선행·완료 기준을 확인한다. 사용자가 직접 하는 `[H]` Task는 ROADMAP의 태그로 구분하고, 비밀 값 보관 규칙은 `docs/TECH.md` 8절에 있다.
- Task 진행은 쉬림프 태스크 매니저(MCP)로 추적한다. 데이터는 `shrimp_data/`(git 제외)에 있고, 경로는 `.claude/settings.local.json`의 `SHRIMP_DATA_DIR`이 정한다(`.mcp.json`이 `${SHRIMP_DATA_DIR}`로 참조). 로드맵이 기준이고 쉬림프는 보조다.
- 사용자 전역 규칙(`~/.claude/CLAUDE.md`)이 적용된다: 주석·로그·에러 메시지·UI 문자열은 한국어, 식별자는 영어, 커밋은 한국어 Conventional Commits, 터미널 명령은 PowerShell 기준(Windows 11), 탐색 → 계획 제시 → 승인 → 구현 순서.

## 제품 개요

OTT내비(ottnavi): 찜한 작품 + 월 예산·월 시청 시간을 바탕으로 국내 OTT 7곳 중 어느 달에 어디를 구독할지 **3개월 롤링 플랜**을 계산하고, 매월 재계산 결과를 이메일로 알리는 비영리 서비스. 데이터 출처는 TMDB(JustWatch 기반)이며 화면마다 출처를 표기한다. 백엔드는 Task 044까지 사용자가 안내서를 보고 직접 구현했고, 045부터는 에이전트가 구현·테스트하며 사용자는 코드를 읽고 설계를 결정하고 핵심 부분을 검수한다(2026-10-14 사용자 합의). 프론트는 Claude가 작성한다(사용자는 흐름·디자인·인증 코드를 검토).

## 문서 체계 (근거 우선순위)

PRD 11절 확정 → PRD 본문 → ERD → rules. 문서끼리 어긋나면 임의로 고르지 말고 사용자에게 올린다.

| 문서 | 역할 |
|---|---|
| `docs/PRD.md` | 요구사항·계산 규칙(5.4)·개발 단계·결정 현황(11절, `R11-N`)의 단일 기준 |
| `docs/ERD.md` | 스키마·상태값·삭제 정책 |
| `docs/TECH.md` | rules에 없는 설계 결정과 함정(`T-N`, 계산 엔진·플랜 저장·배치·인증·outbox·호환성) |
| `docs/ROADMAP.md` | Phase/Task 진행표, 검증 절차(V-F, V-B, V-API, V-FS, V-DEPLOY, V-H) |
| `docs/TASK003_POLICY_PRICING.md` | Task 003 조사 기록: TMDB 약관, 서비스 해지 정책(R11-27), Cloudtype·Supabase·Redis Cloud 무료 플랜 한도와 요금. 출처·확인 수준 표시 |
| `docs/HANDOFF.md` | 세션·계정이 바뀌어도 이어가기 위한 문서: 현재 상태, 다음 작업 순서, 미결·이월 항목, 사용자 직접 할 일, 환경 함정. 근거 문서가 아니고 오래되면 ROADMAP이 기준 |
| `docs/guides/` | **사용자가 요청해 만든 설명·학습 문서 폴더. 근거 문서가 아니고 코드가 기준이다**(규칙의 근거는 PRD·ERD·TECH). `ENGINE_ALGORITHM`(계산 엔진), `COLLECT_GUIDE`(수집 Job의 Step 6개, 후보와 판정의 차이, 읽는 순서), `COLLECT_ADMIN_QA`(수집 관리자 API 이해 노트: `BATCH_*` 표, 조회 방식 선택, 락과 트랜잭션, 용어 풀이), `SPRING_BATCH_BASICS`(Spring Batch 기본 개념), `AVAILABILITY_RULES`(제공 상태 판정·러닝타임), `PACKAGE_MAP`(백엔드 패키지 지도, 패키지를 만들거나 옮기거나 지우는 PR에서 같이 갱신), `FLYWAY_GUIDE`, `JPA_COMPOSITE_KEY_GUIDE`, `OPENFEIGN_GUIDE`, `USER_SETTINGS_WISHLIST_GUIDE`(내 설정·찜 API의 요청 흐름, 잠금과 검증 순서, 읽는 깊이), `PLAN_RECALC_FLOW`(매월 재계산 흐름: 수집 완료 이벤트 → 재계산 Job → 변경 기록·조회, 용어와 조건 정리), `BUNDLE_PRICE_MODE_FLOW`(번들·저가형 요금제와 "가장 저렴한 요금제로 계산" 모드: 기존 알고리즘과 변경 흐름, 구현 전 설계 기준). 새 설명 문서는 이 폴더에 둔다 |
| `docs/proposal_v6.md` | 요약본. 근거로 쓰지 않는다 |
| `docs/PROJECT_STORY.md` | 프로젝트를 만든 이유(포트폴리오·README·자기소개서·면접용 문구). 사용자가 직접 겪은 경험만 담고 용도별(문단·두 문장·구두)로 다듬어 둠. 근거 문서가 아니다 |
| `docs/api/openapi.yaml`, `docs/api/error-codes.md` | API 계약과 오류 코드표. operation 44개(모두 구현됨, `x-planned: true` 0개). 변경 절차는 api-contract.md |
| `.claude/rules/backend.md` | `backend/**` 작업 시 자동 로드. Java/Spring 스타일·스택·패키지 구조 |
| `.claude/rules/frontend.md` | `frontend/**` 작업 시 자동 로드. React/TS 스타일·상태 관리·인증 규칙 |
| `.claude/rules/api-contract.md` | OpenAPI 계약 변경 절차, 응답·오류·페이지네이션 형식 |
| `.claude/rules/git.md` | 브랜치·커밋·병합 규칙 |

`.claude/agents/`에는 문서 작성용 에이전트(prd-writer, tech-writer, roadmap-writer, roadmap-reviewer)와 PR 코드래빗 지적 정리용 읽기 전용 에이전트(coderabbit-triage), API 계약 검사용 읽기 전용 에이전트(contract-checker: `docs/api/openapi.yaml`을 규칙·목업·오류 코드·확정 결정과 대조해 표로 보고, 파일을 수정하지 않음), DB 설계 검토용 읽기 전용 에이전트(db-reviewer: `docs/ERD.md`와 마이그레이션 SQL을 계약·PRD·TECH와 대조해 결함 표와 "사용자에게 시킬 일"로 보고, 모델 opus, 파일을 수정하지 않음), 개발 에이전트 3종(backend-dev: 백엔드 검수·맡긴 구현 / frontend-dev: 프론트 구현 전반 / ui-designer: UI/UX 시각 설계·수정)이, `.claude/skills/`에는 git 작업 스킬(git-branch, git-commit, git-pr, git-merge, git-review)이 있다. 개발 에이전트끼리는 직접 대화하지 못하고 `docs/api/openapi.yaml` 계약과 보고서의 "넘길 내용"으로 협업한다. 작업 공간의 기준은 각 에이전트 md이고 `.claude/hooks/`의 hook은 보조 안전장치다(backend-dev는 `frontend/`, frontend-dev는 `backend/`와 `frontend/src/api/generated/` 수정을 막는다). 프론트는 대부분 frontend-dev가 맡고 UI/UX 수정 요청은 ui-designer가 맡는다. MCP는 `.mcp.json`에 shadcn, shrimp-task-manager가 설정돼 있고, 로드맵은 쉬림프 태스크 매니저가 더 세분화할 것을 전제로 한다.

## 목표 아키텍처 (big picture)

모노레포: `backend/`(Spring Boot) + `frontend/`(Vite SPA) + `infra/`(docker-compose). 배포는 **Vercel → `/api` rewrite → Cloudtype(Spring) → Supabase(PostgreSQL)**, Redis Cloud, TMDB.

**API 계약이 두 쪽을 묶는다.** `docs/api/openapi.yaml`이 유일한 계약이다. 변경 순서는 계약 수정 → 프론트 `npm run api:generate`(orval이 타입·TanStack Query 훅·MSW 목업 생성, `src/api/generated/`는 수정 금지) → 백엔드 구현. springdoc 생성 명세와 `openapi.yaml`이 다르면 CI 실패(`x-planned: true`인 미구현 operation은 제외). 계약 변경과 코드 변경은 같은 PR에 담는다. UI를 먼저 목업(MSW)으로 세우고 API를 하나씩 실제로 교체하는 UI 선행 방식이다.

**백엔드 패키지(`com.ottnavi`)**: `global`(config·security·ratelimit·error·common) / `infra`(tmdb·mail·llm 외부 연동) / 도메인별(`catalog`, `provider`, `product`, `user`, `wishlist`, `plan`, `notification` — 각각 `controller` → `service` → `domain` 단방향. `dto/{request,response}`는 프론트와 주고받는 틀, `service/model`은 서버 안 값 묶음이다. `domain`은 `entity`·`repository`·`enums`(catalog는 `rule`도) 하위 패키지로 나눈다) / `engine` / `collect`(`job`·`step`) / `admin`. 다른 도메인의 리포지토리를 직접 쓰지 않고 서비스를 호출한다.

**계산 엔진(`engine`)**: 순수 Java(프레임워크 의존 금지). `plan` 도메인만 호출한다. `ExactSolver`(후보 상품 제외 S6와 추천형 동치 제거 S7 뒤 남은 조합을 전부 훑고 챔피언 한 명만 들고 가는 완전탐색)와 `GreedySolver`가 `PlanSolver` 인터페이스를 공유하고, 후보 조합 수가 상한을 넘으면 그리디로 전환한다. 목적함수는 사전식 비교기와 시작 조합(baseline)을 묶은 `PlanObjective`를 `solve(input, PlanObjective)`로 받으며, 같은 입력에서 플랜 유형 3종(추천형: 꼭 시청 완료 수 → 점수 → 총비용 → 조기 시청 → 결제 미루기 → 상품 ID 순 / 절약형 / 간편형, PRD 11절 35번)을 계산한다. 규칙 구현부에는 PRD 5.4의 어느 규칙인지 주석을 남긴다. 같은 시리즈의 시즌은 번호 순서대로 시청 완료한다는 규칙(PRD 11절 37번, 2026-10-10)은 구현됐다(정렬 뒤 한 번 훑으며 앞 시즌을 끼워 넣는 방식, ROADMAP Task 040 후속 항목 S). 찜은 최대 20개(11절 36번), 절감액 = 전체 구독 비용 − 플랜 총비용(11절 38번). 상세는 TECH 1절.

**플랜 상태**: DRAFT(계산 결과) → ACTIVE(저장) → ARCHIVED. 사용자당 ACTIVE는 최대 1개, DRAFT는 플랜 유형마다 최대 1개(부분 유니크 인덱스 `(user_id, plan_type)`). 저장하면 고른 유형만 ACTIVE가 되고 나머지 DRAFT는 삭제된다. 활성화 시 **기존 ACTIVE를 ARCHIVED로 바꾸고 명시적 flush한 뒤** 새 ACTIVE로 전환해야 인덱스 위반이 나지 않는다(TECH 2절). 삭제는 FK `ON DELETE CASCADE` + 벌크 JPQL(JPA cascade 삭제 미사용, TECH T-1).

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
- **Flyway**: 스키마는 마이그레이션으로만 바꾼다. 병합된 파일은 고치지 않고 새 버전을 추가한다(MVP는 V1~V9 번호가 Task에 배정됨(V9는 Task 107이 사용함), ROADMAP 4.2).
- **외부 호출(TMDB·메일·LLM)은 트랜잭션 밖**에서 한다(수집 배치만 예외, TECH T-2).
- **버전 함정**: Spring Boot 4.1은 Jackson 3(`tools.jackson.*`), `@MockitoBean`, `spring-boot-starter-flyway`·`spring-boot-starter-batch-jdbc` 필요, orval은 `httpClient: 'axios'` 명시 필요, msw 3은 `onUnhandledRequest`가 아니라 `onUnhandledFrame`, TypeScript 6은 `baseUrl` 없이 `paths`만 쓴다. 전체 목록은 TECH 6절.
- **설정 파일은 YAML로 통일**한다(`application.yml`, `-local.yml`, `-prod.yml`). `.properties`와 섞지 않는다.
- **패키지(폴더)를 만들거나 옮기거나 지우거나 역할이 바뀌는 변경은 `docs/guides/PACKAGE_MAP.md`를 같은 PR에서 갱신한다.** 구현 PR을 올리기 전에 지도가 실제 폴더와 맞는지 확인한다(2026-10-14 사용자 결정).
- **화면 표시**: 제공 상태는 있음/없음/모름 3가지를 색만으로 구분하지 않고, 제공처 화면엔 JustWatch 출처, About엔 TMDB 로고·고지문을 반드시 넣는다. 결과는 "확정"이 아닌 "확인된 범위"로 표현하고 데이터 기준일을 보여준다.

## 협업 방식

- **결정을 물을 때는 전후 사정을 모르는 사람 기준으로 쓴다.** 용어(목업, 계약, PR, 에이전트 등)를 풀고 "배경 → 선택지 표(무엇이 달라지는지·위험) → 추천 → 답변 방법" 순으로 쓴다. 한 줄 요약만 던지지 않는다. 더 설명해 달라고 하면 실제 예시(코드 한두 줄)로 다시 푼다.
- **사용자의 검토 범위는 화면(UI/UX·문구·흐름)이다.** 프론트 코드 정확성은 공식 문서(context7)와 설치된 타입 정의로 Claude가 먼저 확인하고 근거를 보고한다. 인증·토큰 동작 코드는 사용자 검토 대상이다(Task 057에서 다시 확인).
- **푸시·PR 뒤에는 CI·CodeRabbit을 기다리지 않는다.** `sleep`이나 `gh pr checks` 반복 조회로 대기하지 않고 상태를 한 번만 알린 뒤 다른 작업을 이어간다. 사용자가 "코드래빗 왔다"고 알려 주면 그때 `coderabbit-triage`로 정리한다.
- **에이전트를 쓰면 능률이 오르거나 토큰이 절약되는 작업은 시작 전에 먼저 제안한다.** 계획 단계에서 이득과 비용(콜드 스타트)을 한 줄로 알리고, 이득이 불분명하면 쓰지 않는다고 말한다. 병렬 작업은 별도 워크트리가 필요하므로 허락을 먼저 구한다.
- **모델 방침**: 백엔드 작업은 두 단계로 나눈다(2026-10-14 사용자 결정). 1단계 설계·계획: 알고리즘, 상태 전이, 동시성, 여러 경우의 수 처리처럼 "어떻게 풀지"를 먼저 정해야 하는 새 기능은 `backend-dev`를 호출할 때 `model: opus`를 명시하고, 코드를 쓰지 않고 접근 방식·클래스 구성·테스트 케이스·결정 질문만 계획으로 받는다. 2단계 구현·테스트·검수: 사용자가 승인한 계획을 넘겨 기본 모델 sonnet으로 한다. 단순 CRUD·조회·설정·이동 같은 작업은 1단계 없이 바로 sonnet이다. opus에게 구현까지 맡기지 않는다(5시간 할당량이 한 작업에 거의 소진된 적이 있다). 예외로 DB 설계 검토형 `db-reviewer`는 opus를 쓴다(스키마 결함은 ERD·계약·PRD·TECH를 가로지르는 추론이 필요하고 읽기 전용이다, 2026-10-13 사용자 결정). 이 단계 구분은 아래 "작업 속도 방침" D(opus 계획은 인증·플랜 계산·outbox 같은 위험한 Task만)가 우선한다(2026-10-15).
- **설명은 배경부터 한다.** 듣는 사람이 전후 사정과 배경지식이 없다고 보고, 첫 문장에 이 작업이 무엇을 하는지를 말한 뒤 ① 흐름과 각 단계의 주체(서버, DB, 외부 서비스가 누구에게 무엇을 요청하는지) ② 실제 요청·응답 값 예시 ③ 용어와 설계 선택지 순으로 쓴다. 처음 쓰는 용어는 그 자리에서 정의한다. "이 작업에 포함되지 않는 것"은 따로 말하지 않는다. 번호는 `1.`, `2.`로 쓰고 ①②처럼 작은 기호를 쓰지 않는다(사용자가 보기 어렵다고 했다).
- **백엔드 설계와 모든 검증은 `backend-dev` 에이전트가 한다.** 설계·안내서 코드는 임시 워크트리(`$CLAUDE_JOB_DIR/tmp/wtNN`)에서 컴파일·테스트한 결과를 근거로 쓰고, 사용자가 구현한 코드의 검수, 테스트·빌드 결과 확인(`build/test-results/test/*.xml` 집계), `git status`의 `AM`/`AD` 확인도 에이전트로 한다. 사용자의 작업 워크트리 소스는 에이전트가 수정하지 않게 지시한다(Task 044까지의 방식. 045부터는 아래 "구현 방식" 항목을 따른다).
- **백엔드 안내서(`docs/TASKxxx_GUIDE.md`)는 사용자 작업 워크트리의 `docs/`에 추적되지 않는 파일로 두고 커밋하지 않는다.** 사용자가 보면서 구현하고 마지막에 지운다(문서만 위한 PR과 CodeRabbit 재리뷰를 줄이려는 것). 대신 영구히 남길 짧은 설명 문서(100줄 안팎)는 구현 PR에 함께 커밋한다. 안내서는 코드 전체와 핵심 흐름만 쓰고, 테스트 코드 옆에 "무엇을 확인하는지 / 막으려는 사고" 표를 붙인다. 분량이 길어지면(코드 포함 1000줄 이상) 사용자에게 알린다. 이 안내서 방식은 Task 044까지다.
- **구현 방식(Task 045부터)**: 안내서를 따라 치게 하지 않는다. `backend-dev`가 구현하고 테스트까지 통과시킨 뒤 사용자에게 짧은 설명 문서(읽는 순서 표, 값이 틀을 바꿔 가는 예시, 테스트가 확인하는 것 표, 100줄 안팎)를 준다. 사용자는 결정 질문에 답하고 코드를 읽으며, 규칙 한 줄을 일부러 바꿔 어떤 테스트가 깨지는지 보는 "깨뜨려 보기"로 이해를 점검한다. 백엔드 구현은 **`ottnavi-backend` 워크트리에 직접 반영**한다(2026-10-14 사용자 결정). 에이전트 호출 프롬프트에 이 경로를 적는다. 메인 워크트리 `ottnavi`는 `develop` 유지·최신화와 프론트·문서 작업에 쓴다. 핵심 조각을 비워 두고 사용자가 채우는 빈칸 채우기 방식은 쓰지 않는다(사용자 판단: 의미가 약하다). 설명 문서는 아래 "작업 속도 방침" E(사용자가 요청할 때만, 짧게)가 우선하며, 대신 Task가 끝나면 커밋 전에 대화로 설명한다.
- **사용자 PC의 프로그램(Docker Desktop 등)은 사용자 허락 없이 켜지 않는다.** Testcontainers 테스트가 필요하면 먼저 켜 달라고 요청하고, 에이전트 프롬프트에도 "직접 켜지 말고 꺼져 있으면 멈추고 보고하라"를 넣는다(2026-10-14, 에이전트가 허락 없이 Docker를 켠 일이 있었다).
- **문서만 고치는 변경은 따로 PR을 올리지 않고 다음 구현 PR에 묶는다**(ROADMAP 기록, 규칙 갱신 등). 사용자가 PR과 재리뷰를 늘리는 것을 싫어한다. 단 사용자가 문서 PR을 요청하면 올린다.
- **CodeRabbit 정리는 `coderabbit-triage`에 프로젝트 결정을 같이 넘긴다.** 이미 결정한 사항(예: 쿠팡플레이 영화는 항상 모름, tier는 ALL만, Reader 진행 위치 미저장, Supabase Data API 꺼 둠)을 프롬프트에 적어 그 결정과 충돌하는 지적은 걸러 달라고 한다. 반영하기로 하면 푸시는 한 번에 모아서 한다. Docstring Coverage 경고, "Generate docstrings"·Autofix는 무시한다.
- **작업 속도 방침 A~H(2026-10-15 사용자 결정)**: A 결정 질문은 묶어서 한 번에 낸다(중요한 것만 따로 설명하고 나머지는 추천안을 기본값으로, 이의가 있을 때만 바꾼다). B 구현은 2~3단계(오류·필터 / 서비스·API / 문서)로 나눈다. C 계약이 확정된 Task는 백엔드·프론트를 동시에 진행한다(별도 워크트리, 허락을 먼저 구한다). D opus 설계 계획은 위험한 Task(인증, 플랜 계산, outbox)만, 단순 API·연결은 바로 sonnet. E 설명 문서는 사용자가 요청할 때만, 짧게. F 작은 Task는 한 PR로 묶는다(046+047, 050+051, 059+060). G 검증(`contract-checker`, 문서 점검, 전체 빌드)은 마지막에 한 번. H 프론트 V-F 브라우저 확인은 화면이 바뀐 Task만(목업 연결은 테스트로 대체). **테스트 코드는 조금 줄인다**: 핵심 판정 단위 테스트, 통합 정상 경로와 대표 실패 1~2개, 계약 대조는 유지하고 V-API·V-B 필수 케이스도 유지한다. 중복 변형·쿼리 수 확인 같은 부가 테스트는 위험이 있을 때만 쓴다.
- **백엔드 Task가 끝나면 커밋 전에 대화로 설명한다**: 알고리즘, 전체 흐름(배경부터), 파일 추적(몇 번째 줄은 제외), 파일마다 읽는 깊이(꼭 읽기/훑기/넘어가기) 표. 문서가 아니라 대화다.
- **압축 프롬프트 제안**: 묶음 Task가 끝나 PR·병합까지 정리됐거나 컨텍스트가 60%를 넘었고 중요한 작업이 마무리됐다면, 이어가기 프롬프트를 만들지 먼저 한 줄로 묻는다. 허락 없이 만들지 않는다. 만들 때는 저장소 밖 `C:\Users\swmoo\workspace\HANDOFF_PROMPT_<Task번호>.md`에 새 파일로 쓴다(이전 파일은 덮어쓰지 않는다).
- **병합·푸시 규칙**: 병합은 Squash이고 제목 끝에 `(#PR번호)`를 붙인다. 병합하면 메인 워크트리 `develop`을 `git fetch` 후 `merge --ff-only origin/develop`으로 최신화한다. 쉬림프는 Task를 착수할 때 등록하고(`split_tasks` append) `execute_task` 후 완료 때 `verify_task`로 처리한다(앞으로 할 Task는 착수 직전에 올린다).
- **MCP(playwright, shadcn, shrimp)가 자주 끊긴다.** 끊기면 사용자에게 `/mcp` 재연결을 요청한다.
