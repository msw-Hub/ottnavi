import { delay, http, HttpResponse, type RequestHandler } from 'msw'
import type {
  CommonResponseUserSettings,
  FieldError,
  UpdateMySettingsRequest,
  UserSettings,
} from '@/api/generated/model'
import type {
  CommonResponsePlanDraftList,
  CommonResponseSavedPlan,
  CreatePlanRequest,
} from '@/api/generated/model'
import type {
  CommonResponseServicePrice,
  CommonResponseServicePriceList,
  CommonResponseWishlistItemList,
  ServicePrice,
  SetMyServicePriceRequest,
  WishlistItem,
} from '@/api/generated/model'
import {
  denyGuest,
  hasMockScenario,
  mockWishlist,
  type MockWishlistEntry,
  problemResponse,
  readMockJson,
  writeMockJson,
} from '@/mocks/handlerUtils'
import { buildPlanDrafts, type PlanPriceInfo, type PlanScenario } from '@/mocks/fixtures/plan'
import {
  findMockProduct,
  MOCK_PRICE_EFFECTIVE_FROM,
  MOCK_PRICE_VERIFIED_AT,
  MOCK_PRODUCTS,
  isProductOnSale,
} from '@/mocks/fixtures/admin'
import {
  DEFAULT_USER_SETTINGS,
  EMPTY_USER_SETTINGS,
  MOCK_SERVICE_IDS,
} from '@/mocks/fixtures/settings'
import { MOCK_DATA_AS_OF, TITLE_FIXTURES } from '@/mocks/fixtures/titles'
import { isMockOnboardingDone, setMockOnboardingDone } from '@/mocks/mockSession'
import {
  MIN_MONTHLY_WATCH_MINUTES,
  MAX_MONTHLY_WATCH_MINUTES,
} from '@/features/onboarding/watchTime'

/*
 * ===== 회원 화면 목업 핸들러 (Task 028, SCR-07~10) =====
 *
 * 경로·요청·응답 모양은 확정 계약(docs/api/openapi.yaml, Task 031)을 따른다. 생성 목업(*.msw.ts)은 example 한 가지만 주므로
 * 검색 필터·찜 저장·20개 상한 같은 동작은 이 핸들러가 맡는다.
 * 모두 로그인이 필요하다(목업 로그인 역할이 GUEST면 401).
 *
 * - 내 설정:   GET  /api/me/settings                 (온보딩 전에는 isOnboardingDone=false인 빈 설정)
 *              PUT  /api/me/settings                 (저장하면 온보딩 완료. 입력 규칙 위반은 400 VALIDATION_FAILED)
 * - 찜 목록:   GET  /api/me/wishlist-items           (추가·우선순위·다 봤음 PATCH·삭제는 handlers.ts의 Task 027 핸들러를 쓴다)
 * - 요금:      GET    /api/me/service-prices                (판매 중인 상품별 요금 줄. 판매 종료 상품은 나오지 않는다)
 *              PUT    /api/me/service-prices/{productId}    ({ monthlyPrice } 내 금액으로 고침. 없거나 판매 종료인 상품은 404 SERVICE_PRICE_NOT_FOUND)
 *              DELETE /api/me/service-prices/{productId}    (고친 금액을 지우고 기본 요금으로 되돌림, 204)
 * - 플랜:      POST /api/me/plan-drafts              (유형 3종 초안을 새로 계산, 201 + 초안 3개)
 *              GET  /api/me/plan-drafts              (계산 전이거나 저장한 뒤에는 빈 목록)
 *              POST /api/me/plans                    ({ planId }로 저장, 201 + SavedPlan. 저장하면 나머지 초안은 삭제)
 *
 * 시나리오(주소의 ?mockScenario= 값을 목업 모드가 X-Mock-Scenario 요청 헤더로 보낸다):
 * - stale:      플랜 저장의 첫 요청만 409 PLAN_DRAFT_STALE를 돌려주고 두 번째부터 성공한다(자동 재계산 흐름 확인)
 * - same:       세 플랜 유형의 선택이 같다(유형마다 초안 3개를 응답하되 내용이 같다. 합쳐 표시는 프론트 몫: mergeIdenticalDrafts). same-split은 same의 별칭
 * - error:      플랜 계산이 500 오류를 돌려준다
 *
 * 화면 주소의 이 파라미터는 계약에 없는 값이라 요청 헤더(X-Mock-Scenario)로 받는다.
 * 헤더는 목업 모드에서 api/mockHeaders.ts의 요청 인터셉터가 붙인다(getMockRequestHeaders).
 */

