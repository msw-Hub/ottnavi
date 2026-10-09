import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http as mswHttp, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { Providers } from '@/app/providers'
import { SearchScreen } from '@/features/search/components/SearchScreen'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { TITLE_FIXTURES } from '@/mocks/fixtures/titles'
import { setMockRole } from '@/mocks/mockSession'

/*
 * 검색 화면 테스트(Task 027). 사용자가 보는 글자 중심으로 확인하고, API는 앱의 목업 핸들러(MSW)로 대체한다.
 * 주소(?q=)에서 검색어를 읽는 화면이라 메모리 라우터에 검색어가 붙은 주소를 주고 시작한다.
 * 찜은 상세 화면에서만 하므로(카드에 찜 버튼 없음) 찜 동작 테스트는 title-detail 쪽에 있다.
 */

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' })) // 핸들러 없는 요청이 실제 네트워크로 나가지 않게 막는다
afterEach(() => {
  server.resetHandlers() // 테스트 안에서 server.use로 덧붙인 핸들러를 지운다
  setMockRole('GUEST') // 로그인 역할은 모듈 변수라 테스트마다 비로그인으로 되돌린다
  resetMockWishlist()
})
afterAll(() => server.close())

// 지정한 주소로 검색 화면을 연다. 실제 앱과 같은 Providers(QueryClient·토스트)로 감싼다
function renderSearchAt(url: string) {
  const router = createMemoryRouter([{ path: '/', element: <SearchScreen /> }], {
    initialEntries: [url],
  })
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
  return router // 테스트가 검색어(주소)를 바꿀 때 쓴다
}

// 작품 total개를 가진 가짜 검색 서버. page·size를 받아 api-contract.md 페이지 형태로 잘라 돌려준다(검색어는 제목에 붙인다)
function serveFakePagedTitles(total: number) {
  server.use(
    mswHttp.get('/api/public/titles', ({ request }) => {
      const params = new URL(request.url).searchParams
      const page = Number(params.get('page') ?? 0)
      const size = Number(params.get('size') ?? 20)
      const q = params.get('q')
      const all = Array.from({ length: total }, (_, i) => ({
        mediaType: 'movie',
        tmdbId: i + 1,
        title: `${q} 작품 ${i + 1}`,
        releaseYear: 2000,
        posterUrl: null,
        availabilities: [],
      }))
      return HttpResponse.json({
        success: true,
        data: {
          content: all.slice(page * size, (page + 1) * size),
          page,
          size,
          totalElements: total,
          totalPages: Math.ceil(total / size),
          isExternalCollectFailed: false,
          dataAsOf: '2026-10-08',
        },
      })
    }),
  )
}

