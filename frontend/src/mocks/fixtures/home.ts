/*
 * 홈·탐색 목업 데이터(Task 108). 홈의 인기 순위·찜 많은 작품·새로 볼 수 있게 된 작품·장르 목록·장르별 작품 응답을 만드는 재료다.
 *
 * 작품은 기존 작품 상세 목업(titles.ts의 TITLE_FIXTURES)을 그대로 써서, 홈 카드를 눌러 상세로 가면 실제로 상세가 열린다.
 * 여기에 "우리 DB에 아직 없는 작품" 2편을 섞는다(제공 상태 availabilities가 빈 배열, 상세는 404). 실제 인기 순위는 TMDB 결과를
 * 그대로 담아 이런 작품이 나올 수 있기 때문이다(Task 107 이월 2번). 하나는 포스터도 없다.
 * 가상 작품의 제목·ID는 실제 작품이 아니다(tmdbId 9100xx).
 */
import type {
  Genre,
  NewlyAvailableTitle,
  TitleSearchItem,
  WishlistedTitle,
} from '@/api/generated/model'
import { createMockPosterUrl } from '@/mocks/fixtures/posters'
import { MOCK_DATA_AS_OF, TITLE_FIXTURES } from '@/mocks/fixtures/titles'

// 인기 순위 집계일(스냅샷 날짜). 목업은 상세 데이터 기준일의 다음 날로 둔다
export const MOCK_POPULARITY_SNAPSHOT_DATE = '2026-10-17'

// 인기 순위 기준 안내(서버가 notice로 주는 문구와 같은 말)
export const MOCK_POPULARITY_NOTICE = 'TMDB 인기도 기준, OTT 공식 순위 아님'

// 홈 목록 응답의 데이터 기준일(제공 상태 확인일)
export const MOCK_HOME_DATA_AS_OF = MOCK_DATA_AS_OF

// 상세 목업(작품 상세 한 건)을 목록용 한 줄로 줄인다. 시즌·출연진 같은 무거운 값은 싣지 않는다
function toListItem(detail: (typeof TITLE_FIXTURES)[number]): TitleSearchItem {
  return {
    mediaType: detail.mediaType,
    tmdbId: detail.tmdbId,
    title: detail.title,
    releaseYear: detail.releaseDate ? Number(detail.releaseDate.slice(0, 4)) : null,
    posterPath: detail.posterPath,
    availabilities: detail.availabilities,
  }
}

// 우리 DB에 없는 작품(제공 상태 빈 배열). 첫 번째는 포스터도 없다
const NOT_COLLECTED_TITLES: TitleSearchItem[] = [
  {
    mediaType: 'movie',
    tmdbId: 910021,
    title: '아직 수집 전인 영화',
    releaseYear: 2026,
    posterPath: null,
    availabilities: [],
  },
  {
    mediaType: 'tv',
    tmdbId: 910022,
    title: '아직 수집 전인 드라마',
    releaseYear: 2026,
    posterPath: createMockPosterUrl(200),
    availabilities: [],
  },
]

// 목록에 쓰는 작품(기존 상세 목업 + 수집 전 작품). 배열 순서를 "오늘의 인기도 순서"로 본다
// (수집 전 작품이 홈 인기 순위 10위 안에 보이도록 앞쪽에 끼워 넣는다)
const COLLECTED_ITEMS = TITLE_FIXTURES.map(toListItem)
export const HOME_ITEMS: TitleSearchItem[] = [
  ...COLLECTED_ITEMS.slice(0, 2),
  NOT_COLLECTED_TITLES[0],
  ...COLLECTED_ITEMS.slice(2, 5),
  NOT_COLLECTED_TITLES[1],
  ...COLLECTED_ITEMS.slice(5),
]

// 한 서비스에서 "있음"인 작품인지
function isAvailableOn(item: TitleSearchItem, ottServiceId: number): boolean {
  return item.availabilities.some(
    (entry) => entry.ottServiceId === ottServiceId && entry.status === 'AVAILABLE',
  )
}

/**
 * 인기 순위 작품을 고른다. 서비스를 주면 그 서비스에서 "있음"인 작품만(수집 전 작품은 첫 번째 서비스에만 낀 것으로 본다),
 * 안 주면 전체다. 최근 7일(WEEK)은 오늘 순서를 뒤집어 두 기간의 순위가 달라 보이게 한다.
 */
export function pickPopularItems(
  ottServiceId: number | null,
  period: 'TODAY' | 'WEEK',
  limit: number,
): TitleSearchItem[] {
  const ordered = period === 'TODAY' ? HOME_ITEMS : [...HOME_ITEMS].reverse()
  return ordered
    .filter((item) =>
      ottServiceId === null
        ? true
        : item.availabilities.length === 0
          ? ottServiceId === 1
          : isAvailableOn(item, ottServiceId),
    )
    .slice(0, limit)
}

// 찜이 많은 작품(작품 단위 사용자 수, 많은 순). 수집 전 작품은 넣지 않는다(찜은 DB에 있는 작품만 가능하다)
export const WISHLISTED_ITEMS: WishlistedTitle[] = TITLE_FIXTURES.slice(0, 6).map(
  (detail, index) => ({
    title: toListItem(detail),
    wishlistUserCount: 214 - index * 31,
  }),
)

// 새로 볼 수 있게 된 작품. 입고(ARRIVAL)와 공개(RELEASE)를 섞고, 기준 서비스는 그 작품이 "있음"인 첫 서비스다
export const NEW_ARRIVAL_ITEMS: NewlyAvailableTitle[] = TITLE_FIXTURES.slice(0, 8).flatMap(
  (detail, index) => {
    const serviceId = detail.availabilities.find(
      (entry) => entry.status === 'AVAILABLE',
    )?.ottServiceId
    if (serviceId === undefined) return []
    return [
      {
        title: toListItem(detail),
        ottServiceId: serviceId,
        arrivalType: index % 3 === 2 ? 'RELEASE' : 'ARRIVAL',
        availableSince: `2026-10-${String(16 - index).padStart(2, '0')}`,
      } satisfies NewlyAvailableTitle,
    ]
  },
)

// 탐색 장르 칩(TMDB 장르). 서버가 이름 가나다순으로 주는 것과 같게 정렬해 둔다. name은 상세 목업의 genres 글자와 같다
export const MOCK_GENRES: Genre[] = [
  { genreId: 18, name: '드라마' },
  { genreId: 9648, name: '미스터리' },
  { genreId: 80, name: '범죄' },
  { genreId: 53, name: '스릴러' },
  { genreId: 16, name: '애니메이션' },
  { genreId: 28, name: '액션' },
  { genreId: 10759, name: '액션&어드벤처' },
  { genreId: 35, name: '코미디' },
]

// 장르 이름으로 작품을 고른다(상세 목업의 genres 글자에 이름이 들어 있는 작품)
export function pickGenreItems(genreName: string): TitleSearchItem[] {
  return TITLE_FIXTURES.filter((detail) => detail.genres.includes(genreName)).map(toListItem)
}
