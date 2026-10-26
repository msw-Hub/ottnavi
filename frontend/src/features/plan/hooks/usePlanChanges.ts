import { useListPlanChanges } from '@/api/generated/plan/plan'

/**
 * 플랜 변경 내역(SCR-11, 계약 listPlanChanges, GET /me/plan-changes)을 불러온다.
 *
 * 서버가 최신순으로 최대 6건을 주므로 화면은 순서를 다시 정렬하지 않는다. 없으면 빈 배열이다 → 화면은 빈 상태를 보인다.
 * 성공 응답의 CommonResponse 래퍼는 select로 벗기고, 목록(items)만 꺼내 화면이 래퍼 모양을 모르게 한다(frontend.md).
 */
export function usePlanChanges() {
  return useListPlanChanges({ query: { select: (res) => res.data.items } })
}
