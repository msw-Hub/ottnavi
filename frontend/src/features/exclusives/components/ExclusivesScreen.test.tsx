import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http as mswHttp, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { ExclusivesScreen } from '@/features/exclusives/components/ExclusivesScreen'
import { handlers } from '@/mocks/handlers'

/*
 * 서비스별 독점 목록 화면 테스트(Task 079). API는 앱의 목업 핸들러(MSW)로 대체하고, 사용자가 보는 글자 중심으로 확인한다.
 * 확인하는 것: 서비스 탭이 서비스 목록 API에서 오는지, 첫 쪽 24편 + 가로 줄 끝에 닿으면 다음 쪽이 이어 붙는지(실패 시 목록 유지 + 다시 시도),
 * 탭을 바꾸면 처음부터인지, 데이터 부족 서비스(쿠팡플레이)는 "드라마만 확인" 안내와 함께 목록이 보이는지,
 * 예전 응답(isJudged=false)이면 목록 대신 큰 안내인지, 빈·오류 상태, 출처·기준일 표기.
 * 막으려는 사고: 다음 쪽이 목록을 바꿔치기하는 것, 다음 쪽 실패로 받아 둔 목록이 사라지는 것, 출처·기준일·"확인된 범위" 표현 누락.
 */
const server = setupServer(...handlers)

// jsdom에는 IntersectionObserver(줄 끝 센티널이 보이는지 알려 주는 브라우저 기능)가 없다.
// 가짜를 넣어 두고, 테스트가 "줄 끝까지 넘겼다"를 흉내 낼 때 지금 살아 있는 관찰기들에 "보인다"를 알린다
const observerCallbacks = new Set<IntersectionObserverCallback>()
class FakeIntersectionObserver {
  private readonly callback: IntersectionObserverCallback
  constructor(callback: IntersectionObserverCallback) {
    this.callback = callback
  }
  observe() {
    observerCallbacks.add(this.callback)
  }
  disconnect() {
    observerCallbacks.delete(this.callback)
  }
  unobserve() {}
  takeRecords() {
    return []
  }
}

// 사용자가 줄을 오른쪽 끝 근처까지 넘긴 것처럼 센티널이 보였다고 알린다
function scrollRailToEnd() {
  act(() => {
    for (const callback of observerCallbacks) {
      callback([{ isIntersecting: true } as IntersectionObserverEntry], {} as IntersectionObserver)
    }
  })
}

beforeAll(() => server.listen({ onUnhandledFrame: 'error' })) // 핸들러 없는 요청이 실제 네트워크로 나가지 않게 막는다
beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true') // 포스터 data URI를 목업 모드에서만 통과시키는 규칙(getTmdbImageUrl) 때문에 켠다
  vi.stubGlobal('IntersectionObserver', FakeIntersectionObserver)
})
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  vi.unstubAllGlobals()
  observerCallbacks.clear()
})
afterAll(() => server.close())

// 지금 주소를 화면에 글자로 내보내는 작은 도구(테스트가 ?service= 값을 읽는다)
function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>
}

function renderScreen(path = '/exclusives') {
  const router = createMemoryRouter(
    [
      {
        path: '/exclusives',
        element: (
          <>
            <ExclusivesScreen />
            <LocationProbe />
          </>
        ),
      },
      // 카드를 눌러 상세로 갔는지 주소로 확인하려고 둔 자리(상세 화면 자체는 이 테스트의 관심사가 아니다)
      { path: '/titles/:mediaType/:tmdbId', element: <LocationProbe /> },
    ],
    { initialEntries: [path] },
  )
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
}

// 429(요청 제한) 응답. 재시도 없이 바로 오류 상태가 되는 가장 단순한 실패다
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

// 첫 쪽을 그리고, 줄과 첫 카드 링크를 돌려준다
async function renderFirstPage() {
  renderScreen()
  const list = await screen.findByRole('list', { name: '넷플릭스 독점 작품' })
  return { list, link: within(list).getAllByRole('link')[0] }
}

