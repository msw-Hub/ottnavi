import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http as mswHttp, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { HomeScreen } from '@/features/home/components/HomeScreen'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { resetMockMemberState } from '@/mocks/memberHandlers'
import { resetMockSession, setMockRole } from '@/mocks/mockSession'

/*
 * 홈 화면 테스트(Task 108). API는 앱의 목업 핸들러(MSW)로 대체하고, 사용자가 보는 글자 중심으로 확인한다.
 * 확인하는 것: 처음 온 사람에게 가치 제안·시작 버튼과 목록 3줄·출처·기준일이 보이는지, 순위 숫자가 낭독기에도 "인기 1위"로 읽히는지,
 * 줄마다 로딩/빈/오류 상태가 따로 동작하는지(한 줄이 실패해도 다른 줄은 보임), 카드 찜이 찜 API로 저장되는지.
 * 막으려는 사고: 레이아웃을 바꾸다 출처 표기·기준일이 빠지는 것, 빈 찜 줄이 빈 상자로 남는 것, 오류 한 줄이 홈 전체를 지우는 것.
 */
const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' })) // 핸들러 없는 요청이 실제 네트워크로 나가지 않게 막는다
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

function renderHome() {
  const router = createMemoryRouter([{ path: '/', element: <HomeScreen /> }], {
    initialEntries: ['/'],
  })
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
}

// 429(요청 제한) 응답. 재시도 없이 바로 오류 상태가 되는 가장 단순한 실패다(500은 자동 재시도로 오래 걸린다)
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

