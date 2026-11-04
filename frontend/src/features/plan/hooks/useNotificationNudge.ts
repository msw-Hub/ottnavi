import { useGetMyNotificationSetting } from '@/api/generated/notification/notification'
import { useIsDemo } from '@/hooks/useIsDemo'
import { useIsLoggedIn } from '@/hooks/useIsLoggedIn'

// 저장 직후 화면이 알림 설정에 따라 고르는 두 값
export interface NotificationNudgeState {
  isNudgeAvailable: boolean // "알림 켜기" 띠를 보일지(설정을 읽었고 꺼짐일 때만 true)
  isNotificationEnabled: boolean // 알림이 켜져 있다고 확실히 읽었는지(모르면 false)
}

/**
 * 저장 직후 "알림 켜기" 안내를 보일 수 있는 상태인지 돌려준다(알림 기본 꺼짐 + 안내 방침).
 *
 * 왜 features/notification의 훅을 쓰지 않나: 기능 폴더끼리는 import하지 않는다(frontend.md). 같은 생성 훅을 이 화면 전용으로 얇게 감싼다.
 * 두 훅은 같은 쿼리 키를 쓰므로, 알림 설정 화면에서 켜면(setQueryData) 이 값도 함께 바뀐다.
 *
 * - enabled: 로그인했고, 데모 계정이 아니고, 안내가 필요한 순간(isActive)일 때만 요청한다.
 *   데모 계정은 서버가 늘 "꺼짐"으로 답하지만 안내를 보여 줄 이유가 없고, 비로그인은 401이 콘솔에 오류로 남기 때문이다.
 * - staleTime 0: 캐시에 남은 옛 값(예: 전에 꺼짐으로 읽어 둔 값)을 믿지 않고 화면에 올 때마다 새로 읽는다.
 *   켜고 돌아왔는데 안내가 또 보이는 것을 막으려는 안전장치다.
 * - retry false: 이 안내는 덤이다. 실패했을 때 재시도로 오래 기다리지 않고 바로 "보이지 않음"으로 처리한다.
 * - 로딩 중·실패일 때는 false다(저장 결과 화면을 흐리지 않기 위해). 성공했고 isNotificationEnabled가 false일 때만 true다.
 *
 * 저장 완료 상자의 둘째 줄(알림을 켰으면 "메일로 알려 드려요", 아니면 "변경 내역에서 볼 수 있어요")도 같은 값으로 고른다.
 * 그래서 켜짐 여부(isNotificationEnabled)도 함께 돌려준다. 켜짐이라고 확실히 읽었을 때만 true라,
 * 모르는 경우(불러오는 중·실패·데모 계정)에는 "메일로 알려 드린다"고 단정하지 않는 쪽 문구가 보인다.
 * 이전: 안내를 보일지(boolean) 하나만 돌려줬다.
 *
 * @param isActive 지금 안내가 필요한 순간인지(방금 플랜을 저장했는지). false면 요청도 보내지 않는다
 */
export function useNotificationNudge(isActive: boolean): NotificationNudgeState {
  const isDemo = useIsDemo()
  const isLoggedIn = useIsLoggedIn()

  const setting = useGetMyNotificationSetting({
    query: {
      select: (res) => res.data,
      enabled: isActive && isLoggedIn && !isDemo,
      staleTime: 0,
      retry: false,
    },
  })

  // 설정을 확실히 읽은 상태인지(안내가 필요한 순간 + 로그인 + 데모 아님 + 조회 성공). 아래 두 값 모두 이 조건 위에서만 참이 된다
  const isKnown = isActive && isLoggedIn && !isDemo && setting.isSuccess
  return {
    isNudgeAvailable: isKnown && !setting.data.isNotificationEnabled,
    isNotificationEnabled: isKnown && setting.data.isNotificationEnabled,
  }
  // 이전 반환값(boolean 하나):
  // return (
  //   isActive && isLoggedIn && !isDemo && setting.isSuccess && !setting.data.isNotificationEnabled
  // )
}
