import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { AXIOS_INSTANCE, customInstance, isApiError } from '@/api/http'
import type {
  CommonResponseAdminProduct,
  CommonResponseCollectExecutionChangePage,
  CommonResponseCollectExecution,
  CommonResponseCollectExecutionPage,
  CommonResponseCollectExecutionAccepted,
  CommonResponseAdminProvider,
  CommonResponseAdminProviderList,
  CommonResponseAdminProductList,
} from '@/api/generated/model'
import { addDays, getTodayDateString } from '@/lib/dateUtils'
import { formatIsoDate } from '@/lib/format'
import { resetMockAdminState } from '@/mocks/adminHandlers'
import { handlers } from '@/mocks/handlers'

/*
 * 관리자 목업 핸들러 테스트(Task 029, 단계 1). 화면 없이 핸들러가 규칙을 서버처럼 강제하는지, 새로고침 뒤에도 변경이 남는지 본다.
 */

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => {
  server.resetHandlers()
  resetMockAdminState()
})
afterAll(() => server.close())

const TODAY = getTodayDateString()
const NETFLIX_ID = 101 // 줄 3개(지난 / 적용 중 / 예정). 마지막 줄이 예정이다
const DISNEY_ID = 102 // 줄 1개

async function listProducts() {
  const res = await customInstance<CommonResponseAdminProductList>({
    url: '/admin/products',
    method: 'GET',
  })
  return res.data.products
}

// 요청이 규칙 위반으로 실패하면 그 errorCode를 돌려준다. 성공하면 null이다
async function failureCodeOf(config: Parameters<typeof customInstance>[0]) {
  try {
    await customInstance(config)
    return null
  } catch (error) {
    return isApiError(error) ? error.errorCode : 'NOT_API_ERROR'
  }
}

