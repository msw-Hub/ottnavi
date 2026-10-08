# Task 018·025~031 작업 계획 (프론트 화면 골격 → 목업 → 계약 초안)

> 2026-10-08 작성. 기준 문서는 `docs/ROADMAP.md` 3.2(착수 순서)·4.2(공통 규칙)·4.5(검증 절차)·5~6절 각 Task, `docs/PRD.md` 6절(화면)·9절(UI 선행), `.claude/rules/frontend.md`·`api-contract.md`·`git.md`.
> 이 문서는 **계획**이다. 각 Task 착수 전에 해당 Task의 세부 설계를 다시 제시하고 승인받은 뒤 구현한다(전역 규칙: 탐색 → 계획 → 승인 → 구현).
> "(제안)"은 문서에 정해지지 않아 이 계획이 제안하는 값이다. 계약(`openapi.yaml`)에 들어가는 Task 031에서 고정한다.

## 1. 범위와 전제

| Task | 내용 | 태그 | 선행 | 담당 |
|---|---|---|---|---|
| **018** | 라우트·레이아웃·라우트 가드 골격 | [F] | 017 ✅ | Claude |
| 025 | 공통 유틸·에러 인터셉터 | [F] | 018 | Claude |
| 026 | 공통 표시 컴포넌트 1차 | [F] | 025 | Claude |
| 027 | 공개 화면 목업(SCR-01·02·04·06) | [F] | 018, 026 | Claude |
| 028 | 회원 화면 목업(SCR-07~10) | [F] | 027 | Claude |
| 029 | 관리자 화면 목업(SCR-14~16) | [F] | 027 | Claude |
| 030 | 목업 승인과 화면 결정(R11-14·18·19·20) | [H] | 027~029 | **사용자** (기한 10-14) |
| 031 | OpenAPI 초안·orval 생성물 연결 | [F][B] | 030, 023 ✅ | Claude 작성, 사용자 승인 |

- **Task 018을 계획에 넣은 이유**: 요청 범위는 025~031이지만 025·027의 선행인 018이 아직 ⬜이다. 018 없이는 025부터 시작할 수 없다.
- 같은 기간에 사용자가 할 일: Task 022(SMTP 시험, 기한 10-14, `docs/TASK022_GUIDE.md`), Task 024(Redis·Feign 설정, `docs/TASK024_GUIDE.md`), Task 038(계산 엔진 착수, 3.2 1주차). 프론트 작업과 파일이 겹치지 않아 병행할 수 있다. 겹치는 곳은 Task 031의 `docs/api/openapi.yaml`·백엔드 계약 대조 테스트뿐이다.

## 2. 순서와 일정 (가정)

```
10-08  [준비] 프론트 에이전트 + 이 문서 PR ─┐
10-08~09  018 라우트·레이아웃 ───────────────┤ (PR 병합 후 다음 브랜치)
10-09     025 공통 유틸·인터셉터 ────────────┤
10-10     026 공통 컴포넌트 ─────────────────┤
10-10~11  027 공개 화면 목업 ────────────────┤
10-12     028 회원 화면 목업 ─┐ (둘 다 027 이후, 브랜치는 각각 develop에서)
10-13     029 관리자 화면 목업 ┘
10-13~14  030 [H] 사용자 목업 검토·결정 (기한 10-14)
10-15~16  031 OpenAPI 초안 → 사용자 계약 승인 → 병합
```

