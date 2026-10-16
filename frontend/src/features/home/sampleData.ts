/*
 * 홈·랭킹·탐색 1차안의 정적 샘플 데이터다. 서버·MSW 핸들러 없이 화면 모양만 확인하려고 둔다.
 *
 * - 작품 제목은 모두 **가상 작품**이다. 실제 작품명에 임의의 제공 현황·찜 수를 붙이면 사실처럼 보일 수 있어 피했다.
 *   tmdbId도 임의 값(9100xx)이라 카드를 누르면 상세 화면이 "찾을 수 없음"으로 나온다(1차안 한계).
 * - 포스터는 외부(TMDB) 요청 없이 목업 포스터 도우미(mocks/fixtures/posters)의 data URI를 쓴다. 한 작품은 포스터 없음(null)으로 둬
 *   대체 표시도 함께 본다.
 * - 쿠팡플레이(7번)는 데이터 부족 서비스다. 영화는 항상 "모름"(구조적 공백, 기존 규칙)이고, 드라마도 따로 적지 않으면 "모름"이다.
 * - 계약이 정해지면 이 파일은 지우고 API 응답으로 바꾼다. 화면 컴포넌트는 모두 props로 데이터를 받으므로 이 파일만 바꾸면 된다.
 */
import type { AvailabilityStatus, MediaType, ServiceAvailability } from '@/api/generated/model'
import type {
  HomeGenre,
  HomeNewArrival,
  HomePlanSummary,
  HomePopularityPeriod,
  HomeService,
  HomeTitle,
  HomeWishlistedTitle,
} from '@/features/home/mockTypes'
import { createMockPosterUrl } from '@/mocks/fixtures/posters'

// 데이터 기준일. 모든 샘플 목록이 같은 날 계산된 것으로 본다
export const HOME_DATA_AS_OF = '2026-10-16'

// 국내 OTT 7곳. 순서가 탭 순서다(서비스 목록 API의 displayOrder와 같은 순서)
export const HOME_SERVICES: HomeService[] = [
  { ottServiceId: 1, code: 'NETFLIX', name: '넷플릭스', dataQuality: 'NORMAL' },
  { ottServiceId: 2, code: 'DISNEY_PLUS', name: '디즈니+', dataQuality: 'NORMAL' },
  { ottServiceId: 3, code: 'APPLE_TV_PLUS', name: '애플 TV+', dataQuality: 'NORMAL' },
  { ottServiceId: 4, code: 'TVING', name: '티빙', dataQuality: 'NORMAL' },
  { ottServiceId: 5, code: 'WAVVE', name: '웨이브', dataQuality: 'NORMAL' },
  { ottServiceId: 6, code: 'WATCHA', name: '왓챠', dataQuality: 'NORMAL' },
  { ottServiceId: 7, code: 'COUPANG_PLAY', name: '쿠팡플레이', dataQuality: 'INSUFFICIENT' },
]

const COUPANG_PLAY_ID = 7

// 탐색 화면 장르 칩의 화면 글자. 순서가 칩 순서다
export const HOME_GENRE_LABELS: Record<HomeGenre, string> = {
  ANIMATION: '애니메이션',
  VARIETY: '예능',
  DOCUMENTARY: '다큐멘터리',
  CRIME: '범죄·스릴러',
  ROMANCE: '로맨스',
  SF_FANTASY: 'SF·판타지',
  COMEDY: '코미디',
}

/**
 * 7개 서비스의 제공 상태를 만든다.
 * 적지 않은 서비스는 "없음"이고, 쿠팡플레이는 데이터 부족이라 적지 않으면 "모름"이다(영화는 항상 "모름").
 */
function buildAvailabilities(
  mediaType: MediaType,
  availableIds: number[],
  unknownIds: number[] = [],
): ServiceAvailability[] {
  return HOME_SERVICES.map(({ ottServiceId }) => {
    // 1. 쿠팡플레이 영화는 구조적 공백이라 무엇을 적어도 "모름"이다(기존 규칙)
    if (ottServiceId === COUPANG_PLAY_ID && mediaType === 'movie') {
      return { ottServiceId, status: 'UNKNOWN' as AvailabilityStatus }
    }
    // 2. 적은 대로 있음 / 모름
    if (availableIds.includes(ottServiceId)) return { ottServiceId, status: 'AVAILABLE' }
    if (unknownIds.includes(ottServiceId)) return { ottServiceId, status: 'UNKNOWN' }
    // 3. 나머지: 쿠팡플레이는 모름, 다른 서비스는 없음
    return {
      ottServiceId,
      status: ottServiceId === COUPANG_PLAY_ID ? 'UNKNOWN' : 'NOT_AVAILABLE',
    }
  })
}

