/*
 * 플랜 결과(SCR-10) 목업 데이터를 만드는 함수 모음이다(Task 028).
 *
 * 작품명은 실제 작품이지만 제공 현황·러닝타임은 화면 확인용 예시다. "예시 드라마 A"는 가상 작품이다(fixtures/titles.ts 참고).
 * 모든 결과는 화면에서 "확정"이 아니라 "확인된 범위"로 보인다(frontend.md).
 *
 * 만들 수 있는 모양(시나리오):
 * - different (기본): 추천형·절약형·간편형의 선택이 서로 다르다 → 초안 3개
 * - same:       세 유형의 선택이 같다 → 초안은 그대로 3개이고 내용만 같다(합쳐 표시는 프론트가 mergeIdenticalDrafts로 한다). same-split은 same의 별칭
 *
 * 이 데이터가 화면에서 확인하게 해 주는 경우:
 * - 긴 시즌의 달별 분할: "예시 드라마 A 시즌 1"(19시간)이 1월 앞부분 / 2월 이어서로 나뉜다. 추천형은 서비스까지 바뀐다(넷플릭스 → 티빙)
 * - 가격 출처 4가지: 온보딩 설정에 따라 ADMIN / USER_OVERRIDE(요금 화면에서 고친 금액) / ALREADY_PAID(이번 달 이미 결제) / FREE(무료 이용)
 * - 구독 없는 달(절약형 3개월째): products가 빈 배열
 * - 남은 작품 이유: NO_PROVIDER, UNKNOWN, TIME
 */
import type { PriceSource } from '@/components/common/displayTypes'
import type {
  MediaType,
  PlanAssignment,
  PlanDraft,
  PlanMonth,
  PlanMonthProduct,
  PlanType,
  UnscheduledItem,
  UnscheduledReason,
  WishlistPriority,
} from '@/features/plan/mockTypes'
import type { UserSettings } from '@/features/onboarding/mockTypes'
import { MOCK_DATA_AS_OF } from '@/mocks/fixtures/titles'

// 플랜 응답 시나리오(주소의 ?mockScenario= 값과 같은 이름)
export type PlanScenario = 'different' | 'same'

// 모든 플랜 유형. 화면에 보이는 순서이기도 하다
export const PLAN_TYPES: PlanType[] = ['RECOMMENDED', 'SAVER', 'SIMPLE']

// 첫 달. 목업은 항상 2026년 10월부터 3개월(10·11·12월)이다
const START_MONTH = '2026-10'
const MONTHS = ['2026-10', '2026-11', '2026-12']

// 월 시청 시간이 한 번도 모자라지 않게 배정한 목업이라, 월 120분 미만 같은 경계는 다루지 않는다

// 계산에 쓸 작품 목록(찜 9건 가정). planItemId는 어느 플랜 유형에서도 같다
interface PlanItemSource {
  planItemId: number
  mediaType: MediaType
  tmdbId: number
  title: string
  seasonNumber: number | null
  priority: WishlistPriority
  totalMinutes: number // 다 보는 데 필요한 시간(분)
}

const ITEMS = {
  squidS3: {
    planItemId: 1,
    mediaType: 'tv',
    tmdbId: 93405,
    title: '오징어 게임',
    seasonNumber: 3,
    priority: 'MUST',
    totalMinutes: 270,
  },
  exampleDramaS1: {
    planItemId: 2,
    mediaType: 'tv',
    tmdbId: 900001,
    title: '예시 드라마 A',
    seasonNumber: 1,
    priority: 'MUST',
    totalMinutes: 1140,
  },
  parasite: {
    planItemId: 3,
    mediaType: 'movie',
    tmdbId: 496243,
    title: '기생충',
    seasonNumber: null,
    priority: 'MUST',
    totalMinutes: 132,
  },
  hunt: {
    planItemId: 4,
    mediaType: 'movie',
    tmdbId: 837799,
    title: '헌트',
    seasonNumber: null,
    priority: 'WANT',
    totalMinutes: 125,
  },
  exhuma: {
    planItemId: 5,
    mediaType: 'movie',
    tmdbId: 838209,
    title: '파묘',
    seasonNumber: null,
    priority: 'WANT',
    totalMinutes: 134,
  },
  squidS2: {
    planItemId: 6,
    mediaType: 'tv',
    tmdbId: 93405,
    title: '오징어 게임',
    seasonNumber: 2,
    priority: 'WANT',
    totalMinutes: 425,
  },
  gloryS2: {
    planItemId: 7,
    mediaType: 'tv',
    tmdbId: 136283,
    title: '더 글로리',
    seasonNumber: 2,
    priority: 'MAYBE',
    totalMinutes: 450,
  },
  anna: {
    planItemId: 8,
    mediaType: 'tv',
    tmdbId: 118243,
    title: '안나',
    seasonNumber: 1,
    priority: 'MAYBE',
    totalMinutes: 480,
  },
  burning: {
    planItemId: 9,
    mediaType: 'movie',
    tmdbId: 491584,
    title: '버닝',
    seasonNumber: null,
    priority: 'MAYBE',
    totalMinutes: 148,
  },
} satisfies Record<string, PlanItemSource>

