import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { RootLayout } from '@/app/RootLayout'
import { AboutScreen } from '@/features/about/components/AboutScreen'
import { AuthCallbackScreen } from '@/features/auth/components/AuthCallbackScreen'
import { LoginScreen } from '@/features/auth/components/LoginScreen'
import { TitleDetailScreen } from '@/features/title-detail/components/TitleDetailScreen'
import { TITLE_FIXTURES } from '@/mocks/fixtures/titles'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { getMockRole, resetMockSession, setMockOnboardingDone } from '@/mocks/mockSession'

/*
 * 로그인 흐름 테스트(Task 027 2회차): 로그인 화면, 콜백 성공·실패, 비로그인 찜 → 로그인 → 원래 화면 복귀,
 * 머리글의 목업 전환 토글. 실제 머리글(RootLayout)·화면 컴포넌트·Providers를 연결한 작은 경로표로 화면 이동까지 확인한다.
 * 목업 모드(VITE_USE_MOCK=true)를 흉내 내려고 vi.stubEnv를 쓴다(테스트마다 되돌린다).
 */

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
beforeEach(() => vi.stubEnv('VITE_USE_MOCK', 'true'))
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockSession() // 로그인 역할·온보딩 여부는 모듈 변수라 테스트마다 처음으로 되돌린다
  resetMockWishlist()
})
afterAll(() => server.close())

/*
 * 이 테스트 전용 작은 경로표다. 실제 app/router의 routes를 쓰지 않는 이유: 그 라우트는 페이지를 lazy로 불러오는데,
 * 같은 주소를 한 파일에서 여러 번 열면 두 번째부터 화면이 아닌 404가 나는 것을 확인했다(react-router 8, 정확한 원인은 확인하지 못했다).
 * 실제 router.tsx의 경로가 맞는지는 router.test.tsx가 따로 검사한다. 내 설정(SCR-07)·검색은 이 Task의 대상이 아니라 제목만 있는 자리표시를 둔다.
 */
const testRoutes: RouteObject[] = [
  {
    path: '/',
    Component: RootLayout,
    children: [
      { index: true, element: <h1>작품 검색</h1> },
      { path: 'titles/:mediaType/:tmdbId', element: <TitleDetailScreen /> },
      { path: 'about', element: <AboutScreen /> },
      { path: 'login', element: <LoginScreen /> },
      { path: 'auth/callback', element: <AuthCallbackScreen /> },
      { path: 'settings', element: <h1>내 설정</h1> },
    ],
  },
]

// 지정한 주소(와 router state)에서 앱을 연다. 주소의 검색어(?...)는 pathname에 섞지 않고 search로 나눠 넘긴다
function renderAt(url: string, state?: unknown) {
  const { pathname, search } = new URL(url, 'http://localhost')
  const router = createMemoryRouter(testRoutes, { initialEntries: [{ pathname, search, state }] })
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
  return router
}

// 콜백의 로딩 흉내(0.7초)와 화면 불러오기를 기다리는 넉넉한 제한 시간
const WAIT = { timeout: 4000 }

