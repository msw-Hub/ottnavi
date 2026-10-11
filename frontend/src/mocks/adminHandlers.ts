import { delay, http, HttpResponse, type RequestHandler } from 'msw'
import type {
  AddProductPriceRequest,
  AdminProduct,
  AdminProductPrice,
  AdminProvider,
  CommonResponseAdminProvider,
  CollectChange,
  CollectExecution,
  CollectTier,
  CommonResponseCollectChangeList,
  CommonResponseCollectExecution,
  CommonResponseCollectExecutionList,
  CommonResponseRunCollectAccepted,
  RunCollectRequest,
  CommonResponseAdminProviderList,
  SaveProviderMappingRequest,
  CommonResponseAdminProduct,
  CommonResponseAdminProductList,
  CreateAdminProductRequest,
  UpdateAdminProductRequest,
  UpdateProductPriceRequest,
} from '@/features/admin/mockTypes'
import {
  checkAddPriceStartDate,
  checkEditLastPriceStartDate,
  checkFirstPriceStartDate,
  checkMonthlyPriceText,
  getPriceRowKind,
  sortPricesAscending,
  type PriceStartDateIssue,
} from '@/features/admin/priceRules'
import {
  canRecover,
  canRestart,
  judgeRun,
  RUNNABLE_COLLECT_TIERS,
  SIMULATED_COMPLETE_AFTER_READS,
  sortExecutionsForHistory,
} from '@/features/admin/collectRules'
import { validateSaveRequest } from '@/features/admin/providerMapping'
import { getTodayDateString } from '@/lib/dateUtils'
import { hasMockScenario, problemResponse, readMockJson, writeMockJson } from '@/mocks/handlerUtils'
import {
  buildMockAdminProducts,
  buildMockAdminProviders,
  buildMockCollectChanges,
  buildMockCollectExecutions,
} from '@/mocks/fixtures/admin'
import { MOCK_PRODUCTS } from '@/mocks/fixtures/pricing'

/*
 * ===== 관리자 화면 목업 핸들러 (Task 029) =====
 * 단계 1(SCR-14 OTT 구독상품·월 구독요금), 단계 2(SCR-15 제공처 매핑), 단계 3(SCR-16 수집 실행·이력)이 있다.
 *
 * 임시 경로 주의: 아래 API는 아직 계약(docs/api/openapi.yaml)에 없다. 경로·요청·응답 모양은 화면을 먼저 만들기 위한 제안이며
 * Task 031에서 계약으로 확정된다(features/admin/mockTypes.ts 상단에 목록이 있다).
 *
 * 권한: 실제 권한 검사는 백엔드 /api/admin/** ADMIN 역할이 한다(Task 053·057). 이 목업은 역할(비로그인·회원·관리자)을 검사하지 않는다
 * (사용자 결정 2026-10-11). 화면 가드(RequireAdmin)도 Task 057 전까지는 자리표시다.
 *
 * - GET    /api/admin/products                                     목록. 시나리오: ?mockScenario=error(500) / empty(빈 목록)
 * - POST   /api/admin/products                                     등록(첫 월 구독요금 포함)
 * - PATCH  /api/admin/products/:productId                          이름·판매 상태 수정
 * - POST   /api/admin/products/:productId/prices                   월 구독요금 줄 추가
 * - PATCH  /api/admin/products/:productId/prices/:priceId          가장 최근 줄 수정(확인일도 오늘로)
 * - DELETE /api/admin/products/:productId/prices/:priceId          가장 최근 줄 삭제(하나뿐이면 불가)
 * - POST   /api/admin/products/:productId/prices/:priceId/verify   [요금 확인함]: 마지막 확인일을 오늘로
 * - GET    /api/admin/providers                                    제공처 목록. 시나리오: ?mockScenario=error(500) / empty(빈 목록)
 * - PATCH  /api/admin/providers/:providerId                        매핑 저장. 시나리오: ?mockScenario=save-error(저장만 500)
 * - GET    /api/admin/collect                                      실행 이력. 시나리오: ?mockScenario=error(500) / empty(빈 이력)
 * - GET    /api/admin/collect/:executionId/changes                 실행별 변경된 작품
 * - POST   /api/admin/collect                                      수동 실행(202). 시나리오: ?mockScenario=run-error(실행만 500)
 * - POST   /api/admin/collect/:executionId/recover                 중단됨 → 실패로 기록(제안)
 * - POST   /api/admin/collect/:executionId/restart                 실패한 실행 다시 실행(제안, 202, 새 executionId)
 *
 * 규칙 위반은 서버처럼 409 + 제안 오류 코드로 돌려준다(상태 코드와 이름은 모두 "제안"이다. 규칙 위반은 요청 모양 오류(400)가 아니라
 * 현재 저장된 줄과의 충돌이라 409로 두었고, 422와의 선택은 Task 031 계약 절차에서 정한다). 입력 모양 오류는 400 VALIDATION_FAILED다.
 */

