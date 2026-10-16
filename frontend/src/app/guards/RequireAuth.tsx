import { useEffect, type ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { restoreSession } from '@/api/http'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { useSession } from '@/hooks/useSession'
import { useAuthStore } from '@/stores/authStore'

interface RequireAuthProps {
  children?: ReactNode // 통과했을 때 그릴 것. 없으면 자식 라우트(<Outlet />)를 그린다(RequireAdmin이 안쪽에 역할 검사를 끼울 때 쓴다)
}

/**
 * 로그인이 필요한 화면(SCR-07~13)을 감싸는 라우트 가드다.
 *
 * 라우터에서 이 컴포넌트를 "경로 없는 부모 라우트"로 두고 보호할 화면들을 자식으로 넣는다.
 * 부모가 <Outlet />을 돌려주면 자식 화면이 그 자리에 그려지고, 다른 것을 돌려주면 자식 화면이 아예 그려지지 않는다.
 * 그래서 가드 판단을 이 한 곳에만 두면 보호 화면이 늘어나도 각 페이지에서 따로 검사할 필요가 없다.
 *
 * 상태별 동작(hooks/useSession):
 * 1. checking(앱을 열고 재발급을 확인하는 중): 로딩을 보인다. 이때 바로 로그인 화면으로 보내면 새로고침만 해도
 *    로그인한 사용자가 튕겨 나가므로 결과를 기다린다. 보호 화면의 데이터 요청도 이 단계에서는 시작되지 않는다.
 * 2. error(재발급이 서버·네트워크 오류): 로그인 화면으로 보내지 않는다. 로그인 여부를 모를 뿐 로그아웃된 것이 아니므로 "다시 시도"를 준다.
 * 3. guest(비로그인): 로그인 화면으로 보낸다. 가려던 경로는 router state(from)로 넘기고 로그인 뒤 그 화면으로 돌아온다.
 *    돌아갈 때는 이 사이트 안의 경로인지 다시 검사한다(오픈 리다이렉트 방지, lib/safeRedirect). replace라 뒤로 가기가 이 화면으로 되돌아오지 않는다.
 * 4. authenticated: 자식을 그린다.
 *
 * 보안상 전제: 화면 가드는 "로그인하지 않은 사람에게 빈 화면 대신 로그인 화면을 보여주는" 편의 기능일 뿐이다.
 * 브라우저 코드는 사용자가 마음대로 고칠 수 있으므로 실제 권한 검사는 백엔드(`/api/**` 인증)가 한다(frontend.md 인증 규칙).
 * 이 가드를 통과했다고 해서 데이터 접근이 허용되는 것은 아니다. 로그인 도중 토큰이 만료되면 http.ts가 재발급을 시도하고,
 * 재발급이 401이면 상태가 guest로 바뀌어 이 가드가 로그인 화면으로 보낸다.
 */
export function RequireAuth({ children }: RequireAuthProps) {
  const session = useSession()
  const location = useLocation()
  const isSigningOut = useAuthStore((state) => state.isSigningOut)

  // 로그아웃 표시는 이 가드가 화면에서 사라질 때(첫 화면이 그려진 뒤) 거둔다. 타이머로 거두면 느린 환경에서
  // 새 화면이 그려지기 전에 표시가 꺼져 가드가 로그인 화면으로 보내는 경쟁이 생긴다(useLogout 참고)
  useEffect(
    () => () => {
      if (useAuthStore.getState().isSigningOut) useAuthStore.getState().setSigningOut(false)
    },
    [],
  )

  // 1. (화면 제목 h1을 둔다: 낭독기 사용자가 제목으로 이동했을 때 지금 어떤 화면인지 알 수 있게 한다. 로그인 콜백 화면과 같은 모양, 057 검수 제안 4)
  if (session.status === 'checking') {
    return (
      <section className="mx-auto flex w-full max-w-md flex-col gap-4">
        <h1 className="text-2xl font-semibold">로그인 확인 중</h1>
        <LoadingState message="로그인 상태를 확인하는 중입니다" />
      </section>
    )
  }

  // 2. (제목은 아래 오류 상자 제목과 같은 말을 되풀이하지 않게 짧은 화면 이름만 쓴다)
  if (session.status === 'error') {
    return (
      <section className="mx-auto flex w-full max-w-md flex-col gap-4">
        <h1 className="text-2xl font-semibold">로그인 확인</h1>
        <ErrorState
          title="로그인 상태를 확인하지 못했습니다"
          message="서버와 연결하지 못했습니다. 잠시 후 다시 시도해 주세요."
          onRetry={() => void restoreSession()}
        />
      </section>
    )
  }

  // 3. (사용자가 로그아웃을 눌러 비로그인이 된 경우는 로그인 화면이 아니라 첫 화면으로 보낸다)
  if (session.status === 'guest' && isSigningOut) return <Navigate to="/" replace />
  if (session.status === 'guest') {
    return (
      <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
    )
  }

  // 4.
  return children ?? <Outlet />
}
