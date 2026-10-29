import { delay, http, HttpResponse, type RequestHandler } from 'msw'
import type {
  AdminProductPriceRequest,
  AdminProduct,
  AdminProductPrice,
  AdminProvider,
  CommonResponseAdminProvider,
  CollectExecutionChange,
  CollectExecution,
  CollectTier,
  CommonResponseCollectExecutionChangePage,
  CommonResponseCollectExecution,
  CommonResponseCollectExecutionPage,
  CommonResponseCollectExecutionAccepted,
  CreateCollectExecutionRequest,
  CommonResponseAdminProviderList,
  FieldError,
  UpdateAdminProviderRequest,
  CommonResponseAdminProduct,
  CommonResponseAdminProductList,
  CreateAdminProductRequest,
  UpdateAdminProductRequest,
} from '@/api/generated/model'
import {
  checkAddPriceStartDate,
  checkEditLastPriceStartDate,
  checkFirstPriceStartDate,
  MAX_MONTHLY_PRICE,
  getPriceRowKind,
  sortPricesAscending,
  type PriceStartDateIssue,
} from '@/features/admin/priceRules'
import {
  computeCanRecover,
  findRunning,
  judgeRun,
  RUNNABLE_COLLECT_TIERS,
  SIMULATED_COMPLETE_AFTER_READS,
  sortExecutionsForHistory,
  withActionFlags,
} from '@/features/admin/collectRules'
import { getTodayDateString } from '@/lib/dateUtils'
import {
  denyDemo,
  hasMockScenario,
  problemResponse,
  readMockJson,
  writeMockJson,
} from '@/mocks/handlerUtils'
import {
  buildMockAdminProducts,
  buildMockAdminProviders,
  buildMockCollectChanges,
  buildMockCollectExecutions,
  MOCK_PRODUCTS,
} from '@/mocks/fixtures/admin'

