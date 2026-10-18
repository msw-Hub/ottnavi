import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { ExploreScreen } from '@/features/home/components/ExploreScreen'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { resetMockMemberState } from '@/mocks/memberHandlers'
import { resetMockSession } from '@/mocks/mockSession'

/*
 * 탐색 화면 테스트(Task 108). API는 앱의 목업 핸들러(MSW)로 대체한다.
 * 확인하는 것: 장르 칩이 장르 목록 API(TMDB 장르)에서 오고, 칩을 누르면 장르별 작품 API 결과로 바뀌는지,
 * 제목 검색이 작품 검색 API를 쓰는지, 고른 값이 주소에 남는지.
 * 막으려는 사고: 예전 화면용 장르 묶음(샘플)으로 되돌아가는 것, 장르와 검색어가 동시에 눌린 채 한쪽만 적용되는 것.
 */
const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true') // 로그인 상태를 목업 역할로 읽게 한다(useSession)
})
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockSession() // 로그인 역할은 모듈 변수라 테스트마다 비로그인으로 되돌린다
  resetMockMemberState()
  resetMockWishlist()
})
afterAll(() => server.close())

// 지금 주소를 화면에 글자로 내보내는 작은 도구(테스트가 ?genre= 같은 주소 값을 읽는다)
function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>
}

function renderExplore(url = '/explore') {
  const router = createMemoryRouter(
    [
      {
        path: '/explore',
        element: (
          <>
            <ExploreScreen />
            <LocationProbe />
          </>
        ),
      },
    ],
    { initialEntries: [url] },
  )
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
}

describe('탐색 화면', () => {
  it('아무것도 고르지 않으면 인기 순위 작품이 보이고, 장르 칩은 장르 목록 API에서 온다', async () => {
    renderExplore()

    const list = await screen.findByRole('list', { name: '탐색 결과' })
    expect(within(list).getAllByRole('article').length).toBeGreaterThan(0)
    const genreGroup = screen.getByRole('group', { name: '장르' })
    expect(await within(genreGroup).findByRole('button', { name: '미스터리' })).toBeInTheDocument()
    expect(within(genreGroup).getByRole('button', { name: '전체 장르' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })

  it('장르 칩을 누르면 그 장르 작품만 보이고 주소에 장르 ID가 남는다', async () => {
    const user = userEvent.setup()
    renderExplore()

    const genreGroup = screen.getByRole('group', { name: '장르' })
    await user.click(await within(genreGroup).findByRole('button', { name: '범죄' }))

    expect(screen.getByTestId('location')).toHaveTextContent('genre=80')
    expect(within(genreGroup).getByRole('button', { name: '범죄' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    // 상세 목업에서 '범죄' 장르인 작품만 남는다(목업 핸들러가 장르 이름으로 거른다)
    await waitFor(() => {
      const list = screen.getByRole('list', { name: '탐색 결과' })
      expect(within(list).getAllByRole('article')).toHaveLength(2)
    })
  })

  it('제목을 검색하면 작품 검색 결과가 보이고, 장르 선택은 풀린다', async () => {
    const user = userEvent.setup()
    renderExplore('/explore?genre=80')

    await screen.findByRole('list', { name: '탐색 결과' })
    await user.type(screen.getByRole('searchbox', { name: '작품 제목' }), '카지노')
    await user.click(screen.getByRole('button', { name: '찾기' }))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('q='))
    expect(screen.getByTestId('location')).not.toHaveTextContent('genre=')
    await waitFor(() => {
      const list = screen.getByRole('list', { name: '탐색 결과' })
      expect(within(list).getAllByRole('article')).toHaveLength(2) // 같은 제목의 영화와 드라마
    })
  })

  it('"새로 볼 수 있게 된 작품만"을 누르면 새 작품 목록으로 바뀐다', async () => {
    const user = userEvent.setup()
    renderExplore()

    await screen.findByRole('list', { name: '탐색 결과' })
    await user.click(screen.getByRole('button', { name: '새로 볼 수 있게 된 작품만' }))
    expect(screen.getByTestId('location')).toHaveTextContent('view=new')
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '새로 볼 수 있게 된 작품만' })).toHaveAttribute(
        'aria-pressed',
        'true',
      ),
    )
    expect(await screen.findByRole('list', { name: '탐색 결과' })).toBeInTheDocument()
  })
})