// 요청 처리에 걸리는 시간. 테스트(Vitest)에서는 기다리지 않고, 브라우저에서는 로딩 상태가 눈에 보이게 잠깐 기다린다
function wait(ms?: number) {
  return delay(import.meta.env.MODE === 'test' ? 0 : ms)
}

// 플랜 계산은 시간이 걸릴 수 있어 진행 표시가 보이도록 더 오래 기다린다(frontend.md "플랜 계산은 진행 중 상태를 명확히")
const CALCULATE_DELAY_MS = 1500

/*
 * ----- 메모리 저장소 (설정·요금 수정값은 sessionStorage에도 적어 새로고침 후 유지) -----
 * 왜: 개발 중 주소창에 화면 주소를 직접 입력하면 페이지가 새로 로드되어 모듈 메모리가 비워졌다. 찜 목록(handlerUtils)·로그인 역할(mockSession)과
 * 같은 방식으로 sessionStorage에 적어 두고 시작할 때 되살린다. 저장소를 못 쓰면 메모리만으로 동작한다(writeMockJson이 삼킨다).
 * 플랜 계산 상태(calculationSeq 등)는 저장하지 않는다. 새로고침하면 다시 계산하는 편이 오히려 화면 흐름을 확인하기 쉽다.
 */
const SETTINGS_STORAGE_KEY = 'ottnavi:mock-settings'
const PRICE_OVERRIDES_STORAGE_KEY = 'ottnavi:mock-price-overrides-by-product'

// 저장된 설정을 읽는다. 모양이 다르면(손으로 바뀐 값 등) 없는 것으로 본다
function readStoredSettings(): UserSettings | null {
  const stored = readMockJson(SETTINGS_STORAGE_KEY)
  if (typeof stored === 'object' && stored !== null && 'subscriptions' in stored) {
    return stored as UserSettings
  }
  return null
}

// 저장된 요금 수정값을 읽는다([상품 ID, 월 요금] 쌍 목록)
function readStoredPriceOverrides(): [number, number][] {
  const stored = readMockJson(PRICE_OVERRIDES_STORAGE_KEY)
  if (!Array.isArray(stored)) return []
  return stored.filter(
    (pair): pair is [number, number] =>
      Array.isArray(pair) && typeof pair[0] === 'number' && typeof pair[1] === 'number',
  )
}

// 사용자가 저장한 내 설정. null이면 아직 저장하지 않았다
let savedSettings: UserSettings | null = readStoredSettings()

// 요금 화면에서 사용자가 고친 금액(상품 ID → 월 요금). 계약이 상품 단위로 저장한다
const priceOverrides = new Map<number, number>(readStoredPriceOverrides())

// 요금 수정값을 저장소에 적는다. 하나도 없으면 키를 지운다
function persistPriceOverrides(): void {
  writeMockJson(PRICE_OVERRIDES_STORAGE_KEY, priceOverrides.size === 0 ? null : [...priceOverrides])
}

// 플랜 계산 상태
let calculationSeq = 0 // 계산한 횟수. 플랜 ID를 새로 발급하는 기준
let hasDrafts = false // 지금 DRAFT가 있는지(계산 후 true, 저장하면 false: 나머지 DRAFT는 삭제된다)
let isStaleServed = false // stale 시나리오에서 409를 이미 한 번 돌려줬는지

