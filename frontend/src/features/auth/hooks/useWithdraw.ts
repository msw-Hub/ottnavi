import { useCallback } from 'react'
import { toast } from 'sonner'
import { useWithdrawMe } from '@/api/generated/user/user'
import { isApiError } from '@/api/http'
import { useEndSession } from '@/hooks/useEndSession'

// 서버가 오류를 돌려줬지만 화면에 보일 문장을 알 수 없을 때의 안내
const WITHDRAW_FAILED_MESSAGE = '탈퇴하지 못했어요. 잠시 후 다시 시도해 주세요.'

/**
 * 회원 탈퇴를 실행한다(계약 withdrawMe, DELETE /api/me). 탈퇴 화면(WithdrawScreen)에 넘길 값 세 개를 돌려준다.
 *
 * 흐름:
 * 1. DELETE /api/me: 서버가 계정과 데이터를 즉시 지우고, Refresh Token(HttpOnly 쿠키)을 폐기·만료시킨다. 쿠키는 프론트가 못 지운다
 * 2. 성공하면 이 기기의 로그인 상태를 정리하고 첫 화면으로 이동한다(useEndSession: 로그아웃과 같은 정리라 재사용한다)
 * 3. "탈퇴가 완료됐어요" 토스트를 보인다
 *
 * 실패하면 정리하지 않는다. 로그아웃과 달리 탈퇴는 서버가 지웠다는 확인을 받기 전에 로그인 상태를 비우면
 * "탈퇴한 줄 알았는데 데이터가 남은" 상태가 되므로, 실패 안내를 보이고 같은 화면에서 다시 시도하게 한다.
 * 401(이미 만료)이면 http.ts 인터셉터가 재발급을 시도하고, 그래도 안 되면 가드가 로그인 화면으로 보낸다.
 *
 * 생성 훅(useWithdrawMe)의 mutation.onSuccess에서 정리하는 이유: onSuccess가 끝날 때까지 isPending이 참으로 유지돼
 * 이동 직전까지 대화상자가 닫히지 않고 버튼이 잠겨 있다(요청 도중 화면만 닫혀 결과를 못 보는 일을 막는다).
 */
export function useWithdraw() {
  const endSession = useEndSession()
  const mutation = useWithdrawMe({
    mutation: {
      onSuccess: async () => {
        // 2.
        await endSession()
        // 3.
        toast.success('탈퇴가 완료됐어요')
      },
    },
  })

  // 의존성: mutate는 TanStack Query가 안정된 참조로 주지만, 규칙(exhaustive-deps)을 지키려고 배열에 둔다
  const { mutate } = mutation
  // 1.
  const withdraw = useCallback(() => mutate(), [mutate])

  const errorMessage = mutation.isError
    ? isApiError(mutation.error)
      ? mutation.error.userMessage
      : WITHDRAW_FAILED_MESSAGE
    : undefined

  return { withdraw, isPending: mutation.isPending, errorMessage }
}
