import { useQueryClient } from '@tanstack/react-query'
import { useListWishlistItems, useUpdateWishlistItem } from '@/api/generated/wishlist/wishlist'
import type { WishlistPriority } from '@/api/generated/model'
import type { ApiError } from '@/api/http'
import { toastApiError } from '@/lib/errorToast'

// 찜 목록·플랜 초안 쿼리 키. 기능 폴더(features)를 import할 수 없는 공통 훅이라 문자열로 직접 적는다(useWishlistItem과 같은 값)
const WISHLIST_LIST_QUERY_KEY = ['/me/wishlist-items'] as const
const PLAN_DRAFTS_QUERY_KEY = ['/me/plan-drafts'] as const

// 찜 추가 직후 서버가 기본값으로 담는 단계(보고 싶음). 목록에서 항목을 아직 못 찾았을 때의 표시값이다
const DEFAULT_PRIORITY: WishlistPriority = 'WANT'

/**
 * 찜 추가 팝업이 쓰는 "현재 우선순위 + 변경" 훅이다.
 *
 * 왜 따로 두나: 팝업(components/common)은 features를 import할 수 없어서 features/wishlist의 변경 훅을 못 쓴다.
 * 그래서 같은 생성 훅(updateWishlistItem)을 감싸 같은 방식(성공 시 목록·플랜 초안 무효화)으로 바꾼다.
 *
 * 현재 단계는 상태로 복사해 두지 않고 서버 찜 목록에서 항목 ID로 찾아 읽는다(파생 값).
 * 이미 찜해 둔 작품을 다시 담은 경우(서버가 200으로 기존 항목을 돌려줌)에도 실제 단계가 그대로 보인다.
 * 팝업이 열려 있을 때만 목록을 읽는다(enabled) — 닫힌 팝업이 매 화면에서 요청을 보내지 않게 하기 위함이다.
 */
export function useWishlistAddedPriority(wishlistItemId: number | null) {
  const queryClient = useQueryClient()

  const wishlist = useListWishlistItems({
    query: { enabled: wishlistItemId !== null, select: (res) => res.data },
  })
  const update = useUpdateWishlistItem({
    mutation: {
      // 갱신이 끝날 때까지 mutation을 끝내지 않아(Promise 반환) 선택 표시가 낡은 값으로 번쩍이지 않게 한다
      onSuccess: () =>
        Promise.all([
          queryClient.invalidateQueries({ queryKey: WISHLIST_LIST_QUERY_KEY }),
          queryClient.invalidateQueries({ queryKey: PLAN_DRAFTS_QUERY_KEY }),
        ]),
      onError: (error: ApiError) => {
        // 429는 인터셉터가 이미 토스트로 알렸으므로 같은 안내를 또 띄우지 않는다
        if (!error.hasNotifiedUser) toastApiError(error.errorCode, error.userMessage)
      },
    },
  })

  const priority =
    wishlist.data?.items.find((item) => item.wishlistItemId === wishlistItemId)?.priority ??
    DEFAULT_PRIORITY

  function changePriority(next: WishlistPriority) {
    if (wishlistItemId === null || next === priority) return
    update.mutate({ wishlistItemId, data: { priority: next } })
  }

  return { priority, changePriority, isPending: update.isPending }
}