/*
 * ===== 관리자 화면 목업 핸들러 (Task 029) =====
 * 단계 1(SCR-14 OTT 구독상품·월 구독요금), 단계 2(SCR-15 제공처 매핑), 단계 3(SCR-16 수집 실행·이력)이 있다.
 *
 * 경로·요청·응답은 확정된 계약(docs/api/openapi.yaml)과 같다. 응답 타입은 생성 모델이라 계약과 어긋나면 컴파일에서 걸린다(Task 031 6-7).
 * 상태 충돌(409)의 오류 코드는 docs/api/error-codes.md의 BATCH_*·PRICE_* 코드이고, 없는 데이터의 404는 RESOURCE_NOT_FOUND다(관리자 API 예외).
 *
 * 권한: 실제 권한 검사는 백엔드 /api/admin/** ADMIN 역할이 한다(Task 053·057). 이 목업은 역할(비로그인·회원·관리자)을 검사하지 않는다
 * (사용자 결정 2026-10-11). 화면 가드(RequireAdmin)도 Task 057 전까지는 자리표시다.
 *
 * - GET    /api/admin/products                                     목록. 시나리오: ?mockScenario=error(500) / empty(빈 목록)
 * - POST   /api/admin/products                                     등록(첫 월 구독요금 포함)
 * - PATCH  /api/admin/products/:productId                          이름·판매 상태 수정
 * - POST   /api/admin/products/:productId/prices                   월 구독요금 줄 추가
 * - PATCH  /api/admin/products/:productId/prices/:priceId          가장 최근 줄 수정(확인일도 오늘로)
 * - DELETE /api/admin/products/:productId/prices/:priceId          가장 최근 줄 삭제(하나뿐이면 불가, 204 본문 없음)
 * - POST   /api/admin/products/:productId/prices/:priceId/verify   [요금 확인함]: 마지막 확인 일시를 지금으로
 * - GET    /api/admin/providers                                    제공처 목록. 시나리오: ?mockScenario=error(500) / empty(빈 목록)
 * - PATCH  /api/admin/providers/:providerId                        매핑 저장. 시나리오: ?mockScenario=save-error(저장만 500)
 * - GET    /api/admin/collect/executions                           실행 이력(페이지 형태, 대상 날짜 내림차순). 시나리오: ?mockScenario=error(500) / empty(빈 이력)
 * - GET    /api/admin/collect/executions/:executionId/changes      실행별 변경된 작품(페이지 형태, 제목 순)
 * - POST   /api/admin/collect/executions                           수동 실행(202, targetDate는 선택). 시나리오: ?mockScenario=run-error(실행만 500)
 * - POST   /api/admin/collect/executions/:executionId/recover      중단됨 → 실패로 기록(200)
 * - POST   /api/admin/collect/executions/:executionId/restart      실패한 실행 다시 실행(202, 새 executionId)
 *
 * 규칙 위반은 서버처럼 409 + 오류 코드로 돌려준다(Task 031 계약 확정. 규칙 위반은 요청 모양 오류(400)가 아니라
 * 현재 저장된 줄과의 충돌이라 409다). 입력 모양 오류는 400 VALIDATION_FAILED다.
 *
 * 검사 순서와 오류 모양은 서버와 같다(Task 061): 경로 변수·요청 본문 검사(400) → 존재 확인(404) → 규칙(409).
 * 400의 detail은 고정 문구("요청 값이 올바르지 않습니다.")이고 필드별 안내는 fieldErrors에 담는다. 소수 금액은 버림 통과하지 않고
 * "형식이 올바르지 않습니다."로 거절한다. 404의 detail은 서버가 데이터 종류별로 쓰는 문장이다.
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
// 이전 키: 'ottnavi:mock-admin-products'. Task 076에서 상품 모양이 바뀌어(ottServiceId → ottServiceIds, isLowCost 추가)
// 예전 모양으로 저장된 값을 읽으면 화면이 깨지므로 키를 바꿔 새로 시작한다
const STORAGE_KEY = 'ottnavi:mock-admin-products:v2'

// 판매 중 번들 상한(계약 createAdminProduct·updateAdminProduct: 판매 중 번들은 최대 13개)
const MAX_ACTIVE_BUNDLES = 13

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
  for (const product of storedAtStart.products) changedProducts.set(product.productId, product)
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
 * 기본 이력에는 계속 실행 중으로 남는 행이 없다(fixtures/admin.ts 주석: 전체 동시 실행 금지라 모든 실행이 막힌다).
 *
 * 버튼 값(canRecover·canRestart, Task 045): 저장한 값을 믿지 않고 목록을 꺼낼 때마다 서버와 같은 규칙(withActionFlags)으로 다시 계산한다.
 * 다른 실행이 시작되거나 끝나면 같은 행의 canRestart도 바뀌기 때문이다(예: 6번이 실행 중인 동안 2번의 [다시 실행]이 사라진다).
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

// 지금 실행 목록: 오늘 기준 기본 이력에 바뀐 실행을 얹고, 새로 접수한 실행을 더한 뒤 버튼 값을 지금 상태로 다시 계산한다
function getAllExecutions(): CollectExecution[] {
  const base = buildMockCollectExecutions(getTodayDateString())
  const baseIds = new Set(base.map((e) => e.executionId))
  const merged = base.map((e) => changedExecutions.get(e.executionId) ?? e)
  const created = [...changedExecutions.values()].filter((e) => !baseIds.has(e.executionId))
  return withActionFlags([...merged, ...created])
}

function saveExecution(execution: CollectExecution): void {
  changedExecutions.set(execution.executionId, execution)
  persistCollect()
}

// 접수한 실행이 성공했을 때 보여 줄 변경 작품(예시)
const SIMULATED_CHANGES: CollectExecutionChange[] = [
  {
    mediaType: 'movie',
    tmdbId: 838209,
    title: '파묘',
    seasonNumber: null,
    ottServiceId: 5,
    previousStatus: null,
    currentStatus: 'AVAILABLE',
  },
  {
    mediaType: 'tv',
    tmdbId: 93405,
    title: '오징어 게임',
    seasonNumber: 1,
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

function getChanges(executionId: number): CollectExecutionChange[] {
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

// 시작·재시작 판정(judgeRun, 서버 judgeLaunch와 같은 순서)이 OK가 아니면 409 응답을, OK면 undefined를 돌려준다.
// 상태 코드는 모두 409: 요청 모양이 아니라 이미 있는 실행과의 충돌이라서다. detail 문구는 서버와 같게 둔다(화면은 errorCode로 문장을 고른다)
function judgementResponse(request: Request, targetDate: string, tier: CollectTier) {
  const executions = getAllExecutions()
  switch (judgeRun(executions, targetDate, tier)) {
    case 'ALREADY_RUNNING': {
      // 같은 날짜·범위가 실행 중인지, 다른 날짜가 실행 중인지에 따라 서버 detail이 다르다(코드는 같다)
      if (findRunning(executions, targetDate, tier)) {
        return ruleViolation(
          request,
          'BATCH_ALREADY_RUNNING',
          '같은 날짜의 수집이 이미 실행 중입니다.',
        )
      }
      const other = executions.find((e) => e.status === 'RUNNING')
      return ruleViolation(
        request,
        'BATCH_ALREADY_RUNNING',
        `수집이 이미 실행 중입니다(실행 중인 대상 날짜: ${other?.targetDate ?? '알 수 없음'}).`,
      )
    }
    case 'ALREADY_COMPLETED':
      return ruleViolation(
        request,
        'BATCH_ALREADY_COMPLETED',
        '해당 날짜의 수집은 이미 완료되었습니다.',
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
    canRecover: false, // 버튼 값은 목록을 꺼낼 때 다시 계산한다(getAllExecutions)
    canRestart: false,
    recalcTargetCount: null, // 재계산은 수집이 끝난 뒤에 돈다
  }
  simulatedReads[String(execution.executionId)] = 0
  saveExecution(execution)
  const body: CommonResponseCollectExecutionAccepted = {
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
  const baseIds = new Set(base.map((product) => product.productId))
  const merged = base.map((product) => changedProducts.get(product.productId) ?? product)
  const created = [...changedProducts.values()]
    .filter((product) => !baseIds.has(product.productId))
    .sort((a, b) => a.productId - b.productId)
  return [...merged, ...created]
}

// 상품 하나를 저장소에 반영하고 돌려준다(줄은 시작일 오름차순으로 맞춘다)
function saveProduct(product: AdminProduct): AdminProduct {
  const saved = { ...product, prices: sortPricesAscending(product.prices) }
  changedProducts.set(saved.productId, saved)
  persistProducts()
  return saved
}

function okProduct(product: AdminProduct, status = 200) {
  const body: CommonResponseAdminProduct = { success: true, data: product }
  return HttpResponse.json(body, { status })
}

// 서버 ErrorCode.VALIDATION_FAILED의 기본 문구. 서버는 필드를 알리는 400의 detail에 항상 이 문구를 쓰고, 필드별 안내는 fieldErrors에 담는다
const VALIDATION_FAILED_DETAIL = '요청 값이 올바르지 않습니다.'

// 서버가 JSON 값의 형식(정수 자리의 소수, 잘못된 날짜 등)이 맞지 않을 때 fieldErrors에 담는 문구
const TYPE_MISMATCH_MESSAGE = '형식이 올바르지 않습니다.'

// 경로 변수 검사 문구(서버 @Positive)
const POSITIVE_MESSAGE = '1 이상이어야 합니다.'

/**
 * 필드 오류 목록을 담은 400 VALIDATION_FAILED. 서버(GlobalExceptionHandler)처럼 detail은 고정 문구이고 필드별 안내는 fieldErrors에 담는다.
 * Bean Validation 실패·형식 불일치·서버의 FieldValidationException이 모두 이 모양이다. 화면은 이 문구를 해당 입력 아래에 보인다.
 */
