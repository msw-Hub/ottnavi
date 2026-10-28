import { useGetAdminNotificationSummary } from '@/api/generated/admin/admin'

/**
 * 관리자 수집 화면(SCR-16)의 알림 발송 상태 요약(계약 getAdminNotificationSummary, GET /admin/notifications/summary)을 불러온다.
 * 최근 7일(windowDays) 동안 만들어진 알림의 대기·발송·실패 건수와 마지막 발송 시각이다. 사용자 이메일이나 본문은 없다.
 * 같은 요청은 TanStack Query가 한 번만 보내고 결과를 나눠 쓰므로, 펼친 행이 여러 개여도 서버 요청은 늘지 않는다.
 * 성공 응답의 CommonResponse 래퍼는 select로 벗긴다(frontend.md).
 */
export function useAdminNotificationSummary() {
  return useGetAdminNotificationSummary({ query: { select: (res) => res.data } })
}