// 샘플 작품 한 개를 만든다(순서 번호로 포스터 색을 바꿔 카드끼리 구분되게 한다)
function sampleTitle(
  order: number,
  mediaType: MediaType,
  title: string,
  releaseYear: number,
  genres: HomeGenre[],
  availableIds: number[],
  options: { unknownIds?: number[]; hasPoster?: boolean } = {},
): HomeTitle {
  return {
    mediaType,
    tmdbId: 910000 + order,
    title,
    releaseYear,
    posterUrl: options.hasPoster === false ? null : createMockPosterUrl((order * 37) % 360),
    genres,
    availabilities: buildAvailabilities(mediaType, availableIds, options.unknownIds),
  }
}

/*
 * 샘플 작품 18개. 배열 순서가 "인기도(TMDB popularity) 순서"라고 가정한다.
 * 독점(한 서비스에서만 있음), 여러 곳 제공, 포스터 없음, 아주 긴 제목, 쿠팡플레이 드라마 제공을 섞었다.
 */
export const HOME_TITLES: HomeTitle[] = [
  sampleTitle(1, 'tv', '한밤의 정류장', 2025, ['CRIME'], [1]),
  sampleTitle(2, 'tv', '서울 탐정 사무소', 2024, ['CRIME', 'COMEDY'], [4, 5]),
  sampleTitle(3, 'movie', '마지막 여름의 기록', 2023, ['ROMANCE'], [1, 6]),
  sampleTitle(4, 'movie', '별을 그리는 아이', 2022, ['ANIMATION', 'SF_FANTASY'], [2]),
  sampleTitle(5, 'tv', '바다 건너 식탁', 2025, ['VARIETY'], [4]),
  sampleTitle(6, 'tv', '오래된 지도의 비밀', 2024, ['SF_FANTASY'], [3], { unknownIds: [6] }),
  sampleTitle(7, 'movie', '푸른 계단', 2021, ['ROMANCE'], [5, 6]),
  sampleTitle(8, 'tv', '강철의 계절', 2023, ['CRIME'], [1, 4, 7]),
  sampleTitle(
    9,
    'movie',
    '작은 섬의 우체국: 여름부터 겨울까지 이어진 편지들',
    2020,
    ['DOCUMENTARY'],
    [6],
  ),
  sampleTitle(10, 'tv', '로봇 정비소의 하루', 2025, ['ANIMATION', 'COMEDY'], [1, 2]),
  sampleTitle(11, 'movie', '도시의 끝, 첫 번째 밤', 2024, ['CRIME'], [5]),
  sampleTitle(12, 'tv', '라디오 스타트', 2022, ['VARIETY', 'COMEDY'], [7]),
  sampleTitle(13, 'movie', '겨울 숲 산책', 2019, ['DOCUMENTARY'], [1, 6], { hasPoster: false }),
  sampleTitle(14, 'tv', '은하 열차 정비반', 2025, ['SF_FANTASY', 'ANIMATION'], [3, 4]),
  sampleTitle(15, 'tv', '우리 동네 영웅들', 2023, ['COMEDY'], [4, 5]),
  sampleTitle(16, 'movie', '첫눈이 오면', 2025, ['ROMANCE'], [1]),
  sampleTitle(17, 'tv', '판사의 저녁', 2024, ['CRIME'], [4]),
  sampleTitle(18, 'movie', '기억 수집가', 2023, ['SF_FANTASY'], [2, 5]),
]

// tmdbId로 샘플 작품을 찾는다(아래 목록 정의용. 없는 ID를 적으면 바로 알 수 있게 예외를 던진다)
function titleById(tmdbId: number): HomeTitle {
  const found = HOME_TITLES.find((title) => title.tmdbId === tmdbId)
  if (!found) throw new Error(`샘플 작품이 없습니다: ${tmdbId}`)
  return found
}

// 찜이 많은 작품(작품 단위 사용자 수, 많은 순). 숫자만 보이고 누가 찜했는지는 보이지 않는다
export const HOME_MOST_WISHLISTED: HomeWishlistedTitle[] = [
  { title: titleById(910002), wishlistUserCount: 214 },
  { title: titleById(910001), wishlistUserCount: 198 },
  { title: titleById(910008), wishlistUserCount: 151 },
  { title: titleById(910014), wishlistUserCount: 97 },
  { title: titleById(910003), wishlistUserCount: 88 },
  { title: titleById(910010), wishlistUserCount: 64 },
  { title: titleById(910017), wishlistUserCount: 41 },
  { title: titleById(910018), wishlistUserCount: 27 },
]

