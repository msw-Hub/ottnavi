import { delay, http, HttpResponse, type RequestHandler } from 'msw'
import type {
  CommonResponseExclusiveTitlePage,
  CommonResponseGenreList,
  CommonResponseNewlyAvailableTitleList,
  CommonResponsePopularityRanking,
  CommonResponseTitleExplorePage,
  CommonResponseWishlistedTitleList,
  TitleSearchItem,
} from '@/api/generated/model'
import {
  getMockExclusiveTitles,
  MOCK_EXCLUSIVES_DATA_AS_OF,
  MOCK_SERVICE_ID_MAX,
  // MOCK_UNJUDGED_SERVICE_ID, // 판정하지 않는 서비스가 없어져 쓰지 않는다(fixtures/exclusives 참고)
} from '@/mocks/fixtures/exclusives'
import {
  MOCK_GENRES,
  MOCK_HOME_DATA_AS_OF,
  MOCK_POPULARITY_NOTICE,
  MOCK_POPULARITY_SNAPSHOT_DATE,
  NEW_ARRIVAL_ITEMS,
  pickGenreItems,
  pickPopularItems,
  WISHLISTED_ITEMS,
} from '@/mocks/fixtures/home'
import { hasMockScenario, problemResponse } from '@/mocks/handlerUtils'

/*
 * ===== 홈·탐색 공개 조회 목업 핸들러 (Task 108) =====
 *
 * 경로·응답은 확정된 계약(docs/api/openapi.yaml)의 홈·탐색 공개 조회와 같다. 응답 타입은 생성 모델을 쓰므로 계약과 어긋나면 컴파일에서 걸린다.
 * 생성 목업(*.msw.ts)을 쓰지 않은 이유: 생성 목업은 계약의 example 한 가지만 돌려줘서 서비스·기간·장르에 따라 달라지는 목록을
 * 낼 수 없다. 화면 확인에 필요한 현실적인 작품은 fixtures/home.ts에 있다.
 * - 인기 순위:      GET /api/public/popularity-rankings?period=&ottServiceId=&limit=
 * - 찜 많은 작품:   GET /api/public/wishlisted-titles?limit=
 * - 새 작품:        GET /api/public/newly-available-titles?ottServiceId=&limit=
 * - 장르 목록:      GET /api/public/genres
 * - 장르별 작품:    GET /api/public/genres/:genreId/titles?page=&size=
 * - 출연자별 작품:  GET /api/public/people/titles?name=  (화면에 아직 연결하지 않아 빈 페이지만 돌려준다)
 * - 서비스별 독점:  GET /api/public/ott-services/:ottServiceId/exclusive-titles?page=&size=  (Task 079. 같은 홈 공개 조회 파일에 함께 둔다)
 * 시나리오(X-Mock-Scenario 헤더): rate-limit(429 요청 제한), error(500), home-empty(찜 많은 작품·인기 순위·새 작품·독점이 모두 빈 목록, 서비스 초기 모습).
 */

// 숫자 쿼리 값을 읽는다. 없거나 정수가 아니면 undefined
function readInt(url: URL, key: string): number | undefined {
  const raw = url.searchParams.get(key)
  if (raw === null) return undefined
  const value = Number(raw)
  return Number.isInteger(value) ? value : undefined
}

// 시나리오가 오류를 요구하면 그 응답을, 아니면 undefined를 돌려준다(핸들러 첫 줄에서 쓴다)
function scenarioError(request: Request) {
  if (hasMockScenario(request, 'rate-limit')) {
    return problemResponse(
      request,
      429,
      'Too Many Requests',
      'RATE_LIMIT_EXCEEDED',
      '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.',
    )
  }
  if (hasMockScenario(request, 'error')) {
    return problemResponse(
      request,
      500,
      'Internal Server Error',
      'INTERNAL_ERROR',
      '일시적인 오류가 발생했습니다.',
    )
  }
  return undefined
}

// 페이지 번호·크기로 목록을 자르고 계약의 페이지 응답 모양으로 만든다
function toExplorePage(
  all: TitleSearchItem[],
  page: number,
  size: number,
): CommonResponseTitleExplorePage {
  return {
    success: true,
    data: {
      content: all.slice(page * size, page * size + size),
      page,
      size,
      totalElements: all.length,
      totalPages: Math.ceil(all.length / size),
      dataAsOf: MOCK_HOME_DATA_AS_OF,
    },
  }
}

