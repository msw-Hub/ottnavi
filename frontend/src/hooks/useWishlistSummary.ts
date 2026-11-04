import { useListWishlistItems } from '@/api/generated/wishlist/wishlist'

// 계산 안내에 쓰는 찜 개수 두 가지
export interface WishlistSummary {
  totalCount: number // 찜 전체 개수("다 봤음" 포함)
  activeItemCount: number // 계산 대상 개수("다 봤음" 제외). 0이면 서버가 계산을 400으로 거절한다
}

/**
 * 플랜 계산 전에 "계산할 찜이 있는지"를 알려 주는 찜 개수 요약을 돌려준다(요금 확인·플랜 결과 화면의 빈 찜 안내용).
 *
 * 왜 src/hooks에 두나: 요금(features/pricing)과 플랜(features/plan) 두 기능이 함께 쓰는데, 기능 폴더끼리는 import하지 않는다(frontend.md).
 * 찜 기능의 useWishlist를 가져다 쓸 수 없어, 같은 생성 훅(useListWishlistItems)을 여기서 한 번 감싸 공유한다.
 * 머리글 찜 배지(app/HeaderAccountNav)·찜 목록 화면과 같은 쿼리 키(['/me/wishlist-items'])라 캐시를 함께 쓰고,
 * 찜을 바꾸면 그쪽에서 이 키를 무효화하므로 여기 값도 따로 손대지 않아도 갱신된다.
 *
 * select로 필요한 두 숫자만 꺼낸다(CommonResponse 래퍼도 여기서 벗긴다). 목록 전체를 넘기지 않아 화면이 개수 말고 다른 값에 기대지 않게 한다.
 * 불러오는 중·실패는 호출하는 쪽이 isPending·isError로 판단한다(안내를 보이지 않고 원래 흐름에 맡긴다).
 */
export function useWishlistSummary() {
  return useListWishlistItems({
    query: {
      select: (res): WishlistSummary => ({
        totalCount: res.data.items.length,
        activeItemCount: res.data.activeItemCount,
      }),
    },
  })
}
