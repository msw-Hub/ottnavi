import { NotificationSummary } from '@/features/admin/components/collect/NotificationSummary'
import { MonitoringScreenView } from '@/features/admin/components/monitoring/MonitoringScreenView'
import { MONITORING_LINK_URLS } from '@/features/admin/components/monitoring/grafanaLink'
import { useAdminUserStats } from '@/features/admin/hooks/useAdminUserStats'
import { useAdminVisitorStats } from '@/features/admin/hooks/useAdminVisitorStats'

/**
 * SCR-17 관리자 모니터링 화면의 조립 컴포넌트다(Task 097).
 *
 * 회원 현황·방문자 수를 여기서 조회해 카드(MonitoringScreenView)에 넘기고, 메일 발송 카드는 수집 화면과 같은
 * <NotificationSummary />를 그대로 쓴다. NotificationSummary가 자체 조회를 하고 쿼리 키도 수집 화면과 같아서
 * 두 탭을 오가도 캐시를 나눠 쓴다.
 * 회원 현황과 방문자 수를 훅 둘로 나눈 이유: 방문 통계는 외부(Google)에 기대 느리거나 실패할 수 있어,
 * 한쪽이 늦거나 실패해도 다른 카드는 바로 숫자를 보이게 하려는 것이다.
 * 수집 실행 요약(useCollectExecutions + CollectRunSummary)은 수집 탭과 겹쳐 보류했다.
 */
export function MonitoringScreen() {
  const userStats = useAdminUserStats()
  const visitorStats = useAdminVisitorStats()

  return (
    <MonitoringScreenView
      urls={MONITORING_LINK_URLS}
      overview={{
        userStats: {
          status: userStats.status,
          stats: userStats.data,
          error: userStats.error,
          onRetry: () => void userStats.refetch(),
        },
        visitorStats: {
          status: visitorStats.status,
          stats: visitorStats.data,
          error: visitorStats.error,
          onRetry: () => void visitorStats.refetch(),
        },
        notificationSummary: <NotificationSummary />,
      }}
    />
  )
}
