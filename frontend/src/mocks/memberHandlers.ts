import { delay, http, HttpResponse, type RequestHandler } from 'msw'
import type {
  CommonResponseUserSettings,
  SaveUserSettingsRequest,
  UserSettings,
} from '@/features/onboarding/mockTypes'
import type {
  CommonResponsePlanDraftList,
  CommonResponseSavedPlan,
  PlanType,
} from '@/features/plan/mockTypes'
import type {
  CommonResponsePricingOverview,
  CommonResponsePricingService,
  PricingService,
  UpdatePricingRequest,
} from '@/features/pricing/mockTypes'
import type { CommonResponseWishlistList, WishlistListItem } from '@/features/wishlist/mockTypes'
import {
  denyGuest,
  getRequestMockVariant,
  getRequestMockWatchVariant,
  hasMockScenario,
  mockWishlist,
  type MockWishlistEntry,
  problemResponse,
  readMockJson,
  writeMockJson,
} from '@/mocks/handlerUtils'
import {
  buildPlanDrafts,
  PLAN_TYPES,
  type PlanPriceInfo,
  type PlanScenario,
} from '@/mocks/fixtures/plan'
import { findMockProduct, MOCK_PRICE_EFFECTIVE_FROM, MOCK_PRODUCTS } from '@/mocks/fixtures/pricing'
import { DEFAULT_USER_SETTINGS, EMPTY_USER_SETTINGS } from '@/mocks/fixtures/settings'
import { MOCK_DATA_AS_OF, TITLE_FIXTURES } from '@/mocks/fixtures/titles'
import { isMockOnboardingDone, setMockOnboardingDone } from '@/mocks/mockSession'
import {
  MIN_MONTHLY_WATCH_MINUTES,
  MAX_MONTHLY_WATCH_MINUTES,
} from '@/features/onboarding/watchTime'

/*
 * ===== 회원 화면 목업 핸들러 (Task 028, SCR-07~10) =====
 *
 * 임시 경로 주의: 아래 API는 아직 계약(docs/api/openapi.yaml)에 없다. 경로·요청·응답 모양은 화면을 먼저 만들기 위한 제안이며
 * Task 031에서 계약으로 확정된다. 그때 orval이 만드는 생성 목업으로 바뀌면 이 핸들러와 fixtures는 지운다.
 * 모두 로그인이 필요하다(목업 로그인 역할이 GUEST면 401).
 *
 * - 내 설정:   GET  /api/me/settings                 (온보딩 전에는 isOnboardingDone=false인 빈 설정)
 *              PUT  /api/me/settings                 (저장하면 온보딩 완료. 입력 규칙 위반은 400 VALIDATION_FAILED)
 * - 찜 목록:   GET  /api/me/wishlist-items           (추가·우선순위·다 봤음 PATCH·삭제는 handlers.ts의 Task 027 핸들러를 쓴다)
 * - 요금:      GET  /api/me/pricing
 *              PATCH /api/me/pricing/{ottServiceId}  ({ monthlyPrice: 정수 | null }. null이면 기본 요금으로 되돌림)
 * - 플랜:      POST /api/me/plans/calculate          (유형 3종 초안을 새로 계산)
 *              GET  /api/me/plans/drafts             (계산 전이거나 저장한 뒤에는 빈 목록)
 *              POST /api/me/plans/{planType}/save    ({ planId })
 *
 * 시나리오(?mockScenario=):
 * - stale:      플랜 저장의 첫 요청만 409 PLAN_DRAFT_STALE를 돌려주고 두 번째부터 성공한다(자동 재계산 흐름 확인)
 * - same:       세 플랜 유형의 선택이 같다(유형마다 초안 3개를 응답하되 내용이 같다. 합쳐 표시는 프론트 몫: mergeIdenticalDrafts). same-split은 same의 별칭
 * - error:      플랜 계산이 500 오류를 돌려준다
 * 목업 안: ?mockVariantWatch=a|b는 R11-18(환산 입력 저장), ?mockVariant=a|b는 R11-19(드라마 찜 단위). 설명은 src/lib/mockScenario.ts.
 *
 * 화면 주소의 이 파라미터들은 features의 훅이 API 요청에 실어 보낸다(getMockScenarioParams, getMockVariantParams).
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
const PRICE_OVERRIDES_STORAGE_KEY = 'ottnavi:mock-price-overrides'

// 저장된 설정을 읽는다. 모양이 다르면(손으로 바뀐 값 등) 없는 것으로 본다
function readStoredSettings(): UserSettings | null {
  const stored = readMockJson(SETTINGS_STORAGE_KEY)
  if (typeof stored === 'object' && stored !== null && 'subscriptions' in stored) {
    return stored as UserSettings
  }
  return null
}

// 저장된 요금 수정값을 읽는다([서비스 ID, 월 요금] 쌍 목록)
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

// 요금 화면에서 사용자가 고친 금액(서비스 ID → 월 요금)
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
  const override = priceOverrides.get(ottServiceId)
  return {
    productId: product.productId,
    productName: product.productName,
    adminPrice: product.adminPrice,
    price: override ?? product.adminPrice,
    source: override === undefined ? 'ADMIN' : 'USER_OVERRIDE',
  }
}

// 요금 화면의 한 줄을 만든다
function toPricingService(ottServiceId: number, settings: UserSettings | null): PricingService {
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

function validationError(request: Request, detail: string) {
  return problemResponse(request, 400, 'Bad Request', 'VALIDATION_FAILED', detail)
}

// 저장 요청의 값이 입력 규칙(schema.ts와 같은 규칙)을 지키는지 검사한다. 어긋나면 안내 문장을, 맞으면 null을 돌려준다
function validateSettingsRequest(body: SaveUserSettingsRequest): string | null {
  if (!Number.isInteger(body.monthlyBudget) || body.monthlyBudget < 0) {
    return '월 예산은 0 이상의 정수여야 합니다.'
  }
  if (
    !Number.isInteger(body.monthlyWatchMinutes) ||
    body.monthlyWatchMinutes < MIN_MONTHLY_WATCH_MINUTES ||
    body.monthlyWatchMinutes > MAX_MONTHLY_WATCH_MINUTES
  ) {
    return `월 시청 시간은 ${MIN_MONTHLY_WATCH_MINUTES}분 이상이어야 합니다.`
  }
  if (!Array.isArray(body.subscriptions) || body.subscriptions.length === 0) {
    return '서비스별 이용 상태를 입력해 주세요.'
  }
  for (const subscription of body.subscriptions) {
    if (subscription.status !== 'SUBSCRIBED') continue
    const day = subscription.billingDay
    if (!subscription.planName || day === null || !Number.isInteger(day) || day < 1 || day > 31) {
      return '구독 중인 서비스는 요금제 이름과 결제일(1~31)이 필요합니다.'
    }
  }
  return null
}

/* ----- 찜 목록 변환 ----- */

