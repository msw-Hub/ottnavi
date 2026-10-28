import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { RootLayout } from '@/app/RootLayout'
import { LoginScreen } from '@/features/auth/components/LoginScreen'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { getMockRole, resetMockSession, setMockOnboardingDone } from '@/mocks/mockSession'
import { useAuthStore } from '@/stores/authStore'

/*
 * 데모 계정 로그인 테스트(Task 109). API는 앱의 목업 핸들러(MSW)이고, 오류 상황은 화면 주소의 ?mockScenario= 값으로 고른다
 * (목업 모드의 요청 인터셉터가 window 주소에서 읽어 X-Mock-Scenario 헤더로 보내므로 history.pushState로 주소를 맞춘다).
 *
 * | 확인하는 것 | 막으려는 사고 |
 * | 성공하면 역할이 DEMO가 되고 원래 화면으로 간다(실제 모드는 토큰이 메모리에 들어간다) | 로그인됐는데 로그인 화면에 머물거나 엉뚱한 곳으로 가는 것 |
 * | 빈 칸이면 요청이 나가지 않는다 | 빈 값으로 서버 요청 제한(15분 5회)을 헛되이 쓰는 것 |
 * | 401이면 오류 문장과 비밀번호 칸 포커스 | 무엇이 틀렸는지 모르고 다시 입력하기 어려운 것 |
 * | 429면 "약 14분" 안내와 제출 막힘, 다시 눌러도 요청 없음 | 막힌 동안 계속 눌러 대기 시간만 늘어나는 것 |
 * | 404면 폼 대신 사용 불가 안내 | 꺼진 기능에 계속 입력하게 하는 것 |
 * | /login?demo=1이면 펼쳐진 채 시작, 평소에는 접힘 | 일반 방문자가 회원가입 폼으로 오해하는 것 |
 */

const server = setupServer(...handlers)

// 데모 로그인 요청이 나간 횟수(요청이 "안 나갔는지"를 보려고 센다)
let demoLoginRequestCount = 0

beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' })
  server.events.on('request:start', ({ request }) => {
    if (new URL(request.url).pathname === '/api/auth/demo-login') demoLoginRequestCount += 1
  })
})
beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true')
  demoLoginRequestCount = 0
})
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockSession()
  resetMockWishlist()
  useAuthStore.getState().clearSession()
  window.history.pushState({}, '', '/')
})
afterAll(() => server.close())

const testRoutes: RouteObject[] = [
  {
    path: '/',
    Component: RootLayout,
    children: [
      { index: true, element: <h1>첫 화면</h1> },
      { path: 'login', element: <LoginScreen /> },
      { path: 'wishlist', element: <h1>찜 목록 화면</h1> },
      { path: 'settings', element: <h1>내 설정</h1> },
    ],
  },
]

// 로그인 화면을 연다. scenario는 목업 시나리오(?mockScenario=), from은 로그인 뒤 돌아갈 곳(router state)
function renderLogin({
  isDemoLink = true,
  scenario,
  from,
}: { isDemoLink?: boolean; scenario?: string; from?: string } = {}) {
  const params = new URLSearchParams()
  if (isDemoLink) params.set('demo', '1')
  if (scenario) params.set('mockScenario', scenario)
  const search = params.toString() ? `?${params}` : ''
  window.history.pushState({}, '', `/login${search}`)
  const router = createMemoryRouter(testRoutes, {
    initialEntries: [{ pathname: '/login', search, state: from ? { from } : undefined }],
  })
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
  return router
}

// 아이디·비밀번호를 넣고 로그인 버튼을 누른다
async function submitDemoLogin(user: ReturnType<typeof userEvent.setup>) {
  const form = await screen.findByRole('form', { name: '데모 계정 로그인' })
  await user.type(within(form).getByLabelText('아이디'), 'demo')
  await user.type(within(form).getByLabelText('비밀번호'), 'secret-password')
  await user.click(within(form).getByRole('button', { name: '로그인' }))
  return form
}