function validationFailed(request: Request, fieldErrors: FieldError[]) {
  return problemResponse(
    request,
    400,
    'Bad Request',
    'VALIDATION_FAILED',
    VALIDATION_FAILED_DETAIL,
    fieldErrors,
  )
}

// 필드 하나를 알리는 400 VALIDATION_FAILED(서버 FieldValidationException). 예: 수집 실행의 targetDate·tier
function fieldValidationError(request: Request, field: string, message: string) {
  return validationFailed(request, [{ field, message }])
}

// 경로 변수(상품·요금 줄·실행·제공처 ID)가 1 이상인지 본다. 아니면 400 응답, 맞으면 undefined (서버는 컨트롤러에서 가장 먼저 검사한다)
function pathIdProblem(
  request: Request,
  ids: Record<string, string | readonly string[] | undefined>,
) {
  const errors: FieldError[] = []
  for (const [field, raw] of Object.entries(ids)) {
    const value = Number(raw)
    if (!Number.isInteger(value) || value < 1) errors.push({ field, message: POSITIVE_MESSAGE })
  }
  return errors.length > 0 ? validationFailed(request, errors) : undefined
}

// 'YYYY-MM-DD'이면서 실제로 있는 날짜인지 본다(서버 LocalDate 해석). 2026-02-30 같은 날은 거절한다
function isRealDate(text: unknown): text is string {
  if (typeof text !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(text)) return false
  const date = new Date(`${text}T00:00:00Z`)
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === text
}

/**
 * 월 구독요금 요청 본문(금액·적용 시작일)을 서버의 요청 검사와 같은 순서로 본다. 올바르면 빈 배열이다.
 * 서버는 먼저 JSON 값의 형식(소수 금액·잘못된 날짜 → "형식이 올바르지 않습니다.", 첫 오류에서 멈춤)을 보고,
 * 통과하면 Bean Validation(필수·0 이상·1,000,000 이하)을 모든 필드에 대해 본다. 이 검사는 존재 확인(404)·규칙(409)보다 항상 먼저다.
 */
function priceBodyProblems(body: Partial<AdminProductPriceRequest>): FieldError[] {
  // 1. 형식 불일치: 값이 있는데 정수가 아닌 금액, 날짜가 아닌 시작일
  const mismatches: FieldError[] = []
  if (body.monthlyPrice != null && !Number.isInteger(body.monthlyPrice)) {
    mismatches.push({ field: 'monthlyPrice', message: TYPE_MISMATCH_MESSAGE })
  }
  if (body.effectiveFrom != null && !isRealDate(body.effectiveFrom)) {
    mismatches.push({ field: 'effectiveFrom', message: TYPE_MISMATCH_MESSAGE })
  }
  if (mismatches.length > 0) return mismatches.slice(0, 1)

  // 2. 필수·범위
  const errors: FieldError[] = []
  if (body.monthlyPrice == null) {
    errors.push({ field: 'monthlyPrice', message: '월 구독요금은 필수입니다.' })
  } else if (body.monthlyPrice < 0) {
    errors.push({ field: 'monthlyPrice', message: '0 이상이어야 합니다.' })
  } else if (body.monthlyPrice > MAX_MONTHLY_PRICE) {
    errors.push({ field: 'monthlyPrice', message: '1,000,000 이하여야 합니다.' })
  }
  if (body.effectiveFrom == null) {
    errors.push({ field: 'effectiveFrom', message: '적용 시작일은 필수입니다.' })
  }
  return errors
}

// 상품 이름 길이 상한(서버 NAME_MAX_LENGTH)
const NAME_MAX_LENGTH = 100

/** 이름의 앞뒤 공백을 지우고 1~100자인지 본다(서버 normalizeName). 올바르면 정리한 이름을, 아니면 오류를 돌려준다. */
function normalizeProductName(name: string): { name: string } | { error: FieldError } {
  const trimmed = name.trim()
  if (trimmed === '') return { error: { field: 'name', message: '이름은 비어 있을 수 없습니다.' } }
  if (trimmed.length > NAME_MAX_LENGTH) {
    return { error: { field: 'name', message: `이름은 ${NAME_MAX_LENGTH}자 이하여야 합니다.` } }
  }
  return { name: trimmed }
}

// 지금 시각(ISO 일시). 월 구독요금의 마지막 확인 일시(lastVerifiedAt)에 쓴다. 화면은 한국 날짜로 바꿔 보인다
function nowIso(): string {
  return new Date().toISOString()
}

/**
 * 쪽 번호·크기(page 0부터, size 1~100, 기본 20)를 읽는다. 벗어나면 null이다(호출한 쪽이 400을 돌려준다, api-contract.md 페이지네이션).
 */
