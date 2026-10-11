import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { customInstance, type ApiError } from '@/api/http'
import { getMockScenarioParams } from '@/lib/mockScenario'
import type {
  AdminProvider,
  AdminProviderList,
  CommonResponseAdminProvider,
  CommonResponseAdminProviderList,
  SaveProviderMappingRequest,
} from '@/features/admin/mockTypes'

/*
 * 제공처 매핑(SCR-15) 데이터 훅 모음이다.
 *
 * 임시 구현: 이 API(/admin/providers)는 아직 계약에 없어 생성 훅이 없다. Task 031에서 생성 훅으로 바뀐다.
 * 실제 권한 검사는 백엔드(/api/admin/** ADMIN, Task 053·057)가 한다.
 */

// 목록 캐시 키의 앞부분. 저장이 성공하면 이 키로 시작하는 캐시를 무효화해 목록(과 메뉴의 미매핑 수)을 다시 불러온다
const PROVIDERS_QUERY_KEY = '/admin/providers'

/**
 * TMDB 제공처 목록을 불러온다.
 * 같은 키를 쓰는 곳(제공처 화면, 관리자 메뉴의 미매핑 수)이 요청 하나를 나눠 쓴다(TanStack Query가 같은 키의 요청을 합친다).
 */
export function useAdminProviders() {
  return useQuery<CommonResponseAdminProviderList, ApiError, AdminProviderList>({
    queryKey: [PROVIDERS_QUERY_KEY, getMockScenarioParams()],
    queryFn: ({ signal }) =>
      customInstance<CommonResponseAdminProviderList>({
        url: '/admin/providers',
        method: 'GET',
        params: getMockScenarioParams(),
        signal,
      }),
    select: (res) => res.data,
  })
}

/** 제공처 하나의 매핑을 저장한다(PATCH /admin/providers/{providerId}). */
export function useSaveProviderMapping() {
  const queryClient = useQueryClient()
  return useMutation<
    AdminProvider,
    ApiError,
    SaveProviderMappingRequest & { providerId: number } // providerId: 저장할 제공처(주소에 쓴다)
  >({
    mutationFn: ({ providerId, ...data }) =>
      customInstance<CommonResponseAdminProvider>({
        url: `/admin/providers/${providerId}`,
        method: 'PATCH',
        data,
        // save-error 같은 목업 시나리오를 저장 요청에도 실어 보낸다(운영에서는 빈 객체)
        params: getMockScenarioParams(),
      }).then((res) => res.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [PROVIDERS_QUERY_KEY] }),
  })
}
