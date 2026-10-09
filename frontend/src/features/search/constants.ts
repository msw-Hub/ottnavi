import type { MediaType } from '@/features/search/mockTypes'

// 작품 구분 글자. 동명 영화·드라마를 목록에서 구분하는 핵심 정보다
export const MEDIA_TYPE_LABELS: Record<MediaType, string> = {
  movie: '영화',
  tv: '드라마',
}