function readPageParams(request: Request): { page: number; size: number } | null {
  const params = new URL(request.url).searchParams
  const page = Number(params.get('page') ?? 0)
  const size = Number(params.get('size') ?? 20)
  if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 100) {
    return null
  }
  return { page, size }
}

/**
 * 쪽 번호·크기가 계약 범위를 벗어났을 때의 400 응답. 서버처럼 어긋난 필드마다 fieldErrors를 담는다
 * (page 0 이상, size 1 이상 100 이하, 숫자가 아니면 "형식이 올바르지 않습니다."). 모두 올바르면 undefined다.
 */
function pageParamsProblem(request: Request) {
  const params = new URL(request.url).searchParams
  const errors: FieldError[] = []
  const pageText = params.get('page')
  if (pageText !== null) {
    const page = Number(pageText)
    if (pageText.trim() === '' || !Number.isInteger(page)) {
      errors.push({ field: 'page', message: TYPE_MISMATCH_MESSAGE })
    } else if (page < 0) {
      errors.push({ field: 'page', message: '0 이상이어야 합니다.' })
    }
  }
  const sizeText = params.get('size')
  if (sizeText !== null) {
    const size = Number(sizeText)
    if (sizeText.trim() === '' || !Number.isInteger(size)) {
      errors.push({ field: 'size', message: TYPE_MISMATCH_MESSAGE })
    } else if (size < 1) {
      errors.push({ field: 'size', message: '1 이상이어야 합니다.' })
    } else if (size > 100) {
      errors.push({ field: 'size', message: '100 이하여야 합니다.' })
    }
  }
  return errors.length > 0 ? validationFailed(request, errors) : undefined
}

// 전체 목록에서 한 쪽을 잘라 PageMeta 형식(content, page, size, totalElements, totalPages)으로 만든다
function toPage<T>(all: T[], { page, size }: { page: number; size: number }) {
  return {
    content: all.slice(page * size, (page + 1) * size),
    page,
    size,
    totalElements: all.length,
    totalPages: Math.ceil(all.length / size),
  }
}

// 관리자 API의 없는 데이터는 도메인 코드 없이 404 RESOURCE_NOT_FOUND다(error-codes.md). detail 문구는 서버가 데이터 종류별로 쓰는 문장이다
function notFound(request: Request, detail: string) {
  return problemResponse(request, 404, 'Not Found', 'RESOURCE_NOT_FOUND', detail)
}

const PRODUCT_NOT_FOUND = '해당 구독상품을 찾을 수 없습니다.'
const PRICE_NOT_FOUND = '해당 월 구독요금을 찾을 수 없습니다.'
const PROVIDER_NOT_FOUND = '해당 제공처를 찾을 수 없습니다.'
const EXECUTION_NOT_FOUND = '해당 수집 실행을 찾을 수 없습니다.'

// 수정·삭제가 같이 쓰는 서버 문구(ErrorCode.PRICE_NOT_LATEST)
const PRICE_NOT_LATEST_DETAIL = '가장 최근 월 구독요금만 수정하거나 삭제할 수 있습니다.'

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
      // 본문 검사(priceBodyProblems)가 먼저 걸러 여기까지 오지 않지만, 검사 결과 종류를 빠짐없이 처리하려고 둔다
      return fieldValidationError(request, 'effectiveFrom', TYPE_MISMATCH_MESSAGE)
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
        '적용 시작일은 마지막 월 구독요금의 시작일보다 늦어야 합니다.',
      )
    case 'FIRST_IN_FUTURE':
      return ruleViolation(
        request,
        'PRICE_FIRST_START_DATE_IN_FUTURE',
        '첫 월 구독요금의 적용 시작일은 오늘 또는 그 이전이어야 합니다.',
      )
  }
}

