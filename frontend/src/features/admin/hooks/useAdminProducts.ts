import { useMutation, useQueryClient } from '@tanstack/react-query'
import type {
  AdminProduct,
  AdminProductPriceRequest,
  CreateAdminProductRequest,
  UpdateAdminProductRequest,
} from '@/api/generated/model'
import {
  addAdminProductPrice,
  createAdminProduct,
  deleteAdminProductPrice,
  getListAdminProductsQueryKey,
  updateAdminProduct,
  updateAdminProductPrice,
  useListAdminProducts,
  verifyAdminProductPrice,
} from '@/api/generated/admin/admin'
import type { ApiError } from '@/api/http'
import { getMockScenarioParams } from '@/lib/mockScenario'

/*
 * OTT 구독상품·월 구독요금(SCR-14) 데이터 훅 모음이다. 확정 계약의 생성 훅·함수를 감싸 화면이 쓰던 모양(요청 본문 + 주소의 ID)을 그대로 둔다.
 * 실제 권한 검사는 백엔드(/api/admin/** ADMIN, Task 053·057)가 한다. 화면 가드는 편의일 뿐이다.
 */

// 목록 조회 캐시 키의 앞부분(생성 키 `/admin/products`와 같다). 쓰기 훅이 성공하면 이 키로 시작하는 캐시를 모두 무효화해 목록을 다시 불러온다
const PRODUCTS_QUERY_KEY = getListAdminProductsQueryKey()[0]

/** OTT 구독상품 목록(월 구독요금 이력 포함)을 불러온다(계약 listAdminProducts). */
export function useAdminProducts() {
  return useListAdminProducts({
    query: {
      // 목업 시나리오(?mockScenario=error|empty)가 바뀌면 다시 불러오도록 키에 넣는다(요청에는 X-Mock-Scenario 헤더로 실린다)
      queryKey: [PRODUCTS_QUERY_KEY, getMockScenarioParams()],
      select: (res) => res.data,
    },
  })
}

// 쓰기가 성공했을 때 공통으로 하는 일: 목록 캐시를 낡은 것으로 표시해 다시 불러온다
function useInvalidateProducts() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: [PRODUCTS_QUERY_KEY] })
}

/** OTT 구독상품을 등록한다(POST /admin/products, 201). 첫 월 구독요금도 함께 보낸다. */
export function useCreateAdminProduct() {
  const invalidate = useInvalidateProducts()
  return useMutation<AdminProduct, ApiError, CreateAdminProductRequest>({
    mutationFn: (data) => createAdminProduct(data).then((res) => res.data),
    onSuccess: invalidate,
  })
}

/** OTT 구독상품의 이름·판매 상태를 고친다(PATCH /admin/products/{productId}). */
export function useUpdateAdminProduct() {
  const invalidate = useInvalidateProducts()
  return useMutation<
    AdminProduct,
    ApiError,
    UpdateAdminProductRequest & { productId: number } // productId: 고칠 OTT 구독상품 ID(주소에 쓴다)
  >({
    mutationFn: ({ productId, ...data }) =>
      updateAdminProduct(productId, data).then((res) => res.data),
    onSuccess: invalidate,
  })
}

/** 월 구독요금 줄을 추가한다(POST /admin/products/{productId}/prices, 201). */
export function useAddProductPrice() {
  const invalidate = useInvalidateProducts()
  return useMutation<
    AdminProduct,
    ApiError,
    AdminProductPriceRequest & { productId: number } // productId: 줄을 추가할 OTT 구독상품 ID
  >({
    mutationFn: ({ productId, ...data }) =>
      addAdminProductPrice(productId, data).then((res) => res.data),
    onSuccess: invalidate,
  })
}

/** 가장 최근 월 구독요금 줄을 고친다(PATCH /admin/products/{productId}/prices/{priceId}). */
export function useUpdateProductPrice() {
  const invalidate = useInvalidateProducts()
  return useMutation<
    AdminProduct,
    ApiError,
    AdminProductPriceRequest & { productId: number; priceId: number } // 고칠 줄의 위치(주소에 쓴다)
  >({
    mutationFn: ({ productId, priceId, ...data }) =>
      updateAdminProductPrice(productId, priceId, data).then((res) => res.data),
    onSuccess: invalidate,
  })
}

/**
 * 가장 최근 월 구독요금 줄을 지운다(DELETE /admin/products/{productId}/prices/{priceId}, 204).
 * 본문이 없어 바뀐 상품을 돌려받지 못한다. 성공하면 목록을 다시 불러와 화면이 새 이력을 보인다.
 */
export function useDeleteProductPrice() {
  const invalidate = useInvalidateProducts()
  return useMutation<void, ApiError, { productId: number; priceId: number }>({
    mutationFn: ({ productId, priceId }) => deleteAdminProductPrice(productId, priceId),
    onSuccess: invalidate,
  })
}

/** [요금 확인함]: 줄의 마지막 확인 일시를 지금으로 갱신한다(POST …/prices/{priceId}/verify, 요청 본문 없음). */
export function useVerifyProductPrice() {
  const invalidate = useInvalidateProducts()
  return useMutation<AdminProduct, ApiError, { productId: number; priceId: number }>({
    mutationFn: ({ productId, priceId }) =>
      verifyAdminProductPrice(productId, priceId).then((res) => res.data),
    onSuccess: invalidate,
  })
}
