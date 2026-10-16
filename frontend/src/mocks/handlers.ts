import { delay, http, HttpResponse, type RequestHandler } from 'msw'
import { getProductMock } from '@/api/generated/product/product.msw'
import type {
  AddWishlistItemRequest,
  CommonResponseAddWishlistItemResult,
  CommonResponseTitleDetail,
  CommonResponseTitleSearchPage,
  CommonResponseUpdatedWishlistItem,
  ProblemDetail,
  TitleSearchItem,
  UpdateWishlistItemRequest,
} from '@/api/generated/model'
import { MOCK_DATA_AS_OF, TITLE_FIXTURES } from '@/mocks/fixtures/titles'
import {
  getRequestMockPageSize,
  hasMockScenario,
  issueWishlistItemId,
  mockWishlist,
  problemResponse,
  resetMockWishlist,
} from '@/mocks/handlerUtils'
import { adminHandlers } from '@/mocks/adminHandlers'
import { authHandlers } from '@/mocks/authHandlers'
import { memberHandlers } from '@/mocks/memberHandlers'
import { getMockRole } from '@/mocks/mockSession'

// MSW 요청 핸들러 모음. 목업 모드(VITE_USE_MOCK=true)에서 브라우저가 이 핸들러로 /api 요청을 가로챈다.
// orval이 계약(docs/api/openapi.yaml)의 operation마다 태그별 핸들러를 생성하고, 여기서 한 배열로 펼친다.
// 생성된 목업 응답은 계약의 example을 그대로 쓴다. 그래서 서비스 목록(listOttServices)은 별도 픽스처 없이
// 실제 7개 서비스 값으로 응답한다(Task 019).
// 화면 확인용 현실적인 데이터(실제 작품명, 3개월 플랜)는 src/mocks/fixtures/에 직접 작성한다.

// hasMockScenario·problemResponse·찜 목록 저장소는 회원 화면 핸들러(memberHandlers.ts)와 함께 쓰려고 handlerUtils.ts로 옮겼다(Task 028).
// 찜 목록을 비우는 함수는 기존 테스트의 import 경로(@/mocks/handlers)를 유지하려고 다시 내보낸다
export { resetMockWishlist }

// 활성 찜(= "다 봤음"이 아닌 찜)의 상한. PRD 11절 36번
const WISHLIST_ACTIVE_LIMIT = 20

/*
 * 시나리오 핸들러. 생성 핸들러(getProductMock)보다 배열 앞에 둔다.
 * MSW는 배열 앞에서부터 주소가 맞는 핸들러를 찾고, 핸들러가 아무것도 돌려주지 않으면(undefined) 다음 핸들러로 넘어간다.
 * 그래서 시나리오 헤더가 없을 때는 아무것도 돌려주지 않아 원래의 생성 목업(정상 응답)이 그대로 쓰인다.
 * 시나리오는 계약에 없는 화면 검토용 값이라 쿼리 파라미터가 아니라 요청 헤더(X-Mock-Scenario)로 받는다(src/lib/mockScenario.ts).
 */
const scenarioHandlers: RequestHandler[] = [
  // rate-limit: 서비스 목록(listOttServices)이 429 요청 제한 오류를 돌려준다(Task 025, 429 토스트 확인용)
  http.get('/api/public/ott-services', ({ request }) => {
    if (!hasMockScenario(request, 'rate-limit')) return

    // 백엔드 ProblemDetail과 같은 모양(docs/api/error-codes.md). 생성 타입으로 검사해 계약과 어긋나지 않게 한다
    const body: ProblemDetail = {
      title: 'Too Many Requests',
      status: 429,
      detail: '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.',
      instance: '/api/public/ott-services',
      errorCode: 'RATE_LIMIT_EXCEEDED',
    }
    // HttpResponse.json은 기본 Content-Type이 application/json이다. 실제 백엔드처럼 problem+json으로 명시해야
    // 인터셉터(api/http.ts)가 ProblemDetail로 인식한다
    return HttpResponse.json(body, {
      status: 429,
      headers: { 'Content-Type': 'application/problem+json' },
    })
  }),
]