describe('홈 화면', () => {
  it('비로그인이면 가치 제안·시작 버튼과 목록 3줄, 순위 숫자 읽기, 출처·기준일이 보인다', async () => {
    renderHome()

    expect(
      screen.getByRole('heading', {
        level: 1,
        name: '찜한 작품, 어느 달에 어디서 볼지 계산해 드려요',
      }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '무료로 플랜 계산 시작하기' })).toHaveAttribute(
      'href',
      '/login',
    )

    for (const heading of [
      '서비스별 인기 순위',
      '찜이 많은 작품',
      '최근 새로 볼 수 있게 된 작품',
    ]) {
      expect(await screen.findByRole('heading', { level: 2, name: heading })).toBeInTheDocument()
    }
    // 독점 줄은 독점 API(Task 078) 전까지 그리지 않는다
    expect(screen.queryByRole('heading', { name: '서비스 독점 작품' })).not.toBeInTheDocument()

    // 순위 숫자는 장식이 아니라 글자로 읽힌다
    const ranking = await screen.findByRole('list', { name: '전체 인기 순위' })
    expect(
      within(ranking).getByText(
        (_, element) => element?.tagName === 'P' && element.textContent === '인기 1위',
      ),
    ).toBeInTheDocument()
    // 서버가 준 기준 안내가 그대로 보인다
    expect(screen.getByText(/TMDB 인기도 기준, OTT 공식 순위 아님 · 오늘/)).toBeInTheDocument()
    // 출처·기준일: 목록 3줄마다 한 줄씩
    await waitFor(() =>
      expect(screen.getAllByText(/제공처 정보 출처/).length).toBeGreaterThanOrEqual(3),
    )
    expect(screen.getAllByText(/데이터 기준일/).length).toBeGreaterThanOrEqual(3)
  })

  it('인기 순위 기간을 최근 7일로 바꾸면 기준 문구에 며칠치인지 밝히고 순위가 바뀐다', async () => {
    const user = userEvent.setup()
    renderHome()

    const todayRanking = await screen.findByRole('list', { name: '전체 인기 순위' })
    const todayFirst = within(todayRanking).getAllByRole('heading', { level: 3 })[0].textContent

    await user.click(screen.getByRole('button', { name: '최근 7일' }))

    expect(await screen.findByText(/최근 7일 평균\(3일치\)/)).toBeInTheDocument()
    await waitFor(() => {
      const weekRanking = screen.getByRole('list', { name: '전체 인기 순위' })
      expect(within(weekRanking).getAllByRole('heading', { level: 3 })[0].textContent).not.toBe(
        todayFirst,
      )
    })
  })

  it('쿠팡플레이 탭을 고르면 데이터 부족 안내가 나온다', async () => {
    const user = userEvent.setup()
    renderHome()

    const tabs = await screen.findByRole('tablist', { name: '인기 순위 서비스 선택' })
    await user.click(within(tabs).getByRole('tab', { name: '쿠팡플레이' }))
    expect(await screen.findByText(/영화는 순위에서 빠지고/)).toBeInTheDocument()
  })

  it('우리 DB에 없는 작품 카드는 제공 상태를 단정하지 않고 "제공 정보 수집 전"으로 보인다', async () => {
    renderHome()
    const ranking = await screen.findByRole('list', { name: '전체 인기 순위' })
    expect(within(ranking).getAllByText('제공 정보 수집 전').length).toBeGreaterThan(0)
  })

  it('찜 많은 작품이 빈 목록이면 그 줄을 숨기고, 인기 순위가 비면 안내를 보인다', async () => {
    server.use(
      mswHttp.get('/api/public/wishlisted-titles', () =>
        HttpResponse.json({
          success: true,
          data: { minUserCount: 3, dataAsOf: '2026-10-08', items: [] },
        }),
      ),
      mswHttp.get('/api/public/popularity-rankings', () =>
        HttpResponse.json({
          success: true,
          data: {
            period: 'TODAY',
            ottServiceId: null,
            snapshotDate: null,
            daysCounted: 0,
            notice: 'TMDB 인기도 기준, OTT 공식 순위 아님',
            items: [],
          },
        }),
      ),
    )
    renderHome()

    expect(await screen.findByText('아직 집계된 인기 순위가 없습니다')).toBeInTheDocument()
    // 다른 줄이 다 받아진 뒤에도 찜 줄은 그려지지 않는다
    await screen.findByRole('heading', { level: 2, name: '최근 새로 볼 수 있게 된 작품' })
    expect(screen.queryByRole('heading', { name: '찜이 많은 작품' })).not.toBeInTheDocument()
  })

  it('한 줄이 요청 제한(429)으로 실패해도 오류 안내와 다시 시도가 보이고 다른 줄은 그대로다', async () => {
    server.use(mswHttp.get('/api/public/newly-available-titles', rateLimited))
    renderHome()

    expect(
      await screen.findByText('최근 새로 볼 수 있게 된 작품을 불러오지 못했습니다'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
    // 인기 순위와 찜 줄은 정상으로 보인다
    expect(await screen.findByRole('list', { name: '전체 인기 순위' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: '찜이 많은 작품' })).toBeInTheDocument()
  })

  it('새로 볼 수 있게 된 작품은 입고(확인)와 공개를 구분해 보인다', async () => {
    renderHome()
    const section = (
      await screen.findByRole('heading', { level: 2, name: '최근 새로 볼 수 있게 된 작품' })
    ).closest('section') as HTMLElement
    expect((await within(section).findAllByText(/월 \d+일 확인/)).length).toBeGreaterThan(0)
    expect(within(section).getAllByText(/월 \d+일 공개/).length).toBeGreaterThan(0)
  })

  it('비로그인이면 카드의 찜 버튼이 로그인 화면으로 가는 링크다', async () => {
    renderHome()
    const ranking = await screen.findByRole('list', { name: '전체 인기 순위' })
    expect(
      within(ranking).getAllByRole('link', { name: /찜하기 \(로그인 필요\)/ })[0],
    ).toHaveAttribute('href', '/login')
  })

  it('회원이면 영화 카드 찜이 찜 API로 저장·해제되고, 드라마는 상세로 보낸다', async () => {
    setMockRole('USER')
    const user = userEvent.setup()
    renderHome()

    const ranking = await screen.findByRole('list', { name: '전체 인기 순위' })
    // 드라마는 시즌을 골라야 해서 버튼이 아니라 상세로 가는 링크다
    expect(
      within(ranking)
        .getAllByRole('link', { name: /시즌 골라 찜하기/ })[0]
        .getAttribute('href'),
    ).toMatch(/^\/titles\/tv\/\d+$/)

    // 영화: 찜 목록을 받은 뒤(버튼이 활성화된 뒤) 누르면 찜한 상태가 되고, 다시 누르면 해제된다
    // (우리 DB에 없는 "수집 전" 영화는 찜 대상이 아니라 건너뛴다)
    const heart = within(ranking)
      .getAllByRole('button', { name: /찜하기$/ })
      .find((button) => !button.getAttribute('aria-label')?.includes('수집 전'))!
    await waitFor(() => expect(heart).toBeEnabled())
    expect(heart).toHaveAttribute('aria-pressed', 'false')
    await user.click(heart)
    await waitFor(() => expect(heart).toHaveAttribute('aria-pressed', 'true'))
    await waitFor(() => expect(heart).toBeEnabled())
    await user.click(heart)
    await waitFor(() => expect(heart).toHaveAttribute('aria-pressed', 'false'))
  })

  it('회원이면 맨 위에 내 플랜 자리가 보인다', async () => {
    setMockRole('USER')
    renderHome()
    expect(await screen.findByRole('heading', { level: 2, name: /내 플랜/ })).toBeInTheDocument()
  })
})
