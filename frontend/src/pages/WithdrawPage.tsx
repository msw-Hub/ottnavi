// import { ComingSoon } from '@/components/common/ComingSoon'
import { PageTitle } from '@/components/common/PageTitle'
import { WithdrawScreen } from '@/features/auth/components/WithdrawScreen'
import { useWithdraw } from '@/features/auth/hooks/useWithdraw'

/**
 * SCR-13 회원 탈퇴(`/settings/withdraw`, 로그인 필요). 화면은 features/auth의 WithdrawScreen이 맡고, 이 파일은 연결만 한다.
 *
 * 이전(Task 081 전): 2단계 화면이라 준비 중 자리표시만 보였다.
 * return <ComingSoon title="회원 탈퇴" plannedPhase="2단계" />
 *
 * 탈퇴 요청·로그인 상태 정리·이동·토스트는 useWithdraw가 맡는다(목업 단계에서는 MSW 핸들러가 서버를 대신한다. 실제 API는 Task 082·084).
 */
export function WithdrawPage() {
  const { withdraw, isPending, errorMessage } = useWithdraw()
  return (
    <>
      <PageTitle name="회원 탈퇴" />
      <WithdrawScreen onWithdraw={withdraw} isPending={isPending} errorMessage={errorMessage} />
    </>
  )
}