/*
 * ===== 공개 화면 + 찜 추가·변경·삭제 목업 핸들러 (Task 027, 031 6-2) =====
 *
 * 경로·요청·응답은 확정된 계약(docs/api/openapi.yaml)과 같다. 응답 타입은 생성 모델을 쓰므로 계약과 어긋나면 컴파일에서 걸린다.
 * 이 핸들러를 기존 구조 그대로 둔 이유: 생성 목업(*.msw.ts)은 계약의 example 한 가지만 돌려줘서, 검색어에 따른 필터·찜 상태 저장·
 * 20개 상한 같은 "화면 확인용 동작"을 낼 수 없다. 그래서 응답 모양만 생성 모델에 맞추고 동작은 직접 쓴다.
 * - 검색:      GET  /api/public/titles?q=제목&page=0&size=20   (비로그인 공개, 요청 제한 대상)
 * - 작품 상세: GET  /api/public/titles/{mediaType}/{tmdbId}
 * - 찜 추가:   POST   /api/me/wishlist-items      새로 추가 201, 이미 찜한 작품(시즌)이면 200(PRD FR-09). 활성 찜이 20개일 때 새 항목은 409 WISHLIST_LIMIT_EXCEEDED
 * - 찜 변경:   PATCH  /api/me/wishlist-items/{id} ({ priority?, isWatched? } 보낸 것만 바꾼다). "다 봤음"을 되돌릴 때 활성 찜이 20개면 409
 * - 찜 삭제:   DELETE /api/me/wishlist-items/{id} 204 (이미 없어도 204)
 * 시나리오(X-Mock-Scenario 헤더): collect-failed(검색, 외부 수집 실패 → DB 결과만), error(검색·상세, 500 오류),
 * wishlist-full(찜 추가·되돌리기가 "활성 찜 20개"로 간주해 409를 확인한다).
 */

// 검색 결과 한 줄(목록용 요약)을 상세 데이터에서 뽑는다. 시즌·출연진 같은 무거운 값은 목록에 싣지 않는다
function toSearchItem(detail: (typeof TITLE_FIXTURES)[number]): TitleSearchItem {
  return {
    mediaType: detail.mediaType,
    tmdbId: detail.tmdbId,
    title: detail.title,
    releaseYear: detail.releaseDate ? Number(detail.releaseDate.slice(0, 4)) : null,
    posterPath: detail.posterPath,
    availabilities: detail.availabilities,
  }
}

// 지금 활성 찜(= "다 봤음"이 아닌 찜)의 개수. wishlist-full 시나리오에서는 상한과 같은 20개로 본다
function countActiveWishlist(request: Request): number {
  if (hasMockScenario(request, 'wishlist-full')) return WISHLIST_ACTIVE_LIMIT
  return [...mockWishlist.values()].filter((entry) => entry.watchedAt === null).length
}

