/*
 * 임시 타입 파일 (Task 027, SCR-01 검색).
 *
 * 작품 검색 API와 찜 추가 API는 아직 계약(docs/api/openapi.yaml)에 없어 orval 생성 타입이 없다.
 * 그래서 frontend.md "목업 단계의 임시 타입 예외"에 따라 이 파일 한 곳에만 임시로 둔다.
 * Task 031에서 계약에 operation을 추가하고 `npm run api:generate`로 생성 타입을 만든 뒤,
 * 이 파일의 타입을 생성 타입으로 바꾸고 이 파일을 지운다(완료 조건: mockTypes.ts가 남지 않는다).
 *
 * 주의: 이 타입은 "계약이 이럴 것"이라는 제안일 뿐이다. 경로와 필드는 Task 031에서 바뀔 수 있다.
 * 계약의 이미지 필드는 posterPath(TMDB 상대 경로)지만, 목업은 외부 이미지 요청을 막으려고 완성된 주소
 * (data URI 또는 null)를 posterUrl로 내려준다. 교체할 때 posterPath + getTmdbImageUrl로 바꾼다.
 */
import type { AvailabilityStatus } from '@/components/common/displayTypes'

// 임시: 계약 반영 후 생성 타입으로 교체 — 작품 구분. TMDB는 영화(movie)와 TV(tv)의 ID 공간이 달라 함께 써야 작품이 정해진다
export type MediaType = 'movie' | 'tv'

// 임시: 계약 반영 후 생성 타입으로 교체 — 서비스 하나의 제공 상태. 서비스 이름은 서비스 목록 API에서 ID로 찾는다
export interface ServiceAvailability {
  ottServiceId: number // 서비스 내부 ID(서비스 목록 API의 ottServiceId)
  status: AvailabilityStatus // 이 작품이 이 서비스에서 구독으로 제공되는지
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 검색 결과의 작품 하나
export interface TitleSearchItem {
  mediaType: MediaType // 영화 / 드라마(tv)
  tmdbId: number // TMDB ID. mediaType과 함께 작품을 정한다(동명 영화·드라마를 구분한다)
  title: string // 한국어 제목
  releaseYear: number | null // 개봉(첫 방영) 연도. 모르면 null
  posterUrl: string | null // 포스터 주소. 없으면 null → "포스터 없음" 대체 표시
  availabilities: ServiceAvailability[] // 서비스별 제공 상태. 드라마는 시즌 중 하나라도 제공되면 AVAILABLE
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 검색 결과 한 페이지. 페이지 형태는 api-contract.md 페이지네이션 규칙을 따른다
export interface TitleSearchResult {
  content: TitleSearchItem[] // 이 페이지의 작품들(없으면 빈 배열)
  page: number // 현재 페이지 번호(0부터)
  size: number // 한 페이지 크기(요청한 size. 기본 20, 최대 100)
  totalElements: number // 검색된 작품 전체 수
  totalPages: number // 전체 페이지 수
  isExternalCollectFailed: boolean // 외부(TMDB) 수집이 실패해 저장된 결과(DB)만 보여 주는지
  dataAsOf: string // 데이터 기준일(YYYY-MM-DD)
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 성공 응답 래퍼(CommonResponse)
export interface CommonResponseTitleSearchResult {
  success: boolean
  data: TitleSearchResult
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 찜 우선순위. 꼭(MUST) / 보고 싶음(WANT) / 여유될 때(MAYBE) (PRD FR-09)
export type WishlistPriority = 'MUST' | 'WANT' | 'MAYBE'

// 임시: 계약 반영 후 생성 타입으로 교체 — 찜 추가 요청
export interface AddWishlistItemRequest {
  mediaType: MediaType
  tmdbId: number
  seasonNumber: number | null // 시즌 단위 찜일 때 시즌 번호. 영화는 null
  priority: WishlistPriority
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 찜 추가 성공 응답 래퍼
export interface CommonResponseAddWishlistItem {
  success: boolean
  data: { wishlistItemId: number }
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 찜 우선순위 변경 요청(PATCH /me/wishlist-items/{id})
export interface UpdateWishlistPriorityRequest {
  priority: WishlistPriority
}
