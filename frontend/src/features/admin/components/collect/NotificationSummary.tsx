import { CircleCheckIcon, CircleXIcon, ClockIcon } from 'lucide-react'
import { AdminStatusPill } from '@/features/admin/components/AdminStatusPill'
import { useAdminNotificationSummary } from '@/features/admin/hooks/useAdminNotificationSummary'
import { getErrorMessage } from '@/lib/errorMessages'
import { isApiError } from '@/api/http'
import { formatDateTime } from '@/lib/format'

/**
 * 수집 화면(CollectScreen) 상단 "알림 발송 상태" 카드의 내용이다(SCR-16, Task 071). 이전에는 "2단계부터 제공" 태그(PhaseLaterTag)만 있었다.
 * 처음에는 수집 실행 상세(펼침) 칸 안에 있었지만, 실행 한 건이 아니라 최근 7일 전체 알림 기준이라 화면 상단으로 옮겼다.
 *
 * 최근 7일 동안 만들어진 알림의 대기·발송·실패 건수와 마지막 발송 시각을 보인다. 건수는 실행 한 건이 아니라 전체 알림 기준이라
 * "최근 N일"을 글자로 밝힌다. 상태는 색만으로 구분하지 않고 아이콘 + 글자 + 테두리 모양(AdminStatusPill)을 함께 쓴다.
 * 실패가 1건 이상일 때만 빨간 실선(danger)으로 눈에 띄게 하고, 0건이면 평범한 테두리(outline)로 둔다.
 * 불러오는 중·오류는 이 카드 안의 글자로만 알려, 아래 수동 실행 양식·실행 이력을 가리지 않는다.
 * 배지는 기본 크기(md)다. 상세 칸의 보조 정보였을 때는 작은 크기(sm, 12px 글자)였지만 이제 카드의 주 내용이라
 * 실행 이력 표의 상태 배지와 같은 크기로 맞췄다.
 */
export function NotificationSummary() {
  const summary = useAdminNotificationSummary()

  if (summary.isPending) {
    return <span className="text-muted-foreground">불러오는 중</span>
  }
  if (summary.isError) {
    return (
      // 이전: <span role="alert">에 문장과 버튼을 이어 써서 좁은 화면에서 "시도해 / 주세요. 다시 시도"처럼 문장 중간이 끊겼다.
      // 문장(낱말 단위 줄바꿈)과 버튼을 따로 묶어, 자리가 모자라면 버튼이 통째로 다음 줄로 내려가게 한다
      <div role="alert" className="flex flex-wrap items-center gap-x-2 text-destructive">
        <span className="break-keep">
          {isApiError(summary.error) ? summary.error.userMessage : getErrorMessage()}
        </span>
        <button
          type="button"
          onClick={() => void summary.refetch()}
          className="inline-flex min-h-11 items-center rounded-md font-semibold underline underline-offset-3 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
        >
          다시 시도
        </button>
      </div>
    )
  }

  const { windowDays, pendingCount, sentCount, failedCount, latestSentAt } = summary.data
  const latestText = formatDateTime(latestSentAt)

  return (
    <div className="grid gap-1.5">
      <p className="text-muted-foreground">최근 {windowDays}일 기준</p>
      {/* 이전: 상세 칸 안이라 gap-1.5와 size="sm"(작은 배지)였다 */}
      <div className="flex flex-wrap gap-2">
        <AdminStatusPill icon={ClockIcon} label={`대기 ${pendingCount}건`} tone="neutral" />
        <AdminStatusPill icon={CircleCheckIcon} label={`발송 ${sentCount}건`} tone="success" />
        <AdminStatusPill
          icon={CircleXIcon}
          label={`실패 ${failedCount}건`}
          tone={failedCount > 0 ? 'danger' : 'outline'}
        />
      </div>
      <p className="text-muted-foreground tabular-nums">마지막 발송: {latestText ?? '아직 없음'}</p>
    </div>
  )
}