// 저장한 설정을 지운다(테스트가 서로 영향을 주지 않게 쓴다)
export function resetMockSettings(): void {
  savedSettings = null
  writeMockJson(SETTINGS_STORAGE_KEY, null)
}

// 사용자가 고친 요금을 지운다
export function resetMockPricing(): void {
  priceOverrides.clear()
  persistPriceOverrides()
}

// 플랜 계산·저장 상태를 처음으로 되돌린다
export function resetMockPlans(): void {
  calculationSeq = 0
  hasDrafts = false
  isStaleServed = false
}

// 회원 화면 목업 상태를 한꺼번에 되돌린다(찜 목록은 resetMockWishlist, 로그인 역할은 resetMockSession으로 따로 되돌린다)
export function resetMockMemberState(): void {
  resetMockSettings()
  resetMockPricing()
  resetMockPlans()
}

/* ----- 도우미 ----- */

// 지금 유효한 내 설정을 돌려준다. 저장한 값이 없어도 개발용 토글로 "가입을 마친 회원"이 됐다면 기본 설정을 쓴다. 온보딩 전이면 null
function getEffectiveSettings(): UserSettings | null {
  if (savedSettings) return savedSettings
  return isMockOnboardingDone() ? DEFAULT_USER_SETTINGS : null
}

// 서비스 한 곳의 상품·금액 정보(사용자가 고친 금액 반영)
function getPriceInfo(ottServiceId: number): PlanPriceInfo {
  const product = findMockProduct(ottServiceId)
  if (!product) throw new Error(`목업 상품이 없는 서비스입니다: ${ottServiceId}`)
  const override = priceOverrides.get(product.productId)
  return {
    productId: product.productId,
    productName: product.productName,
    adminPrice: product.adminPrice,
    price: override ?? product.adminPrice,
    source: override === undefined ? 'ADMIN' : 'USER_OVERRIDE',
  }
}

// 요금 화면의 한 줄(계약 ServicePrice)을 만든다
function toServicePrice(ottServiceId: number, settings: UserSettings | null): ServicePrice {
  const info = getPriceInfo(ottServiceId)
  return {
    ottServiceId,
    productId: info.productId,
    productName: info.productName,
    adminPrice: info.adminPrice,
    userPrice: info.source === 'USER_OVERRIDE' ? info.price : null,
    appliedPrice: info.price,
    priceSource: info.source,
    subscriptionStatus:
      settings?.subscriptions.find((s) => s.ottServiceId === ottServiceId)?.status ??
      'NOT_SUBSCRIBED',
    effectiveFrom: MOCK_PRICE_EFFECTIVE_FROM,
    lastVerifiedAt: MOCK_PRICE_VERIFIED_AT,
  }
}

// 요청의 시나리오 파라미터에서 플랜 응답 모양을 고른다
function getPlanScenario(request: Request): PlanScenario {
  if (hasMockScenario(request, 'same') || hasMockScenario(request, 'same-split')) return 'same'
  return 'different'
}

// 지금 계산 순번 기준의 초안 목록을 만든다
function buildCurrentDrafts(request: Request, settings: UserSettings) {
  return buildPlanDrafts(getPlanScenario(request), { settings, calculationSeq, getPriceInfo })
}

// 요금을 고칠 수 없는 상품(없는 상품·판매 종료)이면 돌려주는 404
function servicePriceNotFound(request: Request) {
  return problemResponse(
    request,
    404,
    'Not Found',
    'SERVICE_PRICE_NOT_FOUND',
    '이 서비스의 요금 정보를 찾을 수 없습니다.',
  )
}

function validationError(request: Request, detail: string, fieldErrors?: FieldError[]) {
  return problemResponse(request, 400, 'Bad Request', 'VALIDATION_FAILED', detail, fieldErrors)
}

