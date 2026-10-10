import { WishlistButton } from '@/components/common/WishlistButton'
import { useWishlistItem } from '@/features/title-detail/hooks/useWishlistItem'
import type { MediaType } from '@/features/title-detail/mockTypes'

interface WishlistControlProps {
  mediaType: MediaType // 찜할 작품의 구분
  tmdbId: number // 찜할 작품의 TMDB ID
  seasonNumber: number | null // 시즌 단위 찜이면 시즌 번호, 영화는 null
  panelAlign?: 'start' | 'end' // 설정 상자를 맞출 쪽(오른쪽 끝에 놓인 시즌 줄은 end)
  popupName?: string // 찜 추가 팝업 문장에 쓸 이름. 생략하면 targetName(시즌 줄은 "작품 제목 시즌 2"처럼 작품 제목을 붙여 넘긴다)
  posterUrl?: string | null // 찜 추가 팝업 썸네일 포스터 주소
  targetName: string // 무엇을 찜하는지 이름(예: "기생충", "시즌 2"). 화면 낭독기가 여러 찜 버튼을 구분하는 데 쓴다
}

/**
 * 상세 화면의 찜 버튼이다. 영화는 작품 상단에 하나, 드라마는 접은 상태의 시즌 요약 줄마다 하나씩 둔다.
 *
 * 우선순위는 찜할 때 묻지 않는다(기본값 "보고 싶음"으로 바로 추가). 찜한 뒤 버튼에서 우선순위를 바꾸거나 해제한다.
 * 찜 상태와 요청은 useWishlistItem이, 버튼 모양은 WishlistButton이 맡고 이 컴포넌트는 둘을 잇기만 한다.
 * 비로그인이면 서버가 401을 주고, 훅이 로그인 화면(/login)으로 보낸다(돌아올 경로 from 포함, useWishlistItem 참고).
 */
export function WishlistControl({
  mediaType,
  tmdbId,
  seasonNumber,
  targetName,
  popupName,
  posterUrl = null,
  panelAlign,
}: WishlistControlProps) {
  const wishlist = useWishlistItem({ mediaType, tmdbId, seasonNumber }, targetName, {
    name: popupName ?? targetName,
    posterUrl,
  })

  return <WishlistButton targetName={targetName} panelAlign={panelAlign} {...wishlist} />
}
