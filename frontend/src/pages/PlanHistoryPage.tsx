import { ComingSoon } from '@/components/common/ComingSoon'
import { PlanHistoryScreen } from '@/features/plan/components/PlanHistoryScreen'
import { isMockMode } from '@/lib/mockScenario'

/**
 * SCR-11 플랜 변경 내역(`/plan/history`, 로그인 필요). Task 068 1차안(정적 샘플)이다.
 * 화면 조립은 features/plan에 있고, 이 파일은 연결만 한다(pages는 얇게, frontend.md).
 *
 * 운영(목업이 아닌 환경)에서는 아직 변경 내역 API가 없어 준비 중 화면을 보인다(CodeRabbit 지적 반영).
 * 정적 샘플은 목업 모드에서만 보여, 운영 사용자에게 지어낸 변경 내역이 나가지 않는다. 실제 API를 연결하는 Task 070에서 이 분기를 없앤다.
 * 이전: 2단계 화면이라 준비 중 자리표시만 보였다 → return <ComingSoon title="플랜 변경 내역" plannedPhase="2단계" />
 */
export function PlanHistoryPage() {
  if (!isMockMode()) {
    return <ComingSoon title="플랜 변경 내역" plannedPhase="2단계" />
  }
  return <PlanHistoryScreen />
}