// 경로의 상품 ID가 1 이상 정수인지 본다(서버 @Positive). 어긋나면 fieldErrors에 담을 오류를 돌려준다
function positiveProductIdError(raw: string | readonly string[] | undefined): FieldError | null {
  const value = Number(raw)
  return Number.isInteger(value) && value >= 1
    ? null
    : { field: 'productId', message: '1 이상이어야 합니다.' }
}

/**
 * 내 월 요금 요청 본문의 금액을 서버(SetMyServicePriceRequest)와 같이 검사한다. 올바르면 null이다.
 * 소수·숫자가 아닌 값은 "형식이 올바르지 않습니다."(Jackson 형식 오류가 Bean Validation보다 먼저), 없으면 필수 오류,
 * 범위는 0 이상 1,000,000 이하다.
 */
function servicePriceBodyError(monthlyPrice: unknown): FieldError | null {
  if (monthlyPrice == null) return { field: 'monthlyPrice', message: '월 요금은 필수입니다.' }
  if (typeof monthlyPrice !== 'number' || !Number.isInteger(monthlyPrice)) {
    return { field: 'monthlyPrice', message: '형식이 올바르지 않습니다.' }
  }
  if (monthlyPrice < 0) return { field: 'monthlyPrice', message: '0 이상이어야 합니다.' }
  if (monthlyPrice > 1_000_000) {
    return { field: 'monthlyPrice', message: '1,000,000 이하여야 합니다.' }
  }
  return null
}

// 서비스 ID 범위(서비스 목록 API의 7곳). 이 밖의 ID는 서버가 "없는 서비스"로 400을 준다
const MOCK_SERVICE_ID_SET = new Set<number>(MOCK_SERVICE_IDS)

// 요금제 이름 최대 길이, 서비스 상태 최대 개수(계약 UpdateMySettingsRequest)
const MAX_PLAN_NAME_LENGTH = 30
const MAX_SUBSCRIPTION_COUNT = 7

/**
 * 저장 요청이 서버 규칙을 지키는지 검사한다. 어긋나면 필드별 오류 목록을, 맞으면 빈 배열을 돌려준다.
 * 순서는 실제 서버(USER_SETTINGS_WISHLIST_GUIDE 2.3~2.4)와 같다: 1단계 형태·범위(Bean Validation) → 2단계 서비스 간 규칙(서비스 계층).
 * 1단계에서 하나라도 어긋나면 2단계는 검사하지 않고 거절한다(서버는 1단계에서 요청이 끝난다).
 * 금액·시청 시간·결제일이 소수이면 정수가 아니라서 400이다(Number.isInteger).
 */
