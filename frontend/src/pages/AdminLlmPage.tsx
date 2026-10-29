import { PageTitle } from '@/components/common/PageTitle'
import { OverviewTranslateScreen } from '@/features/admin/components/overview/OverviewTranslateScreen'

/** SCR-18 관리자 LLM 줄거리 번역(`/admin/llm`, 관리자). 영어 줄거리의 한국어 번역 초안을 만들고 검토·반영한다. */
export function AdminLlmPage() {
  return (
    <>
      <PageTitle name="[관리] LLM 줄거리 번역" />
      <OverviewTranslateScreen />
    </>
  )
}