describe('월 구독요금 규칙 강제', () => {
  it('새 줄 시작일이 마지막 줄과 같으면 PRICE_START_DATE_DUPLICATE, 더 이르면 PRICE_START_DATE_NOT_AFTER_LAST', async () => {
    const netflix = (await listProducts()).find((p) => p.productId === NETFLIX_ID)!
    const lastStart = netflix.prices[netflix.prices.length - 1].effectiveFrom
    const url = `/admin/products/${NETFLIX_ID}/prices`

    expect(
      await failureCodeOf({
        url,
        method: 'POST',
        data: { monthlyPrice: 15000, effectiveFrom: lastStart },
      }),
    ).toBe('PRICE_START_DATE_DUPLICATE')
    expect(
      await failureCodeOf({
        url,
        method: 'POST',
        data: { monthlyPrice: 15000, effectiveFrom: addDays(lastStart, -1) },
      }),
    ).toBe('PRICE_START_DATE_NOT_AFTER_LAST')
  })

  it('가장 최근 줄이 아니면 수정·삭제할 수 없고, 줄이 하나뿐이면 삭제할 수 없다', async () => {
    const netflix = (await listProducts()).find((p) => p.productId === NETFLIX_ID)!
    const oldestId = netflix.prices[0].priceId
    expect(
      await failureCodeOf({
        url: `/admin/products/${NETFLIX_ID}/prices/${oldestId}`,
        method: 'DELETE',
      }),
    ).toBe('PRICE_NOT_LATEST')

    const disney = (await listProducts()).find((p) => p.productId === DISNEY_ID)!
    expect(
      await failureCodeOf({
        url: `/admin/products/${DISNEY_ID}/prices/${disney.prices[0].priceId}`,
        method: 'DELETE',
      }),
    ).toBe('PRICE_LAST_ROW_CANNOT_DELETE')
  })

  it('가장 최근 줄을 삭제하면 204(본문 없음)이고 목록에서 줄이 하나 줄어든다', async () => {
    const netflix = (await listProducts()).find((p) => p.productId === NETFLIX_ID)!
    const last = netflix.prices.at(-1)!
    const res = await AXIOS_INSTANCE.delete(`/admin/products/${NETFLIX_ID}/prices/${last.priceId}`)
    expect(res.status).toBe(204)
    const after = (await listProducts()).find((p) => p.productId === NETFLIX_ID)!
    expect(after.prices).toHaveLength(netflix.prices.length - 1)
  })

  it('등록할 때 첫 월 구독요금의 시작일이 오늘보다 뒤면 PRICE_FIRST_START_DATE_IN_FUTURE', async () => {
    expect(
      await failureCodeOf({
        url: '/admin/products',
        method: 'POST',
        data: {
          name: '테스트',
          productType: 'SINGLE',
          ottServiceIds: [1],
          isLowCost: false,
          monthlyPrice: 5000,
          effectiveFrom: addDays(TODAY, 1),
        },
      }),
    ).toBe('PRICE_FIRST_START_DATE_IN_FUTURE')
  })

  it('[요금 확인함]은 지금 적용 중인 줄만 가능하다(지난·예정 줄은 409 PRICE_NOT_CURRENT)', async () => {
    const netflix = (await listProducts()).find((p) => p.productId === NETFLIX_ID)!
    const [past, current, upcoming] = netflix.prices
    const verify = (priceId: number) =>
      failureCodeOf({
        url: `/admin/products/${NETFLIX_ID}/prices/${priceId}/verify`,
        method: 'POST',
      })
    expect(await verify(past.priceId)).toBe('PRICE_NOT_CURRENT')
    expect(await verify(upcoming.priceId)).toBe('PRICE_NOT_CURRENT')
    expect(await verify(current.priceId)).toBeNull()
  })

  it('[요금 확인함]은 마지막 확인 일시만 지금으로 바꾼다(한국 날짜로는 오늘)', async () => {
    const apple = (await listProducts()).find((p) => p.productId === 103)! // 확인일이 90일을 넘은 상품
    const row = apple.prices[0]
    const res = await customInstance<CommonResponseAdminProduct>({
      url: `/admin/products/103/prices/${row.priceId}/verify`,
      method: 'POST',
    })
    expect(res.data.prices[0]).toEqual({ ...row, lastVerifiedAt: expect.any(String) })
    // 계약의 lastVerifiedAt은 일시(date-time)다. 화면과 같이 한국 날짜로 바꾸면 오늘이다
    expect(res.data.prices[0].lastVerifiedAt).toMatch(/^\d{4}-\d{2}-\d{2}T/)
    expect(formatIsoDate(res.data.prices[0].lastVerifiedAt)).toBe(TODAY)
  })
})

describe('시나리오', () => {
  it('?mockScenario=empty는 빈 목록, error는 500을 돌려준다', async () => {
    const empty = await customInstance<CommonResponseAdminProductList>({
      url: '/admin/products',
      method: 'GET',
      headers: { 'X-Mock-Scenario': 'empty' },
    })
    expect(empty.data.products).toEqual([])
    expect(
      await failureCodeOf({
        url: '/admin/products',
        method: 'GET',
        headers: { 'X-Mock-Scenario': 'error' },
      }),
    ).toBe('INTERNAL_ERROR')
  })
})

