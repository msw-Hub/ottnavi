import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  removeWishlistItem,
  updateWishlistItem,
  useListWishlistItems,
} from '@/api/generated/wishlist/wishlist'
import type {
  CommonResponseUpdatedWishlistItem,
  UpdateWishlistItemRequest,
} from '@/api/generated/model'
import type { ApiError } from '@/api/http'

/**
 * 내 찜 목록(SCR-08)을 불러온다. 최근에 찜한 순이며 "다 봤음" 항목도 포함한다(watchedAt에 값이 있음).
 *
 * 생성 훅(useListWishlistItems, 계약 listWishlistItems)을 감싸고 select로 CommonResponse 래퍼를 벗긴다(frontend.md).
 * 쿼리 키는 생성 훅의 기본 키(['/me/wishlist-items'])다. 작품 상세의 찜 버튼(features/title-detail의 useWishlistItem)도
 * 같은 키를 쓰므로, 여기서 변경·삭제한 뒤 목록을 무효화하면 상세 화면의 찜 상태도 함께 갱신된다(Task 058).
 */
export function useWishlist() {
  return useListWishlistItems({ query: { select: (res) => res.data } })
}

// 찜 항목 하나의 변경 요청 인자
export interface UpdateWishlistItemVariables extends UpdateWishlistItemRequest {
  wishlistItemId: number // 바꿀 찜 항목 ID
}

/**
 * 찜 항목의 우선순위 변경과 "다 봤음" 표시/되돌리기를 한다(PATCH /me/wishlist-items/{id}).
 * 보낸 필드만 바뀐다. 성공하면 목록을 다시 불러오고, 계산 대상이 달라지므로 플랜 초안 캐시도 무효화한다.
 * 409(WISHLIST_LIMIT_EXCEEDED, 되돌릴 때 활성 찜이 20개)는 호출하는 화면이 onError로 안내한다.
 */
export function useUpdateWishlistItem() {
  const queryClient = useQueryClient()

  return useMutation<CommonResponseUpdatedWishlistItem, ApiError, UpdateWishlistItemVariables>({
    // 생성 함수(updateWishlistItem)를 쓴다. 화면이 넘기는 { wishlistItemId, priority?, isWatched? } 모양은 그대로 둔다
    mutationFn: ({ wishlistItemId, ...body }) => updateWishlistItem(wishlistItemId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['/me/wishlist-items'] })
      void queryClient.invalidateQueries({ queryKey: ['/me/plan-drafts'] })
    },
  })
}

/**
 * 찜을 삭제한다(DELETE /me/wishlist-items/{id}). 인자는 찜 항목 ID다.
 * 성공하면 목록을 다시 불러온다. 상세 화면의 "찜함" 표시도 같은 목록 쿼리에서 나오므로 함께 갱신된다.
 */
export function useRemoveWishlistItem() {
  const queryClient = useQueryClient()

  return useMutation<unknown, ApiError, number>({
    mutationFn: (wishlistItemId) => removeWishlistItem(wishlistItemId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['/me/wishlist-items'] })
      void queryClient.invalidateQueries({ queryKey: ['/me/plan-drafts'] })
    },
  })
}
