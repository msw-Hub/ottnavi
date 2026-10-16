import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { AXIOS_INSTANCE, customInstance, isApiError } from '@/api/http'
import type {
  CommonResponsePlanDraftList,
  CommonResponseSavedPlan,
  CommonResponseServicePrice,
  CommonResponseServicePriceList,
  CommonResponseUserSettings,
  UpdateMySettingsRequest,
} from '@/api/generated/model'
import type { CommonResponseWishlistItemList } from '@/api/generated/model'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { resetMockMemberState } from '@/mocks/memberHandlers'
import { resetMockSession, setMockRole } from '@/mocks/mockSession'

/*
 * 회원 화면 목업 핸들러 테스트(Task 028). 화면 없이 핸들러가 약속한 응답을 주는지 확인한다.
 * 이 핸들러는 Task 031까지 화면 확인용이라 핵심 흐름(설정 안 a/b, 찜 목록, 플랜 3가지 모양, 409 재현)만 본다.
 */

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
beforeEach(() => setMockRole('USER'))
afterEach(() => {
  server.resetHandlers()
  resetMockSession()
  resetMockWishlist()
  resetMockMemberState()
})
afterAll(() => server.close())

// 요청 하나를 보내고 ApiError(오류 응답)나 성공 본문을 돌려준다
async function request<T>(config: Parameters<typeof customInstance>[0]) {
  return customInstance<T>(config)
}

// 영화 "기생충"을 찜한다. 플랜 계산은 찜이 있어야 한다
async function addWishlist() {
  await request({
    url: '/me/wishlist-items',
    method: 'POST',
    data: { mediaType: 'movie', tmdbId: 496243, seasonNumber: null, priority: 'MUST' },
  })
}

const SETTINGS_REQUEST: UpdateMySettingsRequest = {
  subscriptions: [
    { ottServiceId: 1, status: 'SUBSCRIBED', planName: '스탠다드', billingDay: 5 },
    { ottServiceId: 2, status: 'NOT_SUBSCRIBED', planName: null, billingDay: null },
  ],
  monthlyBudget: 15000,
  monthlyWatchMinutes: 1559,
  watchPreset: 'CUSTOM',
}

