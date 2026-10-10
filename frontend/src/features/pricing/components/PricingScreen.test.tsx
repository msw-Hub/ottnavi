import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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
  vi.stubEnv('VITE_USE_MOCK', 'true')
  setMockRole('USER')
  setMockOnboardingDone(true) // 설정(예산·시청 시간)이 있는 회원이어야 계산할 수 있다
})
afterEach(() => {
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

  it('찜한 작품이 없으면 계산이 실패하고, 찜 목록으로 가는 링크와 함께 이유를 알린다', async () => {
    const user = userEvent.setup()
    const router = renderPricing()

    await user.click(await screen.findByRole('button', { name: '3개월 플랜 계산하기' }))

    expect(await screen.findByText('플랜을 계산하지 못했어요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '찜 목록 확인하기' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/pricing') // 실패하면 이동하지 않는다
  })
})
