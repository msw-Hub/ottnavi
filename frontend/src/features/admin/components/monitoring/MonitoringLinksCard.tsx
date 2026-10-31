import { useId } from 'react'
import { Link } from 'react-router'
import { ArrowRightIcon, ExternalLinkIcon, HourglassIcon, LinkIcon } from 'lucide-react'
import { cn } from 'cn'
import { Button } from '@/components/ui/button'
import type { MonitoringLinkUrls } from '@/features/admin/components/monitoring/grafanaLink'
import {
  MONITORING_ROWS,
  type MonitoringRow,
} from '@/features/admin/components/monitoring/monitoringDestinations'

interface MonitoringLinksCardProps {
  urls: MonitoringLinkUrls // 바깥 도구별 주소. 빈 문자열인 줄만 "준비 중"으로 보인다(보통 MONITORING_LINK_URLS)
}

interface MonitoringRowItemProps {
  row: MonitoringRow // 보고 싶은 것·가는 곳·이동 방식
  url: string // 바깥 도구 줄의 주소(앱 안 줄은 쓰지 않는다)
  notReadyReasonId: string // 준비 중 이유 문장의 id. 비활성 버튼이 aria-describedby로 가리킨다
}

// 줄 오른쪽의 이동 버튼. 앱 안 화면 / 바깥 도구(주소 있음) / 바깥 도구(주소 없음) 세 갈래다
function RowAction({ row, url, notReadyReasonId }: MonitoringRowItemProps) {
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

  // 2. 바깥 도구에 주소가 있으면 새 탭 링크.
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

  // 3. 주소가 없으면 비활성 [준비 중]. "왜 안 눌리지"가 남지 않게 카드 아래 이유 문장과 aria-describedby로 묶는다
  return (
    <Button
      type="button"
      variant="outline"
      disabled
      aria-describedby={notReadyReasonId}
      className="ml-auto"
    >
      <HourglassIcon aria-hidden="true" />
      <span className="sr-only">{row.placeLabel}</span> 준비 중
    </Button>
  )
}

// 한 줄. 왼쪽 아이콘 칸, 가운데 보고 싶은 것(굵게) + 가는 곳 이름, 오른쪽(좁은 화면에서는 아래) 이동 버튼
function MonitoringRowItem({ row, url, notReadyReasonId }: MonitoringRowItemProps) {
  const isNotReady = row.kind === 'external' && url.trim() === ''
  const Icon = row.icon

  return (
    <li
      className={cn(
        'flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border p-3',
        // 준비 중은 점선 테두리 + 옅은 바탕으로 모양부터 다르게 한다(버튼의 모래시계·글자와 함께 써서 색만으로 구분하지 않는다)
        isNotReady ? 'border-dashed border-input bg-muted/40' : 'border-solid border-border',
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

      <RowAction row={row} url={url} notReadyReasonId={notReadyReasonId} />
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
 */
export function MonitoringLinksCard({ urls }: MonitoringLinksCardProps) {
  // useId: 이 카드가 여러 번 그려져도 id가 겹치지 않게 한다(aria-labelledby·aria-describedby 연결용)
  const headingId = useId()
  const notReadyReasonId = useId()
  const hasNotReady = MONITORING_ROWS.some(
    (row) => row.kind === 'external' && urls[row.linkKey].trim() === '',
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
      </div>

      <ul className="grid gap-2">
        {MONITORING_ROWS.map((row) => (
          <MonitoringRowItem
            key={row.topic}
            row={row}
            url={row.kind === 'external' ? urls[row.linkKey] : ''}
            notReadyReasonId={notReadyReasonId}
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