// 요청 처리에 걸리는 시간. 테스트에서는 기다리지 않고, 브라우저에서는 로딩 상태가 눈에 보이게 잠깐 기다린다
function wait() {
  return delay(import.meta.env.MODE === 'test' ? 0 : 300)
}

/*
 * ----- 메모리 저장소 (새로고침 후에도 유지) -----
 * 날짜를 fixture에 고정해 저장하면 시간이 지날수록 "예정 줄"이 과거가 되고 "오래됨" 판정이 흔들린다(fixtures/admin.ts 주석).
 * 그래서 기본 목록은 매번 "오늘" 기준으로 build*에서 다시 만들고, 사용자가 건드린(수정·등록한) OTT 구독상품만 저장해 그 위에 얹는다.
 * 건드린 상품은 그 시점의 날짜가 그대로 굳는다. sessionStorage를 못 쓰면 메모리만으로 동작한다(writeMockJson이 삼킨다).
 */
const STORAGE_KEY = 'ottnavi:mock-admin-products'

const FIRST_NEW_PRODUCT_ID = 201 // 기본 상품 ID(101~107)와 겹치지 않는 시작 번호
const FIRST_NEW_PRICE_ID = 5000 // 기본 월 구독요금 줄 ID(1011~1073)와 겹치지 않는 시작 번호

interface StoredAdminProducts {
  nextProductId: number // 다음에 발급할 OTT 구독상품 ID
  nextPriceId: number // 다음에 발급할 월 구독요금 줄 ID
  products: AdminProduct[] // 사용자가 건드리거나 새로 등록한 OTT 구독상품
}

let nextProductId = FIRST_NEW_PRODUCT_ID
let nextPriceId = FIRST_NEW_PRICE_ID
const changedProducts = new Map<number, AdminProduct>() // OTT 구독상품 ID → 건드린 상품

// 저장된 값을 읽는다. 모양이 다르면(손으로 바뀐 값 등) 없는 것으로 본다
function readStoredProducts(): StoredAdminProducts | null {
  const stored = readMockJson(STORAGE_KEY)
  if (
    typeof stored === 'object' &&
    stored !== null &&
    'nextProductId' in stored &&
    typeof stored.nextProductId === 'number' &&
    'nextPriceId' in stored &&
    typeof stored.nextPriceId === 'number' &&
    'products' in stored &&
    Array.isArray(stored.products)
  ) {
    return stored as StoredAdminProducts
  }
  return null
}

function persistProducts(): void {
  const stored: StoredAdminProducts = {
    nextProductId,
    nextPriceId,
    products: [...changedProducts.values()],
  }
  writeMockJson(STORAGE_KEY, stored)
}

const storedAtStart = readStoredProducts()
if (storedAtStart) {
  nextProductId = storedAtStart.nextProductId
  nextPriceId = storedAtStart.nextPriceId
  for (const product of storedAtStart.products) changedProducts.set(product.id, product)
}

/*
 * ----- 제공처 매핑 저장소 -----
 * 상품과 같은 방식이다: 기본 목록은 매번 오늘 기준으로 다시 만들고, 사용자가 저장한 제공처만 sessionStorage에 적어 그 위에 얹는다.
 */
const PROVIDERS_STORAGE_KEY = 'ottnavi:mock-admin-providers'

const changedProviders = new Map<number, AdminProvider>() // 제공처 ID → 저장한 제공처

function readStoredProviders(): AdminProvider[] {
  const stored = readMockJson(PROVIDERS_STORAGE_KEY)
  if (!Array.isArray(stored)) return []
  return stored.filter(
    (item): item is AdminProvider =>
      typeof item === 'object' && item !== null && typeof item.providerId === 'number',
  )
}

