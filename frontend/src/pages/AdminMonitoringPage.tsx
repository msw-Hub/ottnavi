import { PageTitle } from '@/components/common/PageTitle'
import { MonitoringScreen } from '@/features/admin/components/monitoring/MonitoringScreen'

/**
 * SCR-17 관리자 모니터링(`/admin/monitoring`, 관리자). 화면 조립은 features/admin이 맡는다(Task 097).
 * 이전: 3단계 화면이라 준비 중 자리표시(ComingSoon title="모니터링" plannedPhase="3단계")만 보였다.
 */
export function AdminMonitoringPage() {
  return (
    <>
      <PageTitle name="[관리] 모니터링" />
      <MonitoringScreen />
    </>
  )
}