- **일정 위험**: 1주차(10-08~14)에 018·025~029 5개 PR(025·026은 한 PR)과 030 결정이 몰려 있다. 3.2는 031까지 1주차로 잡았지만 030이 10-14 기한이라 **031은 10-15~16으로 하루 이틀 밀릴 가능성이 높다**. 031의 후속(032 ERD 보정, 033·034 마이그레이션)은 2주차라 영향은 작다. 밀리면 ROADMAP 3.2를 갱신한다.
- **025와 026은 PR 하나로 합친다**(사용자 결정 P5, 2026-10-08). 리뷰 대기를 한 번 줄이기 위함이다. 브랜치 이름은 `feature/f1-common-lib-components`(제안)로 하고, ROADMAP Task 025·026의 브랜치 이름과 다르므로 완료할 때 두 Task `기록:`에 합쳤다는 사실과 실제 브랜치 이름을 남긴다. 합쳐도 **단계마다 멈춰서 확인**하는 원칙은 유지한다(025 검증 4종 통과 → 026 진행).

## 3. 브랜치·PR 전략

- 모든 Task는 `develop`에서 브랜치를 만들고 `develop` 대상 PR(squash)로 합친다(git.md). 브랜치 이름은 ROADMAP에 적힌 그대로다: `feature/f0-routes-layout`, `feature/f1-public-screens`, `feature/f1-member-screens`, `feature/f1-admin-screens`, `feature/f1-openapi-draft`. 025·026은 합쳐서 `feature/f1-common-lib-components` 하나를 쓴다(2절).
- **앞 Task의 PR이 병합된 뒤 다음 브랜치를 만든다.** 병합 전 브랜치 위에 다음 브랜치를 쌓으면(stacked) squash 병합 때 이력이 달라져 충돌 정리가 필요하다. 1인 개발이고 PR 수가 적어 기다리는 편이 싸다.
- 028·029는 둘 다 027만 선행이라 027 병합 후 각각 `develop`에서 만든다. 공통 파일(`src/mocks/handlers.ts`, `router.tsx`)을 함께 건드리므로 **028을 먼저 병합하고 029 브랜치는 그 뒤에 `develop`을 받아 만든다**.
- 커밋·푸시·PR·병합은 사용자가 요청할 때만 한다. CodeRabbit 재리뷰 한도(시간당 10건) 때문에 커밋을 모아 푸시한다.

## 4. 공통 작업 방식 (모든 프론트 Task)

1. **검증 4종을 각각 따로 실행해 종료 코드 확인**: `npm run lint`, `npm run format:check`, `npm run test`, `npm run build`(PowerShell에서 `;`로 이으면 앞 실패가 가려진다).
2. **V-F(Playwright MCP)**: dev 서버 백그라운드 실행 → `browser_navigate` → `browser_snapshot` → 상호작용 재현 → `browser_console_messages`(error 0건) → 필요 시 375×812 → `browser_close`·서버 종료. Phase 2 깊이는 "목업 흐름이 끝까지 이어짐"(4.5).
3. **문서 확인**: React 19, React Router 8, TanStack Query 5, Tailwind 4, shadcn, orval 8, MSW 3, Vitest 5는 구현 전에 context7로 사용 버전 API를 확인한다. 특히 **`react-router` 8**(설치 버전 `^8.4.0`)은 데이터 라우터 API(`createBrowserRouter`·`RouterProvider`의 import 경로, `errorElement`, `lazy`)를 v6·v7 기억으로 쓰지 않는다.
4. **화면 시각 방향**: 화면 Task(027~029)는 `frontend-design:frontend-design` 스킬로 방향을 잡되, 값은 `src/styles/tokens.css` 토큰으로만 쓴다(확정은 Task 036·037).
5. 주석은 frontend.md "주석 스타일"대로 상세히(컴포넌트·훅 역할, `useEffect` 의존성 이유, 인증·가드 코드의 보안 이유).

## 5. Task별 계획

### Task 018 — 라우트·레이아웃·가드 골격

