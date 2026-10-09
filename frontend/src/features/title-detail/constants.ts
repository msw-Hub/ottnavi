import type { MediaType, WishlistPriority } from '@/features/title-detail/mockTypes'

// 작품 구분 글자(영화 / 드라마)
export const MEDIA_TYPE_LABELS: Record<MediaType, string> = {
  movie: '영화',
  tv: '드라마',
}

// 찜 우선순위 선택지. 순서가 화면에 보이는 순서다(PRD FR-09: 꼭 / 보고 싶음 / 여유될 때)
export const WISHLIST_PRIORITY_OPTIONS: { value: WishlistPriority; label: string }[] = [
  { value: 'MUST', label: '꼭 볼 작품' },
  { value: 'WANT', label: '보고 싶음' },
  { value: 'MAYBE', label: '여유될 때' },
]

// 우선순위를 고르지 않았을 때의 기본값
export const DEFAULT_WISHLIST_PRIORITY: WishlistPriority = 'WANT'