type ItemKey = keyof typeof ITEMS

// 한 달의 설계: 어느 서비스를 구독하고(없으면 구독 없음) 어떤 작품을 몇 분 보는지
interface MonthSpec {
  serviceId: number | null
  watches: [ItemKey, number][]
}

// 한 플랜의 설계(3개월 + 남은 작품과 이유)
interface PlanSpec {
  months: [MonthSpec, MonthSpec, MonthSpec]
  unscheduled: [ItemKey, UnscheduledReason][]
}

const NETFLIX = 1
const TVING = 4

// 찜한 작품을 볼 수 있는 서비스(목업 가정: 넷플릭스·티빙). 절감액 기준(allSubscribeCost)에 쓴다
const CANDIDATE_SERVICE_IDS = [NETFLIX, TVING]

// 시즌 순서 규칙(같은 시리즈는 앞 시즌을 먼저 다 본다)은 엔진 미정, 사용자 결정 대기.
// 아래 배정표는 목업 데이터 보정일 뿐이다. 오징어 게임 시즌 2가 시즌 3보다 앞선 달에 오도록 손으로 맞췄고,
// fixtures/plan.test.ts가 세 유형·same 시나리오 모두에서 이 순서가 지켜지는지 확인한다.
const RECOMMENDED_SPEC: PlanSpec = {
  months: [
    {
      serviceId: NETFLIX,
      watches: [
        ['squidS2', 425],
        ['exampleDramaS1', 630],
        ['parasite', 132],
      ],
    },
    // 긴 시즌 이어 보기: 1월 넷플릭스에서 앞부분(630분), 2월 티빙에서 이어서(510분)
    {
      serviceId: TVING,
      watches: [
        ['exampleDramaS1', 510],
        ['exhuma', 134],
      ],
    },
    {
      serviceId: NETFLIX,
      watches: [
        ['squidS3', 270],
        ['hunt', 125],
        ['gloryS2', 450],
      ],
    },
  ],
  unscheduled: [
    ['anna', 'NO_PROVIDER'],
    ['burning', 'UNKNOWN'],
  ],
}

// 절약형: 추천형 점수의 70%(올림) 이상 중 가장 싸게. 3개월째는 구독하지 않는다
const SAVER_SPEC: PlanSpec = {
  months: [
    {
      serviceId: NETFLIX,
      watches: [
        ['squidS2', 425],
        ['exampleDramaS1', 630],
        ['parasite', 132],
      ],
    },
    {
      serviceId: NETFLIX,
      watches: [
        ['exampleDramaS1', 510],
        ['squidS3', 270],
        ['hunt', 125],
        ['exhuma', 134],
      ],
    },
    { serviceId: null, watches: [] },
  ],
  unscheduled: [
    ['gloryS2', 'TIME'],
    ['anna', 'NO_PROVIDER'],
    ['burning', 'UNKNOWN'],
  ],
}

// 간편형: 새로 가입하는 횟수 최소. 한 서비스(넷플릭스)를 3개월 내내 유지한다
const SIMPLE_SPEC: PlanSpec = {
  months: [
    {
      serviceId: NETFLIX,
      watches: [
        ['squidS2', 425],
        ['exampleDramaS1', 630],
        ['parasite', 132],
      ],
    },
    {
      serviceId: NETFLIX,
      watches: [
        ['exampleDramaS1', 510],
        ['squidS3', 270],
        ['hunt', 125],
        ['exhuma', 134],
      ],
    },
    { serviceId: NETFLIX, watches: [['gloryS2', 450]] },
  ],
  unscheduled: [
    ['anna', 'NO_PROVIDER'],
    ['burning', 'UNKNOWN'],
  ],
}

