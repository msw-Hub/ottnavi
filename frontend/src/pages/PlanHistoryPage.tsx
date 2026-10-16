// import { ComingSoon } from '@/components/common/ComingSoon'
import { PlanHistoryScreen } from '@/features/plan/components/PlanHistoryScreen'

/**
 * SCR-11 플랜 변경 내역(`/plan/history`, 로그인 필요). Task 068 1차안(정적 샘플)이다.
 * 화면 조립은 features/plan에 있고, 이 파일은 연결만 한다(pages는 얇게, frontend.md).
 * 이전: 2단계 화면이라 준비 중 자리표시만 보였다 → return <ComingSoon title="플랜 변경 내역" plannedPhase="2단계" />
 */
export function PlanHistoryPage() {
  return <PlanHistoryScreen />
}
