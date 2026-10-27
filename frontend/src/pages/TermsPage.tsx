import { PageTitle } from '@/components/common/PageTitle'
import { TermsScreen } from '@/features/legal/components/TermsScreen'

/** 이용약관 화면(`/terms`, 공개). 내용은 features/legal이 맡고, 이 파일은 라우트에 연결만 한다. */
export function TermsPage() {
  return (
    <>
      <PageTitle name="이용약관" />
      <TermsScreen />
    </>
  )
}