describe('새로고침 뒤 유지', () => {
  it('등록·추가한 변경이 sessionStorage에 남아 모듈을 다시 읽어도 보이고, reset이 지운다', async () => {
    const created = await customInstance<CommonResponseAdminProduct>({
      url: '/admin/products',
      method: 'POST',
      data: {
        name: '유지 확인용',
        productType: 'SINGLE',
        ottServiceIds: [2],
        isLowCost: false,
        monthlyPrice: 5000,
        effectiveFrom: addDays(TODAY, -3),
      },
    })
    await customInstance({
      url: `/admin/products/${DISNEY_ID}/prices`,
      method: 'POST',
      data: { monthlyPrice: 11000, effectiveFrom: addDays(TODAY, 10) },
    })

    // 모듈 변수를 버리고 다시 읽는다(브라우저 새로고침). sessionStorage는 남는다
    vi.resetModules()
    const fresh = await import('@/mocks/adminHandlers')
    server.use(...fresh.adminHandlers)

    const products = await listProducts()
    expect(
      products.some((p) => p.productId === created.data.productId && p.name === '유지 확인용'),
    ).toBe(true)
    expect(products.find((p) => p.productId === DISNEY_ID)!.prices).toHaveLength(2)
    // 건드리지 않은 상품은 기본 목록 그대로다
    expect(products.find((p) => p.productId === NETFLIX_ID)!.prices).toHaveLength(3)

    fresh.resetMockAdminState()
    expect(window.sessionStorage.getItem('ottnavi:mock-admin-products:v2')).toBeNull()
  })

  it('저장된 값이 깨져 있으면 기본 목록으로 시작한다', async () => {
    window.sessionStorage.setItem('ottnavi:mock-admin-products:v2', '{깨진 값')
    vi.resetModules()
    const fresh = await import('@/mocks/adminHandlers')
    server.use(...fresh.adminHandlers)
    // 기본 목록은 단품·저가형·번들을 합쳐 12개다(Task 076 목업, 추가 상품 108~112)
    expect(await listProducts()).toHaveLength(12)
  })
})

describe('제공처 매핑', () => {
  async function listProviders() {
    const res = await customInstance<CommonResponseAdminProviderList>({
      url: '/admin/providers',
      method: 'GET',
    })
    return res.data.providers
  }

  it('서비스 선택은 MAPPED, 관련 없음은 IGNORED로 저장하고, 연결하지 않으면 등급은 STANDARD로 고정된다', async () => {
    const mapped = await customInstance<CommonResponseAdminProvider>({
      url: '/admin/providers/283',
      method: 'PATCH',
      data: { mappingState: 'MAPPED', ottServiceId: 2, tier: 'AD' },
    })
    expect(mapped.data).toMatchObject({ mappingState: 'MAPPED', ottServiceId: 2, tier: 'AD' })

    const ignored = await customInstance<CommonResponseAdminProvider>({
      url: '/admin/providers/283',
      method: 'PATCH',
      data: { mappingState: 'IGNORED', ottServiceId: null, tier: 'AD' },
    })
    expect(ignored.data).toMatchObject({
      mappingState: 'IGNORED',
      ottServiceId: null,
      tier: 'STANDARD',
    })
  })

  it('없는 제공처는 404, 모르는 서비스는 400이다', async () => {
    expect(
      await failureCodeOf({
        url: '/admin/providers/99999',
        method: 'PATCH',
        data: { mappingState: 'IGNORED', ottServiceId: null, tier: 'STANDARD' },
      }),
    ).toBe('RESOURCE_NOT_FOUND')
    expect(
      await failureCodeOf({
        url: '/admin/providers/283',
        method: 'PATCH',
        data: { mappingState: 'MAPPED', ottServiceId: 99, tier: 'STANDARD' },
      }),
    ).toBe('VALIDATION_FAILED')
  })

  it('?mockScenario=save-error는 저장만 500이고 목록은 정상이다', async () => {
    expect(
      await failureCodeOf({
        url: '/admin/providers/283',
        method: 'PATCH',
        headers: { 'X-Mock-Scenario': 'save-error' },
        data: { mappingState: 'IGNORED', ottServiceId: null, tier: 'STANDARD' },
      }),
    ).toBe('INTERNAL_ERROR')
    expect(await listProviders()).toHaveLength(13)
  })

  it('저장한 매핑이 새로고침 뒤에도 남고 reset이 지운다', async () => {
    await customInstance({
      url: '/admin/providers/283',
      method: 'PATCH',
      data: { mappingState: 'MAPPED', ottServiceId: 5, tier: 'STANDARD' },
    })
    vi.resetModules()
    const fresh = await import('@/mocks/adminHandlers')
    server.use(...fresh.adminHandlers)

    expect((await listProviders()).find((p) => p.providerId === 283)).toMatchObject({
      mappingState: 'MAPPED',
      ottServiceId: 5,
    })
    fresh.resetMockAdminState()
    expect(window.sessionStorage.getItem('ottnavi:mock-admin-providers')).toBeNull()
  })
})

