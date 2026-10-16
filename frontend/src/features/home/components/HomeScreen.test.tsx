import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { HomeScreen } from '@/features/home/components/HomeScreen'

/*
 * 홈 1차안 렌더링 테스트. 정적 샘플 데이터라 API 대역(MSW)은 쓰지 않는다.
 * 확인하는 것: 처음 온 사람에게 가치 제안·3단계·시작 버튼이 보이는지, 목록의 순위 숫자가 낭독기에도 "인기 1위"로 읽히는지,
 * 출처·기준일이 있는지, 데이터 부족 서비스(쿠팡플레이)의 안내가 탭에 나오는지.
 * 막으려는 사고: 레이아웃을 바꾸다 출처 표기나 데이터 부족 안내가 빠지는 것(frontend.md 화면 표시 규칙).
 */
function renderHome() {
  const router = createMemoryRouter([{ path: '/', element: <HomeScreen /> }], {
    initialEntries: ['/'],
  })
  render(<RouterProvider router={router} />)
}

describe('홈 화면(1차안)', () => {
  it('비로그인이면 가치 제안·3단계 안내·시작 버튼과 목록 4줄, 출처·기준일이 보인다', () => {
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
    // 검색창은 머리글(RootLayout)로 통일해 홈 본문에는 없다(홈 1차안 피드백)
    expect(screen.queryByRole('searchbox')).not.toBeInTheDocument()

    for (const heading of [
      '서비스별 인기 순위',
      '찜이 많은 작품',
      '최근 새로 볼 수 있게 된 작품',
      '서비스 독점 작품',
    ]) {
      expect(screen.getByRole('heading', { level: 2, name: heading })).toBeInTheDocument()
    }
    // 순위 숫자는 장식이 아니라 글자로 읽힌다
    const ranking = screen.getByRole('list', { name: '전체 인기 순위' })
    expect(
      within(ranking).getByText(
        (_, element) => element?.tagName === 'P' && element.textContent === '인기 1위',
      ),
    ).toBeInTheDocument()
    // 출처·기준일(목록마다 한 줄 + 화면 아래 전체 묶음)
    expect(screen.getAllByText(/제공처 정보 출처/).length).toBeGreaterThanOrEqual(5)
    expect(screen.getAllByText(/데이터 기준일/).length).toBeGreaterThanOrEqual(5)
  })

  it('인기 순위 기간을 최근 7일로 바꾸면 기준 문구와 순위가 바뀐다', async () => {
    const user = userEvent.setup()
    renderHome()

    expect(screen.getByText(/TMDB 인기도 기준 · 오늘 ·.*OTT 공식 순위 아님/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '최근 7일' }))
    expect(screen.getByRole('button', { name: '최근 7일' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText(/TMDB 인기도 기준 · 최근 7일 평균 ·/)).toBeInTheDocument()
    // 샘플에서 최근 7일 1위는 오늘 1위(한밤의 정류장)와 다르다
    const ranking = screen.getByRole('list', { name: '전체 인기 순위' })
    expect(within(ranking).getAllByRole('heading', { level: 3 })[0]).toHaveTextContent(
      '강철의 계절',
    )
  })

  it('쿠팡플레이 탭을 고르면 인기 순위는 데이터 부족 안내를, 독점은 목록 대신 안내를 보인다', async () => {
    const user = userEvent.setup()
    renderHome()

    const popularTabs = screen.getByRole('tablist', { name: '인기 순위 서비스 선택' })
    const exclusiveTabs = screen.getByRole('tablist', { name: '독점 작품 서비스 선택' })
    await user.click(within(popularTabs).getByRole('tab', { name: '쿠팡플레이' }))
    expect(screen.getByText(/영화는 순위에서 빠지고/)).toBeInTheDocument()

    await user.click(within(exclusiveTabs).getByRole('tab', { name: '쿠팡플레이' }))
    expect(screen.getByText('쿠팡플레이는 독점 여부를 판단하지 않아요')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: '쿠팡플레이 독점 작품' })).not.toBeInTheDocument()
  })
})
