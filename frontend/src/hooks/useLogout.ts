import { useCallback } from 'react'
import { toast } from 'sonner'
import { logout } from '@/api/generated/auth/auth'
import { useEndSession } from '@/hooks/useEndSession'

// 서버 로그아웃 요청이 실패했을 때의 안내. 화면에서는 이미 로그아웃됐지만 서버의 Refresh Token은 남았을 수 있음을 알린다
const LOGOUT_SERVER_FAILED_MESSAGE =
  '이 기기에서는 로그아웃했지만 서버에서 로그아웃을 완료하지 못했습니다. 공용 기기라면 잠시 후 다시 로그아웃해 주세요.'

/**
 * 로그아웃을 실행하는 함수를 돌려준다. 머리글 "내 메뉴"의 로그아웃 항목이 쓴다.
 * (이전: 머리글의 "로그아웃" 버튼이 썼다)
 *
 * 흐름:
 * 1. POST /api/auth/logout: 서버가 Refresh Token 계열을 폐기하고 쿠키를 지운다(HttpOnly 쿠키는 프론트가 못 지운다)
 * 2. 이 기기의 로그인 상태 정리(첫 화면 이동·메모리 토큰·쿼리 캐시): useEndSession이 맡는다. 회원 탈퇴도 같은 정리를 쓴다
 *
 * 서버 요청이 실패해도 2는 한다. 사용자가 로그아웃을 눌렀는데 이 기기에 로그인 상태를 남기는 쪽이 더 위험하다.
 * 이 경우 서버의 Refresh Token은 만료(14일)까지 살아 있을 수 있어 그 사실을 토스트로 알린다.
 * (이전: 이동·토큰·캐시 정리 코드가 이 파일에 직접 있었다. 탈퇴와 나누려고 hooks/useEndSession으로 옮겼다)
 */
export function useLogout(): () => Promise<void> {
  const endSession = useEndSession()

  // 의존성: endSession은 navigate·queryClient가 바뀔 때만 바뀐다
  return useCallback(async () => {
    // 1.
    let serverFailed = false
    try {
      await logout()
    } catch {
      serverFailed = true
    }
    // 2.
    await endSession()
    if (serverFailed) toast.warning(LOGOUT_SERVER_FAILED_MESSAGE)
  }, [endSession])
}