export const adminHandlers: RequestHandler[] = [
  /*
   * 데모 계정(목업 역할 DEMO, Task 109)의 관리자 쓰기 막기. 서버는 데모 계정에 /api/admin/** 의 조회(GET)만 허락하고
   * 그 밖의 모든 요청(등록·수정·삭제·실행)을 403 FORBIDDEN으로 막는다(SecurityConfig). 목업도 같게 답한다.
   * 배열 맨 앞에 두는 이유: MSW는 앞에서부터 주소가 맞는 핸들러를 찾고, 아무것도 돌려주지 않으면(undefined) 다음 핸들러로 넘어간다.
   * 그래서 데모가 아니거나 GET이면 그냥 통과시켜 아래의 원래 핸들러가 답한다.
   * 다른 역할(비로그인·회원)은 지금처럼 검사하지 않는다(위 "권한" 주석의 결정).
   */
  http.all('/api/admin/*', ({ request }) => {
    if (request.method === 'GET') return
    return denyDemo(request)
  }),

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
    const raw = (await request.json()) as Partial<CreateAdminProductRequest>

    // 1. 요청 본문 검사(서버 Bean Validation): 형식 불일치 → 필수·범위. 존재 확인·규칙보다 항상 먼저다
    const bodyErrors = priceBodyProblems(raw)
    if (raw.productType != null && raw.productType !== 'SINGLE' && raw.productType !== 'BUNDLE') {
      return fieldValidationError(request, 'productType', TYPE_MISMATCH_MESSAGE)
    }
    if (typeof raw.name !== 'string' || raw.name.trim() === '') {
      bodyErrors.push({ field: 'name', message: '이름은 필수입니다.' })
    }
    if (raw.productType == null) {
      bodyErrors.push({ field: 'productType', message: '상품 종류는 필수입니다.' })
    }
    // 이전(Task 076 전): ottServiceId 하나만 받았다
    if (!Array.isArray(raw.ottServiceIds) || raw.ottServiceIds.length === 0) {
      bodyErrors.push({ field: 'ottServiceIds', message: '서비스는 필수입니다.' })
    } else if (raw.ottServiceIds.some((id) => !Number.isInteger(id) || id < 1)) {
      bodyErrors.push({ field: 'ottServiceIds', message: POSITIVE_MESSAGE })
    }
    if (typeof raw.isLowCost !== 'boolean') {
      bodyErrors.push({ field: 'isLowCost', message: '저가형 여부는 필수입니다.' })
    }
    if (bodyErrors.length > 0) return validationFailed(request, bodyErrors)
    // 위 검사로 필수 값이 모두 채워졌음이 확인됐다
    const body = raw as CreateAdminProductRequest

    // 2. 서비스 계층 검사 순서(목업): 이름 길이 → 구성 개수·중복 → 서비스 존재 → 판매 중 번들 상한 → 첫 요금 시작일(409)
    //    서버의 정확한 순서는 확인하지 않았다(목업은 화면 확인용)
    const normalized = normalizeProductName(body.name)
    if ('error' in normalized) return validationFailed(request, [normalized.error])
    const serviceIds = [...new Set(body.ottServiceIds)].sort((a, b) => a - b)
    if (serviceIds.length !== body.ottServiceIds.length) {
      return fieldValidationError(request, 'ottServiceIds', '같은 서비스를 두 번 넣을 수 없습니다.')
    }
    if (body.productType === 'SINGLE' && serviceIds.length !== 1) {
      return fieldValidationError(request, 'ottServiceIds', '단품은 서비스 1개만 넣을 수 있습니다.')
    }
    if (body.productType === 'BUNDLE' && serviceIds.length < 2) {
      return fieldValidationError(request, 'ottServiceIds', '번들은 서비스 2개 이상이 필요합니다.')
    }
    if (!serviceIds.every((id) => MOCK_PRODUCTS.some((p) => p.ottServiceId === id))) {
      return fieldValidationError(request, 'ottServiceIds', '존재하지 않는 서비스입니다.')
    }
    // 판매 중 번들 상한. 화면 확인용 시나리오 ?mockScenario=bundle-limit이면 이미 13개가 찬 것으로 본다
    const activeBundleCount = hasMockScenario(request, 'bundle-limit')
      ? MAX_ACTIVE_BUNDLES
      : getAllProducts().filter((p) => p.productType === 'BUNDLE' && p.isActive).length
    if (body.productType === 'BUNDLE' && activeBundleCount >= MAX_ACTIVE_BUNDLES) {
      return fieldValidationError(
        request,
        'productType',
        `판매 중인 번들은 ${MAX_ACTIVE_BUNDLES}개까지 등록할 수 있습니다.`,
      )
    }
    const today = getTodayDateString()
    const dateProblem = startDateProblem(
      request,
      checkFirstPriceStartDate(body.effectiveFrom, today).issue,
    )
    if (dateProblem) return dateProblem

    const product: AdminProduct = {
      productId: nextProductId++,
      name: normalized.name,
      productType: body.productType,
      ottServiceIds: serviceIds,
      isLowCost: body.isLowCost,
      isActive: true,
      prices: [
        {
          priceId: nextPriceId++,
          monthlyPrice: body.monthlyPrice,
          effectiveFrom: body.effectiveFrom,
          lastVerifiedAt: nowIso(),
        },
      ],
    }
    return okProduct(saveProduct(product), 201)
  }),

  // 이름·판매 상태 수정
  http.patch('/api/admin/products/:productId', async ({ request, params }) => {
    await wait()
    // 1. 경로 변수·본문 검사(404보다 먼저). 서버는 보낸 필드만 바꾸고 둘 다 없으면 400이다
    const idProblem = pathIdProblem(request, { productId: params.productId })
    if (idProblem) return idProblem
    const body = (await request.json()) as UpdateAdminProductRequest
    if (body.isActive != null && typeof body.isActive !== 'boolean') {
      return fieldValidationError(request, 'isActive', TYPE_MISMATCH_MESSAGE)
    }
    if (body.isLowCost != null && typeof body.isLowCost !== 'boolean') {
      return fieldValidationError(request, 'isLowCost', TYPE_MISMATCH_MESSAGE)
    }
    if (body.name == null && body.isActive == null && body.isLowCost == null) {
      return fieldValidationError(
        request,
        'request',
        'name, isActive, isLowCost 중 하나는 보내야 합니다.',
      )
    }
    let newName: string | undefined
    if (body.name != null) {
      const normalized = normalizeProductName(body.name)
      if ('error' in normalized) return validationFailed(request, [normalized.error])
      newName = normalized.name
    }

    // 2. 상품을 찾아 바꾼다
    const product = getAllProducts().find((p) => p.productId === Number(params.productId))
    if (!product) return notFound(request, PRODUCT_NOT_FOUND)
    // 3. 판매 종료된 번들을 다시 판매하려는데 판매 중 번들이 이미 13개면 거절한다(계약 updateAdminProduct).
    //    화면 확인용 시나리오 ?mockScenario=bundle-limit이면 이미 13개가 찬 것으로 본다
    const isReactivatingBundle =
      product.productType === 'BUNDLE' && !product.isActive && body.isActive === true
    const activeBundleCount = hasMockScenario(request, 'bundle-limit')
      ? MAX_ACTIVE_BUNDLES
      : getAllProducts().filter((p) => p.productType === 'BUNDLE' && p.isActive).length
    if (isReactivatingBundle && activeBundleCount >= MAX_ACTIVE_BUNDLES) {
      return fieldValidationError(
        request,
        'isActive',
        `판매 중인 번들은 ${MAX_ACTIVE_BUNDLES}개까지 둘 수 있습니다.`,
      )
    }
    return okProduct(
      saveProduct({
        ...product,
        name: newName ?? product.name,
        isActive: body.isActive ?? product.isActive,
        isLowCost: body.isLowCost ?? product.isLowCost,
      }),
    )
  }),

  // 월 구독요금 줄 추가
  http.post('/api/admin/products/:productId/prices', async ({ request, params }) => {
    await wait()
    // 1. 경로 변수·본문 검사(404·409보다 먼저, 서버 컨트롤러 단계)
    const idProblem = pathIdProblem(request, { productId: params.productId })
    if (idProblem) return idProblem
    const body = (await request.json()) as AdminProductPriceRequest
    const bodyErrors = priceBodyProblems(body)
    if (bodyErrors.length > 0) return validationFailed(request, bodyErrors)

    // 2. 상품 확인 → 시작일 규칙(409)
    const product = getAllProducts().find((p) => p.productId === Number(params.productId))
    if (!product) return notFound(request, PRODUCT_NOT_FOUND)
    const today = getTodayDateString()
    const dateProblem = startDateProblem(
      request,
      checkAddPriceStartDate(sortPricesAscending(product.prices), body.effectiveFrom, today).issue,
    )
    if (dateProblem) return dateProblem

    const row: AdminProductPrice = {
      priceId: nextPriceId++,
      monthlyPrice: body.monthlyPrice,
      effectiveFrom: body.effectiveFrom,
      lastVerifiedAt: nowIso(),
    }
    return okProduct(saveProduct({ ...product, prices: [...product.prices, row] }), 201)
  }),

  // 가장 최근 줄 수정. 금액을 직접 고친 것은 확인한 것으로 보아 마지막 확인일도 오늘로 갱신한다(사용자 결정 2026-10-11)
  http.patch('/api/admin/products/:productId/prices/:priceId', async ({ request, params }) => {
    await wait()
    // 1. 경로 변수·본문 검사(404·409보다 먼저, 서버 컨트롤러 단계)
    const idProblem = pathIdProblem(request, {
      productId: params.productId,
      priceId: params.priceId,
    })
    if (idProblem) return idProblem
    const body = (await request.json()) as AdminProductPriceRequest
    const bodyErrors = priceBodyProblems(body)
    if (bodyErrors.length > 0) return validationFailed(request, bodyErrors)

    // 2. 상품·요금 줄 확인(404) → 가장 최근 줄인지(409) → 시작일 규칙(409). 서버 updatePrice와 같은 순서다
    const product = getAllProducts().find((p) => p.productId === Number(params.productId))
    if (!product) return notFound(request, PRODUCT_NOT_FOUND)
    const sorted = sortPricesAscending(product.prices)
    const target = sorted.find((row) => row.priceId === Number(params.priceId))
    if (!target) return notFound(request, PRICE_NOT_FOUND)
    if (sorted[sorted.length - 1].priceId !== target.priceId) {
      return ruleViolation(request, 'PRICE_NOT_LATEST', PRICE_NOT_LATEST_DETAIL)
    }
    const dateProblem = startDateProblem(
      request,
      checkEditLastPriceStartDate(sorted, body.effectiveFrom, getTodayDateString()).issue,
    )
    if (dateProblem) return dateProblem

    return okProduct(
      saveProduct({
        ...product,
        prices: product.prices.map((row) =>
          row.priceId === target.priceId
            ? {
                ...row,
                monthlyPrice: body.monthlyPrice,
                effectiveFrom: body.effectiveFrom,
                lastVerifiedAt: nowIso(),
              }
            : row,
        ),
      }),
    )
  }),

  // 가장 최근 줄 삭제. 줄이 하나뿐이면 삭제할 수 없다
  http.delete('/api/admin/products/:productId/prices/:priceId', async ({ request, params }) => {
    await wait()
    const idProblem = pathIdProblem(request, {
      productId: params.productId,
      priceId: params.priceId,
    })
    if (idProblem) return idProblem
    const product = getAllProducts().find((p) => p.productId === Number(params.productId))
    if (!product) return notFound(request, PRODUCT_NOT_FOUND)
    const sorted = sortPricesAscending(product.prices)
    const target = sorted.find((row) => row.priceId === Number(params.priceId))
    if (!target) return notFound(request, PRICE_NOT_FOUND)
    // 서버 validateDeletable 순서: 하나뿐인지 먼저, 그다음 가장 최근 줄인지
    if (sorted.length === 1) {
      return ruleViolation(
        request,
        'PRICE_LAST_ROW_CANNOT_DELETE',
        '월 구독요금이 하나뿐이라 삭제할 수 없습니다.',
      )
    }
    if (sorted[sorted.length - 1].priceId !== target.priceId) {
      return ruleViolation(request, 'PRICE_NOT_LATEST', PRICE_NOT_LATEST_DETAIL)
    }
    saveProduct({
      ...product,
      prices: product.prices.filter((row) => row.priceId !== target.priceId),
    })
    // 삭제는 본문 없는 204다(계약). 화면은 목록을 다시 불러와 새 이력을 본다
    return new HttpResponse(null, { status: 204 })
  }),

  // [요금 확인함]: 마지막 확인일만 오늘로 바꾼다. 지금 적용 중인 줄만 확인할 수 있다(지난·예정 줄은 확인 대상이 아니다)
  http.post(
    '/api/admin/products/:productId/prices/:priceId/verify',
    async ({ request, params }) => {
      await wait()
      const idProblem = pathIdProblem(request, {
        productId: params.productId,
        priceId: params.priceId,
      })
      if (idProblem) return idProblem
      const product = getAllProducts().find((p) => p.productId === Number(params.productId))
      if (!product) return notFound(request, PRODUCT_NOT_FOUND)
      const target = product.prices.find((row) => row.priceId === Number(params.priceId))
      if (!target) return notFound(request, PRICE_NOT_FOUND)

      const today = getTodayDateString()
      const sorted = sortPricesAscending(product.prices)
      const kind = getPriceRowKind(
        sorted,
        sorted.findIndex((row) => row.priceId === target.priceId),
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
            row.priceId === target.priceId ? { ...row, lastVerifiedAt: nowIso() } : row,
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

    // 1. 경로 변수·본문 형태 검사(서버 컨트롤러 단계, 404보다 먼저)
    const idProblem = pathIdProblem(request, { providerId: params.providerId })
    if (idProblem) return idProblem
    const body = (await request.json()) as Partial<UpdateAdminProviderRequest>
    const bodyErrors: FieldError[] = []
    if (body.mappingState == null) {
      bodyErrors.push({ field: 'mappingState', message: '매핑 상태는 필수입니다.' })
    } else if (!['UNMAPPED', 'MAPPED', 'IGNORED'].includes(body.mappingState)) {
      bodyErrors.push({ field: 'mappingState', message: TYPE_MISMATCH_MESSAGE })
    }
    if (body.tier == null) {
      bodyErrors.push({ field: 'tier', message: 'tier는 필수입니다.' })
    } else if (!['STANDARD', 'AD'].includes(body.tier)) {
      bodyErrors.push({ field: 'tier', message: TYPE_MISMATCH_MESSAGE })
    }
    if (bodyErrors.length > 0) return validationFailed(request, bodyErrors)
    const valid = body as UpdateAdminProviderRequest
    const ottServiceId = valid.ottServiceId ?? null

    // 2. 서비스 계층 검사 순서(서버 updateProvider): 상태와 서비스 연결이 맞는지(400) → 제공처 존재(404) → 서비스 존재(400)
    if (valid.mappingState === 'MAPPED' && ottServiceId === null) {
      return fieldValidationError(request, 'ottServiceId', 'MAPPED이면 서비스를 선택해야 합니다.')
    }
    if (valid.mappingState !== 'MAPPED' && ottServiceId !== null) {
      return fieldValidationError(
        request,
        'ottServiceId',
        'MAPPED가 아니면 서비스를 연결할 수 없습니다.',
      )
    }
    const provider = getAllProviders().find((p) => p.providerId === Number(params.providerId))
    if (!provider) return notFound(request, PROVIDER_NOT_FOUND)
    if (ottServiceId !== null && !MOCK_PRODUCTS.some((p) => p.ottServiceId === ottServiceId)) {
      return fieldValidationError(request, 'ottServiceId', '존재하지 않는 서비스입니다.')
    }

    const saved: AdminProvider = {
      ...provider,
      mappingState: valid.mappingState,
      ottServiceId,
      tier: valid.mappingState === 'MAPPED' ? valid.tier : 'STANDARD',
    }
    changedProviders.set(saved.providerId, saved)
    writeMockJson(PROVIDERS_STORAGE_KEY, [...changedProviders.values()])
    const response: CommonResponseAdminProvider = { success: true, data: saved }
    return HttpResponse.json(response)
  }),

  // 수집 실행 이력(페이지 형태, 대상 날짜 내림차순). 화면에서 접수한 실행(시뮬레이션)은 이력을 SIMULATED_COMPLETE_AFTER_READS번 읽으면 완료로 바뀐다
  http.get('/api/admin/collect/executions', async ({ request }) => {
    await wait()
    if (hasMockScenario(request, 'error')) return internalError(request)
    const pageProblem = pageParamsProblem(request)
    if (pageProblem) return pageProblem
    const pageParams = readPageParams(request)!
    if (hasMockScenario(request, 'empty')) {
      const empty: CommonResponseCollectExecutionPage = {
        success: true,
        data: toPage([], pageParams),
      }
      return HttpResponse.json(empty)
    }
    advanceSimulatedRuns()
    const body: CommonResponseCollectExecutionPage = {
      success: true,
      data: toPage(sortExecutionsForHistory(getAllExecutions()), pageParams),
    }
    return HttpResponse.json(body)
  }),

  // 실행별 변경된 작품
  http.get('/api/admin/collect/executions/:executionId/changes', async ({ request, params }) => {
    await wait()
    // 1. 경로 변수·쪽 번호 검사(서버 컨트롤러 단계, 404보다 먼저) 2. 실행 확인
    const idProblem = pathIdProblem(request, { executionId: params.executionId })
    if (idProblem) return idProblem
    const pageProblem = pageParamsProblem(request)
    if (pageProblem) return pageProblem
    const pageParams = readPageParams(request)!
    const executionId = Number(params.executionId)
    if (!getAllExecutions().some((e) => e.executionId === executionId)) {
      return notFound(request, EXECUTION_NOT_FOUND)
    }
    // 제목 순, 같으면 시즌 번호·서비스 ID 순(계약)
    const sorted = getChanges(executionId).sort(
      (a, b) =>
        a.title.localeCompare(b.title, 'ko') ||
        (a.seasonNumber ?? 0) - (b.seasonNumber ?? 0) ||
        a.ottServiceId - b.ottServiceId,
    )
    const body: CommonResponseCollectExecutionChangePage = {
      success: true,
      data: toPage(sorted, pageParams),
    }
    return HttpResponse.json(body)
  }),

  // 수동 실행: 202 접수. 판정은 judgeRun(collectRules)과 같다
  http.post('/api/admin/collect/executions', async ({ request }) => {
    await wait()
    if (hasMockScenario(request, 'run-error')) return internalError(request)

    const body = (await request.json()) as CreateCollectExecutionRequest
    // 1. 요청 본문 검사(서버 Bean Validation·JSON 형식). 계약: targetDate는 선택이라 비우거나 null이면 오늘(KST)로 본다.
    //    날짜 형식이 아니거나 모르는 범위 값은 "형식이 올바르지 않습니다."이고, 범위를 비우면 필수 오류다
    if (body.targetDate != null && !isRealDate(body.targetDate)) {
      return fieldValidationError(request, 'targetDate', TYPE_MISMATCH_MESSAGE)
    }
    if (body.tier != null && !['ALL', 'DAILY', 'WEEKLY'].includes(body.tier)) {
      return fieldValidationError(request, 'tier', TYPE_MISMATCH_MESSAGE)
    }
    if (body.tier == null) {
      return fieldValidationError(request, 'tier', '수집 범위는 필수입니다.')
    }

    // 2. 서비스 계층 검사 순서(서버 start): 범위(ALL만) → 날짜. 목업은 날짜 지정을 허용하는 로컬 환경처럼 미래만 막는다
    //    (운영은 오늘만 받아 "운영 환경에서는 오늘(KST) 날짜만 실행할 수 있습니다."가 온다)
    if (!RUNNABLE_COLLECT_TIERS.includes(body.tier)) {
      return fieldValidationError(request, 'tier', 'MVP는 ALL만 실행할 수 있습니다.')
    }
    const targetDate = body.targetDate ? body.targetDate : getTodayDateString()
    if (targetDate > getTodayDateString()) {
      return fieldValidationError(request, 'targetDate', '미래 날짜는 실행할 수 없습니다.')
    }

    // 3. 같은 날짜·범위 판정(실행 중·완료·중단됨 409)과 접수(202)
    return startRun(request, targetDate, body.tier)
  }),

  // 복구: 서버가 중단됨으로 판정한 실행을 실패로 기록한다. 응답은 버튼 값을 다시 계산한 실행이다(canRestart로 이어서 재시작할 수 있는지 안다)
  http.post('/api/admin/collect/executions/:executionId/recover', async ({ request, params }) => {
    await wait()
    const idProblem = pathIdProblem(request, { executionId: params.executionId })
    if (idProblem) return idProblem
    const executionId = Number(params.executionId)
    const target = getAllExecutions().find((e) => e.executionId === executionId)
    if (!target) return notFound(request, EXECUTION_NOT_FOUND)
    if (!computeCanRecover(target)) {
      return ruleViolation(request, 'BATCH_NOT_STOPPED', '중단된 실행만 복구할 수 있습니다.')
    }
    saveExecution({
      ...target,
      status: 'FAILED',
      // 복구는 "끝난 시각"을 알 수 없다(서버가 언제 멈췄는지 모른다). 그래서 종료 시각을 기록하지 않고 null로 두며 화면은 "—"로 보인다.
      // 계약: recover 응답의 endedAt은 null이다. Spring Batch의 recover는 END_TIME을 채우지만 API는 복구 표시를 읽어 null로 내린다
      endedAt: null,
      // 계약: 관리자가 복구한 실행의 실패 사유는 이 고정 문구다
      failureReason: '중단된 실행을 관리자가 실패로 복구함',
    })
    const recovered = getAllExecutions().find((e) => e.executionId === executionId)!
    const body: CommonResponseCollectExecution = { success: true, data: recovered }
    return HttpResponse.json(body)
  }),

  // 다시 실행: 실패한 실행을 같은 날짜·범위로 새로 접수한다. 새 executionId를 준다(202).
  // 거절은 그 날짜·범위의 최신 실행 상태(실행 중 / 성공 / 중단됨)와 다른 실행 중 여부로 정한다(startRun의 judgeRun).
  // "실패한 실행이 아님" 전용 코드는 없다. BATCH_NOT_STOPPED는 복구 API에서만 쓴다(계약)
  http.post('/api/admin/collect/executions/:executionId/restart', async ({ request, params }) => {
    await wait()
    if (hasMockScenario(request, 'run-error')) return internalError(request)
    const idProblem = pathIdProblem(request, { executionId: params.executionId })
    if (idProblem) return idProblem
    const target = getAllExecutions().find((e) => e.executionId === Number(params.executionId))
    if (!target) return notFound(request, EXECUTION_NOT_FOUND)
    // 예전 방식(Task 029~044): 실패가 아니면 판정 결과, 판정이 OK면 BATCH_NOT_STOPPED로 거절했다
    // if (!canRestart(target.status)) {
    //   return (
    //     judgementResponse(request, target.targetDate, target.tier) ??
    //     ruleViolation(request, 'BATCH_NOT_STOPPED', '실패한 실행만 다시 실행할 수 있습니다.')
    //   )
    // }
    return startRun(request, target.targetDate, target.tier)
  }),
]
