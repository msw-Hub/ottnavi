import { useRef } from 'react'
import { Link, Outlet, ScrollRestoration } from 'react-router'
import { useMeasuredHeaderHeight } from '@/hooks/useMeasuredHeaderHeight'
import { useIsDemo } from '@/hooks/useIsDemo'
import { usePageViewTracking } from '@/hooks/usePageViewTracking'
import { useSession } from '@/hooks/useSession'
import { DemoBanner } from '@/app/DemoBanner'
import { HeaderAccountNav } from '@/app/HeaderAccountNav'
import { HeaderPrimaryNav } from '@/app/HeaderPrimaryNav'
import { TitleSearchForm } from '@/components/common/TitleSearchForm'
import { WishlistAddedDialog } from '@/components/common/WishlistAddedDialog'
// import { isMockMode } from '@/lib/mockScenario' // 이전: 개발용 이동 링크(주석 처리됨)용

// 푸터 링크 공통 모양. 글자색은 푸터의 회색(muted)을 그대로 받고, 마우스를 올리면 밑줄이 생긴다.
// 키보드 포커스 표시는 index.css의 전역 :focus-visible 윤곽선이 맡는다(이전 About 링크와 같은 방식)
const FOOTER_LINK_CLASS = 'inline-flex min-h-10 items-center underline-offset-4 hover:underline'

// 이전(홈 1차안 첫 판): 본문에 큰 검색창이 있는 화면에서 머리글 검색창을 숨기던 목록. 검색창을 머리글로 통일하며 쓰지 않는다
// const PAGES_WITH_OWN_SEARCH = ['/', '/search']

/**
 * 모든 화면이 공유하는 바깥 틀(헤더 · 본문 · 푸터)이다.
 *
 * 라우터의 최상위 라우트가 이 컴포넌트를 그리고, 주소에 맞는 화면은 <Outlet /> 자리에 들어간다.
 * 그래서 화면을 옮겨 다녀도 헤더·푸터는 다시 만들어지지 않고 본문만 바뀐다.
 * 색은 디자인 토큰(bg-background, text-muted-foreground 등)만 쓴다.
 */