describe('서비스별 독점 목록', () => {
  it('확인된 범위·구조적 공백·출처·기준일을 보이고 첫 탭(넷플릭스)의 첫 쪽 24편을 보인다', async () => {
    renderScreen()

    expect(
      screen.getByRole('heading', { level: 2, name: '확인된 범위의 독점이에요' }),
    ).toBeInTheDocument()
    expect(screen.getByText('영화는 쿠팡플레이와 비교하지 않았어요.')).toBeInTheDocument()
    expect(screen.getByText(/제공처 정보 출처/)).toBeInTheDocument()

    const list = await screen.findByRole('list', { name: '넷플릭스 독점 작품' })
    expect(within(list).getAllByRole('article')).toHaveLength(24)
    // 요약 줄은 지금 받은 수가 아니라 전체 수다. 기준일은 응답의 dataAsOf다
    expect(screen.getByText(/독점으로 확인된 작품 30편/)).toBeInTheDocument()
    expect(await screen.findByText('2026년 10월 16일')).toBeInTheDocument()
  })

  it('줄 끝까지 넘기면 다음 쪽이 뒤에 이어 붙는다. 더보기 버튼과 불러온 편수 표시는 없다', async () => {
    renderScreen()

    const list = await screen.findByRole('list', { name: '넷플릭스 독점 작품' })
    const firstTitle = within(list).getAllByRole('heading', { level: 3 })[0].textContent
    expect(screen.queryByRole('button', { name: '더보기' })).not.toBeInTheDocument()
    expect(screen.queryByText(/\d+ \/ \d+편/)).not.toBeInTheDocument() // 불러온 편수 표시는 뺐다

    scrollRailToEnd()

    await waitFor(() => expect(within(list).getAllByRole('article')).toHaveLength(30))
    // 앞쪽 작품은 그대로 있고 뒤에 이어 붙었다
    expect(within(list).getAllByRole('heading', { level: 3 })[0].textContent).toBe(firstTitle)
  })

  it('카드마다 인기 순위가 낭독기용 글자로 붙고, 다음 쪽이 붙으면 순위가 25위부터 이어진다', async () => {
    renderScreen()

    const list = await screen.findByRole('list', { name: '넷플릭스 독점 작품' })
    const links = within(list).getAllByRole('link')
    // 화면의 큰 숫자는 장식(aria-hidden)이고, 제목 링크 이름이 "인기 1위 {제목}"으로 읽힌다
    expect(links[0]).toHaveAccessibleName(/^인기 1위 /)
    expect(links[23]).toHaveAccessibleName(/^인기 24위 /)
    // 카드 하단의 "다른 N곳" 확인 결과 줄은 2차안에서 뺐다
    expect(within(list).queryByText(/다른 \d+곳/)).not.toBeInTheDocument()

    scrollRailToEnd()

    await waitFor(() => expect(within(list).getAllByRole('link')).toHaveLength(30))
    expect(within(list).getAllByRole('link')[24]).toHaveAccessibleName(/^인기 25위 /)
  })

  it('다음 쪽 요청이 실패하면 받아 둔 목록은 두고 줄 끝의 다시 시도로 이어 받는다', async () => {
    const user = userEvent.setup()
    renderScreen()

    const list = await screen.findByRole('list', { name: '넷플릭스 독점 작품' })
    // 첫 쪽을 받은 뒤의 다음 요청 한 번만 실패시킨다
    server.use(
      mswHttp.get('/api/public/ott-services/:ottServiceId/exclusive-titles', rateLimited, {
        once: true,
      }),
    )
    scrollRailToEnd()

    expect(await screen.findByText('다음 작품을 불러오지 못했어요')).toBeInTheDocument()
    expect(within(list).getAllByRole('article')).toHaveLength(24)
    await user.click(screen.getByRole('button', { name: '다시 시도' }))
    await waitFor(() => expect(within(list).getAllByRole('article')).toHaveLength(30))
  })

  it('탭을 바꾸면 그 서비스의 첫 쪽부터 보이고 주소의 서비스 코드가 바뀐다', async () => {
    const user = userEvent.setup()
    renderScreen()

    await screen.findByRole('list', { name: '넷플릭스 독점 작품' })
    scrollRailToEnd()
    await waitFor(() =>
      expect(
        within(screen.getByRole('list', { name: '넷플릭스 독점 작품' })).getAllByRole('article'),
      ).toHaveLength(30),
    )

    await user.click(await screen.findByRole('tab', { name: '디즈니+' }))
    const disneyList = await screen.findByRole('list', { name: '디즈니+ 독점 작품' })
    expect(within(disneyList).getAllByRole('article')).toHaveLength(4)
    expect(screen.getByTestId('location')).toHaveTextContent('/exclusives?service=DISNEY_PLUS')
    // 다시 넷플릭스로 오면 이어 붙인 목록이 아니라 처음 쪽(24편)부터다
    await user.click(screen.getByRole('tab', { name: '넷플릭스' }))
    const netflixList = await screen.findByRole('list', { name: '넷플릭스 독점 작품' })
    expect(within(netflixList).getAllByRole('article')).toHaveLength(24)
    expect(screen.getByTestId('location')).toHaveTextContent('/exclusives?service=NETFLIX')
  })

  it('데이터 부족 서비스(쿠팡플레이)는 "드라마만 확인" 안내와 함께 드라마 독점 목록을 보인다', async () => {
    const user = userEvent.setup()
    renderScreen()

    await user.click(await screen.findByRole('tab', { name: /쿠팡플레이/ }))
    const list = await screen.findByRole('list', { name: '쿠팡플레이 독점 작품' })
    expect(within(list).getAllByRole('article')).toHaveLength(3)
    expect(
      screen.getByText('쿠팡플레이는 영화 제공 정보를 받지 못해 드라마만 확인했어요.'),
    ).toBeInTheDocument()
    expect(screen.getByText('데이터 부족')).toBeInTheDocument()
    // 한 쪽 안의 짧은 목록에는 "3 / 3편" 같은 불러온 편수를 붙이지 않는다
    expect(screen.queryByText(/\d+ \/ \d+편/)).not.toBeInTheDocument()
  })

  it('예전 응답처럼 서버가 판정하지 않았다고 답하면(isJudged=false) 목록 대신 큰 안내를 보인다', async () => {
    server.use(
      mswHttp.get('/api/public/ott-services/:ottServiceId/exclusive-titles', () =>
        HttpResponse.json({
          success: true,
          data: {
            content: [],
            page: 0,
            size: 24,
            totalElements: 0,
            totalPages: 0,
            ottServiceId: 7,
            isJudged: false,
            dataAsOf: '2026-10-16',
          },
        }),
      ),
    )
    const user = userEvent.setup()
    renderScreen()

    await user.click(await screen.findByRole('tab', { name: /쿠팡플레이/ }))
    expect(await screen.findByText('쿠팡플레이는 독점 여부를 판단하지 않아요')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: '쿠팡플레이 독점 작품' })).not.toBeInTheDocument()
  })

  it('다음 쪽을 불러오는 중에는 줄 끝에 불러오는 표시가 나온다', async () => {
    await renderFirstPage()
    // 다음 쪽 응답을 끝내 주지 않아 "받는 중" 상태를 붙잡아 둔다
    server.use(
      mswHttp.get('/api/public/ott-services/:ottServiceId/exclusive-titles', async () => {
        await delay('infinite')
        return HttpResponse.json({})
      }),
    )
    scrollRailToEnd()

    expect(await screen.findByText('다음 작품을 불러오고 있어요')).toBeInTheDocument()
  })

  describe('마우스 끌기', () => {
    it('임계값(5px) 안에서 움직인 뒤 놓으면 끌기가 아니라 클릭이라 카드 상세로 이동한다', async () => {
      const { list, link } = await renderFirstPage()

      fireEvent.pointerDown(list, { pointerType: 'mouse', button: 0, pointerId: 1, clientX: 100 })
      fireEvent.pointerMove(list, { pointerType: 'mouse', pointerId: 1, clientX: 97 })
      fireEvent.pointerUp(list, { pointerType: 'mouse', pointerId: 1, clientX: 97 })
      fireEvent.click(link)

      expect(list.scrollLeft).toBe(0) // 줄은 움직이지 않았다
      expect(screen.getByTestId('location')).toHaveTextContent(/^\/titles\//)
    })

    it('임계값을 넘겨 끌면 줄이 따라 움직이고, 놓은 직후의 click은 막혀 상세로 가지 않는다', async () => {
      const { list, link } = await renderFirstPage()
      list.scrollLeft = 300 // jsdom은 레이아웃이 없어 scrollLeft를 직접 읽고 쓴다

      fireEvent.pointerDown(list, { pointerType: 'mouse', button: 0, pointerId: 1, clientX: 200 })
      fireEvent.pointerMove(list, { pointerType: 'mouse', pointerId: 1, clientX: 150 })
      // 마우스가 왼쪽으로 50px 갔으니 줄은 그만큼 오른쪽으로 넘어간다(300 + 50)
      expect(list.scrollLeft).toBe(350)
      fireEvent.pointerUp(list, { pointerType: 'mouse', pointerId: 1, clientX: 150 })
      fireEvent.click(link)

      expect(screen.getByTestId('location')).toHaveTextContent('/exclusives')
      // 막는 것은 직후의 click 한 번뿐이다: 다음 턴 이후의 정상 클릭은 상세로 간다
      await act(async () => {
        await new Promise((resolve) => setTimeout(resolve, 10))
      })
      fireEvent.click(link)
      expect(screen.getByTestId('location')).toHaveTextContent(/^\/titles\//)
    })

    // jsdom에는 레이아웃이 없다. 줄의 폭(scrollWidth·clientWidth)과 카드 위치(getBoundingClientRect)를 지정해 끝 근처 계산을 재현한다.
    // 카드는 176px 간격(w-40 + gap-4)으로 늘어서 있고, 줄은 전체 3000px 중 1000px가 보인다(최대 스크롤 위치 2000)
    function setUpLayout(list: HTMLElement) {
      Object.defineProperty(list, 'scrollWidth', { configurable: true, value: 3000 })
      Object.defineProperty(list, 'clientWidth', { configurable: true, value: 1000 })
      list.getBoundingClientRect = () => ({ left: 0 }) as DOMRect
      within(list)
        .getAllByRole('article')
        .forEach((article, index) => {
          const item = article.closest('li') as HTMLElement
          // 카드 왼쪽 = 줄 안 위치(index*176 + 안쪽 여백 16) - 지금 스크롤 위치
          item.getBoundingClientRect = () =>
            ({ left: index * 176 + 16 - list.scrollLeft }) as DOMRect
        })
    }

    it('끝 근처에서 놓아도 줄이 뒤로 되돌아가지 않고 최대 스크롤 위치에 머문다', async () => {
      const { list } = await renderFirstPage()
      setUpLayout(list)
      list.scrollLeft = 1990 // 최대 위치(2000) 직전. 앞 카드 경계(1936)가 가까워 보이는 자리다

      fireEvent.pointerDown(list, { pointerType: 'mouse', button: 0, pointerId: 1, clientX: 100 })
      fireEvent.pointerMove(list, { pointerType: 'mouse', pointerId: 1, clientX: 90 }) // 줄이 10px 더 넘어가 2000
      fireEvent.pointerUp(list, { pointerType: 'mouse', pointerId: 1, clientX: 90 })

      expect(list.scrollLeft).toBe(2000) // 자르지 않던 때는 1936으로 되돌아갔다
    })

    it('끝에 닿으면(소수 오차 포함) 오른쪽 화살표가 비활성이 되고, 되돌아오면 다시 활성이 된다', async () => {
      const { list } = await renderFirstPage()
      setUpLayout(list)
      const nextButton = screen.getByRole('button', { name: '다음 작품 보기' })

      list.scrollLeft = 500
      fireEvent.scroll(list)
      expect(nextButton).toBeEnabled()

      list.scrollLeft = 1998.5 // 끝에서 1.5px 모자란 소수 위치도 끝으로 본다
      fireEvent.scroll(list)
      expect(nextButton).toBeDisabled()

      list.scrollLeft = 1900
      fireEvent.scroll(list)
      expect(nextButton).toBeEnabled()
    })

    it('터치 포인터는 직접 처리하지 않는다(브라우저 기본 스와이프에 맡긴다)', async () => {
      const { list } = await renderFirstPage()
      list.scrollLeft = 300

      fireEvent.pointerDown(list, { pointerType: 'touch', button: 0, pointerId: 2, clientX: 200 })
      fireEvent.pointerMove(list, { pointerType: 'touch', pointerId: 2, clientX: 100 })

      expect(list.scrollLeft).toBe(300)
    })
  })

  it('주소의 ?service= 코드로 해당 탭이 열린다', async () => {
    renderScreen('/exclusives?service=WATCHA')

    expect(await screen.findByRole('list', { name: '왓챠 독점 작품' })).toBeInTheDocument()
    expect(await screen.findByRole('tab', { name: '왓챠' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
  })

  it('판정은 했지만 독점 작품이 없으면 빈 상태 문구를 보인다', async () => {
    server.use(
      mswHttp.get('/api/public/ott-services/:ottServiceId/exclusive-titles', () =>
        HttpResponse.json({
          success: true,
          data: {
            content: [],
            page: 0,
            size: 24,
            totalElements: 0,
            totalPages: 0,
            ottServiceId: 6,
            isJudged: true,
            dataAsOf: '2026-10-16',
          },
        }),
      ),
    )
    renderScreen('/exclusives?service=WATCHA')

    expect(await screen.findByText('왓챠 독점으로 확인된 작품이 아직 없어요')).toBeInTheDocument()
  })

  it('독점 목록을 받지 못하면 오류 안내와 다시 시도가 보이고, 다시 시도하면 목록이 나온다', async () => {
    const user = userEvent.setup()
    server.use(
      mswHttp.get('/api/public/ott-services/:ottServiceId/exclusive-titles', rateLimited, {
        once: true,
      }),
    )
    renderScreen()

    expect(await screen.findByText('독점 작품을 불러오지 못했어요')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('list', { name: '넷플릭스 독점 작품' })).toBeInTheDocument()
  })
})
