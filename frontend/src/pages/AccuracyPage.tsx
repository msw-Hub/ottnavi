import { PageTitle } from '@/components/common/PageTitle'
import { AccuracyScreen } from '@/features/accuracy/components/AccuracyScreen'
// 이전(준비 중 자리표시, Task 089 전): import { ComingSoon } from '@/components/common/ComingSoon'

/** SCR-05 데이터 정확도 안내(`/accuracy`, 공개). 정적 화면이라 화면 조립은 AccuracyScreen이 맡는다. */
export function AccuracyPage() {
  // 이전: return <ComingSoon title="데이터 정확도 안내" plannedPhase="3단계" />
  return (
    <>
      <PageTitle name="정확도 안내" />
      <AccuracyScreen />
    </>
  )
}
