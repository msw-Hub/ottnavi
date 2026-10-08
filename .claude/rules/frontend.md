---
paths:
  - "frontend/**"
---

> 이 문서는 React + TypeScript 생태계의 보편적인 모범사례를 바탕으로, ottnavi 프론트엔드 결정 사항에 맞춰 작성되었다.
> 프론트엔드는 Claude가 주로 작성하고, 사용자는 흐름·디자인·인증 코드를 검토한다. 스타일 판단이 필요한 부분은 이 문서 기준을 따르되 Claude가 상황에 맞게 조정한다.
> 공통 언어/주석 원칙은 `~/.claude/CLAUDE.md`(User instructions)를 따른다. 여기는 React/TypeScript 고유 스타일만 다룬다.
> 화면 요구사항은 `docs/PRD.md`, API 계약은 `docs/api/openapi.yaml`을 기준으로 한다.

- UI에 노출되는 문자열(레이블, 에러 메시지 등)은 한국어로 쓴다.

## 코딩 스타일

- **함수형 컴포넌트 + Hooks**만 사용한다. 클래스 컴포넌트는 쓰지 않는다.
- **TypeScript를 기본으로 한다.** `any`는 지양하고, 불가피하면 명시적으로 표시(`// TODO: any 제거` 등)한다.
- 컴포넌트는 **named export**를 기본으로 한다 (default export보다 리팩토링·자동완성에 유리). 라우트 지연 로딩처럼 default export가 필요한 경우만 예외로 둔다.
- 컴포넌트 하나는 **하나의 책임만** 지도록 작게 유지한다. 커지면 하위 컴포넌트로 쪼갠다.
- Props는 TypeScript `interface`로 정의하고, 구조 분해(destructuring)로 받는다.
- 상태가 복잡하게 중첩되지 않도록 하고, 파생 가능한 값은 state로 따로 두지 않고 계산해서 쓴다.
- `useMemo`/`useCallback`은 실제로 필요한 곳(무거운 연산, 자식 컴포넌트 리렌더링 방지)에만 사용한다 — 모든 곳에 습관적으로 붙이지 않는다.
- 커스텀 훅으로 재사용 가능한 로직을 분리한다 (`use` 접두사 필수).
- 렌더링 중에 객체/함수를 새로 생성하지 않도록 주의한다.
- 리스트 렌더링 시 `key`는 배열 인덱스 대신 고유 식별자를 사용한다 (예: `watchUnitId`, `productId`).
- `pages/`의 화면 컴포넌트는 얇게 유지하고, 데이터 조회와 화면 조립은 `features/`의 컴포넌트와 훅에 둔다.

## 상태 관리 원칙

어떤 상태를 어디에 둘지 다음 기준으로 정한다.

| 상태 종류 | 위치 | 예 |
|---|---|---|
| 서버 데이터 | TanStack Query (orval 생성 훅) | 작품 상세, 찜 목록, 현재 플랜 |
| 로그인 상태 | Zustand (`stores/authStore`) | Access Token, 사용자 역할 |
| 폼 입력 | React Hook Form | 온보딩, 요금 수정 |
| 화면 위치·필터 | URL 검색 파라미터 | 검색어, 페이지 번호 |
| 컴포넌트 내부 UI | `useState` | 펼침 여부, 선택 탭 |

- Zustand에는 인증 상태만 둔다. 서버 데이터를 Zustand에 복사해 두지 않는다.
- 검색어처럼 공유·새로고침 시 유지되어야 하는 값은 URL에 둔다.

## 변수 스타일

- 컴포넌트명: **PascalCase** (`PlanMonthCard`)
- 변수/함수명: **camelCase**
- boolean 변수/props: `is`/`has` 접두사 (`isLoading`, `hasError`)
- 상수: **UPPER_SNAKE_CASE** (`WATCH_TIME_PRESETS`)
- Enum 대신 문자열 리터럴 유니온 타입을 우선한다. 백엔드 enum 값은 orval이 생성한 타입을 그대로 쓴다.
- 비동기 함수: `fetch`/`load`/`get` 접두사로 동작을 드러냄 (`fetchCurrentPlan`)
- CSS 클래스명: Tailwind 유틸리티를 쓰고, 별도 클래스가 필요하면 kebab-case
- 도메인 용어는 백엔드와 같은 영문 용어를 쓴다 (`title`, `watchUnit`, `availability`, `ottService`, `product`, `plan`)
- `Item`, `Wrapper`, `Data` 같은 의미 없는 범용 이름은 피하고, 역할이 드러나는 이름을 쓴다

