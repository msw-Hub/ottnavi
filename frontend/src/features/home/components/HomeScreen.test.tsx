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
      '서비스 독점 작품',
    ]) {
      expect(await screen.findByRole('heading', { level: 2, name: heading })).toBeInTheDocument()
    }

    // 순위 숫자는 장식이 아니라 글자로 읽힌다
    const ranking = await screen.findByRole('list', { name: '전체 인기 순위' })
    expect(
      within(ranking).getByText(
        (_, element) => element?.tagName === 'P' && element.textContent === '인기 1위',
      ),
    ).toBeInTheDocument()
    // 서버가 준 기준 안내가 그대로 보인다
    // 순위 기준(최근 2년 안 한국 작품 중 인기도 순, 공식 순위 아님)과 기간이 함께 보인다
    expect(
      screen.getByText(
        /최근 2년 안에 공개된 한국 작품 중 TMDB 인기도 순\(OTT 공식 순위 아님\) · 오늘/,
      ),
    ).toBeInTheDocument()
    // 출처: JustWatch 출처는 맨 아래 한 곳(H-1-2), 기준일은 목록마다 제목 아래 "○월 ○일 기준"
    // 이전: 목록마다 출처 한 줄이 있어 "제공처 정보 출처"·"데이터 기준일"이 4번 이상 보였다
    expect(screen.getByRole('link', { name: /JustWatch/ })).toHaveAttribute(
      'href',
      'https://www.justwatch.com/',
    )
    await waitFor(() =>
      expect(
        screen.getAllByText(/\d+월 \d+일/, { selector: 'time' }).length,
      ).toBeGreaterThanOrEqual(4),
    )
  })

  it('독점 작품 줄은 첫 서비스의 독점 첫 쪽(10편)을 보이고, 쿠팡플레이는 "드라마만 확인" 안내와 함께 목록을 보인다', async () => {
    const user = userEvent.setup()
    renderHome()

    // 넷플릭스 독점은 30편이지만 줄에는 첫 쪽 10편만 온다. 카드는 포스터 이미지 주소가 TMDB 상대 경로에서 만들어진다
    const list = await screen.findByRole('list', { name: '넷플릭스 독점 작품' })
    expect(within(list).getAllByRole('article')).toHaveLength(10)
    // 탭이 이미 서비스를 말해 카드마다 "넷플릭스 [있음]" 줄을 반복하지 않는다
    expect(within(list).queryByText('있음')).not.toBeInTheDocument()
    // 더보기는 같은 서비스 탭이 열린 독점 목록 화면으로 간다
    const section = list.closest('section') as HTMLElement
    expect(within(section).getByRole('link', { name: /더보기/ })).toHaveAttribute(
      'href',
      '/exclusives?service=NETFLIX',
    )

    const tabs = within(section).getByRole('tablist', { name: '독점 작품 서비스 선택' })
    await user.click(within(tabs).getByRole('tab', { name: /쿠팡플레이/ }))
    expect(
      await within(section).findByText(
        '쿠팡플레이는 영화 제공 정보를 받지 못해 드라마만 확인했어요.',
      ),
    ).toBeInTheDocument()
    expect(
      await within(section).findByRole('list', { name: '쿠팡플레이 독점 작품' }),
    ).toBeInTheDocument()
  })

  it('독점 작품 줄에서 서버가 판정하지 않았다고 답하면(구버전 isJudged=false) 목록 대신 안내를 보인다', async () => {
    server.use(
      mswHttp.get('/api/public/ott-services/:ottServiceId/exclusive-titles', () =>
        HttpResponse.json({
          success: true,
          data: {
            content: [],
            page: 0,
            size: 10,
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
    renderHome()

    const tabs = await screen.findByRole('tablist', { name: '독점 작품 서비스 선택' })
    await user.click(within(tabs).getByRole('tab', { name: /쿠팡플레이/ }))
    expect(await screen.findByText(/독점 여부를 판단하지 않아요/)).toBeInTheDocument()
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
    // 서비스 탭에서는 카드마다 "쿠팡플레이 [있음]" 줄을 반복하지 않는다(H-1-3)
    const ranking = await screen.findByRole('list', { name: '쿠팡플레이 인기 순위' })
    expect(within(ranking).queryByText('있음')).not.toBeInTheDocument()
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
    // 카드 아래는 상태 배지("있음") 대신 볼 수 있는 서비스 이름 칩 목록이다
    expect(within(section).queryByText('있음')).not.toBeInTheDocument()
    expect(within(section).getAllByRole('list', { name: '볼 수 있는 곳' }).length).toBeGreaterThan(
      0,
    )
  })

  // H-2: 카드의 찜 버튼 자리를 "볼 수 있는 곳" OTT 로고로 바꿨다. 로고는 서비스 이름으로 읽히고, 찜 버튼은 카드에 없다
  it('카드 포스터 위에 볼 수 있는 OTT가 서비스 이름으로 읽히는 로고 목록으로 보이고, 찜 버튼은 없다', async () => {
    renderHome()
    const ranking = await screen.findByRole('list', { name: '전체 인기 순위' })
    const logos = within(ranking).getAllByRole('list', { name: '볼 수 있는 곳' })
    expect(logos.length).toBeGreaterThan(0)
    expect(within(logos[0]).getByRole('img', { name: '넷플릭스' })).toBeInTheDocument()
    expect(within(ranking).queryByRole('link', { name: /찜하기/ })).not.toBeInTheDocument()
    expect(within(ranking).queryByRole('button', { name: /찜하기/ })).not.toBeInTheDocument()
  })

  // H-3(B안): 제목은 항상 한 줄 말줄임이다. 잘려 보여도 DOM 글자와 링크 title에는 전체 제목이 있어 낭독기·마우스 사용자가 전체를 안다
  it('카드 제목은 한 줄 말줄임이고, 링크 이름과 title에는 전체 제목이 남는다', async () => {
    renderHome()
    const ranking = await screen.findByRole('list', { name: '전체 인기 순위' })
    const heading = within(ranking).getAllByRole('heading', { level: 3 })[0]
    const link = within(heading).getByRole('link')
    expect(heading).toHaveClass('truncate')
    expect(link.textContent).not.toBe('')
    expect(link).toHaveAttribute('title', link.textContent)
    expect(link).toHaveAccessibleName(link.textContent ?? '')
  })

  /* 이전(H-2 전): 카드 찜 버튼 테스트 2개. 찜 버튼(CardWishlistAction)을 홈 카드에서 쓰지 않아 주석으로 남긴다
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
  */

  it('회원이면 맨 위에 내 플랜 자리가 보인다', async () => {
    setMockRole('USER')
    renderHome()
    expect(await screen.findByRole('heading', { level: 2, name: /내 플랜/ })).toBeInTheDocument()
  })
})
