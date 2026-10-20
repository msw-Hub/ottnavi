import { PageTitle } from '@/components/common/PageTitle'
import { PlanHistoryScreen } from '@/features/plan/components/PlanHistoryScreen'

/**
 * SCR-11 플랜 변경 내역(`/plan/history`, 로그인 필요).
 * 화면 조립은 features/plan에 있고, 이 파일은 연결만 한다(pages는 얇게, frontend.md).
 *
 * 이전(Task 068 1차안): 목업이 아닌 환경에서는 API가 없어 "준비 중" 화면(ComingSoon)을 보였다.
 * 변경 내역 API 연결(Task 070)로 그 분기를 없앴다.
 */
export function PlanHistoryPage() {
  return (
    <>
      <PageTitle name="플랜 변경 내역" />
      <PlanHistoryScreen />
    </>
  )
}