## 주석 스타일 (React/TS 고유 — 공용 원칙에 추가로 적용)

프론트엔드는 아직 익숙하지 않아 코드를 검토하는 데 시간이 걸리는 편이다. 그래서 공용 주석 원칙(User instructions)보다
**더 상세하게, 더 자주** 남긴다.

- 컴포넌트/훅 상단에 그 역할을 설명하는 주석을 남긴다 — 한 줄 요약에 그치지 않고, 필요하면 "왜 이 방식을 썼는지"까지 덧붙인다.
- Hook을 쓸 때(`useEffect`의 의존성 배열, `useMemo`/`useCallback`을 쓴 이유 등)는 **왜 필요한지**를 주석으로 남긴다.
  단순히 "무엇을 하는지"보다 "왜 이 시점에 이렇게 처리해야 하는지"를 설명하는 게 더 도움이 된다.
- 복잡한 조건문·계산 로직은 "단순한 방법 → 문제점 → 이 방식을 택한 이유" 흐름으로 설명한다.
- Props 타입 정의에도 각 필드가 무엇을 의미하는지 간단히 주석을 단다 (특히 의미가 바로 안 드러나는 필드).
- 인증·토큰 처리 코드(`api/http.ts`, `stores/authStore`, 라우트 가드)에는 보안상 이유를 반드시 주석으로 남긴다. 사용자가 직접 검토하는 코드다.
- `api/generated/`의 orval 생성 코드에는 주석을 달지 않는다 (재생성 시 사라진다).

## 백엔드 연동 규칙

백엔드는 `.claude/rules/backend.md` 기준을 따른다. 프론트는 그 규칙에 맞춰 다음을 지킨다.

- **API 계약**: `docs/api/openapi.yaml`이 유일한 근거다. 필드를 추측하거나 타입을 손으로 정의하지 않는다(목업 단계의 예외는 아래 "목업 단계의 임시 타입 예외").
  계약에 없는 데이터가 필요하면 코드를 짜기 전에 계약 변경부터 제안한다.
- **코드 생성**: orval로 `docs/api/openapi.yaml`에서 타입, TanStack Query 훅, MSW 목업을 생성한다 (`npm run api:generate`).
  생성물은 `src/api/generated/`에 두고 **직접 수정하지 않는다.**
- **HTTP 클라이언트**: orval의 custom mutator로 `src/api/http.ts`의 axios 인스턴스를 쓴다. 베이스 URL은 항상 상대 경로 `/api`다.
  배포 환경은 Vercel rewrite가, 로컬은 Vite dev server proxy가 백엔드로 전달한다. 백엔드 주소를 코드나 환경변수로 직접 지정하지 않는다.
- **성공 응답**: 백엔드가 `CommonResponse<T>`로 감싸서 내려준다. 생성된 훅의 타입도 래퍼를 포함하므로, 꺼내는 작업은
  `features/{기능}/hooks`에서 생성 훅을 감싸며 `select: (res) => res.data`로 처리한다. mutator에서 몰래 벗기지 않는다 (생성된 타입과 실제 값이 어긋난다).
- **에러 응답**: 백엔드가 `ProblemDetail`(RFC 9457, `application/problem+json`)로 내려주므로, 공통 인터셉터에서
  `status`/`title`/`detail`/`errorCode`를 파싱하는 형태로 통일한다. `errorCode`(SCREAMING_SNAKE_CASE)는 `src/lib/errorMessages.ts`의 매핑 테이블로 한국어 메시지를 보여준다.
  429(`RATE_LIMIT_EXCEEDED`)는 "잠시 후 다시 시도" 안내로 처리한다.