function validateSettingsRequest(body: UpdateMySettingsRequest): FieldError[] {
  // 1. 1단계: 형태·범위
  const shapeErrors: FieldError[] = []
  if (!Number.isInteger(body.monthlyBudget) || body.monthlyBudget < 0) {
    shapeErrors.push({ field: 'monthlyBudget', message: '0 이상의 정수여야 합니다.' })
  }
  if (
    !Number.isInteger(body.monthlyWatchMinutes) ||
    body.monthlyWatchMinutes < MIN_MONTHLY_WATCH_MINUTES ||
    body.monthlyWatchMinutes > MAX_MONTHLY_WATCH_MINUTES
  ) {
    shapeErrors.push({
      field: 'monthlyWatchMinutes',
      message: `${MIN_MONTHLY_WATCH_MINUTES} 이상 ${MAX_MONTHLY_WATCH_MINUTES} 이하의 정수여야 합니다.`,
    })
  }
  if (!body.watchPreset) {
    shapeErrors.push({ field: 'watchPreset', message: '시청 시간 방식을 골라야 합니다.' })
  }
  const subscriptions = body.subscriptions
  if (
    !Array.isArray(subscriptions) ||
    subscriptions.length < 1 ||
    subscriptions.length > MAX_SUBSCRIPTION_COUNT
  ) {
    shapeErrors.push({
      field: 'subscriptions',
      message: `서비스 상태는 1개 이상 ${MAX_SUBSCRIPTION_COUNT}개 이하여야 합니다.`,
    })
    return shapeErrors
  }
  subscriptions.forEach((s, index) => {
    if (s.planName != null && s.planName.length > MAX_PLAN_NAME_LENGTH) {
      shapeErrors.push({
        field: `subscriptions[${index}].planName`,
        message: `${MAX_PLAN_NAME_LENGTH}자 이하여야 합니다.`,
      })
    }
    const day = s.billingDay
    if (day != null && (!Number.isInteger(day) || day < 1 || day > 31)) {
      shapeErrors.push({
        field: `subscriptions[${index}].billingDay`,
        message: '1 이상 31 이하의 정수여야 합니다.',
      })
    }
  })
  if (shapeErrors.length > 0) return shapeErrors

  // 2. 2단계: 서비스 간 규칙. 없는 서비스·중복·상태별 요금제/결제일 규칙
  const ruleErrors: FieldError[] = []
  const seen = new Set<number>()
  subscriptions.forEach((s, index) => {
    if (!MOCK_SERVICE_ID_SET.has(s.ottServiceId)) {
      ruleErrors.push({
        field: `subscriptions[${index}].ottServiceId`,
        message: '존재하지 않는 서비스입니다.',
      })
    } else if (seen.has(s.ottServiceId)) {
      // 같은 서비스를 두 번 보내면 어느 쪽이 의도인지 알 수 없어 요청 전체를 거절한다
      ruleErrors.push({
        field: `subscriptions[${index}].ottServiceId`,
        message: '같은 서비스를 두 번 보낼 수 없습니다.',
      })
    }
    seen.add(s.ottServiceId)

    if (s.status === 'SUBSCRIBED') {
      if (s.planName == null || s.planName.trim() === '') {
        ruleErrors.push({
          field: `subscriptions[${index}].planName`,
          message: '구독 중인 서비스는 요금제 이름이 필요합니다.',
        })
      }
    } else if (s.planName != null || s.billingDay != null) {
      // 구독 중이 아닌데 요금제 이름·결제일을 보내면 DB CHECK에 걸리기 전에 400으로 거절한다(Task 055, 계약 설명)
      ruleErrors.push({
        field: `subscriptions[${index}]`,
        message: '구독 중이 아닌 서비스에는 요금제 이름과 결제일을 보낼 수 없습니다.',
      })
    }
  })
  return ruleErrors
}

/* ----- 찜 목록 변환 ----- */

// 찜 한 건을 목록의 한 줄로 바꾼다. 작품 정보를 못 찾으면 null이다. 드라마는 시즌 단위다(R11-19, 계약 WishlistItem)
function toWishlistListItem(
  wishlistItemId: number,
  entry: MockWishlistEntry,
  watchableServiceIds: Set<number>,
): WishlistItem | null {
  const detail = TITLE_FIXTURES.find(
    (t) => t.mediaType === entry.mediaType && t.tmdbId === entry.tmdbId,
  )
  if (!detail) return null

  // 드라마는 찜한 시즌의 값(필요 시간·제공 상태)을, 영화는 작품 값을 쓴다
  const season =
    entry.seasonNumber !== null
      ? detail.seasons.find((s) => s.seasonNumber === entry.seasonNumber)
      : undefined

  const requiredMin = season ? season.totalRuntimeMin : (detail.runtimeMin ?? 0)
  const isRuntimeEstimated = season ? season.isRuntimeEstimated : detail.isRuntimeEstimated
  const availabilities = season ? season.availabilities : detail.availabilities
  return {
    wishlistItemId,
    mediaType: detail.mediaType,
    tmdbId: detail.tmdbId,
    title: detail.title,
    posterPath: detail.posterPath,
    seasonNumber: season ? season.seasonNumber : null,
    seasonName: season ? season.name : null,
    priority: entry.priority,
    requiredMin,
    isRuntimeEstimated,
    availabilities: availabilities.map((a) => ({ ottServiceId: a.ottServiceId, status: a.status })),
    // 구독 중이거나 무료로 이용하는 서비스에서 "있음"이면 이미 볼 수 있는 작품이다
    isWatchableNow: availabilities.some(
      (a) => a.status === 'AVAILABLE' && watchableServiceIds.has(a.ottServiceId),
    ),
    watchedAt: entry.watchedAt,
  }
}

