/*
 * 임시 타입 파일 (Task 027, SCR-02 작품 상세).
 *
 * 작품 상세 API와 찜 추가 API는 아직 계약(docs/api/openapi.yaml)에 없어 orval 생성 타입이 없다.
 * 그래서 frontend.md "목업 단계의 임시 타입 예외"에 따라 이 파일 한 곳에만 임시로 둔다.
 * Task 031에서 계약에 operation을 추가하고 `npm run api:generate`로 생성 타입을 만든 뒤,
 * 이 파일의 타입을 생성 타입으로 바꾸고 이 파일을 지운다(완료 조건: mockTypes.ts가 남지 않는다).
 *
 * 기능 폴더끼리는 import하지 않으므로 features/search/mockTypes.ts와 겹치는 타입(MediaType 등)은 여기에도 따로 둔다.
 * 둘 다 같은 계약 스키마의 임시 복사이고, Task 031에서 같은 생성 타입 하나로 합쳐진다.
 * 계약의 이미지 필드는 posterPath(TMDB 상대 경로)지만, 목업은 외부 이미지 요청을 막으려고 완성된 주소
 * (data URI 또는 null)를 posterUrl로 내려준다. 교체할 때 posterPath + getTmdbImageUrl로 바꾼다.
 */
import type { AvailabilityStatus } from '@/components/common/displayTypes'

// 임시: 계약 반영 후 생성 타입으로 교체 — 작품 구분(영화 / TV)
export type MediaType = 'movie' | 'tv'

// 임시: 계약 반영 후 생성 타입으로 교체 — 서비스 하나의 제공 상태
export interface ServiceAvailability {
  ottServiceId: number // 서비스 내부 ID(서비스 목록 API의 ottServiceId)
  status: AvailabilityStatus // 구독으로 제공되는지(있음/없음/모름)
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 드라마의 시즌 하나
export interface TitleSeason {
  seasonNumber: number // 시즌 번호(찜의 단위)
  name: string // 시즌 이름(예: "시즌 1")
  episodeCount: number // 회차 수
  airYear: number | null // 방영 연도. 모르면 null
  totalRuntimeMin: number // 시즌 총 시청 시간(분). 방영된 회차 러닝타임의 합
  isRuntimeEstimated: boolean // TMDB에 러닝타임이 비어 있어 기본값으로 추정했는지 → "추정" 표시
  availabilities: ServiceAvailability[] // 시즌별 서비스 제공 상태(시즌마다 제공처가 다를 수 있다)
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 출연진 한 명
export interface CastMember {
  name: string // 배우 이름
  character: string | null // 배역 이름. 모르면 null
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 줄거리 언어. 한국어 줄거리가 없으면 영어로 대체한다(overview_lang, PRD FR-03)
export type OverviewLanguage = 'ko' | 'en'

// 임시: 계약 반영 후 생성 타입으로 교체 — 작품 상세
export interface TitleDetail {
  mediaType: MediaType // 영화 / 드라마(tv)
  tmdbId: number // TMDB ID
  title: string // 한국어 제목
  originalTitle: string // 원제
  posterUrl: string | null // 포스터 주소. 없으면 null → "포스터 없음" 대체 표시
  overview: string | null // 줄거리. 아예 없으면 null
  overviewLang: OverviewLanguage | null // overview의 언어. overview가 null이면 null
  genres: string[] // 장르 이름들
  releaseDate: string | null // 개봉일(영화) 또는 첫 방영일(드라마), YYYY-MM-DD. 모르면 null
  runtimeMin: number | null // 영화 러닝타임(분). 드라마는 null(시즌별 총 시간을 쓴다)
  isRuntimeEstimated: boolean // runtimeMin이 추정값인지 → "추정" 표시
  cast: CastMember[] // 주요 출연진(상위 10명)
  availabilities: ServiceAvailability[] // 작품 전체 서비스별 제공 상태. 드라마는 시즌 중 하나라도 제공되면 AVAILABLE
  seasons: TitleSeason[] // 드라마의 시즌들. 영화는 빈 배열
  dataAsOf: string // 데이터 기준일(YYYY-MM-DD)
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 성공 응답 래퍼(CommonResponse)
export interface CommonResponseTitleDetail {
  success: boolean
  data: TitleDetail
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
