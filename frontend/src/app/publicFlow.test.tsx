import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http as mswHttp, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { Providers } from '@/app/providers'
import { RootLayout } from '@/app/RootLayout'
import { SearchScreen } from '@/features/search/components/SearchScreen'
import { TitleDetailScreen } from '@/features/title-detail/components/TitleDetailScreen'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { TITLE_FIXTURES } from '@/mocks/fixtures/titles'
import { resetMockSession } from '@/mocks/mockSession'

/*
 * 비로그인 공개 흐름 테스트(Task 052): 헤더 검색창 → 검색 결과 → 상세, 429 안내, 상태 배지.
 * 화면 하나가 아니라 실제 라우터(app/router)를 통째로 열어, 사용자가 누르는 순서대로 확인한다.
 * API는 앱의 목업 핸들러(MSW)로 대체하고, 429처럼 목업 시나리오(요청 헤더)로 만들 수 없는 상황은 server.use로 직접 지정한다.
 */

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' })) // 핸들러 없는 요청이 실제 네트워크로 나가지 않게 막는다
afterEach(() => {
  server.resetHandlers()
  resetMockSession()
  resetMockWishlist()
})
afterAll(() => server.close())

// 실제 앱과 같은 공통 틀(RootLayout: 헤더 검색창·푸터)과 Providers로 지정한 주소를 연다.
// app/router의 routes를 그대로 쓰지 않는 이유: 그 라우트는 페이지를 lazy로 불러오고, React Router가 lazy 라우트를 처음 열 때 객체를 직접 고쳐
// 같은 routes로 만든 두 번째 라우터는 "No route matches"가 난다(router.test.tsx 주석의 관찰). 그래서 같은 화면 컴포넌트를 테스트마다 새 라우트로 묶는다
function renderAppAt(url: string) {
  const router = createMemoryRouter(
    [
      {
        element: <RootLayout />,
        children: [
          // 홈 1차안: 검색 화면 주소가 `/`에서 `/search`로 바뀌어 맞췄다(이전: path: '/')
          { path: '/search', element: <SearchScreen /> },
          { path: '/titles/:mediaType/:tmdbId', element: <TitleDetailScreen /> },
        ],
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

// 백엔드 요청 제한 응답과 같은 모양(problem+json, errorCode RATE_LIMIT_EXCEEDED)
function rateLimited() {
  return HttpResponse.json(
    {
      title: 'Too Many Requests',
      status: 429,
      detail: '요청이 너무 많습니다.',
      errorCode: 'RATE_LIMIT_EXCEEDED',
    },
    { status: 429, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

describe('비로그인 검색 → 상세 흐름', () => {
  it('헤더 검색창에 제목을 입력하면 결과가 나오고, 결과를 누르면 그 작품의 상세가 열린다', async () => {
    const user = userEvent.setup()
    renderAppAt('/search')

    // 1. 검색어를 입력하고 제출한다
    await user.type(await screen.findByRole('searchbox', { name: '작품 제목 검색' }), '기생충')
    await user.click(screen.getByRole('button', { name: '검색' }))

    // 2. 결과와 출처·기준일이 보인다
    expect(
      await screen.findByRole('heading', { level: 2, name: /기생충.*검색 결과 1건/ }),
    ).toBeInTheDocument()
    expect(screen.getAllByText(/제공처 정보 출처/).length).toBeGreaterThan(0)
    expect(screen.getByText(/데이터 기준일/)).toBeInTheDocument()

    // 3. 결과의 제목 링크를 눌러 상세로 간다
    await user.click(screen.getByRole('link', { name: /기생충/ }))
    expect(await screen.findByRole('heading', { level: 1, name: '기생충' })).toBeInTheDocument()
  })

  it('검색 결과가 없으면 아직 수집되지 않은 작품은 검색되지 않을 수 있다는 안내가 보인다', async () => {
    renderAppAt(`/search?q=${encodeURIComponent('없는작품제목')}`)

    expect(
      await screen.findByText(/아직 수집되지 않은 작품은 검색되지 않을 수 있습니다/),
    ).toBeInTheDocument()
  })
})

describe('요청 제한(429) 안내', () => {
  it('검색이 429를 받으면 "잠시 후 다시 시도" 안내가 나오고, 다시 시도를 누르면 결과가 나온다', async () => {
    const user = userEvent.setup()
    let isFirstCall = true
    server.use(
      mswHttp.get('/api/public/titles', () => {
        // 첫 요청만 제한에 걸리고 다음 요청부터는 목업 핸들러(정상 응답)로 넘어간다
        if (isFirstCall) {
          isFirstCall = false
          return rateLimited()
        }
      }),
    )
    renderAppAt(`/search?q=${encodeURIComponent('기생충')}`)

    const alert = await screen.findByRole('alert')
    expect(
      within(alert).getByText(/요청이 많습니다. 잠시 후 다시 시도해 주세요/),
    ).toBeInTheDocument()

    await user.click(within(alert).getByRole('button', { name: '다시 시도' }))
    expect(
      await screen.findByRole('heading', { level: 2, name: /검색 결과 1건/ }),
    ).toBeInTheDocument()
  })

  it('상세가 429를 받아도 같은 안내와 다시 시도 버튼이 나온다', async () => {
    server.use(mswHttp.get('/api/public/titles/:mediaType/:tmdbId', () => rateLimited()))
    renderAppAt('/titles/movie/496243')

    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText(/잠시 후 다시 시도해 주세요/)).toBeInTheDocument()
    expect(within(alert).getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })
})

describe('제공 상태 배지', () => {
  it('상세의 서비스별 있음/없음/모름 글자가 응답 값과 같다', async () => {
    const user = userEvent.setup()
    const base = TITLE_FIXTURES.find((t) => t.title === '기생충')!
    // 응답 값을 일부러 세 가지로 바꿔 화면이 응답을 그대로 따르는지 본다(나머지 서비스는 모름)
    const [first, second, third, ...rest] = base.availabilities
    server.use(
      mswHttp.get('/api/public/titles/:mediaType/:tmdbId', () =>
        HttpResponse.json({
          success: true,
          data: {
            ...base,
            availabilities: [
              { ...first, status: 'AVAILABLE' },
              { ...second, status: 'NOT_AVAILABLE' },
              { ...third, status: 'UNKNOWN' },
              ...rest.map((a) => ({ ...a, status: 'UNKNOWN' })),
            ],
          },
        }),
      ),
    )
    renderAppAt(`/titles/${base.mediaType}/${base.tmdbId}`)

    await screen.findByRole('heading', { level: 1, name: '기생충' })
    await user.click(screen.getByText('서비스별 자세히 보기'))
    const table = within(screen.getByText('서비스별 자세히 보기').closest('details')!)
    expect(table.getByText('넷플릭스').closest('li')).toHaveTextContent('있음')
    expect(table.getByText('디즈니+').closest('li')).toHaveTextContent('없음')
    expect(table.getByText('애플 TV+').closest('li')).toHaveTextContent('모름')
  })
})