export const homeHandlers: RequestHandler[] = [
  http.get('/api/public/popularity-rankings', async ({ request }) => {
    await delay() // 로딩 상태가 눈에 보이도록 실제 서버처럼 잠깐 걸린다(테스트(Node)에서는 거의 즉시)
    const failed = scenarioError(request)
    if (failed) return failed

    const url = new URL(request.url)
    const period = url.searchParams.get('period') === 'WEEK' ? 'WEEK' : 'TODAY'
    const ottServiceId = readInt(url, 'ottServiceId') ?? null
    const limit = readInt(url, 'limit') ?? 20
    const isEmpty = hasMockScenario(request, 'home-empty')

    const body: CommonResponsePopularityRanking = {
      success: true,
      data: {
        period,
        ottServiceId,
        // 빈 시나리오는 아직 집계가 한 번도 없는 상태(스냅샷 없음)를 흉내 낸다
        snapshotDate: isEmpty ? null : MOCK_POPULARITY_SNAPSHOT_DATE,
        daysCounted: isEmpty ? 0 : period === 'TODAY' ? 1 : 3,
        notice: MOCK_POPULARITY_NOTICE,
        items: isEmpty
          ? []
          : pickPopularItems(ottServiceId, period, limit).map((title, index) => ({
              rank: index + 1,
              title,
            })),
      },
    }
    return HttpResponse.json(body)
  }),

  http.get('/api/public/wishlisted-titles', async ({ request }) => {
    await delay()
    const failed = scenarioError(request)
    if (failed) return failed

    const limit = readInt(new URL(request.url), 'limit') ?? 10
    const body: CommonResponseWishlistedTitleList = {
      success: true,
      data: {
        minUserCount: 3,
        dataAsOf: MOCK_HOME_DATA_AS_OF,
        items: hasMockScenario(request, 'home-empty') ? [] : WISHLISTED_ITEMS.slice(0, limit),
      },
    }
    return HttpResponse.json(body)
  }),

  http.get('/api/public/newly-available-titles', async ({ request }) => {
    await delay()
    const failed = scenarioError(request)
    if (failed) return failed

    const url = new URL(request.url)
    const ottServiceId = readInt(url, 'ottServiceId')
    const limit = readInt(url, 'limit') ?? 20
    const items = hasMockScenario(request, 'home-empty')
      ? []
      : NEW_ARRIVAL_ITEMS.filter(
          (entry) => ottServiceId === undefined || entry.ottServiceId === ottServiceId,
        ).slice(0, limit)

    const body: CommonResponseNewlyAvailableTitleList = {
      success: true,
      data: { dataAsOf: MOCK_HOME_DATA_AS_OF, items },
    }
    return HttpResponse.json(body)
  }),

  http.get('/api/public/genres', async ({ request }) => {
    await delay()
    const failed = scenarioError(request)
    if (failed) return failed

    const body: CommonResponseGenreList = { success: true, data: MOCK_GENRES }
    return HttpResponse.json(body)
  }),

  http.get('/api/public/genres/:genreId/titles', async ({ request, params }) => {
    await delay()
    const failed = scenarioError(request)
    if (failed) return failed

    const url = new URL(request.url)
    const genre = MOCK_GENRES.find((entry) => String(entry.genreId) === params.genreId)
    const items = genre ? pickGenreItems(genre.name) : []
    return HttpResponse.json(
      toExplorePage(items, readInt(url, 'page') ?? 0, readInt(url, 'size') ?? 20),
    )
  }),

  // 서비스별 독점 목록(Task 079). 홈의 독점 줄(size=10)과 독점 화면(size=24, 더보기로 page를 올림)이 함께 쓴다.
  // 없는 서비스는 404다. 쿠팡플레이(데이터 부족)도 드라마 독점 목록을 isJudged=true로 돌려준다(영화만 판정하지 못함).
  // isJudged=false는 구버전 응답 대비 분기라 테스트가 server.use로 따로 만든다
  http.get(
    '/api/public/ott-services/:ottServiceId/exclusive-titles',
    async ({ request, params }) => {
      await delay()
      const failed = scenarioError(request)
      if (failed) return failed

      // 1. 경로·쪽 값 검증(서버와 같게): 서비스 ID는 1 이상 정수, page는 0 이상, size는 1~100. 어긋나면 400
      const url = new URL(request.url)
      const page = readInt(url, 'page') ?? 0
      const size = readInt(url, 'size') ?? 20
      const isIdValid = /^[1-9]\d{0,15}$/.test(String(params.ottServiceId))
      if (!isIdValid || page < 0 || size < 1 || size > 100) {
        return problemResponse(
          request,
          400,
          'Bad Request',
          'VALIDATION_FAILED',
          '요청 값이 올바르지 않습니다.',
        )
      }

      // 2. 목업이 모르는 서비스 ID는 404 OTT_SERVICE_NOT_FOUND
      const ottServiceId = Number(params.ottServiceId)
      if (ottServiceId > MOCK_SERVICE_ID_MAX) {
        return problemResponse(
          request,
          404,
          'Not Found',
          'OTT_SERVICE_NOT_FOUND',
          '서비스를 찾을 수 없습니다.',
        )
      }

      // 3. 모든 서비스가 판정된 목록이다(쿠팡플레이도 드라마는 판정). 빈 시나리오(home-empty)는 빈 목록
      // 이전: 쿠팡플레이는 판정하지 않아 isJudged=false와 빈 목록이었다
      // const isJudged = ottServiceId !== MOCK_UNJUDGED_SERVICE_ID
      const isJudged = true
      const all = hasMockScenario(request, 'home-empty') ? [] : getMockExclusiveTitles(ottServiceId)
      const body: CommonResponseExclusiveTitlePage = {
        success: true,
        data: {
          content: all.slice(page * size, page * size + size),
          page,
          size,
          totalElements: all.length,
          totalPages: Math.ceil(all.length / size),
          ottServiceId,
          isJudged,
          dataAsOf: MOCK_EXCLUSIVES_DATA_AS_OF,
        },
      }
      return HttpResponse.json(body)
    },
  ),

  http.get('/api/public/people/titles', async ({ request }) => {
    await delay()
    const failed = scenarioError(request)
    if (failed) return failed

    const url = new URL(request.url)
    return HttpResponse.json(
      toExplorePage([], readInt(url, 'page') ?? 0, readInt(url, 'size') ?? 20),
    )
  }),
]
