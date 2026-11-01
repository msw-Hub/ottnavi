import { useGetAdminUserStats } from '@/api/generated/admin/admin'

/**
 * 관리자 모니터링 화면(SCR-17)의 회원 현황 숫자(계약 getAdminUserStats, GET /admin/user-stats)를 불러온다.
 * 가입 회원 수·신규·활성 플랜·알림 켠 회원 수와 집계 시각이다(관리자·데모 계정 제외, 서버가 센다).
 * 쿼리 키는 생성 훅이 정한 고정 키라 같은 화면 안에서 여러 번 호출해도 요청은 한 번만 나간다.
 * 성공 응답의 CommonResponse 래퍼는 select로 벗긴다(frontend.md).
 */
export function useAdminUserStats() {
  return useGetAdminUserStats({ query: { select: (res) => res.data } })
}