const publicScreenHandlers: RequestHandler[] = [
  // 작품 검색: 제목(한국어·원제)에 검색어가 들어 있으면 결과에 넣는다(대소문자 무시)
  http.get('/api/public/titles', async ({ request }) => {
    await delay() // 로딩 상태가 눈에 보이도록 실제 서버처럼 잠깐 걸린다(테스트(Node)에서는 거의 즉시)
    if (hasMockScenario(request, 'error')) {
      return problemResponse(
        request,
        500,
        'Internal Server Error',
        'INTERNAL_ERROR',
        '일시적인 오류가 발생했습니다.',
      )
    }

    const query = (new URL(request.url).searchParams.get('q') ?? '').trim().toLowerCase()
    if (query === '') {
      return problemResponse(
        request,
        400,
        'Bad Request',
        'VALIDATION_FAILED',
        '검색어를 입력해 주세요.',
      )
    }

    // 페이지 파라미터 검증(api-contract.md): page는 0 이상 정수(기본 0), size는 1~100 정수(기본 20). 벗어나면 400
    const searchParams = new URL(request.url).searchParams
    const page = Number(searchParams.get('page') ?? 0)
    // 목업 확인용: X-Mock-Page-Size 헤더가 있으면 한 페이지 크기를 그 값으로 줄여 "더보기"를 눈으로 확인한다(요청 size는 무시)
    const mockPageSize = getRequestMockPageSize(request)
    const size = mockPageSize ?? Number(searchParams.get('size') ?? 20)
    if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 100) {
      return problemResponse(
        request,
        400,
        'Bad Request',
        'VALIDATION_FAILED',
        'page는 0 이상, size는 1~100 사이여야 합니다.',
      )
    }

    // 목업 확인용: 검색어가 '*'이면 모든 픽스처를 돌려준다(그리드·더보기를 한 번에 보려는 장치).
    // 제목 부분 일치만으로는 한 번에 여러 건이 나오는 검색어가 없어서 추가했다.
    const matched = TITLE_FIXTURES.filter(
      (t) =>
        query === '*' ||
        t.title.toLowerCase().includes(query) ||
        t.originalTitle.toLowerCase().includes(query),
    ).map(toSearchItem)
    const body: CommonResponseTitleSearchPage = {
      success: true,
      data: {
        content: matched.slice(page * size, (page + 1) * size),
        page,
        size,
        totalElements: matched.length,
        totalPages: Math.ceil(matched.length / size),
        // collect-failed: 외부(TMDB) 수집이 실패해 DB에 있는 결과만 돌려주는 상황(PRD SCR-01)
        isExternalCollectFailed: hasMockScenario(request, 'collect-failed'),
        dataAsOf: MOCK_DATA_AS_OF,
      },
    }
    return HttpResponse.json(body)
  }),

  // 작품 상세: mediaType과 tmdbId가 모두 맞는 작품을 찾는다(동명 영화·드라마를 구분하려고 둘 다 본다)
  http.get('/api/public/titles/:mediaType/:tmdbId', async ({ request, params }) => {
    await delay()
    if (hasMockScenario(request, 'error')) {
      return problemResponse(
        request,
        500,
        'Internal Server Error',
        'INTERNAL_ERROR',
        '일시적인 오류가 발생했습니다.',
      )
    }

    const found = TITLE_FIXTURES.find(
      (t) => t.mediaType === params.mediaType && String(t.tmdbId) === params.tmdbId,
    )
    if (!found) {
      return problemResponse(
        request,
        404,
        'Not Found',
        'TITLE_NOT_FOUND',
        '작품을 찾을 수 없습니다.',
      )
    }
    const body: CommonResponseTitleDetail = { success: true, data: found }
    return HttpResponse.json(body)
  }),

  // 찜 추가: 비로그인이면 401 UNAUTHORIZED. 실제 인증과 연결하지 않고 목업 로그인 역할(mockSession)만 본다.
  // 같은 작품(시즌)을 다시 추가해도 오류 없이 200으로 기존 항목 ID를 돌려준다(PRD FR-09 "중복 추가는 무시된다").
  // 활성 찜이 20개일 때 "새" 항목은 409지만 중복은 200이다(계약: 이미 20개여도 중복 추가는 409가 아니라 200)
  http.post('/api/me/wishlist-items', async ({ request }) => {
    await delay()
    if (getMockRole() === 'GUEST') {
      return problemResponse(request, 401, 'Unauthorized', 'UNAUTHORIZED', '로그인이 필요합니다.')
    }

    const requestBody = (await request.json()) as AddWishlistItemRequest
    // 영화는 seasonNumber가 null, 드라마(tv)는 시즌 번호가 필수다
    const isSeasonMismatch =
      requestBody.mediaType === 'tv'
        ? requestBody.seasonNumber === null
        : requestBody.seasonNumber !== null
    if (isSeasonMismatch) {
      return problemResponse(
        request,
        400,
        'Bad Request',
        'VALIDATION_FAILED',
        '드라마는 시즌 번호가 필요하고, 영화는 시즌 번호가 없어야 합니다.',
      )
    }
    const title = TITLE_FIXTURES.find(
      (t) => t.mediaType === requestBody.mediaType && t.tmdbId === requestBody.tmdbId,
    )
    const hasSeason =
      requestBody.seasonNumber === null ||
      title?.seasons.some((season) => season.seasonNumber === requestBody.seasonNumber)
    if (!title || !hasSeason) {
      return problemResponse(
        request,
        404,
        'Not Found',
        'TITLE_NOT_FOUND',
        '작품을 찾을 수 없습니다.',
      )
    }
    // 이미 찜한 같은 작품(시즌)이면 새로 만들지 않고 기존 항목을 200으로 돌려준다
    const existing = [...mockWishlist.entries()].find(
      ([, item]) =>
        item.mediaType === requestBody.mediaType &&
        item.tmdbId === requestBody.tmdbId &&
        item.seasonNumber === requestBody.seasonNumber,
    )
    if (!existing && countActiveWishlist(request) >= WISHLIST_ACTIVE_LIMIT) {
      return problemResponse(
        request,
        409,
        'Conflict',
        'WISHLIST_LIMIT_EXCEEDED',
        '찜은 20개까지 담을 수 있습니다.',
      )
    }
    const wishlistItemId = existing ? existing[0] : issueWishlistItemId()
    if (!existing) mockWishlist.set(wishlistItemId, { ...requestBody, watchedAt: null })
    const body: CommonResponseAddWishlistItemResult = { success: true, data: { wishlistItemId } }
    return HttpResponse.json(body, { status: existing ? 200 : 201 })
  }),

  // 찜 변경: 비로그인이면 401, 없는 항목이면 404, 되돌릴 때 활성 찜이 20개면 409
  http.patch('/api/me/wishlist-items/:id', async ({ request, params }) => {
    await delay()
    if (getMockRole() === 'GUEST') {
      return problemResponse(request, 401, 'Unauthorized', 'UNAUTHORIZED', '로그인이 필요합니다.')
    }
    const id = Number(params.id)
    const current = mockWishlist.get(id)
    if (!current) {
      return problemResponse(
        request,
        404,
        'Not Found',
        'WISHLIST_ITEM_NOT_FOUND',
        '찜 항목을 찾을 수 없습니다.',
      )
    }
    // 우선순위(priority)와 "다 봤음"(isWatched)을 보낸 것만 바꾼다. 다 봤음 시각은 목업이라 요청 시각을 그대로 쓴다.
    // 이미 "다 봤음"인 항목에 true를 보내도 watchedAt은 처음 값을 유지한다(계약)
    const { priority = current.priority, isWatched } =
      (await request.json()) as UpdateWishlistItemRequest
    // "다 봤음"을 되돌리면 활성 찜이 하나 늘어난다. 이미 20개면 되돌릴 수 없다(PRD 11절 36번)
    if (
      isWatched === false &&
      current.watchedAt !== null &&
      countActiveWishlist(request) >= WISHLIST_ACTIVE_LIMIT
    ) {
      return problemResponse(
        request,
        409,
        'Conflict',
        'WISHLIST_LIMIT_EXCEEDED',
        '찜은 20개까지 담을 수 있습니다.',
      )
    }
    let watchedAt = current.watchedAt
    if (isWatched === true && current.watchedAt === null) watchedAt = new Date().toISOString()
    if (isWatched === false) watchedAt = null
    mockWishlist.set(id, { ...current, priority, watchedAt })
    const body: CommonResponseUpdatedWishlistItem = {
      success: true,
      data: { wishlistItemId: id, priority, watchedAt },
    }
    return HttpResponse.json(body)
  }),

  // 찜 삭제: 비로그인이면 401. 이미 없는 항목도 오류 없이 204로 답한다(삭제는 여러 번 해도 결과가 같다)
  http.delete('/api/me/wishlist-items/:id', async ({ request, params }) => {
    await delay()
    if (getMockRole() === 'GUEST') {
      return problemResponse(request, 401, 'Unauthorized', 'UNAUTHORIZED', '로그인이 필요합니다.')
    }
    mockWishlist.delete(Number(params.id))
    return new HttpResponse(null, { status: 204 })
  }),
]

export const handlers: RequestHandler[] = [
  ...scenarioHandlers,
  ...authHandlers, // 재발급·로그아웃(Task 057)
  ...publicScreenHandlers,
  ...memberHandlers,
  ...adminHandlers, // 관리자 화면(Task 029). 역할을 검사하지 않는다(adminHandlers 주석)
  ...getProductMock(),
]