describe('데모 계정 로그인 화면', () => {
  it('/login?demo=1이면 펼쳐진 채 시작하고, 평소에는 접혀 있다가 누르면 펼쳐지며 아이디 칸에 포커스가 간다', async () => {
    const user = userEvent.setup()
    renderLogin({ isDemoLink: false })

    const toggle = await screen.findByRole('button', { name: '데모 계정으로 로그인' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByLabelText('아이디')).not.toBeInTheDocument()

    await user.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByLabelText('아이디')).toHaveFocus()
  })

  it('/login?demo=1로 열면 폼이 펼쳐져 있고 비밀번호 값은 화면에 적혀 있지 않다', async () => {
    renderLogin()

    expect(await screen.findByRole('button', { name: '데모 계정으로 로그인' })).toHaveAttribute(
      'aria-expanded',
      'true',
    )
    expect(screen.getByLabelText('아이디')).toBeInTheDocument()
    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('type', 'password')
    expect(screen.getByText('아이디와 비밀번호는 제출 문서에 적혀 있어요.')).toBeInTheDocument()
  })

  it('성공하면 역할이 데모 계정이 되고 원래 화면으로 이동한다', async () => {
    setMockOnboardingDone(true) // 이미 설정을 마친 공용 계정이면 원래 화면으로 간다
    const user = userEvent.setup()
    renderLogin({ from: '/wishlist' })

    await submitDemoLogin(user)

    expect(await screen.findByRole('heading', { name: '찜 목록 화면' })).toBeInTheDocument()
    expect(getMockRole()).toBe('DEMO')
    // 로그인 뒤 머리글 아래에 데모 안내 띠가 보인다(토스트 대신 이 띠가 로그인을 알린다)
    expect(screen.getByRole('note', { name: '데모 계정 안내' })).toHaveTextContent(
      '데모 계정으로 둘러보는 중이에요.',
    )
    expect(demoLoginRequestCount).toBe(1)
  })

  it('실제 환경(목업 아님)에서는 받은 Access Token과 역할을 메모리(authStore)에 넣는다', async () => {
    vi.stubEnv('VITE_USE_MOCK', 'false')
    setMockOnboardingDone(true)
    const user = userEvent.setup()
    renderLogin({ from: '/wishlist' })

    await submitDemoLogin(user)

    expect(await screen.findByRole('heading', { name: '찜 목록 화면' })).toBeInTheDocument()
    expect(useAuthStore.getState()).toMatchObject({
      status: 'authenticated',
      accessToken: 'mock-access-token',
      role: 'DEMO',
    })
  })

  it('빈 칸이면 요청을 보내지 않고 칸 아래에 안내하며 첫 빈 칸으로 포커스를 옮긴다', async () => {
    const user = userEvent.setup()
    renderLogin()

    const form = await screen.findByRole('form', { name: '데모 계정 로그인' })
    await user.click(within(form).getByRole('button', { name: '로그인' }))

    expect(await screen.findByText('아이디를 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('비밀번호를 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByLabelText('아이디')).toHaveFocus()
    expect(screen.getByLabelText('아이디')).toHaveAttribute('aria-invalid', 'true')
    expect(demoLoginRequestCount).toBe(0)
  })

  it('401이면 오류 문장이 보이고 비밀번호 칸으로 포커스가 간다', async () => {
    const user = userEvent.setup()
    renderLogin({ scenario: 'demo-login-failed' })

    await submitDemoLogin(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '아이디 또는 비밀번호가 올바르지 않습니다. 제출 문서의 값을 다시 확인해 주세요.',
    )
    expect(screen.getByLabelText('비밀번호')).toHaveFocus()
    expect(getMockRole()).toBe('GUEST')
  })

  it('429면 "약 14분" 안내가 보이고 제출이 막혀 다시 눌러도 요청이 나가지 않는다', async () => {
    const user = userEvent.setup()
    renderLogin({ scenario: 'demo-login-rate-limited' })

    const form = await submitDemoLogin(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '시도가 너무 많아요. 약 14분 뒤에 다시 시도해 주세요.',
    )
    const submit = within(form).getByRole('button', { name: '로그인' })
    expect(submit).toHaveAttribute('aria-disabled', 'true')
    expect(submit).toHaveAccessibleDescription('약 14분 남았어요')
    // 화면이 직접 안내하므로 전역 요청 제한 토스트는 띄우지 않는다
    expect(
      screen.queryByText('요청이 많습니다. 잠시 후 다시 시도해 주세요.'),
    ).not.toBeInTheDocument()

    await user.click(submit)
    expect(demoLoginRequestCount).toBe(1)
  })

  it('404(데모 로그인 꺼짐)면 폼 대신 사용할 수 없다는 안내가 보인다', async () => {
    const user = userEvent.setup()
    renderLogin({ scenario: 'demo-login-disabled' })

    await submitDemoLogin(user)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '지금은 데모 계정을 사용할 수 없어요. Google 계정으로 로그인해 주세요.',
    )
    expect(screen.queryByRole('form', { name: '데모 계정 로그인' })).not.toBeInTheDocument()
    // 펼침 버튼은 남는다
    expect(screen.getByRole('button', { name: '데모 계정으로 로그인' })).toBeInTheDocument()
  })
})
