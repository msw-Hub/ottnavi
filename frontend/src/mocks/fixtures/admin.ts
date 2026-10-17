/*
 * 관리자 화면 목업 데이터다(Task 029, SCR-14 OTT 구독상품·월 구독요금 / SCR-15 제공처 매핑 / SCR-16 수집).
 *
 * 날짜를 하드코딩하지 않는 이유: "확인일 90일 초과", "예정된 월 구독요금", "방금 시작한 수집"은 오늘을 기준으로 의미가 정해진다.
 * 2026-10-11에 고정한 날짜를 쓰면 시간이 지난 뒤 모든 요금이 "오래됨"으로 보이거나 예정 줄이 과거가 된다.
 * 그래서 아래 build* 함수는 기준일(오늘)을 받아 그날에서 상대적으로 날짜를 만든다. 호출할 때마다 새 객체를 만들어,
 * 핸들러가 값을 고쳐도 원본 fixture가 오염되지 않는다.
 *
 * 값의 출처:
 * - 제공처 매핑 8행·관련 없음 2행: docs/ROADMAP.md Task 004 기록(TMDB KR 제공처 조사, 2026-10-05)의 실제 값이다.
 * - 미매핑 3행: 화면의 "미매핑 강조"를 확인하려는 예시다. 이름과 ID는 Task 004가 기록한 "매핑되지 않은 KR 제공처" 목록의 실제 값을 썼지만,
 *   "방금 새로 발견됐다"는 발견 시각은 예시다(실제 수집 결과가 아니다).
 * - OTT 구독상품 7개: 이름과 현재 월 구독요금은 이 파일의 MOCK_PRODUCTS를 그대로 따른다(회원 요금 화면과 금액이 어긋나지 않게 같은 값을 쓴다).
 *   MOCK_PRODUCTS는 Task 004 가격표에 맞춰져 있다(2026-10-11).
 *   이력(과거 줄·예정 줄·확인일)은 화면 확인용 예시다.
 * - 수집 실행 이력과 변경된 작품: 모두 예시다.
 */
import type { AvailabilityStatus } from '@/components/common/displayTypes'
import type {
  AdminProduct,
  AdminProductPrice,
  AdminProvider,
  CollectExecutionChange,
  CollectExecution,
} from '@/api/generated/model'
import { addDays } from '@/lib/dateUtils'

/* ===== OTT 구독상품 기본 데이터 (회원 요금 화면·관리자 화면이 함께 쓴다) =====
 * 서비스별 상품·기본 요금 목업 데이터다. 원래 fixtures/pricing.ts였고(Task 028) 관리자 목업이 같은 목록을 써서 Task 031 6-7에서 여기로 옮겼다.
 * 금액은 ROADMAP Task 004 가격표(조사일 2026-10-05, 표시 가격 그대로)에 맞췄다(2026-10-11 사용자 결정). 요금제 기준: 넷플릭스·디즈니+·티빙·웨이브
 * 스탠다드, 왓챠 베이직(STANDARD로 사용), 애플 TV+ 단일 요금, 쿠팡플레이는 와우 멤버십(광고 포함) 월 요금 7,890원 기준이다.
 * 상품은 서비스마다 하나(단품)로 단순화했다. 번들 상품은 2단계 이후에 다룬다.
 * 관리자가 요금을 확인(verify)해도 회원 요금 화면의 기준 일시(MOCK_PRICE_VERIFIED_AT)는 바뀌지 않는다(연결하지 않기로 했다, 사용자 결정).
 */

export interface MockProduct {
  ottServiceId: number // 서비스 내부 ID
  productId: number // 상품 ID
  productName: string // 상품(요금제) 이름
  adminPrice: number // 관리자 기본 월 요금(원)
}

// 요금표 적용 시작일(계약 ServicePrice.effectiveFrom)
export const MOCK_PRICE_EFFECTIVE_FROM = '2026-10-01'

