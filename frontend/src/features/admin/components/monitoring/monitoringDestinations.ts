import {
  BellRingIcon,
  DatabaseIcon,
  LayoutDashboardIcon,
  ScrollTextIcon,
  ServerIcon,
  type LucideIcon,
} from 'lucide-react'
import type { MonitoringLinkKey } from '@/features/admin/components/monitoring/grafanaLink'

/*
 * 모니터링 화면의 "무엇을 어디서 보나" 줄 목록이다(Task 097).
 * 한 줄 = 보고 싶은 것(topic) + 가는 곳(이름·아이콘) + 이동 버튼.
 * 큰 틀만 적는다. 대시보드 패널 이름은 Task 098에서 정해지므로 여기에 단정해 쓰지 않는다.
 */

// 줄마다 같이 쓰는 표시 정보
interface MonitoringRowBase {
  topic: string // 보고 싶은 것(예: "오류·경고 로그")
  placeLabel: string // 가는 곳 이름(예: "Grafana 로그 탐색"). 버튼의 낭독기 이름에도 쓴다
  icon: LucideIcon // 가는 곳을 모양으로 구분하는 아이콘(장식이라 낭독기에는 숨긴다)
}

// 바깥 도구 줄: 주소 묶음(MonitoringLinkUrls)에서 linkKey로 주소를 꺼내 새 탭으로 연다. 주소가 비면 그 줄만 준비 중
export interface MonitoringExternalRow extends MonitoringRowBase {
  kind: 'external'
  linkKey: MonitoringLinkKey
}

// 앱 안 화면 줄: 같은 탭에서 이동한다(예: 수집 탭)
export interface MonitoringInternalRow extends MonitoringRowBase {
  kind: 'internal'
  path: string
}

export type MonitoringRow = MonitoringExternalRow | MonitoringInternalRow

// 줄 순서: 자주 보는 순(지표 → 로그 → 알림 → 배포 → 수집)
export const MONITORING_ROWS: MonitoringRow[] = [
  {
    kind: 'external',
    topic: '서버 메모리·DB 연결 풀·응답 시간',
    placeLabel: 'Grafana 대시보드',
    icon: LayoutDashboardIcon,
    linkKey: 'grafanaDashboard',
  },
  {
    kind: 'external',
    topic: '오류·경고 로그',
    placeLabel: 'Grafana 로그 탐색',
    icon: ScrollTextIcon,
    linkKey: 'grafanaExplore',
  },
  {
    kind: 'external',
    topic: '이상 발생 알림',
    placeLabel: 'Grafana 알림 규칙',
    icon: BellRingIcon,
    linkKey: 'grafanaAlertRules',
  },
  {
    kind: 'external',
    topic: '서버 배포·재시작',
    placeLabel: 'Cloudtype 콘솔',
    icon: ServerIcon,
    linkKey: 'cloudtypeConsole',
  },
  {
    kind: 'internal',
    topic: '수집 실행 결과',
    placeLabel: '수집 탭',
    icon: DatabaseIcon,
    path: '/admin/collect',
  },
]
