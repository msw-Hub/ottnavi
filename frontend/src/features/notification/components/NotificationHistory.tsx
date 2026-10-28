import { LoaderCircleIcon } from 'lucide-react'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { Button } from '@/components/ui/button'
import { NotificationStatusBadge } from '@/features/notification/components/NotificationStatusBadge'
import { useNotificationHistory } from '@/features/notification/hooks/useNotificationHistory'
import { NOTIFICATION_TYPE_LABELS } from '@/features/notification/notificationDisplay'
import type { NotificationHistoryItem } from '@/api/generated/model'
import { formatDateTimeKorean } from '@/lib/format'

/**
 * 알림 발송 이력 목록이다(SCR-12, FR-16). 최신순이고 "더 보기"로 다음 페이지를 이어 붙인다.
 *
 * 상태 화면: 불러오는 중 / 실패(다시 시도) / 비어 있음 / 목록. 더 보기 도중 실패하면 이미 불러온 목록은 그대로 두고
 * 목록 아래에 오류 안내와 다시 시도를 보인다(목록 전체를 오류 화면으로 바꾸지 않는다).
 * 알림을 껐더라도 이전에 만들어진 이력은 남아 있으므로 설정과 무관하게 항상 보인다.
 */
export function NotificationHistory() {
  const history = useNotificationHistory()

  if (history.isPending) return <LoadingState message="발송 이력을 불러오고 있어요" />
  // 첫 조회 실패만 전체 오류 화면으로 보인다. "더 보기" 실패도 isError가 되지만 이때는 data가 남아 있어 아래 목록을 지키려고 data로 가른다
  if (history.isError && !history.data) {
    return (
      <ErrorState
        error={history.error}
        title="발송 이력을 불러오지 못했어요"
        onRetry={() => void history.refetch()}
      />
    )
  }
  if (!history.data || history.data.items.length === 0) {
    return (
      <EmptyState
        title="아직 받은 알림이 없어요"
        // 이전: "알림을 켜면 …". 이미 켜 둔 사람에게도 어색하지 않게 켜짐·꺼짐 어느 쪽에서나 맞는 문장으로 바꿨다
        description="알림이 켜져 있으면 월초 안내나 플랜 변경 알림이 만들어지는 대로 여기에 기록돼요."
      />
    )
  }

  return (
    <div className="grid gap-3">
      <p className="text-sm text-muted-foreground tabular-nums">
        전체 {history.data.totalElements}건 · 최신순
      </p>
      <ol aria-label="발송 이력 목록" className="grid gap-2">
        {history.data.items.map((item) => (
          <li key={item.notificationId}>
            <NotificationHistoryRow item={item} />
          </li>
        ))}
      </ol>

      {/* 더 보기 도중 실패: 불러온 목록은 남기고 아래에 알린다 */}
      {history.isFetchNextPageError && (
        <ErrorState
          error={history.error}
          title="이력을 더 불러오지 못했어요"
          onRetry={() => void history.fetchNextPage()}
        />
      )}
      {history.hasNextPage && !history.isFetchNextPageError && (
        <Button
          type="button"
          variant="outline"
          className="h-11"
          disabled={history.isFetchingNextPage}
          onClick={() => void history.fetchNextPage()}
        >
          {history.isFetchingNextPage && (
            <LoaderCircleIcon aria-hidden="true" className="motion-safe:animate-spin" />
          )}
          {history.isFetchingNextPage ? '불러오는 중' : '발송 이력 더 보기'}
        </Button>
      )}
    </div>
  )
}

// 이력 한 줄: 유형, 상태 배지, 만든 시각, 발송 시각(발송 전·실패면 사유 글자)
function NotificationHistoryRow({ item }: { item: NotificationHistoryItem }) {
  const createdAt = formatDateTimeKorean(item.createdAt)
  const sentAt = formatDateTimeKorean(item.sentAt)

  // 계약: sentAt은 발송 성공 시각이라 대기·실패면 null이다 → 상태에 맞는 글자로 대신 알린다
  let sentText: string
  if (sentAt) sentText = `${sentAt}에 발송했어요`
  else if (item.status === 'PENDING') sentText = '아직 발송 전이에요'
  else sentText = '발송하지 못했어요'

  return (
    <div className="grid gap-1.5 rounded-lg border border-border bg-card px-4 py-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-semibold">{NOTIFICATION_TYPE_LABELS[item.type]}</p>
        <NotificationStatusBadge status={item.status} />
      </div>
      <p className="text-sm text-muted-foreground tabular-nums">
        {createdAt ? `${createdAt}에 만들어짐` : '만든 시각을 알 수 없어요'}
      </p>
      <p className="text-sm text-muted-foreground tabular-nums">{sentText}</p>
    </div>
  )
}
