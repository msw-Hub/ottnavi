import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { customInstance } from '@/api/http'
import { Providers } from '@/app/providers'
import { PricingScreen } from '@/features/pricing/components/PricingScreen'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { resetMockMemberState } from '@/mocks/memberHandlers'
import { resetMockSession, setMockOnboardingDone, setMockRole } from '@/mocks/mockSession'

/*
 * 요금 확인 화면 테스트(Task 028): 금액을 고쳐 적용하고, "계산하기"로 플랜 계산을 시작하면 결과 화면으로 이동하는 흐름을 본다.
 * 결과 화면(plan 기능)은 기능 폴더끼리 import하지 않으려고 제목만 있는 자리표시로 대신한다. API는 앱의 목업 핸들러(MSW)다.
 */

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
beforeEach(() => {
  // 요금 확인일의 "90일 넘음" 판정이 실제 날짜에 흔들리지 않도록 Date만 고정한다(MSW·user-event의 타이머는 그대로 둔다)
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-13T12:00:00+09:00'))
  vi.stubEnv('VITE_USE_MOCK', 'true')
  setMockRole('USER')
  setMockOnboardingDone(true) // 설정(예산·시청 시간)이 있는 회원이어야 계산할 수 있다
})
afterEach(() => {
  vi.useRealTimers()
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockSession()
  resetMockMemberState()
  resetMockWishlist()
})
afterAll(() => server.close())

function renderPricing(initialPath = '/pricing') {
  const router = createMemoryRouter(
    [
      { path: '/pricing', element: <PricingScreen /> },
      { path: '/plan', element: <h1>플랜 결과 화면</h1> },
      { path: '/wishlist', element: <h1>찜 목록 화면</h1> },
    ],
    { initialEntries: [initialPath] },
  )
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
  return router
}

// 영화 "기생충"을 찜해 둔다. 찜한 작품이 있어야 서버(목업)가 계산을 받아 준다
async function addWishlistItem() {
  await customInstance({
    url: '/me/wishlist-items',
    method: 'POST',
    data: { mediaType: 'movie', tmdbId: 496243, seasonNumber: null, priority: 'MUST' },
  })
}

// 요금 API 응답 한 줄을 만든다(계약 ServicePrice). 필요한 값만 덮어써서 쓴다
function priceLine(overrides: Record<string, unknown>) {
  return {
    ottServiceId: 1,
    productId: 101,
    productName: '넷플릭스 스탠다드',
    productType: 'SINGLE',
    ottServiceIds: [1],
    isLowCost: false,
    isCalcCandidate: true,
    adminPrice: 13500,
    userPrice: null,
    appliedPrice: 13500,
    priceSource: 'ADMIN',
    subscriptionStatus: 'NOT_SUBSCRIBED',
    effectiveFrom: '2026-01-01',
    lastVerifiedAt: '2026-10-08T09:00:00+09:00',
    ...overrides,
  }
}

// 요금 목록 응답을 주어진 줄로 바꾼다
function useServicePrices(lines: Record<string, unknown>[]) {
  server.use(
    http.get('/api/me/service-prices', () =>
      HttpResponse.json({ success: true, data: { services: lines } }),
    ),
  )
}

