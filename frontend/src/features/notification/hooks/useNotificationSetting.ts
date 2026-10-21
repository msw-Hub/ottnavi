import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { CommonResponseNotificationSetting } from '@/api/generated/model'
import {
  getGetMyNotificationSettingQueryKey,
  updateMyNotificationSetting,
  useGetMyNotificationSetting,
} from '@/api/generated/notification/notification'
import type { ApiError } from '@/api/http'

/**
 * 알림 설정(SCR-12, 계약 getMyNotificationSetting, GET /me/notification-setting)을 불러온다.
 * 알림은 기본 꺼짐이고 온보딩 전이어도 200이다(isNotificationEnabled=false). 수신 이메일은 계정 이메일이라 수정할 수 없다.
 * 성공 응답의 CommonResponse 래퍼는 select로 벗긴다(frontend.md).
 */
export function useNotificationSetting() {
  return useGetMyNotificationSetting({ query: { select: (res) => res.data } })
}

/**
 * 알림을 켜거나 끈다(계약 updateMyNotificationSetting, PUT /me/notification-setting). 켜는 것 자체로는 메일이 나가지 않는다.
 * 내 설정(온보딩)을 한 번도 저장하지 않았으면 409 ONBOARDING_REQUIRED가 온다 → 화면이 errorCode로 분기해 설정 화면 링크를 보인다.
 *
 * 생성 훅 대신 생성 함수를 감싸는 이유: 화면이 켤지 끌지(boolean)만 mutate(true)로 넘기게 하려는 것이다(요청 본문 모양은 여기서 맡는다).
 * 성공하면 서버가 돌려준 저장된 설정으로 조회 캐시를 바꾼다. 다시 불러오지 않아도 스위치가 서버 값을 따르게 하려는 것이다.
 * 실패하면 캐시를 건드리지 않으므로 스위치는 저장 전 서버 값으로 자동으로 되돌아간다(낙관적 갱신을 쓰지 않은 이유).
 */
export function useUpdateNotificationSetting() {
  const queryClient = useQueryClient()

  return useMutation<CommonResponseNotificationSetting, ApiError, boolean>({
    mutationFn: (isNotificationEnabled) => updateMyNotificationSetting({ isNotificationEnabled }),
    onSuccess: (res) => {
      queryClient.setQueryData(getGetMyNotificationSettingQueryKey(), res)
    },
  })
}
