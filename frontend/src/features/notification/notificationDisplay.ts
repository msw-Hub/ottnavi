import type { LucideIcon } from 'lucide-react'
import { CircleCheckIcon, CircleXIcon, ClockIcon } from 'lucide-react'
import type {
  NotificationHistoryItemStatus,
  NotificationHistoryItemType,
} from '@/api/generated/model'

/*
 * 알림 이력(SCR-12)의 유형·상태를 화면 글자와 아이콘으로 바꾸는 표다.
 * 상태는 색만으로 전달하지 않는다(frontend.md 화면 표시 규칙): 항상 "글자 + 아이콘 + 테두리 모양" 세 가지를 함께 쓴다.
 * 표를 컴포넌트와 따로 둔 이유: 글자 표현을 한곳에서 고치고, 컴포넌트 없이 테스트할 수 있게 하기 위함이다.
 */

// 알림 유형 → 화면 글자 (계약 NotificationHistoryItem.type)
export const NOTIFICATION_TYPE_LABELS: Record<NotificationHistoryItemType, string> = {
  SUBSCRIBE_GUIDE: '가입 안내',
  MONTHLY: '월초 안내',
  PLAN_CHANGE: '플랜 변경 알림',
}

export interface NotificationStatusDisplay {
  label: string // 화면에 보이는 상태 글자
  icon: LucideIcon // 상태를 모양으로 알리는 아이콘(글자와 같은 뜻이라 낭독기에는 숨긴다)
  className: string // 바탕·글자·테두리 모양(토큰 이름만 쓴다). 상태마다 테두리 모양(실선/점선)도 달라 흑백으로도 구분된다
}

// 알림 상태 → 표시 방법 (계약 NotificationHistoryItem.status)
export const NOTIFICATION_STATUS_DISPLAY: Record<
  NotificationHistoryItemStatus,
  NotificationStatusDisplay
> = {
  PENDING: {
    label: '발송 대기',
    icon: ClockIcon,
    className: 'border-dashed border-input bg-muted text-muted-foreground',
  },
  SENT: {
    label: '발송됨',
    icon: CircleCheckIcon,
    className: 'border-solid border-success-bg bg-success-bg text-success',
  },
  FAILED: {
    label: '발송 실패',
    icon: CircleXIcon,
    className: 'border-solid border-destructive bg-transparent text-destructive',
  },
}
