import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { customInstance, isApiError } from '@/api/http'
import type {
  CommonResponseUserSettings,
  SaveUserSettingsRequest,
} from '@/features/onboarding/mockTypes'
import type {
  CommonResponsePlanDraftList,
  CommonResponseSavedPlan,
} from '@/features/plan/mockTypes'
import type { CommonResponseWishlistList } from '@/features/wishlist/mockTypes'
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

const SETTINGS_REQUEST: SaveUserSettingsRequest = {
  subscriptions: [
    { ottServiceId: 1, status: 'SUBSCRIBED', planName: '스탠다드', billingDay: 5 },
    { ottServiceId: 2, status: 'NOT_SUBSCRIBED', planName: null, billingDay: null },
  ],
  monthlyBudget: 15000,
  monthlyWatchMinutes: 1559,
  watchPreset: 'CUSTOM',
  weeklyInput: { timesPerWeek: 3, hoursPerTime: 2 },
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

  it('안 a(기본)는 환산 입력값을 버리고, 안 b는 저장한다', async () => {
    const a = await request<CommonResponseUserSettings>({
      url: '/me/settings',
      method: 'PUT',
      data: SETTINGS_REQUEST,
    })
    expect(a.data.weeklyInput).toBeNull()
    expect(a.data.monthlyWatchMinutes).toBe(1559)

    const b = await request<CommonResponseUserSettings>({
      url: '/me/settings',
      method: 'PUT',
      data: SETTINGS_REQUEST,
      params: { mockVariantWatch: 'b' },
    })
    expect(b.data.weeklyInput).toEqual({ timesPerWeek: 3, hoursPerTime: 2 })
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

describe('찜 목록', () => {
  it('찜하면 목록에 보이고 다 봤음을 표시할 수 있다', async () => {
    await addWishlist()
    const before = await request<CommonResponseWishlistList>({
      url: '/me/wishlist-items',
      method: 'GET',
    })
    expect(before.data.items).toHaveLength(1)
    expect(before.data.items[0]).toMatchObject({ title: '기생충', watchedAt: null })

    await request({
      url: `/me/wishlist-items/${before.data.items[0].wishlistItemId}`,
      method: 'PATCH',
      data: { isWatched: true },
    })
    const after = await request<CommonResponseWishlistList>({
      url: '/me/wishlist-items',
      method: 'GET',
    })
    expect(after.data.items[0].watchedAt).not.toBeNull()
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
      url: '/me/plans/calculate',
      method: 'POST',
      params: scenario ? { mockScenario: scenario } : undefined,
    })
  }

  it('찜이 없으면 계산할 수 없다', async () => {
    const error = await request({ url: '/me/plans/calculate', method: 'POST' }).catch(
      (e: unknown) => e,
    )
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
      url: '/me/plans/calculate',
      method: 'POST',
      params: { mockScenario: 'same-split' },
    })
    expect(split.data.drafts).toHaveLength(3)
    expect(new Set(split.data.drafts.map((d) => d.totalCost)).size).toBe(1)
  })

  it('R11-19 안 b는 드라마를 시즌이 아니라 작품 단위로 묶어 보인다', async () => {
    for (const seasonNumber of [2, 3]) {
      await request({
        url: '/me/wishlist-items',
        method: 'POST',
        data: { mediaType: 'tv', tmdbId: 93405, seasonNumber, priority: 'WANT' },
      })
    }
    const bySeason = await request<CommonResponseWishlistList>({
      url: '/me/wishlist-items',
      method: 'GET',
    })
    expect(bySeason.data.items.map((item) => item.seasonNumber)).toEqual([3, 2])

    const byTitle = await request<CommonResponseWishlistList>({
      url: '/me/wishlist-items',
      method: 'GET',
      params: { mockVariant: 'b' },
    })
    expect(byTitle.data.items).toHaveLength(1)
    expect(byTitle.data.items[0].seasonNumber).toBeNull()
  })

  it('stale 시나리오는 첫 저장만 409이고 다시 계산한 뒤 저장하면 성공한다', async () => {
    const { data } = await calculate('stale')
    const save = (planId: number) =>
      request<CommonResponseSavedPlan>({
        url: '/me/plans/RECOMMENDED/save',
        method: 'POST',
        data: { planId },
        params: { mockScenario: 'stale' },
      })

    const error = await save(data.drafts[0].planId).catch((e: unknown) => e)
    expect(isApiError(error) && error.status === 409 && error.errorCode).toBe('PLAN_DRAFT_STALE')

    const recalculated = await request<CommonResponsePlanDraftList>({
      url: '/me/plans/calculate',
      method: 'POST',
      params: { mockScenario: 'stale' },
    })
    // 다시 계산하면 플랜 ID가 새로 발급된다
    expect(recalculated.data.drafts[0].planId).not.toBe(data.drafts[0].planId)

    const saved = await save(recalculated.data.drafts[0].planId)
    expect(saved.data).toMatchObject({ planType: 'RECOMMENDED', status: 'ACTIVE' })

    // 저장하면 나머지 초안은 삭제되어 조회 결과가 비어 있다
    const drafts = await request<CommonResponsePlanDraftList>({
      url: '/me/plans/drafts',
      method: 'GET',
    })
    expect(drafts.data.drafts).toEqual([])
  })
})

describe('요금', () => {
  it('사용자 금액을 고치면 적용 금액이 바뀌고 null로 되돌린다', async () => {
    const changed = await request<{ data: { appliedPrice: number; priceSource: string } }>({
      url: '/me/pricing/1',
      method: 'PATCH',
      data: { monthlyPrice: 11000 },
    })
    expect(changed.data).toMatchObject({ appliedPrice: 11000, priceSource: 'USER_OVERRIDE' })

    const reverted = await request<{ data: { appliedPrice: number; priceSource: string } }>({
      url: '/me/pricing/1',
      method: 'PATCH',
      data: { monthlyPrice: null },
    })
    expect(reverted.data).toMatchObject({ appliedPrice: 13500, priceSource: 'ADMIN' })
  })

  it('음수 금액은 400이다', async () => {
    const error = await request({
      url: '/me/pricing/1',
      method: 'PATCH',
      data: { monthlyPrice: -1 },
    }).catch((e: unknown) => e)
    expect(isApiError(error) && error.status).toBe(400)
  })
})