- **타입 정의**: 백엔드 DTO는 Java record라 필드명이 camelCase로 그대로 직렬화된다. 생성된 타입을 그대로 쓰고, 변환 레이어를 두지 않는다.
- **목업 단계의 임시 타입 예외**: 이 프로젝트는 화면(목업)을 먼저 만들고 계약(`openapi.yaml`)은 승인된 화면에서 뽑는다(PRD 9절, ROADMAP 4.4). 그래서 **아직 계약에 없는 API**는 생성 타입이 없다. 이 경우에 한해 임시 타입을 허용한다.
  - 위치: `features/{기능}/mockTypes.ts` 한 파일에만 둔다(제거할 때 한 번에 찾기 위함).
  - 표시: 타입마다 `// 임시: 계약 반영 후 생성 타입으로 교체` 주석을 단다.
  - 이미 계약에 있는 API(예: 서비스 목록)는 처음부터 생성 타입을 쓴다.
  - 제거: 계약이 반영되는 Task(MVP는 Task 031, 2단계 이후 기능은 해당 기능의 "실제 API 교체" Task)에서 생성 타입으로 바꾸고 `mockTypes.ts`를 지운다. 이 Task의 완료 조건은 해당 기능에 `mockTypes.ts`가 남지 않는 것이다. 확인은 범위를 구분한다: 2단계 이후 기능은 해당 기능 폴더만(`Get-ChildItem -Recurse -Filter mockTypes.ts frontend/src/features/{기능}`), MVP의 Task 031은 MVP 기능 전체가 대상이라 `frontend/src` 전체(`Get-ChildItem -Recurse -Filter mockTypes.ts frontend/src`)가 비어 있어야 한다. 아직 계약에 반영되지 않은 다른 기능의 `mockTypes.ts`는 남아 있어도 된다.
  - 이 예외는 `mockTypes.ts`에만 적용한다. 컴포넌트·훅에서 API 응답 타입을 따로 손으로 만들지 않는다.
- **환경변수**: 비밀 값은 프론트에 두지 않는다. `VITE_` 접두사 변수는 브라우저에 노출되므로 공개해도 되는 값(목업 사용 여부 `VITE_USE_MOCK` 등)만 둔다.

## 인증 규칙

- Access Token은 **Zustand 메모리에만** 둔다. `localStorage`, `sessionStorage`, 쿠키에 직접 저장하지 않는다 (XSS 시 탈취 위험).
- Refresh Token은 백엔드가 HttpOnly 쿠키로 관리한다. 프론트는 이 쿠키를 읽거나 다루지 않는다.
- 새로고침이나 로그인 직후에는 `/api/auth/refresh`를 호출해 Access Token을 다시 받는다. URL 파라미터로 토큰을 주고받지 않는다.
- 401 응답 시 인터셉터가 재발급을 한 번만 시도하고, 동시에 여러 요청이 401을 받아도 재발급 요청은 하나만 보낸다. 재발급 실패 시 로그인 화면으로 보낸다.
- 라우트는 공개 / 로그인 필요(`RequireAuth`) / 관리자(`RequireAdmin`) 세 가지로 나눈다. 화면 가드는 편의 기능일 뿐이고, 실제 권한 검사는 백엔드가 한다.

## 화면 표시 규칙 (ottnavi 고유)

- **제공 상태**: 있음 / 없음 / 모름 세 가지를 모양과 텍스트로 구분한다. 색만으로 구분하지 않는다. 공통 컴포넌트(`ProviderStatusBadge` 등)를 재사용한다.
- **출처 표기**: 제공처 정보가 보이는 화면에는 반드시 JustWatch 출처를, About 화면에는 TMDB 로고와 고지문을 둔다. 공통 컴포넌트로 만들어 빠뜨리지 않게 한다.
- **표현 수위**: 제공 여부와 추천 결과는 "확정"이 아니라 "확인된 범위"로 표현한다. 데이터 기준일을 함께 보여준다.
- **이미지**: TMDB의 `posterPath` 등은 상대 경로다. `src/lib/tmdbImage.ts`의 헬퍼로 크기를 지정해 URL을 만들고, 이미지가 없을 때의 대체 표시를 둔다.
- **숫자 표시**: 금액은 `Intl.NumberFormat('ko-KR')`로 "15,000원", 시간은 "12시간 30분" 형식으로 `src/lib/format.ts`의 함수를 통해서만 표시한다. 추정 러닝타임은 "추정" 표시를 붙인다.
- **디자인 값**: 색, 글꼴, 간격은 `src/styles`의 디자인 토큰(CSS 변수)만 사용한다. 컴포넌트에 색상 값을 직접 쓰지 않는다. 디자인 방향이 정해지면 토큰만 바꿔 전체에 반영하기 위함이다.
- **shadcn/ui**: `components/ui/`의 원본 컴포넌트는 가능한 한 그대로 두고, 변형이 필요하면 variant 추가나 `components/common/`의 래핑 컴포넌트로 처리한다.
- **로딩·오류·빈 상태**: 데이터를 보여주는 모든 화면에 세 상태를 함께 만든다. 특히 플랜 계산은 시간이 걸릴 수 있으므로 진행 중 상태를 명확히 보여준다.