describe('내 설정', () => {
  it('비로그인이면 401이다', async () => {
    setMockRole('GUEST')
    await expect(request({ url: '/me/settings', method: 'GET' })).rejects.toMatchObject({
      status: 401,
    })
  })

  it('온보딩 전에는 빈 설정을 돌려준다', async () => {
    const res = await request<CommonResponseUserSettings>({ url: '/me/settings', method: 'GET' })
    expect(res.data).toMatchObject({ isOnboardingDone: false, monthlyBudget: null })
  })

  it('저장하면 분 값이 그대로 저장되고 응답에 환산 입력값 필드가 없다', async () => {
    const saved = await request<CommonResponseUserSettings>({
      url: '/me/settings',
      method: 'PUT',
      data: SETTINGS_REQUEST,
    })
    expect(saved.data.monthlyWatchMinutes).toBe(1559)
    expect(saved.data).not.toHaveProperty('weeklyInput')
  })

  it('구독 중인데 결제일이 비어 있으면 1일로 저장하고 요금제 이름 앞뒤 공백을 뗀다', async () => {
    const saved = await request<CommonResponseUserSettings>({
      url: '/me/settings',
      method: 'PUT',
      data: {
        ...SETTINGS_REQUEST,
        subscriptions: [
          { ottServiceId: 1, status: 'SUBSCRIBED', planName: ' 스탠다드 ', billingDay: null },
          { ottServiceId: 2, status: 'UNKNOWN', planName: null, billingDay: null },
        ],
      },
    })
    expect(saved.data.subscriptions).toEqual([
      { ottServiceId: 1, status: 'SUBSCRIBED', planName: '스탠다드', billingDay: 1 },
      { ottServiceId: 2, status: 'UNKNOWN', planName: null, billingDay: null },
    ])
  })

  it('구독 중이 아닌 서비스에 요금제 이름이나 결제일을 보내면 400이다(서버 규칙)', async () => {
    const error = await request({
      url: '/me/settings',
      method: 'PUT',
      data: {
        ...SETTINGS_REQUEST,
        subscriptions: [{ ottServiceId: 2, status: 'UNKNOWN', planName: '남은 값', billingDay: 9 }],
      },
    }).catch((e: unknown) => e)
    expect(error).toMatchObject({ status: 400, errorCode: 'VALIDATION_FAILED' })
    expect(isApiError(error) && error.fieldErrors[0]?.field).toBe('subscriptions[0]')
  })

  it('없는 서비스 ID와 소수 금액은 400이다', async () => {
    const unknownService = await request({
      url: '/me/settings',
      method: 'PUT',
      data: {
        ...SETTINGS_REQUEST,
        subscriptions: [{ ottServiceId: 99, status: 'FREE', planName: null, billingDay: null }],
      },
    }).catch((e: unknown) => e)
    expect(unknownService).toMatchObject({ status: 400, errorCode: 'VALIDATION_FAILED' })

    const decimalBudget = await request({
      url: '/me/settings',
      method: 'PUT',
      data: { ...SETTINGS_REQUEST, monthlyBudget: 15000.5 },
    }).catch((e: unknown) => e)
    expect(decimalBudget).toMatchObject({ status: 400, errorCode: 'VALIDATION_FAILED' })
  })

  it('같은 서비스를 두 번 보내면 400이다', async () => {
    const error = await request({
      url: '/me/settings',
      method: 'PUT',
      data: {
        ...SETTINGS_REQUEST,
        subscriptions: [
          { ottServiceId: 1, status: 'NOT_SUBSCRIBED', planName: null, billingDay: null },
          { ottServiceId: 1, status: 'FREE', planName: null, billingDay: null },
        ],
      },
    }).catch((e: unknown) => e)
    expect(error).toMatchObject({ status: 400, errorCode: 'VALIDATION_FAILED' })
  })

  it('입력 규칙을 어기면 400 VALIDATION_FAILED다', async () => {
    const error = await request({
      url: '/me/settings',
      method: 'PUT',
      data: { ...SETTINGS_REQUEST, monthlyWatchMinutes: 60 },
    }).catch((e: unknown) => e)
    expect(isApiError(error) && error.status === 400 && error.errorCode).toBe('VALIDATION_FAILED')
  })
})

describe('찜 변경·삭제의 서버 규칙(Task 058)', () => {
  it('PATCH 본문이 비어 있으면 400이고, 경로 ID 형식이 틀리면 PATCH·DELETE 모두 400이다', async () => {
    const created = await request<{ data: { wishlistItemId: number } }>({
      url: '/me/wishlist-items',
      method: 'POST',
      data: { mediaType: 'movie', tmdbId: 496243, seasonNumber: null, priority: 'MUST' },
    })
    const id = created.data.wishlistItemId

    const emptyBody = await request({
      url: `/me/wishlist-items/${id}`,
      method: 'PATCH',
      data: {},
    }).catch((e: unknown) => e)
    expect(emptyBody).toMatchObject({ status: 400, errorCode: 'VALIDATION_FAILED' })

    const badId = await request({
      url: '/me/wishlist-items/abc',
      method: 'PATCH',
      data: { priority: 'WANT' },
    }).catch((e: unknown) => e)
    expect(badId).toMatchObject({ status: 400, errorCode: 'VALIDATION_FAILED' })

    const badDeleteId = await request({ url: '/me/wishlist-items/0', method: 'DELETE' }).catch(
      (e: unknown) => e,
    )
    expect(badDeleteId).toMatchObject({ status: 400, errorCode: 'VALIDATION_FAILED' })
  })

  it('없는 찜 항목의 변경은 404 WISHLIST_ITEM_NOT_FOUND다', async () => {
    const notFound = await request({
      url: '/me/wishlist-items/999',
      method: 'PATCH',
      data: { priority: 'WANT' },
    }).catch((e: unknown) => e)
    expect(notFound).toMatchObject({ status: 404, errorCode: 'WISHLIST_ITEM_NOT_FOUND' })
  })
})