// 관리자가 요금을 마지막으로 확인한 일시(계약 ServicePrice.lastVerifiedAt, date-time). 화면은 한국 날짜만 보인다
export const MOCK_PRICE_VERIFIED_AT = '2026-10-08T18:30:00+09:00'

export const MOCK_PRODUCTS: MockProduct[] = [
  { ottServiceId: 1, productId: 101, productName: '넷플릭스 스탠다드', adminPrice: 13500 },
  { ottServiceId: 2, productId: 102, productName: '디즈니+ 스탠다드', adminPrice: 9900 },
  { ottServiceId: 3, productId: 103, productName: '애플 TV+', adminPrice: 6500 },
  { ottServiceId: 4, productId: 104, productName: '티빙 스탠다드', adminPrice: 13500 },
  { ottServiceId: 5, productId: 105, productName: '웨이브 스탠다드', adminPrice: 10900 },
  { ottServiceId: 6, productId: 106, productName: '왓챠 베이직', adminPrice: 7900 },
  { ottServiceId: 7, productId: 107, productName: '쿠팡플레이', adminPrice: 7890 },
]

/*
 * 저가형 단품·번들 목업 상품(Task 076). 위 MOCK_PRODUCTS(서비스마다 표준 단품 1개)는 플랜 계산 목업이 서비스 ID로 찾아 쓰므로 그대로 두고,
 * 요금 화면(SCR-09)과 관리자 상품 화면(SCR-14)에만 이 상품들을 덧붙인다. 플랜 계산 목업은 번들·저가형을 계산하지 않는다(1차안 한계).
 * 구성은 docs/guides/BUNDLE_PRICE_MODE_FLOW.md의 예시(넷플릭스 광고형 7,000원, 티빙+웨이브 번들 16,000원)를 따랐고, 나머지는 화면 확인용 예시다.
 */
export interface MockExtraProduct {
  productId: number // 상품 ID(단품 108~110, 번들 111~112. 새로 등록한 상품은 201부터라 겹치지 않는다)
  productName: string // 상품(요금제) 이름
  ottServiceIds: number[] // 포함 서비스 ID(오름차순). 단품 1개, 번들 2개 이상
  isLowCost: boolean // 저가형(광고형·베이직 등)인지
  adminPrice: number // 관리자 기본 월 요금(원)
}

export const MOCK_EXTRA_PRODUCTS: MockExtraProduct[] = [
  // 저가형 단품: 모드를 켜면 넷플릭스·티빙은 이 상품이 계산에 쓰인다
  {
    productId: 108,
    productName: '넷플릭스 광고형 스탠다드',
    ottServiceIds: [1],
    isLowCost: true,
    adminPrice: 7000,
  },
  {
    productId: 109,
    productName: '티빙 광고형 스탠다드',
    ottServiceIds: [4],
    isLowCost: true,
    adminPrice: 5500,
  },
  // 표준이지만 더 비싼 단품: 모드와 관계없이 계산에 쓰지 않는다
  {
    productId: 110,
    productName: '디즈니+ 프리미엄',
    ottServiceIds: [2],
    isLowCost: false,
    adminPrice: 13900,
  },
  // 번들: 표준 번들은 항상, 저가형 번들은 모드를 켰을 때만 계산에 쓴다
  {
    productId: 111,
    productName: '티빙·웨이브 더블 이용권',
    ottServiceIds: [4, 5],
    isLowCost: false,
    adminPrice: 16000,
  },
  {
    productId: 112,
    productName: '디즈니+·티빙 광고형 번들',
    ottServiceIds: [2, 4],
    isLowCost: true,
    adminPrice: 11900,
  },
]

// 판매 종료로 둔 목업 상품(왓챠 베이직). 관리자 화면의 종료 상품 표시 확인용이고, 회원 요금 목록에는 나오지 않는다
const SUSPENDED_PRODUCT_IDS = [106]

// 판매 중인 상품인지 확인한다(회원 요금 API는 판매 중인 상품만 다룬다)
export function isProductOnSale(productId: number): boolean {
  return !SUSPENDED_PRODUCT_IDS.includes(productId)
}

