# OTT내비(ottnavi) ROADMAP — 개발 로드맵

## 1. 문서 정보

| 항목 | 내용 |
|---|---|
| 기준 문서 | [PRD.md](./PRD.md) v0.3 (2026-10-03) |
| 근거 문서 | [TECH.md](./TECH.md)(T-1~T-8 확정, T-9 결정 필요), [ERD.md](./ERD.md) v0.3, `.claude/rules/backend.md`, `frontend.md`, `api-contract.md`, `git.md` |
| 작성일 | 2026-10-03 (초판), 2026-10-03 (2판: 리뷰 반영·Task 재번호), 2026-10-03 (3판: 재평가·사용자 결정 반영, Task 021 신설로 재번호) |
| 갱신 중인 근거 | PRD 11절 4·5번·FR-18, TECH 3·4절, `api-contract.md`는 3판의 사용자 결정(R-14, R-20, R-21)에 맞춰 다른 작업자가 갱신 중이다. 이 로드맵은 갱신된 내용을 전제로 적었다 |
| 참고만 | `docs/proposal_v6.md`(v7 요약본, 세부 근거로 쓰지 않음) |

**저장소 상태 (2026-10-04 확인).**
- 원격 저장소 `github.com/msw-Hub/ottnavi`(공개)가 연결돼 있고 `main`과 `develop`이 있다. 기본 브랜치는 `main`이다. `main`·`develop`에는 보호 규칙(ruleset `protect-main-develop`)이 걸려 있어 직접 푸시할 수 없고 PR로만 합친다(Task 012, 필수 검사 `backend-ci`·`frontend-ci`).
- 생성됨: `.github/workflows/`(Task 010), `frontend/vercel.json`(Task 009), `backend/`(Spring Boot 4.1.1 골격, Task 006), `frontend/`(Vite 골격과 개발 도구 구성, Task 007·017), `docs/api/`(계약 골격, Task 011), `infra/docker-compose.yml`·`.env.example`(Task 008, PR #1), `.coderabbit.yaml`(PR 자동 리뷰).
- Task 016 완료(2026-10-06): 백엔드 의존성 전체, `application.yml`·`-local.yml`·`-prod.yml`, 임시 `SecurityConfig`(전 경로 `permitAll`), Testcontainers 테스트 베이스가 있다.
- PR은 `gh` CLI로 만들고, 병합 방식은 Squash다. PR마다 CodeRabbit이 자동 리뷰한다(`develop` 대상, 한국어, 생성 파일 제외). 자동 수정(Autofix·Autopilot)은 쓰지 않는다.
- 플러그인: `feature-dev`, `frontend-design`, `skill-creator`(claude-plugins-official)가 프로젝트 범위로 설치돼 있고 `.claude/settings.json`의 `enabledPlugins`에 켜져 있다(`~/.claude/plugins/installed_plugins.json`, 2026-10-03 확인, Task 013).

**기술 스택 확인 결과.**
- 스택과 버전은 rules가 기준이다. 백엔드는 Java 17, Spring Boot 4.1(Spring Security 7, Hibernate 7, Jackson 3), Gradle, Spring Batch 6, OpenFeign 2025.1.2 이상. 프론트는 React SPA, Vite, TypeScript, Tailwind v4, shadcn/ui, React Router, TanStack Query, Zustand, React Hook Form·Zod, orval, axios, MSW, Vitest·RTL, npm.
- context7로 직접 확인한 것
  - orval: `output.httpClient` 기본값이 `'fetch'`라 axios mutator를 쓰려면 `httpClient: 'axios'`와 `override.mutator`를 함께 지정한다(`/orval-labs/orval`, 초판 확인).
  - Spring Boot 4.1: `spring-boot-starter-flyway`가 따로 있고 `flyway-database-postgresql`은 BOM 관리. Batch는 JDBC 저장소와 메모리 저장소를 따로 자동 구성한다(`/spring-projects/spring-boot/v4.1.0`, 초판 확인).
  - Spring Boot 4.1 스타터 이름: 웹은 `spring-boot-starter-webmvc`, OAuth2 클라이언트는 `spring-boot-starter-security-oauth2-client`(기존 `spring-boot-starter-oauth2-client`는 이 이름으로 대체되어 deprecated)(2판 확인). Task 016 의존성 목록에 반영했다.
- context7로 확인하지 못한 것
  - `spring-boot-starter-batch-jdbc` 아티팩트 이름. 이번 조회에서도 `spring.batch.jdbc.*` 설정만 확인됐다. TECH 6절(web, c7)을 근거로 썼고, Task 016에서 Maven Central의 4.1.1 아티팩트 존재를 확인해 `compileJava`가 통과했다(해소, 초판 10.3 R-13을 이곳으로 옮김).
  - Vercel `vercel.json`의 `routes[].transforms`로 환경 변수를 요청 헤더에 넣는 방식. context7 조회 결과에 해당 문서가 없었다. TECH T-8 ①(web)을 근거로 한다.
  - `routes`와 `rewrites` 혼용이 배포 검증에서 거부되는지. 3판 조회(`/vercel/vercel`)에서 `@vercel/routing-utils`의 `getTransformedRoutes`가 두 값을 함께 받으면 사용자 `routes`를 먼저 넣고 `{ handle: 'filesystem' }` 뒤에 `rewrites`를 붙인다는 코드는 확인했다(적용 순서 근거). 혼용 시 배포 거부 여부는 확인하지 못해 Task 014에서 미리보기 배포로 실측한다(10.3 R-17).
- TECH·ERD·rules 사이의 불일치는 고르지 않고 10절에 올렸다.

**표기.**
- 상태: `⬜ 대기` / `🔄 진행 중` / `✅ 완료` / `⏸ 보류`. 완료는 4절의 검증 절차를 통과한 것만 표시한다.
- 태그: `[B]` 백엔드(직접 작성), `[F]` 프론트(AI 작성, 흐름·디자인·인증 코드는 직접 검토), `[H]` 사람이 직접 하는 비개발 작업. 인프라 Task는 태그가 둘일 수 있다.
- `R11-N`은 PRD 11절 N번, `T-N`은 TECH 7절, `R-N`은 이 문서 10.3이다. 경로·이름 중 문서에 정해지지 않은 것은 "(제안)"으로 표시하고, 정해지는 Task에서 계약(`docs/api/openapi.yaml`)에 고정한다. 사용자가 정하지 않은 항목의 권고는 "추천안(미확정)"으로 적는다.
- `[H]` Task의 결과는 해당 Task 아래에 `기록:` 줄을 추가해 남긴다. 이 로드맵은 태스크 매니저가 더 쪼갤 것을 전제로 하므로 Task에는 순서·선행·결정 시점과 핵심 구현 사항만 적는다.

---

## 2. 전체 진행 현황

| Phase | 이름 | 완료/전체 | 상태 |
|---|---|---|---|
| 1 | 프로젝트 초기 설정 (골격 구축) | 22/23 | 🔄 진행 중 |
| 2 | 공통 모듈/컴포넌트 개발 | 3/14 | 🔄 진행 중 |
| 3 | 핵심 기능 개발 | 0/30 | ⬜ 대기 |
| 4 | 추가 기능 개발 | 0/24 | ⬜ 대기 |
| 5 | 최적화 및 배포 | 0/14 | ⬜ 대기 |
| 합계 | | 25/105 | |

- Task 023(공통 응답·예외 처리)은 Task 019의 선행이라 2026-10-04 사용자 결정으로 Phase 2에서 Phase 1로 옮겼다(번호는 그대로). 그래서 Phase 1은 23개, Phase 2는 14개다.

---

## 3. PRD 단계와 Phase 매핑

Phase 번호는 작업의 **성격**에 따른 분류이고 시간 순서와 꼭 같지는 않다. 그래서 선행 Task가 뒤 Phase에 있을 수 있다(예: 얇은 배포 Task 019가 공통 응답 Task 023을 선행으로 둔다. 이 경우는 Task 023을 Phase 1로 옮겨 해소했다). PRD 9절 "위험한 작업은 일찍"에 따라 계산 엔진(B2, Phase 3)은 1주차에 골격과 함께 착수하고, MVP 배포(B8·F8, Phase 5)는 2단계 작업(Phase 4)보다 먼저 한다. 실제 착수 순서는 3.2를 따른다.

**UI 선행 순서(PRD 9절).**
- MVP: 전체 화면 목업(F1, Task 027~029) → 목업 승인과 화면 결정(Task 030) → OpenAPI 초안(Task 031) → ERD 보정(Task 032) → 작품·상품 영역(A·B, Task 033)과 사용자·플랜·알림 영역(C·D·E, Task 034) 마이그레이션. 1주차 얇은 배포에 필요한 V1(`ott_service`, Task 019)만 예외로 먼저 만든다.
- F1 목업 범위: SCR-01, 02, 04, 06~10, 14~16은 목업으로 만들고, 2·3단계 화면 SCR-03, 05, 11~13, 17, 18은 "준비 중" 자리표시로 둔다. 이 화면들은 2단계 이후 기능 단위로 "화면 목업 → 계약 추가·백엔드 구현 → 실제 API 교체" Task 묶음에서 만든다(4.4).

### 3.1 작업 ID 매핑표

| PRD 단계 | 작업 ID | 내용 | 시나리오 | Phase | Task |
|---|---|---|---|---|---|
| 0 준비 | H1 | 문서 | — | 1 | 001 |
| | H2 | 계정·정책·provider_id·요금표 | — | 1 | 002, 003, 004 (+도메인 005) |
| | B0 | 백엔드 세팅 | — | 1, 2 | 006, 008, 010, 016 / 023, 024 |
| | F0 | 프론트 세팅 | — | 1 | 007, 009, 010, 017, 018 |
| | (git.md 등) | 계약 골격·브랜치·플러그인·Vercel·시크릿 | — | 1 | 011, 012, 013, 014, 015 |
| 1 MVP | F1 | 화면 골격·OpenAPI 초안·ERD 보정·스키마 | 전체 | 2 | 025, 027~035 |
| | B1 | 얇은 배포 경로(Cloudtype 서비스 생성 포함) | — | 1 | 019, 020, 021, 022 |
| | B2 | 계산 엔진 | S5 | 3 | 038, 039, 040 |
| | H3 | 디자인 결정 | — | 2 | 036 |
| | F2 | 디자인·공통 컴포넌트 | — | 2 | 026, 037 |
| | B3 | 데이터 수집 | S8 | 3 | 041~048 |
| | B4 | 공개 조회 API | S1, S2 | 3 | 049, 050, 051 |
| | F3 | 공개 화면 연결 | S1, S2 | 3 | 052 |
| | B5 | 인증·사용자 | S3, S4 | 3 | 053~056 |
| | F4 | 인증 연동 | S3 | 3 | 057 |
| | F5 | 사용자 화면 연결 | S3, S4 | 3 | 058 |
| | B6 | 상품·요금 | S5, S9 | 3 | 059, 060 |
| | F6 | 관리자 화면(MVP) | S8, S9 | 3 | 061 |
| | B7 | 플랜 API | S5, S6(저장) | 3 | 062~066 |
| | F7 | 계산 흐름 연결 | S5, S6(저장) | 3 | 067 |
| | B8·F8 | MVP 배포 | S1~S5 | 5 | 092~095 |
| 2 운영 | O1 | 매월 재계산 | S7, S8 | 4 | 068, 069, 070 |
| | O2 | 알림 | S6, S7 | 4 | 071~074 |
| | O3 | 데이터 확장(번들·광고형·계층·독점) | S2, S9 | 4 | 075~079 |
| | O4 | 탈퇴·정리 | — | 4 | 080~084 |
| | (배포) | 2단계 배포 | — | 5 | 102 |
| 3 품질 | Q1 | 관측 | S8 | 5 | 096~099 |
| | Q2 | 성능 | — | 5 | 100, 101 |
| | Q3 | LLM | — | 4 | 085~088 |
| | Q4 | 마무리(정확도 안내, 반응형·접근성 / 재현율, 문서) | — | 4, 5 | 089, 090 / 103, 104 |
| | (배포) | 3단계 배포 | — | 5 | 105 |
| 2단계 후보 | T-9 | 오래된 DRAFT 판정 개선 | S5 | 4 | 091 (⏸) |

### 3.2 착수 순서와 가정 일정

PRD는 주차만 정했다(예상치). 10절의 기한을 날짜로 적기 위해 2026-10-05 착수를 **가정**했다. 시작일이 바뀌면 같은 간격으로 민다.

| 구간 | 가정 기간 | 착수 Task |
|---|---|---|
| 준비 | 2026-10-05 ~ 10-07 | 002~011(006·007·011은 `main` 직접 커밋, 008·009·010은 `feature/*` PR) → 012(`develop` 생성·보호 규칙) → 014(R-17 실측), 015(GitHub·Vercel·로컬 시크릿) |
| MVP 1주차 | 10-08 ~ 10-14 | 016, 017, 018 / 023, 024 / 019 → 020(Cloudtype 서비스·환경 변수·배포 토큰, R-12 기한 10-12) → 021(**첫 배포**) / 022(SMTP 시험) / 025~031 / **038 착수(B2)** |
| MVP 2주차 | 10-15 ~ 10-21 | 032~037 / 039, 040 / 041~048 / 049~052 |
| MVP 3주차 | 10-22 ~ 10-28 | 053~061 |
| MVP 4주차 | 10-29 ~ 11-04 | 062~067 / 092~095 (**MVP 완료**, `v0.1.0`) |
| 2단계(5~6주차) | 11-05 ~ 11-18 | 068~084, (091 결정 시) / 102 (`v0.2.0`) |
| 3단계(7~8주차) | 11-19 ~ 12-02 | 085~090 / 096~101, 103, 104 / 105 (`v0.3.0`) |

- **2026-10-08 갱신**: 030(기한 10-14)이 1주차 끝에 몰려 있어 **031은 10-15~16으로 밀릴 가능성이 높다**. 032~034(2주차)는 영향이 작다. 1주차의 프론트 작업(018·025~029)은 `frontend-dev`·`ui-designer` 에이전트가 맡고(`docs/TASK018_031_PLAN.md` 7절), 사용자 직접 작업은 022·024·030이다. 025·026은 PR 하나로 합친다.

### 3.3 MVP 완료 기준 충족 지점

| PRD 9절 MVP 완료 기준 | 충족 Task |
|---|---|
| ① 로그인 사용자가 구독 상태·예산·시청 시간 입력 → 검색·찜 → 3개월 플랜 계산·저장 | 058, 067 (로컬), 095 (배포) |
| ② 비로그인 방문자가 검색과 작품 상세를 본다 | 052 |
| ③ 계산 엔진이 정답 테스트를 통과하고 정확해·그리디 비교를 한 번 이상 기록 | 040(정답 테스트), 065(테스트 세트 측정으로 `calc_run` 비교 기록, TECH T-4) |
| ④ 배포 환경에서 로그인과 계산이 동작 | 095 |

**MVP 완료 = Task 095 완료**(`develop` → `main` 병합, 스모크 통과, `v0.1.0` 태그). PRD 9절 "MVP에서 미루는 것"(알림 메일, 매월 재계산, 번들·광고형, 독점 목록, 탈퇴, 관측, LLM)은 모두 Phase 4~5 Task로 뺐다.

---

## 4. 개발 환경, 공통 규칙, 검증 절차

### 4.1 개발 환경

| 구분 | 구성 | DB | 용도 |
|---|---|---|---|
| ① 로컬 | `docker compose -f infra/docker-compose.yml up -d`로 PostgreSQL·Redis만 컨테이너로 띄운다. 백엔드는 PC에서 `.\gradlew.bat bootRun --args="--spring.profiles.active=local"`(localhost:8080). 프론트는 `npm run dev`가 `/api`를 localhost:8080으로 프록시한다. `VITE_USE_MOCK=true`면 MSW로 백엔드 없이 UI를 개발한다 | 도커 PostgreSQL | 일상 개발 |
| ② 테스트 | Testcontainers(PostgreSQL, Redis). Docker Desktop이 켜져 있어야 한다. H2는 쓰지 않는다. CI(GitHub Actions)도 같은 방식이다 | 컨테이너 PostgreSQL | V-B, CI |
| ③ 얇은 배포(1주차) | Vercel → `/api` → Cloudtype → Supabase. 배포 경로 검증용이며 개발 환경이 아니다. 이후 개발은 계속 ①에서 한다 | Supabase | Task 021 |
| ④ 운영(MVP 배포) | Vercel → Cloudtype → Supabase·Redis Cloud·TMDB. 무료 기간 Cloudtype은 매일 꺼지고 요청으로 다시 켜지지 않는다(Hobby 전환은 Task 092) | Supabase | Task 092 이후 |

- 성능 측정(k6)만 예외로, 백엔드 컨테이너의 `cpus`·`mem_limit`을 고정한 로컬 compose 구성에서 한다(Task 100).

### 4.2 공통 규칙

**브랜치(git.md).**
- 초기 세팅 중 Task 006·007·011·017(일부)과 문서는 `main`에 직접 커밋했고(2026-10-04), 그 뒤 `develop`을 만들어 푸시했다. 이후 Task 008(PR #1)·009(PR #4)·010(PR #6)은 2026-10-04 사용자 결정으로 `main` 직접 커밋 예외를 쓰지 않고 `feature/*` 브랜치와 `develop` 대상 PR로 진행했다.
- 이후 모든 개발 Task는 `develop`에서 `feature/{작업ID}-{설명}`을 만들어 `develop` 대상 PR로 합친다(squash). 그래서 모든 feature Task는 Task 012를 전제로 한다(체인의 첫 Task에만 선행으로 표기).
- `develop` → `main`은 배포 Task에서만 merge commit으로 합친다: 첫 배포(021), MVP(095), 2단계(102), 3단계(105). `main` 직접 커밋이나 `develop` 수동 배포 예외는 두지 않는다.
- cron 워크플로는 기본 브랜치(`main`) 기준으로 실행된다. 그래서 워크플로를 만드는 Task는 PR·로컬 수준으로 검증하고, 실제 실행 확인은 `main`에 병합하는 배포 Task의 완료 기준에 둔다.
- `[H]` Task의 `기록:` 갱신과 문서 수정(ROADMAP, ERD 등)도 Task 012 이후에는 `develop`에서 `feature/docs-{설명}` 브랜치를 만들어 PR로 합친다(git.md "문서만 고치는 작업도 `feature/`"). Task 012 이전 기록은 초기 세팅 커밋에 함께 넣는다.

**모든 feature Task 공통 완료 조건.**
- `develop` 대상 PR에서 `backend-ci`·`frontend-ci`가 통과한다.
- API를 바꾸면 `docs/api/openapi.yaml`, `docs/api/error-codes.md`, `npm run api:generate` 결과를 같은 PR에 담고, 계약 대조 테스트(T-5, Task 023)가 통과한다.

**MVP 계약 초안과 `x-planned` 표시(R-20 확정).**
- MVP 계약 초안은 별도 파일 없이 `docs/api/openapi.yaml` 하나에 둔다. Task 031이 아직 구현되지 않은 operation에 `x-planned: true`를 달고 description에 "제안"을 표시한다.
- 계약 대조 테스트(Task 023)는 `x-planned: true`인 operation을 비교에서 제외한다. 구현 Task는 해당 operation을 구현하면서 같은 PR에서 `x-planned`를 지운다. 이 표시가 남은 operation이 MVP 배포(Task 095) 시점에 없어야 한다.
- orval 입력은 계속 `openapi.yaml` 하나다(Task 017). `x-planned` operation도 훅·MSW가 생성되므로 목업 개발에 그대로 쓴다.

**Flyway 버전 번호 규칙(충돌 방지).**
- MVP 마이그레이션은 Task에 번호를 미리 배정한다: V1 `ott_service`(019), V2 작품 영역 A(033), V3 상품 영역 B(033), V4 `BATCH_*`(033), V5 사용자 영역 C(034), V6 플랜 영역 D(034), V7 알림 E·`shedlock`(034).
- 그 뒤 Task는 `develop` 병합 직전에 "현재 최대 번호 + 1"로 정한다. 두 브랜치가 같은 번호를 쓰면 나중에 병합하는 쪽이 번호를 올린다.
- `develop`에 병합된 마이그레이션 파일은 고치지 않고 새 버전을 추가한다.

### 4.3 플러그인 활용 규칙 (설치 완료, 확인일 2026-10-03)

| 플러그인 | 이름 | 쓰는 곳 |
|---|---|---|
| frontend-design | 스킬 `frontend-design:frontend-design`(새 UI·화면 개편 때 시각 방향·타이포 선택 안내) | 프론트 UI Task: 목업(027~029, 068, 071, 075, 081, 086, 097), 디자인 토큰(037), 각 화면 연결 Task |
| feature-dev | 스킬 `feature-dev:feature-dev`, 에이전트 `feature-dev:code-explorer`(기존 코드 흐름 분석), `feature-dev:code-architect`(구현 설계), `feature-dev:code-reviewer`(버그·규칙 위반 리뷰) | 큰 백엔드 Task를 태스크 매니저에서 쪼갠 소 작업마다 탐색(code-explorer) → 설계(code-architect) → 구현 → 리뷰(code-reviewer). 대상: 수집 Job(043, 044), 인증(053, 054), 계산 API(062~064), 알림(072, 073) |
| skill-creator | 스킬 `skill-creator:skill-creator`(스킬 생성·개선·평가) | 반복 검증 절차(V-F, V-API, openapi 변경 체크리스트)를 `.claude/skills/`의 프로젝트 스킬로 만들 때 |

- 색·간격·글꼴은 `src/styles/tokens.css`와 `frontend.md`가 우선이다. frontend-design이 제안한 값은 토큰으로 옮기고, 컴포넌트에 색 값을 직접 쓰지 않는다.
- 플러그인은 4.5의 검증 절차와 "탐색 → 계획 → 승인 → 구현" 규칙을 대신하지 않는다.

### 4.4 2단계 이후 기능 진행 순서 (PRD 9절)

1. **[F] 화면 목업**: 화면과 `src/mocks/fixtures/` 데이터, 손으로 쓴 MSW 핸들러로 흐름을 만든다. 필요한 API의 계약 변경안을 Task `기록:`에 남기고 사용자가 승인한다. `openapi.yaml`에는 아직 넣지 않는다(PRD 9절 2단계 규칙. `x-planned` 표시는 MVP 초안(Task 031)에만 쓴다. 2단계에도 쓸지는 사용자 판단, 10.3 R-20).
2. **[B] 계약 추가 + 백엔드 구현**: 승인된 변경안을 `openapi.yaml`에 넣고 `npm run api:generate`, 구현을 같은 PR에 담는다.
3. **[F] 실제 API 교체**: 목업의 임시 타입·핸들러를 생성 훅으로 바꾸고 V-FS로 확인한다.

### 4.5 검증 절차

각 Task의 완료 기준이 아래 절차를 참조한다.

- **V-F (프론트 브라우저 확인, Playwright MCP)**
  1. `frontend`에서 `npm run dev`를 백그라운드로 실행한다.
  2. `browser_navigate`로 Task의 라우트에 접근한다.
  3. `browser_snapshot`(또는 `browser_take_screenshot`)으로 렌더링을 확인한다. 상호작용은 `browser_click` / `browser_type` / `browser_select_option`으로 재현한다.
  4. `browser_console_messages`(level `error`)로 콘솔 에러가 0건인지 확인한다.
  5. 반응형이 필요한 Task는 `browser_resize`(예: 375×812)로 모바일 폭도 본다.
  6. 끝나면 `browser_close`로 닫고 dev 서버를 종료한다.
  - 깊이: Phase 1은 "에러 없이 로드", Phase 2는 "목업 흐름이 끝까지 이어짐", Phase 3 이후는 "실제 값이 화면에 반영됨". Vitest·RTL 테스트는 V-F를 대체하지 않는다.
- **V-B (백엔드 테스트)**: Docker Desktop을 켠 상태에서 `backend`에서 `.\gradlew.bat test`(또는 `.\gradlew.bat test --tests "com.ottnavi.OttnaviApplicationTests"` 처럼 클래스·메서드 이름을 지정)가 통과한다. 통합 테스트는 Testcontainers(PostgreSQL, Redis), H2 금지.
- **V-API (실제 엔드포인트 호출)**
  1. `docker compose -f infra/docker-compose.yml up -d`
  2. `backend`에서 `.\gradlew.bat bootRun --args="--spring.profiles.active=local"`
  3. PowerShell 7에서 `Invoke-WebRequest -Uri http://localhost:8080/api/… -SkipHttpErrorCheck`로 호출한다.
  4. 상태 코드, `Content-Type`(성공 `application/json` + `CommonResponse` / 오류 `application/problem+json` + `errorCode`), 본문을 확인한다.
- **V-FS (풀스택)**: `frontend/.env.local`에 `VITE_USE_MOCK=false`를 두고 로컬 백엔드를 띄운 뒤 V-F를 수행한다. 화면 값이 DB·API 응답과 같은지 확인한다.
- **V-DEPLOY (배포 스모크)**. 배포 환경 응답 헤더 확인은 이 절차 한 곳에서 정의한다.
  - 인증: 배포 URL에서 로그인 → 새로고침(`/api/auth/refresh` 200, AT 재발급) → 로그아웃(RT 쿠키 삭제) → AT 만료 후 401 → 재발급 1회 → 원요청 재시도.
  - 기능: 검색, 상세, 계산·저장(git.md 스모크 항목).
  - 헤더·경로: `/api/**` 응답에 `Cache-Control: no-store`가 있고 Vercel 캐시 적중이 없다(`x-vercel-cache`가 HIT 아님, T-8 ②). `x-origin-secret` 없이 Cloudtype 주소를 직접 호출하면 거부된다(T-8 ①). 백엔드 로그에 실제 방문자 IP(`x-real-ip`·`x-forwarded-for`)가 찍힌다.
  - 인증 단계가 없는 배포(Task 021)는 "헤더·경로"만 수행한다.
- **V-H (사람 확인)**: 무엇을 어디서 확인했는지, 날짜와 결과를 Task 아래 `기록:`에 남긴다. 비밀 값 자체는 적지 않는다.

---

### 4.6 백엔드 메모리 예산과 측정 지점 (Task 003 조사 반영)

운영비 기조는 "저예산(절약할수록 좋음)"이고 Cloudtype 구독 메모리는 512MB당 월 6,600원이다. 필요한 메모리는 추측하지 않고 **측정으로 정한다**. 아래 수치는 일반적인 추정이며 실측값이 아니다.

- **전제**: 무료 메모리(프리티어 1GB, 매일 꺼짐)와 구독 메모리는 따로 쓰고 한 서비스에 합쳐지지 않는다. 운영 서비스의 메모리 상한은 **구독한 양**뿐이다. Task 022 시험 서비스처럼 임시로 쓰는 서비스는 무료 리소스에 둔다.
- **추정**: Spring Boot 4(JPA·Security·Redis·Batch·Feign 등 의존성 전체)는 힙 150~300MB, 메타스페이스 100~150MB, 스레드·코드 캐시·네이티브 50~100MB로 컨테이너 전체가 약 400~600MB다. 서비스 목록 API만 있는 첫 배포는 부하가 작지만 기동 메모리는 의존성에 좌우된다.
- **JVM 옵션 원칙**: 힙은 컨테이너 한도의 약 50~60%로 제한하고 메타스페이스·스레드 스택·GC를 줄인다. 운영에서 불필요한 기능(springdoc UI 등)은 `prod`에서 끈다(Task 016에서 적용). 연결 풀은 약 5로 제한했다(Task 016). Tomcat 스레드는 기본값으로 두고(Task 016 결정, 연결 풀이 병목이라 값을 줄여도 효과가 작다), 힙 옵션은 yml이 아니라 Cloudtype 환경 변수(`JAVA_TOOL_OPTIONS`)로 Task 020·021에서 첫 배포 실측 후 정한다.
- **설계 원칙**: 수집·재계산은 chunk 단위로 처리하고 전체를 메모리에 올리지 않는다(Spring Batch). 계산 엔진은 후보 조합 수 상한(R11-30)으로 메모리·시간 상한을 함께 정한다. 대량 조회는 페이징·프로젝션을 쓴다. 새 의존성은 필요한지 한 번 더 본다.

| 측정 지점 | Task | 확인할 것 |
|---|---|---|
| 첫 배포 | 021 | 기동 직후와 API 호출 후 사용량(무료 1GB 기준 기본 사용량) |
| 엔진 완성 | 040 | 최악 입력·후보 상한 근처의 힙 사용량 |
| 첫 전체 수집 | 048 | 수집 사이클의 최대 사용량, DB 용량(Supabase 500MB 한도 대비) |
| MVP 배포 직전 | 092 | 운영 구성으로 다시 측정해 구독 메모리 단위 확정 |
| 성능 측정 | 100 | `mem_limit: 512m` 컨테이너에서 계산·수집·조회 부하로 OOM 여부 최종 판정 |

- 측정 지점에서 예산을 넘으면 먼저 튜닝과 설계(청크 축소, 후보 상한 조정, 의존성 제거)로 줄이고, 그래도 부족하면 구독 메모리를 올리거나(512MB 단위) 무거운 작업의 실행 위치를 바꾸는 방안을 사용자에게 올린다.
- 512MB에서 도는지는 Cloudtype 무료 1GB의 측정값으로는 확인되지 않으므로, 로컬에서 `docker run --memory=512m` 또는 Task 100의 측정용 컨테이너로 시험한다.

## 5. Phase 1 — 프로젝트 초기 설정 (골격 구축) · 22/23

**목표.**
- 모노레포 뼈대, 로컬 실행 환경, CI, 브랜치 규칙을 준비하고, 1주차 안에 배포 경로(Vercel `/api` → Cloudtype)를 첫 배포로 얇게 확인한다.
- 외부 계정·키·정책 확인(H2)을 끝내 B1·B3이 막히지 않게 한다.
- 무료 기간 전제: Cloudtype 무료 플랜은 매일 꺼지고 요청으로 재기동되지 않는다. MVP 배포 전 Cloudtype에는 첫 배포의 서비스 목록 API만 있고 수집 코드와 cron은 없다(R11-4, R-14 확정). 첫 전체 수집은 로컬에서 한다(Task 048).

#### Task 001: 기획 문서(PRD·TECH·ERD) 작성으로 요구사항 기준 확정 ✅
- 태그: [H] · PRD: H1 · 브랜치: `main`(커밋 `52a3841`)
- 구현 사항
  - [x] PRD v0.3(요구사항, 계산 규칙, 데이터 근거, 단계·작업 ID)
  - [x] TECH(T-1~T-8 확정, 호환성 함정)
  - [x] ERD v0.3(FK `ON DELETE CASCADE`, 참조 컬럼 인덱스)
- 완료 기준
  - V-H: 세 문서가 `main`에 커밋돼 있음을 확인했다(2026-10-03, `git log`).
  - ERD 보정은 F1 이후 Task 032에서 한다.

#### Task 002: 외부 서비스 계정 생성과 API 키 발급으로 연동 준비 ✅
- 태그: [H] · PRD: H2 · 선행: 없음
- 구현 사항
  - [x] TMDB API 키(비영리 용도)
  - [x] Google Cloud OAuth 클라이언트. 로컬 리다이렉트 URI는 `http://localhost:5173/api/login/oauth2/code/google`처럼 TECH 4절 경로 규칙에 맞추고(포트는 Vite 설정 확인 후), 운영 URI는 Task 092에서 추가
  - [x] Supabase 프로젝트(Session Pooler 5432), Redis Cloud 무료 DB, Cloudtype(무료), Vercel, GitHub 저장소 권한
  - [x] SMTP 시험용 메일 발신 계정(Gmail 앱 비밀번호). HTTPS 메일 API 키는 Task 022에서 SMTP 시험이 실패할 때만 발급한다(순환 의존 방지)
  - [x] 키는 저장소 밖(비밀번호 관리자 등)에만 보관. Gemini 키는 Task 085, Grafana Cloud 계정은 Task 096에서 발급한다
- 완료 기준
  - V-H: 서비스별 발급 여부·발급일·보관 위치(값 제외)를 기록했고, 저장소·채팅에 키가 없음을 확인했다.
- 기록: 2026-10-05 사용자가 완료를 보고했다. 키·비밀번호는 모두 저장소 밖(비밀번호 관리자)에 보관하고 값은 어디에도 적지 않는다. 서비스별 발급일은 사용자의 비밀번호 관리자 기록을 따른다. 확인한 설정: **Supabase** PostgreSQL 17.11(로컬 compose는 17.6이며 17.x 마이너 차이라 호환에 문제 없다고 보고 맞추지 않음), Session Pooler 5432, Data API는 켜 둔 상태(백엔드는 JDBC 직접 접속, 이 프로젝트는 Supabase 클라이언트 라이브러리를 쓰지 않음). **Redis Cloud** 8.6, eviction `allkeys-lru`, persistence 없음, TLS는 꺼 둔 상태(Task 016·020에서 Spring 접속 확인과 함께 켤지 정함), 프로토콜 RESP3, 비밀번호는 Redis Cloud가 자동 생성한 기본 사용자 비밀번호. **Cloudtype** 가입과 GitHub 저장소 연동 확인(`ottnavi`가 목록에 표시됨, 서비스는 아직 만들지 않음). **Gmail 앱 비밀번호**는 SMTP 시험용으로 발급(`MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`). GitHub 저장소는 소유자 계정으로 `admin` 권한이다. 변수별 등록 위치와 난수 생성 방법은 `docs/PHASE1_HUMAN_TASKS.md`의 "비밀 값 보관 규칙"에 정리했다. **주의**: Redis Cloud 무료 DB는 14일 동안 Redis 명령이 없으면 삭제된다(Task 003 `기록:`). 백엔드가 Redis를 쓰기 전까지 삭제되면 다시 만든다.

#### Task 003: 약관·운영 정책 조사로 표현과 제약 확정 ✅
- 태그: [H] · PRD: H2, R11-27 · 선행: 없음
- 구현 사항
  - [x] TMDB 약관: 비영리, 승인 로고·고지문, 6개월 보관, JustWatch 출처 표기 조건
  - [x] 7개 서비스 해지 예약 정책(R11-27, 안내 문구에만 영향)
  - [x] Cloudtype 무료·Hobby 조건, Supabase(500MB, 7일 비활성 일시정지)·Redis Cloud(30MB, 초당 100 ops) 한도
  - [x] Cloudtype 무료 플랜에서 운영 서비스 외에 서비스를 하나 더 만들 수 있는지(개수 제한). Task 022 SMTP 시험용 일회성 서비스의 전제다(R-15)
- 완료 기준
  - V-H: 항목별 출처 URL·날짜·결론을 기록했고, R11-27 결과를 10.2에 반영할지 사용자가 판단했다. 서비스 추가가 불가능하면 Task 022 진행 방식을 사용자에게 올렸다.
- 기록: 2026-10-05 완료. 항목별 출처 URL·확인일·원문 인용은 **`docs/TASK003_POLICY_PRICING.md`** 에 정리했다(확인됨 / 사용자 제공 / 2차 자료 / 모름으로 확인 수준을 표시). 결론 요약:
  - **TMDB 약관**(`themoviedb.org/api-terms-of-use`, 최종 수정 2023-10-20): 무료 API는 비상업 용도이고 상업 이용은 별도 서면 계약이 필요하다(**수익화 금지**). 고지 문구와 로고를 About에 넣고, TMDB 정보는 6개월을 넘겨 캐시하지 않는다(PRD FR-18과 일치). JustWatch 출처 표기는 약관이 아니라 Watch Providers API 문서(`developer.themoviedb.org/reference/movie-watch-providers`)에 있다.
  - **7개 서비스 해지 예약 정책(R11-27 확정)**: 7개 서비스의 일반 유료 구독을 직접 결제한 경우 결제 주기·잔여 기간 종료까지 이용할 수 있는 예약 해지다. 예외는 단정하지 않고 계정 화면 확인으로 안내한다: 무료 체험·프로모션(디즈니+는 즉시 발효될 수 있고 Apple TV+는 체험 종료 24시간 전 취소), 앱스토어·통신사·제휴사 결제(결제처 정책), 즉시 해지·환불(서비스별 조건)이다. 확인 근거는 넷플릭스(해지 안내), 디즈니+(이용 약관 2절 c항), Apple TV+(약관 I절, 이용 종료일은 구독 관리 화면의 만료일), 티빙(유료이용약관 제8·12~15조, 2026-08-03 시행), 웨이브(결제 약관 제8조 제3항), 왓챠(이용약관 제15조 제2항), 쿠팡(와우 FAQ, 이용 약관 제9·10조, 패스 FAQ)이다. **모르는 부분**은 쿠팡 와우 해지와 패스의 관계(FAQ끼리 불일치)와 와우 없이 패스를 단독 구독할 수 있는지이고, 단정하지 않고 계정 화면에서 확인하도록 안내한다. 화면 안내 문구 초안은 정리 문서에 있다.
  - **Cloudtype**(`cloudtype.io/ko/pricing`, 사용자 제공 스크린샷): 프리티어 무료(카드 등록 필요), 하비 월 6,600원부터, 프로 월 22,000원부터. 리소스 요금은 메모리 512MB 6,600원, CPU 1 vCPU 6,600원, 디스크 10GB 6,600원, 추가 트래픽 100GB 13,200원(월, VAT 포함). HTTP 타임아웃 프리티어 1분·하비 5분. 동시실행은 서비스 수(프리티어 4개, 하비 5개, 512MB당 +1). **무료 메모리와 구독 메모리는 따로 쓰고 서비스를 배포할 때 리소스를 고른다.** 커스텀 이미지 배포는 프로 전용이다. 운영비 기조는 "저예산(절약할수록 좋음)"이다.
  - **서비스 추가 가능 여부(R-15)**: 가능하다. 프리티어 동시실행 4개라 운영 서비스 외에 Task 022의 일회성 SMTP 시험 서비스를 무료 리소스로 만들 수 있다.
  - **Supabase 무료**(공식 문서): DB 500MB(초과 시 읽기 전용), 7일 비활성이면 일시정지(정지 후 1년까지 복구, 경고 이메일 약 1주 전). Egress에 외부 서버가 받는 DB 결과가 포함되는지는 문서에 없어 **모름**이다(대시보드 사용량으로 확인).
  - **Redis Cloud 무료**(공식 문서·지원 문서 원문): 30MB, 계정당 1개, 동시 연결 30개, 월 5GB, 100 ops/sec, CIDR 규칙 1개. **14일 연속 Redis 명령이 없으면 DB와 데이터가 영구 삭제되고 복구되지 않는다**(콘솔 접속은 활동이 아님, 경고 이메일은 보장되지 않음). 이 정책은 "평가 서비스 이용약관"에 정의돼 있고 지원 문서는 무료 DB를 운영 워크로드에 권장하지 않는다.
  - **R11-27**은 PRD 11절 27번을 확정으로 바꾸고 10.2 표에서 뺐다.
  - 후속 반영: TECH T-6(하비 5분), Task 020(R-12, Redis 생존 확인), Task 022(무료 리소스), 4.6(메모리 예산과 측정 지점), Task 021·040·048·100(측정 항목).

#### Task 004: KR 제공처 ID 확정과 요금표 조사로 매핑·가격 입력값 준비 ✅
- 태그: [H] · PRD: H2, FR-18, FR-19 · 선행: 002
- 구현 사항
  - [x] TMDB KR 제공처 목록에서 7개 서비스의 `provider_id`, 원본 라벨, tier(STANDARD/AD) 판정표
  - [x] 7개 서비스 STANDARD 단품 월 가격과 조사일
  - [x] 매핑표는 Task 033 시드 입력값, 가격표는 Task 061 관리자 입력값으로 넘긴다
- 완료 기준
  - V-H: 매핑표·가격표(출처, 조사일)와 매핑되지 않은 KR 제공처 목록을 기록했다.
- 기록: 2026-10-05 완료. TMDB `watch/providers/{movie,tv}?watch_region=KR&language=ko-KR`를 조회했다(v4 Read Access Token을 `Authorization: Bearer` 헤더로 보냄. 이 토큰은 `api_key` 쿼리로는 401). 영화·TV 목록의 ID는 같다.
  - **매핑표(Task 033 `tmdb_provider` 시드 입력값)**

    | 서비스 | provider_id | TMDB 원본 라벨 | tier | 비고 |
    |---|---|---|---|---|
    | Netflix | 8 | Netflix | STANDARD | |
    | Netflix 광고형 | 1796 | Netflix Standard with Ads | AD | MVP에서 사용하지 않음(O3) |
    | Disney+ | 337 | Disney Plus | STANDARD | 광고형 별도 ID 없음 |
    | TVING | 1883 | TVING | STANDARD | 광고형 별도 ID 없음 |
    | Watcha | 97 | Watcha | STANDARD | |
    | wavve | 356 | wavve | STANDARD | 광고형 별도 ID 없음 |
    | Coupang Play | 1881 | Coupang Play | STANDARD | **TV 목록에만 있고 영화 목록에는 없다**(TMDB 쪽 누락 가능, 수집 결과로 재확인) |
    | Apple TV+ | 350 | Apple TV | STANDARD | 라벨이 "Apple TV"이나 구독형이다 |

    광고형 라벨이 따로 있는 것은 Netflix(1796)뿐이다. 나머지 서비스의 광고형 요금제는 TMDB에서 STANDARD와 구분되지 않는다.
  - **가격표(Task 061 관리자 입력값, 조사일 2026-10-05, 표시 가격 그대로)**

    | 서비스 | STANDARD 요금제 | 월 가격(원) | 출처 | 확인 수준 |
    |---|---|---|---|---|
    | Netflix | 스탠다드 | 13,500 | help.netflix.com/ko/node/24926 | 공식. 기준일 표기 없음, "지역에 따라 세금이 추가될 수 있음" 안내 |
    | Disney+ | 스탠다드 | 9,900 (부가세 포함) | disneyplus.com/ko-kr/commerce/plans | 공식(사용자가 화면에서 확인) |
    | TVING | 스탠다드 | 13,500 | tving.com/bill/subscription/plan | 공식 |
    | Watcha | 베이직 | 7,900 | 공식 정리 페이지 없음, 결제 화면에서 직접 확인 | 직접 확인 |
    | wavve | 스탠다드 | 10,900 | wavve.com/voucher/?voucher-type=wavve | 공식 |
    | Coupang Play | 와우 멤버십(광고 포함) | 7,890 | namu.wiki 쿠팡플레이 문서(요금제 표) | **비공식**. 쿠팡플레이는 와우 회원만 볼 수 있어 와우 월 요금이 곧 이용 비용이다. 광고 없는 시청은 프리미엄 패스 3,900 추가(합계 11,790) |
    | Apple TV+ | 단일 | 6,500 | apple.com/kr/apple-tv | 공식 |

    STANDARD 기준은 광고 없는 요금제가 원칙이지만 Coupang Play는 TMDB에 ID가 하나뿐이고 가격이 와우 7,890 하나로 정해져 7,890을 쓴다(2026-10-05 사용자 결정). Watcha는 베이직만 있어 이를 STANDARD로 쓴다. 와우 이용자의 해지 안내는 R11-27 결론을 따르며 "와우–패스 관계"는 TASK003에서 모름으로 둔 그대로다.
  - **O3 참고 자료(MVP는 STANDARD 단품만이라 사용하지 않는다. O3 착수 때 다시 확인)**: Netflix 광고형 7,000·프리미엄 17,000. TVING 광고형 스탠다드 5,500·베이직 9,500. wavve 광고형 스탠다드 5,500·베이직 7,900. 번들(조사일 2026-10-05): 티빙+웨이브 더블 스탠다드 15,000, 티빙+웨이브 더블 광고형 스탠다드 7,000, 웨이브+티빙 더블 베이직 13,500, 디즈니+티빙 스탠다드 18,000(부가세 포함), 디즈니+티빙+웨이브 스탠다드 21,500(부가세 포함). 쿠팡 패스: 프리미엄 3,900, 스포츠 12,400(와우 회원가, 일반 회원가 19,300).
  - **매핑되지 않은 KR 제공처 31개**: 7개 서비스 밖이다. 이 중 국내 구독 후보로 볼 만한 것은 Amazon Prime Video(119), Google Play Movies(3)이며 둘 다 범위 밖이다. 나머지는 해외 소규모·다큐·독립영화 서비스다(상위 30개: JustWatch TV 2285, GuideDoc 100, Curiosity Stream 190, DOCSVILLE 475, Plex 538, WOW Presents Plus 546, Magellan TV 551, BroadwayHD 554, Filmzie 559, Dekkoo 444, True Story 567, DocAlliance Films 569, Hoichoi 315, Eventive 677, Cultpix 692, Takflix 1771, Sun Nxt 309, Crunchyroll 283, Jolt Film 2330, FOUND TV 2478, MUBI 11, Bloodstream 2555, MovieMe 2565, KableOne 2603, CaixaForum+ 2620, Artiflix 2623, Artify 2685, Pijama Films 2765, Filmtap 2782. 표시 우선순위 순).

#### Task 005: 도메인 ottnavi.shop 1년 구매로 운영 주소 확보 ⬜
- 태그: [H] · PRD: 11절 기획 확정(도메인), R11-3 · 선행: 없음 · 기한: Task 092 착수 직전, 2026-11-04 이전(1년 사용 기간이 구매일부터 시작하므로 구매를 미룬다. 구매 전에는 `*.vercel.app`·Cloudtype 기본 주소를 쓴다)
- 구현 사항
  - [ ] 포트폴리오용이라 **1년만 쓰는 것을 기준**으로 한다. 등록업체별 **첫해 가격**을 비교하고(갱신 가격은 기록만 한다), 도메인 월 환산 비용(첫해 가격 ÷ 12)을 Cloudtype 구독료(512MB당 월 6,600원)와 합쳐 저예산 범위인지 확인한다(고정 상한은 두지 않는다)
  - [ ] 구매. 1년 선결제만 지원하고 자동 갱신이 없다(이전 경험상 만료 전에 업체가 연장하라고 연락해 온다). 자동 갱신이 있다면 꺼 둔다. 소유자 이메일은 만료·연장 안내가 오는 주소라 평소 확인하는 메일을 쓴다. DNS 연결은 Task 092
  - [ ] 만료 한 달 전 알림을 남기고, 만료되면 `*.vercel.app` 기본 주소로 돌아가도록 `APP_FRONTEND_ORIGIN`·`OAUTH2_REDIRECT_BASE_URL`·Google OAuth 운영 리디렉션 URI·`vercel.json`의 주소만 바꾸면 되는지(코드 변경 없음)를 포트폴리오 문서에 적어 둔다
- 완료 기준
  - V-H: 업체·첫해 가격(·갱신 가격 참고)·구매일·만료일을 기록했다.

#### Task 006: 백엔드 Gradle 프로젝트 골격 생성으로 빌드 기반 마련 ✅
- 태그: [B] · PRD: B0 · 선행: 없음 · 브랜치: `main` 직접 커밋(초기 세팅)
- 구현 사항
  - [ ] `backend/settings.gradle`, `backend/build.gradle`, Gradle wrapper(`gradlew.bat` 포함). Java 17, Spring Boot 4.1
  - [ ] `backend/src/main/java/com/ottnavi/OttnaviApplication.java`, 컨텍스트 로드 테스트(DB가 필요해지는 Task 016에서 Testcontainers로 전환)
  - [ ] 패키지(`global`, `infra`, `engine`, `collect`, `admin`, 도메인)는 실제 클래스가 생길 때 만든다
- 완료 기준
  - V-B: `.\gradlew.bat build`가 성공한다.
- 기록: 2026-10-04 완료(커밋 `23fe649`, `main` 직접 커밋). Spring Boot 4.1.1, Gradle 래퍼 9.7.1, Java 17. `.\gradlew.bat build` 통과, 메인 클래스 `com.ottnavi.OttnaviApplication`(패키지 루트 `com.ottnavi`). 설정 파일은 `application.yml`로 통일했다(Initializr 기본값 `application.properties`를 바꿈). 의존성은 `starter-webmvc`와 테스트 기본뿐이고 나머지는 Task 016에서 추가한다.

#### Task 007: 프론트 Vite 프로젝트 골격 생성으로 빌드 기반 마련 ✅
- 태그: [F] · PRD: F0 · 선행: 없음 · 브랜치: `main` 직접 커밋(초기 세팅)
- 구현 사항
  - [ ] `frontend/` Vite + React + TypeScript 템플릿(npm), `package-lock.json` 커밋
  - [ ] `engines.node`를 Node 20.19+ 또는 22.12+로 고정(TECH 6절)
  - [ ] 스크립트 `dev`, `build`, `lint`, `test` 자리(도구 구성은 Task 017)
- 완료 기준
  - `npm run build` 성공, V-F: 루트(`/`)가 에러 없이 로드된다.
- 기록: 2026-10-04 완료(커밋 `5093728`에 Task 017과 함께 포함, `main` 직접 커밋). Vite 8.3, React 19, TypeScript 6.0. `engines.node`는 `^20.19.0 || >=22.12.0`. `npm run build` 통과, 목업 모드 V-F에서 콘솔 에러 0건. `test` 스크립트는 Task 017에서 no-op을 `vitest run`으로 교체했다.

#### Task 008: 로컬 Docker Compose와 .env.example 작성으로 실행 환경 통일 ✅
- 태그: [B] · PRD: B0 · 선행: 006 · 브랜치: `feature/b0-local-env`(PR #1. `develop`을 먼저 만들기로 해서 `main` 직접 커밋 예외를 쓰지 않았다)
- 구현 사항
  - [x] `infra/docker-compose.yml`: PostgreSQL 17.6·Redis 8.6만, 볼륨, 헬스체크(4.1 ①). 운영(Supabase·Redis Cloud)과 같은 버전에 맞췄다. 백엔드 컨테이너는 k6용으로 Task 100에서 추가
  - [ ] 루트 `.env.example`: 이름만 둔다. `APP_FRONTEND_ORIGIN`, `OAUTH2_REDIRECT_BASE_URL`, `JWT_SECRET`, `ADMIN_BATCH_TOKEN`, `ORIGIN_SECRET`, DB·Redis·TMDB·Google OAuth·메일 변수. 프론트는 `VITE_USE_MOCK`만
  - [ ] `.gitignore`가 `.env`·`.env.*`를 막는지 확인
- 완료 기준
  - `docker compose -f infra/docker-compose.yml up -d` 후 두 컨테이너가 healthy이고 `psql`·`redis-cli` 접속이 된다.
- 기록: 2026-10-04 완료(PR #1, 스쿼시 커밋 `667efd4`). `postgres:17.6-alpine`(`NULLS NOT DISTINCT` 동작 확인), `redis:8.6-alpine`(8.6.7, `SET ... EX`·`TTL` 확인). 접속 정보는 환경 변수 + 로컬 전용 기본값(`ottnavi`)이고 포트는 `127.0.0.1`에만 공개한다. compose는 `.env`를 `infra/` 기준으로 찾으므로 루트 `.env`를 쓰려면 `--env-file .env`를 붙인다. `.env.example`은 이름만 담았고 `.gitignore`(`.env`·`.env.*` 차단, `!.env.example` 예외)를 확인했다. Redis는 Redis Cloud에서 8.6으로 생성해야 로컬과 맞는다(Task 002).

#### Task 009: vercel.json 초안 작성으로 /api 프록시·비밀 헤더·SPA 라우팅 준비 ✅
- 태그: [F] · PRD: F0 · 선행: 007 · 브랜치: `feature/f0-vercel-json`(제안. `develop`이 먼저 생겨 `main` 직접 커밋 예외를 쓰지 않는다)
- 관련: TECH T-8, TECH 6절(rewrite 순서·캐시·`routes`+`rewrites`)
- 구현 사항
  - [x] `frontend/vercel.json`: `/api/:path*` → Cloudtype 백엔드를 먼저, SPA 대체(`/index.html`)를 뒤에 둔다. 백엔드 주소는 Task 021까지 자리표시값
  - [x] `routes[].transforms`로 `/api` 요청에 `x-origin-secret` 헤더를 환경 변수 `ORIGIN_SECRET`에서 주입(`type: request.headers`, `op: set`, `env: ["ORIGIN_SECRET"]`). 비밀 값은 파일에 쓰지 않는다
  - [x] `/api` 프록시의 Vercel rewrite 캐시 비활성화(T-8 ②). `routes` 항목의 `respectOriginCacheControl: false`로 구현한다(`@vercel/routing-utils` 스키마에서 확인, `x-vercel-enable-rewrite-caching: 0` 헤더와 같은 목적). 실제 효과는 Task 021 배포 후 응답 헤더로 확인한다
  - [x] `routes`와 `rewrites`·`headers`는 함께 쓰지 않는다(Vercel CLI 소스에서 함께 정의하면 스키마 검증이 실패하도록 둔 것을 확인). 그래서 처음부터 `routes` 하나(`/api` 프록시 → `{ "handle": "filesystem" }` → SPA 대체)로 작성한다. `transforms` 형식은 Vercel 공식 도구 `@vercel/config`가 만든 JSON(`env`가 transforms 항목 안, `args`는 `$ORIGIN_SECRET`)과 같고 `routesSchema` 검증을 통과했다
- 완료 기준
  - JSON 문법이 유효하다(`@vercel/routing-utils` 스키마 검증 통과). `routes` 단일 형식이라 혼용 여부는 시험하지 않고, 미리보기 배포 성공은 Task 014, 실제 프록시 동작과 적용 순서는 Task 021에서 확인한다.
- 기록: 2026-10-04 완료(PR #4, 스쿼시 커밋 `854a754`). `frontend/vercel.json`을 `routes` 단일 형식으로 작성: `/api/(.*)` → `https://REPLACE_BACKEND_HOST/api/$1`(`respectOriginCacheControl: false`, `x-origin-secret`을 `transforms`로 `$ORIGIN_SECRET` 주입) → `{ "handle": "filesystem" }` → `/index.html`. `routesSchema`·`normalizeRoutes`·`getTransformedRoutes` 검증 통과, `transforms` 형식은 공식 `@vercel/config` 출력과 동일. **`REPLACE_BACKEND_HOST`는 Task 021에서 반드시 교체한다**(코드래빗이 Major로 지적했으나 의도된 자리표시값이라 유지, 운영 배포 전 확인 항목은 Task 021 완료 기준). 같은 PR에서 `frontend/.prettierrc`에 `endOfLine: auto`를 추가했다(Windows `core.autocrlf`로 `package.json`이 CRLF가 되면 `format:check`가 실패하던 문제).

#### Task 010: CI 골격 작성으로 PR 자동 검증 기반 마련 ✅
- 태그: [B][F] · PRD: B0, F0 · 선행: 006, 007 · 브랜치: `feature/b0-ci-skeleton`(제안. `develop`이 먼저 생겨 `main` 직접 커밋 예외를 쓰지 않는다)
- 구현 사항
  - [x] `.github/workflows/backend-ci.yml`: JDK 17, Gradle 캐시, `./gradlew test`(ubuntu 러너 Docker로 Testcontainers)
  - [x] `.github/workflows/frontend-ci.yml`: Node 버전 고정, `npm ci`, `lint`, `test`, `build`. Task 017에서 Vitest를 구성하기 전까지 `test`는 `npm run test`(Task 017에서 `vitest run`으로 구성됨)를 실행한다. 추가로 `format:check`(Prettier)를 넣을지는 이 Task에서 정한다
  - [x] 트리거: `develop`·`main` 대상 PR과 두 브랜치 push. 필수 상태 검사로 쓸 job 이름 고정
  - [x] (제안) `main` 대상 PR에서만 `frontend/vercel.json`에 `REPLACE_BACKEND_HOST`가 있으면 실패하는 한 단계를 `frontend-ci`에 넣는다. `develop` PR은 통과해야 하므로 `github.base_ref == 'main'`일 때만 실행한다(Task 021의 사람 확인을 자동으로도 막는 이중 안전장치). 넣을지는 Task 010 계획에서 사용자가 정한다
- 완료 기준
  - 로컬에서 워크플로와 같은 명령(`.\gradlew.bat build`, `npm ci; npm run build`)이 성공하고 YAML 문법이 유효하다. GitHub Actions 실행 성공은 이 Task의 PR(`develop` 대상)에서 확인하고, 그 실행 링크를 Task 012 `기록:`에 남긴다(첫 푸시는 이미 끝났다).
- 기록: 2026-10-04 완료(브랜치 `feature/b0-ci-skeleton`). `backend-ci`(JDK 17 temurin, `gradle/actions/setup-gradle`, `./gradlew build`)와 `frontend-ci`(Node 24, `npm ci`·`lint`·`format:check`·`test`·`build`)를 만들었다. Node 22(npm 10)는 `npm ci`가 `Missing: msw@2.15.0`으로 실패해(vitest 선택적 peer `msw ^2`와 설치된 msw 3 불일치, npm 11은 통과) 로컬과 같은 Node 24로 맞췄다. 트리거는 `develop`·`main` 대상 PR과 두 브랜치 push이고 경로 필터·단어 트리거는 쓰지 않았다(필수 검사 미보고 방지). 같은 ref의 중복 실행은 `concurrency`로 취소한다. `main` 대상 PR에서만 `vercel.json`의 `REPLACE_BACKEND_HOST` 잔존 시 실패하는 단계를 넣었다(사용자 결정). `backend/gradlew`가 git에 실행 권한 없이(100644) 올라가 있어 러너에서 `chmod +x gradlew` 단계를 둔다. 로컬에서 `gradlew build`, 프론트 `npm ci`·`lint`·`format:check`·`test`·`build`(각 종료 코드 0)와 YAML 문법을 확인했다. **GitHub Actions 실행은 이 Task의 PR #6에서 확인했다(두 워크플로 성공, 실행 링크는 Task 012 `기록:`).** CD(`backend-deploy.yml`)는 `main` push + `workflow_dispatch`로 Task 020·021에서 만든다.

#### Task 011: docs/api 계약 골격 작성으로 API 계약 출발점 마련 ✅
- 태그: [B] · PRD: F1 선행 준비 · 선행: 없음 · 브랜치: `main` 직접 커밋(초기 세팅)
- 구현 사항
  - [ ] `docs/api/openapi.yaml`: `servers`(`/api`), `paths: {}`, `bearerAuth`, `tags`(catalog, user, wishlist, product, plan, auth, notification, admin — api-contract.md), 공통 `ProblemDetail`(`errorCode` 확장)·페이지네이션 스키마
  - [ ] `docs/api/error-codes.md`: 공통 코드(`VALIDATION_FAILED`, `UNAUTHORIZED`, `FORBIDDEN`, `RATE_LIMIT_EXCEEDED`, `INTERNAL_ERROR`, `EXTERNAL_API_ERROR`, `TITLE_NOT_FOUND`)와 TECH 신규 코드(`PLAN_DRAFT_STALE`, `PLAN_DRAFT_NOT_FOUND`, `BATCH_ALREADY_RUNNING`, `BATCH_ALREADY_COMPLETED`). 열: HTTP 상태, 의미, 화면 메시지
- 완료 기준
  - 지금 쓸 수 있는 검사 도구로 OpenAPI 3 문법 검사를 통과한다(예: `npx @redocly/cli lint docs/api/openapi.yaml`, 도구는 제안). orval 생성 확인은 Task 017에서 한다.
- 기록: 2026-10-04 완료(커밋 `4eeca03`, `main` 직접 커밋). OpenAPI 3.0.3(`nullable` 표기 때문), `redocly lint` 에러 0건(경고 11건: 미사용 컴포넌트 10건, `info.license` 1건. operation이 생기면 미사용 경고는 사라진다). `ProblemDetail`의 `fieldErrors`(필드 오류 목록) 구조는 제안값이다. springdoc이 생성하는 버전과 다르면 Task 023의 계약 대조 테스트에서 맞춘다.

#### Task 012: develop 브랜치 생성과 보호 규칙 설정으로 작업 흐름 고정 ✅
- 태그: [H] · PRD: — (git.md) · 선행: 006~011
- 구현 사항
  - [x] 미커밋 문서(1절 저장소 상태)와 초기 세팅 커밋을 `origin/main`에 푸시
  - [x] `main`에서 `develop`을 만들어 푸시. 기본 브랜치는 `main` 유지
  - [x] `main`·`develop` 보호: 직접 푸시 금지, PR 필수, `backend-ci`·`frontend-ci` 통과 필수, 강제 푸시·삭제 금지(리뷰 승인 필수는 두지 않음). CI 경로 필터를 쓰면 필수 검사가 보고되지 않아 PR이 막힐 수 있으니 필터 사용 여부를 정한다
  - [x] Secret scanning·push protection 사용 가능 여부 확인 후 켬
- 완료 기준
  - Task 010의 `develop` 대상 PR에서 GitHub Actions의 두 워크플로가 성공했다(실행 링크 기록, Task 010 검증 이관분). 첫 푸시는 이미 끝났고 `frontend-ci`의 `test`는 Task 017에서 `vitest run`으로 구성됐다.
  - V-H: 보호 규칙 설정을 기록했고, `develop` 직접 푸시가 거부되는 것을 확인했다.
- 기록: 2026-10-04 완료. `main`·`develop`을 `origin`에 푸시했고 기본 브랜치는 `main`이다. 저장소는 공개이고 Settings → Advanced Security에서 Secret Protection·Push protection이 켜져 있음을 확인했다. 보호 규칙은 Rulesets의 `protect-main-develop`(id 24456595, Enforcement: Active, 대상 `refs/heads/main`·`refs/heads/develop`, bypass 없음)이고 규칙은 삭제 금지(`deletion`), 강제 푸시 금지(`non_fast_forward`), PR 필수(`pull_request`, 승인 0, 병합 방식은 Merge·Squash·Rebase 모두 허용), 필수 상태 검사(`required_status_checks`: `backend-ci`·`frontend-ci`, GitHub Actions 통합, `strict`는 꺼서 "병합 전 최신 브랜치" 조건은 두지 않음)다. CI 경로 필터는 쓰지 않았다(필수 검사가 보고되지 않아 PR이 막히는 것을 피함). `develop` 직접 푸시 거부 확인: 규칙 설정 전에는 확인용 빈 커밋 `acabfaa`(`chore: 보호 규칙 확인용`)가 그대로 푸시돼 규칙이 없음을 알았고, 설정 후에는 `remote: error: GH013: Repository rule violations found ... Changes must be made through a pull request.`로 거부됐다(`gh api`로 규칙 내용도 확인). 원격 `develop`에는 빈 커밋 `acabfaa`가 남아 있다(파일 변경 없음, 강제 푸시로 지우지 않고 그대로 둠). Task 010 PR #6의 Actions 실행: `backend-ci` run `37195614998`, `frontend-ci` run `37195615009` 성공(처음 실패한 `37194586850`은 Node 22의 `npm ci` 오류, Task 010 `기록:` 참고).

#### Task 013: Claude Code 플러그인 3종 설치로 작업 보조 도구 준비 ✅
- 태그: [H] · PRD: — · 선행: 없음
- 구현 사항
  - [x] `/plugin install feature-dev@claude-plugins-official`
  - [x] `/plugin install frontend-design@claude-plugins-official`
  - [x] `/plugin install skill-creator@claude-plugins-official`
  - [x] 노출된 스킬·에이전트 이름을 4.3에 반영
- 완료 기준
  - V-H: 설치 상태와 이름을 기록했다.
- 기록: 플러그인 설치 확인(2026-10-03), `.claude/settings.json`(`enabledPlugins`에 세 플러그인 `true`, 미커밋이라 Task 012 초기 세팅 커밋에 포함). `~/.claude/plugins/installed_plugins.json`에 세 플러그인이 `scope: project`, `projectPath: ottnavi`로 등록됨. 스킬 `feature-dev:feature-dev`, `frontend-design:frontend-design`, `skill-creator:skill-creator`, 에이전트 `feature-dev:code-explorer`, `feature-dev:code-architect`, `feature-dev:code-reviewer`.

#### Task 014: Vercel 프로젝트 연결과 Production Branch 설정으로 프론트 배포 경로 준비 ✅
- 태그: [H] · PRD: F0, B1 · 선행: 012(009 포함) · 기한: 2026-10-07(R-17)
- 구현 사항
  - [x] GitHub 저장소 연결, Root Directory = `frontend`, Production Branch = `main`(`develop`·PR은 미리보기만)
  - [x] 빌드 명령·Node 버전을 CI와 같게(Node **24**. CI를 24로 맞춘 이유는 Task 010 `기록:` 참고, Node 22(npm 10)는 `npm ci`가 실패한다)
  - [x] R-17 실측: Task 009의 `vercel.json`(`routes` 단일 형식, 혼용하지 않음)으로 미리보기 배포가 설정 오류 없이 만들어지는지 확인한다. 백엔드 주소는 자리표시값이라 `/api` 프록시 동작과 적용 순서는 Task 021에서 본다
- 완료 기준
  - V-H: 설정값을 기록했고, PR(또는 `feature/*` 브랜치 푸시)로 생긴 미리보기 URL의 루트 페이지가 열린다. `develop`은 보호 규칙(Task 012) 때문에 직접 푸시할 수 없다.
  - R-17: Task 009가 이미 `routes` 단일 형식이라 혼용 여부를 시험하지 않는다. 미리보기 배포가 설정 오류 없이 만들어지는지만 확인해 `기록:`에 남기고, 실패하면 오류 메시지를 근거로 Task 009를 고친다. 어느 쪽이든 10.3 R-17을 갱신한다.
- 기록: 2026-10-05 완료. GitHub 저장소 `msw-Hub/ottnavi`를 Vercel에 연결했고 **Root Directory는 `frontend`**, **Production Branch는 기본값 `main`**(Environments의 Production에서 확인, 저장소 기본 브랜치가 `main`이라 자동 지정), **Node.js Version은 24.x**(CI와 동일, 기본값으로 설정돼 있음), 프리셋은 Vite로 자동 인식돼 `npm run build`·출력 `dist`가 기본값으로 들어갔다. 연결 직후 `main`(커밋 `23fe649`)이 Production으로 먼저 배포됐는데, 이때 `main`에는 `vercel.json`이 없었다(`vercel.json`은 PR #4로 `develop`에만 병합됨). **R-17 실측**은 PR #7의 미리보기(브랜치 `feature/docs-human-tasks-status`, 커밋 `adba3e1`)로 했다: 빌드(`vite build`, 21개 모듈)가 `Build Completed`·`Deployment completed`로 끝났고 `vercel.json` 검증 오류는 없었다. `routes` 단일 형식이 설정 오류 없이 배포됐고, `ORIGIN_SECRET` 환경 변수가 없어도 배포는 실패하지 않았다(Task 015에서 등록). 미리보기 루트 페이지(`/`)가 열린다. `/api`(슬래시 없음)는 프록시 규칙 `^/api/(.*)$`에 걸리지 않아 SPA 화면이 나오는 것이 정상이고, `/api/public/ott-services`는 SPA 화면이 아니라 `502 DNS_HOSTNAME_NOT_FOUND`(자리표시 호스트 `REPLACE_BACKEND_HOST`로 프록시 시도, 요청 처리 지역 `icn1`)가 나와 `/api` 프록시가 SPA 대체보다 먼저 적용됨을 확인했다. 혼용(`routes`+`rewrites`)은 시험하지 않았다. 빌드 로그의 `engines` 경고(새 메이저 Node가 나오면 자동 상향될 수 있다는 안내)는 정상이다. `/api` 프록시 동작과 헤더 주입의 실제 확인은 Task 021이다.

#### Task 015: 비밀 값 저장 위치 정리와 GitHub·Vercel·로컬 등록으로 키 노출 방지 ✅
- 태그: [H] · PRD: 6절 보안 · 선행: 002, 014
- 관련: TECH 3절(cron 직접 호출), 4절, T-7, T-8
- 범위: 준비 구간에는 GitHub·Vercel·로컬만 등록한다. Cloudtype 서비스 생성·환경 변수 등록·배포 토큰(R-12)은 Task 020에서 한다.
- 구현 사항
  - [x] 저장 위치표(값 제외). Cloudtype 항목은 표에만 적고 등록은 Task 020
    - GitHub Actions secrets: `ADMIN_BATCH_TOKEN`, `ORIGIN_SECRET`(cron이 Cloudtype 직접 호출 시 사용)을 지금 등록. Cloudtype 백엔드 주소·배포 토큰은 Task 020
    - Vercel 환경 변수: `ORIGIN_SECRET`(`routes[].transforms`가 읽음)을 **Production과 Preview 환경 모두**에 등록(Task 094의 미리보기 확인이 Preview 값을 쓴다)
    - Cloudtype 환경 변수(Task 020에서 등록): DB, Redis, TMDB, Google OAuth, `JWT_SECRET`, `ADMIN_BATCH_TOKEN`, `ORIGIN_SECRET`, `APP_FRONTEND_ORIGIN`, `OAUTH2_REDIRECT_BASE_URL`, 메일
    - 로컬: `.env`(커밋 금지)
  - [x] `JWT_SECRET` 256비트 이상, `ADMIN_BATCH_TOKEN`·`ORIGIN_SECRET`은 긴 난수로 이때 만든다. `VITE_` 변수에 비밀 값 금지
  - [x] 노출 시 재발급 절차 한 줄씩
- 완료 기준
  - V-H: 저장 위치표와 GitHub·Vercel(Production·Preview)·로컬 등록일을 기록했고, 저장소에 비밀 값이 없음을 확인했다.
- 기록: 2026-10-05 완료(값은 어디에도 적지 않는다).
  - **저장 위치표**

    | 변수 | 비밀번호 관리자 | GitHub Actions secrets | Vercel | 로컬 `.env` | Cloudtype(Task 020) |
    |---|---|---|---|---|---|
    | `ORIGIN_SECRET` | ✅ | ✅ | ✅ Production·Preview(사용자 보고) | ✅ | 같은 값 등록 |
    | `ADMIN_BATCH_TOKEN` | ✅ 개발용·운영용 | ✅ (운영용) | — | ✅ (개발용) | 운영용 등록 |
    | `JWT_SECRET` | ✅ 개발용·운영용 | — | — | ✅ (개발용) | 운영용 등록 |
    | 운영 DB(Supabase)·Redis(Redis Cloud) 접속 정보 | ✅ | — | — | **넣지 않음**(비움) | 등록 |
    | TMDB·Google OAuth·메일 | ✅ | — | — | ✅ | 등록 |

  - **등록 결과**
    - GitHub Actions secrets: `ADMIN_BATCH_TOKEN`, `ORIGIN_SECRET`을 2026-10-05 05:39(KST)에 등록했다. `gh secret list`로 이름과 등록 시각을 확인했다(값은 볼 수 없다).
    - Vercel: `ORIGIN_SECRET`을 2026-10-05에 등록했다(사용자 보고). 실수로 삭제한 뒤 다시 등록하려 하자 "이미 존재한다"는 응답이 와 변수가 남아 있음을 확인했다. Production·Preview 적용 범위와 GitHub secrets와 같은 값인지는 이 Task에서 직접 확인하지 못했고 Task 021(`/api` 프록시 헤더 주입)과 Task 094(미리보기)에서 검증한다.
    - 로컬 `.env`: 저장소 루트에 만들었다. `.gitignore:8`에 걸려 추적되지 않고 추적되는 env 파일은 `.env.example`뿐이다. 변수 이름은 `.env.example`과 같다. `JWT_SECRET`·`ADMIN_BATCH_TOKEN`·`ORIGIN_SECRET`은 각각 44자(32바이트 Base64, 256비트)이고 서로 다른 값이다.
  - **정리한 점**: `.env`에 운영 Supabase·Redis Cloud 접속 정보(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PASSWORD`)가 들어 있어 비웠다(`DB_NAME`, `DB_PORT`, `REDIS_PORT`도 비어 있음). 비워 두면 docker compose·로컬 프로필이 로컬 기본값을 쓴다. 이를 두면 `bootRun`·Flyway가 운영 DB에 붙을 수 있고, compose가 `--env-file .env`로 읽으면 로컬 PostgreSQL 컨테이너가 Supabase 계정으로 만들어진다.
  - **저장소 검사**: `git grep -i -E "secret|password|token"`(`docs/`, `.env.example` 제외) 결과에 비밀 값은 없다(변수 이름, 설명 문구, `vercel.json`의 `$ORIGIN_SECRET` 참조, compose의 로컬 기본값 `ottnavi`뿐). `git status`에 `.env`는 나타나지 않는다.

#### Task 016: 백엔드 의존성·프로필·임시 보안 설정 구성으로 공통 실행 기반 마련 ✅
- 태그: [B] · PRD: B0 · 선행: 006, 008, 012 · 브랜치: `feature/b0-backend-setup`
- 관련: TECH 6절, backend.md 개발 환경·기술 스택
- 구현 사항
  - [x] `backend/build.gradle` 의존성 전체
    - `spring-boot-starter-webmvc`, `-data-jpa`, `-data-redis`, `-validation`, `-actuator`, `-security`, `-security-oauth2-client`, `-flyway` + `flyway-database-postgresql`, `-batch-jdbc`(이름 재확인, 1절)
    - Spring Cloud OpenFeign 2025.1.2 이상 BOM, springdoc-openapi, Bucket4j(로컬·Redis), ShedLock(JDBC), jjwt(`jjwt-jackson`은 runtime), PostgreSQL 드라이버
    - 테스트: `testcontainers-postgresql`(패키지 `org.testcontainers.postgresql.*`), Redis 컨테이너, WireMock(Jetty 충돌 시 `wiremock-jetty12`)
    - 메일·Thymeleaf·GreenMail은 넣지 않는다. 2단계 Task 072에서 추가한다(MVP 범위 번짐 방지)
  - [x] `application.yml` / `-local.yml` / `-prod.yml`. 운영 값은 `${ENV_VAR}`. `spring.batch.jdbc.initialize-schema=never`, `spring.batch.job.enabled=false`, `ddl-auto=validate`, Hikari 최대 풀 약 5, `server.forward-headers-strategy=framework`
  - [x] **임시 보안 설정**: security 스타터를 넣으면 기본 동작이 전 경로 인증 요구이므로, `global/security/SecurityConfig`에 STATELESS, csrf·formLogin·httpBasic 비활성, 전 경로 `permitAll`을 둔다. Task 053에서 실제 규칙으로 교체한다는 주석을 단다
  - [x] Testcontainers 통합 테스트 베이스(PostgreSQL, Redis). 빈 목킹은 `@MockitoBean`(`@MockBean` 제거됨)
- 완료 기준
  - V-B: Testcontainers로 컨텍스트 로드 테스트가 통과한다.
  - V-API: local 프로필 기동 시 Flyway·Batch·Security 관련 오류가 없고 기본 로그인 폼·생성 비밀번호가 나오지 않는다.
- 기록: 2026-10-06 완료(브랜치 `feature/b0-backend-setup`). 상세 절차와 근거는 `docs/TASK016_GUIDE.md`.
  - 의존성: Boot 4.1.1 BOM에 Spring Cloud 2025.1.3, springdoc 3.1.1, Bucket4j 8.21.0, ShedLock 7.10.1, jjwt 0.13.0, WireMock `wiremock-standalone` 3.13.2를 더했다. `spring-boot-starter-batch-jdbc` 아티팩트는 Maven Central에서 4.1.1 존재를 확인했고 `compileJava`가 통과했다(context7로는 이름을 확인하지 못했다, 1절 해소). 메일·Thymeleaf·GreenMail은 제외했다. Lombok을 추가했다(목록에는 없었으나 backend.md가 전제). `dependencyInsight` 결과 Jackson 2는 `jjwt-jackson`·springdoc(swagger-core)로만 들어오고 Boot는 Jackson 3다. bucket4j-lettuce와 Lettuce 7 호환은 미확인이라 요청 제한 구현 Task에서 검증한다.
  - 결정: OAuth2 Google 등록·`JWT_SECRET` 참조는 Task 053으로 미룬다(첫 배포 기동 실패 방지). `spring.profiles.default: local`, `spring.application.name: ottnavi`를 뒀다. Tomcat 스레드는 기본값이다. JVM 힙은 yml이 아니라 Cloudtype 환경 변수(`JAVA_TOOL_OPTIONS`)로 Task 020·021에서 첫 배포 실측 후 정한다(첫 배포는 프리티어 1GB 기준, 스왑은 플랫폼 허용 여부 미확인이라 계획에서 뺀다). Redis health 대응은 Task 020에서 Redis Cloud 생존을 확인한 뒤 판단한다. prod에서 springdoc을 끈다.
  - 검증: V-B는 로컬 compose를 끈 상태에서 `OttnaviApplicationTests`(`IntegrationTestSupport` 상속)가 통과했고 Testcontainers의 `postgres:17.6-alpine`·`redis:8.6-alpine` 기동을 로그로 확인했다. `.\gradlew.bat build`도 통과했다. V-API는 local 프로필 기동 5.6초, `/actuator/health` 200 UP, `/login` 404, `/v3/api-docs` 200이며 Flyway는 `No migrations found` WARN만 낸다(V1은 Task 019).
  - PR #9 CodeRabbit 반영: `IntegrationTestSupport`에 `@SpringBootTest`를 둬 상속만으로 컨텍스트가 뜨게 했다. Redis Cloud 무료 플랜은 TLS를 쓸 수 없다고 해서(사용자 확인) prod yml에 `ssl.enabled`를 넣지 않고 사유를 주석과 `TASK003_POLICY_PRICING.md`에 남겼다. frontend 차단 hook은 문자열 검사라 완전 차단이 아니며 실수 방지용이라는 한계를 스크립트 주석에 적었다.
  - 발견: 베이스를 상속하지 않으면 테스트가 local 프로필(compose DB)에 붙어 로컬에서만 통과한다(TECH 6절에 추가). springdoc이 `openapi 3.1.0`을 출력해 계약(3.0.3)과 달라 Task 023 계약 대조 테스트 때 맞춘다.

#### Task 017: 프론트 개발 도구와 API 생성 파이프라인 구성으로 목업 개발 준비 ✅
- 태그: [F] · PRD: F0 · 선행: 007, 011, 012 · 브랜치: `feature/f0-frontend-setup`(실제로는 `develop` 생성 전에 진행해 커밋 `5093728`에 `main` 직접 커밋으로 들어갔고, `@hookform/resolvers` 추가분만 PR #1에 담겼다)
- 구현 사항
  - [ ] Tailwind v4, shadcn/ui(`src/components/ui/`), React Router, TanStack Query, Zustand, React Hook Form·Zod, ESLint·Prettier, Vitest·RTL
  - [ ] `frontend/orval.config.ts`: 입력 `../docs/api/openapi.yaml` 하나(초안 파일 없음, `x-planned` operation도 생성 대상, 4.2), 출력 `src/api/generated/`, `react-query`, **`httpClient: 'axios'`** + `override.mutator`(`src/api/http.ts`), MSW 생성
  - [ ] `src/api/http.ts`(베이스 `/api`), `src/mocks/`(`VITE_USE_MOCK=true`면 `worker.start()` await 후 렌더링), `vite.config.ts` proxy `/api` → `http://localhost:8080`
  - [ ] 스크립트 `api:generate`, `lint`, `test`(Vitest 실행, Task 010의 no-op 대체)
- 완료 기준
  - `npm run api:generate`·`lint`·`test`·`build`가 성공한다(Task 011 계약 골격의 생성 확인 포함).
  - V-F: 목업 모드에서 루트가 로드되고 콘솔 에러가 0건이다.
- 기록: 2026-10-04 완료. `api:generate`·`lint`·`test`·`build`·`format:check` 통과, 목업 모드 V-F 콘솔 에러 0건. 계약이 `paths: {}`여도 `api:generate`는 에러 없이 끝나지만 타입 11개만 생성되고 훅·핸들러는 만들어지지 않는다. 임시 계약(조회 1개·등록 1개)으로 `orval` 8.39와 `msw` 3.0 호환(생성·타입 검사·런타임 가로채기)을 확인한 뒤 임시 파일은 지웠다. `src/mocks/handlers.ts`는 operation이 생기는 Task 019·031에서 채운다. `@hookform/resolvers` 5.9.1을 함께 설치했다. 템플릿 CSS 정리(`#root`·템플릿 변수·다크 모드 충돌)는 Task 018, 디자인 토큰 정리는 Task 037에서 한다.

#### Task 018: 라우트·레이아웃·라우트 가드 골격 구현으로 전체 화면 뼈대 마련 ✅
- 태그: [F] · PRD: F0~F1, FR-06, FR-17 · 선행: 017 · 브랜치: `feature/f0-routes-layout`
- 구현 사항
  - [ ] `src/app/router.tsx`: SCR-01~18 경로표(제안)와 빈 페이지 연결. 2·3단계 화면은 "준비 중" 자리표시. `/auth/callback`은 TECH 4절 그대로
  - [ ] `src/app/RootLayout.tsx`(헤더 검색·로그인, 푸터 About·출처 자리), `src/app/providers.tsx`(QueryClient)
  - [ ] `RequireAuth`, `RequireAdmin` 자리(동작은 Task 057), 404, 전역 오류 경계
- 완료 기준
  - V-F: 모든 경로가 로드되고 콘솔 에러가 0건이다.
- 결정(2026-10-08): 경로표는 `docs/TASK018_031_PLAN.md` 5절의 표로 확정한다(P6). 이 경로는 브라우저 주소창에 보이는 프론트 화면 주소이며 `/api/...` 백엔드 주소와 다르다. 작품 상세는 `/titles/:mediaType/:tmdbId`. 구현은 `frontend-dev` 에이전트가 하고(에이전트 PR 병합 후 `develop`에서 브랜치), 사용자는 가드 코드를 검토한다.
- 기록: 2026-10-09 완료(`feature/f0-routes-layout`). 경로 19개와 404를 `src/app/router.tsx`에 등록했고 페이지는 `lazy`(React Router 8 객체형)로 나눠 관리자 화면이 첫 번들에 섞이지 않는다. `RootLayout`(헤더 검색·로그인, 푸터 출처 자리), `Providers`(QueryClient), `RouteErrorBoundary`(두 겹), `RouteLoadingFallback`, `ComingSoon`, 빈 페이지 20개를 만들었다. `RequireAuth`·`RequireAdmin`은 지금 `<Outlet />`만 돌려주며 `TODO(Task 057)`에 들어올 동작을 적어 두었다. `/auth/callback`은 가드 밖(공개)이다. `App.tsx`·`App.css`·템플릿 에셋·`public/icons.svg`를 지우고 `index.css`의 템플릿 CSS를 정리했다(`favicon.svg`는 Task 036·037에서 교체). 화면 문구는 존댓말로 통일했다. `lint`·`format:check`·`test`(23건, 경로표 비교 포함)·`build` 통과, V-F 경로 19개·404 로드와 콘솔 에러 0건. 확인하지 못한 것: `RouteErrorBoundary`가 실제로 그려지는 모습과 뒤로 가기 때 검색창 동기화(Task 027에서 Playwright로 확인). `/admin`만 입력하면 404이며 자동 이동은 Task 029에서 정한다. 관리자 권한 없음 처리 방식은 Task 057로 이관했다. 사용자가 가드 설계를 검토·승인했다.

#### Task 019: ott_service 마이그레이션과 서비스 목록 API 로컬 구현으로 얇은 배포 대상 마련 ✅
- 태그: [B] · PRD: B1 · 선행: 016, 023, 011 · 브랜치: `feature/b1-ott-services`
- 관련: TECH T-8, TECH 4절 IP 헤더
- 구현 사항
  - [x] `db/migration/V1__create_ott_service.sql`: `ott_service`와 7개 서비스 시드(쿠팡플레이 `data_quality = INSUFFICIENT`), `provider/domain/OttService` 엔티티·리포지토리
  - [x] 계약에 먼저 추가 후 구현: 상태 확인 API와 서비스 목록 API(예: `GET /api/public/ott-services`, operationId `listOttServices`, 경로·이름은 제안). 상태 확인 API는 새로 만들지 않고 `/actuator/health`로 대신한다(아래 기록 D1)
  - [x] `global/security`의 오리진 비밀 헤더 필터(`x-origin-secret`, 상수 시간 비교, T-8 ①). local 프로필에서는 끄는 설정 키(제안: `app.origin-secret.enabled`)
  - [x] `/api/**` 기본 `Cache-Control: no-store` 응답 헤더(T-8 ②, 이 Task 한 곳에서 구현)
  - [x] 진입 요청의 `x-real-ip`·`x-forwarded-for`·`x-forwarded-host`를 로그로 남김(TECH 4절, 확인 후 Task 049에서 정리)
- 완료 기준
  - V-B: 헤더 없음 → 거부, 올바른 헤더 → 200, 응답에 `no-store`가 있는 테스트가 통과한다.
  - V-API: 서비스 목록이 `CommonResponse`로 7건 온다.
- 기록: 2026-10-06 완료(브랜치 `feature/b1-ott-services`). 계약 초안 `docs/TASK019_CONTRACT_DRAFT.md`는 이 기록에 흡수하고 삭제했다.
  - 결정(사용자 승인, D1~D8): ① **상태 확인 API는 만들지 않는다.** PRD B1·이 Task의 "상태 확인 API"는 이미 있는 `/actuator/health`(`/api` 밖이라 Cloudtype 헬스체크가 오리진 비밀 헤더 없이 호출 가능)로 대신하고, Vercel 경유 확인은 V-DEPLOY 정의대로 서비스 목록 API로 한다. 새 operation·DTO·프론트 훅을 늘리지 않기 위함이며 PRD 문구는 고치지 않았다. ② ERD 7행(공통 감사 컬럼 생략 표기)에 따라 `created_at`·`updated_at`을 두고 `BaseTimeEntity`를 상속했다. ③ T-8 ②는 Spring Security 기본 캐시 헤더(`no-cache, no-store, max-age=0, must-revalidate`)로 충족하고, `SecurityConfig`에 `headers.cacheControl(withDefaults())`를 명시·주석·테스트로 고정했다(Task 053 교체 시 끄지 않는다). ④ `logoPath`는 required·non-null·DB NOT NULL(7건 모두 값 확정). ⑤ 시드 전용 엔티티라 생성자 오버로드를 두지 않았다(protected 기본 생성자만). ⑥ 오리진 비밀 헤더 필터는 Security 체인 밖의 서블릿 필터(`@Order(HIGHEST_PRECEDENCE + 20)`), 대상은 `/api/**`만, 거부는 `AccessDeniedException`을 `HandlerExceptionResolver`에 위임해 403 `FORBIDDEN` ProblemDetail. `app.origin-secret.enabled` 기본 true·local만 false, prod `value: ${ORIGIN_SECRET}`(기본값 없음), 켜져 있는데 값이 비면 기동 실패. 401이 아닌 403인 이유: 사용자 인증 챌린지(`WWW-Authenticate`)가 아니라 출처 제한이다. ⑦ IP 로그는 `x-real-ip` 원본 + 반영된 `remoteAddr`·`serverName`(아래 실측). ⑧ 서비스 클래스명 `OttServiceQueryService`.
  - 계약: `/public/ott-services`(`listOttServices`, 태그 `product`), `OttService`·`CommonResponseOttServiceList` 스키마, `fieldErrors` "(제안)" 제거, `error-codes.md`의 `FORBIDDEN` 의미 보강. 시드 값(표시명·로고 경로)은 사용자 확정값이고 왓챠 로고는 TMDB에 남은 왓챠플레이 시절 구버전이다(V1 주석). TMDB `provider_id`는 넣지 않았다(Task 033). 프론트 생성물·MSW 핸들러는 메인 컨텍스트에서 갱신했다.
  - 검증(V-B): `.\gradlew.bat test --tests "com.ottnavi.OttnaviApplicationTests"`(Flyway V1 적용·`ddl-auto=validate` 통과), `"com.ottnavi.contract.OpenApiContractTest"`(첫 실제 operation 대조 차이 0건), `"com.ottnavi.provider.*"`(`OttServiceApiTest` 6건: 올바른 헤더 200·7건 정렬, `no-store`, 헤더 없음 403, 틀린·빈 값 403, `/actuator/health` 헤더 없이 200, IP 로그) 통과. 마지막에 `.\gradlew.bat clean build`가 전체 36건 실패 0으로 통과했다.
  - 검증(V-API): local 프로필 `bootRun`(compose DB에 V1 적용, 기동 9.0초) 후 `curl -i -H "x-real-ip: 1.2.3.4" -H "x-forwarded-for: 5.6.7.8, 9.9.9.9" http://localhost:8080/api/public/ott-services` → 200 `application/json`, `CommonResponse` 7건(ID 1~7), `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`. 로그 `x-real-ip=1.2.3.4, remoteAddr=5.6.7.8, serverName=localhost`.
  - 실측으로 확인한 것: `springdoc.default-produces-media-type: application/json`이 operation 응답 media type에 반영된다(`/v3/api-docs.yaml`의 200 응답이 `application/json`). record 컴포넌트의 `@Schema(requiredMode = REQUIRED)`가 `required`로 나오고 Java enum 필드는 인라인 `enum`으로 나온다. `@Enumerated(STRING)` ↔ `VARCHAR`, `BaseTimeEntity`의 `Instant` + `@JdbcTypeCode(TIMESTAMP_WITH_TIMEZONE)` ↔ `TIMESTAMPTZ`가 `ddl-auto=validate`를 통과한다. Boot 4.1.1은 `ForwardedHeaderFilter`를 order `Integer.MIN_VALUE`로 등록하고(jar `javap`), spring-web 7.0.9의 이 필터는 `X-Forwarded-*` 원본을 요청에서 숨긴다(`ForwardedHeaderExtractingRequest extends ForwardedHeaderRemovingRequest`). 그래서 뒤의 필터는 XFF 첫 값을 `remoteAddr`, XFH를 `serverName`으로만 보고 `x-real-ip`는 원본 그대로 본다(테스트·V-API로 확인). `@AutoConfigureMockMvc`는 `@Component` 필터와 Boot의 `ForwardedHeaderFilter`를 포함한다(403·IP 로그 테스트 통과).
  - 미확인: `MessageDigest.isEqual`의 상수 시간 보장은 문서로 대조하지 않았다(JDK 6u17 이후 상수 시간 구현으로 알려져 있음). XFF 원본 체인(Cloudtype이 홉을 덧붙이는지, Vercel이 클라이언트 XFF를 덮어쓰는지)은 로그에 첫 값만 남아 Task 021 배포 스모크에서 `x-real-ip`와 `remoteAddr`를 비교해 판단한다. `/actuator/health`는 Cloudtype 주소로 직접 호출해도 열려 있다(상세는 기본 비공개). Windows에서 `gradlew bootRun` 출력을 파일로 받으면 한국어 로그가 깨져 보였다(콘솔 인코딩 문제로 보이며 앱 동작과 무관, 원인 미확인).
  - PR #11 CodeRabbit 반영(Major, 보안): 오리진 필터와 IP 로그 필터가 `getRequestURI()` 원본으로 `/api/`를 판정해 `/%61pi/public/ott-services`처럼 퍼센트 인코딩한 경로가 필터를 건너뛰고 MVC가 `/api/...`로 매칭해 헤더 없이 200이 됐다(CWE-647). 재현 테스트로 수정 전 200을 확인한 뒤 `UrlPathHelper`로 디코딩·정규화한 경로를 쓰는 공통 `ApiRequestPath`로 고쳤다(`/api;x=1/...`도 403, `//api/...`는 MVC가 매칭하지 못해 404이므로 "200이 아님"만 고정). 최종 `clean build` 테스트 38건 실패 0(`OttServiceApiTest` 8건). 실제 Tomcat(Cloudtype)에서도 인코딩 우회가 403임을 Task 020 검증에서 확인했다.

#### Task 020: Cloudtype 서비스 생성과 환경 변수·배포 토큰 등록으로 첫 배포 준비 ✅
- 태그: [H] · PRD: B1, H2 · 선행: 002, 015, 019 · 기한: 2026-10-12(R-12)
- 관련: 10.3 R-12, TECH 3절, T-8
- 구현 사항
  - [x] R-12 결정: Cloudtype 배포 방식(GitHub Actions 배포 액션 여부)과 배포 토큰 이름·발급 위치를 Cloudtype 문서로 확인하고 사용자가 정한다. **하비는 커스텀 이미지 배포·이미지 저장소 연결이 프로 전용이라 쓸 수 없으므로**, Cloudtype이 저장소에서 직접 빌드(Dockerfile·빌드팩)하고 GitHub Actions는 그 배포를 트리거하는 방식이 전제다(Task 003 `기록:`). 빌드시간은 하비 월 500분, 프리티어 월 200분이다
  - [x] Task 002에서 만든 **Redis Cloud 무료 DB가 아직 살아 있는지 확인**한다(14일 동안 Redis 명령이 없으면 삭제됨, Task 003 `기록:`). 삭제됐거나 곧 쓸 계획이 없으면 필요할 때 다시 만든다
  - [x] Cloudtype 무료 플랜에 운영 백엔드 서비스 생성(Java 17, 포트 8080, `prod` 프로필). SMTP 시험용 일회성 서비스(Task 022)와 이름으로 구분. 서비스를 배포할 때 사용할 리소스(무료/구독)를 고른다(무료 메모리와 구독 메모리는 따로 쓰고 합산되지 않는다, Task 003 `기록:`)
  - [x] Cloudtype 환경 변수 등록(Task 015 위치표의 Cloudtype 항목, 이름만 기록). 첫 배포 기동에 필요 없는 값(Google OAuth, TMDB, 메일 등)은 Task 092 최종 점검 때 채워도 된다
  - [x] GitHub Actions secrets에 Cloudtype 배포 토큰(R-12 이름)과 백엔드 주소 등록 → **하지 않음(보류)**: R-12 결정으로 당분간 콘솔 수동 배포를 쓰므로 토큰을 만들지 않았다(아래 기록)
- 완료 기준
  - V-H: 서비스 이름·생성일, 등록한 변수 이름 목록(값 제외), R-12 결정 내용을 기록했고 10.3 R-12를 갱신했다.
- 기록: 2026-10-06 완료(사용자가 콘솔에서 수행, 기록은 Claude).
  - 서비스: 프로젝트 `backend`, 배포환경 `main`(Cloudtype의 배포환경 이름이며 Git 브랜치와 무관), 서비스 `ottnavi`, `java@17`, 프리티어 1GB, 서울 리전. 생성일 2026-10-06(약 15:31 KST, 첫 배포 시도). 공개 주소 `https://port-0-ottnavi-17xco2nlst8pr67.sel5.cloudtype.app`(같은 프로젝트 안의 내부 주소는 `ottnavi:8080`, Vercel에서는 쓰지 않는다).
  - **R-12 결정(사용자, 2026-10-06)**: 저장소를 연결해 Cloudtype이 직접 빌드하고, 배포는 콘솔의 `배포하기`로 **수동**으로 한다. GitHub Actions 배포(문서 기준 secrets `CLOUDTYPE_TOKEN`, 권한 `repo`·`workflow`·`admin:public_key`의 PAT `GHP_TOKEN`)는 큰 권한의 토큰 관리와 빌드 시간 부담 때문에 당분간 쓰지 않는다. 그래서 `backend-deploy.yml`과 GitHub secrets의 Cloudtype 토큰은 만들지 않았다. 자동 배포가 필요해지면 그때 다시 정한다.
  - 설정값: 서브 디렉토리 `backend`, JDK 17, 포트 8080, Build Command `./gradlew bootJar`, Start Command `java -Dspring.profiles.active=prod -jar build/libs/backend-0.0.1-SNAPSHOT.jar`. Cloudtype이 Dockerfile을 자동 생성(`Build type is dockerfile`)하므로 저장소에 Dockerfile이 필요 없고, `gradlew` 실행 권한 부여와 실행 jar 탐색(`-plain.jar` 제외)도 자동이다. 헬스체크(경로·Initial Delay)는 비워 두었다.
  - 환경 변수(이름만): 런타임 `Environment variables` 7개 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `ORIGIN_SECRET`. `SPRING_PROFILES_ACTIVE`는 빌드 전용인 `Build Variables`에만 들어 있어 런타임에 전달되지 않으므로 Start Command의 `-D`로 대신한다. Build Variables에도 위 7개가 중복 입력돼 있다. 021의 재배포 때 정리한다(런타임에 `SPRING_PROFILES_ACTIVE=prod` 추가, Build Variables 비우기 — 비밀 값이 빌드 인자로 남는 노출을 줄이기 위함).
  - Redis Cloud: 생존 확인(2026-10-06), 평문 접속(무료 플랜 TLS 불가, Task 003). **알려진 수용 위험**: 채팅에 노출된 것으로 보이는 문자열(마스킹된 글자 수로 Redis 비밀번호로 추정)을 재설정하지 않고 계속 쓰기로 했다(사용자 결정). 현재 Redis에 저장하는 데이터가 없어 허용하고, Refresh Token 같은 민감한 값을 저장하기 **전(Task 053 이전)에 비밀번호를 재설정**한다.
  - **첫 배포는 `develop` 브랜치로 했다(일회성 예외)**: git.md의 "develop은 배포하지 않는다"의 예외로, 019까지 병합된 `develop`(`97b682a`)을 배포해 prod 프로필·Supabase·Redis·`ORIGIN_SECRET`·V1을 한 번에 검증했다. Task 021에서 `main` 병합 후 배포 브랜치를 `main`으로 바꾼다.
  - 시도 이력(배포 내역 5건): ① 서브 디렉토리를 비워 둬 빌드 실패(`chmod: cannot access 'gradlew'`) → ② 서브 디렉토리 `backend` 후 빌드 성공 → ③ 런타임에 `SPRING_PROFILES_ACTIVE`가 없어 `local` 프로필로 떠 `127.0.0.1:5432` 접속 실패 → ④ `ORIGIN_SECRET`이 런타임에 없어 오리진 필터 빈 생성 실패(값 없이 켜지면 기동 실패하는 설계대로 동작) → ⑤ `DB_URL`의 `?`가 공백이라 `database "postgres sslmode=require" does not exist` → 수정 후 성공.
  - 검증: 로그에서 `profile is active: "prod"`, Flyway `Successfully applied 1 migration ... now at version v1`(운영 Supabase PostgreSQL 17.11에 V1 첫 적용), Hibernate validate 통과, `Tomcat started on port 8080`, `Started OttnaviApplication in 201.494 seconds`. 공개 주소로 `/actuator/health` 200 `UP`(Redis 포함), `/api/public/ott-services` 헤더 없음 403 `FORBIDDEN`(`application/problem+json`), 인코딩 우회 `/%61pi/public/ott-services`도 403(실제 Tomcat에서 확인). 올바른 헤더로 200 확인은 Task 021(Vercel 경유)에서 한다.
  - 실측: 기동 약 201초(프리티어 공유 CPU, 웹 컨텍스트 초기화만 약 60초). Redis 헬스 첫 호출 11.5초(`took 11489ms`), 이후 0.3~0.5초. **메모리 기동 직후 302.27MB**(1GB 중 약 30%, 서비스 목록 API만 있는 상태의 기준값, 4.6). 프리티어는 매일 1회 정지하므로 재기동마다 3분 이상 걸린다. 빌드 시간은 회당 약 1~3분이고 성공 3회·실패 1회 합계 약 6~8분으로 추정한다(월 200분 중, 콘솔에서 합계는 확인하지 못함).
  - IP 헤더 실험(1회): `x-forwarded-for: 1.2.3.4, 5.6.7.8`, `x-real-ip: 9.9.9.9`, `x-forwarded-host: spoof.example`을 실어 공개 주소로 직접 호출했다(403). 로그의 `x-real-ip`·`remoteAddr`는 둘 다 실제 클라이언트 IP(위조 값이 아님, Cloudtype이 덮어씀), `serverName=spoof.example`(`X-Forwarded-Host`는 통과). 한 번의 관찰이라 일반화하지 않는다. Task 053의 로그인 리디렉션은 `OAUTH2_REDIRECT_BASE_URL` 고정을 쓴다(TECH 4절).
  - 미확인·보류: 헬스체크 설정(기동이 길어 쓰려면 Initial Delay 300초 이상이 필요, Task 021 스모크 뒤 결정), 월 빌드 시간 합계(콘솔에서 확인 못 함). 위 현상과 함정은 TECH 6절에 정리했다.

#### Task 021: Vercel·Cloudtype 얇은 배포와 develop→main 첫 배포로 배포 경로 검증 ✅
- 태그: [B][F] · PRD: B1 · 선행: 009, 014, 015, 019, 020 · 기한: 2026-10-14 · 브랜치: `feature/b1-thin-deploy` → `develop` → `main`
- 관련: git.md "1주차 얇은 배포", TECH T-8, 10.3 R-12·R-17
- 구현 사항
  - [x] `.github/workflows/backend-deploy.yml`: `main` push에서 Cloudtype 배포 → **만들지 않음(보류)**: R-12 결정(Task 020 `기록:`)으로 당분간 `main` 병합 후 Cloudtype 콘솔에서 `배포하기`로 수동 배포한다. prod 프로필(Supabase Session Pooler 5432)은 Task 020에서 이미 적용됐다
  - [x] `frontend/vercel.json` 백엔드 주소 확정(자리표시값 `REPLACE_BACKEND_HOST`를 Cloudtype 공개 주소로 교체, 2026-10-06), `routes[].transforms`로 `x-origin-secret` 주입, rewrite 캐시 비활성화(두 설정은 Task 009에서 작성)
  - [x] feature → `develop` PR(squash) 후 `develop` → `main` PR을 merge commit으로 병합(사용자가 실행). 이것이 첫 배포이고 Vercel Production도 함께 배포된다
  - [x] 이 시점 백엔드는 임시 `permitAll`(Task 016)이고 공개 API는 서비스 목록뿐임을 확인한다
- 완료 기준
  - V-DEPLOY "헤더·경로"(Vercel Production URL 기준): `/api/public/ott-services`가 200 `CommonResponse`, `no-store`, 캐시 적중 없음, Cloudtype 직접 호출 거부, 로그에 방문자 IP.
  - `기록:` `routes` 단일 형식에서의 실제 적용 순서(미리보기 배포 성공 여부는 Task 014 기록), 로그에 찍힌 IP 헤더 실제 값. 무료 플랜이 꺼져 있으면 대시보드에서 기동한 뒤 확인한다.
  - `frontend/vercel.json`에 `REPLACE_BACKEND_HOST`가 남아 있지 않다(`Select-String -Path frontend/vercel.json -Pattern REPLACE_BACKEND_HOST`가 아무것도 찾지 못함). Task 009가 남긴 자리표시값이 운영 배포로 새지 않게 하는 확인이다(Task 009 PR의 코드래빗 Major 지적에 대한 대응).
  - 기동 직후와 서비스 목록 API 호출 후의 **메모리 사용량**(Cloudtype 대시보드)을 `기록:`에 남긴다(메모리 예산의 기본 사용량, 4.6). 무료 1GB 한도에서 측정한 값이라 512MB에서의 동작은 보장하지 않는다. → **일부 이관**: 기동 후 값(302.27MB)만 기록했고, 올바른 헤더로 호출을 반복한 뒤의 값은 측정하지 못해 Task 040·100의 측정 항목으로 넘긴다(아래 `기록:`).
- 진행 메모(작성 당시, 아래 `기록:`에서 모두 완료): `frontend/vercel.json`에 Cloudtype 공개 주소를 반영했다(`Select-String`으로 `REPLACE_BACKEND_HOST` 잔존 없음 확인). 서버는 Task 020에서 `develop` 브랜치로 이미 배포돼 동작한다(기동 약 201초, 메모리 302.27MB 기준값은 020 `기록:`). **남은 것**: `develop` → `main` PR(merge commit, 사용자), 병합 후 Cloudtype 배포 브랜치를 `main`으로 변경, 환경 변수 정리(런타임에 `SPRING_PROFILES_ACTIVE=prod` 추가, Build Variables 비우기), 콘솔 `배포하기`, V-DEPLOY 확인(Vercel Production URL에서 `/api/public/ott-services` 200·`no-store`·캐시 미적중·로그의 IP 헤더 값)과 `기록:` 작성, Vercel Production·Preview의 `ORIGIN_SECRET`이 Cloudtype과 같은 값인지는 Vercel 경유 200으로 확인한다.
- 기록: 2026-10-06 완료.
  - 병합: PR #12(`feature/b1-thin-deploy` → `develop`, squash `c076d3e`, `vercel.json` 주소와 020 기록), PR #13(`develop` → `main`, **merge commit** `ec6162d`, 13커밋·71파일). 병합 직후 Vercel Production 배포 성공(`ec6162d`). 사용자가 Cloudtype 배포 브랜치를 **`main`으로 변경**하고 재배포했다(배포 내역 이름 `Merge pull request #13 from msw-Hub/develop`). 환경 변수는 사용자가 정리했다고 보고했다(런타임 `Environment variables`에 `SPRING_PROFILES_ACTIVE=prod` 추가, `Build Variables` 비움, 화면은 Claude가 확인하지 못함). Start Command의 `-Dspring.profiles.active=prod`는 유지한다. `backend-deploy.yml`은 만들지 않았다(R-12, 020 `기록:`).
  - 운영 도메인 `ottnavi.vercel.app`(응답의 프론트 `index.html`과 빌드 해시가 일치해 우리 프로젝트로 확인). 개별 배포 주소(`ottnavi-<해시>-team-msw.vercel.app`)는 Vercel Authentication 보호(302)라 외부에서 호출되지 않는다.
  - **첫 Vercel 경유 호출은 403 `FORBIDDEN`**이었다. 요청은 Cloudtype까지 도달했고 백엔드 오리진 필터가 거부한 것이다. `vercel.json`의 `transforms`(`args: "$ORIGIN_SECRET"` + `env`) 문법은 Vercel 문서 예시와 같아 후보에서 제외했다. Vercel에 `ORIGIN_SECRET` 키는 있었고 값은 열람이 안 되어, 사용자가 값을 다시 입력하고 Vercel에서 재배포하자 200이 됐다. 값을 볼 수 없어 정확한 원인(값 불일치 또는 미반영)은 확인하지 못했고, 문서상 변수가 없거나 다르면 `$VAR`가 글자 그대로 나가 403이 되는 동작과 일치한다. TECH 6절에 정리했다. 참고로 이 직전 Cloudtype 서버는 재배포 중이라 한동안 503(Cloudtype 오류 페이지)이었고 기동에 약 2분 37초가 걸렸다.
  - **V-DEPLOY(운영 도메인 `ottnavi.vercel.app`)**: `/api/public/ott-services` → **200**, `CommonResponse` 서비스 7건(NETFLIX → COUPANG_PLAY 순, 쿠팡플레이만 `INSUFFICIENT`), `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`, 연속 3회 `X-Vercel-Cache: MISS`(캐시 미적중). Cloudtype 직접 호출(헤더 없음)은 403, 실제 Tomcat에서 인코딩 우회(`/%61pi/...`)도 403(Vercel 경유로 같은 경로를 부르면 `/api` 프록시 규칙에 걸리지 않아 프론트 `index.html` 200이며 백엔드에는 닿지 않는다). 클라이언트가 보낸 가짜 `x-origin-secret`은 Vercel `set`이 덮어써 200. Vercel↔Cloudtype `ORIGIN_SECRET` 값 일치는 이 200으로 검증됐다. `/api` 프록시 규칙이 SPA 대체(`/index.html`)보다 먼저 적용된다(R-17의 `routes` 단일 형식, 실측).
  - **IP 헤더 실측(로그 값은 기록하지 않음)**: Vercel 경유에서 `x-real-ip`는 Vercel의 IP(호출마다 바뀌는 AWS 서울 대역으로 보이는 값, 추정)이고 `remoteAddr`(= `X-Forwarded-For` 첫 값)가 **방문자의 실제 IP**였다. 위조한 `x-forwarded-for`·`x-real-ip`·`x-forwarded-host`는 어느 것도 백엔드에 닿지 않았다(Vercel이 덮어씀). TECH 4절의 "방문자 IP는 `x-real-ip` 1순위" 가정이 **틀렸음**을 확인해 `remoteAddr`로 정정했다. **후속: IP별 요청 제한은 반드시 `remoteAddr`를 쓴다**(`x-real-ip`를 쓰면 방문자가 Vercel의 몇 개 IP로 뭉쳐 한도를 공유한다).
  - 메모리: 302.27MB(020 `기록:`, 1GB 중 약 30%). 이 값은 정확한 기동 시점이 아니라 **기동 후 `/actuator/health`와 403 응답 몇 건을 처리한 시점**에 사용자가 대시보드에서 읽은 값이다(그 뒤 조금 늘어 보였다는 관찰이 있었으나 수치는 없다). `main` 재배포 후와 올바른 헤더로 서비스 목록 호출을 반복한 뒤의 값은 이 기록에 담지 못했다. 완료 기준의 "호출 후 메모리"는 **Task 040·100의 측정 항목으로 이관**했고, 그때까지 이 값은 "서비스 목록 API만 있는 상태의 대략적인 기준값"으로만 쓴다. 헬스체크(경로·Initial Delay)는 여전히 미설정이다(기동 약 200초라 쓰려면 Initial Delay 300초 이상 필요, 미검증).
  - 후속 메모: ① Redis 비밀번호는 Redis에 민감한 값을 저장하기 전(Task 053 이전)에 재설정(020 수용 위험). ② `RequestIpLoggingFilter` 로그는 Task 049에서 레벨·항목 정리(방문자 IP는 `remoteAddr`로 본다). ③ 자동 배포가 필요해지면 R-12 재결정. ④ 서비스를 지우고 다시 만들면 주소가 바뀌어 `vercel.json`을 고쳐야 한다.

#### Task 022: Cloudtype SMTP 외부 발신 테스트로 메일 방식 결정 근거 확보 ✅
- 태그: [B] · PRD: B1, R11-11 · 선행: 002, 003, 006, 015, 016 · 기한: 2026-10-14
- 관련: 10.3 R-15(해소)
- 구현 사항
  - [x] `develop`에서 만든 시험 브랜치(제안: `feature/b1-smtp-trial`)에 최소 발신 코드를 둔다. 이 브랜치는 병합하지 않는다
  - [x] 운영 서비스와 분리된 일회성 Cloudtype 서비스로 시험 브랜치를 배포해 SMTP 587 발신 시험(Task 003 결과: 프리티어 동시실행이 4개라 서비스 추가가 가능하다. 시험 서비스는 **무료 리소스**로 만들어 운영 서비스의 구독 메모리와 분리한다)
  - [x] 연결·읽기·쓰기 타임아웃 설정 상태로 시험(TECH 5절)
  - [x] 실패 시에만 HTTPS 메일 API(예: Resend, Brevo) 하나의 키를 발급하고(Task 002에서 옮김) 같은 시험 → 587이 성공해 해당 없음
  - [x] 시험 후 일회성 서비스와 시험 브랜치 삭제
- 완료 기준
  - V-H: 성공·실패, 오류 메시지, 시험일, 일회성 서비스 삭제를 기록했고 사용자가 R11-11을 결정했다.
- 안내서(2026-10-08): `docs/TASK022_GUIDE.md`에 이유·방법·순서·판정표·`기록:` 초안을 정리했다. 시험 코드는 기동 시 1회 발송하는 `ApplicationRunner`(공개 엔드포인트 없음)를 추천한다.
- 기록: 2026-10-09 사용자가 수행했다. **결과: Cloudtype에서 SMTP 587(Gmail, STARTTLS) 연결·인증과 시험 메일 발송이 성공했고 수신함에 도착했다.** 그래서 465 SSL 재시험과 HTTPS 메일 API 시험은 하지 않았다. 환경: 일회성 서비스 `ottnavi-smtp-trial`(프리티어 무료 리소스 0.5GB·최소 vCPU, prod 프로필, 브랜치 `feature/b1-smtp-trial`은 병합하지 않음). 로컬 사전 시험도 성공했다(연결·인증 1911ms, 발송 3311ms). Cloudtype에서의 소요 시간은 서비스를 먼저 삭제해 기록하지 못했다. 시험 서비스와 시험 브랜치(로컬·원격)는 삭제했다.
  - 시행착오: ① 1차 배포는 DB 비밀번호 인증 실패(`28P01`, 시험 서비스의 `DB_USERNAME`·`DB_PASSWORD` 값이 운영과 달랐다)로 기동하지 못했고 운영 값을 그대로 복사해 해결했다. ② 2차 배포에서 시험 로그가 나오지 않은 원인은 푸시된 커밋에 `SmtpTrialRunner` 빈 클래스만 있고 메일 의존성·`application.yml`이 미커밋 상태였기 때문이다(Cloudtype은 원격 브랜치를 빌드한다). 푸시 전 `git status`로 미커밋 파일이 없는지 확인한다.
  - 새로 알게 된 것: 프리티어 무료 1GB는 서비스를 만들 때 서비스별로 나눠 고른다(이번에 0.5GB). 0.5GB·최소 vCPU에서 앱이 OOM 없이 기동했으나 기동에 약 220초가 걸렸다(로컬 5.7초, Spring 컨텍스트 초기화만 약 65초). 메일 발송만 하는 상태의 값이라 운영 부하와는 다르며 Task 021·092의 메모리 측정 자료로 쓴다.
  - 운영 서비스: 시험 전에 이미 내려져 있었고(프리티어라 매일 아침 꺼진다) 시험 중 따로 중지·재시작한 일은 없다. 그래서 안내서 ⑨의 "운영 서비스 재시작 확인"은 해당 없음이다.
  - **R11-11 결정(2026-10-09, 사용자): SMTP(Gmail) 채택.** 시험 결과가 SMTP 발송 가능이므로 Task 072는 SMTP 구현체를 기본으로 쓴다(`app.mail.provider=smtp`, TECH 5절의 HTTPS 메일 API 구현체는 필요할 때 교체용으로 둔다). 반영: 10.1의 "해소되어 이 표에서 뺀 항목" 줄(10.1·10.2 표에서는 행을 뺌), PRD 11절 11번(확정 목록), `backend.md` 메일 줄. 하루 발송량 한도·Gmail 계정 제재 같은 운영 위험은 Task 071~074 구현 때 다시 본다.

#### Task 023: 공통 응답·예외 처리와 계약 대조 테스트 구현으로 API 형식 통일 ✅
- 태그: [B] · PRD: B0 · 선행: 016 · 브랜치: `feature/b0-common-response`
- 이동: 원래 Phase 2였으나 Task 019(서비스 목록 API)의 선행이라 2026-10-04 사용자 결정으로 Phase 1로 옮겼다. 번호는 그대로다.
- 관련: api-contract.md 공통 응답, backend.md 예외 처리, TECH T-5
- 구현 사항
  - [x] `global/common/CommonResponse`(record), `BaseTimeEntity`
  - [x] `global/error/GlobalExceptionHandler`: `ProblemDetail` + `errorCode`, `VALIDATION_FAILED` 필드 오류 목록, 보안 엔트리포인트·거부 핸들러도 같은 형식
  - [x] `ErrorCode`(error-codes.md와 일치), 커스텀 예외 기반(생성자 2개)
  - [x] `OpenApiContractTest`(T-5): `/v3/api-docs.yaml`과 `docs/api/openapi.yaml`을 경로·operationId·스키마·required·enum으로 비교(`servers`·`info.version`·`example` 제외). 계약 쪽 `x-planned: true` operation은 비교에서 제외한다(R-20, 4.2)
- 완료 기준
  - V-B: 예외 유형별 응답 테스트와 계약 대조 테스트가 통과한다. `x-planned: true` operation을 하나 둔 계약으로 제외 동작을 확인하는 테스트를 포함한다.
  - V-API: 없는 경로·잘못된 요청에 `application/problem+json`과 `errorCode`가 온다.
- 기록: 2026-10-06 완료(브랜치 `feature/b0-common-response`).
  - 결정(사용자 승인, 계획 D1~D8): ① 공통 코드 3개 추가 `INVALID_REQUEST`(400, 깨진 JSON 등. 406·415 같은 기타 4xx는 원래 상태 유지), `RESOURCE_NOT_FOUND`(404, 없는 경로), `METHOD_NOT_ALLOWED`(405). ② TECH 신규 4개(`PLAN_DRAFT_*`, `BATCH_ALREADY_*`)는 넣지 않고 해당 기능 Task에서 확정과 함께 추가한다. ③ 계약 path는 `servers`(`/api`)를 뺀 상대 경로로 쓰고 비교기가 생성 쪽 `/api`를 뗀다(api-contract.md에 명시). ④ `x-planned`인데 구현된 operation은 실패시킨다. ⑤ 비교 범위에 `tags`·`nullable`도 넣는다. ⑥ `BaseTimeEntity`는 Hibernate `@CreationTimestamp`·`@UpdateTimestamp`(Auditing 설정 불필요). ⑦ 보안 핸들러는 지금 `SecurityConfig`에 등록하고 테스트 전용 체인으로 검증한다. ⑧ error-codes.md "공통 코드" 표와 `ErrorCode`의 일치를 `ErrorCodeDocumentTest`가 검사한다.
  - 구현: `GlobalExceptionHandler`는 `ResponseEntityExceptionHandler`를 확장하고, 내장 예외가 모두 모이는 `handleExceptionInternal` 한 곳에서 errorCode·한국어 detail·`fieldErrors`를 붙인다. 도메인 예외는 abstract `BusinessException`(생성자 2개 + `getErrorCode()`). 보안 401·403은 `ProblemDetailAuthenticationEntryPoint`·`ProblemDetailAccessDeniedHandler`가 `HandlerExceptionResolver`에 위임해 같은 핸들러를 탄다. 계약 대조는 테스트 소스의 `OpenApiContractComparator`(SnakeYAML Map 비교, `$ref`를 풀어 구조 비교, `allOf` 병합, 2xx 응답만)와 `OpenApiContractTest`(Testcontainers)다. `application.yml`에 `springdoc.api-docs.version: openapi_3_0`, `springdoc.default-produces-media-type: application/json`. `build.gradle`에 `testImplementation 'org.yaml:snakeyaml'`(BOM 관리)과 `test` 입력으로 `docs/api`(문서만 바뀌어도 테스트 재실행).
  - 검증: 단계별로 `.\gradlew.bat test --tests "com.ottnavi.global.error.*"`(13건), `"com.ottnavi.global.*"`(16건), `"com.ottnavi.support.contract.*"`(11건), `"com.ottnavi.contract.OpenApiContractTest"`(1건)가 통과했고 마지막에 `.\gradlew.bat clean build`가 전체 29건 실패 0으로 통과했다. V-API는 local 프로필 기동 후 `curl.exe -i`로 `GET /api/does-not-exist` → 404 `application/problem+json` `RESOURCE_NOT_FOUND`, `POST /v3/api-docs`·`DELETE /actuator/health` → 405 `Allow: GET` `METHOD_NOT_ALLOWED`, `/v3/api-docs.yaml` → `openapi: 3.0.1`을 확인했다. 검증 실패(400)·깨진 JSON·415·500·401·403은 실행 중인 앱에 해당 엔드포인트가 없어 `@WebMvcTest`(테스트 전용 컨트롤러·체인)로만 확인했다.
  - 실측으로 확인한 것: springdoc 3.0 출력 키와 그 결과 버전(3.0.1). 두 설정 키는 springdoc jar의 설정 메타데이터에도 있다. 테스트 클래스 안의 중첩 `@RestController`는 `@SpringBootTest` 스캔에서 빠진다(계약 대조 차이 0건). 보안 핸들러가 handler 없이 `HandlerExceptionResolver`에 위임해도 advice가 적용되고 `instance`가 채워진다. Spring 7의 ProblemDetail은 `type`이 `about:blank`이면 JSON에서 생략한다(계약에서 `type`은 required가 아니라 문제없음). 405에서는 Spring `PageNotFound` 로거와 이 핸들러가 WARN을 한 번씩 남긴다.
  - 미확인: `springdoc.default-produces-media-type`이 실제 operation의 응답 media type을 `application/json`으로 바꾸는지(operation이 없어 Task 019에서 확인). springdoc이 Java record 응답 필드를 `required`·`nullable`로 어떻게 표시하는지(필요하면 DTO에 `@Schema(requiredMode = REQUIRED)`·`nullable` 지정, Task 019에서 확인). `BaseTimeEntity`의 `Instant` + `@JdbcTypeCode(TIMESTAMP_WITH_TIMEZONE)`가 TIMESTAMPTZ와 `ddl-auto=validate`를 통과하는지(엔티티가 없어 Task 019 V1에서 확인). Boot `spring.mvc.problemdetails.enabled`는 켜지 않았고, 직접 만든 핸들러와 함께 켰을 때의 동작은 확인하지 않았다. MVC 밖(필터·컨테이너)에서 난 오류는 Boot `/error`로 가서 problem+json이 아닐 수 있다(요청 제한 필터 Task에서 직접 같은 형식으로 쓴다).
  - 남은 일: `openapi.yaml`의 `ProblemDetail.fieldErrors` description에 남은 "(제안)"은 다음에 `openapi.yaml`을 수정할 때 지운다(이번에는 프론트 생성물 변경을 피하려고 계약 파일을 건드리지 않았다. 구조는 확정). 신규 3개 코드의 화면 메시지는 Task 025 `errorMessages.ts`에서 확정한다.

---

## 6. Phase 2 — 공통 모듈/컴포넌트 개발 · 3/14

**목표.**
- 공통 응답·예외, 공통 설정, 프론트 공통 유틸·컴포넌트를 한 번만 정의해 Phase 3~4에서 재사용한다.
- F1 목업으로 전체 흐름을 확정하고(Phase 1~2만으로 목업 흐름 확인 가능) OpenAPI 초안 → ERD 보정 → 스키마 순으로 고정한다.

#### Task 024: Redis·캐시·Feign 공통 설정 구성으로 외부 연동 기반 마련 ✅
- 태그: [B] · PRD: B0 · 선행: 016 · 브랜치: `feature/b0-redis-feign-config`
- 구현 사항
  - [x] `global/config/RedisConfig`: 캐시 값 JSON 직렬화(Jackson 3용 클래스명은 구현 시 확인), 캐시 이름·TTL 자리(`titleDetail`, `planCalc`, `exclusiveTitles`)
  - [x] `global/config/FeignConfig`: `spring.cloud.openfeign.client.config.tmdb.*` 타임아웃. 앱 직렬화는 Jackson 3만
- 완료 기준
  - V-B: Testcontainers Redis에 캐시를 저장·조회하고 값이 JSON인지 확인하는 테스트가 통과한다.
- 안내서(2026-10-08): `docs/TASK024_GUIDE.md`. 주의: Jackson 3의 `GenericJacksonJsonRedisSerializer`는 기본으로 타입 정보를 넣지 않아 record가 Map으로 돌아올 수 있으므로, 추천안(미확정, 안내서 D1)은 캐시별 타입 지정(`JacksonJsonRedisSerializer<T>`)이고 record 왕복 테스트를 포함한다. 사용자가 직접 작성하고 `backend-dev`가 검수한다.
- 기록: 2026-10-09 완료(`feature/b0-redis-feign-config`, PR #17 develop 병합 9ba0ae5). 결정(안내서 5절): D1 값 직렬화 B 방침(기본 직렬화기는 타입 정보 없는 `GenericJacksonJsonRedisSerializer`로 구성했고, 캐시별 `JacksonJsonRedisSerializer<T>`는 DTO가 생기는 후속 Task에서 등록한다. 지금 `src/main`에는 등록된 것이 없고 테스트 fixture(`RedisCacheConfigTest`)에서만 B 방식으로 왕복을 확인했다), D2 `spring-boot-starter-cache` 추가(Boot 4는 캐시 자동 구성이 `spring-boot-cache` 모듈이고 `RedisCacheManagerBuilderCustomizer`가 `org.springframework.boot.cache.autoconfigure`에 있다), D3 Feign `tmdb` 연결 3초·읽기 5초(Task 048 첫 전체 수집에서 실측 후 조정), D4 TTL 기본 1시간·세 캐시 24시간(실측 86400초), D5 Redis 명령 2초·연결 5초, **D6 캐시 쓰기 즉시 반영(`immediateWrites`)**(구현 중 발견: Spring Data Redis 4에서 Lettuce면 기본 `RedisCacheWriter`의 `put`·`evict`·`clear`가 비동기라 수집 직후 clear해도 옛 값이 읽힐 수 있다. 블로킹 비용은 규모가 작아 수용, 운영 응답 시간은 첫 배포 뒤 로그로 확인). 테스트 `RedisCacheConfigTest` 8건(저장·조회, JSON, 키 접두사, TTL, 실제 캐시 TTL, 즉시 반영, null 미저장) 통과, 전체 `clean build` 8개 클래스 46건 통과, CI 4건과 CodeRabbit 통과(지적 0건). 로컬 기동에서 오류 없이 `/actuator/health` 200 `UP`, `@EnableFeignClients`는 클라이언트 없이도 뜬다. 첫 Redis 연결 때 Netty `TCP_KEEPCOUNT`·`TCP_KEEPIDLE`·`TCP_KEEPINTERVAL` WARN 3건이 나오나 영향이 없어 무시(Windows·Java 17 추정, 운영 로그에서 다시 나오면 판단). 확인하지 못한 것: Feign 기본 디코더의 Jackson 3 사용 여부(Task 042 WireMock 테스트), 운영(Redis Cloud) 블로킹 쓰기 응답 시간.
  - 이후 Task에 넘길 내용: ① 캐시에 DTO를 쓰는 Task(049·051·062 등)는 `RedisConfig`에 그 캐시의 `JacksonJsonRedisSerializer<DTO>`를 한 줄씩 등록한다(기본 직렬화기로는 꺼낸 값이 `LinkedHashMap`이 되어 `ClassCastException`). ② `cache.put(key, null)`은 `IllegalArgumentException`이므로 `@Cacheable`에 `unless = "#result == null"`을 붙인다. ③ CodeRabbit이 짚은 일반론: 운영 스키마 호환(DTO 변경 시 기존 캐시 값)과 동시 무효화는 TTL만으로 보장되지 않으니 해당 Task에서 다룬다.

#### Task 025: 공통 유틸과 에러 인터셉터 작성으로 화면 표시 규칙 통일 ✅
- 태그: [F] · PRD: F1~F2, FR-05 · 선행: 018 · 브랜치: `feature/f1-common-lib-components`(Task 026과 합침, 2026-10-08 결정. 원래 `feature/f1-common-lib`)
- 구현 사항
  - [x] `src/lib/format.ts`(금액 "15,000원", 시간 "12시간 30분"), `tmdbImage.ts`, `errorMessages.ts`(error-codes.md와 일치)
  - [x] `src/api/http.ts` 응답 인터셉터: `ProblemDetail` 파싱, 429 "잠시 후 다시 시도"(401 재발급은 Task 057)
  - [x] Vitest 단위 테스트
- 완료 기준
  - `npm run test` 통과, V-F: 429 목업 핸들러를 켰을 때 안내 문구가 보인다.
- 결정(2026-10-08): 026과 PR 하나로 합친다(025 검증 4종 통과 → 026 진행, 단계마다 확인). 완료할 때 025·026 `기록:`에 합친 사실을 남긴다. 429·오류 안내는 shadcn `sonner`(토스트)를 추가해 쓴다(P3).
- 기록: 2026-10-09 완료(026과 같은 브랜치·PR #21 병합 1c48931). 검증·V-F는 Task 026 기록과 같다.
  - 구현: `lib/format.ts`(금액·시간), `tmdbImage.ts`, `errorMessages.ts`(error-codes.md와 일치, 문서에 없는 코드용·네트워크 오류용 기본 문구 2개는 임의로 정함), `api/http.ts` 응답 인터셉터(`ApiError`로 정규화, `ProblemDetail` 파싱, 429는 토스트 id `api-rate-limit`로 한 번만 표시, 401 재발급은 Task 057), 단위 테스트, MSW `?mockScenario=rate-limit`로 429 시나리오. 토스트는 shadcn `sonner`(원본 유지)를 `app/providers.tsx`에 두었다: 위쪽 가운데·머리글 아래, 큰 화면 폭 `--container-md`, 오류는 `--destructive-solid` 바탕+흰 글자, `theme="dark"`(jsdom에 matchMedia가 없어 `system`을 피함). 429 문구는 "잠시 후 다시 시도"를 안내한다.
  - 확인하지 못한 것: MSW가 오래 열어 둔 페이지에서 요청을 놓쳐 502가 한 번 난 일(재현되지 않음, 목업 영역). 신규 공통 코드 3개(`INVALID_REQUEST`, `RESOURCE_NOT_FOUND`, `METHOD_NOT_ALLOWED`)의 화면 메시지는 이 Task에서 확정했다(Task 023의 남은 일 해소).

#### Task 026: 공통 표시 컴포넌트 1차 구현으로 화면 간 중복 제거 ✅
- 태그: [F] · PRD: F2(1차), FR-02, 5.6 · 선행: 025 · 브랜치: `feature/f1-common-lib-components`(Task 025와 같은 브랜치·PR, 2026-10-08 결정. 원래 `feature/f1-common-components`)
- 구현 사항
  - [x] `src/components/common/`: `ProviderStatusBadge`(모양·텍스트로 3상태), `ServiceStatusRow`(쿠팡플레이 "데이터 부족"), `PosterCard`, `PriceDisplay`(가격 출처 라벨), `SourceAttribution`, `DataAsOf`, `EstimatedTag`, `LoadingState`·`ErrorState`·`EmptyState`
  - [x] 색은 임시 토큰(`src/styles/tokens.css`)만, 확정은 Task 037
  - [x] RTL 테스트: 3상태 텍스트 구분
- 완료 기준
  - `npm run test` 통과, V-F: 확인용 임시 화면에서 3상태·출처·추정 표시가 보인다.
- 기록: 2026-10-09 완료(025와 같은 브랜치 `feature/f1-common-lib-components`, PR #21 develop 병합 1c48931, CodeRabbit 지적 0건). 검증 4종 통과(테스트 7개 파일 126건), V-F(`/__dev/components`, 1280·375 폭, 흑백 보기, 오류 토스트) 콘솔 에러 0건, 사용자가 화면을 확인했다.
  - 결정(사용자): ① 시각 방향은 **미드나잇 블루 D2c 어두운 기본 테마**(기준 시안 `.playwright-mcp/directions-dark/direction-D2c.html`, 강조색 하늘 230). ② 한글 글꼴은 **Pretendard 웹폰트**(`pretendard@^1.3.9` 가변 다이내믹 서브셋, 새 의존성). ③ 가격 라벨은 "기본 요금". ④ 오류 토스트 빨강(`--destructive-solid` #e7000b)은 유지하고 Task 037에서 다시 본다.
  - 구현: `src/styles/tokens.css`(상태·품질·추정 토큰과 `--destructive-solid`), `src/components/common/`(`ProviderStatusBadge`, `ServiceStatusRow`, `PosterCard`, `PosterImage`, `PriceDisplay`, `SourceAttribution`, `DataAsOf`, `EstimatedTag`, `DataInsufficientTag`, `LoadingState`·`ErrorState`·`EmptyState`), 개발 전용 확인 화면 `/__dev/components`(`import.meta.env.DEV`일 때만 라우트 등록, 운영 번들에 없음을 확인). `PosterCard`는 완성된 `posterUrl`을 받아 목업·테스트에서 외부 요청이 나가지 않게 했고, 상세 화면용 제목 없는 `PosterImage`를 분리했다. `ServiceStatusRow`는 서비스 목록 전체(`<ul>`)를 그린다. 한글 단어 단위 줄바꿈(`break-keep`), outline 버튼 테두리 `border-input`, 버튼·입력창 40px, 머리글 `bg-card`와 모바일 2줄 배치, "모름" 물음표 확대를 `ui-designer` 검수로 반영했다.
  - 이후 Task에 넘길 내용: ① 제공 상태·가격 출처 타입(`AvailabilityStatus`, `PriceSource`)은 계약에 없어 `components/common/displayTypes.ts`에 임시로 두었다. Task 031에서 enum을 계약에 넣고 생성 타입으로 교체한다. ② Task 027 상세 화면은 `PosterImage`를 쓴다. 쿠팡플레이 "데이터 부족"은 `DataInsufficientTag`. ③ 토스트 offset이 머리글 높이(57px)를 손수 계산한 값인데 이번에 머리글 입력창·버튼이 40px로 커졌으므로 머리글 높이가 바뀌었는지 Task 027 확인 때 같이 본다.
  - 확인하지 못한 것: Pretendard가 실제로 적용됐는지와 숫자 폭 고정(`tabular-nums`) 동작(스크린샷 눈대중만), 화면 움직임 줄이기(`reducedMotion`) 설정 시 동작, 375 폭 흑백, 화면 낭독기. 접근성 이름은 RTL 테스트로만 확인했다.
  - 작업 방식 메모: `frontend-dev` 모델을 `sonnet`으로 바꾸고 검증 4종·스크린샷은 모든 구현이 끝난 마지막에 한 번만 하도록 에이전트 지침에 넣었다(토큰 사용량 절약). `ui-designer`는 새 시각 방향 제안 때만 `opus`를 쓰는 안.

#### Task 027: 공개 화면 목업 구현(SCR-01·02·04·06)으로 공개 흐름 확정 ⬜
- 태그: [F] · PRD: F1, FR-01~03, 05, 06 · 선행: 018, 026 · 브랜치: `feature/f1-public-screens`
- 구현 사항
  - [ ] `src/mocks/fixtures/`: 실제 작품명, 7개 서비스, 영화·드라마 동명 작품, 시즌별 제공처가 다른 드라마
  - [ ] `features/search`, `features/title-detail`(영어 대체 표시, 출연진 10명, 시즌별 제공처·총 시간, 기준일, 시즌 단위 찜)
  - [ ] About(TMDB 로고·고지문 자리, JustWatch, 비영리), 로그인(버튼, 콜백 로딩·실패)
  - [ ] SCR-03·05는 자리표시 유지(3절)
- 완료 기준
  - V-F: 검색 → 결과 → 상세 → 찜(비로그인 시 로그인 화면) 흐름을 클릭으로 재현했고 콘솔 에러가 0건이다.
- 결정(2026-10-08): ① 목업의 로그인 상태는 목업 모드(`VITE_USE_MOCK=true`)에서만 보이는 전환 토글(비로그인/회원/관리자)로 바꾼다. 실제 인증 저장소(Task 057)와 분리한다(P2). ② 포스터는 실제 TMDB 이미지 URL 없이 대체 이미지 표시를 기본으로 해 외부 요청 없이 콘솔 에러 0건을 유지한다(P4). ③ 계약에 아직 없는 API의 타입은 `features/*/mockTypes.ts`에만 임시로 둔다(P1, `frontend.md` "목업 단계의 임시 타입 예외"). ④ 화면의 시각 방향 수정 요청은 `ui-designer`가 맡는다.

#### Task 028: 회원 화면 목업 구현(SCR-07~10)으로 계산 흐름 확정 ⬜
- 태그: [F] · PRD: F1, FR-08~12, 14 · 선행: 027 · 브랜치: `feature/f1-member-screens`
- 구현 사항
  - [ ] `features/onboarding`(7개 서비스 상태, 요금제·결제일, 시청 시간 프리셋·환산·직접 입력, 예산), `features/wishlist`, `features/pricing`, `features/plan`
  - [ ] 저장 시 409 `PLAN_DRAFT_STALE` 목업 → 자동 재계산 흐름
  - [ ] 플랜 결과(SCR-10)에서 긴 시즌의 분할을 달별로 보여 준다(예: "시즌 2: 1월 넷플릭스 앞부분, 2월 티빙에서 이어서", "이어 보기" 라벨, 한 달 최소 2시간 규칙 안내). 문구는 "확정"이 아니라 "확인된 범위" 규칙을 따른다
  - [ ] 작품 상세의 시즌 줄 총 시간이 월 시청 시간보다 크면(회원 상태일 때만) "여러 달에 나눠 봐야 해요" 같은 짧은 안내
  - [ ] SCR-11~13은 자리표시 유지
- 완료 기준
  - V-F: 온보딩 → 찜 → 요금 확인 → 계산 → 결과 → 저장(409 자동 재계산 포함)을 목업으로 끝까지 재현했다.
- 결정(2026-10-08): 409 `PLAN_DRAFT_STALE`는 목업 시나리오로 재현한다. R11-18(환산 입력 저장)·R11-19(드라마 찜 단위)는 두 안을 비교할 수 있게 만들고 화면에 어느 안인지 표시해 Task 030 검토를 돕는다. 임시 타입은 `mockTypes.ts`(Task 027 결정 참고). 029보다 먼저 병합한다.

#### Task 029: 관리자 화면 목업 구현(SCR-14~16)으로 운영 흐름 확정 ⬜
- 태그: [F] · PRD: F1, FR-17~20 · 선행: 027 · 브랜치: `feature/f1-admin-screens`
- 구현 사항
  - [ ] 상품·가격(단품, 기간 겹침 오류), 제공처 매핑(미매핑 하이라이트), 수집(이력, 수동 실행 202·409, 복구, 실행별 변경된 작품, 재계산 대상 플랜 수·알림 상태는 "2단계부터" 자리)
  - [ ] SCR-17·18은 자리표시 유지
- 완료 기준
  - V-F: 세 화면의 등록·매핑·실행 상호작용을 목업으로 재현했고 콘솔 에러가 0건이다.
- 결정(2026-10-08): 028 병합 후 `develop`을 받아 브랜치를 만든다(`router.tsx`·`handlers.ts` 충돌 방지). 관리자 가드는 목업 로그인 전환 토글(Task 027 결정 ①)로 확인한다. 임시 타입은 `mockTypes.ts`.

#### Task 030: 목업 흐름 승인과 화면 관련 결정으로 계약 범위 고정 ⬜
- 태그: [H] · PRD: F1 "직접 판단 지점", R11-14, 18, 19, 20 · 선행: 027, 028, 029 · 기한: 2026-10-14
- 구현 사항
  - [ ] 목업을 직접 눌러 보고 승인 또는 수정 요청
  - [ ] R11-14(SCR-06·07 통합), R11-18(환산 입력 저장), R11-19(드라마 찜 단위), R11-20(시즌 0 안내) 결정
- 완료 기준
  - V-H: 승인일과 네 항목 결정을 기록하고 10.2를 갱신했다.
- 준비(2026-10-08): Claude가 목업 둘러보기 순서표(경로·확인할 점)와 네 항목의 선택지 비교표(ERD·계약에 주는 영향 포함)를 만들어 둔다. 목업 완성 뒤 사용자가 직접 눌러 보고 정한다. 근거: `docs/TASK018_031_PLAN.md` 5절.

#### Task 031: OpenAPI 초안 도출과 orval 생성물 연결로 MVP 계약 고정 ⬜
- 태그: [F][B] · PRD: F1 · 선행: 030, 023(`x-planned` 제외 동작) · 브랜치: `feature/f1-openapi-draft`
- 구현 사항
  - [ ] 승인된 목업에서 MVP API를 `docs/api/openapi.yaml`에 정의(경로 규칙, operationId, tags, `CommonResponseXxx`, example, nullable·required·enum). 별도 초안 파일은 두지 않는다(R-20 확정)
  - [ ] 아직 구현되지 않은 operation마다 `x-planned: true`를 달고 description에 "제안"을 표시한다. 이미 구현된 API(Task 019 서비스 목록 등)에는 달지 않는다. 이 표시는 각 구현 Task가 지운다(4.2)
  - [ ] `npm run api:generate`(입력은 `openapi.yaml` 하나, Task 017 설정 그대로)로 생성 훅·MSW로 바꾸고, 화면 전용 데이터만 `src/mocks/fixtures/`에 남긴다. `features/*/hooks`에서 `select: (res) => res.data`
  - [ ] 사용자(백엔드 담당)가 계약 초안을 승인
- 완료 기준
  - `npm run build` 성공, V-F: Task 027~029 흐름이 생성된 목업으로 그대로 동작한다.
  - V-B: `x-planned` operation을 제외한 계약 대조 테스트(Task 023)가 통과한다.
  - 임시 타입 제거(2026-10-08 결정): Task 027~029에서 만든 `features/*/mockTypes.ts`를 모두 생성 타입으로 바꾸고 지운다. `Get-ChildItem -Recurse -Filter mockTypes.ts frontend/src`의 결과가 비어 있다(`frontend.md` "목업 단계의 임시 타입 예외").

#### Task 032: ERD 보정 반영으로 스키마 확정 근거 마련 ⬜
- 태그: [H] · PRD: F1(ERD 보정) · 선행: 030, 031
- 구현 사항
  - [ ] R11-18(환산 입력 저장)·R11-19(드라마 찜 단위) 결정과 계약 초안에서 나온 ERD 변경 필요 항목을 목록으로 정리
  - [ ] 사용자가 `docs/ERD.md`를 고치고 버전을 올린다(이 로드맵 작업은 ERD를 수정하지 않음). 변경이 없으면 "변경 없음"을 기록
- 완료 기준
  - V-H: 반영 항목과 ERD 버전(또는 변경 없음)을 기록했다.

#### Task 033: 작품·상품 영역(A·B)과 배치 메타 Flyway 마이그레이션 작성으로 수집 스키마 확정 ⬜
- 태그: [B] · PRD: B0, 8.1 · 선행: 019, 030, 032, 004 · 브랜치: `feature/b0-schema-catalog`
- 구현 사항
  - [ ] Task 032에서 A·B 영역 변경이 나오면 아직 병합 전인 V2·V3에 넣고, 이미 `develop`에 병합된 파일(V1 `ott_service`)이 대상이면 그 파일을 고치지 않고 새 마이그레이션 버전으로 반영한다(4.2)
  - [ ] V2 작품 영역: `tmdb_provider`, `title`(`UNIQUE(media_type, tmdb_id)`), `watch_unit`(`UNIQUE(title_id, season_number) NULLS NOT DISTINCT`), `availability`(`UNIQUE(watch_unit_id, ott_service_id)`), `availability_change`, `genre`, `title_genre`, `title_cast`(`UNIQUE(title_id, cast_order)`)
  - [ ] V3 상품 영역: `subscription_product`, `product_component`, `product_price`(`UNIQUE(product_id, effective_from)`). 마스터 FK는 NO ACTION
  - [ ] V4 Spring Batch PostgreSQL 메타데이터(`BATCH_*`). ERD E 영역이지만 수집 Job(Task 043)이 먼저 필요해 여기서 만든다
  - [ ] Task 004 매핑표로 `tmdb_provider` 시드
- 완료 기준
  - V-B: 마이그레이션 테스트 통과. 영화 행(시즌 NULL) 중복과 `title` (media_type, tmdb_id) 중복이 거부된다.

#### Task 034: 사용자·플랜·알림 영역(C·D·E) 마이그레이션과 FK 동작 테스트 작성으로 사용자 데이터 스키마 확정 ⬜
- 태그: [B] · PRD: B0, FR-07, 12, 14 · 선행: 032, 033 · 브랜치: `feature/b0-schema-user-plan`
- 관련: ERD C·D·E, "FK 삭제 동작"·"참조 컬럼 인덱스", TECH T-1
- 구현 사항
  - [ ] V5·V6·V7: `users`, `user_setting`, `user_subscription`, `user_price_override`, `wishlist_item`, `plan`, `plan_month`, `plan_month_product`, `plan_item`, `plan_assignment`, `calc_run`, `notification_outbox`, `shedlock`(Task 032 보정 반영)
  - [ ] 유니크 제약 전부: `users.email` UK, `users(oauth_provider, oauth_subject)`, `user_subscription(user_id, ott_service_id)`, `user_price_override(user_id, product_id)`, `wishlist_item(user_id, watch_unit_id)`, `plan(user_id) WHERE status='ACTIVE'`·`…'DRAFT'`, `plan_month(plan_id, month_index)`, `plan_item(plan_id, watch_unit_id)`, `plan_assignment(plan_item_id, plan_month_id)`, `notification_outbox.dedupe_key`. ERD "참조 컬럼 인덱스"가 일부 FK 인덱스를 생략한 근거가 이 유니크 제약의 선두 컬럼이므로 하나도 빠뜨리지 않는다
  - [ ] FK 동작은 ERD 표 그대로(CASCADE / SET NULL / NO ACTION, 각 FK에 SQL 주석). 추가 인덱스: `plan_month_product(plan_month_id)`, `plan_assignment(plan_month_id)`, `calc_run(plan_id)`, `plan(previous_plan_id)`, `plan(user_id)`, `notification_outbox(user_id)`, `notification_outbox(status, next_attempt_at)`
  - [ ] Testcontainers 테스트: DRAFT 삭제 후 하위 4개 테이블 0행, `users` 삭제 후 사용자 소유 행 0행·`calc_run.plan_id` NULL, ACTIVE 2개 삽입 시 유니크 위반
- 완료 기준
  - V-B: 위 테스트가 통과한다(`ddl-auto=validate`는 ON DELETE를 검사하지 않으므로 이 테스트가 기준).

#### Task 035: 작품·상품 영역 엔티티와 리포지토리 작성으로 도메인 모델 마련 ⬜
- 태그: [B] · PRD: B0 · 선행: 033, 023 · 브랜치: `feature/b0-catalog-entities`
- 구현 사항
  - [ ] `catalog/domain`(Title, WatchUnit, Availability, AvailabilityChange, Genre, TitleCast), `provider/domain/TmdbProvider`, `product/domain`(SubscriptionProduct, ProductComponent, ProductPrice)
  - [ ] Setter 없음, `protected` 기본 생성자, enum `STRING`, 단방향 `@ManyToOne(LAZY)`. `Availability` 상태·tier 플래그는 도메인 메서드 하나에서만 변경
- 완료 기준
  - V-B: `ddl-auto=validate` 통합 테스트와 리포지토리 기본 조회 테스트가 통과한다.

#### Task 036: 디자인 방향 결정으로 토큰 확정 근거 마련 ⬜
- 태그: [H] · PRD: H3, R11-1 · 선행: 027~029 · 기한: 2026-10-21(MVP 2주차)
- 구현 사항
  - [ ] 색·글꼴·간격·모서리 방향 결정(3상태는 색 외 구분 유지), 참고 화면·시안
  - [ ] Task 025·026에서 임시로 정한 값 확정(이월, 2026-10-09): 오류 토스트의 색 톤(지금 진한 빨강 `--destructive` 바탕+흰 글자, 대안은 연한 빨강 바탕+빨간 테두리·아이콘+검정 글자. 빨간 글자는 대비 기준 미달), 큰 화면 크기(지금 폭 448px·글자 14px), 아이콘 크기(지금 16px), 모바일에서 토스트가 본문 제목을 약 4초 덮는 것을 허용할지(지금은 머리글을 덮지 않도록 위쪽 69px에서 시작), 오류 외 토스트(성공·정보·경고)의 모양
- 완료 기준
  - V-H: 결정 내용(토큰 값 후보)을 기록했다. 늦어지면 F2 이후 화면을 다시 손봐야 한다(PRD 9절).

#### Task 037: 디자인 토큰 확정과 공통 컴포넌트 정리로 화면 일관성 확보 ⬜
- 태그: [F] · PRD: F2 · 선행: 036 · 브랜치: `feature/f2-design-tokens`
- 구현 사항
  - [ ] `src/styles/tokens.css`에 Task 036 결정 반영, 직접 쓴 색 값 점검
  - [ ] shadcn/ui 변형은 variant 또는 `components/common/` 래핑, 키보드 포커스·`alt`·대비 점검
  - [ ] Task 025·026에서 이월한 정리(2026-10-09): ① 토스트 위치 `offset`이 머리글 높이(57px)를 간격 토큰으로 손수 계산한 값(`providers.tsx`의 `HEADER_HEIGHT`)이라 머리글의 여백·높이·줄 수가 바뀌면 같이 고쳐야 한다 — 값을 공유하는 방법 검토. ② `index.css`의 `--destructive-foreground`는 `:root`(light)에만 있고 `.dark`용 값은 없다 — 다크 모드 도입 시 정의. ③ `Toaster`의 `theme="light"` 고정과 `next-themes`(shadcn `sonner`가 끌고 온 패키지, 지금은 쓰이지 않음) 정리. ④ 붉은 토스트 위의 키보드 포커스 링이 잘 보이는지(sonner 기본 포커스 링 `rgba(0,0,0,0.2)`) 점검. ⑤ 여러 토스트가 쌓일 때의 모양(지금은 같은 id라 1개만 뜸). ⑥ `errorMessages.ts`의 기본 메시지 2개(문서에 없는 코드용, 네트워크 오류용)는 Task 025에서 임의로 정한 문구라 확정. ⑦ Task 026의 임시 토큰(`src/styles/tokens.css`)을 036 결정값으로 교체
  - 2026-10-09 갱신(Task 026 결과): 어두운 테마(미드나잇 블루 D2c)가 기본으로 확정돼 ②는 "`.dark`용 값 정의"가 아니라 `:root`가 곧 어두운 값이고 `index.css`에 남은 `@custom-variant dark`와 `.dark` 블록을 정리하는 일로 바뀌었고, ③의 `Toaster`는 이미 `theme="dark"`다(`next-themes` 제거 여부만 남음). ⑧ 오류 토스트 빨강(`--destructive-solid` #e7000b, 흰 글자 대비 4.76:1로 기준 4.5:1에 근접)을 더 짙게(`oklch(0.5 0.18 25)`, 6.59:1) 할지. ⑨ 버튼·입력창 크기 단계(32/36/40px)를 하나로 정리하고 토스트 offset의 머리글 높이 값과 맞추기. ⑩ shadcn `dark:` 변형을 늘 켤지(outline 버튼이 `bg-input/30`이 되는 영향 비교). ⑪ 포커스 링 `ring-ring/50`(바탕 대비 약 3.28:1)을 `--ring` 그대로로 진하게. ⑫ 머리글 "로그인"·푸터 링크의 누르는 영역, 가격 출처 알약(`bg-muted`가 카드와 1.11:1) 눈에 띄게 할지. ⑬ Pretendard의 숫자 폭 고정(`tabular-nums`) 동작 확인. ⑭ 임시 타입 `displayTypes.ts`는 Task 031에서 계약 enum으로 교체.
- 완료 기준
  - V-F: SCR-01, 02, 08, 10 스크린샷을 남겼고 콘솔 에러 0건, 3상태가 흑백에서도 구분된다.

---

## 7. Phase 3 — 핵심 기능 개발 · 0/30

**목표.**
- MVP 핵심 가치(3개월 롤링 플랜 계산, 데이터 수집, 공개 조회, 인증·사용자 데이터, 단품 요금)를 실제로 동작시키고 목업을 실제 API로 교체한다.
- 계산 엔진(038~040)은 1주차에 착수한다. 각 백엔드 API Task는 Task 031이 `openapi.yaml`에 `x-planned: true`로 넣은 해당 operation을 구현하고 같은 PR에서 표시를 지운다(4.2). 초안에 없던 API(제안 경로)는 계약에 먼저 추가한다.

#### Task 038: 계산 엔진 입력 모델과 배정 규칙 구현으로 계산 기반 마련 ⬜
- 태그: [B] · PRD: B2, FR-12, 5.4 · 선행: 006, 012 · 브랜치: `feature/b2-engine-assignment`
- 관련: TECH 1절(쿠팡플레이 포함 여부는 입력 단계 필터라 엔진은 바뀌지 않음)
- 구현 사항
  - [ ] `engine/` 순수 Java record: 시청 단위, 상품, 계산 조건, 결과
  - [ ] 배정 규칙: 시청 가능 상품이 없는 단위(NO_PROVIDER·UNKNOWN)는 맨 뒤 → 우선순위 → 필요 분 짧은 순 → `watchUnitId`(정렬은 `PlanInput` 생성자가 한 번), 가장 이른 달부터, 긴 시즌은 서비스와 상관없이 연속된 달(그 시즌을 볼 수 있는 상품이 선택된 달)에 이어서 분할하고 다 본 달에 점수(2026-10-09 결정, 예: 1월 넷플릭스·2월 티빙), 실제 시청 달도 연속(가운데 달 배정이 0분이면 분할 불가), 마지막 조각 외 한 달 최소 120분(`MIN_LONG_SEASON_SEGMENT_MINUTES`, PRD 11절 34번), 3개월 안에 안 끝나면 미배정(일부 배정 시간은 풀림)
  - [ ] 시청 가능 규칙(FREE 상시 0원, SUBSCRIBED 이번 달 0원(평가기가 0번째 달에 선택 없이도 시청 가능으로 취급), "모름"은 시청 가능 아님), `Evaluator`(`new Evaluator(planInput)` 후 `evaluate(selection)` 반복 호출, 상품 32개 초과 시 생성자 예외)와 목적함수 비교기(`EvaluationComparator`)(must 완주 → 점수 → 총비용 → 결제를 미루는 쪽(이른 달 새로 결제하는 상품이 적은 쪽, 2026-10-09 결정) → 상품 ID 사전순), 이유 코드(`NO_PROVIDER` → `UNKNOWN` → `BUDGET` → `TIME`)
  - [ ] 규칙 구현부에 PRD 5.4 규칙 주석
- 완료 기준
  - V-B: Spring 컨텍스트 없는 정답 테스트(긴 시즌 분할, 분할 불가, 실제 시청 달 끊김, 한 달 최소 2시간, 이유 코드 4종, 동점)가 통과한다.

#### Task 039: 정확해·그리디 Solver 구현으로 조합 탐색 완성 ⬜
- 태그: [B] · PRD: B2, FR-12, 13, TECH T-4 · 선행: 038 · 브랜치: `feature/b2-engine-solvers`
- 구현 사항
  - [ ] `engine/PlanSolver`, `ExactSolver`(지배 조합 제거, 3개월 중첩 순회), `GreedySolver`
  - [ ] 후보 조합 수 상한 초과 시 그리디 전환(상한은 설정 값, R11-30 전까지 잠정값), 선택 알고리즘을 결과에 남김
- 완료 기준
  - V-B: 두 Solver가 정답과 일치하는 케이스, 그리디가 정확해보다 나쁜 케이스(차이 단언), 상한 초과 시 GREEDY 전환 테스트가 통과한다.

#### Task 040: 정답 테스트 세트와 알고리즘 비교 측정 작성으로 엔진 품질 근거 확보 ⬜
- 태그: [B] · PRD: B2, FR-13, MVP 완료 기준 ③(정답 테스트) · 선행: 039 · 브랜치: `feature/b2-engine-benchmark`
- 구현 사항
  - [ ] 정답 입력·기대 결과 세트(예산·찜·구독 상태 조합). Task 065가 같은 세트를 쓰도록 위치를 정한다(제안: `backend/src/main/resources/engine-benchmark/`)
  - [ ] 입력 정규화와 `input_hash`(SHA-256) 계산기(정렬된 단위·상품·가격, 예산, 시청 분, `data_as_of`)
  - [ ] 두 Solver 점수·비용 차이와 계산 시간(3회 중앙값) 비교 테스트, 최악 입력(단품 7개, 큰 찜) 탐색 시간 측정
  - [ ] 최악 입력과 후보 상한 근처에서 **힙 사용량**을 함께 기록한다(메모리 예산 측정 지점, 4.6)
- 완료 기준
  - V-B: 정답 세트 전체 통과. 비교 결과표(점수 차, 비용 차, 시간)와 R11-30 잠정 상한 근거를 `기록:`에 남겼다. `calc_run` 기록은 Task 065.

#### Task 041: TMDB Feign 클라이언트 구현으로 외부 데이터 호출 기반 마련 ⬜
- 태그: [B] · PRD: B3, 5.5 · 선행: 024 · 브랜치: `feature/b3-tmdb-client`
- 구현 사항
  - [ ] `infra/tmdb/TmdbClient`: discover, 작품·시즌 상세, 작품·시즌별 제공처, 검색. DTO는 `infra/tmdb` 안에만
  - [ ] Bucket4j 로컬 버킷(`app.tmdb.rate-limit.*`), `ErrorDecoder`(429·5xx 재시도 가능), `Retryer` 지수 백오프·`Retry-After` 우선, `tmdb.call` 카운터
  - [ ] WireMock 테스트, 샘플 JSON `src/test/resources/tmdb/`
- 완료 기준
  - V-B: 정상, 429 후 재시도, 타임아웃 테스트가 통과한다(실제 TMDB 호출 없음).

#### Task 042: 제공 상태 판정과 러닝타임 계산 도메인 로직 구현으로 3상태 판정 확정 ⬜
- 태그: [B] · PRD: B3, FR-02, 03, 8.2 · 선행: 035, 041 · 브랜치: `feature/b3-availability-rules`
- 구현 사항
  - [ ] 작품·시즌별 제공처의 KR flatrate에 매핑 서비스가 직접 있을 때만 AVAILABLE, 대여·구매만이면 아님, KR 없음 UNKNOWN(`NO_KR_DATA`), 쿠팡플레이 영화 UNKNOWN(`STRUCTURAL_GAP`)
  - [ ] 상태가 바뀌면 `availability_change` 기록
  - [ ] 러닝타임: 영화 runtime, 시즌은 방영 회차 합, 빈 값은 120/45/24분 추정(설정 키, R11-25)
  - [ ] 시즌 0 제외, 한국어 줄거리 없으면 영어 대체·`overview_lang`, 출연진 상위 10명
- 완료 기준
  - V-B: discover 오판정(웨이브 구매만), 시즌별 제공처 상이(8.2 랑야방 구조), 추정 표시 케이스가 통과한다.

#### Task 043: 수집 Job 후보 discover Step 구현으로 수집 대상 확보 ⬜
- 태그: [B] · PRD: B3, FR-18(MVP 일괄), 5.5 · 선행: 033, 042 · 브랜치: `feature/b3-collect-candidates`
- 관련: TECH 3절, T-2
- 구현 사항
  - [ ] `collect/` Job 골격과 첫 Step: 서비스별 discover(KR 구독)로 후보 `title` 저장. 처음 본 제공처는 `tmdb_provider`에 `first_seen_at`과 함께 미매핑 저장
  - [ ] Job 파라미터 `targetDate`(LocalDate) + `tier`. MVP 일괄 수집은 **`tier=ALL`**(R-16 확정, `tier`의 뜻은 TECH 3절 설명 참고). 작품의 `title.refresh_tier`(DAILY/WEEKLY)와 Job 파라미터 `tier`가 같은 enum을 공유하는지는 이 Task에서 정한다(공유하지 않으면 `ALL`은 Job 파라미터에만 둔다)
  - [ ] 작은 chunk, 짧은 Feign 타임아웃, 연결 풀 사용량 지표(T-2)
- 완료 기준
  - V-B: Testcontainers + WireMock으로 작은 discover 응답을 수집했을 때 후보 `title`·`tmdb_provider` 행이 기대값과 같다.

#### Task 044: 수집 Job 상세·시즌·판정 Step 구현으로 제공 상태 저장 완성 ⬜
- 태그: [B] · PRD: B3, FR-18 · 선행: 043 · 브랜치: `feature/b3-collect-details`
- 구현 사항
  - [ ] 상세·시즌 수집 Step(`watch_unit`, 장르, 출연진), 제공 상태 판정·이력 Step(`availability`, `availability_change`)
  - [ ] `JpaPagingItemReader`는 Job 파라미터의 고정 기준 시각으로 필터하고 `id` 순 정렬(페이지 밀림 방지)
  - [ ] TV 시즌 호출량 지표(R11-26 측정용)
- 완료 기준
  - V-B: 작은 후보 세트 전체 Job 실행 후 `title`·`watch_unit`·`availability`·`availability_change`가 기대값과 같다.

#### Task 045: 수집 실행·복구·이력 관리자 API 구현으로 수동·cron 실행 경로 마련 ⬜
- 태그: [B] · PRD: B3, FR-18, FR-20 · 선행: 044, 031 · 브랜치: `feature/b3-collect-admin-api`
- 구현 사항
  - [ ] `POST /api/admin/collect/run`: `TaskExecutor`를 가진 `JobOperator`로 비동기 시작, 202 + `jobExecutionId`. 실행 중 409 `BATCH_ALREADY_RUNNING`, 완료 409 `BATCH_ALREADY_COMPLETED`, 실패는 restart
  - [ ] 관리자 수집 API의 `tier` 파라미터 enum은 `DAILY`·`WEEKLY`·`ALL`이다(R-16 확정). MVP 일괄 수집은 `ALL`을 쓴다. 계약(`openapi.yaml`)과 코드의 enum을 같게 둔다
  - [ ] `targetDate` 직접 지정은 `local` 프로필에서만 허용한다(설정 키 제안: `app.collect.allow-target-date-override`, local만 `true`). 운영(`prod`)에서 지정하면 거부(제안: 400 `VALIDATION_FAILED`)하고 서버 날짜를 쓴다. 같은 날 409를 피해 재수집을 재현하는 용도다(Task 067, 069)
  - [ ] 복구 `POST /api/admin/collect/{executionId}/recover`(제안): `recover` 후 `restart`
  - [ ] 이력 조회: `BATCH_*` 읽기 전용(시작·종료, 성공/실패, 처리 건수, 실패 사유)
  - [ ] FR-20 "변경된 작품": 실행별 `availability_change`(`job_execution_id`로 연결)의 작품·시즌·서비스·이전/이후 상태 조회(이력 상세 또는 별도 조회, 경로는 제안). 재계산 대상 플랜 수는 2단계 Task 069이 계약에 추가한다
  - [ ] `ADMIN_BATCH_TOKEN` 필터(상수 시간 비교, `/api/admin/collect/**`·`/api/admin/plans/monthly/**`만. 재계산 경로를 토큰 허용 범위에 둘지는 TECH 4절 갱신을 따른다, R-21). ADMIN 역할 검사는 Task 054에서 합친다. 그 전까지 이력·복구 API는 토큰 필터로만 보호된다
- 완료 기준
  - V-API: 토큰으로 실행 202, 즉시 재호출 409 `BATCH_ALREADY_RUNNING`, 완료 후 409 `BATCH_ALREADY_COMPLETED`, 토큰 없음 401. local에서 다른 `targetDate`로 실행하면 202. USER 역할 403은 Task 054 완료 기준.
  - V-B: 중단 실행 복구 테스트, `prod` 프로필에서 `targetDate` 지정 거부 테스트, 실행별 변경된 작품 조회 테스트가 통과한다.

#### Task 046: 제공처 매핑 관리 API 구현으로 매핑 운영 수단 마련 ⬜
- 태그: [B] · PRD: B3, FR-18(R11-23 흡수안), SCR-15 · 선행: 035, 031 · 브랜치: `feature/b3-provider-mapping-api`
- 구현 사항
  - [ ] `GET /api/admin/providers`(미매핑 여부, `first_seen_at`), `PUT /api/admin/providers/{providerId}`(서비스 또는 없음, tier)
  - [ ] 매핑 변경은 다음 수집부터 반영됨을 응답 설명에 적는다
- 완료 기준
  - V-API: 미매핑 제공처 매핑 후 재조회에 반영된다. 이 API는 Task 054 전까지 보호되지 않는 상태(local 전용)이고, USER 403 확인은 Task 054 완료 기준이다.

#### Task 047: 수집 cron 워크플로를 비활성 상태로 작성해 Hobby 전환 후 자동 실행 준비 ⬜
- 태그: [B] · PRD: B3, FR-18, R11-4 · 선행: 045, 015 · 브랜치: `feature/b3-collect-cron`
- 관련: TECH 3절(cron 제약, Cloudtype 직접 호출)
- 구현 사항
  - [ ] `.github/workflows/collect.yml`: `workflow_dispatch`만, `schedule`은 주석(활성화 값 예: `17 18 * * *` = KST 03:17)
  - [ ] Vercel을 거치지 않고 Cloudtype 주소로 직접 호출하며 `x-origin-secret`(`ORIGIN_SECRET`)과 `Authorization: Bearer`(`ADMIN_BATCH_TOKEN`)를 함께 보낸다. 202·409는 성공, 그 외 실패
  - [ ] 워크플로는 수집 시작만 호출한다. 재계산(Task 069)과 월초 알림 생성(Task 073)은 백엔드가 수집 Job 완료 뒤 이어서 실행하므로 워크플로 단계를 추가하지 않는다(R-21 확정)
  - [ ] 공개 저장소 60일 무활동 시 예약 워크플로가 꺼진다는 주석
- 완료 기준
  - 워크플로의 호출 명령과 같은 요청을 PowerShell로 로컬 백엔드에 보내 202, 재호출 409를 확인했고 YAML 문법이 유효하다. GitHub Actions 실행 확인은 `main` 병합 후 Task 095.

#### Task 048: 첫 전체 수집 실행과 측정으로 용량·러닝타임 근거 확보 ⬜
- 태그: [H] · PRD: B3, R11-25, R11-26 · 선행: 045, 044 · 기한: 2026-10-21
- 구현 사항
  - [ ] 로컬 ①에서 실행·측정한다(R-14 확정). 운영 DB 적재는 MVP 배포 Task 095에서 한다
  - [ ] TV 시즌 호출량, 소요 시간, 실패 건수, DB 용량(`pg_database_size`, Supabase 무료 한도 500MB와 비교, 인덱스·WAL 포함), Redis ops 측정(R11-26). 수집 사이클의 **최대 힙·메모리 사용량**도 기록한다(메모리 예산 측정 지점, 4.6)
  - [ ] 실측 러닝타임 중앙값으로 기본값 보정안(R11-25). 중단되면 Task 045 복구 API로 이어서 실행
- 완료 기준
  - V-H: 측정값을 기록했고 R11-25·26 결정을 사용자에게 올렸다.

#### Task 049: 공개 API 요청 제한 구현으로 비로그인 남용 방지 ⬜
- 태그: [B] · PRD: B4, FR-05 · 선행: 024, 019 · R11-12 확정(2026-10-09) · 브랜치: `feature/b4-rate-limit`
- 구현 사항
  - [ ] `global/ratelimit/`: Bucket4j Redis 버킷, 키는 방문자 IP인 `remoteAddr`(`server.forward-headers-strategy: framework`가 `X-Forwarded-For` 첫 값을 반영, Task 021 실측: Vercel 경유에서 `x-real-ip`는 Vercel의 IP라 키로 쓰면 모든 방문자가 한 버킷에 묶인다). 2026-10-09 정정
  - [ ] 일반 공개 버킷(fail-open, 인증 경로 `/api/auth/**`·`/api/oauth2/**`·`/api/login/**`도 적용, backend.md), 단건 수집 검색 버킷(fail-closed, 더 낮은 한도)
  - [ ] 초과 시 429 `RATE_LIMIT_EXCEEDED`. 한도는 설정 키(`app.rate-limit.*`), **R11-12 확정값: 일반 버킷 IP당 분당 60회, 단건 수집 검색 버킷 IP당 분당 10회**(순간 몰림 허용량은 한도의 약 1/3). 단건 수집 검색 버킷이 더 낮은 이유는 TECH 3절 "TMDB는 언제 호출하나". 요청 1건이 쓰는 Redis 연산 수는 구현 후 측정한다(Redis Cloud 무료 초당 100 ops를 캐시와 나눠 씀)
- 완료 기준
  - V-B: 한도 초과·IP 헤더 우선순위·fail-open/closed 테스트가 통과한다. V-API: 반복 호출 시 429와 `errorCode`.

#### Task 050: 작품 검색 API 구현으로 공개 검색 제공 ⬜
- 태그: [B] · PRD: B4, FR-01, 02, SCR-01 · 선행: 044, 049, 031 · 브랜치: `feature/b4-title-search`
- 구현 사항
  - [ ] `searchTitles`: DB 제목 검색, 영화·드라마 구분, 7개 서비스 상태 요약, 페이지네이션
  - [ ] DB에 없으면 TMDB 검색으로 단건 수집·저장(단건 수집 버킷 통과, 외부 호출은 트랜잭션 밖)
  - [ ] TMDB 실패 시 DB 결과만 반환하고 외부 수집 실패 여부를 응답에 표시
- 완료 기준
  - V-B: WireMock으로 정상, 단건 수집, TMDB 실패 대체 테스트 통과. V-API: 동명 영화·드라마가 구분돼 반환된다.

#### Task 051: 작품 상세 API 구현으로 공개 상세 제공 ⬜
- 태그: [B] · PRD: B4, FR-02, 03, SCR-02 · 선행: 035, 024, 031 · 브랜치: `feature/b4-title-detail`
- 구현 사항
  - [ ] `GET /api/public/titles/{mediaType}/{tmdbId}`(`getTitleDetail`): 포스터, 원제, 줄거리·`overviewLang`, 장르, 날짜, 러닝타임·추정, 출연진 10명, 서비스별 상태·`unknownReason`, 시즌별 제공처·총 시간, 기준일
  - [ ] Redis `titleDetail` 캐시, 없으면 404 `TITLE_NOT_FOUND`
- 완료 기준
  - V-API: 영화·드라마 상세가 `CommonResponse`, 없는 작품은 404 ProblemDetail. V-B: 캐시 적중 테스트.

#### Task 052: 공개 화면 실제 API 연결과 출처 표기 완성으로 비로그인 기능 제공 ⬜
- 태그: [F] · PRD: F3, FR-01~03, 05, MVP 완료 기준 ② · 선행: 031, 050, 051, 003 · 브랜치: `feature/f3-public-api`
- 구현 사항
  - [ ] 검색·상세 목업 핸들러를 끄고 실제 API로 교체, 외부 수집 실패·429 안내
  - [ ] About: TMDB 승인 로고·고지문(Task 003 확인 조건), JustWatch 출처. 모든 제공처 표시에 출처·기준일
  - [ ] RTL 테스트: 검색 흐름
- 완료 기준
  - V-FS: 비로그인으로 검색 → 상세가 실제 데이터로 동작하고 상태 배지가 DB 값과 같다.

#### Task 053: Google OAuth2 로그인·JWT 발급·AT 검증 구현으로 회원 인증 확보 ⬜
- 태그: [B] · PRD: B5, FR-06, TECH 4절, T-7 · 선행: 034, 023 · 브랜치: `feature/b5-oauth-login`
- 구현 사항
  - [ ] `user/domain/User` 엔티티·리포지토리(`UNIQUE(oauth_provider, oauth_subject)`로 조회·생성)
  - [ ] Task 016 임시 설정을 실제 `SecurityConfig`로 교체: STATELESS, `permitAll`은 `/api/public/**`와 인증 경로(`/api/auth/**`, `/api/oauth2/**`, `/api/login/**`), 그 외 `/api/**`는 인증 필요
  - [ ] OAuth 경로를 `/api` 아래로(`authorizationEndpoint.baseUri("/api/oauth2/authorization")`, `redirectionEndpoint.baseUri("/api/login/oauth2/code/*")`), 쿠키 기반 `AuthorizationRequestRepository`
  - [ ] 성공 핸들러: RT 쿠키(HttpOnly, Secure, SameSite=Lax, Path=`/api/auth`) + 302 `/auth/callback`, URL에 토큰 없음. AT 30분(`app.jwt.access-ttl`), `jjwt-jackson` runtime
  - [ ] AT 검증 필터(`global/security`, Bearer → 인증 객체)
- 완료 기준
  - V-B: 아직 구현되지 않은 회원 경로(예: `/api/me/settings`)에 토큰 없이 요청하면 401 ProblemDetail, 유효 AT면 401이 아님(핸들러가 없어 404), 공개 API는 200. 회원 API가 생기는 Task 055에서 같은 검사를 실제 API로 다시 실행한다.
  - 로컬에서 Vite 프록시 경유 Google 로그인 → `/auth/callback` 도착 → RT 쿠키 생성을 브라우저로 확인했다.

#### Task 054: 토큰 재발급·로그아웃과 관리자 권한 분리 구현으로 인증 수명주기 완성 ⬜
- 태그: [B] · PRD: B5, FR-06, 17 · 선행: 053 · R11-21 확정(2026-10-09) · 브랜치: `feature/b5-token-refresh`
- 구현 사항
  - [ ] `POST /api/auth/refresh`(RT 검증 → 새 AT, RT 회전 14일), `POST /api/auth/logout`(폐기·쿠키 삭제). 두 경로에 `Origin` = `APP_FRONTEND_ORIGIN` 검사
  - [ ] `RefreshTokenStore` 인터페이스와 **Redis TTL 구현**(R11-21 확정: RT는 해시로 저장, 재발급 때 회전, 이미 쓴 RT가 다시 쓰이면 해당 계열 무효화). 서명 검증만 하는 구현은 만들지 않는다. 알려진 한계(Redis가 비워지면 모든 사용자가 다시 로그인)는 TECH 4절
  - [ ] `/api/admin/**`는 ADMIN 역할 필요, 배치 토큰 경로는 토큰 또는 ADMIN. 로컬·테스트에서는 DB에서 `users.role`을 직접 바꿔 ADMIN을 만든다. 운영 지정 방법은 R-5(2026-10-09 **DB 직접 수정으로 확정**, 이 Task에 구현이 늘지 않는다. 절차는 Task 095 ②)
- 완료 기준
  - V-B: RT 회전(이전 RT 거부, 이미 쓴 RT 재사용 시 계열 무효화), Origin 불일치 거부, USER 토큰으로 `/api/admin/providers`·`/api/admin/collect/run` 호출 시 403(Task 045·046 이관분) 테스트가 통과한다.
  - V-API: refresh·logout 호출을 확인했다.

#### Task 055: 내 설정·구독 상태 API 구현으로 계산 조건 저장 ⬜
- 태그: [B] · PRD: B5, FR-08, 10, SCR-07, R11-18 · 선행: 053, 031 · 브랜치: `feature/b5-user-settings`
- 구현 사항
  - [ ] `UserSetting`, `UserSubscription` 엔티티
  - [ ] `GET/PUT /api/me/settings`: 예산·시청 분 필수(없으면 400 `VALIDATION_FAILED`), `watchPreset`(프리셋 값은 설정 키), 환산 입력은 R11-18 결정대로. **월 시청 분은 120 이상**(미만이면 400 `VALIDATION_FAILED`, 2026-10-09 사용자 결정: 긴 시즌 한 달 최소 120분 규칙 때문에 120 미만이면 긴 시즌이 하나도 배정되지 않는다. PRD 11절 34번, Task 038 `MIN_LONG_SEASON_SEGMENT_MINUTES`와 같은 값을 쓴다)
  - [ ] `GET/PUT /api/me/subscriptions`: SUBSCRIBED/FREE, `productId`, 결제일 기본 1, 미구독은 행 삭제
  - [ ] 온보딩 완료 판정 기준을 계약에 명시
- 완료 기준
  - V-B: 사용자 격리, 필수값 400, 비로그인 401 테스트가 통과한다. V-API: 저장 후 조회가 일치한다.

#### Task 056: 찜·우선순위·시청 체크 API 구현으로 계산 대상 관리 ⬜
- 태그: [B] · PRD: B5, FR-09, R11-19 · 선행: 055, 051 · 찜 상한 잠정값 30 확정(2026-10-09) · 브랜치: `feature/b5-wishlist`
- 구현 사항
  - [ ] `/api/wishlist-items` 추가·삭제·목록, 우선순위 변경, "다 봤음"(`watched_at`) 토글. 중복 추가 무시, 드라마는 R11-19 결정 단위
  - [ ] 목록: 필요 시간·추정, 서비스별 상태, "이미 시청 가능"
  - [ ] 찜 상한(**잠정값 30개**, 시청 단위 기준이라 드라마는 시즌마다 1개. 설정 키로 두고 측정 후 조정, R11-30) 초과 시 오류(코드는 R-9 절차로 등록). `refresh_tier` 갱신은 Task 077에서 한다
- 완료 기준
  - V-B: 다른 사용자 찜 차단, 중복 무시, 상한 테스트. V-API: 시즌 찜과 다 봤음 토글.

#### Task 057: 인증 연동(토큰 메모리 보관·재발급·라우트 가드) 구현으로 프론트 로그인 완성 ⬜
- 태그: [F] · PRD: F4, FR-06, 17 · 선행: 054, 031 · 브랜치: `feature/f4-auth`
- 구현 사항
  - [ ] `src/stores/authStore.ts`: AT·역할은 Zustand 메모리만(보안 이유 주석)
  - [ ] `/auth/callback`·앱 시작 시 `POST /api/auth/refresh`
  - [ ] `src/api/http.ts` 401 인터셉터: 재발급 1회, 동시 401이어도 요청 하나, 실패 시 로그인 화면
  - [ ] `RequireAuth`(원래 화면 복귀), `RequireAdmin`, 로그아웃. 토큰 처리 코드는 사용자가 직접 검토
  - [ ] 관리자 권한 없음 처리 방식 결정(Task 018 검토에서 이관, 2026-10-09): 로그인했지만 ADMIN이 아닌 사용자가 `/admin/*`에 들어왔을 때 "권한 없음 안내 화면"을 보일지 "홈으로 이동"할지 정한다. 백엔드 `/api/admin/**`의 403 응답(Task 053·054)과 같은 기준으로 맞추고, 그 결정을 `RequireAdmin`에 반영한다
- 결정(2026-10-09): Task 018의 `RequireAuth`·`RequireAdmin`은 지금 통과만 하는 골격이고 `TODO(Task 057)` 주석에 들어올 동작을 적어 두었다. 이 Task에서 그 주석을 실제 동작으로 바꾼다.
- 완료 기준
  - V-FS: 로그인 → 새로고침 유지 → 로그아웃 → 보호 화면 접근 시 로그인 → 원래 화면 복귀, 일반 사용자 관리자 화면 차단. 짧은 AT TTL(local)로 401 → 재발급 1회 → 재시도를 확인했다.

#### Task 058: 온보딩·찜 화면 실제 API 연결로 사용자 입력 흐름 완성 ⬜
- 태그: [F] · PRD: F5, FR-08~10, MVP 완료 기준 ① · 선행: 055, 056, 057 · 브랜치: `feature/f5-onboarding-wishlist`
- 구현 사항
  - [ ] 온보딩·설정(React Hook Form + Zod, 프리셋·환산·직접 입력, 결제일 기본 1일)
  - [ ] 상세 화면 시즌 찜·우선순위, "이미 시청 가능", 찜 목록(우선순위·삭제·다 봤음·빈 상태)
  - [ ] RTL 테스트: 찜 추가·우선순위 변경
- 완료 기준
  - V-FS: 신규 로그인 → 온보딩 저장 → 시즌 찜 → 우선순위 변경·다 봤음까지 실제 값이 반영되고 새로고침 후 유지된다.

#### Task 059: 관리자 상품·가격 API(단품) 구현으로 요금표 관리 ⬜
- 태그: [B] · PRD: B6, FR-19(단품), SCR-14 · 선행: 035, 054 · 브랜치: `feature/b6-admin-products`
- 구현 사항
  - [ ] `/api/admin/products` 목록·등록·수정·비활성(`active = false`), MVP는 STANDARD 단품
  - [ ] `/api/admin/products/{productId}/prices` 가격 추가, 기간 겹침 거부(코드는 R-9)
  - [ ] 기준일 유효 가격 선택 서비스 메서드(계산·요금 확인 공용)
- 완료 기준
  - V-B: 겹침 거부·기준일별 선택 테스트. V-API: ADMIN 성공, USER 403.

#### Task 060: 사용자 금액 수정과 적용 가격 결정 구현으로 실제 지불 금액 반영 ⬜
- 태그: [B] · PRD: B6, FR-11, SCR-09 · 선행: 059, 055, 031 · 브랜치: `feature/b6-price-override`
- 구현 사항
  - [ ] `GET/PUT /api/me/price-overrides`(`UNIQUE(user_id, product_id)`), 요금 확인 조회 API(관리자 요금표, 사용자 금액, 요금 기준일)
  - [ ] 적용 가격: `USER_OVERRIDE` / `ADMIN` / `ALREADY_PAID`(SUBSCRIBED 이번 달) / `FREE`
- 완료 기준
  - V-B: 가격 출처 4종 테스트. V-API: 수정 후 조회에 반영되고 관리자 가격은 그대로다.

#### Task 061: 관리자 화면(MVP) 실제 API 연결로 운영 화면 완성 ⬜
- 태그: [F] · PRD: F6, FR-18~20 · 선행: 045, 046, 059, 057 · 기한 전제: R11-23, R11-24(2026-10-22) · 브랜치: `feature/f6-admin-screens`
- 구현 사항
  - [ ] 상품·가격(등록·수정·비활성, 겹침 오류, Task 004 가격표 입력), 제공처 매핑, 수집(이력, 수동 실행 202·409, 복구), `RequireAdmin`
  - [ ] SCR-16 FR-20: 실행별 "변경된 작품" 목록(Task 045 조회)을 표시한다. "재계산 대상 플랜 수"와 알림 상태는 "2단계부터" 안내 자리로 두고, 실제 값 연결은 Task 070(재계산 대상 플랜 수)·074(알림 상태)에서 한다
- 완료 기준
  - V-FS: ADMIN 계정으로 상품·가격 등록, 매핑, 수집 실행·이력·변경된 작품이 실제 값으로 동작하고 USER는 차단된다.

#### Task 062: 플랜 영역 엔티티와 리포지토리 작성으로 플랜 저장 모델 마련 ⬜
- 태그: [B] · PRD: B7 · 선행: 034, 035 · 브랜치: `feature/b7-plan-entities`
- 관련: TECH T-1, ERD D·"벌크 삭제 전제"
- 구현 사항
  - [ ] `plan/domain`: Plan, PlanMonth, PlanMonthProduct, PlanItem, PlanAssignment, CalcRun
  - [ ] JPA cascade 삭제(`CascadeType.REMOVE`, `orphanRemoval`) 금지, 자식 `@ManyToOne`에 `@OnDelete(action = OnDeleteAction.CASCADE)` 표기
  - [ ] DRAFT 벌크 삭제 쿼리 `@Modifying(flushAutomatically = true, clearAutomatically = true)`
- 완료 기준
  - V-B: `ddl-auto=validate` 통과, 벌크 삭제 후 하위 4개 테이블 0행이고 영속성 컨텍스트에 삭제된 엔티티가 남지 않는다.

#### Task 063: 계산 입력 수집과 엔진 입출력 변환 구현으로 엔진 연결 ⬜
- 태그: [B] · PRD: B7, FR-12 · 선행: 062, 039, 056, 060 · 기한 전제: R11-13(2026-10-29) · 브랜치: `feature/b7-plan-input`
- 구현 사항
  - [ ] 입력 수집(readOnly): 다 봤음 제외 찜, 제공 상태, 적용 가격, 구독 상태, 설정, `data_as_of`(수집 Job 마지막 COMPLETED 종료 시각)
  - [ ] 쿠팡플레이 포함 여부 필터(R11-13, 입력 단계). 상품을 걸러낼 때 시청 단위의 시청 가능 상품 ID 집합에서도 같은 상품을 함께 거른다(`PlanInput` 생성자가 `products`에 없는 시청 가능 상품 ID를 예외로 막는다). 제외로 시청 가능 상품이 비게 된 단위를 어떤 이유 코드(`NO_PROVIDER` 등)로 보일지는 R11-13 결정(2026-10-29)에 따라 정한다. 원천 상태(모름·없음)를 구분해 넘기지 않으면 `NO_PROVIDER`로 오분류될 수 있어서다(PR #24 CodeRabbit 지적)
  - [ ] 엔티티 ↔ 엔진 record 변환(`plan` 서비스만 `engine` 호출)
- 완료 기준
  - V-B: 정답 세트 한 케이스를 DB 데이터로 구성했을 때 변환 결과가 세트 입력과 같다.

#### Task 064: 계산 트랜잭션·input_hash 캐시·계산 API 구현으로 DRAFT 생성 ⬜
- 태그: [B] · PRD: B7, FR-12, SCR-09·10 · 선행: 063, 040, 031 · 브랜치: `feature/b7-plan-calculate`
- 관련: TECH 1·2절, T-4
- 구현 사항
  - [ ] `POST /api/plans/calculate`(`calculatePlan`): 엔진은 트랜잭션 밖, 정확해 기본·상한 초과 시 그리디 **하나만** 실행하고 `plan.algorithm`에 남김
  - [ ] 쓰기 트랜잭션: `user_setting` `PESSIMISTIC_WRITE` → 기존 DRAFT 벌크 삭제(첫 동작) → DRAFT·하위 행·`calc_run` 1행 저장
  - [ ] `input_hash` 키 Redis `planCalc` 캐시(맞아도 DRAFT 저장), `plan.calculate` 타이머
  - [ ] 응답: 요약, 월별 카드, 남은 작품·이유(꼭 먼저), 절감액, 가입 즉시 해지 예약 권장, 기준일
- 완료 기준
  - V-B: 동시 계산 2건 직렬화(유니크 위반 없음), 재계산 후 이전 DRAFT 하위 행 0행, 설정 누락 400.
  - V-API: 결과가 정답 세트 한 케이스와 일치하고 `calc_run`에 선택된 알고리즘 1행이 남는다.

#### Task 065: FR-13 비교 측정 경로 구현으로 정확해·그리디 비교 기록 확보 ⬜
- 태그: [B] · PRD: B2·B7, FR-13, MVP 완료 기준 ③ · 선행: 062, 040, 054 · 브랜치: `feature/b7-algorithm-benchmark`
- 관련: TECH T-4(비교 기록은 측정 경로에서만)
- 구현 사항
  - [ ] 관리자 측정 API(제안: `POST /api/admin/plans/benchmark`): Task 040 테스트 세트를 같은 `input_hash`로 두 Solver에 3회씩 돌려 `calc_run`에 EXACT·GREEDY 행 저장(`plan_id` NULL)
  - [ ] 결과 요약(점수 차, 비용 차, 시간 중앙값) 응답
- 완료 기준
  - V-API: ADMIN으로 호출 후 `calc_run`에 같은 `input_hash`의 EXACT·GREEDY 행이 남는다(MVP ③ 충족). 결과표를 `기록:`에 남겼다.

#### Task 066: 플랜 저장·현재 플랜 조회 API 구현으로 ACTIVE 플랜 관리 ⬜
- 태그: [B] · PRD: B7, FR-14(MVP 저장) · 선행: 064, 045 · 기한 전제: R-6(2026-10-29) · 브랜치: `feature/b7-plan-activate`
- 구현 사항
  - [ ] `POST /api/plans/{id}/activate`: DRAFT 잠금·소유자 확인(없으면 404 `PLAN_DRAFT_NOT_FOUND`), `data_as_of`가 최신 수집보다 이르면 409 `PLAN_DRAFT_STALE`
  - [ ] 기존 ACTIVE → ARCHIVED → **명시적 flush** → DRAFT → ACTIVE, `previous_plan_id`
  - [ ] `getCurrentPlan`. SUBSCRIBE_GUIDE outbox 행 생성 여부는 R-6
- 완료 기준
  - V-B: 저장 후 ACTIVE 1·ARCHIVED 1(유니크 위반 없음), 수집이 더 늦으면 409, 다른 사용자 DRAFT 404. V-API: 계산 → 저장 → 조회.

#### Task 067: 요금 확인·계산·결과 흐름 실제 API 연결로 핵심 흐름 완성 ⬜
- 태그: [F] · PRD: F7, FR-11, 12, 14, MVP 완료 기준 ① · 선행: 064, 066, 058, 060, 045 · 브랜치: `feature/f7-plan-flow`
- 구현 사항
  - [ ] 요금 확인(요금표, 금액 수정, 예산·시간 요약, 기준일), 계산 진행·실패·재시도
  - [ ] 결과(요약, 월별 카드·가격 출처, 남은 작품·이유, 절감액, 해지 예약 권장, 기준일·출처·"확인된 범위"·추정)
  - [ ] 저장: 409 `PLAN_DRAFT_STALE`이면 자동 재계산 후 안내. RTL 테스트: 요금 수정, 결과 표시
- 완료 기준
  - V-FS: 로그인 → 설정 → 찜 → 요금 수정 → 계산 → 결과 → 저장을 실제 값으로 재현했고, 화면 금액이 API 응답과 같다.
  - 409 재현: 계산 후 local 프로필에서 `targetDate`를 다른 날짜로 지정해 수집을 다시 실행하고(Task 045, 같은 날 409 회피) 완료를 기다린 뒤 저장하면 409 `PLAN_DRAFT_STALE` → 자동 재계산 → 안내가 동작한다.

---

## 8. Phase 4 — 추가 기능 개발 · 0/24

**목표.**
- PRD 2단계(O1~O4)와 3단계 일부(Q3 LLM, Q4 정확도 안내·반응형·접근성)를 4.4 순서(목업 → 계약·백엔드 → 교체)로 구현한다.
- MVP 배포(Task 095) 이후 착수한다. cron 실행 확인은 2단계 배포(Task 102)에서 한다.

#### Task 068: 플랜 변경 내역 화면 목업 구현으로 재계산 결과 표현 확정 ⬜
- 태그: [F] · PRD: O1, FR-14, SCR-11 · 선행: 095, 037 · 브랜치: `feature/o1-plan-history-mock`
- 구현 사항
  - [ ] 이전 플랜 대비 변경 요약·이유, 바뀐 달·서비스·작품, 변경일, 로딩·오류·빈 상태(변경 없음)
  - [ ] 계약 변경안(변경 내역 조회 API)을 `기록:`에 남기고 사용자 승인
- 완료 기준
  - V-F: 목업으로 변경 내역 화면 흐름을 재현했고 콘솔 에러 0건.

#### Task 069: 매월 재계산·변경 요약과 변경 내역 API 구현으로 플랜 자동 갱신 ⬜
- 태그: [B] · PRD: O1, FR-14, FR-18 · 선행: 068, 066, 045 · 브랜치: `feature/o1-monthly-recalc`
- 구현 사항
  - [ ] 계약 추가(Task 068 변경안) + 재계산 Job
  - [ ] 연결(R-21 확정): 수집 Job의 `JobExecutionListener.afterJob`이 상태가 COMPLETED일 때만 재계산 Job을 비동기로 시작한다. 수집이 실패하면 재계산하지 않는다. GitHub Actions(`collect.yml`)는 수정하지 않는다(수집 시작만 호출)
  - [ ] 실패 복구용 관리자 수동 재실행 API(제안: `POST /api/admin/plans/monthly/run`, 202·중복 409). 배치 토큰 허용 여부는 TECH 4절 갱신을 따른다
  - [ ] ACTIVE만 대상. 새 ACTIVE → 이전 ARCHIVED(명시 flush) → `previous_plan_id` → `change_summary`를 한 트랜잭션에서
  - [ ] 영향 플랜 선정(제안, R-19): 이번 수집 실행의 `availability_change`(`job_execution_id`)에 걸린 `watch_unit`을 `plan_item`으로 가진 ACTIVE 플랜. 월초(제안: 그 달 첫 COMPLETED 수집)에는 전체 ACTIVE
  - [ ] 재계산 대상 플랜 수를 실행 기록으로 남기고(제안: 재계산 Job의 실행 컨텍스트 또는 읽기 건수) Task 045 수집 이력 응답에 필드로 추가한다(계약 변경, SCR-16 연결은 Task 070)
- 완료 기준
  - V-B: ACTIVE 1개, `previous_plan_id`, DRAFT 미접촉, `change_summary`, 영향 플랜만 선정, 수집 COMPLETED 시에만 재계산 시작 테스트.
  - V-API: local에서 `targetDate`를 지정한 수집 실행(Task 045) → 재계산이 자동으로 이어지고 수집 이력에 재계산 대상 플랜 수가 나온다. 실제 cron 경로 확인은 Task 102.

#### Task 070: 변경 내역 화면 실제 API 연결로 O1 완성 ⬜
- 태그: [F] · PRD: O1, SCR-11, SCR-16(FR-20) · 선행: 069, 061 · 브랜치: `feature/o1-plan-history-api`
- 구현 사항
  - [ ] SCR-11 변경 내역 목업 핸들러를 생성 훅으로 교체
  - [ ] SCR-16 수집 이력의 "재계산 대상 플랜 수" 자리(Task 061)를 Task 069이 추가한 필드에 연결
- 완료 기준
  - V-FS: 재계산이 일어난 계정에서 변경 내역이 API 값대로 보이고, 관리자 수집 화면의 재계산 대상 플랜 수가 API 값과 같다.

#### Task 071: 알림 설정·이력 화면과 관리자 알림 상태 목업 구현으로 알림 표현 확정 ⬜
- 태그: [F] · PRD: O2, FR-16, SCR-07(링크), 10(알림 켜기), 12, 16(R11-24) · 선행: 095, 037 · 브랜치: `feature/o2-notification-mock`
- 구현 사항
  - [ ] on/off, 수신 이메일, 발송 이력(유형, PENDING/SENT/FAILED, 시각), 설정·결과 화면의 링크, 관리자 수집 화면 알림 상태 요약
  - [ ] 계약 변경안 기록·승인
- 완료 기준
  - V-F: 목업 흐름 재현, 콘솔 에러 0건.

#### Task 072: 알림 outbox와 발송 기반 구현으로 신뢰성 있는 메일 발송 확보 ⬜
- 태그: [B] · PRD: O2, FR-15, 6절 신뢰성 · 선행: 095, 022 · 브랜치: `feature/o2-notification-outbox`
- 관련: R11-5, R11-11, TECH 5절
- 구현 사항
  - [ ] `backend/build.gradle`에 `spring-boot-starter-mail`, `spring-boot-starter-thymeleaf`, GreenMail(테스트) 추가(Task 016에서 옮김)
  - [ ] `infra/mail`: 발송 포트 + SMTP·HTTPS 메일 API 구현, `app.mail.provider`, SMTP 타임아웃 3종
  - [ ] ① 짧은 트랜잭션 임대(`@Lock(PESSIMISTIC_WRITE)` + lock timeout `-2`) ② 트랜잭션 밖 발송 ③ 결과 기록(백오프, FAILED, `app.notification.*`)
  - [ ] `@Scheduled` 1분 + ShedLock(`usingDbTime()`), 발송 직전 행 존재 재확인, Thymeleaf 템플릿("확인된 범위")
- 완료 기준
  - V-B(GreenMail + Testcontainers): 재시도·FAILED 전이, 동시 스케줄러 2개 중복 점유 없음, 로그의 실제 SQL이 `SKIP LOCKED`.

#### Task 073: 알림 생성 트리거와 설정·이력 API 구현으로 월초·변경 알림 제공 ⬜
- 태그: [B] · PRD: O2, FR-15, 16 · 선행: 071, 072, 069, 045 · 기한 전제: R11-17, R11-22(2026-11-05) · 브랜치: `feature/o2-notification-api`
- 구현 사항
  - [ ] 계약 추가(Task 071 변경안), 알림 설정·이력 API(`/api/me/...`, tag `notification`)
  - [ ] SUBSCRIBE_GUIDE: 저장 커밋 후 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`, ①·③ `REQUIRES_NEW`(R-6 결정이 "MVP 미생성"이면 여기서 저장 흐름에 추가)
  - [ ] MONTHLY 생성(`dedupe_key`)은 월초 재계산 Job 완료 뒤 백엔드가 이어서 실행한다(R-21 확정, 재계산 Job의 완료 리스너). 실패 복구용 관리자 수동 재실행 API는 제안. PLAN_CHANGE(이번 달 확정 플랜 작품이 빠질 때만), `notification_enabled = false`면 미생성
  - [ ] 월초 알림용 GitHub Actions 워크플로는 만들지 않는다(수집 cron 하나가 시작점)
- 완료 기준
  - V-B: `dedupe_key` 중복 차단, 알림 꺼짐 미생성, 월초 재계산 완료 뒤에만 MONTHLY 행 생성, 재실행해도 사용자당 1건.
  - V-API: 저장 → 테스트 계정 메일 수신. local에서 `targetDate`를 월 1일로 지정한 수집 → 재계산 → MONTHLY 생성이 이어지는 것을 확인. 실제 cron 경로 확인은 Task 102.

#### Task 074: 알림 화면 실제 API 연결로 O2 완성 ⬜
- 태그: [F] · PRD: O2, SCR-12, 16 · 선행: 073 · 브랜치: `feature/o2-notification-api-connect`
- 완료 기준
  - V-FS: 알림 끄기·켜기와 발송 이력이 API 값대로 보이고, 관리자 화면 알림 상태가 표시된다.

#### Task 075: 번들 관리·독점 목록 화면 목업 구현으로 데이터 확장 표현 확정 ⬜
- 태그: [F] · PRD: O3, FR-04, FR-19, SCR-03, SCR-14(번들) · 선행: 095, 037 · 브랜치: `feature/o3-bundle-exclusive-mock`
- 구현 사항
  - [ ] 독점 목록: 서비스 탭 7개, "확인된 범위"·산정 기준, 구조적 공백 제외 명시, 쿠팡플레이 데이터 부족 안내, 출처·기준일
  - [ ] 상품 화면 번들 구성 편집·tier, 요금 확인 화면 번들 표시. 계약 변경안 기록·승인
- 완료 기준
  - V-F: 탭 전환과 번들 편집 목업 흐름 재현, 콘솔 에러 0건.

#### Task 076: 번들·광고형 요금제 지원 구현으로 상품 선택지 확장 ⬜
- 태그: [B] · PRD: O3, FR-19(번들), R11-9 · 선행: 075, 059 · 브랜치: `feature/o3-bundle-ad-tier`
- 구현 사항
  - [ ] 계약 추가 + 관리자 BUNDLE 구성 편집(구성 서비스·tier), AD tier 단품
  - [ ] 엔진 입력: 번들은 상품 하나, AD tier는 `in_ad_tier`만 시청 가능으로 본다. 사용자 구독의 번들(같은 `product_id`)
  - [ ] 후보 조합 2^P 증가에 따른 그리디 전환 상한 재측정(R11-30)
- 완료 기준
  - V-B: 번들·광고형 정답 케이스와 상한 초과 GREEDY 전환 테스트가 통과한다.

#### Task 077: 계층별 갱신 구현으로 수집 호출량 절감 ⬜
- 태그: [B] · PRD: O3, FR-18(계층), 5.5 · 선행: 095, 056 · 브랜치: `feature/o3-tiered-refresh`
- 구현 사항
  - [ ] DAILY(찜에 있는 작품) 매일, WEEKLY(나머지) 주 1회로 Job 대상 분리(`tier` 파라미터)
  - [ ] 찜 추가·삭제 시 `refresh_tier` 갱신(Task 056에서 이관)
  - [ ] `collect.yml` cron을 tier별로 분리(실행 확인은 Task 102)
- 완료 기준
  - V-B: tier별 대상 선정과 찜 추가·삭제 시 `refresh_tier` 변경 테스트가 통과한다.

#### Task 078: 독점 목록 사전 계산과 공개 API 구현으로 서비스별 독점 조회 제공 ⬜
- 태그: [B] · PRD: O3, FR-04 · 선행: 075, 044, 049 · 브랜치: `feature/o3-exclusive-titles`
- 구현 사항
  - [ ] 계약 추가 + 수집 완료 후 "7개 중 이 서비스에서만 구독 제공"을 계산해 Redis `exclusiveTitles`에 저장(테이블 없음)
  - [ ] 구조적 공백 제외·명시, `INSUFFICIENT` 서비스는 안내 플래그, `/api/public/...`(요청 제한 적용)
- 완료 기준
  - V-B: 독점 판정·공백 제외·데이터 부족 테스트. V-API: 서비스별 목록.

#### Task 079: 번들·독점 화면 실제 API 연결로 O3 완성 ⬜
- 태그: [F] · PRD: O3, SCR-03, SCR-14 · 선행: 076, 078 · 브랜치: `feature/o3-bundle-exclusive-api`
- 완료 기준
  - V-FS: 탭 전환·쿠팡플레이 안내를 확인했고, 번들 등록 후 계산 결과에 번들이 상품 하나로 나온다.

#### Task 080: 탈퇴·데이터 정리 관련 결정으로 O4 구현 범위 확정 ⬜
- 태그: [H] · PRD: O4, FR-07, FR-18(6개월), TECH T-1 · 선행: 095 · 기한: 2026-11-05
- 구현 사항
  - [ ] R-10: `watch_unit` 6개월 삭제가 NO ACTION FK에 막히는 문제의 처리 방식. 막는 참조는 `wishlist_item`과 모든 상태(ACTIVE·DRAFT·ARCHIVED) 플랜의 `plan_item`이다(ERD. `plan_assignment`는 `plan_item`·`plan_month`를 참조하고 `watch_unit`을 직접 참조하지 않는다). 같은 A 영역의 `availability`·`availability_change`도 `watch_unit`을 참조하므로 정리 작업이 먼저 지운다
  - [ ] `BATCH_*` 이력 보존 기준(Supabase 500MB)
  - [ ] 6개월 갱신·삭제 정리 방식(점검 쿼리 주기, 삭제 대상, 실행 경로)
- 완료 기준
  - V-H: 세 결정을 기록하고 10.3 R-10을 갱신했다.

#### Task 081: 탈퇴 화면 목업 구현으로 탈퇴 안내 확정 ⬜
- 태그: [F] · PRD: O4, FR-07, SCR-13 · 선행: 095, 037 · 브랜치: `feature/o4-withdrawal-mock`
- 구현 사항
  - [ ] 삭제 데이터 안내(찜, 구독 상태, 가격 수정값, 플랜, 대기 알림), 확인 절차, 탈퇴 후 로그아웃. 계약 변경안 기록·승인
- 완료 기준
  - V-F: 목업 흐름 재현, 콘솔 에러 0건.

#### Task 082: 탈퇴 API 구현으로 사용자 데이터 삭제 제공 ⬜
- 태그: [B] · PRD: O4, FR-07 · 선행: 081, 072, 054 · 브랜치: `feature/o4-withdrawal`
- 구현 사항
  - [ ] 계약 추가 + 탈퇴 API(`/api/me`): `users` 행 삭제(DB cascade), Redis RT·사용자 캐시 별도 삭제, `calc_run.plan_id` NULL
  - [ ] PENDING 알림 미발송(Task 072의 발송 직전 재확인과 연동). Task 080 결정과 무관하게 진행 가능
- 완료 기준
  - V-B: 탈퇴 후 사용자 데이터 0행, `calc_run` 보존·`plan_id` NULL, 탈퇴·outbox 점유 경합 시 미발송.

#### Task 083: 6개월 갱신 점검과 데이터 정리 작업 구현으로 약관 보관 기한 준수 ⬜
- 태그: [B] · PRD: O4, FR-18, 6절 라이선스 · 선행: 080 · 브랜치: `feature/o4-data-retention`
- 구현 사항
  - [ ] `fetched_at` 점검 쿼리와 정리 작업(Task 080 결정 방식), ARCHIVED 참조 처리(R-10 결정)
  - [ ] `BATCH_*` 이력 정리(Task 080 보존 기준)
- 완료 기준
  - V-B: 기준을 넘긴 데이터만 정리되고, 찜(`wishlist_item`)과 ACTIVE·DRAFT 플랜의 `plan_item`이 참조하는 `watch_unit`은 삭제되지 않는(FK 위반 없음) 테스트가 통과한다.

#### Task 084: 탈퇴 화면 실제 API 연결로 O4 완성 ⬜
- 태그: [F] · PRD: O4, SCR-13 · 선행: 082 · 브랜치: `feature/o4-withdrawal-api`
- 완료 기준
  - V-FS: 테스트 계정 탈퇴 → 로그아웃·메모리 토큰 제거 → 재로그인 시 신규 계정(온보딩)으로 시작한다.

#### Task 085: Gemini API 무료 등급 키 발급으로 LLM 연동 준비 ⬜
- 태그: [H] · PRD: Q3, 6절 LLM · 선행: 없음 · 기한: 2026-11-19(3단계 착수 전)
- 구현 사항
  - [ ] 결제 수단 없는 무료 등급 키 발급, 무료 한도와 "입력이 제품 개선에 쓰임" 조건 확인, Cloudtype 환경 변수 등록
- 완료 기준
  - V-H: 발급일·보관 위치·결제 수단 미등록을 기록했다.

#### Task 086: LLM 검토 화면 목업 구현으로 관리자 검토 흐름 확정 ⬜
- 태그: [F] · PRD: Q3, SCR-18 · 선행: 095, 037 · 브랜치: `feature/q3-llm-review-mock`
- 구현 사항
  - [ ] 초안 목록, 확인·수정·반영, 관리자 가드. 계약 변경안 기록·승인(초안 저장 위치는 R-18)
- 완료 기준
  - V-F: 목업 흐름 재현, 콘솔 에러 0건.

#### Task 087: LLM 관리자 기능 구현으로 줄거리·변경 요약 초안 생성 ⬜
- 태그: [B] · PRD: Q3, 6절 LLM, R11-6 · 선행: 085, 086, 054 · 기한 전제: R-18 · 브랜치: `feature/q3-llm-admin`
- 구현 사항
  - [ ] 초안 저장(R-18): Redis(TTL)면 마이그레이션 없음. ERD 테이블 추가가 결정되면 사용자가 `docs/ERD.md`를 고치고 버전을 올린 뒤, 이 Task에서 새 Flyway 마이그레이션("현재 최대 번호 + 1", 4.2)과 엔티티를 추가한다
  - [ ] 계약 추가 + `infra/llm`(Spring AI 2.0 + Gemini), `XxxPromptService`로 프롬프트 분리. API(제안): 초안 생성 `POST /api/admin/llm/drafts`, 목록 `GET /api/admin/llm/drafts`, 반영 `POST /api/admin/llm/drafts/{draftId}/apply`
  - [ ] 대상: 빈 한국어 줄거리 번역 초안, 변경 이력(`availability_change`) 요약 초안. 관리자 API에서만 호출
  - [ ] 평가 세트(제안): `src/test/resources/llm/`의 고정 입력(영어 줄거리, 변경 이력 샘플)과 확인 항목(한국어 출력, 길이 상한, 원문에 없는 정보 추가 여부는 사람 검토)
  - [ ] 가드레일(제안): 프롬프트에 사용자 정보 필드를 넣지 않는 입력 DTO, 출력 언어·길이 검사 실패 시 초안 폐기, 관리자 반영 전 사용자 화면 미노출, 무료 한도 초과(429) 시 생성 중단과 안내
- 완료 기준
  - V-B: 외부 호출 모킹 단위 테스트, 가드레일·한도 초과 테스트. V-API: ADMIN만 호출된다.

#### Task 088: LLM 검토 화면 실제 API 연결로 Q3 완성 ⬜
- 태그: [F] · PRD: Q3, SCR-18 · 선행: 087 · 브랜치: `feature/q3-llm-review-api`
- 완료 기준
  - V-FS: 초안 수정·반영 후 작품 상세에 반영된 줄거리가 보인다.

#### Task 089: 데이터 정확도 안내 화면 구현으로 검증 결과 공개 ⬜
- 태그: [F] · PRD: Q4, FR-21, SCR-05, 8.2 · 선행: 037 · 기한 전제: R11-15(2026-11-19) · 브랜치: `feature/q4-accuracy-page`
- 구현 사항
  - [ ] 검증 방법, 서비스별 일치 표, 합산 30/33, 한계를 8.2와 같은 수치로. 정적 여부는 R11-15, Task 103 결과로 수치가 바뀌면 갱신
- 완료 기준
  - V-F: 수치가 PRD 8.2와 같고 콘솔 에러 0건.

#### Task 090: 반응형·접근성 점검과 보완으로 모바일·키보드 사용 보장 ⬜
- 태그: [F] · PRD: Q4, 6절 접근성 · 선행: 067 · 브랜치: `feature/q4-responsive-a11y`
- 구현 사항
  - [ ] SCR-01, 02, 07~10, 12 모바일 레이아웃, 키보드만으로 검색 → 찜 → 계산 → 저장, 시맨틱 태그·`alt`·포커스
- 완료 기준
  - V-F: 375×812에서 가로 스크롤 없음, 키보드 흐름 재현, 스냅샷 접근성 트리에 레이블이 있다.

#### Task 091: 오래된 DRAFT 판정 기준 개선으로 불필요한 409 감소 ⏸
- 태그: [B] · PRD: 5.4, TECH T-9(결정 필요) · 선행: 066 · 브랜치(결정 시): `feature/t9-draft-stale-check`
- 구현 사항(T-9가 채택될 때만)
  - [ ] DRAFT의 시청 단위 `availability_change` 또는 사용 상품 가격 변경이 `data_as_of` 이후에 있을 때만 409
  - [ ] 판정 쿼리 비용 측정, `availability_change` 보존 기간 확인(Task 083 정리 기준과 충돌 여부)
- 완료 기준
  - 보류. T-9 결정 전에는 착수하지 않는다.

---

## 9. Phase 5 — 최적화 및 배포 · 0/14

**목표.**
- MVP 배포(B8·F8)로 무료 기간을 끝내고 Hobby로 전환해 cron을 켠다. Task 092~095는 Phase 4보다 먼저 한다.
- 2·3단계 배포, 관측(Grafana Cloud), 성능 측정(k6), 마무리 문서를 완료한다.

#### Task 092: MVP 운영 인프라 전환과 도메인 연결로 공개 준비 ⬜
- 태그: [H] · PRD: B8·F8, R11-3, R11-4 · 선행: 005, 067 · 기한: 2026-11-04
- 구현 사항
  - [ ] Cloudtype Hobby(512MB당 월 6,600원, 메모리는 실측 후 결정) 전환, `ottnavi.shop`을 Vercel에 연결(HTTPS)
  - [ ] `APP_FRONTEND_ORIGIN`, `OAUTH2_REDIRECT_BASE_URL` 확정, Google OAuth 운영 리다이렉트 URI(`https://{도메인}/api/login/oauth2/code/google`)·승인된 출처 추가
  - [ ] 운영 환경 변수 최종 점검(Task 015 위치표, Task 020 등록분). 첫 배포 때 미룬 Google OAuth·TMDB·메일 값을 채운다
- 완료 기준
  - V-H: 전환일, 도메인 연결 결과, 등록 URI를 기록했다.

#### Task 093: 백엔드 운영 설정 점검과 수집 cron 활성화로 자동 운영 준비 ⬜
- 태그: [B] · PRD: B8, FR-18, R11-4 · 선행: 092, 047 · 브랜치: `feature/b8-prod-config`
- 구현 사항
  - [ ] prod 프로필 최종 점검(`ddl-auto=validate`, 풀 약 5, `forward-headers-strategy=framework`, 오리진 비밀 헤더 검사 켬)
  - [ ] `collect.yml`의 `schedule` 활성화(Cloudtype 직접 호출 + 두 헤더). 매일 수집이 Supabase 7일 비활성 일시정지도 막는다
- 완료 기준
  - PR 수준: YAML 문법 유효, prod 프로필로 로컬 기동(환경 변수는 로컬 값) 성공. `main` 반영 후 cron·배포 확인은 Task 095.

#### Task 094: 프론트 운영 배포 설정으로 실제 API 연결 배포 준비 ⬜
- 태그: [F] · PRD: F8, T-8 · 선행: 092 · 브랜치: `feature/f8-deploy`
- 구현 사항
  - [ ] `vercel.json` 운영 백엔드 주소, `/api` 우선 순서, `routes[].transforms` 헤더 주입, rewrite 캐시 비활성화(`respectOriginCacheControl: false`, 또는 Task 021 기록에서 효과가 확인된 방식)를 Task 021 기록 기준으로 점검
  - [ ] `VITE_USE_MOCK=false`, 운영 빌드에 MSW 미포함 확인
- 완료 기준
  - `develop` 미리보기 배포에서 `/api/public/ott-services` 응답이 V-DEPLOY "헤더·경로"를 만족한다(Preview 환경 `ORIGIN_SECRET`, Task 015).
  - 운영 빌드 산출물(`frontend/dist/`)에 MSW 워커·핸들러가 포함되지 않음을 확인했다. 화면 확인은 운영 배포 후 Task 095에서 한다.

#### Task 095: MVP 배포 스모크 테스트와 v0.1.0 태그로 MVP 공개 ⬜
- 태그: [H] · PRD: B8·F8, MVP 완료 기준 ①~④ · 선행: 093, 094, 052, 061, 065, 067, 037
- 구현 사항(이 순서로 진행)
  - [ ] ① `develop` → `main` PR merge commit, `backend-deploy.yml`·Vercel Production 성공 확인(Task 093 이관분). 병합 전 `openapi.yaml`에 `x-planned`가 남아 있지 않음을 확인(4.2)
  - [ ] ② 운영 ADMIN 지정(R-5 확정, 2026-10-09): 운영 관리자는 **2명**(본인 계정과 포트폴리오 평가용 계정)이고 **DB에서 `users.role`을 직접 수정**한다. 순서: 그 사람이 Google로 **한 번 로그인**해 `users` 행이 생기게 한 뒤 `UPDATE users SET role = 'ADMIN' WHERE email = '해당 이메일'`을 실행하고(README에 이 SQL을 기록) 다시 로그인한다. 주의: 평가용 계정에 ADMIN을 주면 상품 가격 수정·수집 실행·제공처 매핑 변경으로 운영 데이터를 바꿀 수 있으니 평가 전에 가격표(Task 004)를 백업해 두고 확인한다. 평가자의 Google 이메일이 `users`에 저장되므로 개인정보 안내(About)와 탈퇴 정책과 일관되게 처리한다. 읽기 전용 관리자 역할은 지금 만들지 않고, 필요해지면 `role` 값을 추가한다
  - [ ] ③ 관리자 화면(SCR-14)으로 운영 상품·가격 입력(Task 004 가격표)
  - [ ] ④ 첫 운영 수집 적재(R-14 확정): `collect.yml` `workflow_dispatch` 202·재실행 409(Task 047 이관분), SCR-16에서 완료·처리 건수 확인. 다음 날 cron 실행 기록 확인
  - [ ] ⑤ 스모크: V-DEPLOY 전체(인증, 기능, 헤더·경로)와 요청 제한 429, 주요 화면 로드(Task 094에서 넘긴 화면 확인)
  - [ ] MVP 완료 기준 ①~④ 체크, `main`에 `v0.1.0` 태그
- 완료 기준
  - V-H: 스모크 항목별 결과와 날짜를 기록했다. 이 Task 완료가 **MVP 완료 지점**이다.

#### Task 096: Grafana Cloud 계정 생성으로 관측 연동 준비 ⬜
- 태그: [H] · PRD: Q1, 6절 관측 · 선행: 없음 · 기한: 2026-11-19(3단계 착수 전)
- 구현 사항
  - [ ] 무료 플랜 계정·스택 생성, 한도(메트릭·로그) 확인, OTLP·Loki 접속 정보를 Cloudtype 환경 변수로 등록
- 완료 기준
  - V-H: 생성일, 무료 한도, 보관 위치(값 제외)를 기록했다.

#### Task 097: 관리자 모니터링 화면 목업 구현으로 표시 범위 확정 ⬜
- 태그: [F] · PRD: Q1, FR-20, SCR-17 · 선행: 095, 037 · 기한 전제: R11-16(2026-11-19) · 브랜치: `feature/q1-monitoring-mock`
- 구현 사항
  - [ ] R11-16 결정 범위(추천안: Grafana Cloud 링크와 수집 실행 요약)로 목업. 새 API가 필요하면 계약 변경안 기록·승인
- 완료 기준
  - V-F: 목업 흐름 재현, 콘솔 에러 0건.

#### Task 098: Grafana Cloud 관측 연동으로 배포 환경 지표·로그 수집 ⬜
- 태그: [B] · PRD: Q1, FR-20, 6절 관측 · 선행: 096, 097 · 브랜치: `feature/q1-observability`
- 관련: TECH 6절(Loki4j·Logback 버전 미대조)
- 구현 사항
  - [ ] Micrometer OTLP 메트릭, Micrometer Tracing, Loki4j(도입 전 Boot 관리 Logback 버전 대조)
  - [ ] 대시보드: 요청, `plan.calculate`, `tmdb.call`, `notification.send`, 연결 풀. 무료 한도 안 전송량
  - [ ] Task 097 변경안에 API가 있으면 계약 추가와 구현
- 완료 기준
  - 로컬에서 OTLP 전송 설정이 오류 없이 기동한다. 배포 환경 대시보드 확인은 Task 105.

#### Task 099: 관리자 모니터링 화면 실제 연결로 Q1 완성 ⬜
- 태그: [F] · PRD: Q1, SCR-17 · 선행: 098 · 브랜치: `feature/q1-monitoring-api`
- 완료 기준
  - V-FS: 관리자 계정으로 화면이 로드되고 요약 값이 API와 같다.

#### Task 100: k6 성능 측정과 개선으로 응답 시간 목표 근거 확보 ⬜
- 태그: [B] · PRD: Q2, 6절 성능, R11-29, R11-30 · 선행: 095 · 브랜치: `feature/q2-k6-performance`
- 구현 사항
  - [ ] 측정용 백엔드 컨테이너를 `cpus`·`mem_limit` 고정으로 추가(제안: `infra/docker-compose.yml`의 `profiles: [perf]` 또는 별도 compose 파일)
  - [ ] `infra/k6/` 스크립트(플랜 계산, 검색, 상세), 전후 각 3회 중앙값, 최악 입력(예산·찜 상한) 탐색 시간
  - [ ] 개선(캐시, 쿼리, 인덱스) 후 재측정. 배포 환경은 스모크만
  - [ ] **메모리 최종 판정**: 측정용 컨테이너에 `mem_limit: 512m`을 걸고 계산·수집·조회 부하에서 OOM 없이 도는지 확인해 구독 메모리 단위(512MB당 월 6,600원)를 판단한다(4.6)
- 완료 기준
  - 전후 비교표를 기록했고 R11-29·30 확정안을 사용자에게 올렸다.

#### Task 101: 프론트 번들과 큰 목록 렌더링 점검으로 화면 성능 확보 ⬜
- 태그: [F] · PRD: Q2 · 선행: 095 · 브랜치: `feature/q2-frontend-performance`
- 구현 사항
  - [ ] 빌드 결과물 크기, 라우트 지연 로딩, 큰 찜 목록·검색 결과 렌더링(필요 시 가상화), 필요한 곳만 memo
- 완료 기준
  - 전후 번들 크기를 기록했고, V-F: 찜 상한 개수 목업으로 찜 목록이 콘솔 에러 없이 스크롤된다.

#### Task 102: 2단계 배포 스모크와 v0.2.0 태그로 운영 기능 공개 ⬜
- 태그: [H] · PRD: O1~O4 완료 · 선행: 068~084
- 구현 사항
  - [ ] `develop` → `main` merge commit, V-DEPLOY
  - [ ] cron 확인(이관분): tier별 두 수집 cron(Task 077)의 실행 기록, 수집 완료 뒤 백엔드가 이어서 실행한 재계산(Task 069, SCR-16 재계산 대상 플랜 수)과 월초 알림 생성(Task 073)의 기록
  - [ ] 2단계 확인: 월초 알림 수신, 변경 내역, 독점 목록, 번들 계산, 탈퇴. 운영비 청구서(저예산 유지 확인)
  - [ ] `v0.2.0` 태그
- 완료 기준
  - V-H: 항목별 결과를 기록했다.

#### Task 103: 재현율과 추가 표본 검증으로 정확도 수치 보강 ⬜
- 태그: [H] · PRD: Q4, R11-31, R11-32, FR-21 · 선행: 048
- 구현 사항
  - [ ] 재현율 표본(서비스당 10건 내외), 쿠팡플레이(중복 제외 2건)·웨이브·왓챠 최근 작품 추가 확인
  - [ ] PRD 8.2 갱신 제안으로 정리(PRD 수정은 사용자 결정)
- 완료 기준
  - V-H: 표본 목록, 확인일, 결과를 기록했다.

#### Task 104: README·포트폴리오 문서·회고 작성으로 프로젝트 정리 ⬜
- 태그: [H] · PRD: Q4 · 선행: 100, 103
- 구현 사항
  - [ ] 루트 `README.md`(PowerShell 기준 실행, 구조, 배포), 포트폴리오 문서(정확해·그리디 비교 `calc_run`, k6 전후, 데이터 정확도), 회고
- 완료 기준
  - V-H: 문서 경로·작성일을 기록했고 README 절차대로 로컬 기동이 재현된다.

#### Task 105: 3단계 배포 스모크와 v0.3.0 태그로 품질 단계 완료 ⬜
- 태그: [H] · PRD: Q1~Q4 완료 · 선행: 085~090, 096~101, 102, 103, 104
- 구현 사항
  - [ ] `develop` → `main` merge commit, V-DEPLOY
  - [ ] 배포 환경 Grafana Cloud 대시보드 지표·로그(Task 098 이관분), LLM 검토, 정확도 안내, 모바일 화면 확인
  - [ ] `v0.3.0` 태그
- 완료 기준
  - V-H: 항목별 결과(대시보드 스크린샷 포함)를 기록했다.

---

## 10. 리스크 및 확인 필요 사항

기한은 3.2의 가정 일정 기준이다. 결론은 내리지 않았고, 사용자가 정하지 않은 권고는 "추천안(미확정)"으로 적었다. 결정되면 해당 행에 결과와 날짜를 적는다.

### 10.1 일찍 해야 하는 결정

| 항목 | 내용 | 기한 | 막히는 Task |
|---|---|---|---|
| R11-13 | 쿠팡플레이를 계산 입력에 포함할지(입력 단계 필터, TECH 1절) | **2026-10-29** (계산 입력 전) | 063 |
| R-6 | MVP 저장 시 SUBSCRIBE_GUIDE outbox 행 생성 여부 | **2026-10-29** | 066, 073 |

해소되어 이 표에서 뺀 항목: R-12(Cloudtype 배포 방식: 저장소 연결 + 콘솔 수동 배포, Task 020 기록), R11-12(비로그인 요청 한도: 일반 IP당 분당 60회·단건 수집 검색 IP당 분당 10회, 2026-10-09 사용자 결정), R11-30의 찜 상한 잠정값(30개, 2026-10-09 사용자 결정. 나머지 상한은 측정 후, 10.2), R-16(MVP 일괄 수집의 `tier`는 새 값 `ALL`, TECH 3절 설명 참고, 2026-10-09), R11-21(Refresh Token은 Redis TTL에 해시로 저장·회전·재사용 감지, 2026-10-09), R-5(운영 ADMIN은 가입 후 DB에서 `users.role` 직접 수정, 2026-10-09), R-14(첫 수집은 로컬, 운영 적재는 Task 095), R-15(일회성 Cloudtype 서비스로 SMTP 시험), R-20(`x-planned` 방식), R-21(수집 → 재계산 → 월초 알림을 백엔드에서 연결), R11-27(7개 서비스 해지 예약 정책, Task 003에서 확정), R11-11(Cloudtype SMTP 발신 가능 여부, 2026-10-09 Task 022 시험 성공으로 SMTP(Gmail) 채택 확정), R-17(Vercel `vercel.json` 미리보기 배포 성공, Task 014에서 확인). 10.3 참고.

### 10.2 PRD 11절 결정 필요·작업 중·보류 항목

PRD 11절 확정 항목 4번(무료 기간 수집 방식)과 5번(Actions 트리거 범위), FR-18의 "무료 기간에는 수동 실행" 문구는 R-14·R-21 결정에 맞춰 다른 작업자가 갱신 중이다(이 표의 대상은 아니다).

| 번호 | 항목 | 분류 | 결정 시점(제안) | 관련 Task |
|---|---|---|---|---|
| R11-13 | 쿠팡플레이 계산 포함 여부 | 결정 필요 | 2026-10-29 | 063 |
| R11-14 | SCR-06·07 화면 통합(18개 화면) | 결정 필요 | 2026-10-14 (F1 승인) | 030 |
| R11-15 | SCR-05 정적 콘텐츠 여부 | 결정 필요 | 2026-11-19 | 089 |
| R11-16 | SCR-17 표시 항목 | 결정 필요 | 2026-11-19 | 097, 099 |
| R11-17 | 가입 안내 1회 판정 조건·저장 방식 | 결정 필요 | 2026-11-05 (O2 전) | 073 |
| R11-18 | "주 N회 × 회당 M시간" 환산 입력 저장(ERD 필드 없음) | 결정 필요 | 2026-10-14 (F1 승인) | 030, 032, 055, 058 |
| R11-19 | 드라마 찜 단위(시즌 vs 작품) | 결정 필요 | 2026-10-14 (F1 승인) | 030, 032, 056 |
| R11-20 | 시즌 0 미수집의 화면 안내 | 결정 필요 | 2026-10-14 (F1 승인) | 030 |
| R11-22 | FR-15에 SUBSCRIBE_GUIDE 포함 | 결정 필요 | 2026-11-05 | 073 |
| R11-23 | 제공처 매핑 관리 별도 FR 없음(FR-18 흡수) | 결정 필요 | 2026-10-22 (F6 전) | 046, 061 |
| R11-24 | SCR-16 알림 상태는 2단계부터(재계산 대상 플랜 수도 2단계 Task 069·070에서 채움) | 결정 필요 | 2026-10-22 (F6 전) | 061, 070, 071, 074 |
| R11-25 | 러닝타임 기본값 실측 보정 | 작업 중 | 첫 전체 수집 후(2026-10-21) | 042, 048 |
| R11-26 | TV 시즌 호출량과 배치 분할 | 작업 중 | 첫 전체 수집 후(2026-10-21) | 043, 044, 048 |
| R11-28 | QueryDSL 도입 시 Boot 4.1·Hibernate 7 호환 버전 | 보류 | 도입 시점 | — |
| R11-29 | 계산·공개 조회 응답 시간 목표 | 보류 | k6 측정 후 | 100 |
| R11-30 | 예산·찜 상한, 그리디 전환 후보 수 상한 | 보류(찜 상한 잠정값 30개는 2026-10-09 확정, 나머지는 측정 후) | 엔진·k6 측정 후 | 039, 040, 056, 076, 100 |
| R11-31 | 재현율 측정 | 보류 | 3단계 | 103 |
| R11-32 | 쿠팡플레이·웨이브·왓챠 표본 추가 | 보류 | 3단계 | 103 |
| T-9 | 오래된 DRAFT 판정 기준 개선 | 결정 필요(2단계 후보) | 2026-11-05 | 091 |
| 3절 | 정량 성공 지표 후보의 채택 여부·수치 | 사용자 결정 | MVP 배포 전 | 040, 065, 100 |

### 10.3 문서 간 불일치와 이 로드맵이 찾은 확인 필요 사항

| ID | 내용 | 상태 | 근거 | 기한 |
|---|---|---|---|---|
| R-1 | 1주차 얇은 배포와 브랜치 규칙 충돌 | **해소(2026-10-03)**: `develop` 생성 후 feature → `develop` → `main` 병합을 첫 배포로 함(git.md 갱신). Task 021 | git.md | — |
| R-2 | backend.md가 RT Redis 저장·Gmail SMTP를 확정처럼 적음 | **해소(2026-10-03)**: backend.md가 "잠정 기준, 확정은 R11-21·R11-11"로 갱신됨 | backend.md | — |
| R-3 | 오리진 비밀 헤더 주입 방법 | **해소(2026-10-03)**: `vercel.json` `routes[].transforms`(TECH T-8 ①). 적용 순서 실측은 Task 021 `기록:` | TECH T-8 | — |
| R-4 | cron이 백엔드에 닿는 경로 | **해소(2026-10-03)**: Cloudtype 직접 호출 + `x-origin-secret`·`ADMIN_BATCH_TOKEN`(TECH 3절). Task 047 | TECH 3절 | — |
| R-5 | 운영 ADMIN 역할 지정 방법이 문서에 없다. 추천안(미확정): 관리자 1명이므로 첫 로그인 후 DB에서 `users.role`을 직접 수정하고 SQL을 README에 기록. 대안: 환경 변수 관리자 이메일 목록으로 로그인 시 부여. 로컬·테스트는 DB 직접 수정으로 진행(Task 054). Task 095 ②단계에서 적용 | **해소(2026-10-09, 사용자 결정)**: DB 직접 수정으로 확정(운영 관리자는 2명: 본인 계정과 포트폴리오 평가용 계정). 환경 변수 이메일 목록은 만들지 않는다. 절차와 주의는 Task 095 ② | FR-17, ERD `users.role` | 2026-11-04 (Task 095 전) |
| R-6 | MVP 저장 시 SUBSCRIBE_GUIDE outbox 행 생성 여부(TECH 2절 4번 vs PRD 9절 알림 미룸). 추천안(미확정): MVP에서는 만들지 않고 Task 073에서 추가. 발송기 없이 쌓인 행이 2단계 배포 때 한꺼번에 나가는 것을 막기 위함 | 미결 | TECH 2절 vs PRD 9절 | 2026-10-29 |
| R-7 | api-contract.md `tags`에 인증·알림 태그 없음 | **해소**: `auth`, `notification` 추가됨 | api-contract.md | — |
| R-8 | 인증 경로가 backend.md 3분류에 걸림 | **해소**: backend.md·api-contract.md가 인증 경로를 3분류의 예외로 명시 | backend.md, api-contract.md | — |
| R-9 | 관리자 가격 겹침, 찜 상한 초과 등 TECH·rules에 없는 오류 코드가 필요. `error-codes.md`에 제안 등록 후 사용자 승인 | 미결 | FR-19, R11-30 | 해당 Task |
| R-10 | `watch_unit` 6개월 삭제가 NO ACTION FK에 막힘. 막는 참조는 `wishlist_item`과 ACTIVE·DRAFT·ARCHIVED 플랜의 `plan_item`이다(`plan_assignment`는 `watch_unit`을 직접 참조하지 않음. 2판의 "ARCHIVED의 `plan_item`·`plan_assignment`" 서술을 ERD에 맞게 바로잡음). 추천안(미확정): 찜이나 ACTIVE·DRAFT가 참조하는 `watch_unit`은 삭제하지 않고 재수집으로 갱신하고, ARCHIVED만 참조하면서 TMDB에서 사라져 갱신할 수 없는 경우에만 해당 ARCHIVED 플랜을 먼저 삭제 | 미결(Task 080) | TECH T-1, ERD FK 삭제 동작 | 2026-11-05 |
| R-11 | 초기 `main` 직접 커밋 범위. git.md는 "폴더 골격, 문서, CI 골격"까지 허용. 최소 빌드 프로젝트(006·007)와 미커밋 문서 변경을 초기 세팅에 포함한다고 가정 | 가정 | git.md | 2026-10-05 |
| R-12 | Cloudtype 배포 방식과 토큰 이름이 문서에 없다. 3판에서 Cloudtype 서비스 생성·환경 변수·배포 토큰을 Task 015에서 떼어 첫 배포 직전 Task 020으로 옮겼다(준비 구간 015는 GitHub·Vercel·로컬만) | **해소(2026-10-06, 사용자 결정)**: 저장소 연결 + Cloudtype 콘솔 수동 배포. GitHub Actions 배포와 Cloudtype 토큰은 당분간 쓰지 않는다(Task 020 `기록:`) | git.md | 2026-10-12 |
| R-14 | 첫 전체 수집(Task 048) 실행 환경 | **해소(2026-10-03, 사용자 결정)**: 첫 전체 수집·성능 실측은 로컬 ①, 운영 DB 적재는 MVP 배포 Task 095. MVP 배포 전 Cloudtype에는 수집 코드와 cron이 없다. PRD 11절 4번·FR-18·TECH 3절 문구는 다른 작업자가 이에 맞춰 갱신 중 | git.md, PRD 9절·11절 4번, TECH 3절 | — |
| R-15 | SMTP 시험(Task 022)의 Cloudtype 배포 방법 | **해소(2026-10-03, 사용자 결정)**: 병합하지 않는 시험 브랜치를 일회성 Cloudtype 서비스로 배포해 시험하고 삭제. 무료 플랜 서비스 추가 가능 여부는 Task 003에서 먼저 확인. Task 022 선행에 003·006·016 추가 | git.md, R11-11 | — |
| R-16 | MVP 일괄 수집의 `tier` 값. TECH 3절은 식별 파라미터를 `tier`(DAILY/WEEKLY)로 정했고 PRD 5.5는 "MVP는 일괄 갱신"이라고만 한다. 일괄 실행에 어떤 값을 쓸지(예: WEEKLY로 전체, 별도 값 추가) 근거가 없다 | **해소(2026-10-09, 사용자 결정)**: 새 값 **`ALL`**을 추가한다(`DAILY`·`WEEKLY`로 표기하면 이름과 실제가 달라 헷갈림). `tier`의 뜻은 TECH 3절에 기록. 계약(Task 045)의 `tier` enum에 `ALL`을 넣고, Job 파라미터 `tier`와 `title.refresh_tier`가 같은 enum을 공유하는지는 Task 043에서 정한다 | PRD 5.5, TECH 3절 | 2026-10-15 |
| R-17 | Vercel `routes`와 `rewrites`(·`headers`) 동시 사용. TECH는 함께 쓸 수 있다고 적었다. context7(`/vercel/vercel`, `@vercel/routing-utils` `getTransformedRoutes`)로 두 값을 함께 받으면 `routes`가 먼저, `rewrites`가 `handle: filesystem` 뒤에 붙는다는 코드는 확인했지만, 배포 검증이 혼용을 거부하는지는 확인하지 못했다(과거 Vercel은 거부한 것으로 알려짐). 3판에서 실측을 Task 014(미리보기 배포)로 당겼다. **2026-10-04 갱신(Task 009 작업 중 조사)**: Vercel CLI 소스(`compile-vercel-config.ts`)가 `routes`와 `rewrites`·`redirects`·`headers`를 함께 정의하면 합치지 않고 스키마 검증이 실패하도록 두고, `@vercel/config` 소스 주석에도 "Vercel doesn't allow mixing routes with redirects, rewrites, headers..."라고 적혀 있어 혼용은 거부될 가능성이 높다고 판단했다(배포 실측은 아님). 그래서 Task 009를 처음부터 `routes` 단일 형식으로 작성했고 `routesSchema` 검증을 통과했다. Task 014는 이 파일의 미리보기 배포 성공만 확인한다. 적용 순서와 프록시·헤더 주입의 실제 동작은 Task 021 `기록:` **2026-10-05 실측(Task 014)**: 미리보기 배포(브랜치 `feature/docs-human-tasks-status`, 커밋 `adba3e1`)가 `routes` 단일 형식 `vercel.json`으로 설정 오류 없이 만들어졌고(`Build Completed`, `Deployment completed`), `ORIGIN_SECRET` 환경 변수가 아직 없어도 배포가 실패하지 않았다. 루트 페이지가 열리고 `/api/public/ott-services`는 SPA 화면이 아니라 `502 DNS_HOSTNAME_NOT_FOUND`(자리표시 호스트로 프록시 시도)가 나와 `/api` 프록시 규칙이 SPA 대체보다 먼저 적용되는 것을 확인했다. 혼용 여부는 시험하지 않았다. 프록시·헤더 주입의 실제 동작은 Task 021 | 확인됨(Task 014, 2026-10-05) | TECH 6절·T-8 | 2026-10-07 |
| R-18 | LLM 초안 저장 위치. ERD(24개 테이블)에 초안 테이블이 없다. Redis(TTL) 또는 ERD 추가 중 선택 필요. ERD 추가로 정하면 ERD 수정과 Flyway 마이그레이션이 Task 087 범위에 들어간다 | 미결 | PRD SCR-18, ERD | 2026-11-19 |
| R-19 | 제공처 변경 시 "영향받는 ACTIVE 플랜" 선정 기준이 문서에 없다. Task 069에 제안(이번 수집의 `availability_change` × `plan_item`)을 적었다. 사용자 승인 필요 | 미결 | FR-18, TECH 2절 | 2026-11-05 |
| R-20 | MVP 계약 초안의 보관 방식 | **해소(2026-10-03, 사용자 결정)**: 별도 초안 파일 없이 `docs/api/openapi.yaml` 하나에 두고, 구현 전 operation에 `x-planned: true`("제안" 표시)를 단다. 계약 대조 테스트(Task 023)는 이 경로를 제외하고, 구현 Task가 표시를 지운다. orval 입력은 `openapi.yaml` 하나(Task 017). 4.2, Task 023·031 반영. `api-contract.md`는 다른 작업자가 갱신 중. 남은 판단: 2단계 이후 기능에도 `x-planned`를 쓸지(현재 4.4는 PRD 9절대로 구현 PR에서 추가) | PRD 9절, TECH T-5, api-contract.md | — |
| R-21 | 수집 → 재계산 → 월초 알림 생성의 연결 주체. 2판은 `collect.yml`이 수집 뒤 재계산을, 별도 cron이 월초 알림 생성을 호출했다 | **해소(2026-10-03, 사용자 결정)**: 백엔드가 수집 Job 완료 리스너(Spring Batch `JobExecutionListener`)에서 재계산을 이어 실행하고, 월초 알림 생성은 재계산 완료 뒤 이어 실행한다. GitHub Actions는 수집 시작만 호출(202). Task 047·069·073·102 반영. PRD 11절 5번·TECH 3·4절(배치 토큰 경로) 문구는 다른 작업자가 갱신 중 | PRD 11절 5번, TECH 3절 | — |

### 10.4 일정·운영 가정

- 3.2의 날짜는 PRD 주차를 2026-10-05 착수로 대응시킨 **가정**이다. PRD는 주차만 정했다.
- MVP 배포 전 Cloudtype에는 첫 배포(Task 021)의 서비스 목록 API만 있고 수집 코드와 cron은 없다(R-14 확정). 무료 기간 서버 수동 기동과 Supabase 일시정지 재개는 배포 확인이 필요할 때만 한다.
- 수집이 매일 돌기 시작하면(Task 095) 대부분의 DRAFT가 다음 날 409가 되므로 프론트 자동 재계산(Task 067)이 필수다.
- 배포 환경은 스모크만 하고 성능 수치는 로컬 k6로만 낸다(6절).

---

## 11. 변경 이력

| 날짜 | 변경 요약 |
|---|---|
| 2026-10-03 | 초판 작성. PRD v0.3·TECH(T-1~T-9)·ERD v0.3·rules 기준으로 5단계 Phase, 87개 Task, PRD 단계 매핑, MVP 완료 지점(Task 079), 리스크·확인 필요 사항 정리. Task 001(H1)만 완료 표시 |
| 2026-10-03 | 2판. roadmap-reviewer 지적과 사용자 결정 반영으로 104개 Task로 재번호. 얇은 배포를 019(로컬 구현)·020(첫 배포)로 분리, 순환 의존 해소(cron·배포 확인을 병합 Task로 이관), 계산 API를 061~064로 분리(T-4: 비교 기록은 측정 경로 064에서만), 마이그레이션 A·B·C·D·E를 목업 승인(029) 뒤로 이동하고 ERD 보정 Task 031 신설, 2단계 이후 기능을 "목업 → 계약·구현 → 교체" 순서로 재배치, 탈퇴 결정 Task 079 분리, 4.1 개발 환경·4.3 플러그인 규칙(설치 완료, Task 013 ✅)·Flyway 번호 규칙 추가, R-1·R-2·R-3·R-4·R-7·R-8 해소, R-14~R-20 신규. MVP 완료 지점은 Task 094 |
| 2026-10-03 | 3판. roadmap-reviewer 재평가와 사용자 결정 반영. Cloudtype 서비스 생성·환경 변수·배포 토큰을 Task 015에서 떼어 Task 020(신설, [H])으로 분리하면서 2판의 Task 020~104를 021~105로 한 칸씩 재번호(105개 Task). 이 행 아래의 번호는 3판 기준: R-20 해소(`openapi.yaml` 하나 + `x-planned: true`, 계약 대조 테스트 023 제외 규칙, 031·017·4.2), R-21 신규·해소(수집 Job 완료 리스너 → 재계산 069 → 월초 알림 073, Actions는 수집 시작만, 047·102), R-14 해소(로컬 첫 수집 048, 운영 적재 095), R-15 해소(일회성 Cloudtype 서비스, 003에서 서비스 추가 가능 여부 확인, 022 선행 003·006·016 추가), MVP 게이트 095 선행에 061·037 추가와 "ADMIN 지정 → 상품·가격 입력 → 첫 운영 수집 → 스모크" 순서, R-5 기한을 095 전으로, local 프로필 전용 `targetDate` 지정(045, 067 재현), R-17 실측을 014로 당김(거부 시 009 수정), 002↔022 순환 해소(HTTPS 메일 API 키는 022 실패 분기), FR-20 변경된 작품·재계산 대상 플랜 수를 045·061·069·070에 연결, 094 완료 기준 축소(화면 확인은 095), 010·012 frontend-ci `test` no-op 명시, [H] 기록·문서 수정의 `feature/` PR 규칙, 033 선행 032 추가와 V1 변경 시 새 버전 규칙, R-10 FK 서술 정정(`wishlist_item`·모든 상태의 `plan_item`), 087 R-18 ERD·마이그레이션, 105 선행 102 추가, 메일·Thymeleaf·GreenMail 의존성을 016에서 072로 이동, Task 013 기록의 존재하지 않는 커밋 해시 삭제 |
| 2026-10-08 | 3판 이후 진행 메모. Task 018·022·024·025~031에 사용자 결정(경로표 확정, 025·026 PR 합침, sonner 토스트, 목업 로그인 전환 토글, 대체 이미지 기본, 임시 타입 예외와 `mockTypes.ts` 제거 조건)과 안내서·계획 문서(`TASK022_GUIDE.md`, `TASK024_GUIDE.md`, `TASK018_031_PLAN.md`) 링크를 반영하고 3.2에 일정 위험을 적었다. 에이전트 `frontend-dev`·`ui-designer` 추가. Task 번호·상태·선행은 바꾸지 않았다 |
