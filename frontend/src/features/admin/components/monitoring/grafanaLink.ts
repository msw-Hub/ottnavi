/*
 * 관리자 모니터링(SCR-17) 바로가기 주소 모음이다(Task 097, PRD 11절 16번).
 *
 * 아직 주소가 정해지지 않아 모두 빈 문자열로 둔다. 항목마다 따로 판단해, 비어 있는 항목만 "준비 중"(비활성)으로 보인다.
 * 주소가 정해지면 해당 상수 한 줄만 바꾸면 된다. 비밀 값이 아니라 브라우저에 그대로 보이는 링크이므로 코드에 둬도 된다.
 * 단, 토큰·비밀번호가 붙은 주소(예: 로그인 없이 열리는 공유 키가 들어간 주소)는 넣지 않는다.
 * 데모(DEMO) 계정에도 같은 링크가 보인다(관측 설계 결정 2026-10-30).
 */

// 바깥 도구 항목을 구분하는 이름. 화면 표시 정보(보고 싶은 것·이름·아이콘)는 monitoringDestinations.ts의 linkKey가 같은 이름으로 묶는다
export type MonitoringLinkKey =
  | 'grafanaDashboard' // Grafana 대시보드(지표)
  | 'grafanaExplore' // Grafana 로그 탐색(Explore)
  | 'grafanaAlertRules' // Grafana 알림 규칙
  | 'cloudtypeConsole' // Cloudtype 콘솔(서버 배포·재시작)

// 항목별 주소. 빈 문자열이면 그 항목만 "준비 중"이다
export type MonitoringLinkUrls = Record<MonitoringLinkKey, string>

export const GRAFANA_DASHBOARD_URL = '' // Grafana 대시보드(서버 지표)
export const GRAFANA_EXPLORE_URL = '' // Grafana 로그 탐색(Explore)
export const GRAFANA_ALERT_RULES_URL = '' // Grafana 알림 규칙 목록
export const CLOUDTYPE_CONSOLE_URL = '' // Cloudtype 콘솔(서버 배포·재시작)

// 화면 조립(MonitoringScreen)이 그대로 넘기는 묶음
export const MONITORING_LINK_URLS: MonitoringLinkUrls = {
  grafanaDashboard: GRAFANA_DASHBOARD_URL,
  grafanaExplore: GRAFANA_EXPLORE_URL,
  grafanaAlertRules: GRAFANA_ALERT_RULES_URL,
  cloudtypeConsole: CLOUDTYPE_CONSOLE_URL,
}