describe('찜 목록', () => {
  it('찜하면 목록에 보이고 다 봤음을 표시할 수 있다', async () => {
    await addWishlist()
    const before = await request<CommonResponseWishlistItemList>({
      url: '/me/wishlist-items',
      method: 'GET',
    })
    expect(before.data.items).toHaveLength(1)
    expect(before.data.items[0]).toMatchObject({ title: '기생충', watchedAt: null })
    expect(before.data).toMatchObject({ activeItemCount: 1, maxActiveItemCount: 20 })
    expect(before.data.items[0]).toHaveProperty('posterPath')
    expect(before.data.items[0]).toHaveProperty('requiredMin')

    await request({
      url: `/me/wishlist-items/${before.data.items[0].wishlistItemId}`,
      method: 'PATCH',
      data: { isWatched: true },
    })
    const after = await request<CommonResponseWishlistItemList>({
      url: '/me/wishlist-items',
      method: 'GET',
    })
    expect(after.data.items[0].watchedAt).not.toBeNull()
    // "다 봤음"은 상한에 세지 않는다
    expect(after.data.activeItemCount).toBe(0)
  })
})

describe('플랜 계산과 저장', () => {
  async function calculate(scenario?: string) {
    await request({
      url: '/me/settings',
      method: 'PUT',
      data: SETTINGS_REQUEST,
    })
    await addWishlist()
    return request<CommonResponsePlanDraftList>({
      url: '/me/plan-drafts',
      method: 'POST',
      headers: scenario ? { 'X-Mock-Scenario': scenario } : undefined,
    })
  }

  it('찜이 없으면 계산할 수 없다', async () => {
    const error = await request({ url: '/me/plan-drafts', method: 'POST' }).catch((e: unknown) => e)
    expect(isApiError(error) && error.status).toBe(400)
  })

  it('기본은 세 유형이 서로 다른 초안 3개이고 긴 시즌이 달별로 나뉜다', async () => {
    const { data } = await calculate()
    expect(data.drafts.map((d) => d.planType)).toEqual(['RECOMMENDED', 'SAVER', 'SIMPLE'])

    const recommended = data.drafts[0]
    const segments = recommended.months
      .flatMap((month) => month.assignments)
      .filter((assignment) => assignment.title === '예시 드라마 A')
    expect(segments.map((s) => [s.segmentIndex, s.segmentCount, s.isContinuation])).toEqual([
      [1, 2, false],
      [2, 2, true],
    ])
    // 절약형은 마지막 달에 구독하지 않는다
    expect(data.drafts[1].months[2].products).toEqual([])
    // 절약형은 추천형보다 싸고, 점수는 추천형의 70%(올림) 이상이다
    expect(data.drafts[1].totalCost).toBeLessThan(recommended.totalCost)
    expect(data.drafts[1].score).toBeGreaterThanOrEqual(Math.ceil(recommended.score * 0.7))
  })

  it('same과 same-split은 모두 내용이 같은 초안 3개를 돌려준다(합치기는 프론트 몫)', async () => {
    const same = await calculate('same')
    expect(same.data.drafts.map((d) => d.planType)).toEqual(['RECOMMENDED', 'SAVER', 'SIMPLE'])
    expect(new Set(same.data.drafts.map((d) => d.totalCost)).size).toBe(1)

    const split = await request<CommonResponsePlanDraftList>({
      url: '/me/plan-drafts',
      method: 'POST',
      headers: { 'X-Mock-Scenario': 'same-split' },
    })
    expect(split.data.drafts).toHaveLength(3)
    expect(new Set(split.data.drafts.map((d) => d.totalCost)).size).toBe(1)
  })

  it('드라마 찜은 시즌 단위로 한 줄씩 보이고, 필요 시간·시즌 이름은 그 시즌 값이다', async () => {
    for (const seasonNumber of [2, 3]) {
      await request({
        url: '/me/wishlist-items',
        method: 'POST',
        data: { mediaType: 'tv', tmdbId: 93405, seasonNumber, priority: 'WANT' },
      })
    }
    const list = await request<CommonResponseWishlistItemList>({
      url: '/me/wishlist-items',
      method: 'GET',
    })
    expect(list.data.items.map((item) => item.seasonNumber)).toEqual([3, 2])
    expect(list.data.items[0]).toMatchObject({ seasonName: '시즌 3' })
    expect(list.data.items[0].requiredMin).toBeGreaterThan(0)
  })

  it('stale 시나리오는 첫 저장만 409이고 다시 계산한 뒤 저장하면 성공한다', async () => {
    const { data } = await calculate('stale')
    const save = (planId: number) =>
      request<CommonResponseSavedPlan>({
        url: '/me/plans',
        method: 'POST',
        data: { planId },
        headers: { 'X-Mock-Scenario': 'stale' },
      })

    const error = await save(data.drafts[0].planId).catch((e: unknown) => e)
    expect(isApiError(error) && error.status === 409 && error.errorCode).toBe('PLAN_DRAFT_STALE')

    const recalculated = await request<CommonResponsePlanDraftList>({
      url: '/me/plan-drafts',
      method: 'POST',
      headers: { 'X-Mock-Scenario': 'stale' },
    })
    // 다시 계산하면 플랜 ID가 새로 발급된다
    expect(recalculated.data.drafts[0].planId).not.toBe(data.drafts[0].planId)

    const saved = await save(recalculated.data.drafts[0].planId)
    expect(saved.data).toMatchObject({ planType: 'RECOMMENDED', status: 'ACTIVE' })

    // 저장하면 나머지 초안은 삭제되어 조회 결과가 비어 있다
    const drafts = await request<CommonResponsePlanDraftList>({
      url: '/me/plan-drafts',
      method: 'GET',
    })
    expect(drafts.data.drafts).toEqual([])
  })

  it('계산은 201로 초안 3개를 주고 배정 시간 필드는 watchMin이다', async () => {
    await request({ url: '/me/settings', method: 'PUT', data: SETTINGS_REQUEST })
    await addWishlist()
    const res = await AXIOS_INSTANCE.post('/me/plan-drafts')
    expect(res.status).toBe(201)
    const assignment = res.data.data.drafts[0].months[0].assignments[0]
    expect(assignment).toHaveProperty('watchMin')
    expect(assignment).not.toHaveProperty('minutes')
  })

  it('없는 planId로 저장하면 404 PLAN_DRAFT_NOT_FOUND이고, 저장하면 초안 3개가 모두 사라진다', async () => {
    const { data } = await calculate()
    const notFound = await request({
      url: '/me/plans',
      method: 'POST',
      data: { planId: 999999 },
    }).catch((e: unknown) => e)
    expect(notFound).toMatchObject({ status: 404, errorCode: 'PLAN_DRAFT_NOT_FOUND' })

    // 절약형 초안을 저장하면 응답의 유형은 그 초안의 유형이고, 나머지 초안도 함께 삭제된다
    const saver = data.drafts.find((d) => d.planType === 'SAVER')!
    const saved = await request<CommonResponseSavedPlan>({
      url: '/me/plans',
      method: 'POST',
      data: { planId: saver.planId },
    })
    expect(saved.data).toMatchObject({ planId: saver.planId, planType: 'SAVER', status: 'ACTIVE' })
    const drafts = await request<CommonResponsePlanDraftList>({
      url: '/me/plan-drafts',
      method: 'GET',
    })
    expect(drafts.data.drafts).toEqual([])
  })

  it('계산 전에 저장하면 404 PLAN_DRAFT_NOT_FOUND다', async () => {
    const error = await request({
      url: '/me/plans',
      method: 'POST',
      data: { planId: 1 },
    }).catch((e: unknown) => e)
    expect(error).toMatchObject({ status: 404, errorCode: 'PLAN_DRAFT_NOT_FOUND' })
  })
})

