import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { isMockMode } from '@/lib/mockScenario'
import { setMockRole } from '@/mocks/mockSession'
import { useAuthStore } from '@/stores/authStore'

/**
 * 이 기기의 로그인 상태를 정리하고 첫 화면으로 보내는 함수를 돌려준다. 로그아웃(useLogout)과 회원 탈퇴(useWithdraw)가 함께 쓴다.
 *
 * 서버 쪽 정리(로그아웃은 POST /api/auth/logout, 탈퇴는 DELETE /api/me)는 호출하는 쪽이 먼저 끝내고 부른다.
 * 여기서는 서버가 못 하는 "브라우저 안" 정리만 한다. Refresh Token은 HttpOnly 쿠키라 프론트가 지울 수 없고 서버가 만료시킨다.
 *
 * 흐름:
 * 1. 로그아웃 중 표시를 켜고 첫 화면으로 이동한다(보호 화면에 남아 가드가 로그인 화면으로 다시 보내는 일을 피하려고 이동을 먼저 마친다)
 * 2. 메모리의 Access Token·역할을 비운다
 * 3. 쿼리 캐시를 모두 지운다(이전 사용자의 찜·플랜 데이터가 다음 사용자 화면에 남지 않게)
 *
 * 로그아웃 표시는 여기서 거두지 않는다. 옛 보호 화면의 가드(RequireAuth)가 화면에서 사라질 때 스스로 거둔다
 * (타이머로 거두면 첫 화면이 그려지기 전에 꺼질 수 있어 느린 환경에서 테스트가 흔들렸다).
 * Access Token은 서버에 저장하지 않으므로 메모리에서 지우는 것이 폐기의 전부다(TECH 4절).
 */
export function useEndSession(): () => Promise<void> {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  // 의존성: queryClient·navigate는 거의 바뀌지 않지만, 바뀌면 최신 값으로 다시 만들어야 해서 배열에 둔다
  return useCallback(async () => {
    // 1. 로그아웃 중임을 표시한다. 가드(RequireAuth)가 곧 바뀔 비로그인 상태를 보고도 로그인 화면이 아니라 첫 화면으로 보내게 한다.
    //    이동(navigate)이 끝나도 React가 새 화면을 그리기 전까지 옛 보호 화면의 가드가 남아 있을 수 있어, 이동만 기다려서는 막을 수 없다
    useAuthStore.getState().setSigningOut(true)
    await navigate('/', { replace: true })
    // 2. 메모리의 토큰·역할을 비운다
    useAuthStore.getState().clearSession()
    if (isMockMode()) setMockRole('GUEST') // 목업 모드의 로그인 상태는 개발용 전환 값이 정한다
    // 3. 이전 사용자의 서버 데이터 캐시를 비운다
    queryClient.clear()
  }, [navigate, queryClient])
}