describe('수집 실행·이력', () => {
  async function listExecutions() {
    const res = await customInstance<CommonResponseCollectExecutionPage>({
      url: '/admin/collect/executions',
      method: 'GET',
    })
    return res.data.content
  }

  async function run(targetDate: string) {
    return customInstance<CommonResponseCollectExecutionAccepted>({
      url: '/admin/collect/executions',
      method: 'POST',
      data: { targetDate, tier: 'ALL' },
    })
  }

  it('이력은 페이지 형태(content/page/size/totalElements/totalPages)다', async () => {
    const res = await customInstance<CommonResponseCollectExecutionPage>({
      url: '/admin/collect/executions',
      method: 'GET',
      params: { page: 0, size: 2 },
    })
    expect(res.data).toMatchObject({ page: 0, size: 2, totalElements: 5, totalPages: 3 })
    expect(res.data.content.map((e) => e.executionId)).toEqual([5, 4])

    const bad = await customInstance({
      url: '/admin/collect/executions',
      method: 'GET',
      params: { size: 101 },
    }).catch((e: unknown) => e)
    expect(bad).toMatchObject({ status: 400, errorCode: 'VALIDATION_FAILED' })
  })

  it('targetDate를 비우고 실행하면 오늘로 본다(오늘 성공한 5번이 있어 409다)', async () => {
    const error = await customInstance({
      url: '/admin/collect/executions',
      method: 'POST',
      data: { tier: 'ALL' },
    }).catch((e: unknown) => e)
    expect(error).toMatchObject({ status: 409, errorCode: 'BATCH_ALREADY_COMPLETED' })
  })

  it('기본 이력은 5건이고 최신 순이며, 버튼 값은 중단됨(3번)만 복구·최신 실패(2번)만 다시 실행이다', async () => {
    const list = await listExecutions()
    expect(list.map((e) => e.executionId)).toEqual([5, 4, 3, 2, 1])
    expect(list.map((e) => [e.executionId, e.canRecover, e.canRestart])).toEqual([
      [5, false, false],
      [4, false, false],
      [3, true, false],
      [2, false, true],
      [1, false, false],
    ])
  })

  it('다른 실행이 돌고 있으면 실패 행의 canRestart가 거짓이 되고, 끝나면 다시 참이다(응답마다 다시 계산)', async () => {
    await run(addDays(TODAY, -6)) // 6번 실행 중
    const first = await listExecutions() // 조회 1번째: 6번은 아직 실행 중
    expect(first.find((e) => e.executionId === 6)!.status).toBe('RUNNING')
    expect(first.find((e) => e.executionId === 2)!.canRestart).toBe(false)
    const second = await listExecutions() // 조회 2번째: 6번 성공
    expect(second.find((e) => e.executionId === 6)!.status).toBe('COMPLETED')
    expect(second.find((e) => e.executionId === 2)!.canRestart).toBe(true)
  })

  it('실행을 접수하면 이력 두 번째 조회에서 성공으로 바뀐다(조회 횟수로 정하는 결정적 규칙)', async () => {
    const accepted = await run(addDays(TODAY, -6))
    const id = accepted.data.executionId
    const first = (await listExecutions()).find((e) => e.executionId === id)!
    expect(first.status).toBe('RUNNING')
    const second = (await listExecutions()).find((e) => e.executionId === id)!
    expect(second.status).toBe('COMPLETED')
    expect(second.changedCount).toBe(2)

    const changes = await customInstance<CommonResponseCollectExecutionChangePage>({
      url: `/admin/collect/executions/${id}/changes`,
      method: 'GET',
    })
    expect(changes.data.content).toHaveLength(2)
    expect(changes.data.totalElements).toBe(2)
  })

  it('같은 날짜·범위의 최신 상태를 먼저 보고(성공 / 중단됨), 그다음 다른 실행 중을 본다(전체 동시 실행 금지)', async () => {
    const post = (date: string) =>
      failureCodeOf({
        url: '/admin/collect/executions',
        method: 'POST',
        data: { targetDate: date, tier: 'ALL' },
      })
    expect(await post(TODAY)).toBe('BATCH_ALREADY_COMPLETED')
    expect(await post(addDays(TODAY, -2))).toBe('BATCH_RECOVERY_REQUIRED')

    await run(addDays(TODAY, -6)) // 6번 실행 중(이력을 읽지 않아 계속 실행 중)
    expect(await post(addDays(TODAY, -6))).toBe('BATCH_ALREADY_RUNNING') // 같은 날짜·범위가 실행 중
    expect(await post(addDays(TODAY, -7))).toBe('BATCH_ALREADY_RUNNING') // 다른 날짜가 실행 중
    // 같은 날짜·범위의 상태가 먼저다: 다른 실행이 돌아도 성공·중단됨은 그 코드다
    expect(await post(addDays(TODAY, -1))).toBe('BATCH_ALREADY_COMPLETED')
    expect(await post(addDays(TODAY, -2))).toBe('BATCH_RECOVERY_REQUIRED')
  })

  it('범위는 ALL만, 날짜는 오늘 이하만 받고(400) 서버처럼 fieldErrors로 필드를 알린다', async () => {
    const tierError = await customInstance({
      url: '/admin/collect/executions',
      method: 'POST',
      data: { targetDate: addDays(TODAY, -9), tier: 'DAILY' },
    }).catch((e: unknown) => e)
    expect(tierError).toMatchObject({ status: 400, errorCode: 'VALIDATION_FAILED' })
    expect(isApiError(tierError) && tierError.fieldErrors.map((f) => f.field)).toEqual(['tier'])

    const dateError = await customInstance({
      url: '/admin/collect/executions',
      method: 'POST',
      data: { targetDate: addDays(TODAY, 1), tier: 'ALL' },
    }).catch((e: unknown) => e)
    expect(dateError).toMatchObject({ status: 400, errorCode: 'VALIDATION_FAILED' })
    expect(isApiError(dateError) && dateError.fieldErrors).toEqual([
      { field: 'targetDate', message: '미래 날짜는 실행할 수 없습니다.' },
    ])
  })

  it('복구는 중단됨만 가능하고, 복구한 실행은 실패가 되어 다시 실행할 수 있다(새 실행 ID)', async () => {
    expect(
      await failureCodeOf({ url: '/admin/collect/executions/4/recover', method: 'POST' }),
    ).toBe('BATCH_NOT_STOPPED')
    const recovered = await customInstance<CommonResponseCollectExecution>({
      url: '/admin/collect/executions/3/recover',
      method: 'POST',
    })
    // 계약: 복구 응답은 실패, 종료 시각 없음, 고정 사유, 버튼 값은 서버 규칙으로 계산(최신 실패이고 실행 중 없음 → 다시 실행 가능)
    expect(recovered.data).toMatchObject({
      status: 'FAILED',
      endedAt: null,
      failureReason: '중단된 실행을 관리자가 실패로 복구함',
      canRecover: false,
      canRestart: true,
    })

    const restarted = await customInstance<CommonResponseCollectExecutionAccepted>({
      url: '/admin/collect/executions/3/restart',
      method: 'POST',
    })
    expect(restarted.data.executionId).toBe(6) // 이력의 최대 ID(5) + 1
    const list = await listExecutions()
    expect(list.find((e) => e.executionId === 3)).toMatchObject({
      status: 'FAILED', // 실패 기록은 남는다
      canRestart: false, // 같은 날짜·범위에 더 나중 실행(6번)이 생겼다
    })
    expect(list.find((e) => e.executionId === 6)).toMatchObject({
      targetDate: recovered.data.targetDate,
      status: 'RUNNING',
    })
    expect(list.find((e) => e.executionId === 2)!.canRestart).toBe(false) // 6번이 실행 중이라 막힌다
  })

  it('다시 실행 거절 코드는 그 날짜·범위의 최신 상태와 다른 실행 중 여부로 정한다(BATCH_NOT_STOPPED는 쓰지 않는다)', async () => {
    const restart = (id: number) =>
      failureCodeOf({ url: `/admin/collect/executions/${id}/restart`, method: 'POST' })
    expect(await restart(4)).toBe('BATCH_ALREADY_COMPLETED') // 성공
    expect(await restart(3)).toBe('BATCH_RECOVERY_REQUIRED') // 중단됨
    expect(await restart(99)).toBe('RESOURCE_NOT_FOUND')

    await run(addDays(TODAY, -6)) // 다른 날짜가 실행 중
    expect(await restart(2)).toBe('BATCH_ALREADY_RUNNING')
  })

  it('?mockScenario=run-error는 실행만 500이고 이력은 정상이다', async () => {
    expect(
      await failureCodeOf({
        url: '/admin/collect/executions',
        method: 'POST',
        headers: { 'X-Mock-Scenario': 'run-error' },
        data: { targetDate: addDays(TODAY, -6), tier: 'ALL' },
      }),
    ).toBe('INTERNAL_ERROR')
    expect(await listExecutions()).toHaveLength(5)
  })

  it('접수한 실행이 새로고침 뒤에도 남고 reset이 지운다', async () => {
    await run(addDays(TODAY, -6))
    vi.resetModules()
    const fresh = await import('@/mocks/adminHandlers')
    server.use(...fresh.adminHandlers)

    const list = await listExecutions()
    expect(list).toHaveLength(6)
    expect(list.map((e) => e.executionId)).toEqual([5, 4, 3, 2, 1, 6]) // 대상 날짜 내림차순(새 실행은 6일 전)
    fresh.resetMockAdminState()
    expect(window.sessionStorage.getItem('ottnavi:mock-admin-collect')).toBeNull()
  })
})

