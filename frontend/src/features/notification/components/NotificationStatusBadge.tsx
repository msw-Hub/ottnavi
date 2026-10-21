import { cn } from 'cn'
import type { NotificationHistoryItemStatus } from '@/api/generated/model'
import { NOTIFICATION_STATUS_DISPLAY } from '@/features/notification/notificationDisplay'

interface NotificationStatusBadgeProps {
  status: NotificationHistoryItemStatus // 알림 상태(계약 enum). 글자·아이콘·테두리 모양이 상태마다 다르다
}

/**
 * 알림 발송 상태 배지다(SCR-12 이력). 알약 모양에 "아이콘 + 글자"를 함께 두고 테두리도 상태마다 달라(대기=점선, 발송됨=채움, 실패=빨간 실선)
 * 색을 못 보는 사람이나 흑백 화면에서도 구분된다(색만으로 정보를 전달하지 않는다, frontend.md).
 * 관리자 화면의 AdminStatusPill과 모양을 맞췄지만, 기능 폴더끼리는 import하지 않으므로(frontend.md) 이 기능 안에 따로 둔다.
 */
export function NotificationStatusBadge({ status }: NotificationStatusBadgeProps) {
  const { label, icon: Icon, className } = NOTIFICATION_STATUS_DISPLAY[status]

  return (
    <span
      className={cn(
        'inline-flex w-fit items-center gap-1 rounded-full border-2 py-0.5 pr-2.5 pl-2 text-sm leading-5 font-semibold whitespace-nowrap',
        className,
      )}
    >
      <Icon aria-hidden="true" className="size-4" />
      {label}
    </span>
  )
}