// 서비스 ID로 상품을 찾는다. 없으면 undefined
export function findMockProduct(ottServiceId: number): MockProduct | undefined {
  return MOCK_PRODUCTS.find((product) => product.ottServiceId === ottServiceId)
}

// 한국 시간 기준 날짜 + 'HH:mm'을 ISO 일시 문자열로 만든다(+09:00 오프셋이 붙어 어느 시간대에서도 같은 시각을 가리킨다)
function toSeoulIso(date: string, time: string): string {
  return `${date}T${time}:00+09:00`
}

/* ===== OTT 구독상품·월 구독요금 (SCR-14) ===== */

// 월 구독요금 줄 하나를 만든다. 줄 ID는 OTT 구독상품 ID * 10 + 순번이라 상품마다 겹치지 않는다
function priceRow(
  productId: number,
  sequence: number,
  monthlyPrice: number,
  effectiveFrom: string,
  lastVerifiedAt: string,
): AdminProductPrice {
  // 계약의 lastVerifiedAt은 일시(date-time)라 한국 시간 오전 9시로 만든다(날짜 기준 판정이라 시각은 중요하지 않다)
  return {
    priceId: productId * 10 + sequence,
    monthlyPrice,
    effectiveFrom,
    lastVerifiedAt: toSeoulIso(lastVerifiedAt, '09:00'),
  }
}

/**
 * OTT 구독상품 7개(서비스마다 단품 하나)와 월 구독요금 이력을 만든다.
 * - 넷플릭스: 줄 3개(과거 / 지금 적용 중 / 예정) — "다음 줄 시작일의 전날"까지가 적용 기간임을 보이는 예시
 * - 애플 TV+: 마지막 확인일이 오늘로부터 120일 전 — 오래됨 태그(90일 초과) 확인용
 * - 왓챠: 판매 종료(isActive=false) — 종료 상품 표시 확인용
 * - 나머지: 줄 하나, 최근에 확인함
 * 지금 적용 중인 줄의 금액은 위 MOCK_PRODUCTS(SCR-09)의 기본 월 요금과 같다.
 */
export function buildMockAdminProducts(today: string): AdminProduct[] {
  const singles = MOCK_PRODUCTS.map((product): AdminProduct => {
    const { productId, productName, ottServiceId, adminPrice } = product
    let prices: AdminProductPrice[]
    let isActive = true

    if (productId === 101) {
      // 넷플릭스: 과거 줄 → 지금 적용 중 → 예정 줄(적용 시작일이 오늘보다 뒤)
      prices = [
        priceRow(productId, 1, 12000, addDays(today, -400), addDays(today, -200)),
        priceRow(productId, 2, adminPrice, addDays(today, -10), addDays(today, -5)),
        priceRow(productId, 3, 14500, addDays(today, 30), addDays(today, -2)),
      ]
    } else if (productId === 103) {
      // 애플 TV+: 확인일이 90일을 넘었다
      prices = [priceRow(productId, 1, adminPrice, addDays(today, -300), addDays(today, -120))]
    } else {
      prices = [priceRow(productId, 1, adminPrice, addDays(today, -180), addDays(today, -7))]
    }
    if (!isProductOnSale(productId)) isActive = false // 왓챠: 판매 종료

    return {
      productId: productId,
      name: productName,
      productType: 'SINGLE',
      ottServiceIds: [ottServiceId],
      isLowCost: false, // 기본 7개는 모두 표준 단품이다
      isActive,
      prices,
    }
  })
  // 저가형 단품·번들(Task 076)은 줄 하나, 최근에 확인함
  const extras = MOCK_EXTRA_PRODUCTS.map((product): AdminProduct => ({
    productId: product.productId,
    name: product.productName,
    productType: product.ottServiceIds.length > 1 ? 'BUNDLE' : 'SINGLE',
    ottServiceIds: product.ottServiceIds,
    isLowCost: product.isLowCost,
    isActive: true,
    prices: [
      priceRow(product.productId, 1, product.adminPrice, addDays(today, -60), addDays(today, -3)),
    ],
  }))
  return [...singles, ...extras]
}

