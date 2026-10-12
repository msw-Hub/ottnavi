import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { customInstance, type ApiError } from '@/api/http'
import { getMockScenarioParams } from '@/lib/mockScenario'
import type {
  AddProductPriceRequest,
  AdminProduct,
  AdminProductList,
  CommonResponseAdminProduct,
  CommonResponseAdminProductList,
  CreateAdminProductRequest,
  UpdateAdminProductRequest,
  UpdateProductPriceRequest,
} from '@/features/admin/mockTypes'

/*
 * OTT 구독상품·월 구독요금(SCR-14) 데이터 훅 모음이다.
 *
 * 임시 구현: 이 API(/admin/products 아래)는 아직 계약에 없어 생성 훅이 없다. 그래서 customInstance를 직접 감싼다.
 * Task 031에서 생성 훅으로 바뀌면 이 파일은 생성 훅을 감싸는 얇은 훅(select: res => res.data)만 남는다.
 * 실제 권한 검사는 백엔드(/api/admin/** ADMIN, Task 053·057)가 한다. 화면 가드는 편의일 뿐이다.
 */

// 목록 조회 캐시 키의 앞부분. 쓰기 훅이 성공하면 이 키로 시작하는 캐시를 모두 무효화해 목록을 다시 불러온다
const PRODUCTS_QUERY_KEY = '/admin/products'

/** OTT 구독상품 목록(월 구독요금 이력 포함)을 불러온다. */
export function useAdminProducts() {
  return useQuery<CommonResponseAdminProductList, ApiError, AdminProductList>({
    // 목업 시나리오(?mockScenario=error|empty)가 바뀌면 다시 불러오도록 키에 넣는다
    queryKey: [PRODUCTS_QUERY_KEY, getMockScenarioParams()],
    queryFn: ({ signal }) =>
      customInstance<CommonResponseAdminProductList>({
        url: '/admin/products',
        method: 'GET',
        signal,
      }),
    select: (res) => res.data,
  })
}

// 쓰기가 성공했을 때 공통으로 하는 일: 목록 캐시를 낡은 것으로 표시해 다시 불러온다
function useInvalidateProducts() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: [PRODUCTS_QUERY_KEY] })
}

/** OTT 구독상품을 등록한다(POST /admin/products). 첫 월 구독요금도 함께 보낸다. */
export function useCreateAdminProduct() {
  const invalidate = useInvalidateProducts()
  return useMutation<AdminProduct, ApiError, CreateAdminProductRequest>({
    mutationFn: (data) =>
      customInstance<CommonResponseAdminProduct>({
        url: '/admin/products',
        method: 'POST',
        data,
      }).then((res) => res.data),
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
      customInstance<CommonResponseAdminProduct>({
        url: `/admin/products/${productId}`,
        method: 'PATCH',
        data,
      }).then((res) => res.data),
    onSuccess: invalidate,
  })
}

/** 월 구독요금 줄을 추가한다(POST /admin/products/{productId}/prices). */
export function useAddProductPrice() {
  const invalidate = useInvalidateProducts()
  return useMutation<
    AdminProduct,
    ApiError,
    AddProductPriceRequest & { productId: number } // productId: 줄을 추가할 OTT 구독상품 ID
  >({
    mutationFn: ({ productId, ...data }) =>
      customInstance<CommonResponseAdminProduct>({
        url: `/admin/products/${productId}/prices`,
        method: 'POST',
        data,
      }).then((res) => res.data),
    onSuccess: invalidate,
  })
}

/** 가장 최근 월 구독요금 줄을 고친다(PATCH /admin/products/{productId}/prices/{priceId}). */
export function useUpdateProductPrice() {
  const invalidate = useInvalidateProducts()
  return useMutation<
    AdminProduct,
    ApiError,
    UpdateProductPriceRequest & { productId: number; priceId: number } // 고칠 줄의 위치(주소에 쓴다)
  >({
    mutationFn: ({ productId, priceId, ...data }) =>
      customInstance<CommonResponseAdminProduct>({
        url: `/admin/products/${productId}/prices/${priceId}`,
        method: 'PATCH',
        data,
      }).then((res) => res.data),
    onSuccess: invalidate,
  })
}

/** 가장 최근 월 구독요금 줄을 지운다(DELETE /admin/products/{productId}/prices/{priceId}). */
export function useDeleteProductPrice() {
  const invalidate = useInvalidateProducts()
  return useMutation<AdminProduct, ApiError, { productId: number; priceId: number }>({
    mutationFn: ({ productId, priceId }) =>
      customInstance<CommonResponseAdminProduct>({
        url: `/admin/products/${productId}/prices/${priceId}`,
        method: 'DELETE',
      }).then((res) => res.data),
    onSuccess: invalidate,
  })
}

/** [요금 확인함]: 줄의 마지막 확인일만 오늘로 갱신한다(POST …/prices/{priceId}/verify, 요청 본문 없음). */
export function useVerifyProductPrice() {
  const invalidate = useInvalidateProducts()
  return useMutation<AdminProduct, ApiError, { productId: number; priceId: number }>({
    mutationFn: ({ productId, priceId }) =>
      customInstance<CommonResponseAdminProduct>({
        url: `/admin/products/${productId}/prices/${priceId}/verify`,
        method: 'POST',
      }).then((res) => res.data),
    onSuccess: invalidate,
  })
}
