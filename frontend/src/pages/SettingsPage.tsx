import { PageTitle } from '@/components/common/PageTitle'
import { SettingsScreen } from '@/features/onboarding/components/SettingsScreen'

/** SCR-07 온보딩·내 설정 화면(`/settings`, 로그인 필요). 내용은 features/onboarding이 맡는다. */
export function SettingsPage() {
  return (
    <>
      <PageTitle name="내 설정" />
      <SettingsScreen />
    </>
  )
}