/* ===== 제공처 매핑 (SCR-15) ===== */

// 매핑된 제공처 한 행. 이미 정리된 제공처라 처음 발견된 시각은 오래전이다
function mappedProvider(
  today: string,
  providerId: number,
  tmdbName: string,
  ottServiceId: number,
  tier: AdminProvider['tier'] = 'STANDARD',
): AdminProvider {
  return {
    providerId,
    tmdbName,
    ottServiceId,
    tier,
    firstSeenAt: toSeoulIso(addDays(today, -200), '03:10'),
    mappingState: 'MAPPED',
  }
}

/**
 * TMDB 제공처 목록을 만든다. 순서는 매핑됨(Task 004 표 순서) → 관련 없음 → 미매핑이다.
 * 화면이 미매핑을 맨 위로 올려 강조하는 정렬은 화면(핸들러·훅) 몫이다.
 * 서비스 ID는 위 MOCK_PRODUCTS의 서비스 번호(1 넷플릭스, 2 디즈니+, 3 애플 TV+, 4 티빙, 5 웨이브, 6 왓챠, 7 쿠팡플레이)와 같다.
 */
export function buildMockAdminProviders(today: string): AdminProvider[] {
  return [
    // --- 매핑됨: ROADMAP Task 004 매핑표 8행 ---
    mappedProvider(today, 8, 'Netflix', 1),
    mappedProvider(today, 1796, 'Netflix Standard with Ads', 1, 'AD'), // 광고형. MVP에서는 사용하지 않는다(O3)
    mappedProvider(today, 337, 'Disney Plus', 2),
    mappedProvider(today, 1883, 'TVING', 4),
    mappedProvider(today, 97, 'Watcha', 6),
    mappedProvider(today, 356, 'wavve', 5),
    mappedProvider(today, 1881, 'Coupang Play', 7),
    mappedProvider(today, 350, 'Apple TV', 3),
    // --- 관련 없음: Task 004가 "국내 구독 후보로 볼 만하지만 범위 밖"으로 기록한 제공처. 운영자가 확인하고 쓰지 않기로 했다 ---
    {
      providerId: 119,
      tmdbName: 'Amazon Prime Video',
      ottServiceId: null,
      tier: 'STANDARD',
      firstSeenAt: toSeoulIso(addDays(today, -200), '03:10'),
      mappingState: 'IGNORED',
    },
    {
      providerId: 3,
      tmdbName: 'Google Play Movies',
      ottServiceId: null,
      tier: 'STANDARD',
      firstSeenAt: toSeoulIso(addDays(today, -200), '03:10'),
      mappingState: 'IGNORED',
    },
    // --- 미매핑(예시): 아직 운영자가 확인하지 않은 제공처 ---
    {
      providerId: 283,
      tmdbName: 'Crunchyroll',
      ottServiceId: null,
      tier: 'STANDARD',
      firstSeenAt: toSeoulIso(addDays(today, -1), '03:12'),
      mappingState: 'UNMAPPED',
    },
    {
      providerId: 11,
      tmdbName: 'MUBI',
      ottServiceId: null,
      tier: 'STANDARD',
      firstSeenAt: toSeoulIso(addDays(today, -2), '03:12'),
      mappingState: 'UNMAPPED',
    },
    {
      providerId: 538,
      tmdbName: 'Plex',
      ottServiceId: null,
      tier: 'STANDARD',
      firstSeenAt: toSeoulIso(addDays(today, -5), '03:15'),
      mappingState: 'UNMAPPED',
    },
  ]
}

/* ===== 수집 (SCR-16) ===== */

