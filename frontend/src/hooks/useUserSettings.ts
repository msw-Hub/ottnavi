import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { customInstance, type ApiError } from '@/api/http'
import { getMockRequestParams } from '@/lib/mockScenario'
import type {
  CommonResponseUserSettings,
  SaveUserSettingsRequest,
  UserSettings,
} from '@/features/onboarding/mockTypes'

/*
 * 내 설정 훅 모음이다. 원래 features/onboarding/hooks에 있었으나, 작품 상세(title-detail)가 월 시청 시간을 읽어
 * "여러 달에 나눠 봐야 해요" 안내를 띄워야 해서 src/hooks로 올렸다. 기능 폴더끼리는 import하지 않으므로(frontend.md)
 * 여러 기능이 함께 쓰는 훅은 이 폴더에 둔다. 응답 타입은 임시 타입 파일(features/onboarding/mockTypes)을 그대로 쓴다(Task 031에서 생성 타입으로 교체).
 */

// 내 설정의 캐시 키. 화면 주소의 목업 안(?mockVariantWatch=)이 바뀌면 응답 모양이 달라 새로 불러오도록 키에 넣는다(운영에서는 빈 객체)
function getSettingsQueryKey() {
  return ['/me/settings', getMockRequestParams()] as const
}

/**
 * 내 설정(SCR-07)을 불러온다. 온보딩 전이면 isOnboardingDone이 false이고 나머지 값은 비어 있다.
 *
 * 임시 구현: 이 API(GET /me/settings)는 아직 계약에 없어 생성 훅이 없다. Task 031에서 생성 훅을 감싸는 형태로 바뀐다.
 * 성공 응답의 CommonResponse 래퍼는 select로 벗긴다(frontend.md).
 *
 * options.enabled: false이면 요청을 보내지 않는다. 공개 화면(작품 상세)에서 비로그인 사용자가 이 훅을 부르면
 * 어차피 401이 오고 브라우저 콘솔에 오류가 남기 때문에, 호출하는 쪽이 로그인 여부로 막을 수 있게 열어 둔다(기본은 켜짐).
 */
export function useUserSettings(options?: { enabled?: boolean }) {
  return useQuery<CommonResponseUserSettings, ApiError, UserSettings>({
    enabled: options?.enabled ?? true,
    queryKey: getSettingsQueryKey(),
    queryFn: ({ signal }) =>
      customInstance<CommonResponseUserSettings>({
        url: '/me/settings',
        method: 'GET',
        params: getMockRequestParams(),
        signal,
      }),
    select: (res) => res.data,
  })
}

/**
 * 내 설정을 저장한다(PUT /me/settings). 처음 저장하면 온보딩 완료다.
 * 저장하면 설정 캐시를 새 값으로 바꾸고, 설정에 따라 달라지는 요금·플랜 초안 캐시는 낡은 값이 되므로 무효화한다.
 * 화면 이동(원래 화면으로 복귀)과 성공 토스트는 이 훅을 쓰는 화면이 mutate의 onSuccess로 처리한다.
 * 입력 규칙 위반(400)은 훅이 던지는 ApiError의 fieldErrors·userMessage로 알린다.
 */
export function useSaveUserSettings() {
  const queryClient = useQueryClient()

  return useMutation<CommonResponseUserSettings, ApiError, SaveUserSettingsRequest>({
    mutationFn: (request) =>
      customInstance<CommonResponseUserSettings>({
        url: '/me/settings',
        method: 'PUT',
        data: request,
        params: getMockRequestParams(),
      }),
    onSuccess: (res) => {
      queryClient.setQueryData(getSettingsQueryKey(), res)
      // 다른 기능(pricing, plan)의 캐시 키를 문자열로 직접 무효화한다. 기능 폴더끼리 import하지 않기 위함이다
      void queryClient.invalidateQueries({ queryKey: ['/me/pricing'] })
      void queryClient.invalidateQueries({ queryKey: ['/me/plans/drafts'] })
    },
  })
}
