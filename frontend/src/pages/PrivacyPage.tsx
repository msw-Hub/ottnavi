import { PageTitle } from '@/components/common/PageTitle'
import { PrivacyPolicyScreen } from '@/features/legal/components/PrivacyPolicyScreen'

/** 개인정보처리방침 화면(`/privacy`, 공개). 내용은 features/legal이 맡고, 이 파일은 라우트에 연결만 한다. */
export function PrivacyPage() {
  return (
    <>
      <PageTitle name="개인정보처리방침" />
      <PrivacyPolicyScreen />
    </>
  )
}
