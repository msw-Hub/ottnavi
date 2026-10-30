import { MonitoringLinksCard } from '@/features/admin/components/monitoring/MonitoringLinksCard'
import type { MonitoringLinkUrls } from '@/features/admin/components/monitoring/grafanaLink'
// 이전: Grafana 대시보드 하나만 있던 카드(GrafanaDashboardCard). 바로가기 목록 MonitoringLinksCard로 넓히며 파일을 대체했다(2026-10-31)
// 이전: 바로가기와 길잡이(MonitoringGuideCard)를 두 칸으로 나눴으나 같은 곳이 두 번 나와 MonitoringLinksCard 한 카드로 합쳤다(2026-10-31 사용자 결정)
// 보류: 수집 실행 요약은 수집 탭(/admin/collect)과 내용이 겹쳐 화면에서 뺐다(2026-10-31 사용자 결정). 파일은 남겨 둔다
// import {
//   CollectRunSummary,
//   type CollectRunSummaryProps,
// } from '@/features/admin/components/monitoring/CollectRunSummary'

interface MonitoringScreenViewProps {
  urls: MonitoringLinkUrls // 바깥 도구별 주소(빈 문자열인 줄은 준비 중). 보통 MONITORING_LINK_URLS
}

/**
 * SCR-17 관리자 모니터링 화면의 배치만 맡는 껍데기다(Task 097).
 *
 * 서버 상태·로그·알림·배포는 바깥 도구(Grafana Cloud, Cloudtype)에서 보므로 이 화면은
 * "무엇을 어디서 보나" 카드 하나에 가는 곳과 이동 버튼을 모은다. 폭은 다른 관리자 화면처럼 전체 폭을 쓴다.
 */
export function MonitoringScreenView({ urls }: MonitoringScreenViewProps) {
  return (
    <section className="flex flex-col gap-5">
      <div className="grid gap-1">
        <h1 className="text-2xl font-semibold">모니터링</h1>
        <p className="text-sm break-keep text-muted-foreground">
          서버 지표·로그·알림은 Grafana에서, 배포·재시작은 Cloudtype에서 합니다. 수집 실행 결과는
          수집 탭에서 확인하세요.
        </p>
      </div>

      <MonitoringLinksCard urls={urls} />
    </section>
  )
}
