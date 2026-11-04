import { useId } from 'react'
import { Link } from 'react-router'
import { ArrowRightIcon, ExternalLinkIcon, HourglassIcon, LinkIcon, LockIcon } from 'lucide-react'
import { cn } from 'cn'
import { Button } from '@/components/ui/button'
import type { MonitoringLinkUrls } from '@/features/admin/components/monitoring/grafanaLink'
import {
  MONITORING_ROWS,
  type MonitoringRow,
} from '@/features/admin/components/monitoring/monitoringDestinations'

/*
 * 눌리지 않는 [준비 중]·[관리자 전용] 버튼의 겉모습(시각 검수 2026-11-03).
 * - 단순한 방법: shadcn Button의 disabled 기본값(opacity-50)을 그대로 쓴다.
 * - 문제점: 글자까지 반투명해져 "관리자 전용"·"준비 중"이 흐려 읽기 어렵다. 이 글자 자체가 "왜 못 여는지"를 알리는 정보라 읽혀야 한다.
 * - 택한 방법: 투명도는 그대로(opacity-100) 두고 글자를 보조색(muted-foreground)으로 낮춘다. 관리자 테마에서 보조색은
 *   흰 카드 위 7.4:1, 옅은 회색 위 6.5:1이라(admin-theme.css 주석) 4.5:1 이상으로 읽힌다.
 *   바탕은 비워(bg-transparent) 줄의 옅은 점선 바탕이 비치게 해, 채워진 [열기] 버튼과 "누를 수 있는 것처럼" 겹쳐 보이지 않게 한다.
 *   outline 변형은 dark: 클래스(앱 전체에서 항상 켜짐, index.css)로 bg-input/30을 칠하므로 dark:bg-transparent도 같이 둔다.
 * 색만으로 구분하지 않는다: 자물쇠·모래시계 아이콘 + 글자 + 줄의 점선 테두리가 함께 상태를 알린다.
 */
const LOCKED_BUTTON_CLASS =
  'bg-transparent text-muted-foreground disabled:opacity-100 dark:bg-transparent'

interface MonitoringLinksCardProps {
  urls: MonitoringLinkUrls // 바깥 도구별 주소. 빈 문자열인 줄만 "준비 중"으로 보인다(보통 MONITORING_LINK_URLS, 데모는 DEMO_MONITORING_LINK_URLS)
  isDemo: boolean // 데모 계정이면 true. 관리자 전용 줄(isAdminOnly)의 [열기]를 비활성 "관리자 전용"으로 바꾼다
}

interface MonitoringRowItemProps {
  row: MonitoringRow // 보고 싶은 것·가는 곳·이동 방식
  url: string // 바깥 도구 줄의 주소(앱 안 줄은 쓰지 않는다)
  notReadyReasonId: string // 준비 중 이유 문장의 id. 비활성 버튼이 aria-describedby로 가리킨다
  adminOnlyReasonId: string // 관리자 전용 안내 문장의 id. 막힌 버튼이 aria-describedby로 가리킨다
  isLockedForDemo: boolean // 이 줄이 데모 계정에게 막혀 있는지(데모 && 관리자 전용 줄)
}