// 찜 한 건을 목록의 한 줄로 바꾼다. 작품 정보를 못 찾으면 null이다
function toWishlistListItem(
  wishlistItemId: number,
  entry: MockWishlistEntry,
  isByTitle: boolean,
  watchableServiceIds: Set<number>,
): WishlistListItem | null {
  const detail = TITLE_FIXTURES.find(
    (t) => t.mediaType === entry.mediaType && t.tmdbId === entry.tmdbId,
  )
  if (!detail) return null

  // 시즌 단위 안(a)이고 시즌 번호가 있으면 그 시즌 값, 그 밖에는 작품 전체 값을 쓴다
  const season =
    !isByTitle && entry.seasonNumber !== null
      ? detail.seasons.find((s) => s.seasonNumber === entry.seasonNumber)
      : undefined
  const isSeasonRow = season !== undefined

  let requiredMinutes = detail.runtimeMin ?? 0
  let isRuntimeEstimated = detail.isRuntimeEstimated
  if (detail.mediaType === 'tv') {
    const targetSeasons = isSeasonRow ? [season] : detail.seasons
    requiredMinutes = targetSeasons.reduce((sum, s) => sum + s.totalRuntimeMin, 0)
    isRuntimeEstimated = targetSeasons.some((s) => s.isRuntimeEstimated)
  }

  const availabilities = isSeasonRow ? season.availabilities : detail.availabilities
  return {
    wishlistItemId,
    mediaType: detail.mediaType,
    tmdbId: detail.tmdbId,
    title: detail.title,
    posterUrl: detail.posterUrl,
    seasonNumber: isSeasonRow ? season.seasonNumber : null,
    seasonName: isSeasonRow ? season.name : null,
    priority: entry.priority,
    requiredMinutes,
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

    const requestBody = (await request.json()) as SaveUserSettingsRequest
    const invalidReason = validateSettingsRequest(requestBody)
    if (invalidReason) return validationError(request, invalidReason)

    // R11-18 두 안: 안 a(기본)는 환산 결과(분)만 저장하고 환산 입력값은 버린다. 안 b는 환산 입력값도 함께 저장한다
    const variant = getRequestMockWatchVariant(request)
    savedSettings = {
      isOnboardingDone: true,
      subscriptions: requestBody.subscriptions,
      monthlyBudget: requestBody.monthlyBudget,
      monthlyWatchMinutes: requestBody.monthlyWatchMinutes,
      watchPreset: requestBody.watchPreset,
      weeklyInput: variant === 'b' ? requestBody.weeklyInput : null,
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

    // R11-19 두 안: 안 a(기본)는 시즌마다 한 줄, 안 b는 같은 드라마를 작품 하나로 묶어 한 줄(가장 최근에 찜한 항목 기준)
    const isByTitle = getRequestMockVariant(request) === 'b'
    const settings = getEffectiveSettings()
    const watchableServiceIds = new Set(
      (settings?.subscriptions ?? [])
        .filter((s) => s.status === 'SUBSCRIBED' || s.status === 'FREE')
        .map((s) => s.ottServiceId),
    )

    // 최근에 찜한 순(ID가 큰 순). 안 b는 같은 작품의 첫 항목(가장 최근 찜)만 대표로 남긴다
    const seenTitles = new Set<string>()
    const items: WishlistListItem[] = []
    for (const [id, entry] of [...mockWishlist.entries()].sort(([x], [y]) => y - x)) {
      if (isByTitle) {
        const titleKey = `${entry.mediaType}:${entry.tmdbId}`
        if (seenTitles.has(titleKey)) continue
        seenTitles.add(titleKey)
      }
      const item = toWishlistListItem(id, entry, isByTitle, watchableServiceIds)
      if (item) items.push(item)
    }

    const body: CommonResponseWishlistList = {
      success: true,
      data: { items, dataAsOf: MOCK_DATA_AS_OF },
    }
    return HttpResponse.json(body)
  }),

  // 요금 확인
  http.get('/api/me/pricing', async ({ request }) => {
    await wait()
    const denied = denyGuest(request)
    if (denied) return denied

    const settings = getEffectiveSettings()
    const body: CommonResponsePricingOverview = {
      success: true,
      data: {
        services: MOCK_PRODUCTS.map((product) => toPricingService(product.ottServiceId, settings)),
        priceEffectiveFrom: MOCK_PRICE_EFFECTIVE_FROM,
        monthlyBudget: settings?.monthlyBudget ?? null,
        monthlyWatchMinutes: settings?.monthlyWatchMinutes ?? null,
      },
    }
    return HttpResponse.json(body)
  }),

  // 사용자 금액 수정. monthlyPrice가 null이면 고친 금액을 지우고 기본 요금으로 되돌린다
  http.patch('/api/me/pricing/:ottServiceId', async ({ request, params }) => {
    await wait()
    const denied = denyGuest(request)
    if (denied) return denied

    const ottServiceId = Number(params.ottServiceId)
    if (!findMockProduct(ottServiceId)) {
      return problemResponse(
        request,
        404,
        'Not Found',
        'RESOURCE_NOT_FOUND',
        '서비스를 찾을 수 없습니다.',
      )
    }

    const { monthlyPrice } = (await request.json()) as UpdatePricingRequest
    if (monthlyPrice === null) {
      priceOverrides.delete(ottServiceId)
      persistPriceOverrides()
    } else if (!Number.isInteger(monthlyPrice) || monthlyPrice < 0 || monthlyPrice > 1_000_000) {
      return validationError(request, '월 요금은 0원 이상 100만 원 이하의 정수여야 합니다.')
    } else {
      priceOverrides.set(ottServiceId, monthlyPrice)
      persistPriceOverrides()
    }

    const body: CommonResponsePricingService = {
      success: true,
      data: toPricingService(ottServiceId, getEffectiveSettings()),
    }
    return HttpResponse.json(body)
  }),

  // 플랜 계산: 유형 3종 초안을 새로 만든다(기존 초안은 덮어쓴다)
  http.post('/api/me/plans/calculate', async ({ request }) => {
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
    return HttpResponse.json(body)
  }),

  // 플랜 초안 조회: 계산 전이거나 저장한 뒤에는 빈 목록이다(저장하면 나머지 DRAFT는 삭제되기 때문)
  http.get('/api/me/plans/drafts', async ({ request }) => {
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

  // 플랜 저장: 고른 유형의 초안을 ACTIVE로 만든다. stale 시나리오에서는 첫 요청만 409다
  http.post('/api/me/plans/:planType/save', async ({ request, params }) => {
    await wait()
    const denied = denyGuest(request)
    if (denied) return denied

    const planType = params.planType as PlanType
    if (!PLAN_TYPES.includes(planType)) {
      return validationError(request, '플랜 유형이 올바르지 않습니다.')
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

    const draft = buildCurrentDrafts(request, settings).find((d) => d.planType === planType)
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
        planType,
        status: 'ACTIVE',
        activatedAt: new Date().toISOString(),
      },
    }
    return HttpResponse.json(body, { status: 201 })
  }),
]
