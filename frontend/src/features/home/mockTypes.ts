/*
 * 홈·랭킹·탐색 1차안이 쓰는 임시 타입이다(frontend.md "목업 단계의 임시 타입 예외").
 *
 * 왜 임시인가: 홈의 공통 목록(인기 순위, 찜 많은 작품, 새로 볼 수 있게 된 작품, 독점)은 아직 계약(docs/api/openapi.yaml)에 없다.
 * 1차안은 화면을 먼저 보고 방향을 정하는 단계라, 화면에 필요한 값의 모양만 여기 적고 정적 샘플(sampleData.ts)로 채운다.
 * 계약이 정해지면 이 파일을 지우고 생성 타입으로 바꾼다(그때 찾기 쉽게 이 파일 하나에만 둔다).
 * 이미 계약에 있는 값(작품 구분 MediaType, 제공 상태 ServiceAvailability, 서비스 OttService)은 처음부터 생성 타입을 쓴다.
 */
import type { MediaType, OttService, ServiceAvailability } from '@/api/generated/model'

// 임시: 계약 반영 후 생성 타입으로 교체
// 홈 목록이 쓰는 서비스 정보. 서비스 목록 API(listOttServices)의 필드 일부만 쓴다(로고는 1차안에서 쓰지 않는다)
export type HomeService = Pick<OttService, 'ottServiceId' | 'code' | 'name' | 'dataQuality'>

// 임시: 계약 반영 후 생성 타입으로 교체
// 탐색 화면의 장르 칩 값(TMDB 장르를 화면용으로 묶은 이름)
export type HomeGenre =
  'ANIMATION' | 'VARIETY' | 'DOCUMENTARY' | 'CRIME' | 'ROMANCE' | 'SF_FANTASY' | 'COMEDY'

// 임시: 계약 반영 후 생성 타입으로 교체
// 목록 카드 하나가 보여 주는 작품 정보. 평점은 화면에 보이지 않으므로(PRD 3절 비목표) 필드도 두지 않는다
export interface HomeTitle {
  mediaType: MediaType // 영화(movie) / 드라마(tv). 상세 주소와 카드의 "영화/드라마" 글자에 쓴다
  tmdbId: number // 상세 주소에 쓰는 TMDB ID
  title: string // 한국어 제목
  releaseYear: number | null // 개봉·첫 방영 연도(모르면 null)
  posterUrl: string | null // 완성된 포스터 주소. 1차안은 외부 요청 없이 data URI만 쓴다. 없으면 null → "포스터 없음"
  genres: HomeGenre[] // 탐색 화면 장르 칩으로 거를 때 쓴다
  availabilities: ServiceAvailability[] // 서비스별 제공 상태(7개). 작품 검색 API와 같은 모양이다
}

// 임시: 계약 반영 후 생성 타입으로 교체
// 찜이 많은 작품 한 줄. 사용자 수만 보이고 누가 찜했는지는 보이지 않는다(PRD 9절 고도화 후보 "숫자만 노출")
export interface HomeWishlistedTitle {
  title: HomeTitle
  wishlistUserCount: number // 이 작품을 찜한 사용자 수("다 봤음" 제외)
}

// 임시: 계약 반영 후 생성 타입으로 교체
// 새로 볼 수 있게 된 작품 한 줄(availability_change에서 "없음/모름 → 있음"으로 바뀐 기록. 첫 적재분은 제외)
export interface HomeNewArrival {
  title: HomeTitle
  ottServiceId: number // 새로 볼 수 있게 된 서비스
  availableSince: string // 처음 "있음"으로 확인된 날('YYYY-MM-DD'). 실제 서비스에 들어온 날이 아니라 우리가 확인한 날이다
}

// 임시: 계약 반영 후 생성 타입으로 교체
// 로그인한 사용자의 현재 플랜 요약(홈 상단 "내 플랜" 자리). 플랜이 없으면 null
export interface HomePlanSummary {
  planTypeLabel: string // 저장한 플랜 유형 이름(추천형 / 절약형 / 간편형)
  monthLabel: string // 이번 달(예: "10월")
  serviceNames: string[] // 이번 달 구독할 서비스 이름
  monthlyCost: number // 이번 달 결제 예상 금액(원)
  mustWatchDone: number // 3개월 안에 끝까지 볼 수 있다고 계산된 "꼭 볼 작품" 수
  mustWatchTotal: number // 찜한 "꼭 볼 작품" 수
  calculatedAt: string // 계산한 날('YYYY-MM-DD')
}

// 임시: 계약 반영 후 생성 타입으로 교체
// 인기 순위의 기간. TODAY: 오늘 하루의 TMDB 인기도 / WEEK: 최근 7일 인기도 평균(홈 1차안 피드백: 순위는 하루 또는 최근 1주일 기준)
export type HomePopularityPeriod = 'TODAY' | 'WEEK'