for (const provider of readStoredProviders()) changedProviders.set(provider.providerId, provider)

// 지금 제공처 목록: 오늘 기준 기본 목록에 저장한 제공처를 얹는다
function getAllProviders(): AdminProvider[] {
  return buildMockAdminProviders(getTodayDateString()).map(
    (provider) => changedProviders.get(provider.providerId) ?? provider,
  )
}

/*
 * ----- 수집 실행 이력 저장소 -----
 * 기본 이력은 매번 오늘 기준으로 다시 만들고(fixtures/admin.ts), 바뀌었거나 새로 접수한 실행만 sessionStorage에 적어 그 위에 얹는다.
 *
 * 완료 시뮬레이션(결정적 규칙): 화면에서 접수한 실행(수동 실행·다시 실행)은 실제로 도는 일이 없으므로,
 * 이력 조회를 SIMULATED_COMPLETE_AFTER_READS번 받으면 완료로 바뀐다. 시간이 아니라 요청 횟수로 정하는 이유는
 * 시간에 기대면 테스트와 화면 확인이 흔들리기 때문이다(조회 1번째는 실행 중, [새로 고침] 뒤 2번째에서 성공).
 * 기본 이력의 "실행 중"(5번)은 시뮬레이션 대상이 아니라 계속 실행 중으로 남는다. 같은 날짜 재실행 409를 보여 주려는 것이다.
 */
const COLLECT_STORAGE_KEY = 'ottnavi:mock-admin-collect'

interface StoredCollect {
  nextExecutionId?: number // (더 이상 쓰지 않는다) 예전 저장값과 모양을 맞추려고 남겨 둔 필드. 실행 ID는 이력의 최대 ID + 1로 발급한다
  executions: CollectExecution[] // 바뀌었거나 새로 접수한 실행
  reads: Record<string, number> // 시뮬레이션 대상 실행 ID → 지금까지 이력을 받은 횟수
}

const changedExecutions = new Map<number, CollectExecution>()
let simulatedReads: Record<string, number> = {}

function readStoredCollect(): StoredCollect | null {
  const stored = readMockJson(COLLECT_STORAGE_KEY)
  if (
    typeof stored === 'object' &&
    stored !== null &&
    'executions' in stored &&
    Array.isArray(stored.executions) &&
    'reads' in stored &&
    typeof stored.reads === 'object' &&
    stored.reads !== null
  ) {
    return stored as StoredCollect
  }
  return null
}

function persistCollect(): void {
  const stored: StoredCollect = {
    executions: [...changedExecutions.values()],
    reads: simulatedReads,
  }
  writeMockJson(COLLECT_STORAGE_KEY, stored)
}

const storedCollectAtStart = readStoredCollect()
if (storedCollectAtStart) {
  simulatedReads = storedCollectAtStart.reads
  for (const execution of storedCollectAtStart.executions) {
    changedExecutions.set(execution.executionId, execution)
  }
}

// 지금 실행 목록: 오늘 기준 기본 이력에 바뀐 실행을 얹고, 새로 접수한 실행을 더한다
function getAllExecutions(): CollectExecution[] {
  const base = buildMockCollectExecutions(getTodayDateString())
  const baseIds = new Set(base.map((e) => e.executionId))
  const merged = base.map((e) => changedExecutions.get(e.executionId) ?? e)
  const created = [...changedExecutions.values()].filter((e) => !baseIds.has(e.executionId))
  return [...merged, ...created]
}

function saveExecution(execution: CollectExecution): void {
  changedExecutions.set(execution.executionId, execution)
  persistCollect()
}

// 접수한 실행이 성공했을 때 보여 줄 변경 작품(예시)
const SIMULATED_CHANGES: CollectChange[] = [
  {
    mediaType: 'movie',
    tmdbId: 838209,
    title: '파묘',
    ottServiceId: 5,
    previousStatus: null,
    currentStatus: 'AVAILABLE',
  },
  {
    mediaType: 'tv',
    tmdbId: 93405,
    title: '오징어 게임',
    ottServiceId: 1,
    previousStatus: 'AVAILABLE',
    currentStatus: 'UNKNOWN',
  },
]

