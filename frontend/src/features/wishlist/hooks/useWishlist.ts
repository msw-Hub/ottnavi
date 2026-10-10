import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { customInstance, type ApiError } from '@/api/http'
import { getMockRequestParams } from '@/lib/mockScenario'
import type {
  CommonResponseWishlistItemUpdated,
  CommonResponseWishlistList,
  UpdateWishlistItemRequest,
  WishlistList,
} from '@/features/wishlist/mockTypes'

// 찜 목록의 캐시 키. 목업 안(?mockVariant=)에 따라 응답이 달라서 키에 넣는다(운영에서는 빈 객체)
function getWishlistQueryKey() {
  return ['/me/wishlist-items', getMockRequestParams()] as const
}

// 검색·상세 화면의 찜 상태 캐시 키(features/search, features/title-detail의 useWishlistItem과 같은 문자열이어야 화면 사이에 상태가 공유된다)
const WISHLIST_STATE_KEY = ['mock-wishlist-state'] as const

// 그 훅이 캐시에 두는 값의 모양: 항목 키 → { 찜 항목 ID, 우선순위 }
type WishlistState = Record<string, { wishlistItemId: number; priority: string }>

/**
 * 내 찜 목록(SCR-08)을 불러온다. 최근에 찜한 순이며 "다 봤음" 항목도 포함한다(watchedAt에 값이 있음).
 *
 * 임시 구현: 이 API(GET /me/wishlist-items)는 아직 계약에 없어 생성 훅이 없다. Task 031에서 바뀐다.
 */
export function useWishlist() {
  return useQuery<CommonResponseWishlistList, ApiError, WishlistList>({
    queryKey: getWishlistQueryKey(),
    queryFn: ({ signal }) =>
      customInstance<CommonResponseWishlistList>({
        url: '/me/wishlist-items',
        method: 'GET',
        params: getMockRequestParams(),
        signal,
      }),
    select: (res) => res.data,
  })
}

// 찜 항목 하나의 변경 요청 인자
export interface UpdateWishlistItemVariables extends UpdateWishlistItemRequest {
  wishlistItemId: number // 바꿀 찜 항목 ID
}

/**
 * 찜 항목의 우선순위 변경과 "다 봤음" 표시/되돌리기를 한다(PATCH /me/wishlist-items/{id}).
 * 보낸 필드만 바뀐다. 성공하면 목록을 다시 불러오고, 계산 대상이 달라지므로 플랜 초안 캐시도 무효화한다.
 */
export function useUpdateWishlistItem() {
  const queryClient = useQueryClient()

  return useMutation<CommonResponseWishlistItemUpdated, ApiError, UpdateWishlistItemVariables>({
    mutationFn: ({ wishlistItemId, ...body }) =>
      customInstance<CommonResponseWishlistItemUpdated>({
        url: `/me/wishlist-items/${wishlistItemId}`,
        method: 'PATCH',
        data: body,
      }),
    onSuccess: (res) => {
      // 검색·상세 화면의 찜 버튼은 이 캐시에서 우선순위를 읽는다. 목록만 다시 불러오면 거기엔 옛 값이 남으므로,
      // 응답의 같은 항목(wishlistItemId)만 새 우선순위로 바꾼다. 캐시에 없는 항목은 새로 만들지 않는다(키 정보가 없다).
      // "다 봤음"(watchedAt)만 바꾼 요청이어도 응답 priority는 그대로라 같은 값으로 덮어쓸 뿐이다.
      const { wishlistItemId, priority } = res.data
      queryClient.setQueryData<WishlistState>(WISHLIST_STATE_KEY, (current = {}) =>
        Object.fromEntries(
          Object.entries(current).map(([key, entry]) => [
            key,
            entry.wishlistItemId === wishlistItemId ? { ...entry, priority } : entry,
          ]),
        ),
      )
      void queryClient.invalidateQueries({ queryKey: ['/me/wishlist-items'] })
      void queryClient.invalidateQueries({ queryKey: ['/me/plans/drafts'] })
    },
  })
}

/**
 * 찜을 삭제한다(DELETE /me/wishlist-items/{id}). 인자는 찜 항목 ID다.
 * 성공하면 목록을 다시 불러온다. 검색·상세 화면이 들고 있는 "찜함" 표시도 같이 지운다.
 * 지우지 않으면 찜 목록에서 삭제한 작품이 검색 카드에는 계속 "찜함"으로 보인다.
 */
export function useRemoveWishlistItem() {
  const queryClient = useQueryClient()

  return useMutation<unknown, ApiError, number>({
    mutationFn: (wishlistItemId) =>
      customInstance({ url: `/me/wishlist-items/${wishlistItemId}`, method: 'DELETE' }),
    onSuccess: (_res, wishlistItemId) => {
      queryClient.setQueryData<WishlistState>(WISHLIST_STATE_KEY, (current = {}) =>
        Object.fromEntries(
          Object.entries(current).filter(([, entry]) => entry.wishlistItemId !== wishlistItemId),
        ),
      )
      void queryClient.invalidateQueries({ queryKey: ['/me/wishlist-items'] })
      void queryClient.invalidateQueries({ queryKey: ['/me/plans/drafts'] })
    },
  })
}