const SPECS_BY_TYPE: Record<PlanType, PlanSpec> = {
  RECOMMENDED: RECOMMENDED_SPEC,
  SAVER: SAVER_SPEC,
  SIMPLE: SIMPLE_SPEC,
}

// 점수에 더하는 우선순위별 값. 꼭(MUST)은 점수가 아니라 mustCompleted로만 센다(PRD 5.4)
const SCORE_BY_PRIORITY: Record<WishlistPriority, number> = { MUST: 0, WANT: 2, MAYBE: 1 }

// 서비스 한 곳의 상품·금액 정보. 요금 화면에서 사용자가 고친 금액을 반영하려고 핸들러가 함수로 넘겨 준다
export interface PlanPriceInfo {
  productId: number
  productName: string
  price: number // 적용 금액(사용자 금액이 있으면 그 값)
  adminPrice: number // 기본 요금
  source: 'ADMIN' | 'USER_OVERRIDE'
}

// 플랜을 만들 때 필요한 바깥 정보
export interface PlanBuildContext {
  settings: UserSettings // 계산 당시의 내 설정(예산·시청 시간 스냅샷, 이용 상태 → 가격 출처)
  calculationSeq: number // 몇 번째 계산인지. 계산할 때마다 플랜 ID가 새로 발급되는 것을 흉내 낸다
  getPriceInfo: (ottServiceId: number) => PlanPriceInfo
}

// 서비스의 이용 상태를 찾는다. 설정에 없으면 미구독으로 본다
function getSubscriptionStatus(settings: UserSettings, ottServiceId: number) {
  return (
    settings.subscriptions.find((s) => s.ottServiceId === ottServiceId)?.status ?? 'NOT_SUBSCRIBED'
  )
}

// 그 달에 구독할 상품 하나를 만든다. 가격 출처는 이용 상태가 우선한다:
// 무료 이용 서비스는 FREE(어느 달이든 0원), 구독 중인 서비스는 이번 달만 ALREADY_PAID(이미 결제해서 비용 0원), 그 밖에는 요금표 출처
function buildProduct(
  monthIndex: number,
  ottServiceId: number,
  context: PlanBuildContext,
): PlanMonthProduct {
  const info = context.getPriceInfo(ottServiceId)
  const status = getSubscriptionStatus(context.settings, ottServiceId)
  let priceSource: PriceSource = info.source
  if (status === 'FREE') priceSource = 'FREE'
  else if (status === 'SUBSCRIBED' && monthIndex === 0) priceSource = 'ALREADY_PAID'
  return {
    productId: info.productId,
    productName: info.productName,
    ottServiceIds: [ottServiceId],
    appliedPrice: info.price,
    priceSource,
  }
}

// 이번 달 비용에 넣는 금액인지. 이미 결제했거나 무료인 상품은 비용에 넣지 않는다
function countsTowardCost(source: PriceSource): boolean {
  return source === 'ADMIN' || source === 'USER_OVERRIDE'
}

