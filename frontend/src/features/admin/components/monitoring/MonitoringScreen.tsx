import { NotificationSummary } from '@/features/admin/components/collect/NotificationSummary'
import { MonitoringScreenView } from '@/features/admin/components/monitoring/MonitoringScreenView'
import {
  DEMO_MONITORING_LINK_URLS,
  MONITORING_LINK_URLS,
} from '@/features/admin/components/monitoring/grafanaLink'
import { useAdminUserStats } from '@/features/admin/hooks/useAdminUserStats'
import { useAdminVisitorStats } from '@/features/admin/hooks/useAdminVisitorStats'
import { useIsDemo } from '@/hooks/useIsDemo'

/**
 * SCR-17 관리자 모니터링 화면의 조립 컴포넌트다(Task 097).
 *
 * 회원 현황·방문자 수를 여기서 조회해 카드(MonitoringScreenView)에 넘기고, 메일 발송 카드는 수집 화면과 같은
 * <NotificationSummary />를 그대로 쓴다. NotificationSummary가 자체 조회를 하고 쿼리 키도 수집 화면과 같아서
 * 두 탭을 오가도 캐시를 나눠 쓴다.
 * 회원 현황과 방문자 수를 훅 둘로 나눈 이유: 방문 통계는 외부(Google)에 기대 느리거나 실패할 수 있어,
 * 한쪽이 늦거나 실패해도 다른 카드는 바로 숫자를 보이게 하려는 것이다.
 * 바깥 도구 주소는 역할로 고른다: 관리자는 비공개 링크 묶음, 데모는 대시보드만 공개 링크인 묶음이다(사용자 결정 2026-11-03).
 * 이 구분은 화면 편의일 뿐이다. 로그·알림·콘솔을 실제로 막는 것은 각 서비스(Grafana·Cloudtype)의 로그인이고, 데모에는 그 계정이 없다.
 * 수집 실행 요약(useCollectExecutions + CollectRunSummary)은 수집 탭과 겹쳐 보류했다.
 */
export function MonitoringScreen() {
  const isDemo = useIsDemo()
  const userStats = useAdminUserStats()
  const visitorStats = useAdminVisitorStats()

  return (
    <MonitoringScreenView
      urls={isDemo ? DEMO_MONITORING_LINK_URLS : MONITORING_LINK_URLS}
      isDemo={isDemo}
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
