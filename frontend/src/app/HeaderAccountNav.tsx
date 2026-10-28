import { HeartIcon } from 'lucide-react'
import { Link, useLocation } from 'react-router'
import { useListWishlistItems } from '@/api/generated/wishlist/wishlist'
import { HeaderAccountMenu } from '@/app/HeaderAccountMenu'
import { MockRoleSwitcher } from '@/app/MockRoleSwitcher'
// import { useLogout } from '@/hooks/useLogout' // 이전: 머리글 로그아웃 버튼용
import { useSession } from '@/hooks/useSession'
import { getMockRequestParams } from '@/lib/mockScenario'
import { cn } from '@/lib/utils'

/**
 * 머리글 오른쪽의 계정 영역이다. 비로그인이면 "로그인" 링크, 로그인했으면 찜 하트(개수 배지)와 "내 메뉴" 드롭다운(HeaderAccountMenu)을 보인다.
 * 이전: 하트 옆에 관리 링크(ADMIN만)와 "로그아웃" 버튼이 따로 있었다. 둘 다 "내 메뉴" 안으로 옮겼다.
 *
 * 로그인 상태는 hooks/useSession이 준다(실제 환경은 authStore, 목업 모드는 개발용 전환 토글의 역할).
 * 재발급을 확인하는 중(checking)에는 계정 영역을 비워 둔다(로그인한 사용자에게 "로그인" 링크가 잠깐 보였다 사라지는 깜빡임을 피한다).
 * 로그아웃은 메뉴 안에서 POST /api/auth/logout을 부른다(hooks/useLogout).
 *
 * 머리글 높이를 키우지 않으려고 모두 text-sm 한 줄이고 개발용 전환은 h-6이다(토스트 시작 위치가 머리글 높이에 맞춰져 있다).
 */
