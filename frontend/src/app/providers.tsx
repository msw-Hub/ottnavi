import { useState, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { isApiError } from '@/api/http'
import { Toaster } from '@/components/ui/sonner'

interface ProvidersProps {
  children: ReactNode // 전역 Provider 안에 그릴 앱(지금은 RouterProvider)
}

// 조회(query)가 실패했을 때 다시 시도하는 최대 횟수. TanStack Query 기본값은 3번이다
const MAX_QUERY_RETRIES = 2

/**
 * 조회 실패 시 다시 시도할지 정한다. TanStack Query의 retry 옵션에 넘긴다.
 *
 * 기본값(무조건 3번 재시도)을 바꾸는 이유:
 * - 4xx(입력 오류, 권한 없음, 없는 작품 등)는 다시 보내도 결과가 같다. 사용자는 오류 안내를 늦게 보게 될 뿐이다.
 * - 특히 429(요청 제한)는 다시 보내면 제한을 더 오래 끌고, 서버에 부담만 준다.
 * 그래서 일시적일 가능성이 있는 네트워크 오류와 5xx만 최대 MAX_QUERY_RETRIES번 다시 시도한다.
 * 저장·삭제 같은 변경(mutation)은 TanStack Query 기본값이 재시도 0번이라 따로 정하지 않는다(중복 저장 방지).
 */
function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  if (failureCount >= MAX_QUERY_RETRIES) return false
  if (!isApiError(error)) return false
  if (error.kind === 'network') return true
  return error.status !== null && error.status >= 500
}

/*
 * 토스트 묶음(목록)에 붙이는 클래스.
 * - "toaster group": shadcn 원본(components/ui/sonner.tsx)이 붙이는 기본 클래스다. className prop을 넘기면 원본 값이 통째로
 *   바뀌므로 그대로 다시 적어 둔다.
 * - md 이상(768px~)에서만 토스트 폭을 넓힌다. sonner 기본 폭은 356px라 큰 화면 가운데에 두면 작아 보인다.
 *   28rem(448px, Tailwind 기본 토큰 --container-md)은 한 줄 안내문이 줄바꿈 없이 들어가면서도 화면을 가로지르지 않는 폭이다.
 *   sonner가 이 값(--width)을 요소의 인라인 style로 넣어서, 클래스로 덮으려면 !(important)가 필요하다.
 * - 600px 이하(모바일)는 sonner가 화면 폭에 맞춰(양옆 16px 여백) 따로 그리므로 이 설정과 상관없이 이전 모양 그대로다.
 */
const TOASTER_CLASS_NAME = 'toaster group md:[--width:var(--container-md)]!'

/*
 * 토스트 하나하나에 붙이는 클래스(sonner의 toastOptions.classNames).
 * toastOptions를 넘기면 원본의 toastOptions도 통째로 바뀌므로 원본의 "cn-toast"를 다시 적어 둔다.
 */
/*
 * 머리글(app/RootLayout.tsx의 <header>) 높이. 토스트를 이 높이보다 아래에서 시작시키려고 둔다.
 * 57px = 위아래 여백 py-3(12px × 2) + 검색창·검색 버튼 높이 h-8(32px) + 아래 테두리 1px.
 * 1280px·1920px·375px 폭에서 실제로 잰 높이도 모두 57px이다(375px에서도 머리글이 한 줄이다).
 * 숫자 대신 Tailwind 간격 토큰(--spacing, 4px)으로 적은 이유: 머리글 클래스(py-3, h-8)와 같은 단위라
 * 어떤 값에서 나온 높이인지 바로 보이고, 간격 토큰을 바꾸면 머리글과 토스트 위치가 함께 바뀐다.
 * 머리글과 CSS 변수(--header-height 등)를 공유하지 않은 이유: 머리글은 내용에 맞춰 높이가 정해지고(flex-wrap),
 * 공유하려면 머리글 높이를 고정값으로 묶어야 해 레이아웃이 바뀐다. 쓰는 곳이 이 한 곳뿐이라 여기 두는 편이 단순하다.
 * 머리글의 여백·높이·줄 수를 바꾸면 이 값도 함께 고친다(바꾸지 않으면 토스트가 다시 머리글을 덮는다).
 */
const HEADER_HEIGHT = 'calc(var(--spacing) * 14 + 1px)'

/*
 * 토스트 묶음의 위쪽 시작 위치. 머리글 바로 아래에 12px(간격 토큰 3칸) 띄워 로고·검색창·로그인을 덮지 않게 한다.
 * sonner는 offset(601px 이상)과 mobileOffset(600px 이하)을 따로 받는다. 둘 다 top만 정하고, 정하지 않은
 * 좌우·아래는 sonner 기본값(큰 화면 24px, 모바일 16px)이 그대로 들어가 폭·좌우 여백은 이전과 같다.
 */
const TOASTER_OFFSET = { top: `calc(${HEADER_HEIGHT} + var(--spacing) * 3)` }

