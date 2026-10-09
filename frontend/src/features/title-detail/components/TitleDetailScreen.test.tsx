import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { TitleDetailScreen } from '@/features/title-detail/components/TitleDetailScreen'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { TITLE_FIXTURES } from '@/mocks/fixtures/titles'
import { resetMockMemberState } from '@/mocks/memberHandlers'
import { resetMockSession, setMockOnboardingDone, setMockRole } from '@/mocks/mockSession'

/*
 * 작품 상세 화면 테스트(Task 027). 사용자가 보는 글자 중심으로 확인하고, API는 앱의 목업 핸들러(MSW)로 대체한다.
 * 주소(/titles/:mediaType/:tmdbId)에서 작품을 정하는 화면이라 같은 경로 패턴의 메모리 라우터로 연다.
 */

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' })) // 핸들러 없는 요청이 실제 네트워크로 나가지 않게 막는다
// 로그인 판단(useIsLoggedIn)은 목업 모드에서만 목업 역할을 믿으므로, 회원 상태 테스트를 위해 목업 모드를 흉내 낸다(.env.local이 없는 CI에서도 같게)
beforeEach(() => vi.stubEnv('VITE_USE_MOCK', 'true'))
afterEach(() => {
  vi.unstubAllEnvs()
  server.resetHandlers()
  resetMockSession() // 로그인 역할·온보딩 여부는 모듈 변수라 테스트마다 처음(비로그인, 온보딩 미완료)으로 되돌린다
  resetMockMemberState() // 저장한 내 설정 등도 지운다
  resetMockWishlist()
})
afterAll(() => server.close())

// 목업 작품을 제목과 구분으로 찾는다(같은 제목의 영화·드라마가 있어 구분까지 본다)
function findFixture(title: string, mediaType: 'movie' | 'tv') {
  const found = TITLE_FIXTURES.find((t) => t.title === title && t.mediaType === mediaType)
  if (!found) throw new Error(`목업 작품 "${title}"(${mediaType})을 찾을 수 없다`)
  return found
}

// 해당 작품의 상세 주소로 상세 화면을 연다. 실제 앱과 같은 Providers(QueryClient·토스트)로 감싼다
function renderDetailOf(title: string, mediaType: 'movie' | 'tv') {
  const fixture = findFixture(title, mediaType)
  const router = createMemoryRouter(
    [{ path: '/titles/:mediaType/:tmdbId', element: <TitleDetailScreen /> }],
    { initialEntries: [`/titles/${fixture.mediaType}/${fixture.tmdbId}`] },
  )
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
}

