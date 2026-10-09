/*
 * 임시 타입 파일 (Task 028, SCR-08 찜 목록).
 *
 * 내 찜 목록 조회(GET /me/wishlist-items)는 아직 계약(docs/api/openapi.yaml)에 없다.
 * 추가·삭제 요청 타입은 features/search, features/title-detail의 mockTypes.ts에 있고, 기능 폴더끼리는 import하지 않으므로
 * 이 기능에서 필요한 것만 여기에 따로 둔다. Task 031에서 생성 타입으로 바꾸고 이 파일을 지운다.
 */
import type { AvailabilityStatus, WishlistPriority } from '@/components/common/displayTypes'

export type { WishlistPriority }

// 임시: 계약 반영 후 생성 타입으로 교체 — 작품 구분(영화 / TV)
export type MediaType = 'movie' | 'tv'

// 임시: 계약 반영 후 생성 타입으로 교체 — 서비스 하나의 제공 상태
export interface ServiceAvailability {
  ottServiceId: number // 서비스 내부 ID
  status: AvailabilityStatus // 있음 / 없음 / 모름
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 찜 목록의 한 줄
export interface WishlistListItem {
  wishlistItemId: number // 찜 항목 ID(우선순위 변경·다 봤음·삭제 요청의 주소에 쓴다)
  mediaType: MediaType // 영화 / 드라마(tv)
  tmdbId: number // TMDB ID
  title: string // 한국어 제목
  posterUrl: string | null // 포스터 주소. 없으면 null → "포스터 없음" 대체 표시
  seasonNumber: number | null // 시즌 단위 찜이면 시즌 번호. 영화이거나 작품 단위로 보는 안(b)이면 null
  seasonName: string | null // 시즌 이름(예: "시즌 2"). seasonNumber가 null이면 null
  priority: WishlistPriority // 꼭 / 보고 싶음 / 여유될 때
  requiredMinutes: number // 다 보는 데 필요한 시간(분). 작품 단위 안(b)의 드라마는 모든 시즌의 합
  isRuntimeEstimated: boolean // requiredMinutes가 추정값인지 → "추정" 표시
  availabilities: ServiceAvailability[] // 서비스별 제공 상태
  isWatchableNow: boolean // 내가 구독 중이거나 무료로 이용하는 서비스에서 이미 볼 수 있는지
  watchedAt: string | null // "다 봤음"으로 표시한 시각(ISO-8601). 안 봤으면 null. 값이 있으면 계산에서 빠진다
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 찜 목록 응답
export interface WishlistList {
  items: WishlistListItem[] // 찜한 작품들(최근에 찜한 순). 없으면 빈 배열
  dataAsOf: string // 제공 상태의 데이터 기준일(YYYY-MM-DD)
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 찜 목록 성공 응답 래퍼
export interface CommonResponseWishlistList {
  success: boolean
  data: WishlistList
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 찜 항목 변경 요청(PATCH /me/wishlist-items/{id}). 보낸 필드만 바꾼다
export interface UpdateWishlistItemRequest {
  priority?: WishlistPriority // 우선순위 변경
  isWatched?: boolean // true면 "다 봤음"(watchedAt 기록), false면 되돌림
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 찜 항목 변경 응답 래퍼
export interface CommonResponseWishlistItemUpdated {
  success: boolean
  data: { wishlistItemId: number; priority: WishlistPriority; watchedAt: string | null }
}
