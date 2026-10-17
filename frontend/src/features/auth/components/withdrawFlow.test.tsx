import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { RequireAuth } from '@/app/guards/RequireAuth'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { resetMockMemberState } from '@/mocks/memberHandlers'
import {
  getMockRole,
  isMockOnboardingDone,
  resetMockSession,
  setMockRole,
} from '@/mocks/mockSession'
import { WithdrawPage } from '@/pages/WithdrawPage'
import { useAuthStore } from '@/stores/authStore'

/*
 * 회원 탈퇴 흐름 테스트(Task 081). 실제 가드(RequireAuth)·페이지·Providers를 연결하고 API는 앱의 목업 핸들러(MSW, DELETE /api/me)다.
 * 확인하는 것: 탈퇴에 성공하면 메모리 토큰이 비워지고 첫 화면(/)으로 가며 완료 토스트가 뜨는지(로그인 화면으로 새지 않는지),
 * 서버가 오류를 돌려주면 이동·정리 없이 대화상자 안에 오류 안내가 남는지.
 * 막으려는 사고: 탈퇴 직후 가드가 로그인 화면으로 보내는 것, 실패했는데 로그인 상태만 지워지는 것(데이터는 남음).
 * 시나리오(?mockScenario=)는 목업 모드의 요청 인터셉터가 window 주소에서 읽으므로 history.pushState로 고른다.
 */

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true')
  setMockRole('USER')
  // 실제 로그인한 것처럼 메모리 토큰을 둔다(목업 모드의 화면 판단은 목업 역할이 하지만, 정리 대상은 이 저장소다)
  useAuthStore.getState().setSession('mock-access-token', 'USER')
})
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  window.history.pushState({}, '', '/')
  useAuthStore.getState().clearSession()
  useAuthStore.getState().setSigningOut(false)
  resetMockSession()
  resetMockMemberState()
  resetMockWishlist()
})
afterAll(() => server.close())

function renderWithdraw(scenario?: 'error') {
  window.history.pushState(
    {},
    '',
    scenario ? `/settings/withdraw?mockScenario=${scenario}` : '/settings/withdraw',
  )
  const router = createMemoryRouter(
    [
      { path: '/', element: <h1>첫 화면</h1> },
      { path: '/login', element: <h1>로그인 화면</h1> },
      {
        Component: RequireAuth,
        children: [{ path: '/settings/withdraw', Component: WithdrawPage }],
      },
    ],
    { initialEntries: ['/settings/withdraw'] },
  )
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
  return router
}

// 동의에 체크하고 확인 대화상자에서 [탈퇴]까지 누른다
async function confirmWithdraw(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('checkbox', { name: /탈퇴에 동의해요/ }))
  await user.click(screen.getByRole('button', { name: '탈퇴하기' }))
  const dialog = await screen.findByRole('alertdialog')
  await user.click(within(dialog).getByRole('button', { name: '탈퇴' }))
  return dialog
}

describe('회원 탈퇴 흐름', () => {
  it('성공하면 토큰을 비우고 첫 화면으로 가며 완료 토스트가 뜬다(로그인 화면으로 가지 않는다)', async () => {
    const user = userEvent.setup()
    const router = renderWithdraw()

    await confirmWithdraw(user)

    expect(await screen.findByRole('heading', { name: '첫 화면' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
    expect(await screen.findByText('탈퇴가 완료됐어요')).toBeInTheDocument()
    // 메모리의 Access Token·역할과 목업 로그인 역할이 비워졌고, 다시 가입하면 온보딩부터 시작한다
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useAuthStore.getState().status).toBe('guest')
    await waitFor(() => expect(getMockRole()).toBe('GUEST'))
    expect(isMockOnboardingDone()).toBe(false)
  })

  it('서버가 오류를 돌려주면 이동하지 않고 대화상자 안에 오류 안내가 남으며 로그인 상태도 그대로다', async () => {
    const user = userEvent.setup()
    const router = renderWithdraw('error')

    const dialog = await confirmWithdraw(user)

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      '일시적인 오류가 발생했습니다.',
    )
    expect(router.state.location.pathname).toBe('/settings/withdraw')
    expect(useAuthStore.getState().accessToken).toBe('mock-access-token')
    expect(getMockRole()).toBe('USER')
    // 실패 뒤에는 버튼이 다시 열려 같은 화면에서 재시도할 수 있다
    expect(within(dialog).getByRole('button', { name: '탈퇴' })).toBeEnabled()
  })
})