export const memberHandlers: RequestHandler[] = [
  // 내 설정 조회
  http.get('/api/me/settings', async ({ request }) => {
    await wait()
    const denied = denyGuest(request)
    if (denied) return denied

    const body: CommonResponseUserSettings = {
      success: true,
      data: getEffectiveSettings() ?? EMPTY_USER_SETTINGS,
    }
    return HttpResponse.json(body)
  }),

  // 내 설정 저장. 처음 저장하면 온보딩이 끝난 것으로 본다
  http.put('/api/me/settings', async ({ request }) => {
    await wait()
    const denied = denyGuest(request)
    if (denied) return denied

    const requestBody = (await request.json()) as UpdateMySettingsRequest
    const fieldErrors = validateSettingsRequest(requestBody)
    if (fieldErrors.length > 0) {
      return validationError(request, '요청 값이 올바르지 않습니다.', fieldErrors)
    }

    // 환산 결과(분)만 저장한다(R11-18 안 a 확정, 환산 입력값 필드는 계약에 없다).
    // 구독 중인데 결제일이 비어 있으면 1일로 채우고, 구독 중이 아니면 요금제·결제일을 비운다(계약)
    savedSettings = {
      isOnboardingDone: true,
      subscriptions: requestBody.subscriptions.map((s) =>
        s.status === 'SUBSCRIBED'
          ? { ...s, planName: s.planName?.trim() ?? null, billingDay: s.billingDay ?? 1 }
          : { ...s, planName: null, billingDay: null },
      ),
      monthlyBudget: requestBody.monthlyBudget,
      monthlyWatchMinutes: requestBody.monthlyWatchMinutes,
      watchPreset: requestBody.watchPreset,
    }
    writeMockJson(SETTINGS_STORAGE_KEY, savedSettings)
    // 로그인 콜백이 "온보딩 미완료면 SCR-07, 아니면 원래 화면"을 고를 때 본다(mockSession)
    setMockOnboardingDone(true)

    const body: CommonResponseUserSettings = { success: true, data: savedSettings }
    return HttpResponse.json(body)
  }),

  // 찜 목록 조회: 최근에 찜한 순
  http.get('/api/me/wishlist-items', async ({ request }) => {
    await wait()
    const denied = denyGuest(request)
    if (denied) return denied

    const settings = getEffectiveSettings()
    const watchableServiceIds = new Set(
      (settings?.subscriptions ?? [])
        .filter((s) => s.status === 'SUBSCRIBED' || s.status === 'FREE')
        .map((s) => s.ottServiceId),
    )

    // 최근에 찜한 순(ID가 큰 순)
    const items: WishlistItem[] = []
    for (const [id, entry] of [...mockWishlist.entries()].sort(([x], [y]) => y - x)) {
      const item = toWishlistListItem(id, entry, watchableServiceIds)
      if (item) items.push(item)
    }

    const body: CommonResponseWishlistItemList = {
      success: true,
      data: {
        items,
        // 상한(20)에 세는 개수는 "다 봤음"이 아닌 찜이다(PRD 11절 36번).
        // 화면 확인용: X-Mock-Scenario: wishlist-full이면 20개가 찬 것으로 알린다(찜 추가의 409 시나리오와 같은 이름). 20개를 직접 채우지 않고도
        // "찜 20/20"과 안내 띠를 볼 수 있다
        activeItemCount: hasMockScenario(request, 'wishlist-full')
          ? 20
          : items.filter((item) => item.watchedAt === null).length,
        maxActiveItemCount: 20,
        dataAsOf: MOCK_DATA_AS_OF,
      },
    }
    return HttpResponse.json(body)
  }),

  // 요금 확인: 상품별 요금 줄
  http.get('/api/me/service-prices', async ({ request }) => {
    await wait()
    const denied = denyGuest(request)
    if (denied) return denied

    const settings = getEffectiveSettings()
    const body: CommonResponseServicePriceList = {
      success: true,
      data: {
        // 판매 종료 상품은 목록에서 뺀다(계약: 판매 중인 상품만 준다)
        services: MOCK_PRODUCTS.filter((product) => isProductOnSale(product.productId)).map(
          (product) => toServicePrice(product.ottServiceId, settings),
        ),
      },
    }
    return HttpResponse.json(body)
  }),

  // 내 월 요금 설정(PUT). 0원 이상 100만 원 이하 정수만 받는다. 없는 상품이거나 판매 종료 상품이면 404 SERVICE_PRICE_NOT_FOUND
  http.put('/api/me/service-prices/:productId', async ({ request, params }) => {
    await wait()
    const denied = denyGuest(request)
    if (denied) return denied

    // 1. 경로 변수·본문 검사(서버 컨트롤러 단계라 상품 존재 확인(404)보다 먼저다. Task 060 실측 기록).
    //    소수 금액(9900.5)은 버림으로 통과하지 않고 형식 오류다(서버 accept-float-as-int=false)
    const idError = positiveProductIdError(params.productId)
    if (idError) return validationError(request, '요청 값이 올바르지 않습니다.', [idError])
    const { monthlyPrice } = (await request.json()) as Partial<SetMyServicePriceRequest>
    const priceError = servicePriceBodyError(monthlyPrice)
    if (priceError) return validationError(request, '요청 값이 올바르지 않습니다.', [priceError])
    const validPrice = monthlyPrice as number

    // 2. 상품 확인: 없거나 판매 종료면 404 SERVICE_PRICE_NOT_FOUND
    const product = MOCK_PRODUCTS.find((p) => p.productId === Number(params.productId))
    if (!product || !isProductOnSale(product.productId)) return servicePriceNotFound(request)

    priceOverrides.set(product.productId, validPrice)
    persistPriceOverrides()

    const body: CommonResponseServicePrice = {
      success: true,
      data: toServicePrice(product.ottServiceId, getEffectiveSettings()),
    }
    return HttpResponse.json(body)
  }),

  // 내 월 요금 되돌리기(DELETE): 고친 금액을 지우고 기본 요금을 쓴다. 고친 적이 없어도 204다(여러 번 해도 결과가 같다)
  http.delete('/api/me/service-prices/:productId', async ({ request, params }) => {
    await wait()
    const denied = denyGuest(request)
    if (denied) return denied

    const idError = positiveProductIdError(params.productId)
    if (idError) return validationError(request, '요청 값이 올바르지 않습니다.', [idError])
    const product = MOCK_PRODUCTS.find((p) => p.productId === Number(params.productId))
    if (!product || !isProductOnSale(product.productId)) return servicePriceNotFound(request)

    priceOverrides.delete(product.productId)
    persistPriceOverrides()
    return new HttpResponse(null, { status: 204 })
  }),

  // 플랜 계산(계약 createPlanDrafts): 유형 3종 초안을 새로 만든다(기존 초안은 덮어쓴다). 새 초안 3개를 201로 돌려준다
  http.post('/api/me/plan-drafts', async ({ request }) => {
    await wait(CALCULATE_DELAY_MS)
    const denied = denyGuest(request)
    if (denied) return denied

    if (hasMockScenario(request, 'error')) {
      return problemResponse(
        request,
        500,
        'Internal Server Error',
        'INTERNAL_ERROR',
        '일시적인 오류가 발생했습니다.',
      )
    }

    // 예산·시청 시간이 없으면 계산할 수 없다(PRD FR-10 수용: 없이 계산을 요청하면 400)
    const settings = getEffectiveSettings()
    if (!settings || settings.monthlyBudget === null || settings.monthlyWatchMinutes === null) {
      return validationError(request, '월 예산과 월 시청 시간을 먼저 입력해 주세요.')
    }
    // 다 봤음으로 표시한 작품은 계산에서 빠진다(PRD FR-09)
    const hasTargets = [...mockWishlist.values()].some((entry) => entry.watchedAt === null)
    if (!hasTargets) {
      return validationError(request, '계산할 찜 작품이 없습니다. 작품을 찜해 주세요.')
    }

    calculationSeq += 1
    hasDrafts = true
    const body: CommonResponsePlanDraftList = {
      success: true,
      data: { drafts: buildCurrentDrafts(request, settings) },
    }
    return HttpResponse.json(body, { status: 201 })
  }),

  // 플랜 초안 조회(계약 listPlanDrafts): 계산 전이거나 저장한 뒤에는 빈 목록이다(저장하면 나머지 DRAFT는 삭제되기 때문)
  http.get('/api/me/plan-drafts', async ({ request }) => {
    await wait()
    const denied = denyGuest(request)
    if (denied) return denied

    const settings = getEffectiveSettings()
    const body: CommonResponsePlanDraftList = {
      success: true,
      data: { drafts: hasDrafts && settings ? buildCurrentDrafts(request, settings) : [] },
    }
    return HttpResponse.json(body)
  }),

  // 플랜 저장(계약 createPlan): 본문의 planId인 초안을 ACTIVE로 만든다(유형은 초안이 안다). stale 시나리오에서는 첫 요청만 409다
  http.post('/api/me/plans', async ({ request }) => {
    await wait()
    const denied = denyGuest(request)
    if (denied) return denied

    const { planId } = (await request.json()) as CreatePlanRequest
    if (!Number.isInteger(planId) || planId < 1) {
      return validationError(request, '저장할 플랜 ID가 올바르지 않습니다.')
    }

    const settings = getEffectiveSettings()
    if (!hasDrafts || !settings) {
      return problemResponse(
        request,
        404,
        'Not Found',
        'PLAN_DRAFT_NOT_FOUND',
        '계산된 플랜을 찾을 수 없습니다.',
      )
    }

    // 계산한 뒤 데이터 수집이 새로 끝나 초안의 기준일이 낡은 상황을 흉내 낸다. 클라이언트가 다시 계산하면 두 번째 저장부터 성공한다
    if (hasMockScenario(request, 'stale') && !isStaleServed) {
      isStaleServed = true
      return problemResponse(
        request,
        409,
        'Conflict',
        'PLAN_DRAFT_STALE',
        '데이터가 갱신되어 플랜을 다시 계산해야 합니다.',
      )
    }

    // 지금 초안 중 planId가 같은 것을 찾는다. 다시 계산해 ID가 바뀐 옛 초안이나 없는 ID는 404다
    const draft = buildCurrentDrafts(request, settings).find((d) => d.planId === planId)
    if (!draft) {
      return problemResponse(
        request,
        404,
        'Not Found',
        'PLAN_DRAFT_NOT_FOUND',
        '계산된 플랜을 찾을 수 없습니다.',
      )
    }

    hasDrafts = false // 저장하면 고른 유형만 ACTIVE가 되고 나머지 DRAFT는 삭제된다(PRD 11절 35번)
    const body: CommonResponseSavedPlan = {
      success: true,
      data: {
        planId: draft.planId,
        planType: draft.planType,
        status: 'ACTIVE',
        activatedAt: new Date().toISOString(),
      },
    }
    return HttpResponse.json(body, { status: 201 })
  }),
]
