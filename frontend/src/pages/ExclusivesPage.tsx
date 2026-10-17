import { ComingSoon } from '@/components/common/ComingSoon'
import { ExclusivesScreen } from '@/features/exclusives/components/ExclusivesScreen'
import { isMockMode } from '@/lib/mockScenario'

/**
 * SCR-03 서비스별 독점 목록(`/exclusives`, 공개). Task 075 1차안(정적 샘플)이다.
 * 화면 조립은 features/exclusives에 있고, 이 파일은 연결만 한다(pages는 얇게, frontend.md).
 *
 * 운영(목업이 아닌 환경)에서는 아직 독점 목록 API가 없어 준비 중 화면을 보인다(변경 내역 화면 PlanHistoryPage와 같은 방침).
 * 정적 샘플은 목업 모드에서만 보여, 운영 방문자에게 지어낸 독점 목록이 나가지 않는다. 실제 API를 연결하는 Task 079에서 이 분기를 없앤다.
 * 이전: 2단계 화면이라 준비 중 자리표시만 보였다 → return <ComingSoon title="독점 작품 목록" plannedPhase="2단계" />
 */
export function ExclusivesPage() {
  if (!isMockMode()) {
    return <ComingSoon title="독점 작품 목록" plannedPhase="2단계" />
  }
  return <ExclusivesScreen />
}