describe('요금 확인 화면', () => {
  it('금액을 고쳐 적용하면 "계산에 쓰는 금액"이 바뀌고, 계산하기를 누르면 플랜 결과 화면으로 간다', async () => {
    const user = userEvent.setup()
    await addWishlistItem()
    const router = renderPricing()

    // 넷플릭스 카드의 내 금액을 9,900원으로 바꾼다(이름표는 요금제 이름이다)
    const input = await screen.findByLabelText('내 금액 (넷플릭스 스탠다드)')
    const card = input.closest('li')!
    await user.type(input, '9900')
    await user.click(within(input.closest('form')!).getByRole('button', { name: '적용' }))
    expect(await within(card).findByText('내가 고친 금액')).toBeInTheDocument()
    expect(within(card).getAllByText('9,900원').length).toBeGreaterThan(0)

    await user.click(screen.getByRole('button', { name: '3개월 플랜 계산하기' }))
    expect(await screen.findByRole('heading', { name: '플랜 결과 화면' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/plan')
  })

  it('요금 기준일은 관리자 확인 일시의 한국 날짜로 보이고, 내 금액은 기본 요금으로 되돌릴 수 있다', async () => {
    const user = userEvent.setup()
    renderPricing()

    // 목업의 확인 일시는 2026-10-08T18:30+09:00이다
    expect(await screen.findByText('2026년 10월 8일')).toBeInTheDocument()

    const input = await screen.findByLabelText('내 금액 (넷플릭스 스탠다드)')
    const card = input.closest('li')!
    const form = input.closest('form')!
    await user.type(input, '9900')
    await user.click(within(form).getByRole('button', { name: '적용' }))
    await user.click(await within(form).findByRole('button', { name: '기본 요금으로 되돌리기' }))
    await waitFor(() => expect(within(card).queryByText('내가 고친 금액')).not.toBeInTheDocument())
  })

  it('서비스당 카드 한 장에 계산에 쓰는 요금제를 보이고, 안 쓰는 요금제는 접어 두었다가 펼치면 입력할 수 있다', async () => {
    const user = userEvent.setup()
    renderPricing()

    // 모드 끔(목업 기본): 넷플릭스는 표준 요금제를 쓰고 광고형(저가형)은 접힌 안에 있다
    const input = await screen.findByLabelText('내 금액 (넷플릭스 스탠다드)')
    const card = input.closest('li')!
    expect(within(card).queryByText('계산에 사용')).not.toBeInTheDocument()
    const toggle = within(card).getByRole('button', { name: /계산에 안 쓰는 요금제 1개/ })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(
      within(card).queryByLabelText('내 금액 (넷플릭스 광고형 스탠다드)'),
    ).not.toBeInTheDocument()

    await user.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(within(card).getByLabelText('내 금액 (넷플릭스 광고형 스탠다드)')).toBeInTheDocument()
    expect(within(card).getByText('저가형')).toBeInTheDocument()
    expect(within(card).queryByText('안 씀')).not.toBeInTheDocument()
  })

  it('번들은 단품 아래 "번들 상품" 구역에 카드로 나오고, 제목은 구성 서비스 이름이며, 안 쓰는 번들은 구역 아래 버튼으로 접힌다', async () => {
    const user = userEvent.setup()
    renderPricing()

    const bundleInput = await screen.findByLabelText('내 금액 (티빙·웨이브 더블 이용권)')
    const card = bundleInput.closest('li')!
    const section = screen.getByRole('region', { name: '번들 상품' })
    expect(screen.getByRole('heading', { level: 3, name: '번들 상품' })).toBeInTheDocument()
    expect(section).toContainElement(card)
    expect(within(card).getByText('티빙 + 웨이브', { selector: 'p' })).toBeInTheDocument()
    expect(within(card).getByText('티빙·웨이브 더블 이용권')).toBeInTheDocument()
    expect(within(card).getByText('번들')).toBeInTheDocument()
    expect(within(section).getAllByRole('listitem')).toHaveLength(1)

    // 안 쓰는 번들(저가형)은 구역 맨 아래 버튼 한 줄로 접혀 있고, 펼치면 카드가 보인다
    const toggle = within(section).getByRole('button', { name: /계산에 안 쓰는 번들 1개/ })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(card).not.toContainElement(toggle)
    await user.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(within(section).getAllByRole('listitem')).toHaveLength(2)
    expect(within(section).getByText('번들·저가형')).toBeInTheDocument()
    // 번들 사용 안내는 목록 위에 한 번만 있다
    expect(screen.getAllByText(/번들을 쓰고 있다면/)).toHaveLength(1)
  })

  it('후보 번들이 2개면 번들 카드가 2장이고, 안 쓰는 번들이 없으면 접기 버튼이 없다', async () => {
    const bundle = {
      ottServiceId: null,
      productType: 'BUNDLE',
      ottServiceIds: [4, 5],
      adminPrice: 16000,
      appliedPrice: 16000,
    }
    useServicePrices([
      priceLine({}),
      priceLine({ ...bundle, productId: 201, productName: '티빙·웨이브 더블 이용권' }),
      priceLine({
        ...bundle,
        productId: 202,
        productName: '티빙·웨이브 더블 광고형',
        isLowCost: true,
      }),
    ])
    renderPricing()

    const section = await screen.findByRole('region', { name: '번들 상품' })
    expect(within(section).getAllByRole('listitem')).toHaveLength(2)
    expect(within(section).queryByRole('button', { name: /계산에 안 쓰는 번들/ })).toBeNull()
  })

  it('금액이 잘못되면 오류 문장이 입력 칸과 aria-describedby로 이어진다', async () => {
    const user = userEvent.setup()
    renderPricing()

    const input = await screen.findByLabelText('내 금액 (넷플릭스 스탠다드)')
    await user.type(input, '-5')
    await user.click(within(input.closest('form')!).getByRole('button', { name: '적용' }))

    const message = await screen.findByText('0 이상의 정수(원 단위)로 입력해 주세요.')
    expect(input).toHaveAttribute('aria-describedby', message.id)
  })

  it('90일 넘게 확인하지 않은 요금에만 카드에 경고를 보이고, 줄별 확인일 문구는 없다(계산은 막지 않는다)', async () => {
    // 고정한 시각(2026-10-13)의 어제와 오래된 일시(넷플릭스 2026-06-14)
    const yesterday = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString()
    useServicePrices([
      priceLine({ lastVerifiedAt: '2026-06-14T09:00:00+09:00' }),
      priceLine({
        ottServiceId: 2,
        productId: 102,
        productName: '디즈니+ 스탠다드',
        ottServiceIds: [2],
        adminPrice: 9900,
        appliedPrice: 9900,
        lastVerifiedAt: yesterday,
      }),
    ])
    renderPricing()

    const netflixCard = (await screen.findByLabelText('내 금액 (넷플릭스 스탠다드)')).closest('li')!
    const disneyCard = screen.getByLabelText('내 금액 (디즈니+ 스탠다드)').closest('li')!
    expect(within(netflixCard).getByText('요금이 달라졌을 수 있어요')).toBeInTheDocument()
    expect(within(disneyCard).queryByText(/요금이 달라졌을 수 있어요/)).not.toBeInTheDocument()
    expect(screen.queryByText(/요금 확인일/)).not.toBeInTheDocument()
    // 계산은 막지 않는다
    expect(screen.getByRole('button', { name: '3개월 플랜 계산하기' })).toBeEnabled()
  })

  it('요금 확인 일시를 해석할 수 없으면 오래된 요금으로 보아 경고를 보인다', async () => {
    useServicePrices([priceLine({ lastVerifiedAt: 'invalid' })])
    renderPricing()

    const card = (await screen.findByLabelText('내 금액 (넷플릭스 스탠다드)')).closest('li')!
    expect(within(card).getByText('요금이 달라졌을 수 있어요')).toBeInTheDocument()
  })

  it('구독 중인 서비스는 서비스 이름 옆에 한 번만 표시된다', async () => {
    useServicePrices([priceLine({ subscriptionStatus: 'SUBSCRIBED' })])
    renderPricing()

    const card = (await screen.findByLabelText('내 금액 (넷플릭스 스탠다드)')).closest('li')!
    expect(within(card).getAllByText('구독 중')).toHaveLength(1)
  })

  it('찜한 작품이 없으면 계산이 실패하고, 찜 목록으로 가는 링크와 함께 이유를 알린다', async () => {
    const user = userEvent.setup()
    const router = renderPricing()

    await user.click(await screen.findByRole('button', { name: '3개월 플랜 계산하기' }))

    expect(await screen.findByText('플랜을 계산하지 못했어요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '찜 목록 확인하기' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/pricing') // 실패하면 이동하지 않는다
  })
})