/*
 * Task 061: 서버(AdminProductService·AdminProviderService·CollectExecutionService)와 같은 검사 순서·문구인지 본다.
 * 서버는 요청 본문 검사(400) → 존재 확인(404) → 규칙(409) 순서이고, 400의 detail은 고정 문구, 필드별 안내는 fieldErrors다.
 */
describe('서버 검사 순서와 오류 모양', () => {
  // 실패한 요청의 ApiError를 돌려준다. 성공하면 테스트가 잘못된 것이라 바로 실패시킨다
  async function errorOf(config: Parameters<typeof customInstance>[0]) {
    try {
      await customInstance(config)
    } catch (error) {
      if (isApiError(error)) return error
      throw error
    }
    throw new Error('요청이 성공했다(실패해야 한다)')
  }

  it('소수 금액은 400이고 fieldErrors에 형식 오류가 담긴다(버림으로 통과하지 않는다)', async () => {
    const error = await errorOf({
      url: `/admin/products/${NETFLIX_ID}/prices`,
      method: 'POST',
      data: { monthlyPrice: 9900.5, effectiveFrom: addDays(TODAY, 400) },
    })
    expect(error).toMatchObject({ status: 400, errorCode: 'VALIDATION_FAILED' })
    expect(error.detail).toBe('요청 값이 올바르지 않습니다.')
    expect(error.fieldErrors).toEqual([
      { field: 'monthlyPrice', message: '형식이 올바르지 않습니다.' },
    ])
  })

  it('금액 범위 오류는 필드별 문구로 모두 알린다(0 미만 / 100만 초과 / 시작일 누락)', async () => {
    const tooLow = await errorOf({
      url: `/admin/products/${NETFLIX_ID}/prices`,
      method: 'POST',
      data: { monthlyPrice: -1 },
    })
    expect(tooLow.fieldErrors).toEqual([
      { field: 'monthlyPrice', message: '0 이상이어야 합니다.' },
      { field: 'effectiveFrom', message: '적용 시작일은 필수입니다.' },
    ])
    const tooHigh = await errorOf({
      url: `/admin/products/${NETFLIX_ID}/prices`,
      method: 'POST',
      data: { monthlyPrice: 1_000_001, effectiveFrom: addDays(TODAY, 400) },
    })
    expect(tooHigh.fieldErrors).toEqual([
      { field: 'monthlyPrice', message: '1,000,000 이하여야 합니다.' },
    ])
  })

  it('본문 오류(400)가 존재 확인(404)과 규칙 위반(409)보다 먼저다', async () => {
    // 없는 상품 + 잘못된 본문 → 404가 아니라 400
    expect(
      (
        await errorOf({
          url: '/admin/products/99999/prices',
          method: 'POST',
          data: { monthlyPrice: 9900.5, effectiveFrom: TODAY },
        })
      ).status,
    ).toBe(400)

    // 가장 최근 줄이 아닌 줄 + 잘못된 본문 → PRICE_NOT_LATEST(409)가 아니라 400
    const netflix = (await listProducts()).find((p) => p.productId === NETFLIX_ID)!
    expect(
      (
        await errorOf({
          url: `/admin/products/${NETFLIX_ID}/prices/${netflix.prices[0].priceId}`,
          method: 'PATCH',
          data: { monthlyPrice: 9900.5, effectiveFrom: TODAY },
        })
      ).status,
    ).toBe(400)
  })

  it('없는 상품·없는 요금 줄은 404 RESOURCE_NOT_FOUND이고 문구가 서버와 같다', async () => {
    const noProduct = await errorOf({
      url: '/admin/products/99999/prices/1',
      method: 'DELETE',
    })
    expect(noProduct).toMatchObject({ status: 404, errorCode: 'RESOURCE_NOT_FOUND' })
    expect(noProduct.detail).toBe('해당 구독상품을 찾을 수 없습니다.')

    const noPrice = await errorOf({
      url: `/admin/products/${NETFLIX_ID}/prices/99999`,
      method: 'DELETE',
    })
    expect(noPrice.detail).toBe('해당 월 구독요금을 찾을 수 없습니다.')
  })

  it('PRICE_* 409의 문구가 서버 ErrorCode와 같다', async () => {
    const netflix = (await listProducts()).find((p) => p.productId === NETFLIX_ID)!
    const lastStart = netflix.prices[netflix.prices.length - 1].effectiveFrom
    const notAfter = await errorOf({
      url: `/admin/products/${NETFLIX_ID}/prices`,
      method: 'POST',
      data: { monthlyPrice: 15000, effectiveFrom: addDays(lastStart, -1) },
    })
    expect(notAfter.detail).toBe('적용 시작일은 마지막 월 구독요금의 시작일보다 늦어야 합니다.')

    const notLatest = await errorOf({
      url: `/admin/products/${NETFLIX_ID}/prices/${netflix.prices[0].priceId}`,
      method: 'DELETE',
    })
    expect(notLatest).toMatchObject({ status: 409, errorCode: 'PRICE_NOT_LATEST' })
    expect(notLatest.detail).toBe('가장 최근 월 구독요금만 수정하거나 삭제할 수 있습니다.')
  })

  it('상품 수정은 이름·판매 상태가 모두 없으면 400(request), 이름이 100자를 넘으면 400(name)이다', async () => {
    const empty = await errorOf({
      url: `/admin/products/${NETFLIX_ID}`,
      method: 'PATCH',
      data: {},
    })
    expect(empty.fieldErrors).toEqual([
      { field: 'request', message: 'name, isActive, isLowCost 중 하나는 보내야 합니다.' },
    ])
    const tooLong = await errorOf({
      url: `/admin/products/${NETFLIX_ID}`,
      method: 'PATCH',
      data: { name: 'ㄱ'.repeat(101) },
    })
    expect(tooLong.fieldErrors).toEqual([
      { field: 'name', message: '이름은 100자 이하여야 합니다.' },
    ])
    // 400이 404보다 먼저다
    expect(
      (await errorOf({ url: '/admin/products/99999', method: 'PATCH', data: {} })).status,
    ).toBe(400)
  })

  it('상품 등록의 서비스 계층 검사: 번들은 서비스 2개 이상이어야 하고, 없는 서비스는 ottServiceIds 필드로 알린다', async () => {
    const base = { name: '새 상품', monthlyPrice: 10000, effectiveFrom: TODAY }
    const bundle = await errorOf({
      url: '/admin/products',
      method: 'POST',
      data: { ...base, productType: 'BUNDLE', ottServiceIds: [1], isLowCost: false },
    })
    expect(bundle.fieldErrors).toEqual([
      { field: 'ottServiceIds', message: '번들은 서비스 2개 이상이 필요합니다.' },
    ])
    const noService = await errorOf({
      url: '/admin/products',
      method: 'POST',
      data: { ...base, productType: 'SINGLE', ottServiceIds: [99], isLowCost: false },
    })
    expect(noService.fieldErrors).toEqual([
      { field: 'ottServiceIds', message: '존재하지 않는 서비스입니다.' },
    ])
  })

  it('제공처 저장은 상태·서비스 불일치(400)가 404보다 먼저이고, 없는 제공처는 404 문구가 서버와 같다', async () => {
    const mismatch = await errorOf({
      url: '/admin/providers/99999',
      method: 'PATCH',
      data: { mappingState: 'MAPPED', ottServiceId: null, tier: 'STANDARD' },
    })
    expect(mismatch.status).toBe(400)
    expect(mismatch.fieldErrors).toEqual([
      { field: 'ottServiceId', message: 'MAPPED이면 서비스를 선택해야 합니다.' },
    ])
    const notFound = await errorOf({
      url: '/admin/providers/99999',
      method: 'PATCH',
      data: { mappingState: 'IGNORED', ottServiceId: null, tier: 'STANDARD' },
    })
    expect(notFound.detail).toBe('해당 제공처를 찾을 수 없습니다.')
  })

  it('쪽 번호 오류는 어긋난 필드마다 fieldErrors로 알리고, 변경 작품 조회에서는 404보다 먼저다', async () => {
    const error = await errorOf({
      url: '/admin/collect/executions/99999/changes',
      method: 'GET',
      params: { page: -1, size: 101 },
    })
    expect(error.status).toBe(400)
    expect(error.fieldErrors).toEqual([
      { field: 'page', message: '0 이상이어야 합니다.' },
      { field: 'size', message: '100 이하여야 합니다.' },
    ])
    const notFound = await errorOf({
      url: '/admin/collect/executions/99999/changes',
      method: 'GET',
    })
    expect(notFound.detail).toBe('해당 수집 실행을 찾을 수 없습니다.')
  })

  it('수집 실행 검사 순서: 범위(tier)가 날짜보다 먼저다', async () => {
    const error = await errorOf({
      url: '/admin/collect/executions',
      method: 'POST',
      data: { targetDate: addDays(TODAY, 5), tier: 'DAILY' },
    })
    expect(error.fieldErrors).toEqual([
      { field: 'tier', message: 'MVP는 ALL만 실행할 수 있습니다.' },
    ])
    expect(error.detail).toBe('요청 값이 올바르지 않습니다.')
  })
})