// 줄 오른쪽의 이동 버튼. 앱 안 화면 / 바깥 도구(주소 있음) / 바깥 도구(주소 없음) 세 갈래다
function RowAction({
  row,
  url,
  notReadyReasonId,
  adminOnlyReasonId,
  isLockedForDemo,
}: MonitoringRowItemProps) {
  // 1. 앱 안 화면(수집 탭)은 같은 탭에서 이동하는 링크. 버튼 모양을 다른 줄과 맞춰 오른쪽 칸이 한 줄로 정렬되게 한다
  if (row.kind === 'internal') {
    return (
      <Button asChild variant="outline" className="ml-auto">
        <Link to={row.path}>
          {row.placeLabel}
          <ArrowRightIcon aria-hidden="true" />
        </Link>
      </Button>
    )
  }

  // 2. 데모 계정에게 막힌 줄은 주소가 있어도 [열기]를 만들지 않고 비활성 [관리자 전용](자물쇠 + 글자 + 점선 테두리)을 보인다.
  //    화면에서 막는 것은 편의일 뿐이고 실제로는 Grafana·Cloudtype 로그인이 막는다(데모 계정에는 로그인이 없다).
  //    주소를 DOM에 아예 두지 않아 비공개 링크가 데모 화면에 노출되지 않게도 한다
  if (isLockedForDemo) {
    return (
      <Button
        type="button"
        variant="outline"
        disabled
        aria-describedby={adminOnlyReasonId}
        // className="ml-auto" // 시각 검수(2026-11-03) 전: 원본 disabled:opacity-50 때문에 글자가 흐려 읽기 어려웠다
        className={cn('ml-auto', LOCKED_BUTTON_CLASS)}
      >
        <LockIcon aria-hidden="true" />
        <span className="sr-only">{row.placeLabel}</span> 관리자 전용
      </Button>
    )
  }

  // 3. 바깥 도구에 주소가 있으면 새 탭 링크.
  //    asChild: 버튼 모양이지만 실제 요소는 링크(<a>)라 낭독기에 "링크"로 읽힌다.
  //    rel="noopener noreferrer": 새 탭이 이 화면(window.opener)을 조작하지 못하게 막는다.
  //    보이는 글자는 "열기"뿐이라 낭독기에는 가는 곳 이름을 앞에 붙여 "Grafana 대시보드 열기 (새 탭에서 열림)"으로 읽힌다.
  //    띄어쓰기는 sr-only 칸 밖에 둔다. 칸 안 끝 공백은 이름 계산에서 버려져 "대시보드열기"처럼 붙을 수 있다
  if (url.trim() !== '') {
    return (
      <Button asChild variant="outline" className="ml-auto">
        <a href={url} target="_blank" rel="noopener noreferrer">
          <span className="sr-only">{row.placeLabel}</span> 열기
          <ExternalLinkIcon aria-hidden="true" /> <span className="sr-only">(새 탭에서 열림)</span>
        </a>
      </Button>
    )
  }

  // 4. 주소가 없으면 비활성 [준비 중]. "왜 안 눌리지"가 남지 않게 카드 아래 이유 문장과 aria-describedby로 묶는다
  return (
    <Button
      type="button"
      variant="outline"
      disabled
      aria-describedby={notReadyReasonId}
      // className="ml-auto" // 시각 검수(2026-11-03) 전: "관리자 전용"과 같은 이유로 흐린 글자를 고쳤다
      className={cn('ml-auto', LOCKED_BUTTON_CLASS)}
    >
      <HourglassIcon aria-hidden="true" />
      <span className="sr-only">{row.placeLabel}</span> 준비 중
    </Button>
  )
}

// 한 줄. 왼쪽 아이콘 칸, 가운데 보고 싶은 것(굵게) + 가는 곳 이름, 오른쪽(좁은 화면에서는 아래) 이동 버튼
function MonitoringRowItem(props: MonitoringRowItemProps) {
  const { row, url, isLockedForDemo } = props
  // 막힌 줄은 "준비 중"이 아니다(주소는 있고 권한만 없다). 두 표시가 겹치지 않게 먼저 거른다
  const isNotReady = row.kind === 'external' && !isLockedForDemo && url.trim() === ''
  const Icon = row.icon

  return (
    <li
      className={cn(
        'flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border p-3',
        // 준비 중·관리자 전용은 점선 테두리 + 옅은 바탕으로 모양부터 다르게 한다(버튼의 모래시계·자물쇠·글자와 함께 써서 색만으로 구분하지 않는다)
        isNotReady || isLockedForDemo
          ? 'border-dashed border-input bg-muted/40'
          : 'border-solid border-border',
      )}
    >
      {/* 아이콘 칸은 가는 곳을 모양으로 알리는 장식이라 낭독기에는 숨긴다(이름은 바로 옆 글자로 읽힌다) */}
      <span
        aria-hidden="true"
        className="grid size-9 shrink-0 place-items-center rounded-md bg-muted text-muted-foreground"
      >
        <Icon className="size-4.5" />
      </span>

      {/* basis-48: 375px에서 버튼이 글자 옆에 끼어 문장이 잘게 꺾이지 않고, 자리가 모자라면 버튼이 아래 줄로 내려간다 */}
      <div className="grid min-w-0 flex-1 basis-48 gap-0.5">
        <p className="font-medium break-keep">{row.topic}</p>
        <p className="text-xs break-keep text-muted-foreground">{row.placeLabel}</p>
      </div>

      <RowAction {...props} />
    </li>
  )
}

/**
 * 관리자 모니터링(SCR-17)의 "무엇을 어디서 보나" 바로가기 카드다(Task 097).
 *
 * 서버 지표·로그·알림은 Grafana Cloud에서, 배포·재시작은 Cloudtype에서 하므로 이 화면에 다시 그리지 않고
 * 그곳으로 가는 입구만 모은다(PRD 11절 16번 추천안). 처음에는 바로가기와 길잡이를 두 카드로 나눴으나
 * 같은 곳이 두 번 나와 한 카드로 합쳤다(2026-10-31 사용자 결정). 한 줄에 "보고 싶은 것 → 가는 곳 → 버튼"이 모두 있다.
 *
 * 바깥 도구 줄은 주소를 따로 본다. 비어 있는 줄만 비활성 [준비 중](모래시계 + 글자 + 점선 테두리)이고,
 * 이유 문장은 줄마다 되풀이하지 않고 카드 아래 한 번만 쓴 뒤 비활성 버튼들이 모두 그 문장을 가리킨다.
 *
 * 데모(DEMO) 계정(isDemo)은 대시보드 줄만 공개 링크로 열리고, 로그·알림 규칙·콘솔 줄은 비활성 [관리자 전용]
 * (자물쇠 + 글자 + 점선 테두리)이다(2026-11-03 사용자 결정). 안내 문장은 카드 설명 아래에 한 번 쓰고 막힌 버튼이 가리킨다.
 * 막는 것은 표시 편의이고, 실제 권한은 Grafana·Cloudtype 로그인이 정한다.
 */