export function HeaderAccountNav() {
  const session = useSession()
  const location = useLocation()
  // const logout = useLogout() // 이전: 머리글 로그아웃 버튼용. HeaderAccountMenu로 옮겼다
  // 비로그인으로 그릴 상태: guest, 그리고 재발급 오류(error: 로그인 여부를 몰라 로그인 링크를 남겨 둔다)
  const isGuest = session.status === 'guest' || session.status === 'error'

  // 하트 배지에 쓸 찜 개수(계산 대상 = "다 봤음" 제외, 상한 20). 찜 목록 화면·찜 버튼과 같은 생성 훅·같은 쿼리 키(['/me/wishlist-items'])라
  // 캐시를 함께 쓰고, 찜을 추가·변경·해제하면 그쪽에서 이 키를 무효화하므로 배지도 따로 손대지 않아도 갱신된다.
  // 로그인했을 때만 요청한다(비로그인이면 하트 자체가 없고, 보내 봐야 401이다)
  const wishlistCountQuery = useListWishlistItems({
    query: {
      enabled: session.status === 'authenticated',
      select: (res) => res.data.activeItemCount,
    },
  })
  const wishlistCount = wishlistCountQuery.data ?? 0
  const hasWishlistBadge = wishlistCount > 0

  // 찜 목록 링크에 이어 붙일 목업 확인용 파라미터(없으면 빈 문자열). 운영(목업 아님)에서는 빈 객체라 아무것도 붙지 않는다
  const mockParams = new URLSearchParams(getMockRequestParams()).toString()
  const wishlistSearch = mockParams ? `?${mockParams}` : ''

  // 이전: 로그아웃 버튼이 여기 있었다. 지금은 HeaderAccountMenu가 useLogout을 직접 쓴다
  // function handleLogout() {
  //   void logout()
  // }

  // 링크·버튼은 min-h-10(40px)·좌우 px-1로 누르는 영역을 넓혔다(Task 037 ⑫). -my-2(위아래 -8px)는 넓힌 높이가 레이아웃 높이에는 안 잡히게 하는 장치다:
  // 그냥 40px로 두면 모바일 머리글 윗줄(로고 26px)이 40px로 커져 머리글이 103px → 117px이 되고 토스트 위치(--header-height)와 어긋난다.
  // gap-1 + px-1 x 2 = 12px로 항목 사이 간격은 이전(gap-3)과 같아, 좁은 폭에서 줄바꿈이 새로 생기지 않는다
  // ml-auto를 뺀 이유(홈 1차안): 바로 앞의 돋보기 링크(RootLayout)가 ml-auto로 오른쪽에 붙고, 계정 영역은 그 뒤에 이어 붙는다.
  // 이전: className="ml-auto flex items-center gap-1"
  return (
    <nav aria-label="계정" className="flex items-center gap-1">
      <MockRoleSwitcher />
      {session.status === 'checking' ? null : isGuest ? (
        // 지금 보던 화면을 from으로 넘겨, 로그인이 끝나면 이 화면으로 돌아오게 한다(getSafeRedirectPath가 내부 경로만 통과시킨다)
        <Link
          to="/login"
          state={{ from: `${location.pathname}${location.search}` }}
          className="-my-2 inline-flex min-h-10 items-center px-1 text-sm underline-offset-4 hover:underline"
        >
          로그인
        </Link>
      ) : (
        <>
          {/*
            찜 목록 진입점. "내 메뉴"에도 찜 목록 항목이 있지만 하트는 머리글에 그대로 둔다(사용자 결정).
            푸터의 개발용 이동은 목업 모드에서만 보이므로, 운영에서도 회원이 머리글에서 바로 찜 목록으로 갈 수 있게 둔다.
            목업 확인용 파라미터(?mockScenario=… 등)는 이어 붙이고, 검색어 같은 다른 쿼리는 가져가지 않는다.
          */}
          {/*
            하트 아이콘 + 개수 배지(HANDOFF 5-1 #2). 글자 "찜 목록"을 아이콘으로 바꿔 375px 윗줄의 폭을 아꼈다.
            - 누르는 영역: 40x40px(size-10) 정사각형. -my-2는 다른 링크와 같은 이유로 머리글 높이를 키우지 않게 하는 장치다.
            - mr-2: 하트와 오른쪽 이웃("로그아웃" 글자였고 지금은 "내 메뉴" 버튼)이 너무 붙어 보인다는 사용자 의견으로 오른쪽 간격 8px을 더했다.
            - 배지: 계산 대상 개수(activeItemCount)가 1 이상일 때만 하트 오른쪽 위에 겹쳐 숫자로 보인다(최대 20이라 두 자리까지).
              0개·불러오는 중·실패일 때는 배지 없이 하트만 둔다(0을 굳이 보이면 "아무 일 없음"을 강조하는 꼴이 된다).
              색만으로 알리지 않고 숫자 자체를 보이며, 배지 바탕(primary)과 글자(primary-foreground)는 shadcn 기본 짝이라 대비를 맞춰 둔 값이다.
              위치(top-0 -right-1): 두 자리("20")일 때도 하트가 보이도록 누르는 영역 밖으로 4px 내보냈다. 옆 링크와의 간격(gap-1 + px-1 = 8px) 안이라 겹치지 않는다.
              배지를 위로 더 올리지 않는 이유: 375px에서는 계정 줄이 검색창 바로 아래(위 여백 0px)에 붙어, 배지를 올리면 검색창 아래 테두리를 덮는다.
              대신 하트를 채운 뒤에는 "20" 배지가 하트 오른쪽 위 볼록한 부분을 덮어 모양이 뭉개져 보여서, 채운 하트만 왼쪽·아래로 2px씩 옮겼다(아래 HeartIcon).
              ring-card: 배지 둘레에 머리글 바탕색 테두리를 둘러 하트 선과 숫자가 엉겨 보이지 않게 한다.
            - 접근성 이름: 아이콘·배지는 낭독기에서 숨기고(aria-hidden) 링크의 aria-label로 "찜 목록, 12개"를 읽힌다.
            이전(글자 링크):
            <Link to={{ pathname: '/wishlist', search: wishlistSearch }}
              className="-my-2 inline-flex min-h-10 items-center px-1 text-sm underline-offset-4 hover:underline">찜 목록</Link>
          */}
          <Link
            to={{ pathname: '/wishlist', search: wishlistSearch }}
            aria-label={hasWishlistBadge ? `찜 목록, ${wishlistCount}개` : '찜 목록'}
            className="relative -my-2 mr-2 inline-flex size-10 items-center justify-center rounded-md hover:bg-accent hover:text-accent-foreground focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
          >
            {/*
              하트 모양: 찜이 1개 이상이면 빨강으로 채우고, 0개·불러오는 중·실패면 윤곽선만 둔다(사용자 요청: 윤곽선은 잘 안 보인다).
              0개에도 채우면 "찜한 게 있다"는 신호로 읽혀서 배지와 같은 조건(hasWishlistBadge)으로 맞췄다.
              색: --destructive(index.css의 밝은 빨강). 머리글 바탕(--card) 위 대비 약 7:1로 아이콘 기준(3:1)을 넘는다.
              짙은 빨강 --destructive-solid(#B32228)는 같은 바탕 위 약 1.6:1이라 어두운 머리글에서 묻혀 쓰지 않았다.
              색은 링크가 아니라 아이콘에 직접 줘서, 링크의 hover:text-accent-foreground가 덮어써도 하트는 빨강을 유지한다.
              색만으로 알리지 않는다: 채움(모양 차이)과 숫자 배지, 낭독 이름("찜 목록, 12개")이 같은 정보를 함께 전한다.
              -translate-x-0.5 translate-y-0.5: 배지가 있을 때만 하트를 왼쪽·아래로 2px 옮겨, 배지가 하트 오른쪽 위 모서리에만 걸치게 한다.
              하트+배지 묶음 전체로 보면 가운데에 가까워진다. 누르는 영역(링크 40x40)과 포커스 표시는 그대로다.
              이전: <HeartIcon aria-hidden="true" className="size-5" />
            */}
            <HeartIcon
              aria-hidden="true"
              className={cn(
                'size-5',
                hasWishlistBadge &&
                  '-translate-x-0.5 translate-y-0.5 fill-destructive text-destructive',
              )}
            />
            {hasWishlistBadge && (
              <span
                aria-hidden="true"
                className="absolute top-0 -right-1 inline-flex h-4.5 min-w-4.5 items-center justify-center rounded-full bg-primary px-1 text-[0.6875rem] leading-none font-bold text-primary-foreground tabular-nums ring-2 ring-card"
              >
                {wishlistCount}
              </span>
            )}
          </Link>
          {/*
            "내 메뉴" 드롭다운(시안 A). 관리 화면 진입점(ADMIN만)과 로그아웃이 이 안으로 옮겨졌다.
            역할은 useSession 값을 읽어 표시만 바꾸는 것이고, 실제 권한 검사는 백엔드와 라우트 가드(RequireAdmin)가 한다.
            찜 개수는 위 하트와 같은 값을 내려 보내 요청이 한 번만 나가게 한다.
          */}
          <HeaderAccountMenu
            isAdmin={session.role === 'ADMIN'}
            isDemo={session.role === 'DEMO'}
            wishlistCount={wishlistCount}
            search={wishlistSearch}
          />
          {/*
            이전: 관리 링크와 로그아웃 버튼이 머리글에 따로 있었다.
            {session.role === 'ADMIN' && (
              <Link to="/admin/collect"
                className="-my-2 inline-flex min-h-10 items-center px-1 text-sm underline-offset-4 hover:underline">관리</Link>
            )}
            역할 표시("회원"/"관리자")는 HANDOFF 5-1 #3으로 없앴다:
            <span className="text-sm text-muted-foreground max-sm:sr-only">
              {session.role === 'ADMIN' ? '관리자' : '회원'}
            </span>
            <button type="button" onClick={handleLogout}
              className="-my-2 inline-flex min-h-10 items-center px-1 text-sm underline-offset-4 hover:underline">로그아웃</button>
          */}
        </>
      )}
    </nav>
  )
}
