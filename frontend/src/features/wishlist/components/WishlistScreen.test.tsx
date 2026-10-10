import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { customInstance } from '@/api/http'
import { Providers } from '@/app/providers'
import { WishlistScreen } from '@/features/wishlist/components/WishlistScreen'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { resetMockMemberState } from '@/mocks/memberHandlers'
import { resetMockSession, setMockRole } from '@/mocks/mockSession'

/*
 * 찜 목록 화면 테스트(Task 028). 펼침 패널이 "목록 전체에서 한 번에 하나만" 열리는지 본다.
 * 카드 4개를 찜해 두 행(2개씩 묶음)이 생기게 한 뒤, 다른 행의 카드를 눌렀을 때 이전 패널이 닫히는지 확인한다.
 * API는 앱의 목업 핸들러(MSW)로 대체한다.
 */

// 구역 제목(계산에 쓸 작품 등)도 region이라, 서비스별 시청 가능 여부 패널만 이름으로 골라낸다
const PANEL = { name: /서비스별 시청 가능 여부/ }

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true')
  setMockRole('USER')
})
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockSession()
  resetMockMemberState()
  resetMockWishlist()
})
afterAll(() => server.close())

// 영화 4편을 찜한다(카드 4개 = 데스크톱 기준 두 행)
async function addFourMovies() {
  for (const tmdbId of [524, 496243, 838209, 837799]) {
    await customInstance({
      url: '/me/wishlist-items',
      method: 'POST',
      data: { mediaType: 'movie', tmdbId, seasonNumber: null, priority: 'MUST' },
    })
  }
}

function renderWishlist() {
  const router = createMemoryRouter([{ path: '/wishlist', element: <WishlistScreen /> }], {
    initialEntries: ['/wishlist'],
  })
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
}

describe('찜 목록 펼침 패널', () => {
  it('다른 카드(다른 행 포함)를 펼치면 이전에 열린 패널은 닫힌다', async () => {
    await addFourMovies()
    const user = userEvent.setup()
    renderWishlist()

    const toggles = await screen.findAllByRole('button', { name: /자세히 보기/ })
    expect(toggles).toHaveLength(4)

    // 첫 행의 카드를 연다
    await user.click(toggles[0])
    expect(screen.getAllByRole('region', PANEL)).toHaveLength(1)
    const firstRegion = screen.getByRole('region', PANEL)

    // 둘째 행의 카드를 연다. 첫 패널은 닫히고 패널은 여전히 하나뿐이다
    await user.click(screen.getAllByRole('button', { name: /자세히 보기/ })[2])
    expect(screen.getAllByRole('region', PANEL)).toHaveLength(1)
    expect(screen.getByRole('region', PANEL)).not.toBe(firstRegion)
    expect(screen.getAllByRole('button', { name: /자세히 접기/ })).toHaveLength(1)
    expect(screen.getAllByRole('button', { name: /자세히 보기/ })).toHaveLength(3)
  })

  it('열린 카드의 버튼을 다시 누르면 닫힌다', async () => {
    await addFourMovies()
    const user = userEvent.setup()
    renderWishlist()

    await user.click((await screen.findAllByRole('button', { name: /자세히 보기/ }))[1])
    expect(screen.getAllByRole('region', PANEL)).toHaveLength(1)

    await user.click(screen.getByRole('button', { name: /자세히 접기/ }))
    expect(screen.queryByRole('region', PANEL)).not.toBeInTheDocument()
  })

  it('펼친 카드의 찜을 해제하면 패널도 사라진다', async () => {
    await addFourMovies()
    const user = userEvent.setup()
    renderWishlist()

    await user.click((await screen.findAllByRole('button', { name: /자세히 보기/ }))[0])
    expect(screen.getAllByRole('region', PANEL)).toHaveLength(1)

    // 펼친 카드의 해제 버튼: 패널 제목이 "{작품 이름}의 ..."이라 이름으로 해당 카드의 X 버튼을 찾는다
    const regionTitle = screen.getByRole('region', PANEL).getAttribute('aria-labelledby')
    const title = document
      .getElementById(regionTitle ?? '')
      ?.textContent?.replace('의 서비스별 시청 가능 여부', '')
    await user.click(screen.getByRole('button', { name: `찜 해제: ${title}` }))

    await vi.waitFor(() => expect(screen.queryByRole('region', PANEL)).not.toBeInTheDocument())
    expect(screen.getAllByRole('button', { name: /자세히 보기/ })).toHaveLength(3)
  })
})