describe('검색 화면', () => {
  it('검색어가 없으면 요청 없이 안내만 보인다', () => {
    renderSearchAt('/')
    expect(screen.getByRole('heading', { level: 1, name: '작품 검색' })).toBeInTheDocument()
    expect(screen.getByText(/제목으로 검색해 보세요/)).toBeInTheDocument()
  })

  it('제목이 같은 영화와 드라마가 각각 구분되어 나오고, 출처와 기준일이 보인다', async () => {
    renderSearchAt(`/?q=${encodeURIComponent('카지노')}`)

    expect(
      await screen.findByRole('heading', { level: 2, name: /검색 결과 2건/ }),
    ).toBeInTheDocument()
    const cards = screen.getAllByRole('article')
    expect(cards).toHaveLength(2)

    // 한쪽은 영화(1995), 다른 쪽은 드라마(2022)다. 보조 글씨는 "연도 · 구분"이다
    expect(cards.find((card) => within(card).queryByText('1995 · 영화'))).toBeDefined()
    expect(cards.find((card) => within(card).queryByText('2022 · 드라마'))).toBeDefined()

    // 제공처 정보가 보이는 화면이라 JustWatch 출처와 데이터 기준일이 함께 나온다
    expect(screen.getByRole('link', { name: /JustWatch/ })).toBeInTheDocument()
    expect(screen.getByText(/데이터 기준일/)).toBeInTheDocument()
  })

  it('쿠팡플레이 데이터 부족 안내는 결과 위에 한 번만 나오고 카드마다 반복되지 않는다', async () => {
    renderSearchAt(`/?q=${encodeURIComponent('카지노')}`)

    await screen.findByRole('heading', { level: 2, name: /검색 결과 2건/ })
    expect(
      screen.getAllByText(/데이터가 부족해 제공 여부를 확인하지 못할 수 있습니다/),
    ).toHaveLength(1)
    expect(screen.getAllByText('데이터 부족')).toHaveLength(1)
    for (const card of screen.getAllByRole('article')) {
      expect(within(card).queryByText(/데이터 부족/)).not.toBeInTheDocument()
      expect(within(card).queryByText(/모름 \d+곳/)).not.toBeInTheDocument()
    }
  })

  it('칩은 최대 2개까지 이름으로 보이고 나머지는 "+N"으로 줄어든다', async () => {
    renderSearchAt(`/?q=${encodeURIComponent('기생충')}`)

    // 기생충: 티빙·웨이브·왓챠 있음 → 앞의 둘만 칩, 나머지 하나는 +1
    const card = (await screen.findByRole('article')) as HTMLElement
    const chips = within(within(card).getByRole('list', { name: '볼 수 있는 곳' }))
    expect(chips.getAllByRole('listitem').map((chip) => chip.textContent)).toEqual([
      '티빙',
      '웨이브',
      '+1',
    ])
    expect(within(card).queryByText('왓챠')).not.toBeInTheDocument()
    expect(within(card).queryByText('넷플릭스')).not.toBeInTheDocument()
    expect(within(card).queryByText(/시즌/)).not.toBeInTheDocument()
  })

  it('확인된 제공처가 없으면 칩 대신 "확인된 제공처가 없습니다" 글자가 보인다', async () => {
    const allNotAvailable = [1, 2, 3, 4, 5, 6, 7].map((ottServiceId) => ({
      ottServiceId,
      status: 'NOT_AVAILABLE',
    }))
    server.use(
      mswHttp.get('/api/public/titles', () =>
        HttpResponse.json({
          success: true,
          data: {
            content: [
              {
                mediaType: 'movie',
                tmdbId: 1,
                title: '제공처 없는 작품',
                releaseYear: 2000,
                posterUrl: null,
                availabilities: allNotAvailable,
              },
            ],
            page: 0,
            size: 20,
            totalElements: 1,
            totalPages: 1,
            isExternalCollectFailed: false,
            dataAsOf: '2026-10-08',
          },
        }),
      ),
    )
    renderSearchAt(`/?q=${encodeURIComponent('제공처')}`)

    const card = (await screen.findByRole('article')) as HTMLElement
    expect(within(card).getByText('확인된 제공처가 없습니다')).toBeInTheDocument()
    expect(within(card).queryByRole('list')).not.toBeInTheDocument()
  })

  it('영화·드라마 카드 모두 찜 버튼이 없고, 제목 링크 하나가 상세(영화/드라마 구분 포함)로 이어진다', async () => {
    setMockRole('USER') // 회원이어도 카드에는 찜 버튼이 없다
    const drama = TITLE_FIXTURES.find((t) => t.title === '카지노' && t.mediaType === 'tv')!
    renderSearchAt(`/?q=${encodeURIComponent('카지노')}`)

    await screen.findByRole('heading', { level: 2, name: /검색 결과 2건/ })
    expect(screen.queryByRole('button')).not.toBeInTheDocument()

    const hrefs = screen.getAllByRole('article').map((card) => {
      // 카드의 링크는 제목 링크 하나뿐이다(포스터·칩에 링크를 따로 두지 않는다)
      const links = within(card).getAllByRole('link')
      expect(links).toHaveLength(1)
      expect(links[0]).toHaveTextContent('카지노')
      return links[0].getAttribute('href')
    })
    expect(hrefs).toContain(`/titles/tv/${drama.tmdbId}`)
    expect(hrefs.some((href) => href?.startsWith('/titles/movie/'))).toBe(true)
  })

  it('검색 결과가 없으면 빈 상태 안내가 보인다', async () => {
    renderSearchAt(`/?q=${encodeURIComponent('없는작품제목')}`)

    expect(await screen.findByText('검색 결과가 없습니다')).toBeInTheDocument()
    expect(screen.queryByRole('article')).not.toBeInTheDocument()
  })

  it('외부 수집이 실패하면 저장된 결과만 보여 준다는 안내가 나온다', async () => {
    // 목업 시나리오(?mockScenario=collect-failed)는 목업 모드에서만 요청에 실리므로, 테스트에서는 같은 응답을 직접 지정한다
    server.use(
      mswHttp.get('/api/public/titles', () =>
        HttpResponse.json({
          success: true,
          data: {
            content: [],
            page: 0,
            size: 20,
            totalElements: 0,
            totalPages: 0,
            isExternalCollectFailed: true,
            dataAsOf: '2026-10-08',
          },
        }),
      ),
    )
    renderSearchAt(`/?q=${encodeURIComponent('카지노')}`)

    expect(await screen.findByText(/저장된 결과만 보여 드립니다/)).toBeInTheDocument()
  })
})

