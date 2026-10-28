/*
 * 알림 설정·발송 이력(SCR-12, Task 071)과 관리자 알림 상태 요약(SCR-16) 목업 데이터다.
 * 계약의 NotificationHistoryItem·AdminNotificationSummary 모양 그대로이고 mocks/notificationHandlers.ts가 돌려준다.
 *
 * - 이력은 최신순이다. "더 보기"를 눈으로 확인할 수 있도록 페이지 크기(10)보다 많은 23건을 둔다.
 * - 유형 3종(구독 안내 / 월초 안내 / 플랜 변경)과 상태 3종(대기 / 발송 / 실패)이 모두 한 번 이상 나오도록 맨 위 4건을 직접 골랐고,
 *   나머지는 월초 안내가 한 달에 한 번 발송된 것처럼 지어낸 값이다(실제 발송 이력이 아니다).
 * - 계약대로 sentAt은 발송에 성공한 건(SENT)만 값이 있고, 대기·실패는 null이다.
 */
import type { AdminNotificationSummary, NotificationHistoryItem } from '@/api/generated/model'

// 맨 위 4건: 상태 3종을 한 화면에 모두 보이게 하는 최신 기록
const RECENT_NOTIFICATIONS: NotificationHistoryItem[] = [
  {
    notificationId: 31,
    type: 'MONTHLY',
    status: 'SENT',
    createdAt: '2026-11-01T03:50:00+09:00',
    sentAt: '2026-11-01T03:51:12+09:00',
  },
  {
    notificationId: 30,
    type: 'PLAN_CHANGE',
    status: 'PENDING',
    createdAt: '2026-10-28T04:10:00+09:00',
    sentAt: null,
  },
  {
    notificationId: 29,
    type: 'PLAN_CHANGE',
    status: 'SENT',
    createdAt: '2026-10-22T04:05:00+09:00',
    sentAt: '2026-10-22T04:06:41+09:00',
  },
  {
    notificationId: 27,
    type: 'SUBSCRIBE_GUIDE',
    status: 'FAILED',
    createdAt: '2026-10-21T21:30:00+09:00',
    sentAt: null,
  },
]

// 이전 달들의 월초 안내(모두 발송됨). 알림 ID는 26부터 1씩 줄어들고 9월 1일부터 한 달씩 거슬러 올라간다
const OLDER_MONTHLY_NOTIFICATIONS: NotificationHistoryItem[] = Array.from(
  { length: 19 },
  (_, index) => {
    const month = ((9 - index + 11) % 12) + 1 // 9, 8, ..., 1, 12, 11, ...
    const year = 2026 - (index >= 9 ? 1 : 0)
    const day = `${year}-${String(month).padStart(2, '0')}-01`
    return {
      notificationId: 26 - index,
      type: 'MONTHLY' as const,
      status: 'SENT' as const,
      createdAt: `${day}T03:50:00+09:00`,
      sentAt: `${day}T03:51:30+09:00`,
    }
  },
)

// 최신순 이력 23건
export const MOCK_NOTIFICATIONS: NotificationHistoryItem[] = [
  ...RECENT_NOTIFICATIONS,
  ...OLDER_MONTHLY_NOTIFICATIONS,
]

// 관리자 알림 상태 요약: 최근 7일(대기·발송·실패 건수와 마지막 발송 시각)
export const MOCK_ADMIN_NOTIFICATION_SUMMARY: AdminNotificationSummary = {
  windowDays: 7,
  pendingCount: 2,
  sentCount: 41,
  failedCount: 1,
  latestSentAt: '2026-11-01T03:51:12+09:00',
}
