/*
 * 관리자 모니터링(SCR-17) 바로가기 주소 모음이다(Task 097, PRD 11절 16번).
 *
 * 주소가 정해진 항목만 채우고(2026-11-01: 대시보드·로그 탐색·알림 규칙·Cloudtype), 비어 있는 항목만 "준비 중"(비활성)으로 보인다.
 * 알림 규칙은 2026-11-01에 Grafana에서 규칙(서버 메모리 높음·지표 끊김)을 만들어 그 목록 주소를 넣었다.
 * 주소가 정해지면 해당 상수 한 줄만 바꾸면 된다. 비밀 값이 아니라 브라우저에 그대로 보이는 링크이므로 코드에 둬도 된다.
 * 단, 토큰·비밀번호가 붙은 주소(예: 로그인 없이 열리는 공유 키가 들어간 주소)는 넣지 않는다.
 * 예외: 대시보드 한 장(ottnavi-overview)의 읽기 전용 공개 링크만 둔다(GRAFANA_PUBLIC_DASHBOARD_URL, 사용자 결정 2026-11-03).
 * 로그 탐색·알림 규칙·Cloudtype 콘솔 주소는 여전히 공개 링크로 만들지 않는다.
 * 데모(DEMO) 계정은 Grafana 로그인이 없어 비공개 링크를 열 수 없으므로 대시보드 줄만 공개 링크로 열리고,
 * 나머지 줄은 "관리자 전용"으로 막아 보인다(DEMO_MONITORING_LINK_URLS, MonitoringLinksCard).
 * 이전: 관리자와 데모 계정이 같은 비공개 링크를 봤다(관측 설계 결정 2026-10-30). 데모는 열 수 없어 바꿨다.
 */

// 바깥 도구 항목을 구분하는 이름. 화면 표시 정보(보고 싶은 것·이름·아이콘)는 monitoringDestinations.ts의 linkKey가 같은 이름으로 묶는다
export type MonitoringLinkKey =
  | 'grafanaDashboard' // Grafana 대시보드(지표)
  | 'grafanaExplore' // Grafana 로그 탐색(Explore)
  | 'grafanaAlertRules' // Grafana 알림 규칙
  | 'cloudtypeConsole' // Cloudtype 콘솔(서버 배포·재시작)

// 항목별 주소. 빈 문자열이면 그 항목만 "준비 중"이다
export type MonitoringLinkUrls = Record<MonitoringLinkKey, string>

// Grafana 대시보드 "ottnavi 운영 개요"(고유 ID ottnavi-overview). 제목 부분을 빼 제목을 바꿔도 주소가 깨지지 않는다. 최근 6시간, 1분마다 새로고침
export const GRAFANA_DASHBOARD_URL =
  'https://ottnavi.grafana.net/d/ottnavi-overview?from=now-6h&to=now&refresh=1m'
// Grafana 로그 탐색(Explore): 경고·오류 로그 쿼리 {app="ottnavi", env="prod", level=~"(?i)warn|error"}가 이미 들어 있고 최근 24시간이다
export const GRAFANA_EXPLORE_URL =
  'https://ottnavi.grafana.net/explore?schemaVersion=1&panes=%7B%22if0%22:%7B%22datasource%22:%22grafanacloud-logs%22,%22queries%22:%5B%7B%22refId%22:%22A%22,%22expr%22:%22%7Bapp%3D%5C%22ottnavi%5C%22,%20env%3D%5C%22prod%5C%22,level%3D~%5C%22%28%3Fi%29warn%7Cerror%5C%22%7D%22,%22queryType%22:%22range%22,%22datasource%22:%7B%22type%22:%22loki%22,%22uid%22:%22grafanacloud-logs%22%7D,%22editorMode%22:%22code%22,%22direction%22:%22backward%22%7D%5D,%22range%22:%7B%22from%22:%22now-24h%22,%22to%22:%22now%22%7D,%22panelsState%22:%7B%22logs%22:%7B%22sortOrder%22:%22Descending%22%7D%7D,%22compact%22:false%7D%7D'
// Grafana 알림 규칙 목록. 폴더 ottnavi의 규칙만 모든 상태(정상·대기·알림 중)로 보인다.
// 이전: 사용자가 복사한 주소의 state:inactive 필터는 "정상" 규칙만 보여 줘 알림이 울려도 목록에서 사라지므로 뺐다(2026-11-01)
export const GRAFANA_ALERT_RULES_URL =
  'https://ottnavi.grafana.net/alerting/list?search=namespace:ottnavi'
export const CLOUDTYPE_CONSOLE_URL = 'https://app.cloudtype.io/@swmoon7/backend:main/ottnavi' // Cloudtype 콘솔(서버 배포·재시작)

// 화면 조립(MonitoringScreen)이 그대로 넘기는 묶음
export const MONITORING_LINK_URLS: MonitoringLinkUrls = {
  grafanaDashboard: GRAFANA_DASHBOARD_URL,
  grafanaExplore: GRAFANA_EXPLORE_URL,
  grafanaAlertRules: GRAFANA_ALERT_RULES_URL,
  cloudtypeConsole: CLOUDTYPE_CONSOLE_URL,
}

// Grafana 대시보드 "ottnavi-overview"의 읽기 전용 공개 링크(로그인 없이 열린다). 시간 범위 옵션은 꺼 둬 최근 6시간으로 고정이다.
// 주소 끝 값은 비밀번호가 아니라 이 대시보드 한 장을 읽기 전용으로 공개하기로 사용자가 정한 공개 식별자다(2026-11-03).
export const GRAFANA_PUBLIC_DASHBOARD_URL =
  'https://ottnavi.grafana.net/public-dashboards/bd1c942d8a4443f696bdd19b9e795303'

// 데모 계정이 보는 묶음: 대시보드만 공개 링크로 바꾸고 나머지는 관리자와 같다(나머지 줄은 화면에서 "관리자 전용"으로 막혀 주소를 쓰지 않는다)
export const DEMO_MONITORING_LINK_URLS: MonitoringLinkUrls = {
  ...MONITORING_LINK_URLS,
  grafanaDashboard: GRAFANA_PUBLIC_DASHBOARD_URL,
}