describe('요금', () => {
  // 넷플릭스 스탠다드의 상품 ID(101). 요금 API의 키는 서비스 ID가 아니라 상품 ID다
  const NETFLIX_PRODUCT_ID = 101
  // 판매 종료로 둔 왓챠 베이직의 상품 ID(106)
  const WATCHA_PRODUCT_ID = 106

  it('요금 목록은 상품 단위 줄이고 요금 기준 일시(lastVerifiedAt)를 date-time으로 준다', async () => {
    const res = await request<CommonResponseServicePriceList>({
      url: '/me/service-prices',
      method: 'GET',
    })
    // 판매 종료인 왓챠(106)는 빠지고 6개만 나온다
    expect(res.data.services).toHaveLength(6)
    expect(res.data.services.map((p) => p.productId)).not.toContain(WATCHA_PRODUCT_ID)
    expect(res.data.services[0]).toMatchObject({
      ottServiceId: 1,
      productId: NETFLIX_PRODUCT_ID,
      adminPrice: 13500,
      userPrice: null,
      priceSource: 'ADMIN',
    })
    expect(res.data.services[0].lastVerifiedAt).toMatch(/^\d{4}-\d{2}-\d{2}T/)
    expect(res.data.services[0].effectiveFrom).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('PUT으로 고치면 적용 금액이 바뀌고 DELETE(204)로 기본 요금으로 되돌린다', async () => {
    const changed = await request<CommonResponseServicePrice>({
      url: `/me/service-prices/${NETFLIX_PRODUCT_ID}`,
      method: 'PUT',
      data: { monthlyPrice: 11000 },
    })
    expect(changed.data).toMatchObject({ appliedPrice: 11000, priceSource: 'USER_OVERRIDE' })

    await request({ url: `/me/service-prices/${NETFLIX_PRODUCT_ID}`, method: 'DELETE' })
    const list = await request<CommonResponseServicePriceList>({
      url: '/me/service-prices',
      method: 'GET',
    })
    expect(list.data.services[0]).toMatchObject({ appliedPrice: 13500, priceSource: 'ADMIN' })
  })

  it('범위를 벗어난 금액(음수·100만 원 초과·소수)은 400이다', async () => {
    for (const monthlyPrice of [-1, 1_000_001, 100.5]) {
      const error = await request({
        url: `/me/service-prices/${NETFLIX_PRODUCT_ID}`,
        method: 'PUT',
        data: { monthlyPrice },
      }).catch((e: unknown) => e)
      expect(isApiError(error) && error.status).toBe(400)
    }
  })

  it('금액 오류(400)는 상품 존재 확인(404)보다 먼저이고 fieldErrors로 알린다(서버 실측 순서, Task 060)', async () => {
    const decimal = await request({
      url: '/me/service-prices/999', // 없는 상품이어도 본문 오류가 먼저다
      method: 'PUT',
      data: { monthlyPrice: 9900.5 },
    }).catch((e: unknown) => e)
    expect(decimal).toMatchObject({ status: 400, errorCode: 'VALIDATION_FAILED' })
    expect(isApiError(decimal) && decimal.fieldErrors).toEqual([
      { field: 'monthlyPrice', message: '형식이 올바르지 않습니다.' },
    ])

    const tooHigh = await request({
      url: `/me/service-prices/${NETFLIX_PRODUCT_ID}`,
      method: 'PUT',
      data: { monthlyPrice: 1_000_001 },
    }).catch((e: unknown) => e)
    expect(isApiError(tooHigh) && tooHigh.fieldErrors).toEqual([
      { field: 'monthlyPrice', message: '1,000,000 이하여야 합니다.' },
    ])
  })

  it('없는 상품은 PUT·DELETE 모두 404 SERVICE_PRICE_NOT_FOUND다', async () => {
    const putError = await request({
      url: '/me/service-prices/999',
      method: 'PUT',
      data: { monthlyPrice: 1000 },
    }).catch((e: unknown) => e)
    expect(putError).toMatchObject({ status: 404, errorCode: 'SERVICE_PRICE_NOT_FOUND' })

    const deleteError = await request({ url: '/me/service-prices/999', method: 'DELETE' }).catch(
      (e: unknown) => e,
    )
    expect(deleteError).toMatchObject({ status: 404, errorCode: 'SERVICE_PRICE_NOT_FOUND' })
  })

  it('판매 종료 상품도 PUT·DELETE가 404 SERVICE_PRICE_NOT_FOUND다', async () => {
    const putError = await request({
      url: `/me/service-prices/${WATCHA_PRODUCT_ID}`,
      method: 'PUT',
      data: { monthlyPrice: 1000 },
    }).catch((e: unknown) => e)
    expect(putError).toMatchObject({ status: 404, errorCode: 'SERVICE_PRICE_NOT_FOUND' })

    const deleteError = await request({
      url: `/me/service-prices/${WATCHA_PRODUCT_ID}`,
      method: 'DELETE',
    }).catch((e: unknown) => e)
    expect(deleteError).toMatchObject({ status: 404, errorCode: 'SERVICE_PRICE_NOT_FOUND' })
  })

  it('비로그인이면 401이다', async () => {
    setMockRole('GUEST')
    await expect(request({ url: '/me/service-prices', method: 'GET' })).rejects.toMatchObject({
      status: 401,
    })
  })
})
