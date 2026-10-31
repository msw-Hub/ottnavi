import type { ReactNode } from 'react'
import { MailIcon } from 'lucide-react'
import { MonitoringLinksCard } from '@/features/admin/components/monitoring/MonitoringLinksCard'
import type { MonitoringLinkUrls } from '@/features/admin/components/monitoring/grafanaLink'
import { StatsCardFrame } from '@/features/admin/components/monitoring/StatsCardFrame'
import {
  UserStatsCard,
  type UserStatsCardProps,
} from '@/features/admin/components/monitoring/UserStatsCard'
import {
  VisitorStatsCard,
  type VisitorStatsCardProps,
} from '@/features/admin/components/monitoring/VisitorStatsCard'
// 이전: Grafana 대시보드 하나만 있던 카드(GrafanaDashboardCard). 바로가기 목록 MonitoringLinksCard로 넓히며 파일을 대체했다(2026-10-31)
// 이전: 바로가기와 길잡이(MonitoringGuideCard)를 두 칸으로 나눴으나 같은 곳이 두 번 나와 MonitoringLinksCard 한 카드로 합쳤다(2026-10-31 사용자 결정)
// 보류: 수집 실행 요약은 수집 탭(/admin/collect)과 내용이 겹쳐 화면에서 뺐다(2026-10-31 사용자 결정). 파일은 남겨 둔다
// import {
//   CollectRunSummary,
//   type CollectRunSummaryProps,
// } from '@/features/admin/components/monitoring/CollectRunSummary'

// "운영 현황" 카드 3개에 넘길 값 묶음. 조회는 화면 조립 쪽(MonitoringScreen)이 하고 여기서는 그리기만 한다
export interface MonitoringOverviewProps {
  userStats: UserStatsCardProps // 회원 현황 카드(조회 상태 + AdminUserStats)
  visitorStats: Omit<VisitorStatsCardProps, 'className'> // 방문자 카드(조회 상태 + AdminVisitorStats). 칸 넓이는 이 화면이 정한다
  notificationSummary: ReactNode // 메일 발송 카드 본문. 수집 화면의 <NotificationSummary />를 그대로 넘긴다(자체 조회·로딩·오류 포함)
}

interface MonitoringScreenViewProps {
  urls: MonitoringLinkUrls // 바깥 도구별 주소(빈 문자열인 줄은 준비 중). 보통 MONITORING_LINK_URLS
  overview?: MonitoringOverviewProps // 없으면 "운영 현황" 줄을 그리지 않는다(바로가기만 있던 기존 화면·테스트 그대로)
}

/**
 * SCR-17 관리자 모니터링 화면의 배치만 맡는 껍데기다(Task 097).
 *
 * 위에서 아래로: 제목 → "운영 현황"(회원·메일·방문자 숫자 카드 3개) → "무엇을 어디서 보나" 바로가기.
 * 숫자 카드를 위에 둔 이유: 들어오자마자 "지금 서비스가 어떤가"를 읽고, 자세히 볼 일이 있을 때 아래 바로가기로 간다.
 *
 * 운영 현황 격자: 375px~ 1열로 쌓임 → md(768px~) 2열(회원·메일 나란히, 방문자는 한 줄 전체) → xl(1280px~) 3열 한 줄.
 * 2열에서 방문자 카드를 한 줄 전체로 늘리는 이유: 3번째 카드가 왼쪽 반 칸에만 남으면 오른쪽이 비어 정렬이 어긋나 보인다.
 * 한 줄 전체일 때는 방문자 카드 안의 숫자 4칸도 한 줄로 펼쳐진다(VisitorStatsCard의 @container).
 */
export function MonitoringScreenView({ urls, overview }: MonitoringScreenViewProps) {
  return (
    <section className="flex flex-col gap-5">
      <div className="grid gap-1">
        <h1 className="text-2xl font-semibold">모니터링</h1>
        <p className="text-sm break-keep text-muted-foreground">
          서버 지표·로그·알림은 Grafana에서, 배포·재시작은 Cloudtype에서 합니다. 수집 실행 결과는
          수집 탭에서 확인하세요.
        </p>
      </div>

      {overview && (
        <section aria-labelledby="monitoring-overview-heading" className="grid gap-3">
          <h2 id="monitoring-overview-heading" className="text-lg font-semibold">
            운영 현황
          </h2>
          {/* 카드 높이는 같은 줄끼리 맞춰진다(격자 기본 stretch). min-w-0은 카드 안 긴 글이 칸을 밀어 가로 스크롤을 만들지 않게 한다 */}
          <div className="grid gap-4 *:min-w-0 md:grid-cols-2 xl:grid-cols-3">
            <UserStatsCard {...overview.userStats} />

            {/* 메일 발송 카드: 수집 화면의 NotificationSummary(대기·발송·실패 알약 + 마지막 발송)를 그대로 쓰고 상자와 안내만 이 화면에서 씌운다 */}
            <StatsCardFrame
              title="알림 메일 발송"
              icon={MailIcon}
              description="숫자는 받는 사람 수가 아니라 메일 건수입니다. 대기는 아직 보내지 않았거나 다시 보내려고 기다리는 메일입니다."
            >
              {overview.notificationSummary}
            </StatsCardFrame>

            <VisitorStatsCard {...overview.visitorStats} className="md:col-span-2 xl:col-span-1" />
          </div>
        </section>
      )}

      <MonitoringLinksCard urls={urls} />
    </section>
  )
}
