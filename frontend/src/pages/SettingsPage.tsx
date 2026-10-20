import { SettingsScreen } from '@/features/onboarding/components/SettingsScreen'

/** SCR-07 온보딩·내 설정 화면(`/settings`, 로그인 필요). 내용은 features/onboarding이 맡는다. 탭 제목은 첫 방문 여부에 따라 달라져서 SettingsScreen이 정한다. */
export function SettingsPage() {
  return <SettingsScreen />
}
