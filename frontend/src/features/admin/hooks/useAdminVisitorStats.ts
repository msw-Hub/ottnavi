import { useGetAdminVisitorStats } from '@/api/generated/admin/admin'

/**
 * 관리자 모니터링 화면(SCR-17)의 방문자 수(계약 getAdminVisitorStats, GET /admin/visitor-stats)를 불러온다.
 * 방문 통계(Google Analytics)에 동의한 방문자 수다. 방문 통계 쪽 실패는 오류 코드가 아니라 200 + data.status
 * (OK / DISABLED / UNAVAILABLE)로 오므로, 이 훅의 error는 "우리 서버 요청 자체가 실패한 경우"만 뜻한다.
 * 서버가 결과를 최대 15분 캐시하므로 기본 재조회 설정을 그대로 둔다.
 * 성공 응답의 CommonResponse 래퍼는 select로 벗긴다(frontend.md).
 */
export function useAdminVisitorStats() {
  return useGetAdminVisitorStats({ query: { select: (res) => res.data } })
}