/**
 * 수집 실행 이력을 만든다(최근 시작한 순). 성공·중단됨·실패가 있고, 실행 중은 화면에서 접수한 실행으로 생긴다.
 * - 5번 COMPLETED: 오늘 날짜, 40분 전에 시작해 8분 전에 끝남, 변경 0건
 * - 4번 COMPLETED: 어제 기준, 24분 걸림, 변경 6건
 * - 3번 STOPPED: 서버가 갑자기 멈춰 기록만 "실행 중"으로 남은 것을 서버가 중단됨으로 판정한 실행 → [복구] 대상(canRecover)
 * - 2번 FAILED: 외부(TMDB) 응답 지연으로 실패 → [다시 실행] 대상(canRestart, 같은 날짜·범위의 최신 실패이고 실행 중인 수집이 없다)
 * - 1번 COMPLETED: 4일 전 기준, 1시간 5분 걸림, 변경 3건
 * 5번의 시각만 "지금"에서 빼서 만든다(그 외는 날짜 + 새벽 3시). 지금 시각을 쓰므로 호출 시점마다 조금씩 달라진다.
 *
 * 기본 이력에 "실행 중"을 두지 않는 이유(Task 045 결정 12): 수집은 전체에서 동시에 하나만 돌아, 계속 실행 중인 행이 있으면
 * 어떤 날짜의 실행도, [다시 실행]도 모두 막힌다(canRestart가 전부 거짓). 그래서 예전 5번(오늘 실행 중)을 오늘 성공으로 바꿨다.
 * 실행 중 모습과 BATCH_ALREADY_RUNNING은 [수집 실행]으로 접수한 뒤 다른 날짜를 한 번 더 실행하면 볼 수 있다.
 * canRecover·canRestart는 서버 규칙(collectRules의 computeCanRecover·computeCanRestart)과 같은 값을 미리 적어 둔 것이고,
 * 목업 핸들러는 응답할 때마다 지금 상태로 다시 계산한다(복구·재실행 뒤에도 값이 맞도록).
 */
export function buildMockCollectExecutions(
  today: string,
  now: Date = new Date(),
): CollectExecution[] {
  const d1 = addDays(today, -1)
  const d2 = addDays(today, -2)
  const d3 = addDays(today, -3)
  const d4 = addDays(today, -4)
  return [
    // 예전 값(Task 029~044): 오늘 실행 중으로 계속 남는 행이었다. 위 주석의 이유로 성공으로 바꿨다
    // {
    //   executionId: 5,
    //   targetDate: today,
    //   tier: 'ALL',
    //   status: 'RUNNING',
    //   startedAt: new Date(now.getTime() - 8 * 60_000).toISOString(),
    //   endedAt: null,
    //   processedCount: 1240,
    //   changedCount: 12,
    //   failureReason: null,
    // },
    {
      executionId: 5,
      targetDate: today,
      tier: 'ALL',
      status: 'COMPLETED',
      startedAt: new Date(now.getTime() - 40 * 60_000).toISOString(),
      endedAt: new Date(now.getTime() - 8 * 60_000).toISOString(),
      processedCount: 3205,
      changedCount: 0,
      failureReason: null,
      canRecover: false,
      canRestart: false,
      recalcTargetCount: null, // 재계산 결과 표시는 Task 069 화면 몫이라 목업은 비워 둔다
    },
    {
      executionId: 4,
      targetDate: d1,
      tier: 'ALL',
      status: 'COMPLETED',
      startedAt: toSeoulIso(d1, '03:00'),
      endedAt: toSeoulIso(d1, '03:24'),
      processedCount: 3180,
      changedCount: MOCK_CHANGES_EXEC_4.length,
      failureReason: null,
      canRecover: false,
      canRestart: false,
      recalcTargetCount: null, // 재계산 결과 표시는 Task 069 화면 몫이라 목업은 비워 둔다
    },
    {
      executionId: 3,
      targetDate: d2,
      tier: 'ALL',
      status: 'STOPPED',
      startedAt: toSeoulIso(d2, '03:00'),
      endedAt: null,
      processedCount: 740,
      changedCount: 5,
      failureReason: null,
      canRecover: true, // 중단됨이라 복구할 수 있다
      canRestart: false,
      recalcTargetCount: null,
    },
    {
      executionId: 2,
      targetDate: d3,
      tier: 'ALL',
      status: 'FAILED',
      startedAt: toSeoulIso(d3, '03:00'),
      endedAt: toSeoulIso(d3, '03:07'),
      processedCount: 310,
      changedCount: 2,
      failureReason: 'TMDB 응답 지연(연속 3회 시간 초과)으로 중단했습니다.',
      canRecover: false,
      canRestart: true, // 같은 날짜·범위의 최신 실패이고 실행 중인 수집이 없다
      recalcTargetCount: null,
    },
    {
      executionId: 1,
      targetDate: d4,
      tier: 'ALL',
      status: 'COMPLETED',
      startedAt: toSeoulIso(d4, '03:00'),
      endedAt: toSeoulIso(d4, '04:05'),
      processedCount: 3150,
      changedCount: MOCK_CHANGES_EXEC_1.length,
      failureReason: null,
      canRecover: false,
      canRestart: false,
      recalcTargetCount: null, // 재계산 결과 표시는 Task 069 화면 몫이라 목업은 비워 둔다
    },
  ]
}