- 만들 파일: `src/app/router.tsx`, `src/app/RootLayout.tsx`, `src/app/providers.tsx`(QueryClient), `src/app/guards/RequireAuth.tsx`·`RequireAdmin.tsx`(자리, 지금은 통과), `src/app/RouteErrorBoundary.tsx`, `src/pages/*Page.tsx`(빈 페이지), `src/pages/NotFoundPage.tsx`, `src/components/common/ComingSoon.tsx`("준비 중" 자리표시).
- 정리: `src/App.tsx`·`App.css`·템플릿 에셋(`hero.png`, `react.svg`, `vite.svg`) 삭제, `main.tsx`가 `providers` + `RouterProvider`를 렌더링. `index.css`의 템플릿 CSS 정리(Task 017 `기록:`이 018로 넘긴 항목: `#root`·템플릿 변수·다크 모드 충돌).
- 라우트 표 (제안, PRD 6절 화면 ID 기준):

| 화면 | 경로 (제안) | 가드 | MVP |
|---|---|---|---|
| SCR-01 검색 | `/` (검색어는 `?q=`) | 공개 | 목업 |
| SCR-02 작품 상세 | `/titles/:mediaType/:tmdbId` | 공개 | 목업 |
| SCR-03 독점 목록 | `/exclusives` | 공개 | 준비 중 |
| SCR-04 About | `/about` | 공개 | 목업 |
| SCR-05 정확도 안내 | `/accuracy` | 공개 | 준비 중 |
| SCR-06 로그인 | `/login`, 콜백 `/auth/callback`(TECH 4절 그대로) | 공개 | 목업 |
| SCR-07 온보딩·내 설정 | `/settings` | 로그인 | 목업 |
| SCR-08 찜 목록 | `/wishlist` | 로그인 | 목업 |
| SCR-09 요금 확인 | `/pricing` | 로그인 | 목업 |
| SCR-10 플랜 결과 | `/plan` | 로그인 | 목업 |
| SCR-11 플랜 변경 내역 | `/plan/history` | 로그인 | 준비 중 |
| SCR-12 알림 설정 | `/settings/notifications` | 로그인 | 준비 중 |
| SCR-13 탈퇴 | `/settings/withdraw` | 로그인 | 준비 중 |
| SCR-14 상품·가격 | `/admin/products` | 관리자 | 목업 |
| SCR-15 제공처 매핑 | `/admin/providers` | 관리자 | 목업 |
| SCR-16 수집 | `/admin/collect` | 관리자 | 목업 |
| SCR-17 모니터링 | `/admin/monitoring` | 관리자 | 준비 중 |
| SCR-18 LLM 검토 | `/admin/llm` | 관리자 | 준비 중 |

  - `/titles/:mediaType/:tmdbId`인 이유: 작품은 `UNIQUE(media_type, tmdb_id)`이고(ROADMAP Task 033), `TITLE_NOT_FOUND`도 TMDB ID 기준이다. TMDB는 영화와 TV의 ID 공간이 달라 ID만으로는 겹친다.
  - 페이지는 `lazy`로 나눠 관리자 화면 코드가 일반 사용자 번들에 섞이지 않게 한다(React Router 8 방식은 context7 확인).
- 완료 기준(ROADMAP): V-F에서 **모든 경로**가 로드되고 콘솔 에러 0건. 없는 경로는 404 화면.
- 테스트: 라우트 표의 경로마다 렌더링되는지 보는 Vitest 1개(경로를 빠뜨리면 실패).

### Task 025 — 공통 유틸·에러 인터셉터