// 시뮬레이션: 이력을 한 번 읽을 때마다 접수한 실행의 읽은 횟수를 올리고, 기준 횟수에 닿으면 성공으로 바꾼다
function advanceSimulatedRuns(): void {
  for (const execution of getAllExecutions()) {
    const key = String(execution.executionId)
    if (execution.status !== 'RUNNING' || !(key in simulatedReads)) continue
    simulatedReads[key] += 1
    if (simulatedReads[key] >= SIMULATED_COMPLETE_AFTER_READS) {
      changedExecutions.set(execution.executionId, {
        ...execution,
        status: 'COMPLETED',
        endedAt: new Date().toISOString(),
        processedCount: 3200,
        changedCount: SIMULATED_CHANGES.length,
      })
    }
  }
  persistCollect()
}

function getChanges(executionId: number): CollectChange[] {
  // 화면에서 접수한 실행(시뮬레이션 대상)은 simulatedReads에 키가 있다. 기본 이력(1~5)은 fixture의 변경 목록을 쓴다
  if (String(executionId) in simulatedReads) {
    const execution = changedExecutions.get(executionId)
    return execution?.status === 'COMPLETED' ? SIMULATED_CHANGES.map((c) => ({ ...c })) : []
  }
  return buildMockCollectChanges(executionId)
}

function internalError(request: Request) {
  return problemResponse(
    request,
    500,
    'Internal Server Error',
    'INTERNAL_ERROR',
    '일시적인 오류가 발생했습니다.',
  )
}

// 같은 날짜·범위 판정이 OK가 아니면 409 응답을, OK면 undefined를 돌려준다.
// 상태 코드는 모두 409(제안): 요청 모양이 아니라 이미 있는 실행과의 충돌이라서다. 복구가 먼저 필요한 경우의 코드 이름은 제안이다
function judgementResponse(request: Request, targetDate: string, tier: CollectTier) {
  switch (judgeRun(getAllExecutions(), targetDate, tier)) {
    case 'ALREADY_RUNNING':
      return ruleViolation(
        request,
        'BATCH_ALREADY_RUNNING',
        '같은 날짜·범위의 수집이 이미 실행 중입니다.',
      )
    case 'ALREADY_COMPLETED':
      return ruleViolation(
        request,
        'BATCH_ALREADY_COMPLETED',
        '같은 날짜·범위의 수집이 이미 완료되었습니다.',
      )
    case 'RECOVERY_REQUIRED':
      return ruleViolation(
        request,
        'BATCH_RECOVERY_REQUIRED',
        '중단된 실행이 있어 먼저 복구해야 합니다.',
      )
    case 'OK':
      return undefined
  }
}

// 새 실행을 접수한다(202). 판정이 OK일 때만 새 executionId로 RUNNING 실행을 만든다
function startRun(request: Request, targetDate: string, tier: CollectTier) {
  const problem = judgementResponse(request, targetDate, tier)
  if (problem) return problem
  const execution: CollectExecution = {
    // 새 실행 ID는 지금 이력의 최대 ID + 1이다(5 다음은 6). 저장소에 예전 값이 남아 있어도 이력 기준이라 깨지지 않는다
    executionId: Math.max(0, ...getAllExecutions().map((e) => e.executionId)) + 1,
    targetDate,
    tier,
    status: 'RUNNING',
    startedAt: new Date().toISOString(),
    endedAt: null,
    processedCount: 0,
    changedCount: 0,
    failureReason: null,
  }
  simulatedReads[String(execution.executionId)] = 0
  saveExecution(execution)
  const body: CommonResponseRunCollectAccepted = {
    success: true,
    data: { executionId: execution.executionId },
  }
  return HttpResponse.json(body, { status: 202 })
}

/** 관리자 목업 상태를 처음으로 되돌린다(테스트가 서로 영향을 주지 않게 쓴다). 저장된 값도 지운다. */
export function resetMockAdminState(): void {
  nextProductId = FIRST_NEW_PRODUCT_ID
  nextPriceId = FIRST_NEW_PRICE_ID
  changedProducts.clear()
  writeMockJson(STORAGE_KEY, null)
  changedProviders.clear()
  writeMockJson(PROVIDERS_STORAGE_KEY, null)
  changedExecutions.clear()
  simulatedReads = {}
  writeMockJson(COLLECT_STORAGE_KEY, null)
}

