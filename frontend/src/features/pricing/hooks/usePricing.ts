import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { ServicePrice } from '@/api/generated/model'
import {
  resetMyServicePrice,
  setMyServicePrice,
  useListMyServicePrices,
} from '@/api/generated/product/product'
import type { ApiError } from '@/api/http'

/**
 * 요금 확인 화면(SCR-09)의 서비스별 요금을 불러온다(계약 listMyServicePrices): 기본 요금·내가 고친 금액·적용 금액·이용 상태와
 * 요금 적용 시작일(effectiveFrom)·관리자 확인 일시(lastVerifiedAt). 생성 훅을 감싸고 select로 래퍼를 벗겨 { services } 안의 목록만 돌려준다.
 * 예산·시청 시간 요약은 이 응답에 없어 화면이 내 설정(useUserSettings)에서 따로 읽는다.
 */
export function usePricing() {
  return useListMyServicePrices({ query: { select: (res) => res.data.services } })
}

// 금액 수정 요청 인자
export interface UpdatePricingVariables {
  productId: number // 금액을 바꿀 상품 ID(서비스 ID가 아니다. 사용자 금액은 상품 단위로 저장된다)
  monthlyPrice: number | null // 0 이상 100만 이하 정수(원). null이면 고친 금액을 지우고 기본 요금으로 되돌린다
}

/**
 * 상품 하나의 사용자 금액을 고치거나 되돌린다.
 * - 금액이 있으면 PUT /me/service-prices/{productId}(setMyServicePrice)이고 바뀐 줄(ServicePrice)을 돌려준다.
 * - null이면 DELETE /me/service-prices/{productId}(resetMyServicePrice, 204)라 본문이 없어 null을 돌려준다.
 * 하나의 훅으로 합친 이유: 화면의 입력 칸이 "비워서 적용하면 기본 요금" 한 동작으로 둘을 쓰기 때문이다.
 * 성공하면 요금 목록을 다시 불러온다. 금액이 바뀌면 이미 계산한 플랜 초안은 낡은 값이므로 초안 캐시도 무효화한다.
 */
export function useUpdatePricing() {
  const queryClient = useQueryClient()

  return useMutation<ServicePrice | null, ApiError, UpdatePricingVariables>({
    mutationFn: async ({ productId, monthlyPrice }) => {
      if (monthlyPrice === null) {
        await resetMyServicePrice(productId)
        return null
      }
      const res = await setMyServicePrice(productId, { monthlyPrice })
      return res.data
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['/me/service-prices'] })
      void queryClient.invalidateQueries({ queryKey: ['/me/plans/drafts'] })
    },
  })
}