- `src/lib/format.ts`: `formatWon(15000)` → "15,000원"(`Intl.NumberFormat('ko-KR')`), `formatMinutes(750)` → "12시간 30분"(0분·60분 미만·정각 경계), 날짜 표시(데이터 기준일).
- `src/lib/tmdbImage.ts`: 상대 경로 + 크기 → 이미지 URL, 경로가 없으면 `null`(호출부가 대체 이미지 표시).
- `src/lib/errorMessages.ts`: `docs/api/error-codes.md`의 errorCode → 화면 메시지 표. 공통 코드 10개 + TECH 신규 4개(제안 상태). 모르는 코드는 기본 메시지.
- `src/api/http.ts` 응답 인터셉터: 오류 본문이 `application/problem+json`이면 `status`·`title`·`detail`·`errorCode`·`fieldErrors`를 꺼내 화면에서 쓰기 쉬운 오류 객체로 정리. **429는 "잠시 후 다시 시도" 안내**. 401 재발급은 Task 057(여기서 만들지 않음). 성공 응답 래퍼는 벗기지 않는다(frontend.md).
- 429 안내를 띄울 방법: 토스트 컴포넌트가 필요하다 → shadcn `sonner` 추가 제안(결정 P3).
- 429 V-F 방법(제안): MSW 핸들러가 URL에 `?mockScenario=rate-limit`이 있으면 429 ProblemDetail을 돌려준다. 계약에 없는 API를 새로 만들지 않고, 이미 있는 `listOttServices` 핸들러를 쓴다.
- 테스트: 포맷 함수 경계값, `errorMessages`가 `error-codes.md`의 모든 코드를 갖는지(백엔드 `ErrorCodeDocumentTest`와 같은 발상, Vitest에서 문서 파일을 읽어 비교).

### Task 026 — 공통 표시 컴포넌트 1차

- `src/components/common/`: `ProviderStatusBadge`(있음/없음/모름을 **아이콘 모양 + 텍스트**로, 색은 보조), `ServiceStatusRow`(7개 서비스, 쿠팡플레이 "데이터 부족"), `PosterCard`(이미지 없음 대체), `PriceDisplay`(가격 출처 라벨 ADMIN/USER_OVERRIDE/ALREADY_PAID/FREE), `SourceAttribution`(JustWatch 출처), `DataAsOf`(데이터 기준일), `EstimatedTag`("추정"), `LoadingState`·`ErrorState`·`EmptyState`.
- 색은 임시 토큰(`src/styles/tokens.css`)만. 확정은 Task 037.
- 확인용 화면(제안): 개발 모드에서만 열리는 `/__dev/components`(`import.meta.env.DEV`일 때만 라우트 등록). Task 037에서 스크린샷 비교에도 쓴다.
- 테스트(RTL): 3상태가 **텍스트로** 구분된다(색 없이도 읽힘), 데이터 부족 표시.

### Task 027 — 공개 화면 목업 (SCR-01·02·04·06)

- `src/mocks/fixtures/`: 실제 작품명, 7개 서비스, **영화·드라마 동명 작품**, **시즌별 제공처가 다른 드라마**, 제공 상태 3가지가 모두 나오는 데이터. 포스터는 실제 TMDB 경로를 쓰지 않고 대체 이미지 표시를 확인하는 데이터를 섞는다(결정 P4).
- `features/search`(검색창, URL `?q=`, 결과 카드, 외부 수집 실패 안내 자리), `features/title-detail`(영어 대체 줄거리, 출연진 10명, 시즌별 제공처·총 시간, 기준일, 시즌 단위 찜), About(TMDB 로고·고지문 자리, JustWatch, 비영리), 로그인(버튼, 콜백 로딩·실패).
- 비로그인 찜 → 로그인 화면 이동. 목업에서 로그인 여부를 바꾸는 방법이 필요하다 → 결정 P2.
- 완료 기준: V-F로 검색 → 결과 → 상세 → 찜(비로그인 시 로그인 화면) 클릭 재현, 콘솔 에러 0건.

### Task 028 — 회원 화면 목업 (SCR-07~10)