/* ----- 도우미 ----- */

// 지금 OTT 구독상품 목록: 오늘 기준 기본 목록에 건드린 상품을 얹고, 새로 등록한 상품(ID 순)을 뒤에 붙인다
function getAllProducts(): AdminProduct[] {
  const base = buildMockAdminProducts(getTodayDateString())
  const baseIds = new Set(base.map((product) => product.id))
  const merged = base.map((product) => changedProducts.get(product.id) ?? product)
  const created = [...changedProducts.values()]
    .filter((product) => !baseIds.has(product.id))
    .sort((a, b) => a.id - b.id)
  return [...merged, ...created]
}

// 상품 하나를 저장소에 반영하고 돌려준다(줄은 시작일 오름차순으로 맞춘다)
function saveProduct(product: AdminProduct): AdminProduct {
  const saved = { ...product, prices: sortPricesAscending(product.prices) }
  changedProducts.set(saved.id, saved)
  persistProducts()
  return saved
}

function okProduct(product: AdminProduct, status = 200) {
  const body: CommonResponseAdminProduct = { success: true, data: product }
  return HttpResponse.json(body, { status })
}

function validationError(request: Request, detail: string) {
  return problemResponse(request, 400, 'Bad Request', 'VALIDATION_FAILED', detail)
}

function notFound(request: Request) {
  return problemResponse(request, 404, 'Not Found', 'RESOURCE_NOT_FOUND', '찾을 수 없습니다.')
}

// 월 구독요금 규칙 위반을 제안 오류 코드(409)로 돌려준다
function ruleViolation(request: Request, errorCode: string, detail: string) {
  return problemResponse(request, 409, 'Conflict', errorCode, detail)
}

// 시작일 검사 결과 종류 → 응답. 통과하면 undefined
function startDateProblem(request: Request, issue: PriceStartDateIssue | null) {
  switch (issue) {
    case null:
      return undefined
    case 'REQUIRED':
      return validationError(request, '적용 시작일은 YYYY-MM-DD 형식이어야 합니다.')
    case 'DUPLICATE':
      return ruleViolation(
        request,
        'PRICE_START_DATE_DUPLICATE',
        '같은 적용 시작일의 월 구독요금이 이미 있습니다.',
      )
    case 'NOT_AFTER_PREVIOUS':
      return ruleViolation(
        request,
        'PRICE_START_DATE_NOT_AFTER_LAST',
        '적용 시작일은 앞 줄의 시작일보다 늦어야 합니다.',
      )
    case 'FIRST_IN_FUTURE':
      return ruleViolation(
        request,
        'PRICE_FIRST_START_DATE_IN_FUTURE',
        '첫 월 구독요금의 적용 시작일은 오늘 이하여야 합니다.',
      )
  }
}

// 월 구독요금 값 검사(요청 본문의 숫자). 올바르면 undefined
function monthlyPriceProblem(request: Request, monthlyPrice: unknown) {
  if (typeof monthlyPrice !== 'number' || !Number.isInteger(monthlyPrice)) {
    return validationError(request, '월 구독요금은 정수여야 합니다.')
  }
  const message = checkMonthlyPriceText(String(monthlyPrice))
  return message ? validationError(request, message) : undefined
}