export function RootLayout() {
  // 이전: 개발용 이동 링크가 목업 확인용 주소 파라미터를 이어 붙이도록 현재 주소의 쿼리를 읽었다(링크를 주석 처리해 쓰지 않는다)
  // const { search } = useLocation()
  // 머리글의 실제 높이를 재서 토스트 위치 변수에 반영한다(줄 수가 바뀌어도 토스트가 머리글을 덮지 않게, Task 090)
  const headerRef = useRef<HTMLElement>(null)
  useMeasuredHeaderHeight(headerRef)
  // 화면 이동마다 방문 통계(GA4) page_view를 보낸다. 측정 ID가 비면 아무것도 하지 않는다(lib/analytics.ts)
  usePageViewTracking()
  // 푸터의 회원 탈퇴 링크를 보일지 정한다. 머리글 계정 영역(HeaderAccountNav)과 같은 훅이라 목업·실제 환경에서 같은 판단을 한다
  const isAuthenticated = useSession().status === 'authenticated'
  const isDemo = useIsDemo()
  return (
    <div className="flex min-h-svh flex-col">
      {/* 키보드 사용자가 헤더 링크를 매번 거치지 않고 본문으로 바로 가도록 하는 링크. 포커스를 받을 때만 보인다 */}
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:bg-background focus:px-3 focus:py-2"
      >
        본문으로 건너뛰기
      </a>

      {/*
        bg-card: 본문 바탕보다 한 단계 밝은 면으로 머리글을 구분한다(Task 026 검수).
        min-h-(--header-height): 토스트 위치(app/providers.tsx)와 같은 CSS 변수(styles/tokens.css)를 최소 높이로 써서,
        머리글 높이와 "토스트가 시작하는 높이"가 한 값으로 묶이게 한다(Task 037). 홈 1차안에서 줄 구성이 바뀌어 값을 다시 쟀다(tokens.css).

        배치(홈 1차안 피드백 반영: 검색창은 모든 화면에서 머리글 하나로 통일했다. 홈·검색 본문의 큰 검색창은 없앴다):
        - 640px 이상: 한 줄 [로고][홈·랭킹·탐색] ... [검색창][계정]
        - 640px 미만: [로고][홈·랭킹·탐색] / [검색창 ...][계정] (계정이 검색창 옆에 들어가지 않으면 셋째 줄로 내려간다)
          돋보기를 눌러 펼치는 방식 대신 검색창을 늘 보이게 한 이유: 한 번 더 누르지 않고 바로 입력할 수 있고(사용자 요청 "바로 검색"),
          펼침 버튼의 열림 상태·포커스 이동을 따로 관리하지 않아도 되며, 입력창·버튼이 모두 40px 높이라 손가락으로 누르기 쉽다.
          대신 머리글이 한 줄(약 48px) 높아진다.
        - 보이는 순서 = HTML 순서 = Tab 순서(로고 → 메뉴 → 검색창 → 계정)를 모든 폭에서 지킨다. CSS order로 줄을 바꾸지 않고
          flex-wrap의 자연스러운 줄바꿈만 쓴다(WCAG 2.4.3 포커스 순서). 모바일에서 검색창은 최소 폭 12rem(min-w-48)이라 로고·메뉴 줄에
          들어가지 못하고 둘째 줄로 내려가며, 남는 폭을 모두 차지한다(flex-1).
          이전(order 사용): 모바일 [로고 ... 계정] / [검색창] / [메뉴] — 계정이 보이는 위치(윗줄)보다 Tab 순서에서 늦게 잡혔다.
      */}
      <header ref={headerRef} className="min-h-(--header-height) border-b bg-card">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-3 gap-y-2 px-4 py-3 sm:flex-nowrap sm:gap-x-4">
          <Link
            to="/"
            className="-my-2 inline-flex min-h-10 shrink-0 items-center text-lg font-bold tracking-tight"
          >
            OTT내비
          </Link>
          {/* 이전: className="max-sm:order-2 max-sm:basis-full"(모바일 셋째 줄로 내림). 지금은 모바일에서도 로고 바로 옆이다 */}
          <HeaderPrimaryNav />
          {/* 모바일: 둘째 줄에서 남는 폭 전부(flex-1, 최소 12rem) / 넓은 화면: 메뉴와 계정 사이 남는 폭(최대 24rem)
              이전: className="max-sm:order-1 max-sm:basis-full sm:ml-auto sm:max-w-sm sm:flex-1" */}
          <TitleSearchForm variant="compact" className="min-w-48 flex-1 sm:ml-auto sm:max-w-sm" />
          {/* 계정 영역(로그인 링크 / 찜 목록·관리·로그아웃 / 목업 모드의 개발용 전환). 상태별 표시는 HeaderAccountNav가 맡는다.
              모바일에서 검색창 옆 폭이 모자라면 셋째 줄 오른쪽 끝으로 내려간다(ml-auto). 이전: <div className="max-sm:ml-auto"> */}
          <div className="ml-auto shrink-0 sm:ml-0">
            <HeaderAccountNav />
          </div>
          {/*
            이전(홈 1차안 첫 판): 홈·검색 화면에서는 작은 검색창을 숨기고 돋보기 링크(/search)만 두었다. 사용자 피드백으로 취소했다.
            <Link to="/search" aria-label="작품 검색" className="-my-2 ml-auto inline-flex size-10 ..."><SearchIcon /></Link>
          */}
        </div>
        {/* 데모 계정 안내 띠(Task 109). 머리글 안에 두어 머리글 높이 측정(useMeasuredHeaderHeight)에 함께 잡히고, 토스트가 띠를 덮지 않는다 */}
        <DemoBanner />
      </header>

      {/* tabIndex={-1}: 건너뛰기 링크로 이동했을 때 main이 포커스를 받을 수 있게 한다(Tab 순서에는 들어가지 않음) */}
      <main
        id="main"
        tabIndex={-1}
        className="mx-auto w-full max-w-5xl flex-1 px-4 py-8 outline-none"
      >
        <Outlet />
      </main>
      {/*
        화면을 옮길 때 스크롤 위치를 다룬다(앱에 하나만 둔다. React Router 공식 문서).
        - 이전: 이 컴포넌트가 없어서 설정 화면에서 아래로 내려간 채 홈으로 가면 홈이 맨 위가 아닌 같은 높이에서 시작했다(운영 피드백).
        - 새로 이동(링크 클릭)하면 맨 위에서 시작하고, 뒤로 가기·앞으로 가기에서는 그 화면을 떠날 때의 위치로 돌아온다
          (기본 키가 location.key라서 같은 주소라도 이동할 때마다 새 위치로 본다).
      */}
      <ScrollRestoration />

      {/* 푸터 링크는 inline-flex min-h-10으로 누르는 영역을 세로 40px로 넓혔다(Task 037 ⑫) */}
      <footer className="border-t">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-center gap-x-4 gap-y-1 px-4 py-4 text-center text-sm text-muted-foreground">
          <Link to="/about" className={FOOTER_LINK_CLASS}>
            서비스 소개(About)
          </Link>
          {/* 개인정보처리방침: 로그인 여부와 상관없이 모두에게 보인다(Google 로그인 동의 화면 게시에 필요한 공개 주소) */}
          <Link to="/privacy" className={FOOTER_LINK_CLASS}>
            개인정보처리방침
          </Link>
          <Link to="/terms" className={FOOTER_LINK_CLASS}>
            이용약관
          </Link>
          {/* 출처 표기 자리. 화면별 JustWatch 출처는 SourceAttribution(Task 026), TMDB 로고·고지문은 About 화면(Task 027)에서 채운다 */}
          <p>작품·제공처 정보 출처: TMDB, JustWatch</p>
          {/*
            회원 탈퇴 링크(SCR-13). 이전에는 내 설정 맨 아래 오른쪽에 작은 빨간 글자로 있어 뜬금없어 보였다(운영 피드백, 사용자 확정으로 푸터로 옮김).
            - 로그인한 사용자에게만 보인다(useSession의 authenticated). 확인 중(checking)·비로그인·재발급 오류에서는 숨긴다.
            - 색은 다른 푸터 링크와 같은 회색(muted)이다. 빨강은 실제로 지우는 확인 화면(WithdrawScreen)에서만 쓴다:
              여기서 누르면 바로 지워지는 것이 아니라 설명과 동의 단계가 있는 화면으로 갈 뿐이라, 위험 색으로 겁을 줄 이유가 없다.
            - 맨 끝에 둔 이유: 자주 쓰는 링크가 아니라 눈에 덜 띄는 자리가 맞다. 모바일에서 줄이 넘치면 마지막 줄로 내려간다.
          */}
          {/* 데모 계정에는 숨긴다(사용자 결정 Q4): 공용 계정이라 탈퇴할 수 없다. 주소로 직접 들어가면 탈퇴 화면이 막힌 이유를 안내한다 */}
          {isAuthenticated && !isDemo && (
            <Link to="/settings/withdraw" className={FOOTER_LINK_CLASS}>
              회원 탈퇴
            </Link>
          )}
          {/*
            이전: 목업 확인용 "개발용 이동" 링크(Task 028). 로그인 후 화면으로 가는 상시 메뉴가 없어 목업 모드에만 두었다.
            머리글 "내 메뉴"(HeaderAccountMenu)가 생겨 지운다(주석 처리). 목업 파라미터는 메뉴 링크가 이어 붙인다.
          {isMockMode() && (
            <nav aria-label="개발용 이동" className="flex flex-wrap items-center gap-x-3 gap-y-1">
              <span>개발용 이동</span>
              <Link to={{ pathname: '/settings', search }} className="inline-flex min-h-10 items-center underline-offset-4 hover:underline">내 설정</Link>
              <Link to={{ pathname: '/wishlist', search }} className="...">찜 목록</Link>
              <Link to={{ pathname: '/pricing', search }} className="...">요금 확인</Link>
              <Link to={{ pathname: '/plan', search }} className="...">플랜 결과</Link>
            </nav>
          )}
          */}
        </div>
      </footer>

      {/* 찜 추가 직후 팝업. 앱에 한 번만 둔다(열림 상태는 hooks/useWishlistAddedDialog). Link를 쓰므로 라우터 안인 여기에 둔다 */}
      <WishlistAddedDialog />
    </div>
  )
}

