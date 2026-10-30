import { MonitoringScreenView } from '@/features/admin/components/monitoring/MonitoringScreenView'
import { MONITORING_LINK_URLS } from '@/features/admin/components/monitoring/grafanaLink'

/**
 * SCR-17 관리자 모니터링 화면의 조립 컴포넌트다(Task 097).
 *
 * 지금은 조회할 서버 데이터가 없어 바로가기 주소 묶음만 넘긴다.
 * 수집 실행 요약(useCollectExecutions + CollectRunSummary)은 수집 탭과 겹쳐 보류했다.
 */
export function MonitoringScreen() {
  return <MonitoringScreenView urls={MONITORING_LINK_URLS} />
}
