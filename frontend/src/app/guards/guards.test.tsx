import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { http as mswHttp, HttpResponse } from 'msw'
import { createMemoryRouter, RouterProvider, useLocation, type RouteObject } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { Providers } from '@/app/providers'
import { RequireAdmin } from '@/app/guards/RequireAdmin'
import { RequireAuth } from '@/app/guards/RequireAuth'
import { useLogout } from '@/hooks/useLogout'
import { AuthCallbackScreen } from '@/features/auth/components/AuthCallbackScreen'
import { saveLoginReturnTo } from '@/lib/loginReturn'
import { useAuthStore } from '@/stores/authStore'

/*
 * 라우트 가드 테스트(Task 057). 목업 모드가 아닌 실제 인증 경로(authStore)로 확인한다.
 * 확인하는 것: 비로그인 → 로그인 화면(+원래 경로 from), 확인 중 → 로딩, 재발급 오류 → 로그인 화면으로 보내지 않고 다시 시도,
 * 일반 사용자의 관리자 화면 차단(권한 없음 안내), 관리자 통과, 콜백 화면의 원래 화면 복귀.
 */

const server = setupServer()
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterAll(() => server.close())
beforeEach(() => {
  useAuthStore.getState().clearSession()
  window.sessionStorage.clear()
})
afterEach(() => server.resetHandlers())

// 로그인 화면 대역: 가드가 넘긴 돌아갈 곳(from)을 화면에 적어 확인한다
function LoginProbe() {
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? ''
  return (
    <div>
      <h1>로그인 화면</h1>
      <p>from={from}</p>
    </div>
  )
}

// 보호 화면 안의 로그아웃 버튼 대역(머리글 버튼과 같은 useLogout을 쓴다)
function LogoutProbe() {
  const logout = useLogout()
  return (
    <div>
      <h1>보호 화면</h1>
      <button type="button" onClick={() => void logout()}>
        로그아웃
      </button>
    </div>
  )
}

const testRoutes: RouteObject[] = [
  { path: '/', element: <h1>첫 화면</h1> },
  { path: '/login', element: <LoginProbe /> },
  { path: '/auth/callback', element: <AuthCallbackScreen /> },
  { path: '/settings', element: <h1>내 설정</h1> },
  {
    Component: RequireAuth,
    children: [
      { path: '/wishlist', element: <h1>찜 목록</h1> },
      { path: '/probe', element: <LogoutProbe /> },
    ],
  },
  {
    Component: RequireAdmin,
    children: [{ path: '/admin/collect', element: <h1>수집 화면</h1> }],
  },
]

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