export function MonitoringLinksCard({ urls, isDemo }: MonitoringLinksCardProps) {
  // useId: 이 카드가 여러 번 그려져도 id가 겹치지 않게 한다(aria-labelledby·aria-describedby 연결용)
  const headingId = useId()
  const notReadyReasonId = useId()
  const adminOnlyReasonId = useId()
  // 데모 계정에게 막힌 줄인지. 막힌 줄은 주소 유무와 상관없이 "관리자 전용"이라 "준비 중" 안내 계산에서 뺀다
  const isLocked = (row: MonitoringRow) => isDemo && row.kind === 'external' && row.isAdminOnly
  const hasNotReady = MONITORING_ROWS.some(
    (row) => row.kind === 'external' && !isLocked(row) && urls[row.linkKey].trim() === '',
  )

  return (
    // 다른 관리자 카드와 같은 상자 모양(rounded-xl border bg-card p-4)
    <section
      aria-labelledby={headingId}
      className="grid content-start gap-3 rounded-xl border border-border bg-card p-4 text-sm"
    >
      <div className="grid gap-1">
        <h2 id={headingId} className="flex items-center gap-2 text-lg font-semibold">
          <LinkIcon aria-hidden="true" className="size-5 shrink-0 text-muted-foreground" />
          무엇을 어디서 보나
        </h2>
        <p className="break-keep text-muted-foreground">
          보고 싶은 것에 따라 찾아갈 곳입니다. 바깥 도구는 새 탭에서 열립니다.
        </p>
        {/* 데모 계정 안내: 막힌 줄 버튼이 aria-describedby로 이 문장을 가리켜 "왜 안 눌리지"가 남지 않는다.
            시각 검수(2026-11-03): 알림 띠(Alert)로 키우지 않고 자물쇠 아이콘 한 줄로 둔다. 데모 화면 위에는 이미
            전역 "데모 계정으로 둘러보는 중" 띠와 관리자 "조회 전용" 띠가 있어 띠를 하나 더 두면 안내가 세 겹이 된다.
            대신 자물쇠 아이콘을 줄의 [관리자 전용] 버튼과 같은 모양으로 맞춰 "이 문장이 저 버튼들의 이유"임을 눈으로 잇는다
            (공용 DemoReadOnlyHint inline과 같은 모양. 그 컴포넌트는 로그인 역할을 직접 읽어 isDemo 속성과 어긋날 수 있어 쓰지 않았다) */}
        {/* 이전 모양(단락만):
        {isDemo && (
          <p id={adminOnlyReasonId} className="break-keep text-muted-foreground">
            데모 계정은 지표 대시보드만 볼 수 있습니다. 로그·알림 규칙·서버 콘솔은 보안상 관리자
            계정으로만 열 수 있습니다.
          </p>
        )} */}
        {isDemo && (
          <p id={adminOnlyReasonId} className="flex items-start gap-1.5 break-keep text-foreground">
            {/* mt-0.5: 아이콘(16px)을 첫 줄 글자 높이(20px)의 가운데에 맞춘다. 두 줄로 꺾여도 아이콘은 첫 줄에 남는다 */}
            <LockIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
            <span>
              데모 계정은 지표 대시보드만 볼 수 있습니다. 로그·알림 규칙·서버 콘솔은 보안상 관리자
              계정으로만 열 수 있습니다.
            </span>
          </p>
        )}
      </div>

      <ul className="grid gap-2">
        {MONITORING_ROWS.map((row) => (
          <MonitoringRowItem
            key={row.topic}
            row={row}
            url={row.kind === 'external' ? urls[row.linkKey] : ''}
            notReadyReasonId={notReadyReasonId}
            adminOnlyReasonId={adminOnlyReasonId}
            isLockedForDemo={isLocked(row)}
          />
        ))}
      </ul>

      {hasNotReady && (
        <p id={notReadyReasonId} className="text-xs break-keep text-muted-foreground">
          준비 중인 항목은 주소가 아직 등록되지 않았습니다. 등록되면 이 자리에서 바로 열 수
          있습니다.
        </p>
      )}
    </section>
  )
}
