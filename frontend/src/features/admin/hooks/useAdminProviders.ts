import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { AdminProvider, UpdateAdminProviderRequest } from '@/api/generated/model'
import {
  getListAdminProvidersQueryKey,
  updateAdminProvider,
  useListAdminProviders,
} from '@/api/generated/admin/admin'
import type { ApiError } from '@/api/http'
import { getMockScenarioParams } from '@/lib/mockScenario'

/*
 * 제공처 매핑(SCR-15) 데이터 훅 모음이다. 확정 계약의 생성 훅·함수를 감싼다.
 * 실제 권한 검사는 백엔드(/api/admin/** ADMIN, Task 053·057)가 한다.
 */

// 목록 캐시 키의 앞부분(생성 키 `/admin/providers`와 같다). 저장이 성공하면 이 키로 시작하는 캐시를 무효화해 목록(과 메뉴의 미매핑 수)을 다시 불러온다
const PROVIDERS_QUERY_KEY = getListAdminProvidersQueryKey()[0]

/**
 * TMDB 제공처 목록을 불러온다(계약 listAdminProviders).
 * 같은 키를 쓰는 곳(제공처 화면, 관리자 메뉴의 미매핑 수)이 요청 하나를 나눠 쓴다(TanStack Query가 같은 키의 요청을 합친다).
 */
export function useAdminProviders() {
  return useListAdminProviders({
    query: { queryKey: [PROVIDERS_QUERY_KEY, getMockScenarioParams()], select: (res) => res.data },
  })
}

/** 제공처 하나의 매핑을 저장한다(PATCH /admin/providers/{providerId}). */
export function useSaveProviderMapping() {
  const queryClient = useQueryClient()
  return useMutation<
    AdminProvider,
    ApiError,
    UpdateAdminProviderRequest & { providerId: number } // providerId: 저장할 제공처(주소에 쓴다)
  >({
    mutationFn: ({ providerId, ...data }) =>
      updateAdminProvider(providerId, data).then((res) => res.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [PROVIDERS_QUERY_KEY] }),
  })
}