describe('검색 화면의 더보기', () => {
  const MORE_BUTTON = { name: '검색 결과 더보기' }

  it('첫 페이지에 결과가 다 들어오면 더보기 버튼이 없다', async () => {
    renderSearchAt(`/?q=${encodeURIComponent('카지노')}`)

    await screen.findByRole('heading', { level: 2, name: /검색 결과 2건/ })
    expect(screen.queryByRole('button', MORE_BUTTON)).not.toBeInTheDocument()
  })

  it('결과가 한 번에 가져오는 개수보다 많으면 전체 건수가 보이고, 더보기를 누를 때마다 이어 붙다가 마지막에 버튼이 사라진다', async () => {
    serveFakePagedTitles(25) // 기본 size 10 → 10 + 10 + 5
    const user = userEvent.setup()
    renderSearchAt(`/?q=${encodeURIComponent('테스트')}`)

    expect(
      await screen.findByRole('heading', { level: 2, name: /검색 결과 25건/ }),
    ).toBeInTheDocument()
    expect(screen.getAllByRole('article')).toHaveLength(10)

    await user.click(screen.getByRole('button', MORE_BUTTON))
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(20))
    // 이어 붙이기라 첫 페이지 작품도 그대로 남아 있다
    expect(screen.getByText('테스트 작품 1')).toBeInTheDocument()
    expect(screen.getByRole('button', MORE_BUTTON)).toBeInTheDocument()

    await user.click(screen.getByRole('button', MORE_BUTTON))
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(25))
    expect(screen.queryByRole('button', MORE_BUTTON)).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: /검색 결과 25건/ })).toBeInTheDocument()
  })

  it('검색어를 바꾸면 이어 붙인 목록이 아니라 처음부터 다시 시작한다', async () => {
    serveFakePagedTitles(25)
    const user = userEvent.setup()
    const router = renderSearchAt(`/?q=${encodeURIComponent('가')}`)

    await screen.findByText('가 작품 1')
    await user.click(screen.getByRole('button', MORE_BUTTON))
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(20))

    await router.navigate(`/?q=${encodeURIComponent('나')}`)

    expect(await screen.findByText('나 작품 1')).toBeInTheDocument()
    expect(screen.getAllByRole('article')).toHaveLength(10)
    expect(screen.queryByText('가 작품 1')).not.toBeInTheDocument()
    expect(screen.getByRole('button', MORE_BUTTON)).toBeInTheDocument()
  })

  it('더보기가 실패하면 불러온 목록은 남기고 오류 안내와 다시 시도가 나온다', async () => {
    serveFakePagedTitles(25)
    const user = userEvent.setup()
    renderSearchAt(`/?q=${encodeURIComponent('테스트')}`)
    await screen.findByText('테스트 작품 1')

    // 두 번째 페이지 요청만 실패시킨다(앞서 지정한 핸들러보다 먼저 적용된다). 4xx는 자동 재시도를 하지 않아 바로 오류가 보인다
    server.use(
      mswHttp.get(
        '/api/public/titles',
        () =>
          HttpResponse.json(
            { title: '오류', status: 400, errorCode: 'VALIDATION_FAILED' },
            { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
          ),
        { once: true },
      ),
    )
    await user.click(screen.getByRole('button', MORE_BUTTON))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getAllByRole('article')).toHaveLength(10)

    await user.click(screen.getByRole('button', { name: '다시 시도' }))
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(20))
  })
})