describe('RequireAuth', () => {
  it('비로그인이면 로그인 화면으로 보내고, 가려던 경로를 from으로 넘긴다', async () => {
    useAuthStore.getState().clearSession()
    const router = renderAt('/wishlist?x=1')

    expect(await screen.findByRole('heading', { name: '로그인 화면' })).toBeInTheDocument()
    expect(screen.getByText('from=/wishlist?x=1')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('로그인 확인 중(checking)이면 로딩을 보이고, 확인이 끝나 로그인이면 화면을 그린다', async () => {
    useAuthStore.setState({ status: 'checking', accessToken: null, role: null })
    renderAt('/wishlist')

    expect(await screen.findByText('로그인 상태를 확인하는 중입니다')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '로그인 화면' })).not.toBeInTheDocument()

    useAuthStore.getState().setSession('t', 'USER')
    expect(await screen.findByRole('heading', { name: '찜 목록' })).toBeInTheDocument()
  })

  it('재발급이 오류(error)면 로그인 화면으로 보내지 않고 "다시 시도"를 보인다', async () => {
    const user = userEvent.setup()
    useAuthStore.setState({ status: 'error', accessToken: null, role: null })
    server.use(
      mswHttp.post('/api/auth/refresh', () =>
        HttpResponse.json(
          { success: true, data: { accessToken: 'again', role: 'USER' } },
          { status: 200 },
        ),
      ),
    )
    renderAt('/wishlist')

    expect(await screen.findByText('로그인 상태를 확인하지 못했습니다')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '로그인 화면' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('heading', { name: '찜 목록' })).toBeInTheDocument()
  })

  it('로그인했으면 보호 화면을 그린다', async () => {
    useAuthStore.getState().setSession('t', 'USER')
    renderAt('/wishlist')

    expect(await screen.findByRole('heading', { name: '찜 목록' })).toBeInTheDocument()
  })
})

describe('로그아웃', () => {
  it('보호 화면에서 로그아웃하면 로그인 화면이 아니라 첫 화면으로 가고 토큰을 비운다', async () => {
    const user = userEvent.setup()
    server.use(mswHttp.post('/api/auth/logout', () => new HttpResponse(null, { status: 204 })))
    useAuthStore.getState().setSession('t', 'USER')
    const router = renderAt('/probe')

    await user.click(await screen.findByRole('button', { name: '로그아웃' }))

    expect(await screen.findByRole('heading', { name: '첫 화면' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
    expect(useAuthStore.getState()).toMatchObject({
      status: 'guest',
      accessToken: null,
      role: null,
    })
  })
})

describe('RequireAdmin', () => {
  it('일반 사용자(USER)는 관리자 화면 대신 권한 없음 안내와 홈 링크를 본다', async () => {
    useAuthStore.getState().setSession('t', 'USER')
    renderAt('/admin/collect')

    expect(await screen.findByRole('heading', { name: '접근 권한이 없습니다' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '수집 화면' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: '홈으로 이동' })).toHaveAttribute('href', '/')
  })

  it('관리자(ADMIN)는 통과한다', async () => {
    useAuthStore.getState().setSession('t', 'ADMIN')
    renderAt('/admin/collect')

    expect(await screen.findByRole('heading', { name: '수집 화면' })).toBeInTheDocument()
  })

  it('비로그인이면 권한 없음이 아니라 로그인 화면으로 보낸다', async () => {
    renderAt('/admin/collect')

    expect(await screen.findByRole('heading', { name: '로그인 화면' })).toBeInTheDocument()
    expect(screen.getByText('from=/admin/collect')).toBeInTheDocument()
  })
})

describe('로그인 콜백(실제 환경)', () => {
  // isOnboardingDone: 로그인 직후 내 설정 조회(GET /api/me/settings)가 돌려줄 값. 콜백이 이 값으로 온보딩(/settings) 이동을 정한다(Task 058)
  function useRefreshSuccess(isOnboardingDone = true) {
    server.use(
      mswHttp.post('/api/auth/refresh', () =>
        HttpResponse.json({ success: true, data: { accessToken: 'tok', role: 'USER' } }),
      ),
      mswHttp.get('/api/me/settings', () =>
        HttpResponse.json({
          success: true,
          data: {
            isOnboardingDone,
            subscriptions: [],
            monthlyBudget: null,
            monthlyWatchMinutes: null,
            watchPreset: null,
          },
        }),
      ),
    )
  }

  it('재발급에 성공하면 로그인 화면이 보관해 둔 원래 화면으로 돌아간다', async () => {
    useRefreshSuccess()
    saveLoginReturnTo('/wishlist')
    const router = renderAt('/auth/callback')

    expect(await screen.findByRole('heading', { name: '찜 목록' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/wishlist')
    expect(useAuthStore.getState().status).toBe('authenticated')
    // 보관값은 이동 뒤 지워진다
    expect(window.sessionStorage.getItem('ottnavi:login-return-to')).toBeNull()
  })

  it('보관된 값이 외부 주소 같은 안전하지 않은 값이면 따르지 않고 첫 화면으로 간다', async () => {
    useRefreshSuccess()
    window.sessionStorage.setItem('ottnavi:login-return-to', 'https://evil.example/')
    renderAt('/auth/callback')

    expect(await screen.findByRole('heading', { name: '첫 화면' })).toBeInTheDocument()
  })

  it('첫 로그인(내 설정의 isOnboardingDone=false)이면 원래 화면 대신 내 설정(온보딩)으로 가고 돌아갈 곳을 넘긴다', async () => {
    useRefreshSuccess(false)
    saveLoginReturnTo('/wishlist')
    const router = renderAt('/auth/callback')

    expect(await screen.findByRole('heading', { name: '내 설정' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/settings')
    expect(router.state.location.state).toEqual({ from: '/wishlist' })
  })

  it('?error=login_failed면 재발급하지 않고 실패 안내를 보인다', async () => {
    renderAt('/auth/callback?error=login_failed')

    expect(await screen.findByText('다시 로그인해 주세요')).toBeInTheDocument()
    expect(useAuthStore.getState().status).toBe('guest')
  })

  it('재발급이 실패하면 실패 안내를 보인다', async () => {
    server.use(
      mswHttp.post('/api/auth/refresh', () =>
        HttpResponse.json(
          {
            title: 'x',
            status: 401,
            detail: 'd',
            instance: '/api/auth/refresh',
            errorCode: 'UNAUTHORIZED',
          },
          { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    renderAt('/auth/callback')

    expect(await screen.findByText('다시 로그인해 주세요')).toBeInTheDocument()
  })
})
