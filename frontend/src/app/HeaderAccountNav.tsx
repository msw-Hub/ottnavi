import { Link, useLocation, useNavigate } from 'react-router'
import { MockRoleSwitcher } from '@/app/MockRoleSwitcher'
import { useMockRole } from '@/hooks/useMockRole'
import { isMockMode } from '@/lib/mockScenario'
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