- `features/onboarding`(7개 서비스 상태, 구독 중이면 요금제·결제일, 시청 시간 프리셋·"주 N회 × 회당 M시간" 환산·직접 입력, 예산, React Hook Form + Zod), `features/wishlist`(우선순위 MUST/WANT/MAYBE, 다 봤음, 삭제, 계산하기), `features/pricing`(기본 요금·사용자 금액 수정, 계산 진행 표시), `features/plan`(요약, 이번 달 확정·다음 두 달 예상 카드, 남은 작품과 이유, 절감액, 해지 예약 권장, 저장).
- **저장 시 409 `PLAN_DRAFT_STALE` → 자동 재계산 흐름**을 목업 시나리오로 재현(`?mockScenario=stale` 제안).
- R11-18(환산 입력 저장)·R11-19(드라마 찜 단위)는 030에서 결정되므로 목업은 **두 안을 비교할 수 있게** 만든다(예: 환산 입력값을 저장하는 화면/분으로만 저장하는 화면). 어느 쪽인지 화면에 표시해 030 검토가 쉽게 한다.
- 완료 기준: V-F로 온보딩 → 찜 → 요금 확인 → 계산 → 결과 → 저장(409 자동 재계산 포함) 끝까지.

### Task 029 — 관리자 화면 목업 (SCR-14~16)

- SCR-14 상품·가격(단품 목록, 가격 이력, 가격 추가 시 **기간 겹침 오류**), SCR-15 제공처 매핑(provider_id·원본 라벨·매핑 서비스·tier, **미매핑 하이라이트**, Task 004 매핑표를 fixture로), SCR-16 수집(실행 이력, 수동 실행 **202·409**, 실행별 변경 작품, 재계산 대상·알림 상태는 "2단계부터" 자리).
- 완료 기준: V-F로 세 화면의 등록·매핑·실행 상호작용 재현, 콘솔 에러 0건.

### Task 030 — [H] 목업 승인과 화면 결정 (사용자)

- Claude가 준비할 것: 목업 둘러보기 순서표(경로·확인할 점), 결정 4건의 선택지 비교표(R11-14 SCR-06·07 통합, R11-18 환산 입력 저장, R11-19 드라마 찜 단위, R11-20 시즌 0 안내), 각 선택이 ERD·계약에 주는 영향.
- 사용자가 할 것: `VITE_USE_MOCK=true`로 직접 눌러 보고 승인/수정 요청, 4건 결정. 결과는 ROADMAP Task 030 `기록:`과 10.2에 남긴다(`feature/docs-*` PR).

### Task 031 — OpenAPI 초안·생성물 연결 ([F][B])

- 승인된 목업에서 MVP API를 `docs/api/openapi.yaml`에 정의한다(경로 규칙·operationId·tags·`CommonResponseXxx`·example·nullable·required·enum, `api-contract.md` 절차). 구현 전 operation에는 `x-planned: true` + description "제안". 이미 구현된 `listOttServices`에는 달지 않는다.
- 함께 처리: `ProblemDetail.fieldErrors` description의 "(제안)" 삭제(Task 023 `기록:`의 남은 일), 새로 필요한 오류 코드는 `error-codes.md`에 제안 등록(R-9).
- `npm run api:generate` → 027~029의 손으로 쓴 임시 타입·핸들러를 생성 훅·생성 MSW로 교체, 화면 전용 데이터만 `fixtures/`에 남김, `features/*/hooks`에서 `select: (res) => res.data`.
- 백엔드 쪽 확인: `cd backend; .\gradlew.bat test --tests "com.ottnavi.contract.OpenApiContractTest"`가 `x-planned`를 제외하고 통과(Task 023 동작).
- **사용자(백엔드 담당)의 계약 초안 승인**이 완료 조건이다. 승인 전에 PR 본문에 operation 목록 표를 붙인다.
- **임시 타입 제거**(P1 결정): 027~029에서 만든 `features/*/mockTypes.ts`를 모두 생성 타입으로 바꾸고 지운다. 완료 조건은 `Get-ChildItem -Recurse -Filter mockTypes.ts frontend/src`의 결과가 비어 있는 것이다.

## 6. 사용자 결정 현황 (2026-10-08)