## 목업 (MSW)

- 1주차 골격 단계에서는 `VITE_USE_MOCK=true`로 MSW 목업만으로 전체 화면이 동작해야 한다.
- 기본 목업은 orval이 생성하고, 화면 확인에 필요한 현실적인 데이터(실제 작품명, 7개 서비스, 3개월 플랜 예시)는 `src/mocks/fixtures/`에 직접 작성한다.
- 백엔드 API가 완성되면 해당 핸들러를 끄고 실제 API로 교체한다. 목업과 실제 응답이 다르면 계약(`openapi.yaml`)부터 확인한다.

## 추가 선호사항 (React/TS 고유)

- 컴포넌트 테스트는 구현 세부사항이 아니라 사용자 상호작용/동작 중심으로 작성한다 (React Testing Library 관점). API는 MSW로 대체한다.
- 테스트는 핵심 흐름(검색, 찜, 요금 수정, 결과 표시)과 포맷 함수 위주로 최소한만 둔다.
- 접근성(a11y)은 기본적으로 신경 쓴다 (시맨틱 태그, 적절한 `alt`, 키보드 접근성, 색만으로 정보를 전달하지 않기 등).

## 개발 환경

- Node.js LTS 버전 사용
- 패키지 매니저는 **npm**으로 통일한다 (`package-lock.json` 커밋).
- 로컬 개발 시 Vite dev server proxy로 `/api`를 로컬 백엔드(`http://localhost:8080`)에 연결한다.
- 배포는 Vercel(Root Directory = `frontend`). `vercel.json`에 `/api/*` → Cloudtype 백엔드 rewrite와 SPA 라우팅 fallback을 둔다.

## 기술 스택

이 프로젝트에서 확정한 스택이다.

- **언어/프레임워크**: TypeScript, React (SPA, 서버 렌더링 없음)
- **빌드 도구**: Vite
- **스타일링/UI**: Tailwind CSS v4, shadcn/ui
- **라우팅**: React Router
- **서버 상태**: TanStack Query
- **클라이언트 상태**: Zustand (인증 상태만)
- **폼/검증**: React Hook Form, Zod
- **API 연동**: orval(타입·훅·MSW 목업 생성), axios(custom mutator, 인터셉터)
- **목업**: MSW
- **린트/포맷**: ESLint + Prettier
- **테스트**: Vitest, React Testing Library

## 아키텍처

기능(feature) 단위로 응집시키는 구조를 기본으로 한다.

```
src/
├── app/              # 라우터, 전역 Provider(QueryClient 등), 레이아웃, 라우트 가드
├── pages/            # 라우트 단위 화면 (얇게 유지)
├── features/
│   └── {기능명}/     # search, title-detail, auth, onboarding, wishlist, pricing, plan, admin
│       ├── components/   # 이 기능에서만 쓰는 컴포넌트
│       ├── hooks/        # 생성된 Query 훅을 감싸는 기능 전용 훅
│       └── types.ts      # 화면 전용 타입 (API 타입은 생성물 사용)
├── components/
│   ├── ui/           # shadcn/ui 원본 컴포넌트
│   └── common/       # 여러 기능이 쓰는 공통 컴포넌트 (제공 상태 배지, 포스터 카드, 가격 표시, 출처 표기)
├── api/
│   ├── http.ts       # axios 인스턴스, 인증·에러 인터셉터
│   └── generated/    # orval 생성물 (수정 금지)
├── mocks/            # MSW 설정, 직접 작성한 fixtures
├── stores/           # Zustand (authStore만)
├── lib/              # 포맷터, 이미지 헬퍼, 에러 메시지 매핑 등 순수 함수
└── styles/           # 디자인 토큰, 전역 스타일
```

- 한 기능(feature)은 화면, 기능 전용 훅, 타입까지 자기 폴더 안에서 완결되도록 한다 — 기능 삭제 시 폴더 하나만 지우면 되게.
- 기능별 `api/` 폴더는 두지 않는다. API 호출 코드는 orval이 생성하고, 기능 폴더는 그 훅을 감싸 쓰기만 한다.
- 여러 기능에서 공통으로 쓰는 것만 상위(`components/common`, `lib/`)로 올린다.
- 기능 폴더끼리는 서로 import하지 않는다. 공유가 필요하면 상위로 올린다.
