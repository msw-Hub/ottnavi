import { ComingSoon } from '@/components/common/ComingSoon'

/** SCR-13 탈퇴(`/settings/withdraw`, 로그인 필요). 2단계 화면이라 지금은 준비 중 자리표시만 보인다. */
export function WithdrawPage() {
  return <ComingSoon title="회원 탈퇴" plannedPhase="2단계" />
}
