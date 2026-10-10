import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { customInstance, type ApiError } from '@/api/http'
import { getMockRequestParams } from '@/lib/mockScenario'
import type {
  CommonResponsePricingOverview,
  CommonResponsePricingService,
  PricingOverview,
  UpdatePricingRequest,
} from '@/features/pricing/mockTypes'

/**
 * 요금 확인 화면(SCR-09)의 데이터를 불러온다: 서비스별 기본 요금·내가 고친 금액·적용 금액과 요금 기준일, 예산·시청 시간 요약.
 *
 * 임시 구현: 이 API(GET /me/pricing)는 아직 계약에 없어 생성 훅이 없다. Task 031에서 바뀐다.
 */
export function usePricing() {
  return useQuery<CommonResponsePricingOverview, ApiError, PricingOverview>({
    queryKey: ['/me/pricing', getMockRequestParams()],
    queryFn: ({ signal }) =>
      customInstance<CommonResponsePricingOverview>({
        url: '/me/pricing',
        method: 'GET',
        params: getMockRequestParams(),
        signal,
      }),
    select: (res) => res.data,
  })
}

// 금액 수정 요청 인자
export interface UpdatePricingVariables extends UpdatePricingRequest {
  ottServiceId: number // 금액을 바꿀 서비스 ID
}

/**
 * 서비스 하나의 사용자 금액을 고친다(PATCH /me/pricing/{ottServiceId}). monthlyPrice가 null이면 기본 요금으로 되돌린다.
 * 성공하면 요금 목록을 다시 불러온다. 금액이 바뀌면 이미 계산한 플랜 초안은 낡은 값이므로 초안 캐시도 무효화한다.
 */
export function useUpdatePricing() {
  const queryClient = useQueryClient()

  return useMutation<CommonResponsePricingService, ApiError, UpdatePricingVariables>({
    mutationFn: ({ ottServiceId, monthlyPrice }) =>
      customInstance<CommonResponsePricingService>({
        url: `/me/pricing/${ottServiceId}`,
        method: 'PATCH',
        data: { monthlyPrice },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['/me/pricing'] })
      void queryClient.invalidateQueries({ queryKey: ['/me/plans/drafts'] })
    },
  })
}
