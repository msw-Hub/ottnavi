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

describe('요금 확인 화면', () => {
  it('금액을 고쳐 적용하면 "계산에 쓰는 금액"이 바뀌고, 계산하기를 누르면 플랜 결과 화면으로 간다', async () => {
    const user = userEvent.setup()
    await addWishlistItem()
    const router = renderPricing()

    // 넷플릭스 줄의 내 금액을 9,900원으로 바꾼다
    const input = await screen.findByLabelText('내 금액 (넷플릭스)')
    const row = input.closest('li')!
    await user.type(input, '9900')
    await user.click(within(row).getByRole('button', { name: '적용' }))
    expect(await within(row).findByText('사용자 수정')).toBeInTheDocument()
    expect(within(row).getAllByText('9,900원').length).toBeGreaterThan(0)

    await user.click(screen.getByRole('button', { name: '3개월 플랜 계산하기' }))
    expect(await screen.findByRole('heading', { name: '플랜 결과 화면' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/plan')
  })

  it('요금 기준일은 관리자 확인 일시의 한국 날짜로 보이고, 내 금액은 기본 요금으로 되돌릴 수 있다', async () => {
    const user = userEvent.setup()
    renderPricing()

    // 목업의 확인 일시는 2026-10-08T18:30+09:00이다
    expect(await screen.findByText('2026년 10월 8일')).toBeInTheDocument()

    const input = await screen.findByLabelText('내 금액 (넷플릭스)')
    const row = input.closest('li')!
    await user.type(input, '9900')
    await user.click(within(row).getByRole('button', { name: '적용' }))
    await user.click(await within(row).findByRole('button', { name: '기본 요금으로 되돌리기' }))
    await waitFor(() => expect(within(row).queryByText('사용자 수정')).not.toBeInTheDocument())
  })

  it('요금 확인일을 줄마다 보이고, 90일 넘게 확인하지 않은 요금에만 안내를 덧붙인다(계산은 막지 않는다)', async () => {
    // 목업 기본값(확인일 2026-10-08)을 넷플릭스는 오래된 일시로, 디즈니+는 어제로 바꿔 내려준다
    const yesterday = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString() // 고정한 시각(2026-10-13)의 어제
    server.use(
      http.get('/api/me/service-prices', () =>
        HttpResponse.json({
          success: true,
          data: {
            services: [
              {
                ottServiceId: 1,
                productId: 101,
                productName: '넷플릭스 스탠다드',
                adminPrice: 13500,
                userPrice: null,
                appliedPrice: 13500,
                priceSource: 'ADMIN',
                subscriptionStatus: 'NOT_SUBSCRIBED',
                effectiveFrom: '2026-01-01',
                lastVerifiedAt: '2026-06-14T09:00:00+09:00',
              },
              {
                ottServiceId: 2,
                productId: 102,
                productName: '디즈니+ 스탠다드',
                adminPrice: 9900,
                userPrice: null,
                appliedPrice: 9900,
                priceSource: 'ADMIN',
                subscriptionStatus: 'NOT_SUBSCRIBED',
                effectiveFrom: '2026-01-01',
                lastVerifiedAt: yesterday,
              },
            ],
          },
        }),
      ),
    )
    renderPricing()

    const netflixRow = (await screen.findByLabelText('내 금액 (넷플릭스)')).closest('li')!
    const disneyRow = screen.getByLabelText('내 금액 (디즈니+)').closest('li')!
    expect(within(netflixRow).getByText('요금 확인일 2026년 6월 14일')).toBeInTheDocument()
    expect(
      within(netflixRow).getByText(
        '요금이 달라졌을 수 있어요. 실제 금액이 다르면 직접 수정해 주세요.',
      ),
    ).toBeInTheDocument()
    expect(within(disneyRow).getByText(/요금 확인일/)).toBeInTheDocument()
    expect(within(disneyRow).queryByText(/요금이 달라졌을 수 있어요/)).not.toBeInTheDocument()
    // 계산은 막지 않는다
    expect(screen.getByRole('button', { name: '3개월 플랜 계산하기' })).toBeEnabled()
  })

  it('요금 확인 일시를 해석할 수 없으면 확인일 줄은 빼고 오래된 요금으로 보아 안내만 보인다', async () => {
    server.use(
      http.get('/api/me/service-prices', () =>
        HttpResponse.json({
          success: true,
          data: {
            services: [
              {
                ottServiceId: 1,
                productId: 101,
                productName: '넷플릭스 스탠다드',
                adminPrice: 13500,
                userPrice: null,
                appliedPrice: 13500,
                priceSource: 'ADMIN',
                subscriptionStatus: 'NOT_SUBSCRIBED',
                effectiveFrom: '2026-01-01',
                lastVerifiedAt: 'invalid',
              },
            ],
          },
        }),
      ),
    )
    renderPricing()

    const netflixRow = (await screen.findByLabelText('내 금액 (넷플릭스)')).closest('li')!
    expect(within(netflixRow).queryByText(/요금 확인일/)).not.toBeInTheDocument()
    expect(
      within(netflixRow).getByText(
        '요금이 달라졌을 수 있어요. 실제 금액이 다르면 직접 수정해 주세요.',
      ),
    ).toBeInTheDocument()
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