| ID | 내용 | 결정 / 추천안 | 시점 |
|---|---|---|---|
| P1 | 027~029 목업의 임시 타입. frontend.md는 "타입을 손으로 정의하지 않는다"인데 PRD 9절·ROADMAP은 "목업 → 계약 초안" 순서다 | **결정(A + frontend.md 예외 조항, 2026-10-08)**: `frontend.md`에 "목업 단계의 임시 타입 예외"를 추가했다. 계약에 아직 없는 API에 한해 `features/*/mockTypes.ts`에만 임시 타입을 두고 `// 임시: Task 031에서 생성 타입으로 교체` 표시. 031에서 전부 지운다. 근거 우선순위상 PRD(UI 선행)가 rules보다 앞선다 | 027 전 |
| P2 | 목업에서 로그인 상태 전환 방법 | **결정(A)**: 목업 모드에서만 보이는 "목업 로그인/로그아웃·관리자 전환" 토글. 실제 인증 저장소(Task 057)와 분리 | 027 전 |
| P3 | 429·오류 안내 UI | **결정(A)**: shadcn `sonner`(토스트) 추가 | 025 전 |
| P4 | fixture 포스터 이미지 | **결정(A)**: 실제 TMDB 이미지 URL은 쓰지 않고 대체 이미지 표시를 기본으로(외부 요청 없이 V-F 콘솔 에러 0건 유지) | 027 전 |
| P5 | 025·026 PR 합치기 | **결정: 합친다**(2절) | 025 전 |
| P6 | 라우트 경로(5절 표) | **결정: 표대로**. 이 경로는 브라우저 주소창에 보이는 프론트(클라이언트) 화면 주소이며, 백엔드 API 주소(`/api/...`)와 다르다 | 018 전 |
| 에이전트 | `frontend-dev` 추가 방법과 명세(7절) | **결정(A)**: 이 문서 브랜치에 추가해 PR로 병합. 파일은 작성 완료, hook 9개 시험 통과 | 지금 |

## 7. 프론트 에이전트(`frontend-dev`) 만들기 계획

### 7.1 왜 `develop`에서 바로 만들 수 없나, 어떻게 하나

- 에이전트 파일은 **체크아웃한 브랜치의 `.claude/agents/`** 에서 로드된다(git.md, `coderabbit-triage` 때 확인). `develop`은 보호 규칙으로 직접 커밋·푸시가 막혀 있다(Task 012).
- 그래서 **`develop`에서 문서 브랜치를 만들어 PR로 합친 뒤, 그 이후 `develop`에서 만드는 모든 feature 브랜치가 에이전트를 갖게** 한다. `coderabbit-triage` 에이전트를 `feature/docs-coderabbit-triage-agent`로 넣은 방식과 같다.

| 방법 | 내용 | 판단 |
|---|---|---|
| **A. 문서 브랜치 → PR → 병합** (추천) | 지금 브랜치(`feature/docs-task022-031-plan`)에 에이전트·hook을 함께 넣어 PR 하나로 합친다 | 버전 관리되고, 이후 브랜치 전부에 적용. PR 하나로 CodeRabbit 리뷰도 한 번 |
| B. 사용자 전역 `~/.claude/agents/` | 브랜치와 무관하게 즉시 사용 | 저장소에 남지 않고 다른 프로젝트에도 보인다. A가 병합되기 전 임시로만 쓸 만하다 |
| C. 에이전트만 별도 브랜치(`feature/docs-frontend-dev-agent`) | A와 같지만 PR을 나눈다 | 리뷰가 분리되어 깔끔하지만 PR·대기가 하나 늘어난다 |

