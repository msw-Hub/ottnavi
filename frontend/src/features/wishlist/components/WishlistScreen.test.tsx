import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { customInstance } from '@/api/http'
import { Providers } from '@/app/providers'
import { WishlistScreen } from '@/features/wishlist/components/WishlistScreen'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { mockWishlist } from '@/mocks/handlerUtils'
import { TITLE_FIXTURES } from '@/mocks/fixtures/titles'
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

// 목업 작품의 찜 단위(영화 1편, 드라마 시즌 1개)를 n개 목업 저장소에 직접 넣는다(다 봤음 아님). 화면은 서버(목업)의 개수를 그대로 보인다
function fillActiveWishlist(count: number) {
  let id = 1000
  for (const title of TITLE_FIXTURES) {
    const seasonNumbers =
      title.mediaType === 'tv' ? title.seasons.map((s) => s.seasonNumber) : [null]
    for (const seasonNumber of seasonNumbers) {
      if (mockWishlist.size >= count) return
      mockWishlist.set(id++, {
        mediaType: title.mediaType,
        tmdbId: title.tmdbId,
        seasonNumber,
        priority: 'WANT',
        watchedAt: null,
      })
    }
  }
}

// 우선순위 나누기 권유 문장 (활성 찜 2개 이상 + 모두 같은 단계일 때만)
const SPLIT_HINT = /우선순위를 나누면 계산이 더 의미 있어져요/

async function addMovie(tmdbId: number, priority: 'MUST' | 'WANT' | 'MAYBE') {
  await customInstance({
    url: '/me/wishlist-items',
    method: 'POST',
    data: { mediaType: 'movie', tmdbId, seasonNumber: null, priority },
  })
}

describe('우선순위 나누기 권유', () => {
  it('찜이 2개 이상이고 모두 같은 단계면 권유가 보인다', async () => {
    await addMovie(524, 'WANT')
    await addMovie(496243, 'WANT')
    renderWishlist()

    expect(await screen.findByText(SPLIT_HINT)).toBeInTheDocument()
    // 고정 문구는 그대로 있다
    expect(screen.getByText(/계산은 꼭 볼 작품을 가장 먼저 챙기고/)).toBeInTheDocument()
  })

  it('단계가 섞여 있으면 권유는 없고 고정 문구만 남는다', async () => {
    await addMovie(524, 'MUST')
    await addMovie(496243, 'WANT')
    renderWishlist()

    expect(await screen.findByText(/계산은 꼭 볼 작품을 가장 먼저 챙기고/)).toBeInTheDocument()
    expect(screen.queryByText(SPLIT_HINT)).not.toBeInTheDocument()
  })

  it('찜이 1개뿐이면 나눌 수 없으므로 권유가 없다', async () => {
    await addMovie(524, 'WANT')
    renderWishlist()

    expect(await screen.findByText(/계산은 꼭 볼 작품을 가장 먼저 챙기고/)).toBeInTheDocument()
    expect(screen.queryByText(SPLIT_HINT)).not.toBeInTheDocument()
  })
})

describe('찜 개수와 안내(Task 030 확정 문구)', () => {
  it('개수 줄은 "찜 N/20"이고 20개 전에는 안내 띠가 없다', async () => {
    await addFourMovies()
    renderWishlist()

    expect(await screen.findByText(/찜 4\/20/)).toBeInTheDocument()
    expect(screen.queryByText(/자리가 생겨요/)).not.toBeInTheDocument()
  })

  it('20개에 닿으면 목록 상단에 안내 띠가 보이고, 다 본 작품은 개수에 넣지 않는다', async () => {
    fillActiveWishlist(20)
    renderWishlist()

    expect(await screen.findByText(/찜 20\/20/)).toBeInTheDocument()
    expect(
      screen.getByText(
        "찜은 20개까지 담을 수 있어요. 다 본 작품을 '다 봤음'으로 옮기면 자리가 생겨요.",
      ),
    ).toBeInTheDocument()
  })

  it('같은 드라마의 시즌을 둘 이상 찜하면 계산 순서 안내가 한 줄 보인다', async () => {
    for (const seasonNumber of [1, 2]) {
      await customInstance({
        url: '/me/wishlist-items',
        method: 'POST',
        data: { mediaType: 'tv', tmdbId: 93405, seasonNumber, priority: 'WANT' },
      })
    }
    renderWishlist()

    expect(
      await screen.findByText('같은 드라마는 앞 시즌부터 보는 순서로 계산해요.'),
    ).toBeInTheDocument()
  })

  it('시즌이 하나뿐이면 순서 안내가 없다', async () => {
    await addFourMovies()
    renderWishlist()

    await screen.findByText(/찜 4\/20/)
    expect(screen.queryByText(/앞 시즌부터 보는 순서/)).not.toBeInTheDocument()
  })
})