export const adminHandlers: RequestHandler[] = [
  // OTT 구독상품 목록
  http.get('/api/admin/products', async ({ request }) => {
    await wait()
    if (hasMockScenario(request, 'error')) {
      return problemResponse(
        request,
        500,
        'Internal Server Error',
        'INTERNAL_ERROR',
        '일시적인 오류가 발생했습니다.',
      )
    }
    const body: CommonResponseAdminProductList = {
      success: true,
      data: { products: hasMockScenario(request, 'empty') ? [] : getAllProducts() },
    }
    return HttpResponse.json(body)
  }),

  // OTT 구독상품 등록(첫 월 구독요금 포함)
  http.post('/api/admin/products', async ({ request }) => {
    await wait()
    const body = (await request.json()) as CreateAdminProductRequest
    const name = typeof body.name === 'string' ? body.name.trim() : ''
    if (name === '') return validationError(request, '이름을 입력해 주세요.')
    if (body.productType !== 'SINGLE') {
      return validationError(request, 'MVP는 단품(SINGLE)만 등록할 수 있습니다.')
    }
    if (!MOCK_PRODUCTS.some((p) => p.ottServiceId === body.ottServiceId)) {
      return validationError(request, '서비스를 선택해 주세요.')
    }
    const priceProblem = monthlyPriceProblem(request, body.monthlyPrice)
    if (priceProblem) return priceProblem
    const today = getTodayDateString()
    const dateProblem = startDateProblem(
      request,
      checkFirstPriceStartDate(body.effectiveFrom, today).issue,
    )
    if (dateProblem) return dateProblem

    const product: AdminProduct = {
      id: nextProductId++,
      name,
      productType: 'SINGLE',
      ottServiceId: body.ottServiceId,
      active: true,
      prices: [
        {
          id: nextPriceId++,
          monthlyPrice: body.monthlyPrice,
          effectiveFrom: body.effectiveFrom,
          lastVerifiedAt: today,
        },
      ],
    }
    return okProduct(saveProduct(product), 201)
  }),

  // 이름·판매 상태 수정
  http.patch('/api/admin/products/:productId', async ({ request, params }) => {
    await wait()
    const product = getAllProducts().find((p) => p.id === Number(params.productId))
    if (!product) return notFound(request)

    const body = (await request.json()) as UpdateAdminProductRequest
    if (body.name !== undefined && body.name.trim() === '') {
      return validationError(request, '이름을 입력해 주세요.')
    }
    return okProduct(
      saveProduct({
        ...product,
        name: body.name !== undefined ? body.name.trim() : product.name,
        active: body.active ?? product.active,
      }),
    )
  }),

  // 월 구독요금 줄 추가
  http.post('/api/admin/products/:productId/prices', async ({ request, params }) => {
    await wait()
    const product = getAllProducts().find((p) => p.id === Number(params.productId))
    if (!product) return notFound(request)

    const body = (await request.json()) as AddProductPriceRequest
    const priceProblem = monthlyPriceProblem(request, body.monthlyPrice)
    if (priceProblem) return priceProblem
    const today = getTodayDateString()
    const dateProblem = startDateProblem(
      request,
      checkAddPriceStartDate(sortPricesAscending(product.prices), body.effectiveFrom, today).issue,
    )
    if (dateProblem) return dateProblem

    const row: AdminProductPrice = {
      id: nextPriceId++,
      monthlyPrice: body.monthlyPrice,
      effectiveFrom: body.effectiveFrom,
      lastVerifiedAt: today,
    }
    return okProduct(saveProduct({ ...product, prices: [...product.prices, row] }), 201)
  }),

  // 가장 최근 줄 수정. 금액을 직접 고친 것은 확인한 것으로 보아 마지막 확인일도 오늘로 갱신한다(사용자 결정 2026-10-11)
  http.patch('/api/admin/products/:productId/prices/:priceId', async ({ request, params }) => {
    await wait()
    const product = getAllProducts().find((p) => p.id === Number(params.productId))
    const sorted = product ? sortPricesAscending(product.prices) : []
    const target = sorted.find((row) => row.id === Number(params.priceId))
    if (!product || !target) return notFound(request)
    if (sorted[sorted.length - 1].id !== target.id) {
      return ruleViolation(
        request,
        'PRICE_NOT_LATEST',
        '가장 최근 월 구독요금만 수정할 수 있습니다.',
      )
    }

    const body = (await request.json()) as UpdateProductPriceRequest
    const priceProblem = monthlyPriceProblem(request, body.monthlyPrice)
    if (priceProblem) return priceProblem
    const dateProblem = startDateProblem(
      request,
      checkEditLastPriceStartDate(sorted, body.effectiveFrom, getTodayDateString()).issue,
    )
    if (dateProblem) return dateProblem

    return okProduct(
      saveProduct({
        ...product,
        prices: product.prices.map((row) =>
          row.id === target.id
            ? {
                ...row,
                monthlyPrice: body.monthlyPrice,
                effectiveFrom: body.effectiveFrom,
                lastVerifiedAt: getTodayDateString(),
              }
            : row,
        ),
      }),
    )
  }),

  // 가장 최근 줄 삭제. 줄이 하나뿐이면 삭제할 수 없다
  http.delete('/api/admin/products/:productId/prices/:priceId', async ({ request, params }) => {
    await wait()
    const product = getAllProducts().find((p) => p.id === Number(params.productId))
    const sorted = product ? sortPricesAscending(product.prices) : []
    const target = sorted.find((row) => row.id === Number(params.priceId))
    if (!product || !target) return notFound(request)
    if (sorted.length === 1) {
      return ruleViolation(
        request,
        'PRICE_LAST_ROW_CANNOT_DELETE',
        '월 구독요금이 하나뿐이라 삭제할 수 없습니다.',
      )
    }
    if (sorted[sorted.length - 1].id !== target.id) {
      return ruleViolation(
        request,
        'PRICE_NOT_LATEST',
        '가장 최근 월 구독요금만 삭제할 수 있습니다.',
      )
    }
    return okProduct(
      saveProduct({ ...product, prices: product.prices.filter((row) => row.id !== target.id) }),
    )
  }),

  // [요금 확인함]: 마지막 확인일만 오늘로 바꾼다. 지금 적용 중인 줄만 확인할 수 있다(지난·예정 줄은 확인 대상이 아니다)
  http.post(
    '/api/admin/products/:productId/prices/:priceId/verify',
    async ({ request, params }) => {
      await wait()
      const product = getAllProducts().find((p) => p.id === Number(params.productId))
      const target = product?.prices.find((row) => row.id === Number(params.priceId))
      if (!product || !target) return notFound(request)

      const today = getTodayDateString()
      const sorted = sortPricesAscending(product.prices)
      const kind = getPriceRowKind(
        sorted,
        sorted.findIndex((row) => row.id === target.id),
        today,
      )
      if (kind !== 'CURRENT') {
        // 제안 오류 코드 PRICE_NOT_CURRENT (상태 코드 409는 다른 월 구독요금 규칙 위반과 같다)
        return ruleViolation(
          request,
          'PRICE_NOT_CURRENT',
          '지금 적용 중인 월 구독요금만 확인할 수 있습니다.',
        )
      }
      return okProduct(
        saveProduct({
          ...product,
          prices: product.prices.map((row) =>
            row.id === target.id ? { ...row, lastVerifiedAt: today } : row,
          ),
        }),
      )
    },
  ),

  // 제공처 목록
  http.get('/api/admin/providers', async ({ request }) => {
    await wait()
    if (hasMockScenario(request, 'error')) {
      return problemResponse(
        request,
        500,
        'Internal Server Error',
        'INTERNAL_ERROR',
        '일시적인 오류가 발생했습니다.',
      )
    }
    const body: CommonResponseAdminProviderList = {
      success: true,
      data: { providers: hasMockScenario(request, 'empty') ? [] : getAllProviders() },
    }
    return HttpResponse.json(body)
  }),

  // 매핑 저장: 서비스 선택 → MAPPED, 관련 없음 → IGNORED, 선택 안 함 → UNMAPPED. 서비스에 연결하지 않으면 등급은 STANDARD로 고정한다
  http.patch('/api/admin/providers/:providerId', async ({ request, params }) => {
    await wait()
    // 저장 실패 상황 재현: 목록은 정상이고 저장만 실패한다
    if (hasMockScenario(request, 'save-error')) {
      return problemResponse(
        request,
        500,
        'Internal Server Error',
        'INTERNAL_ERROR',
        '일시적인 오류가 발생했습니다.',
      )
    }
    const provider = getAllProviders().find((p) => p.providerId === Number(params.providerId))
    if (!provider) return notFound(request)

    const body = (await request.json()) as SaveProviderMappingRequest
    const problem = validateSaveRequest(
      body,
      MOCK_PRODUCTS.map((p) => p.ottServiceId),
    )
    if (problem) return validationError(request, problem)

    const saved: AdminProvider = {
      ...provider,
      mappingState: body.mappingState,
      ottServiceId: body.ottServiceId,
      tier: body.mappingState === 'MAPPED' ? body.tier : 'STANDARD',
    }
    changedProviders.set(saved.providerId, saved)
    writeMockJson(PROVIDERS_STORAGE_KEY, [...changedProviders.values()])
    const response: CommonResponseAdminProvider = { success: true, data: saved }
    return HttpResponse.json(response)
  }),

  // 수집 실행 이력(최신 순). 화면에서 접수한 실행(시뮬레이션)은 이력을 SIMULATED_COMPLETE_AFTER_READS번 읽으면 완료로 바뀐다
  http.get('/api/admin/collect', async ({ request }) => {
    await wait()
    if (hasMockScenario(request, 'error')) return internalError(request)
    if (hasMockScenario(request, 'empty')) {
      const empty: CommonResponseCollectExecutionList = { success: true, data: { executions: [] } }
      return HttpResponse.json(empty)
    }
    advanceSimulatedRuns()
    const body: CommonResponseCollectExecutionList = {
      success: true,
      data: { executions: sortExecutionsForHistory(getAllExecutions()) },
    }
    return HttpResponse.json(body)
  }),

  // 실행별 변경된 작품
  http.get('/api/admin/collect/:executionId/changes', async ({ request, params }) => {
    await wait()
    const executionId = Number(params.executionId)
    if (!getAllExecutions().some((e) => e.executionId === executionId)) return notFound(request)
    const body: CommonResponseCollectChangeList = {
      success: true,
      data: { executionId, changes: getChanges(executionId) },
    }
    return HttpResponse.json(body)
  }),

  // 수동 실행: 202 접수. 판정은 judgeRun(collectRules)과 같다
  http.post('/api/admin/collect', async ({ request }) => {
    await wait()
    if (hasMockScenario(request, 'run-error')) return internalError(request)

    const body = (await request.json()) as RunCollectRequest
    if (typeof body.targetDate !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(body.targetDate)) {
      return validationError(request, '대상 날짜는 YYYY-MM-DD 형식이어야 합니다.')
    }
    if (body.targetDate > getTodayDateString()) {
      return validationError(request, '대상 날짜는 오늘 또는 그 이전이어야 합니다.')
    }
    if (!RUNNABLE_COLLECT_TIERS.includes(body.tier)) {
      return validationError(request, 'MVP는 전체(ALL)만 실행할 수 있습니다.')
    }
    return startRun(request, body.targetDate, body.tier)
  }),

  // 복구(제안): 서버가 중단됨으로 판정한 실행을 실패로 기록한다
  http.post('/api/admin/collect/:executionId/recover', async ({ request, params }) => {
    await wait()
    const target = getAllExecutions().find((e) => e.executionId === Number(params.executionId))
    if (!target) return notFound(request)
    if (!canRecover(target.status)) {
      return ruleViolation(request, 'BATCH_NOT_STOPPED', '중단된 실행만 복구할 수 있습니다.')
    }
    const recovered: CollectExecution = {
      ...target,
      status: 'FAILED',
      // 복구는 "끝난 시각"을 알 수 없다(서버가 언제 멈췄는지 모른다). 그래서 종료 시각을 기록하지 않고 null로 두며 화면은 "—"로 보인다.
      // 서버가 복구 시 종료 시각을 기록할지는 Task 031 안건이다(사용자 결정 2026-10-11)
      failureReason: '서버가 중간에 멈춰 복구하면서 실패로 기록했습니다.',
    }
    saveExecution(recovered)
    const body: CommonResponseCollectExecution = { success: true, data: recovered }
    return HttpResponse.json(body)
  }),

  // 다시 실행(제안): 실패한 실행을 같은 날짜·범위로 새로 접수한다. 새 executionId를 준다(mockTypes 주석)
  http.post('/api/admin/collect/:executionId/restart', async ({ request, params }) => {
    await wait()
    if (hasMockScenario(request, 'run-error')) return internalError(request)
    const target = getAllExecutions().find((e) => e.executionId === Number(params.executionId))
    if (!target) return notFound(request)
    if (!canRestart(target.status)) {
      // 실패가 아니면 판정 결과(실행 중 / 성공 / 중단됨)를 그대로 알린다. 판정이 OK여도 실패한 실행이 아니면 다시 실행할 대상이 아니다
      return (
        judgementResponse(request, target.targetDate, target.tier) ??
        ruleViolation(request, 'BATCH_NOT_STOPPED', '실패한 실행만 다시 실행할 수 있습니다.')
      )
    }
    return startRun(request, target.targetDate, target.tier)
  }),
]