진행 순서(A, 사용자 결정 2026-10-08):
1. ✅ 사용자가 7.2 명세를 승인했다(방법 A).
2. ✅ 이 브랜치에 `.claude/agents/frontend-dev.md`와 `.claude/hooks/block-backend-write.ps1`을 추가했다. hook은 9개 경우(backend·generated 쓰기 차단, frontend·`docs/api` 쓰기 허용, Bash 쓰기 차단·`api:generate`·lint·읽기 허용)를 시험해 모두 기대대로 동작했다. 한글 메시지가 깨지지 않도록 BOM 포함 UTF-8로 저장했다.
3. ⬜ **동작 확인**: 새 세션(또는 `/agents`)에서 `frontend-dev`가 목록에 보이는지, 에이전트에게 `backend/` 아래 파일 쓰기를 시켜 hook이 막는지, `frontend/src/api/generated/` 직접 수정이 막히는지, `frontend/` 쓰기와 `npm run lint`는 되는지 본다. (세션 도중 추가한 에이전트가 바로 인식되는지는 확인 필요)
4. 사용자 요청 시 커밋 → 푸시 → `gh pr create --base develop` → CodeRabbit → squash 병합.
5. `git switch develop; git pull` 후 `feature/f0-routes-layout`을 만들어 Task 018부터 시작한다.

### 7.2 에이전트 명세 (초안)

- 이름 `frontend-dev`, 모델 `opus`(backend-dev와 같음).
- 도구: `Read, Glob, Grep, Write, Edit, Bash`, context7(`resolve-library-id`, `query-docs`), Playwright MCP(V-F에 필요한 `browser_navigate`, `browser_snapshot`, `browser_take_screenshot`, `browser_click`, `browser_type`, `browser_select_option`, `browser_fill_form`, `browser_console_messages`, `browser_resize`, `browser_wait_for`, `browser_close`).
- hook(PreToolUse, `Edit|Write|NotebookEdit|Bash`): `block-backend-write.ps1`
  - `backend/` 아래 쓰기 차단(backend-dev의 `block-frontend-write.ps1`과 대칭).
  - `frontend/src/api/generated/` **직접 수정 차단**(생성물은 `npm run api:generate`로만 바뀐다).
  - `docs/api/openapi.yaml`·`error-codes.md`는 허용(Task 031이 계약을 쓴다).
  - 한계도 같다: Bash는 문자열 검사라 실수 방지용이지 보안 경계가 아니다.
- 본문 구성(backend-dev와 같은 틀):
  0. 역할과 금지: 구현·검수, `backend/` 수정 금지(필요하면 변경 내용만 보고), 생성물 수정 금지, 커밋·푸시·PR은 요청 시에만, `main`·`develop`이면 먼저 알림.
  1. 작업 전 확인: 근거 우선순위, `openapi.yaml`이 유일한 계약(필드 추측 금지), 실제 설치 버전은 `frontend/package.json` 우선.
  2. context7: React 19, React Router 8, TanStack Query 5, Tailwind 4, shadcn, orval 8, MSW 3, Vitest 5, RHF·Zod 4. 알려진 함정(orval `httpClient: 'axios'`, msw 3 `onUnhandledFrame`, TS 6 `paths`, Vite 8 `import.meta.dirname`, shadcn `cn` 패키지) 먼저 떠올리기.
  3. 구현 방식: frontend.md 스타일·상태 관리·화면 표시 규칙, 상세 주석, 화면 Task는 `frontend-design` 스킬 참고하되 토큰만 사용.
  4. 검증: 검증 4종 따로 실행, V-F 절차(4.5)와 콘솔 에러 0건, 결과를 있는 그대로 보고.
  5. 보고 형식: 바꾼 파일, 실행 명령과 결과, 확인 못 한 것, 사용자 결정 필요, 백엔드가 해야 할 변경(직접 고치지 않음).
- 메인과의 역할 분담: 작은 수정·문서·계획은 메인이 하고, 화면 Task 구현처럼 출력이 많은 작업을 에이전트에 맡겨 메인 컨텍스트를 아낀다. 인증·라우트 가드 코드는 사용자가 직접 검토한다(frontend.md).

### 7.3 협업 구조와 UI/UX 에이전트(`ui-designer`)

