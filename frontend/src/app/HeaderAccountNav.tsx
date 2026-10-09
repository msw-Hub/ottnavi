import { Link, useLocation, useNavigate } from 'react-router'
import { MockRoleSwitcher } from '@/app/MockRoleSwitcher'
import { useMockRole } from '@/hooks/useMockRole'
import { getMockRequestParams, isMockMode } from '@/lib/mockScenario'
import { setMockRole } from '@/mocks/mockSession'

/**
 * 머리글 오른쪽의 계정 영역이다. 비로그인이면 "로그인" 링크, 로그인했으면 사용자 표시와 "로그아웃"을 보인다.
 *
 * 지금은 목업 단계라 로그인 상태를 mocks/mockSession에서 읽는다. 운영(목업 아님)에서는 항상 비로그인으로 그린다.
 * TODO(Task 057): 실제 인증 상태(stores/authStore)를 읽고, 로그아웃은 POST /api/auth/logout을 부르며, 로그인 상태면 내 메뉴(설정·찜)를 보인다.
 *
 * 머리글 높이를 키우지 않으려고 모두 text-sm 한 줄이고 개발용 전환은 h-6이다(토스트 시작 위치가 머리글 높이에 맞춰져 있다).
 */
export function HeaderAccountNav() {
  const role = useMockRole()
  const location = useLocation()
  const navigate = useNavigate()
  // 운영에서는 목업 역할을 믿지 않는다(역할 값이 남아 있어도 비로그인으로 본다)
  const effectiveRole = isMockMode() ? role : 'GUEST'

  // 찜 목록 링크에 이어 붙일 목업 확인용 파라미터(없으면 빈 문자열). 운영(목업 아님)에서는 빈 객체라 아무것도 붙지 않는다
  const mockParams = new URLSearchParams(getMockRequestParams()).toString()
  const wishlistSearch = mockParams ? `?${mockParams}` : ''

  // 목업 로그아웃: 역할을 비로그인으로 되돌리고 첫 화면으로 보낸다. 서버·토큰이 없어 이 한 줄이 전부다
  function handleLogout() {
    setMockRole('GUEST')
    navigate('/')
  }

  return (
    <nav aria-label="계정" className="ml-auto flex items-center gap-3">
      <MockRoleSwitcher />
      {effectiveRole === 'GUEST' ? (
        // 지금 보던 화면을 from으로 넘겨, 로그인이 끝나면 이 화면으로 돌아오게 한다(getSafeRedirectPath가 내부 경로만 통과시킨다)
        <Link
          to="/login"
          state={{ from: `${location.pathname}${location.search}` }}
          className="text-sm underline-offset-4 hover:underline"
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
            className="text-sm underline-offset-4 hover:underline"
          >
            찜 목록
          </Link>
          <span className="text-sm text-muted-foreground">
            {effectiveRole === 'ADMIN' ? '관리자' : '회원'}
          </span>
          <button
            type="button"
            onClick={handleLogout}
            className="text-sm underline-offset-4 hover:underline"
          >
            로그아웃
          </button>
        </>
      )}
    </nav>
  )
}
