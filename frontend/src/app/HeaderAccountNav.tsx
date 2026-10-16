import { Link, useLocation } from 'react-router'
import { MockRoleSwitcher } from '@/app/MockRoleSwitcher'
import { useLogout } from '@/hooks/useLogout'
import { useSession } from '@/hooks/useSession'
import { getMockRequestParams } from '@/lib/mockScenario'

/**
 * 머리글 오른쪽의 계정 영역이다. 비로그인이면 "로그인" 링크, 로그인했으면 사용자 표시와 "로그아웃"을 보인다.
 *
 * 로그인 상태는 hooks/useSession이 준다(실제 환경은 authStore, 목업 모드는 개발용 전환 토글의 역할).
 * 재발급을 확인하는 중(checking)에는 계정 영역을 비워 둔다(로그인한 사용자에게 "로그인" 링크가 잠깐 보였다 사라지는 깜빡임을 피한다).
 * 로그아웃은 POST /api/auth/logout을 부른다(hooks/useLogout).
 * TODO(Task 058): 로그인 상태면 내 메뉴(설정·찜·요금·플랜)를 보인다. 지금은 찜 목록 링크만 둔다.
 *
 * 머리글 높이를 키우지 않으려고 모두 text-sm 한 줄이고 개발용 전환은 h-6이다(토스트 시작 위치가 머리글 높이에 맞춰져 있다).
 */
export function HeaderAccountNav() {
  const session = useSession()
  const location = useLocation()
  const logout = useLogout()
  // 비로그인으로 그릴 상태: guest, 그리고 재발급 오류(error: 로그인 여부를 몰라 로그인 링크를 남겨 둔다)
  const isGuest = session.status === 'guest' || session.status === 'error'

  // 찜 목록 링크에 이어 붙일 목업 확인용 파라미터(없으면 빈 문자열). 운영(목업 아님)에서는 빈 객체라 아무것도 붙지 않는다
  const mockParams = new URLSearchParams(getMockRequestParams()).toString()
  const wishlistSearch = mockParams ? `?${mockParams}` : ''

  // 로그아웃: 서버에 폐기를 요청하고, 메모리의 토큰·쿼리 캐시를 비운 뒤 첫 화면으로 보낸다(hooks/useLogout)
  function handleLogout() {
    void logout()
  }

  // 링크·버튼은 min-h-10(40px)·좌우 px-1로 누르는 영역을 넓혔다(Task 037 ⑫). -my-2(위아래 -8px)는 넓힌 높이가 레이아웃 높이에는 안 잡히게 하는 장치다:
  // 그냥 40px로 두면 모바일 머리글 윗줄(로고 26px)이 40px로 커져 머리글이 103px → 117px이 되고 토스트 위치(--header-height)와 어긋난다.
  // gap-1 + px-1 x 2 = 12px로 항목 사이 간격은 이전(gap-3)과 같아, 좁은 폭에서 줄바꿈이 새로 생기지 않는다
  return (
    <nav aria-label="계정" className="ml-auto flex items-center gap-1">
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
            임시 진입점(Task 057에서 실제 "내 메뉴"(설정·찜·요금·플랜)로 확정되면 이 링크는 그 메뉴 안으로 옮긴다).
            지금은 찜 목록으로 가는 길이 푸터의 개발용 이동뿐이라 회원일 때 머리글에서 바로 갈 수 있게 둔다.
            목업 확인용 파라미터(?mockScenario=… 등)는 이어 붙이고, 검색어 같은 다른 쿼리는 가져가지 않는다.
          */}
          <Link
            to={{ pathname: '/wishlist', search: wishlistSearch }}
            className="-my-2 inline-flex min-h-10 items-center px-1 text-sm underline-offset-4 hover:underline"
          >
            찜 목록
          </Link>
          <span className="text-sm text-muted-foreground">
            {session.role === 'ADMIN' ? '관리자' : '회원'}
          </span>
          <button
            type="button"
            onClick={handleLogout}
            className="-my-2 inline-flex min-h-10 items-center px-1 text-sm underline-offset-4 hover:underline"
          >
            로그아웃
          </button>
        </>
      )}
    </nav>
  )
}
