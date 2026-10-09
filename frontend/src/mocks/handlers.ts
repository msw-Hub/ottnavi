import { delay, http, HttpResponse, type RequestHandler } from 'msw'
import { getProductMock } from '@/api/generated/product/product.msw'
import type { ProblemDetail } from '@/api/generated/model'
import type {
  AddWishlistItemRequest,
  CommonResponseAddWishlistItem,
  CommonResponseTitleSearchResult,
  TitleSearchItem,
  UpdateWishlistPriorityRequest,
} from '@/features/search/mockTypes'
import type { CommonResponseTitleDetail } from '@/features/title-detail/mockTypes'
import { MOCK_SCENARIO_PARAM } from '@/lib/mockScenario'
import { MOCK_DATA_AS_OF, TITLE_FIXTURES } from '@/mocks/fixtures/titles'
import { getMockRole } from '@/mocks/mockSession'

// MSW 요청 핸들러 모음. 목업 모드(VITE_USE_MOCK=true)에서 브라우저가 이 핸들러로 /api 요청을 가로챈다.
// orval이 계약(docs/api/openapi.yaml)의 operation마다 태그별 핸들러를 생성하고, 여기서 한 배열로 펼친다.
// 생성된 목업 응답은 계약의 example을 그대로 쓴다. 그래서 서비스 목록(listOttServices)은 별도 픽스처 없이
// 실제 7개 서비스 값으로 응답한다(Task 019).
// 새 태그의 operation이 생기면(Task 031 등) 아래 배열에 그 태그의 목업을 추가한다.
// 예: import { getCatalogMock } from '@/api/generated/catalog/catalog.msw'
//     export const handlers: RequestHandler[] = [...scenarioHandlers, ...getProductMock(), ...getCatalogMock()]
// 화면 확인용 현실적인 데이터(실제 작품명, 3개월 플랜)는 src/mocks/fixtures/에 직접 작성한다.

// 오류 상황을 재현하는 목업 시나리오를 고르는 쿼리 파라미터 이름. 예: /api/public/ott-services?mockScenario=rate-limit
// 계약에 없는 확인용 API를 새로 만들지 않고, 이미 있는 API에 이 파라미터를 붙여 오류 응답을 받는다.
// 이름은 features의 훅도 써야 해서 msw를 모르는 src/lib/mockScenario.ts로 옮겼다(Task 027). 화면 주소의 같은 파라미터가
// 검색·상세 API 요청에 실려 간다. 여기서는 다시 내보내 기존 import 경로를 유지한다.
export { MOCK_SCENARIO_PARAM }

// 요청 URL에 지정한 시나리오가 붙어 있는지 확인한다
function hasMockScenario(request: Request, scenario: string): boolean {
  return new URL(request.url).searchParams.get(MOCK_SCENARIO_PARAM) === scenario
}

/*
 * 시나리오 핸들러. 생성 핸들러(getProductMock)보다 배열 앞에 둔다.
 * MSW는 배열 앞에서부터 주소가 맞는 핸들러를 찾고, 핸들러가 아무것도 돌려주지 않으면(undefined) 다음 핸들러로 넘어간다.
 * 그래서 시나리오 파라미터가 없을 때는 아무것도 돌려주지 않아 원래의 생성 목업(정상 응답)이 그대로 쓰인다.
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
 * ===== 공개 화면 목업 핸들러 (Task 027) =====
 *
 * 임시 경로 주의: 아래 세 API는 아직 계약(docs/api/openapi.yaml)에 없다. 경로·요청·응답 모양은 화면을 먼저 만들기 위한 제안이며
 * Task 031에서 계약으로 확정된다. 그때 orval이 만드는 생성 목업으로 바뀌면 이 핸들러와 fixtures/는 지운다.
 * - 검색:      GET  /api/public/titles?q=제목&page=0&size=20   (비로그인 공개, 요청 제한 대상. 임시 경로·형태, Task 031에서 확정.
 *              응답은 페이지 형태 { content, page, size, totalElements, totalPages } + 수집 실패 여부·기준일)
 * - 작품 상세: GET  /api/public/titles/{mediaType}/{tmdbId}   (api-contract.md 경로 규칙: 작품은 TMDB 기준)
 * - 찜 추가:   POST   /api/me/wishlist-items          (로그인 필요. 임시 경로, Task 031에서 확정)
 * - 우선순위 변경: PATCH  /api/me/wishlist-items/{id}  (로그인 필요. 임시 경로, Task 031에서 확정)
 * - 찜 해제:   DELETE /api/me/wishlist-items/{id}  (로그인 필요. 임시 경로, Task 031에서 확정)
 * 시나리오(?mockScenario=): collect-failed(검색, 외부 수집 실패 → DB 결과만), error(검색·상세, 500 오류).
 */