// 설계(PlanSpec) 하나로 플랜 초안 하나를 만든다
function buildDraft(
  spec: PlanSpec,
  planId: number,
  planType: PlanType,
  context: PlanBuildContext,
): PlanDraft {
  // 작품별로 몇 달에 걸쳐 나눠 보는지 센다(조각 번호·전체 조각 수에 쓴다)
  const segmentCounts = new Map<ItemKey, number>()
  spec.months.forEach((month) =>
    month.watches.forEach(([key]) => segmentCounts.set(key, (segmentCounts.get(key) ?? 0) + 1)),
  )
  const segmentSeen = new Map<ItemKey, number>()
  const watchedMinutes = new Map<ItemKey, number>()

  const months: PlanMonth[] = spec.months.map((monthSpec, index) => {
    const monthIndex = index as 0 | 1 | 2
    const products: PlanMonthProduct[] =
      monthSpec.serviceId === null ? [] : [buildProduct(monthIndex, monthSpec.serviceId, context)]

    const assignments: PlanAssignment[] = monthSpec.watches.map(([key, minutes]) => {
      const item = ITEMS[key]
      const segmentIndex = (segmentSeen.get(key) ?? 0) + 1
      segmentSeen.set(key, segmentIndex)
      watchedMinutes.set(key, (watchedMinutes.get(key) ?? 0) + minutes)
      return {
        planItemId: item.planItemId,
        mediaType: item.mediaType,
        tmdbId: item.tmdbId,
        title: item.title,
        seasonNumber: item.seasonNumber,
        priority: item.priority,
        // 작품을 보는 서비스는 그 달 구독 서비스다(구독이 없는 달에는 배정이 없다)
        ottServiceId: monthSpec.serviceId ?? 0,
        minutes,
        segmentIndex,
        segmentCount: segmentCounts.get(key) ?? 1,
        isContinuation: segmentIndex > 1,
      }
    })

    return {
      monthIndex,
      month: MONTHS[index],
      isConfirmed: index === 0,
      cost: products
        .filter((p) => countsTowardCost(p.priceSource))
        .reduce((sum, p) => sum + p.appliedPrice, 0),
      products,
      assignments,
    }
  })

  const unscheduled: UnscheduledItem[] = spec.unscheduled.map(([key, reason]) => {
    const item = ITEMS[key]
    return {
      planItemId: item.planItemId,
      mediaType: item.mediaType,
      tmdbId: item.tmdbId,
      title: item.title,
      seasonNumber: item.seasonNumber,
      priority: item.priority,
      reason,
    }
  })

  // 필요한 시간을 모두 배정받은 작품만 "다 볼 수 있음"으로 센다
  const completedKeys = [...watchedMinutes.entries()]
    .filter(([key, minutes]) => minutes >= ITEMS[key].totalMinutes)
    .map(([key]) => key)
  const mustKeys = (Object.keys(ITEMS) as ItemKey[]).filter((key) => ITEMS[key].priority === 'MUST')
  const score = completedKeys.reduce((sum, key) => sum + SCORE_BY_PRIORITY[ITEMS[key].priority], 0)

  const totalCost = months.reduce((sum, month) => sum + month.cost, 0)
  // 절감액 기준(추천안, 사용자 결정 대기 - Task 030에서 확정): 모든 플랜 유형에 같은 기준을 쓴다.
  // 기준 = 찜한(안 다 본) 작품을 볼 수 있는 서비스 전부(CANDIDATE_SERVICE_IDS)를 3개월 내내 적용 금액
  // (기본 요금, 사용자가 고친 금액이 있으면 그 금액)으로 구독했을 때의 비용이다.
  // 플랜이 실제로 고른 서비스로 기준을 잡으면 유형마다 기준이 달라져 절감액을 서로 비교할 수 없다.
  // 이 방식이면 같은 계산 안에서 세 유형의 allSubscribeCost가 같고, 절감액 차이는 총비용 차이뿐이다.
  const allSubscribeCost =
    CANDIDATE_SERVICE_IDS.reduce((sum, id) => sum + context.getPriceInfo(id).price, 0) *
    MONTHS.length

  return {
    planId,
    planType,
    startMonth: START_MONTH,
    algorithm: 'EXACT',
    monthlyBudget: context.settings.monthlyBudget ?? 0,
    monthlyWatchMinutes: context.settings.monthlyWatchMinutes ?? 0,
    mustTotal: mustKeys.length,
    mustCompleted: mustKeys.filter((key) => completedKeys.includes(key)).length,
    scheduledCount: completedKeys.length,
    totalCount: Object.keys(ITEMS).length,
    score,
    totalCost,
    allSubscribeCost,
    savedAmount: allSubscribeCost - totalCost,
    months,
    unscheduled,
    dataAsOf: MOCK_DATA_AS_OF,
  }
}

/**
 * 시나리오에 맞는 플랜 초안 목록을 만든다. 같은 입력이면 항상 같은 결과다.
 * 플랜 ID는 계산 순번(calculationSeq)과 유형 순서로 정해, 다시 계산하면 ID가 바뀐다(저장 409 재현에 쓴다).
 */
export function buildPlanDrafts(scenario: PlanScenario, context: PlanBuildContext): PlanDraft[] {
  const baseId = 1000 + context.calculationSeq * 10
  const idOf = (planType: PlanType) => baseId + PLAN_TYPES.indexOf(planType) + 1

  if (scenario === 'same') {
    // 선택이 같은 경우: 유형마다 초안이 있고 내용은 같다(서버는 합치지 않는다)
    return PLAN_TYPES.map((planType) =>
      buildDraft(RECOMMENDED_SPEC, idOf(planType), planType, context),
    )
  }
  return PLAN_TYPES.map((planType) =>
    buildDraft(SPECS_BY_TYPE[planType], idOf(planType), planType, context),
  )
}