const TOAST_OPTIONS = {
  classNames: {
    // md 이상에서 글자를 13px(sonner 기본) → 14px(text-sm 토큰)로 키운다. 모바일 글자 크기는 그대로다.
    // sonner 기본 CSS는 Tailwind 계층(layer) 밖에 있어 우선순위가 더 높으므로 !를 붙인다(sonner 공식 문서의 Tailwind 안내와 같다).
    toast: 'cn-toast md:text-sm!',
    // 오류 토스트만 붉은 바탕 + 흰 글자로 바꾼다.
    // 바탕은 --destructive가 아니라 --destructive-solid(tokens.css)다. 어두운 테마(Task 026)에서 --destructive는
    // 어두운 바탕 위 오류 "글자"용 밝은 빨강이라, 그 위에 흰 글자를 올리면 대비가 모자란다. 흰 글자용 면은 짙은 빨강을 따로 둔다.
    // sonner는 바탕·글자·테두리를 --normal-bg/--normal-text/--normal-border 변수로 칠한다. 이 변수를 오류 토스트 요소에서
    // 디자인 토큰으로 다시 정하면, 묶음에 정해 둔 기본값(흰 바탕)보다 가까운 쪽이 이겨 오류 토스트만 바뀐다.
    // richColors를 쓰지 않은 이유: sonner 고유 색(hsl 값)이 들어가 디자인 토큰을 벗어나고, 연한 빨강 위 빨간 글자가 된다.
    // 아이콘(X 표시 팔각형)과 문구는 그대로라, 색을 구분하기 어려운 사람에게도 오류라는 뜻이 전달된다.
    // 설명문([data-description])은 sonner가 짙은 회색으로 고정해 빨간 바탕에서 읽기 어려워지므로 글자색을 물려받게 한다.
    error:
      '[--normal-bg:var(--destructive-solid)] [--normal-border:var(--destructive-solid)] [--normal-text:var(--destructive-foreground)] [&_[data-description]]:text-inherit!',
    // 밝은 테마 때 쓰던 값(바탕을 --destructive로 칠함). 참고용으로 남긴다
    // '[--normal-bg:var(--destructive)] [--normal-border:var(--destructive)] [--normal-text:var(--destructive-foreground)] [&_[data-description]]:text-inherit!',
  },
}

/**
 * 앱 전체가 공유하는 Provider를 한곳에 모은다. 서버 상태용 TanStack Query와 알림 토스트(sonner)다.
 *
 * 모아 두는 이유: main.tsx와 테스트가 같은 Provider 조합을 쓰게 해, 테스트 환경이 실제 앱과 어긋나지 않게 하기 위함이다.
 */
export function Providers({ children }: ProvidersProps) {
  // useState의 초기화 함수로 QueryClient를 만드는 이유:
  // 컴포넌트 본문에서 바로 new QueryClient()를 하면 다시 그려질 때마다 새 캐시가 생겨 받아 둔 데이터가 사라진다.
  // 초기화 함수는 처음 한 번만 실행되므로 앱이 살아 있는 동안 같은 캐시를 쓴다.
  // 모듈 맨 위에 두지 않은 이유: 테스트마다 Providers를 새로 그리면 캐시도 새로 생겨 테스트끼리 데이터가 섞이지 않는다.
  const [queryClient] = useState(
    () => new QueryClient({ defaultOptions: { queries: { retry: shouldRetryQuery } } }),
  )

  return (
    <QueryClientProvider client={queryClient}>
      {children}
      {/*
        토스트를 그리는 자리. 앱에 한 번만 둔다(여러 개면 같은 토스트가 겹쳐 보인다).
        RootLayout이 아니라 여기 두는 이유:
        - 라우터 바깥이라 화면을 옮겨도, 라우트 오류 화면(RouteErrorBoundary)이 RootLayout을 대신 그려도 토스트가 유지된다.
        - API 인터셉터(api/http.ts)의 429 토스트는 어느 화면에서든 뜰 수 있어 화면과 무관한 위치가 맞다.
        - main.tsx와 테스트가 같은 Providers를 쓰므로 테스트에서도 토스트가 실제와 같은 방식으로 그려진다.
        theme="dark"로 고정하는 이유:
        - 앱은 어두운 테마 하나만 쓴다(Task 026, 밝은 테마·토글 없음). 기본값 "system"이면 OS가 밝은 모드일 때 토스트만 밝게 나온다.
        - "system"이면 sonner가 window.matchMedia를 쓰는데, 테스트 환경(jsdom)에는 이 함수가 없어 렌더링이 깨진다.
          "dark"·"light"처럼 값을 정해 주면 sonner가 matchMedia를 부르지 않는다(sonner dist 코드에서 확인).
        나중에 테마 전환을 도입하면(next-themes ThemeProvider 추가) 이 prop을 지워 원본 동작(useTheme)으로 돌린다.
        (Task 025까지는 밝은 테마라 theme="light"였다)
        position="top-center"인 이유: 큰 화면 오른쪽 아래 구석은 시선에서 멀어 오류 안내를 놓치기 쉽다.
        화면 가운데 위는 시선이 먼저 가는 곳이라 눈에 잘 띈다. 모바일에서도 같은 위치(위쪽, 화면 폭 가득)에 뜬다.
        offset·mobileOffset을 주는 이유: 위쪽 여백이 sonner 기본값(큰 화면 24px, 모바일 16px)이면 토스트가 떠 있는 동안
        (약 4초) 머리글(높이 57px)의 로고·검색창·로그인을 덮는다. 그래서 머리글 아래에서 시작하게 내린다(TOASTER_OFFSET).
      */}
      <Toaster
        theme="dark"
        position="top-center"
        offset={TOASTER_OFFSET}
        mobileOffset={TOASTER_OFFSET}
        className={TOASTER_CLASS_NAME}
        toastOptions={TOAST_OPTIONS}
      />
      {/* 위쪽 여백을 내리기 전 배치(위쪽 가운데, 기본 여백이라 머리글을 덮었다). 참고용으로 남긴다 */}
      {/* <Toaster theme="light" position="top-center" className={TOASTER_CLASS_NAME} toastOptions={TOAST_OPTIONS} /> */}
      {/* 이전 배치(오른쪽 아래, 기본 폭·흰 바탕). 위치·색 조정 전 참고용으로 남긴다 */}
      {/* <Toaster theme="light" /> */}
    </QueryClientProvider>
  )
}
