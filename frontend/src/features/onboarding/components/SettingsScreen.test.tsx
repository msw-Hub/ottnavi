import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { SettingsScreen } from '@/features/onboarding/components/SettingsScreen'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { resetMockMemberState } from '@/mocks/memberHandlers'
import { getMockRole, resetMockSession, setMockRole } from '@/mocks/mockSession'

/*
 * 온보딩·내 설정 화면 테스트(Task 028). 저장에 성공하면 로그인 전에 보던 화면(location.state.from)으로 돌아가고,
 * 돌아갈 곳이 없으면 첫 화면(/)으로 가는지 본다. API는 앱의 목업 핸들러(MSW)로 대체한다.
 */

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
// 회원 상태 판단과 목업 핸들러는 목업 모드에서만 동작하므로 목업 모드를 흉내 낸다
beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true')
  setMockRole('USER') // 온보딩은 아직 하지 않은 첫 방문 회원
})
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockSession()
  resetMockMemberState()
  resetMockWishlist()
})
afterAll(() => server.close())

// 설정 화면을 열고, 돌아갈 곳을 확인할 수 있게 도착 화면 자리표시를 둔다. state가 있으면 로그인 전에 보던 화면(from)을 흉내 낸다
function renderSettings(state?: unknown) {
  const router = createMemoryRouter(
    [
      { path: '/settings', element: <SettingsScreen /> },
      { path: '/', element: <h1>첫 화면</h1> },
      { path: '/wishlist', element: <h1>찜 목록 화면</h1> },
    ],
    { initialEntries: [{ pathname: '/settings', state }] },
  )
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
  return router
}

// 첫 방문 폼에서 필수인 월 예산만 채우고 저장한다(시청 시간은 '보통'이 미리 골라져 있다)
async function fillBudgetAndSave(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole('heading', { level: 1, name: '시작하기' })
  await user.type(screen.getByLabelText(/월 예산/), '20000')
  await user.click(screen.getByRole('button', { name: '저장하기' }))
}

describe('내 설정 저장 후 이동', () => {
  it('로그인 전에 보던 화면(from)이 있으면 저장 뒤 그 화면으로 돌아간다', async () => {
    const user = userEvent.setup()
    const router = renderSettings({ from: '/wishlist' })

    await fillBudgetAndSave(user)

    expect(await screen.findByRole('heading', { name: '찜 목록 화면' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/wishlist')
    expect(getMockRole()).toBe('USER')
  })

  it('돌아갈 화면이 없으면 저장 뒤 첫 화면(/)으로 간다', async () => {
    const user = userEvent.setup()
    const router = renderSettings()

    await fillBudgetAndSave(user)

    expect(await screen.findByRole('heading', { name: '첫 화면' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })

  it('from이 사이트 밖 주소면 따르지 않고 첫 화면(/)으로 간다', async () => {
    const user = userEvent.setup()
    renderSettings({ from: 'https://evil.example/' })

    await fillBudgetAndSave(user)

    expect(await screen.findByRole('heading', { name: '첫 화면' })).toBeInTheDocument()
  })
})
