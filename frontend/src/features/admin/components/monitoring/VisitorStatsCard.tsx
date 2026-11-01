import type { ReactNode } from 'react'
import { CloudOffIcon, EyeIcon, RefreshCwIcon, UnplugIcon } from 'lucide-react'
import { ErrorState } from '@/components/common/ErrorState'
import { Button } from '@/components/ui/button'
import { AdminStatusPill } from '@/features/admin/components/AdminStatusPill'
import {
  StatsCardFrame,
  StatsCardSkeleton,
  StatTile,
} from '@/features/admin/components/monitoring/StatsCardFrame'
import { formatCountWithUnit } from '@/lib/format'
import type { AdminVisitorStats } from '@/api/generated/model'

export interface VisitorStatsCardProps {
  status: 'pending' | 'error' | 'success' // 조회 상태(TanStack Query의 status). 방문 통계 쪽 실패는 200 + stats.status로 온다
  stats?: AdminVisitorStats // status가 'success'일 때의 방문자 수(GET /api/admin/visitor-stats 계약의 data)
  error?: unknown // status가 'error'일 때의 원인(우리 서버 요청 자체가 실패한 경우)
  onRetry?: () => void // [다시 시도]. 요청 실패(error)와 방문 통계 일시 오류(UNAVAILABLE) 둘 다에 쓴다
  className?: string // 격자에서 칸을 넓힐 때만 쓴다(MonitoringScreenView가 정한다)
}

interface NoticeProps {
  onRetry?: () => void
}

// DISABLED: 서버에 방문 통계 연동 설정이 없다. 오류가 아니라 "아직 준비 중"이라 점선(neutral) 알약 + 플러그 빠진 아이콘으로 보인다
function DisabledNotice() {
  return (
    <div className="grid justify-items-start gap-2 rounded-lg border border-dashed border-input bg-muted/40 p-3">
      <AdminStatusPill icon={UnplugIcon} label="연동 안 됨" tone="neutral" />
      <p className="break-keep text-muted-foreground">
        서버에 방문 통계(Google Analytics) 연동 설정이 아직 없습니다. 연동을 마치면 이 자리에 방문자
        수가 보입니다.
      </p>
    </div>
  )
}

// UNAVAILABLE: 방문 통계 조회가 잠깐 실패했다. 이 카드만의 일이라 다른 카드는 정상이라는 점을 함께 알린다.
// 노랑(attention) 알약 + 구름 끊김 아이콘 + 글자로 알려, 연동 안 됨(점선 회색)과 모양부터 다르게 한다
function UnavailableNotice({ onRetry }: NoticeProps) {
  return (
    <div className="grid justify-items-start gap-2 rounded-lg border border-attention-border bg-attention-bg/40 p-3">
      <AdminStatusPill icon={CloudOffIcon} label="지금 불러올 수 없음" tone="attention" />
      <p className="break-keep text-muted-foreground">
        방문 통계에서 값을 받아 오지 못했습니다. 잠시 뒤 다시 확인하세요. 다른 카드의 숫자는 영향이
        없습니다.
      </p>
      {onRetry && (
        <Button type="button" variant="outline" onClick={onRetry}>
          <RefreshCwIcon aria-hidden="true" />
          다시 시도
        </Button>
      )}
    </div>
  )
}

// "잠정" 작은 태그. 오늘 숫자는 집계가 늦게 반영돼 늘어날 수 있다는 뜻을 이름 옆 글자로 알린다(색에 기대지 않는다)
function ProvisionalTag() {
  return (
    <span className="rounded-sm border border-dashed border-input px-1 text-xs leading-4 font-medium">
      잠정
    </span>
  )
}

/**
 * 관리자 모니터링(SCR-17) "운영 현황"의 방문자 카드다. 데이터는 받지 않고 그리기만 한다.
 *
 * 제목을 "동의한 방문자 수"로 둔 이유: 방문 통계는 동의한 방문자만 세므로 실제 방문자보다 적다.
 * 그냥 "방문자 수"라고 쓰면 실제 방문자처럼 읽혀 숫자를 과하게 믿게 되므로 제목에서부터 범위를 밝힌다.
 *
 * 방문 통계 쪽 상태(stats.status) 세 가지를 모양·글자로 나눈다(색만으로 구분하지 않는다).
 * - OK: 숫자 칸 4개(오늘·어제·최근 7일·최근 30일). 오늘에는 "잠정" 태그.
 * - DISABLED: 점선 회색 상자 + [연동 안 됨] 알약 + 준비 중 안내.
 * - UNAVAILABLE: 노란 상자 + [지금 불러올 수 없음] 알약 + 일시 오류 안내 + [다시 시도].
 * 우리 서버 요청 자체가 실패한 경우(status 'error')는 다른 카드와 같은 ErrorState다.
 */
export function VisitorStatsCard({
  status,
  stats,
  error,
  onRetry,
  className,
}: VisitorStatsCardProps) {
  const isOk = status === 'success' && stats?.status === 'OK'

  let body: ReactNode
  if (status === 'pending') {
    body = <StatsCardSkeleton label="방문자 수를 불러오는 중입니다" tileCount={4} />
  } else if (status === 'error' || !stats) {
    body = <ErrorState error={error} onRetry={onRetry} />
  } else if (stats.status === 'DISABLED') {
    body = <DisabledNotice />
  } else if (stats.status === 'UNAVAILABLE') {
    body = <UnavailableNotice onRetry={onRetry} />
  } else {
    body = (
      // @container: 칸 수를 화면 폭이 아니라 이 카드의 폭으로 정한다.
      // 넓은 화면 3열 격자에서는 카드가 좁아 2×2로, 2열 격자에서 한 줄을 다 차지할 때는 4칸 한 줄로 펼친다
      <div className="@container">
        <dl className="grid grid-cols-2 gap-2 @xl:grid-cols-4">
          <StatTile
            label={
              <>
                오늘 <ProvisionalTag />
              </>
            }
            value={formatCountWithUnit(stats.todayVisitorCount, '명')}
          />
          <StatTile label="어제" value={formatCountWithUnit(stats.yesterdayVisitorCount, '명')} />
          <StatTile
            label="최근 7일"
            value={formatCountWithUnit(stats.last7DaysVisitorCount, '명')}
          />
          <StatTile
            label="최근 30일"
            value={formatCountWithUnit(stats.last30DaysVisitorCount, '명')}
          />
        </dl>
      </div>
    )
  }

  return (
    <StatsCardFrame
      title="동의한 방문자 수"
      icon={EyeIcon}
      description="방문 통계에 동의한 방문자만 셉니다. 실제 방문자보다 적습니다."
      measuredAt={isOk ? stats?.fetchedAt : null}
      // 보조 문구는 숫자가 보일 때만 의미가 있어 OK일 때만 둔다
      footnote={
        isOk
          ? '오늘은 집계가 늦게 반영돼 늘어날 수 있습니다. 최근 7일·30일은 같은 방문자를 한 번만 센 값이라 하루 숫자를 더한 값과 다릅니다.'
          : undefined
      }
      className={className}
    >
      {body}
    </StatsCardFrame>
  )
}
