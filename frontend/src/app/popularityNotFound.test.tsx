import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http as mswHttp, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { HomeScreen } from '@/features/home/components/HomeScreen'
import { RankingScreen } from '@/features/home/components/RankingScreen'
import { TitleDetailScreen } from '@/features/title-detail/components/TitleDetailScreen'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { resetMockMemberState } from '@/mocks/memberHandlers'
import { resetMockSession } from '@/mocks/mockSession'

/*
 * "인기 순위 카드로 들어갔는데 상세가 아직 없을 때" 안내 테스트.
 * 홈·랭킹(features/home)과 상세(features/title-detail)는 서로 import하지 못하므로, 둘을 함께 여는 이 테스트는 app/에 둔다.
 * 확인하는 것: 인기 카드로 들어온 404만 "상세 정보를 모으는 중" 안내를 보이고, 주소 직접 입력·다른 목록은 기존 일반 안내 그대로인지.
 * 막으려는 사고: state가 다른 목록까지 새어 일반 404가 "곧 반영"으로 오해되는 것, 인기 카드가 state를 안 싣는 것.
 */
const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
beforeEach(() => vi.stubEnv('VITE_USE_MOCK', 'true'))
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockSession()
  resetMockMemberState()
  resetMockWishlist()
})
afterAll(() => server.close())

const NOT_COLLECTED_TITLE = '아직 수집 전인 영화' // 목업 인기 순위에는 있고 상세 목업에는 없는 작품(tmdbId 910021)
const PENDING_NOTICE = '상세 정보를 모으는 중이에요'
const GENERAL_NOT_FOUND = /아직 수집되지 않은 작품일 수 있습니다/

function renderApp(initialPath: string) {
  const router = createMemoryRouter(
    [
      { path: '/', element: <HomeScreen /> },
      { path: '/ranking', element: <RankingScreen /> },
      { path: '/titles/:mediaType/:tmdbId', element: <TitleDetailScreen /> },
    ],
    { initialEntries: [initialPath] },
  )
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
}

// 모든 상세 조회를 404로 만든다(찜 많은 작품처럼 DB에 있는 작품에서도 404 처리를 확인하려는 용도)
function allDetailsNotFound() {
  server.use(
    mswHttp.get('/api/public/titles/:mediaType/:tmdbId', () =>
      HttpResponse.json(
        { title: 'Not Found', status: 404, errorCode: 'TITLE_NOT_FOUND' },
        { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    ),
  )
}

describe('인기 순위에서 들어온 상세 404 안내', () => {
  it('홈 인기 순위 카드로 들어와 404면 "상세 정보를 모으는 중" 안내와 카드의 제목을 보인다', async () => {
    const user = userEvent.setup()
    renderApp('/')

    const ranking = await screen.findByRole('list', { name: '전체 인기 순위' })
    await user.click(within(ranking).getByRole('link', { name: NOT_COLLECTED_TITLE }))

    expect(await screen.findByText(PENDING_NOTICE)).toBeInTheDocument()
    expect(screen.getByText(/다음 정보 수집 때 함께 반영돼요/)).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(NOT_COLLECTED_TITLE)
    expect(screen.queryByText(GENERAL_NOT_FOUND)).not.toBeInTheDocument()
  })

  it('랭킹 화면의 인기 순위 카드로 들어와도 같은 안내를 보인다', async () => {
    const user = userEvent.setup()
    renderApp('/ranking')

    const ranking = await screen.findByRole('list', { name: '전체 인기 순위' })
    await user.click(within(ranking).getByRole('link', { name: NOT_COLLECTED_TITLE }))

    expect(await screen.findByText(PENDING_NOTICE)).toBeInTheDocument()
  })

  it('주소로 바로 열면(state 없음) 기존 일반 404 안내다', async () => {
    renderApp('/titles/movie/910021')

    expect(await screen.findByText(GENERAL_NOT_FOUND)).toBeInTheDocument()
    expect(screen.queryByText(PENDING_NOTICE)).not.toBeInTheDocument()
  })

  it('인기 순위가 아닌 목록(찜 많은 작품)의 카드는 404여도 일반 안내다', async () => {
    const user = userEvent.setup()
    allDetailsNotFound()
    renderApp('/ranking?sort=wishlist')

    const wishlisted = await screen.findByRole('list', { name: '찜 많은 작품 순위' })
    await user.click(within(wishlisted).getAllByRole('link')[0])

    expect(await screen.findByText(GENERAL_NOT_FOUND)).toBeInTheDocument()
    expect(screen.queryByText(PENDING_NOTICE)).not.toBeInTheDocument()
  })
})