/*
 * 이전 머리글 검색창(HeaderSearchForm). 홈 1차안에서 components/common/TitleSearchForm으로 옮기며
 * 이동 주소를 `/?q=`에서 `/search?q=`로 바꿨다. 참고용으로 남긴다.
 *
 * function HeaderSearchForm() {
 *   const navigate = useNavigate()
 *   const [searchParams] = useSearchParams()
 *   const currentQuery = searchParams.get('q') ?? ''
 *   function handleSubmit(event: FormEvent<HTMLFormElement>) {
 *     event.preventDefault()
 *     const query = String(new FormData(event.currentTarget).get('q') ?? '').trim()
 *     navigate(query ? { pathname: '/', search: `?${createSearchParams({ q: query })}` } : '/')
 *   }
 *   return (
 *     <form role="search" onSubmit={handleSubmit}
 *       className="flex min-w-0 flex-1 items-center gap-2 max-sm:order-1 max-sm:basis-full">
 *       <input key={currentQuery} type="search" name="q" defaultValue={currentQuery}
 *         aria-label="작품 제목 검색" placeholder="작품 제목 검색" className="h-10 ..." />
 *       <Button type="submit" variant="outline" className="h-10 border-input bg-transparent">검색</Button>
 *     </form>
 *   )
 * }
 */