describe('작품 상세 화면', () => {
  it('"볼 수 있는 곳" 칩 요약이 먼저 보이고, 서비스별 7줄 표는 기본 닫힌 "서비스별 자세히 보기" 안에 있다', async () => {
    const user = userEvent.setup()
    renderDetailOf('기생충', 'movie')
    await screen.findByRole('heading', { level: 1, name: '기생충' })

    const chips = within(screen.getByRole('list', { name: '볼 수 있는 곳' }))
    expect(chips.getAllByRole('listitem').map((chip) => chip.textContent)).toEqual([
      '티빙',
      '웨이브',
      '왓챠',
    ])

    const summary = screen.getByText('서비스별 자세히 보기')
    const details = summary.closest('details')!
    expect(details).not.toHaveAttribute('open')
    await user.click(summary)
    expect(details).toHaveAttribute('open')
    // 표에서는 있음/없음/모름이 글자로 구분된다
    const table = within(details)
    expect(table.getAllByRole('listitem')).toHaveLength(7)
    expect(table.getByText('넷플릭스').closest('li')).toHaveTextContent('없음')
    expect(table.getByText('티빙').closest('li')).toHaveTextContent('있음')
  })

  it('시즌마다 제공처가 다른 드라마는 시즌 줄마다 "볼 수 있는 곳" 칩과 총 시청 시간이 보인다', async () => {
    renderDetailOf('예시 드라마 A (시즌별 제공처 다름)', 'tv')

    expect(
      await screen.findByRole('heading', { level: 1, name: /예시 드라마 A/ }),
    ).toBeInTheDocument()

    // 시즌 1은 넷플릭스·티빙, 시즌 2는 티빙·웨이브다
    const season1Chips = within(screen.getByRole('list', { name: '시즌 1 볼 수 있는 곳' }))
    expect(season1Chips.getAllByRole('listitem').map((chip) => chip.textContent)).toEqual([
      '넷플릭스',
      '티빙',
    ])
    const season2Chips = within(screen.getByRole('list', { name: '시즌 2 볼 수 있는 곳' }))
    expect(season2Chips.getAllByRole('listitem').map((chip) => chip.textContent)).toEqual([
      '티빙',
      '웨이브',
    ])

    // 시즌 단위로 찜할 수 있다. 찜 버튼은 이름 옆에 바로 있고, 제목(h3)에는 시즌 이름 글자만 있다
    expect(screen.getByRole('button', { name: '시즌 1 찜하기' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 3, name: '시즌 1' })).toBeInTheDocument()

    // 총 시청 시간은 분이 아니라 "19시간"처럼 읽기 쉬운 형식이다(1140분, 1170분). 섹션 요약에는 합계가 나온다
    expect(screen.getByText(/12회차 · 총 19시간$/)).toBeInTheDocument()
    expect(screen.getByText(/12회차 · 총 19시간 30분/)).toBeInTheDocument()
    expect(screen.getByText('시즌 2개 · 총 38시간 30분')).toBeInTheDocument()
  })

  it('접이식이 없다: 시즌 줄에는 펼침 버튼(aria-expanded)이 없고 시즌 이름은 제목 글자뿐이다', async () => {
    renderDetailOf('오징어 게임', 'tv')
    await screen.findByRole('heading', { level: 1, name: '오징어 게임' })

    expect(screen.queryByRole('button', { expanded: true })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { expanded: false })).not.toBeInTheDocument()
    expect(screen.queryAllByRole('region', { name: /^시즌/ })).toHaveLength(0)
    expect(screen.getByRole('heading', { level: 3, name: '시즌 1' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 3, name: '시즌 2' })).toBeInTheDocument()
  })

  it('모든 시즌의 제공처가 같으면 시즌 줄의 칩을 생략하고 안내 한 줄만 보인다', async () => {
    renderDetailOf('슬기로운 의사생활', 'tv')
    await screen.findByRole('heading', { level: 1, name: '슬기로운 의사생활' })

    expect(screen.queryByRole('list', { name: /^시즌 \d+ 볼 수 있는 곳/ })).not.toBeInTheDocument()
    expect(screen.getByText(/모든 시즌의 제공처가 같습니다/)).toBeInTheDocument()
    // 작품 위쪽 요약에는 넷플릭스가 "있음"으로 보인다
    const chips = within(screen.getByRole('list', { name: '볼 수 있는 곳' }))
    expect(chips.getAllByRole('listitem').map((chip) => chip.textContent)).toEqual(['넷플릭스'])
  })

  it('시즌이 5개를 넘으면 처음 5개만 보이고 "시즌 더 보기"로 나머지를 연다. 섹션 제목 옆에 요약이 보인다', async () => {
    const user = userEvent.setup()
    renderDetailOf('예시 장편 드라마 (12시즌)', 'tv')
    await screen.findByRole('heading', { level: 1, name: /예시 장편 드라마/ })

    // 12시즌의 개수와 합계(formatMinutes). 일부 시즌의 러닝타임이 추정이라 "추정"도 붙는다
    expect(screen.getByText(/^시즌 12개 · 총 \d+시간/)).toBeInTheDocument()

    const seasonHeadings = () => screen.getAllByRole('heading', { level: 3, name: /^시즌 \d+$/ })
    expect(seasonHeadings()).toHaveLength(5)

    await user.click(screen.getByRole('button', { name: '시즌 더 보기 (남은 7개)' }))
    expect(seasonHeadings()).toHaveLength(12)
    expect(screen.queryByRole('button', { name: /시즌 더 보기/ })).not.toBeInTheDocument()
  })

  it('시즌이 5개 이하면 "시즌 더 보기" 버튼이 없다', async () => {
    renderDetailOf('안나', 'tv')
    await screen.findByRole('heading', { level: 1, name: '안나' })

    expect(screen.queryByRole('button', { name: /시즌 더 보기/ })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 3, name: '시즌 1' })).toBeInTheDocument()
  })

  it('한국어 줄거리가 없는 작품은 영어 줄거리와 "영어 원문" 표시가 나온다', async () => {
    renderDetailOf('버닝', 'movie')

    expect(await screen.findByRole('heading', { level: 1, name: '버닝' })).toBeInTheDocument()
    expect(screen.getByText('영어 원문')).toBeInTheDocument()
    // 영어 문단은 화면 낭독기가 영어로 읽도록 lang="en"이 붙어 있다
    expect(screen.getByText(/A young man who works odd jobs/)).toHaveAttribute('lang', 'en')
  })

  it('러닝타임이 추정값인 영화에는 "추정" 표시가 붙고, 포스터가 없으면 대체 표시가 보인다', async () => {
    renderDetailOf('헌트', 'movie')

    expect(await screen.findByRole('heading', { level: 1, name: '헌트' })).toBeInTheDocument()
    expect(screen.getByText('2시간')).toBeInTheDocument()
    expect(screen.getByText('추정')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: '포스터 이미지 없음' })).toBeInTheDocument()
  })

  it('출연진이 10명 보이고, 제공처 정보에는 출처와 데이터 기준일이 함께 나온다', async () => {
    renderDetailOf('오징어 게임', 'tv')

    await screen.findByRole('heading', { level: 1, name: '오징어 게임' })
    const castSection = screen.getByRole('heading', { name: '주요 출연진' }).closest('section')!
    expect(within(castSection).getAllByRole('listitem')).toHaveLength(10)

    expect(screen.getByRole('link', { name: /JustWatch/ })).toBeInTheDocument()
    expect(screen.getByText(/데이터 기준일/)).toBeInTheDocument()
  })

  it('시즌 줄에서 바로 찜하고, 찜한 뒤 우선순위를 바꾸고 해제할 수 있다 (회원)', async () => {
    setMockRole('USER')
    const user = userEvent.setup()
    renderDetailOf('오징어 게임', 'tv')
    await screen.findByRole('heading', { level: 1, name: '오징어 게임' })

    // 시즌 2를 찜한다. 우선순위를 묻지 않고 기본값 "보고 싶음"으로 들어간다
    await user.click(screen.getByRole('button', { name: '시즌 2 찜하기' }))
    const savedButton = await screen.findByRole('button', { name: '시즌 2 찜함 · 보고 싶음' })
    // 다른 시즌은 영향 없이 그대로다
    expect(screen.getByRole('button', { name: '시즌 1 찜하기' })).toBeInTheDocument()

    // 찜 버튼을 누르면 우선순위 3개가 보이고, 고르면 바뀐다
    await user.click(savedButton)
    expect(screen.getAllByRole('radio').map((radio) => radio.parentElement?.textContent)).toEqual([
      '꼭 볼 작품',
      '보고 싶음',
      '여유될 때',
    ])
    await user.click(screen.getByRole('radio', { name: '꼭 볼 작품' }))
    await user.click(await screen.findByRole('button', { name: '시즌 2 찜함 · 꼭 볼 작품' }))

    // 해제하면 다시 "찜하기"로 돌아간다
    await user.click(screen.getByRole('button', { name: /^찜 해제/ }))
    expect(await screen.findByRole('button', { name: '시즌 2 찜하기' })).toBeInTheDocument()
  })

  it('찜한 뒤 비로그인으로 바뀌면 "찜함" 표시가 사라지고 "찜하기"로 돌아간다', async () => {
    setMockRole('USER')
    const user = userEvent.setup()
    renderDetailOf('오징어 게임', 'tv')
    await screen.findByRole('heading', { level: 1, name: '오징어 게임' })

    await user.click(screen.getByRole('button', { name: '시즌 2 찜하기' }))
    expect(
      await screen.findByRole('button', { name: '시즌 2 찜함 · 보고 싶음' }),
    ).toBeInTheDocument()

    // 머리글 전환(로그아웃)과 같은 경로로 역할만 바꾼다. 캐시에는 찜이 남아 있지만 화면은 비로그인 기준으로 그린다
    act(() => setMockRole('GUEST'))
    expect(await screen.findByRole('button', { name: '시즌 2 찜하기' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /찜함/ })).not.toBeInTheDocument()
  })

  it('영화는 상세 상단에 찜 버튼 하나가 있고, 비로그인이면 토스트 대신 로그인 화면으로 이동한다', async () => {
    const user = userEvent.setup()
    const fixture = findFixture('기생충', 'movie')
    const router = createMemoryRouter(
      [
        { path: '/titles/:mediaType/:tmdbId', element: <TitleDetailScreen /> },
        { path: '/login', element: <h1>로그인 화면</h1> },
      ],
      { initialEntries: [`/titles/${fixture.mediaType}/${fixture.tmdbId}`] },
    )
    render(
      <Providers>
        <RouterProvider router={router} />
      </Providers>,
    )
    await screen.findByRole('heading', { level: 1, name: '기생충' })

    await user.click(screen.getByRole('button', { name: '기생충 찜하기' }))
    expect(await screen.findByRole('heading', { name: '로그인 화면' })).toBeInTheDocument()
    expect(screen.queryByText('로그인이 필요합니다.')).not.toBeInTheDocument()
    // 로그인이 끝나면 돌아올 작품 상세 경로가 함께 넘어간다
    expect(router.state.location.state).toEqual({
      from: `/titles/${fixture.mediaType}/${fixture.tmdbId}`,
    })
  })

  describe('긴 시즌 안내("여러 달에 나눠 봐야 해요")', () => {
    // 목업 내 설정의 월 시청 시간은 900분(15시간)이다. 예시 드라마 A는 시즌 1·2가 1140분·1170분이라 한 달에 다 못 본다
    const LONG_SEASON_NOTICE = '여러 달에 나눠 봐야 해요'

    it('온보딩을 마친 회원이면 월 시청 시간보다 긴 시즌 줄에만 안내가 보인다', async () => {
      setMockRole('USER')
      setMockOnboardingDone(true)
      renderDetailOf('예시 드라마 A (시즌별 제공처 다름)', 'tv')
      await screen.findByRole('heading', { level: 1, name: /예시 드라마 A/ })

      // 설정을 불러온 뒤에 안내가 나타난다. 시즌 2개 모두 900분보다 길다
      expect(await screen.findAllByText(LONG_SEASON_NOTICE)).toHaveLength(2)
    })

    it('시즌이 월 시청 시간 이하면 회원이어도 안내가 보이지 않는다', async () => {
      setMockRole('USER')
      setMockOnboardingDone(true)
      renderDetailOf('오징어 게임', 'tv') // 시즌 총 시간이 500·425·270분이라 모두 900분 이하
      await screen.findByRole('heading', { level: 1, name: '오징어 게임' })
      // 설정이 도착할 시간을 준 뒤에도 없는지 확인한다(찜 버튼이 회원 상태로 바뀐 것을 기다리는 대신 요청 완료를 기다린다)
      await new Promise((resolve) => setTimeout(resolve, 50))

      expect(screen.queryByText(LONG_SEASON_NOTICE)).not.toBeInTheDocument()
    })

    it('비회원이면 안내가 없고 내 설정 요청도 보내지 않는다', async () => {
      setMockOnboardingDone(true) // 역할은 GUEST
      const settingsRequests: string[] = []
      server.events.on('request:start', ({ request }) => {
        if (new URL(request.url).pathname === '/api/me/settings') settingsRequests.push(request.url)
      })
      renderDetailOf('예시 드라마 A (시즌별 제공처 다름)', 'tv')
      await screen.findByRole('heading', { level: 1, name: /예시 드라마 A/ })
      await new Promise((resolve) => setTimeout(resolve, 50))

      expect(screen.queryByText(LONG_SEASON_NOTICE)).not.toBeInTheDocument()
      expect(settingsRequests).toHaveLength(0)
      server.events.removeAllListeners()
    })

    it('온보딩을 마치지 않은 회원(설정 없음)이면 안내를 숨긴다', async () => {
      setMockRole('USER') // 온보딩 미완료 → 내 설정이 비어 있다
      renderDetailOf('예시 드라마 A (시즌별 제공처 다름)', 'tv')
      await screen.findByRole('heading', { level: 1, name: /예시 드라마 A/ })
      await new Promise((resolve) => setTimeout(resolve, 50))

      expect(screen.queryByText(LONG_SEASON_NOTICE)).not.toBeInTheDocument()
    })
  })

  it('없는 작품이면 오류 안내와 검색으로 돌아가는 링크가 보인다', async () => {
    const router = createMemoryRouter(
      [{ path: '/titles/:mediaType/:tmdbId', element: <TitleDetailScreen /> }],
      { initialEntries: ['/titles/movie/1'] },
    )
    render(
      <Providers>
        <RouterProvider router={router} />
      </Providers>,
    )

    expect(await screen.findByText('작품을 찾을 수 없습니다.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '작품 검색으로 돌아가기' })).toBeInTheDocument()
  })
})