// 최근 새로 볼 수 있게 된 작품(우리가 "있음"으로 처음 확인한 날 기준, 최근 순)
export const HOME_NEW_ARRIVALS: HomeNewArrival[] = [
  { title: titleById(910016), ottServiceId: 1, availableSince: '2026-10-15' },
  { title: titleById(910010), ottServiceId: 1, availableSince: '2026-10-12' },
  { title: titleById(910001), ottServiceId: 1, availableSince: '2026-10-03' },
  { title: titleById(910004), ottServiceId: 2, availableSince: '2026-10-14' },
  { title: titleById(910018), ottServiceId: 2, availableSince: '2026-10-09' },
  { title: titleById(910006), ottServiceId: 3, availableSince: '2026-10-11' },
  { title: titleById(910017), ottServiceId: 4, availableSince: '2026-10-16' },
  { title: titleById(910005), ottServiceId: 4, availableSince: '2026-10-13' },
  { title: titleById(910014), ottServiceId: 4, availableSince: '2026-10-08' },
  { title: titleById(910011), ottServiceId: 5, availableSince: '2026-10-10' },
  { title: titleById(910007), ottServiceId: 6, availableSince: '2026-10-07' },
  { title: titleById(910012), ottServiceId: 7, availableSince: '2026-10-05' },
]

// 로그인한 사용자의 현재 플랜 샘플(저장한 플랜이 있을 때)
export const HOME_PLAN_SAMPLE: HomePlanSummary = {
  planTypeLabel: '추천형',
  monthLabel: '10월',
  serviceNames: ['넷플릭스', '티빙'],
  monthlyCost: 27000,
  mustWatchDone: 3,
  mustWatchTotal: 4,
  calculatedAt: '2026-10-15',
}

// 서비스 ID로 그 서비스에서 "있음"인지 본다
function isAvailableOn(title: HomeTitle, ottServiceId: number): boolean {
  return title.availabilities.some(
    (entry) => entry.ottServiceId === ottServiceId && entry.status === 'AVAILABLE',
  )
}

/*
 * 최근 7일 평균 기준의 인기도 순서(tmdbId). 오늘 순서(HOME_TITLES 배열 순서)와 일부러 다르게 섞었다:
 * 오늘 새로 뜬 작품(한밤의 정류장·바다 건너 식탁)은 7일 평균에서는 아래로, 꾸준한 작품(강철의 계절·서울 탐정 사무소)은 위로 간다.
 */
const WEEK_POPULARITY_ORDER = [
  910008, 910002, 910003, 910010, 910001, 910014, 910004, 910016, 910015, 910005, 910018, 910007,
  910012, 910011, 910006, 910017, 910013, 910009,
]

/**
 * 인기 순위를 만든다. serviceId가 null이면 7곳 중 한 곳이라도 "있음"인 작품 전체, 아니면 그 서비스에서 "있음"인 작품만 남긴다.
 * 순서: 오늘(TODAY)은 HOME_TITLES 배열 순서, 최근 7일(WEEK)은 WEEK_POPULARITY_ORDER 순서다. 실제로는 서버가 정렬해 내려줄 값이다.
 */
export function getSamplePopularRanking(
  serviceId: number | null,
  limit: number,
  period: HomePopularityPeriod = 'TODAY',
): HomeTitle[] {
  const ordered =
    period === 'TODAY' ? HOME_TITLES : WEEK_POPULARITY_ORDER.map((tmdbId) => titleById(tmdbId))
  return ordered
    .filter((title) =>
      serviceId === null
        ? title.availabilities.some((entry) => entry.status === 'AVAILABLE')
        : isAvailableOn(title, serviceId),
    )
    .slice(0, limit)
}

/**
 * 독점 작품(확인된 범위에서 7곳 중 이 서비스에서만 "있음")을 고른다(PRD FR-04 정의).
 * "모름"은 다른 곳에 있다고 보지 않는다(확인되지 않은 것). 그래서 화면은 "확인된 범위의 독점"으로만 표현한다.
 */
export function getSampleExclusives(serviceId: number): HomeTitle[] {
  return HOME_TITLES.filter(
    (title) =>
      isAvailableOn(title, serviceId) &&
      title.availabilities.filter((entry) => entry.status === 'AVAILABLE').length === 1,
  )
}

// 서비스별 새로 볼 수 있게 된 작품(serviceId가 null이면 전체)
export function getSampleNewArrivals(serviceId: number | null): HomeNewArrival[] {
  return HOME_NEW_ARRIVALS.filter(
    (entry) => serviceId === null || entry.ottServiceId === serviceId,
  ).sort((a, b) => b.availableSince.localeCompare(a.availableSince))
}