- **원칙**: 에이전트 작업 공간의 기준은 **각 에이전트 md 지침**이고, hook은 실수를 줄이는 보조 장치다(사용자 의견, backend-dev 때와 동일). `ui-designer`는 hook 없이 지침만으로 경계를 둔다.
- **사용자 의견 반영**: 프론트는 대부분 `frontend-dev`가 맡고, 사용자는 주로 UI/UX 수정을 요청한다. 그래서 시각 판단을 맡는 `ui-designer`를 추가했다(필요 없다고 판단되면 파일 하나만 지우면 된다).

| 에이전트 | 맡는 일 | 수정 가능 공간 | 접점 |
|---|---|---|---|
| `backend-dev` | 백엔드 검수·맡긴 구현 | `backend/` | 계약(`openapi.yaml`) |
| `frontend-dev` | 프론트 전반: 화면 구조·상태·데이터·라우팅·목업·테스트·검증 | `frontend/`(생성물 제외), 계약 Task에서 `docs/api` | 계약, 메인 경유로 `ui-designer`에게 시각 판단을 넘김 |
| `ui-designer` | 시각 방향·토큰·레이아웃·반응형·접근성 시각 요소·상태 화면의 겉모습, 사용자의 UI/UX 수정 요청 | `frontend/src/styles`·`index.css`, 컴포넌트·페이지의 className·마크업(로직 제외) | 로직 변경은 `frontend-dev`에게 넘길 내용으로 보고 |

- 에이전트끼리는 직접 대화하지 못한다. 메인(또는 사용자)이 순서대로 호출하고, 각 보고서의 "넘길 내용"을 다음 에이전트에게 전달한다. 같은 파일을 동시에 고치지 않는다.
- **디자인 결정은 사용자의 몫**이다(Task 036). `ui-designer`는 2~3가지 방향을 제안하고 사용자가 고르면 그 방향을 토큰으로 옮긴다.
- 추가 파일: `.claude/agents/ui-designer.md`.
- **공식 문서 목록**(사용자 요청 2026-10-08): 세 에이전트 md의 "공식 문서 목록" 표에 context7 라이브러리 ID와 공식 사이트 도메인을 넣었다(`resolve-library-id`로 확인). 표에 있으면 resolve를 건너뛰고 `query-docs`에 바로 쓴다. 확인하지 못한 것: React Router 8·msw 3 전용 ID(없음, 설치된 타입 정의와 TECH 6절로 확인), Jackson 3(context7 ID가 2.x 기준이라 Spring Boot·Spring Data Redis 문서와 공식 이전 가이드로 확인).

## 8. 도구·플러그인

| 도구 | 상태 | 용도 |
|---|---|---|
| `frontend-design` 스킬 | 설치됨(Task 013) | 027~029 화면 방향 |
| `feature-dev` 에이전트들 | 설치됨 | 프론트에서는 선택적. 031 계약 설계 검토에 `code-architect` 정도 |
| Playwright MCP | 사용 가능 | V-F |
| context7 MCP | 사용 가능 | 버전별 문서 |
| **shadcn MCP** | `.mcp.json`에 있으나 **이번 세션에서 연결 실패(타임아웃 30초)** | shadcn 컴포넌트 추가 시 참고. CLI(`npx shadcn add`)로도 되므로 필수는 아니다. 쓰려면 Claude Code 재시작 후 `/mcp`로 상태 확인 |
| 추가 플러그인 | **필요 없음** | 현재 구성으로 018·025~031을 진행할 수 있다 |

## 9. 쉬림프 태스크 매니저

- 이미 등록됨(대기): Task 018, Task 022(코드 부분).
- 미등록: 024, 025~031. 이 계획이 승인되면 Task별로 등록하고 의존 관계(018 → 025 → 026 → 027 → 028·029 → 030 → 031)를 건다. 로드맵이 기준이고 쉬림프는 보조다(CLAUDE.md).
