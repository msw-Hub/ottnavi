import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { logout } from '@/api/generated/auth/auth'
import { isMockMode } from '@/lib/mockScenario'
import { setMockRole } from '@/mocks/mockSession'
import { useAuthStore } from '@/stores/authStore'

// 서버 로그아웃 요청이 실패했을 때의 안내. 화면에서는 이미 로그아웃됐지만 서버의 Refresh Token은 남았을 수 있음을 알린다
const LOGOUT_SERVER_FAILED_MESSAGE =
  '이 기기에서는 로그아웃했지만 서버에서 로그아웃을 완료하지 못했습니다. 공용 기기라면 잠시 후 다시 로그아웃해 주세요.'

/**
 * 로그아웃을 실행하는 함수를 돌려준다. 머리글의 "로그아웃" 버튼이 쓴다.
 *
 * 흐름:
 * 1. POST /api/auth/logout: 서버가 Refresh Token 계열을 폐기하고 쿠키를 지운다(HttpOnly 쿠키는 프론트가 못 지운다)
 * 2. 로그아웃 중 표시를 켜고 첫 화면으로 이동한다(보호 화면에 남아 가드가 로그인 화면으로 다시 보내는 일을 피하려고 이동을 먼저 마친다)
 * 3. 메모리의 Access Token·역할을 비운다
 * 4. 쿼리 캐시를 모두 지운다(이전 사용자의 찜·플랜 데이터가 다음 사용자 화면에 남지 않게)
 *
 * 서버 요청이 실패해도 3·4는 한다. 사용자가 로그아웃을 눌렀는데 이 기기에 로그인 상태를 남기는 쪽이 더 위험하다.
 * 이 경우 서버의 Refresh Token은 만료(14일)까지 살아 있을 수 있어 그 사실을 토스트로 알린다.
 * Access Token은 서버에 저장하지 않으므로 메모리에서 지우는 것이 폐기의 전부다(TECH 4절).
 */
export function useLogout(): () => Promise<void> {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  // 의존성: queryClient·navigate는 거의 바뀌지 않지만, 바뀌면 최신 값으로 다시 만들어야 해서 배열에 둔다
  return useCallback(async () => {
    // 1.
    let serverFailed = false
    try {
      await logout()
    } catch {
      serverFailed = true
    }
    // 2. 로그아웃 중임을 표시한다. 가드(RequireAuth)가 곧 바뀔 비로그인 상태를 보고도 로그인 화면이 아니라 첫 화면으로 보내게 한다.
    //    이동(navigate)이 끝나도 React가 새 화면을 그리기 전까지 옛 보호 화면의 가드가 남아 있을 수 있어, 이동만 기다려서는 막을 수 없다
    useAuthStore.getState().setSigningOut(true)
    await navigate('/', { replace: true })
    // 3. 메모리의 토큰·역할을 비운다
    useAuthStore.getState().clearSession()
    if (isMockMode()) setMockRole('GUEST') // 목업 모드의 로그인 상태는 개발용 전환 값이 정한다
    // 4.
    queryClient.clear()
    if (serverFailed) toast.warning(LOGOUT_SERVER_FAILED_MESSAGE)
    // 로그아웃 표시는 여기서 거두지 않는다. 옛 보호 화면의 가드(RequireAuth)가 화면에서 사라질 때 스스로 거둔다.
    // (예전에는 setTimeout(…, 0)으로 거뒀는데, 첫 화면이 그려지기 전에 꺼질 수 있어 느린 CI에서 테스트가 흔들렸다)
  }, [navigate, queryClient])
}
