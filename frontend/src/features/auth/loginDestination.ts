import type { QueryClient } from '@tanstack/react-query'
import { getGetMySettingsQueryKey, getMySettings } from '@/api/generated/user/user'

// 온보딩(내 설정) 화면 경로. 처음 로그인한 사람은 원래 화면보다 먼저 여기로 보낸다(PRD SCR-06)
const ONBOARDING_PATH = '/settings'

/**
 * 로그인 직후 이동할 곳을 정한다. 내 설정을 불러와 온보딩(첫 설정 저장)을 아직 안 했으면 SCR-07(/settings)로, 마쳤으면 원래 화면으로 보낸다(PRD SCR-06).
 * 판단 근거는 내 설정 조회(getMySettings)의 isOnboardingDone이다(설정 행이 없으면 false, 계약).
 * 목업·실제 환경이 같은 코드를 쓴다(목업은 MSW가 같은 API에 답한다).
 * 쿼리 캐시(fetchQuery)에 넣어 두므로 내 설정 화면이 이어서 같은 값을 쓴다.
 * 조회가 실패하면 로그인 자체는 성공했으므로 원래 화면으로 보낸다(설정 화면이 필요하면 거기서 다시 안내한다).
 *
 * Google 로그인 콜백(AuthCallbackScreen)과 데모 계정 로그인(useDemoLogin, Task 109)이 함께 쓴다.
 * 데모 계정도 첫 로그인 때는 설정 행이 없으므로 회원과 같은 흐름(온보딩 먼저)을 따른다. 그래서 이 함수를 콜백 화면 파일에서 이곳으로 옮겼다.
 */
export async function resolveLoginDestination(
  queryClient: QueryClient,
  target: string,
): Promise<{ path: string; state?: { from: string } }> {
  try {
    const settings = await queryClient.fetchQuery({
      queryKey: getGetMySettingsQueryKey(),
      queryFn: ({ signal }) => getMySettings(undefined, signal),
      staleTime: 0, // 방금 로그인했으므로 캐시에 남은 이전 사용자의 설정을 쓰지 않고 새로 받는다
    })
    if (!settings.data.isOnboardingDone) return { path: ONBOARDING_PATH, state: { from: target } }
  } catch {
    // 조회 실패: 아래에서 원래 화면으로 보낸다
  }
  return { path: target }
}
