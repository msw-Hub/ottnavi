import { CircleCheckIcon, CircleXIcon, ClockIcon } from 'lucide-react'
import { AdminStatusPill } from '@/features/admin/components/AdminStatusPill'
import { useAdminNotificationSummary } from '@/features/admin/hooks/useAdminNotificationSummary'
import { getErrorMessage } from '@/lib/errorMessages'
import { isApiError } from '@/api/http'
import { formatDateTime } from '@/lib/format'

/**
 * 수집 실행 상세의 "알림 발송 상태" 칸 내용이다(SCR-16, Task 071). 이전에는 "2단계부터 제공" 태그(PhaseLaterTag)만 있었다.
 *
 * 최근 7일 동안 만들어진 알림의 대기·발송·실패 건수와 마지막 발송 시각을 보인다. 건수는 실행 한 건이 아니라 전체 알림 기준이라
 * "최근 N일"을 글자로 밝힌다. 상태는 색만으로 구분하지 않고 아이콘 + 글자 + 테두리 모양(AdminStatusPill)을 함께 쓴다.
 * 실패가 1건 이상일 때만 빨간 실선(danger)으로 눈에 띄게 하고, 0건이면 평범한 테두리(outline)로 둔다.
 * 불러오는 중·오류는 이 칸 안의 글자로만 알려, 변경된 작품 목록 등 상세의 나머지를 가리지 않는다.
 */
export function NotificationSummary() {
  const summary = useAdminNotificationSummary()

  if (summary.isPending) {
    return <span className="text-muted-foreground">불러오는 중</span>
  }
  if (summary.isError) {
    return (
      <span role="alert" className="text-destructive">
        {isApiError(summary.error) ? summary.error.userMessage : getErrorMessage()}
        <button
          type="button"
          onClick={() => void summary.refetch()}
          className="ml-2 inline-flex min-h-11 items-center rounded-md font-semibold underline underline-offset-3 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
        >
          다시 시도
        </button>
      </span>
    )
  }

  const { windowDays, pendingCount, sentCount, failedCount, latestSentAt } = summary.data
  const latestText = formatDateTime(latestSentAt)

  return (
    <div className="grid gap-1.5">
      <p className="text-muted-foreground">최근 {windowDays}일 기준</p>
      <div className="flex flex-wrap gap-1.5">
        <AdminStatusPill
          icon={ClockIcon}
          label={`대기 ${pendingCount}건`}
          tone="neutral"
          size="sm"
        />
        <AdminStatusPill
          icon={CircleCheckIcon}
          label={`발송 ${sentCount}건`}
          tone="success"
          size="sm"
        />
        <AdminStatusPill
          icon={CircleXIcon}
          label={`실패 ${failedCount}건`}
          tone={failedCount > 0 ? 'danger' : 'outline'}
          size="sm"
        />
      </div>
      <p className="text-muted-foreground tabular-nums">마지막 발송: {latestText ?? '아직 없음'}</p>
    </div>
  )
}