// 검색 결과 한 줄(목록용 요약)을 상세 데이터에서 뽑는다. 시즌·출연진 같은 무거운 값은 목록에 싣지 않는다
function toSearchItem(detail: (typeof TITLE_FIXTURES)[number]): TitleSearchItem {
  return {
    mediaType: detail.mediaType,
    tmdbId: detail.tmdbId,
    title: detail.title,
    releaseYear: detail.releaseDate ? Number(detail.releaseDate.slice(0, 4)) : null,
    posterUrl: detail.posterUrl,
    availabilities: detail.availabilities,
  }
}

// 백엔드 ProblemDetail과 같은 모양의 오류 응답을 만든다(Content-Type이 problem+json이어야 api/http.ts가 인식한다)
function problemResponse(
  request: Request,
  status: number,
  title: string,
  errorCode: string,
  detail: string,
) {
  const body: ProblemDetail = {
    title,
    status,
    detail,
    instance: new URL(request.url).pathname,
    errorCode,
  }
  return HttpResponse.json(body, {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

// 목업 서버가 메모리에 들고 있는 찜 목록(찜 항목 ID → 내용). 새로고침(모듈 다시 읽기) 전까지만 유지된다.
// 화면의 찜 상태는 요청이 성공할 때마다 프론트 캐시를 고쳐 맞춘다(features/*/hooks/useWishlistItem)
const mockWishlist = new Map<number, AddWishlistItemRequest>()
let nextWishlistItemId = 1

// 목업 찜 목록을 비운다(테스트가 서로 영향을 주지 않게 쓴다)
export function resetMockWishlist(): void {
  mockWishlist.clear()
  nextWishlistItemId = 1
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
    const size = Number(searchParams.get('size') ?? 20)
    if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 100) {
      return problemResponse(
        request,
        400,
        'Bad Request',
        'VALIDATION_FAILED',
        'page는 0 이상, size는 1~100 사이여야 합니다.',
      )
    }

    // 목업 확인용: 검색어가 '*'이면 모든 픽스처를 돌려준다(그리드·더보기를 한 번에 보려는 장치, Task 031 이후 제거 가능).
    // 제목 부분 일치만으로는 한 번에 여러 건이 나오는 검색어가 없어서 추가했다.
    const matched = TITLE_FIXTURES.filter(
      (t) =>
        query === '*' ||
        t.title.toLowerCase().includes(query) ||
        t.originalTitle.toLowerCase().includes(query),
    ).map(toSearchItem)
    const body: CommonResponseTitleSearchResult = {
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
  // 같은 작품(시즌)을 다시 추가해도 오류 없이 성공으로 답한다(PRD FR-09 "중복 추가는 무시된다")
  http.post('/api/me/wishlist-items', async ({ request }) => {
    await delay()
    if (getMockRole() === 'GUEST') {
      return problemResponse(request, 401, 'Unauthorized', 'UNAUTHORIZED', '로그인이 필요합니다.')
    }

    const requestBody = (await request.json()) as AddWishlistItemRequest
    const exists = TITLE_FIXTURES.some(
      (t) => t.mediaType === requestBody.mediaType && t.tmdbId === requestBody.tmdbId,
    )
    if (!exists) {
      return problemResponse(
        request,
        404,
        'Not Found',
        'TITLE_NOT_FOUND',
        '작품을 찾을 수 없습니다.',
      )
    }
    // 이미 찜한 같은 작품(시즌)이면 새로 만들지 않고 기존 항목을 돌려준다
    const existing = [...mockWishlist.entries()].find(
      ([, item]) =>
        item.mediaType === requestBody.mediaType &&
        item.tmdbId === requestBody.tmdbId &&
        item.seasonNumber === requestBody.seasonNumber,
    )
    const wishlistItemId = existing ? existing[0] : nextWishlistItemId++
    if (!existing) mockWishlist.set(wishlistItemId, requestBody)
    const body: CommonResponseAddWishlistItem = { success: true, data: { wishlistItemId } }
    return HttpResponse.json(body, { status: existing ? 200 : 201 })
  }),

  // 우선순위 변경: 비로그인이면 401, 없는 항목이면 404
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
    const { priority } = (await request.json()) as UpdateWishlistPriorityRequest
    mockWishlist.set(id, { ...current, priority })
    return HttpResponse.json({ success: true, data: { wishlistItemId: id, priority } })
  }),

  // 찜 해제: 비로그인이면 401. 이미 없는 항목도 오류 없이 성공으로 답한다(삭제는 여러 번 해도 결과가 같다)
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
  ...publicScreenHandlers,
  ...getProductMock(),
]