describe('로그인 화면', () => {
  it('Google 로그인 버튼과 로그인 혜택이 보인다', async () => {
    renderAt('/login')

    expect(await screen.findByRole('heading', { level: 1, name: '로그인' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Google로 로그인' })).toBeEnabled()
    expect(screen.getByRole('list', { name: '로그인 혜택' })).toBeInTheDocument()
  })

  it('목업이 아닌 환경에서는 로그인 버튼이 꺼져 있고 준비 중이라고 알린다', async () => {
    vi.stubEnv('VITE_USE_MOCK', 'false')
    renderAt('/login')

    expect(await screen.findByRole('button', { name: 'Google로 로그인' })).toBeDisabled()
    expect(screen.getByText('로그인 연동은 아직 준비 중입니다.')).toBeInTheDocument()
  })
})

describe('로그인 콜백 화면', () => {
  it('처음 로그인(온보딩 미완료)이면 회원으로 바뀌고 내 설정(SCR-07)으로 이동한다', async () => {
    renderAt('/auth/callback', { from: '/titles/movie/1' })

    expect(
      await screen.findByRole('heading', { level: 1, name: '로그인 처리 중' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('status')).toBeInTheDocument()
    expect(
      await screen.findByRole('heading', { level: 1, name: '내 설정' }, WAIT),
    ).toBeInTheDocument()
    expect(getMockRole()).toBe('USER')
  })

  it('실패하면 안내와 "다시 시도"가 보이고, 누르면 로그인 화면으로 돌아간다(로그인되지 않는다)', async () => {
    const user = userEvent.setup()
    renderAt('/auth/callback?mockScenario=login-failed')

    expect(await screen.findByText('로그인에 실패했습니다', undefined, WAIT)).toBeInTheDocument()
    expect(getMockRole()).toBe('GUEST')
    await user.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('heading', { level: 1, name: '로그인' })).toBeInTheDocument()
  })

  it('외부 주소를 from으로 넘겨도 따르지 않고 첫 화면으로 간다', async () => {
    setMockOnboardingDone(true)
    renderAt('/auth/callback', { from: 'https://evil.example/' })

    expect(
      await screen.findByRole('heading', { level: 1, name: '작품 검색' }, WAIT),
    ).toBeInTheDocument()
  })
})

describe('비로그인 찜 → 로그인 → 원래 화면', () => {
  // 목업 작품 중 영화 하나(찜 버튼이 작품 상단에 하나 있다)
  const movie = TITLE_FIXTURES.find((t) => t.title === '기생충' && t.mediaType === 'movie')!

  it('비로그인에서 찜을 누르면 토스트 대신 로그인 화면으로 가고, 로그인하면 작품 상세로 돌아온다', async () => {
    const user = userEvent.setup()
    setMockOnboardingDone(true) // 이미 온보딩을 마친 회원이 다시 로그인하는 경우
    const router = renderAt(`/titles/movie/${movie.tmdbId}`)
    await screen.findByRole('heading', { level: 1, name: '기생충' })

    await user.click(screen.getByRole('button', { name: '기생충 찜하기' }))
    expect(await screen.findByRole('heading', { level: 1, name: '로그인' })).toBeInTheDocument()
    expect(screen.queryByText('로그인이 필요합니다.')).not.toBeInTheDocument()
    // 돌아올 경로(from)가 넘어갔다
    expect(router.state.location.state).toEqual({ from: `/titles/movie/${movie.tmdbId}` })

    await user.click(screen.getByRole('button', { name: 'Google로 로그인' }))
    expect(
      await screen.findByRole('heading', { level: 1, name: '기생충' }, WAIT),
    ).toBeInTheDocument()
    expect(router.state.location.pathname).toBe(`/titles/movie/${movie.tmdbId}`)
    expect(getMockRole()).toBe('USER')
    // 로그인 뒤에는 찜이 된다
    await user.click(screen.getByRole('button', { name: '기생충 찜하기' }))
    // 찜하면 선택지 팝업이 뜬다(팝업이 열린 동안 뒤 화면은 낭독기에서 가려지므로 닫은 뒤 버튼을 확인한다)
    expect(await screen.findByRole('dialog', { name: '기생충을 찜했어요' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '더 둘러보기' }))
    expect(
      await screen.findByRole('button', { name: '기생충 찜함 · 보고 싶음' }),
    ).toBeInTheDocument()
  })
})

describe('머리글의 목업 로그인 상태 전환', () => {
  it('전환하면 머리글 표시가 바뀐다: 비로그인(로그인 링크) → 회원(회원·로그아웃) → 관리자 → 로그아웃', async () => {
    const user = userEvent.setup()
    renderAt('/about')
    const select = await screen.findByRole('combobox', { name: '개발용 목업 로그인 상태' })
    const nav = screen.getByRole('navigation', { name: '계정' })
    expect(screen.getByRole('link', { name: '로그인' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '로그아웃' })).not.toBeInTheDocument()

    await user.selectOptions(select, '회원')
    expect(getMockRole()).toBe('USER')
    expect(screen.getByRole('button', { name: '로그아웃' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '로그인' })).not.toBeInTheDocument()
    // 선택 상자의 선택지에도 같은 글자가 있어, 사용자 표시(span)만 골라 확인한다
    expect(within(nav).getByText('회원', { selector: 'span' })).toBeInTheDocument()

    await user.selectOptions(select, '관리자')
    expect(getMockRole()).toBe('ADMIN')
    expect(within(nav).getByText('관리자', { selector: 'span' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '로그아웃' }))
    expect(getMockRole()).toBe('GUEST')
    expect(await screen.findByRole('link', { name: '로그인' })).toBeInTheDocument()
  })

  it('운영(목업이 아닌 환경)에서는 전환이 보이지 않고 항상 로그인 링크만 보인다', async () => {
    vi.stubEnv('VITE_USE_MOCK', 'false')
    renderAt('/about')

    expect(await screen.findByRole('link', { name: '로그인' })).toBeInTheDocument()
    expect(
      screen.queryByRole('combobox', { name: '개발용 목업 로그인 상태' }),
    ).not.toBeInTheDocument()
  })
})