// 변경 한 건. 이전 상태가 null이면 이번에 처음 확인된 신규 작품이다
function change(
  mediaType: CollectExecutionChange['mediaType'],
  tmdbId: number,
  title: string,
  ottServiceId: number,
  previousStatus: AvailabilityStatus | null,
  currentStatus: AvailabilityStatus,
  seasonNumber: number | null = mediaType === 'tv' ? 1 : null, // 드라마는 시즌 단위로 기록된다(계약). 예시는 시즌 1로 둔다
): CollectExecutionChange {
  return { mediaType, tmdbId, title, seasonNumber, ottServiceId, previousStatus, currentStatus }
}

// 4번 실행(어제 기준 성공)에서 바뀐 작품: 있음/없음/모름 전환 + 신규(이전 null)를 섞었다
const MOCK_CHANGES_EXEC_4: CollectExecutionChange[] = [
  change('movie', 496243, '기생충', 4, 'NOT_AVAILABLE', 'AVAILABLE'),
  change('movie', 838209, '파묘', 1, 'UNKNOWN', 'AVAILABLE'),
  change('tv', 136283, '더 글로리', 1, 'AVAILABLE', 'NOT_AVAILABLE'),
  change('tv', 93405, '오징어 게임', 1, 'AVAILABLE', 'UNKNOWN'),
  change('movie', 837799, '헌트', 6, null, 'AVAILABLE'),
  change('movie', 491584, '버닝', 5, null, 'NOT_AVAILABLE'),
]

// 1번 실행(4일 전 기준 성공)에서 바뀐 작품
const MOCK_CHANGES_EXEC_1: CollectExecutionChange[] = [
  change('tv', 96578, '슬기로운 의사생활', 1, 'NOT_AVAILABLE', 'AVAILABLE'),
  change('movie', 496243, '기생충', 5, 'AVAILABLE', 'NOT_AVAILABLE'),
  change('movie', 838209, '파묘', 4, null, 'AVAILABLE'),
]

/**
 * 실행별 변경된 작품 목록을 만든다. 성공한 실행 중 4번·1번에만 목록이 있다(5번은 변경 0건이라 빈 목록).
 * 실행 중·중단됨·실패 실행이나 없는 실행 ID는 빈 목록이다(중간까지의 변경은 이 목업에서 보여 주지 않는다).
 */
export function buildMockCollectChanges(executionId: number): CollectExecutionChange[] {
  if (executionId === 4) return MOCK_CHANGES_EXEC_4.map((c) => ({ ...c }))
  if (executionId === 1) return MOCK_CHANGES_EXEC_1.map((c) => ({ ...c }))
  return []
}
