import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { toast } from 'sonner'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { RequireAuth } from '@/app/guards/RequireAuth'
import { getErrorMessage } from '@/lib/errorMessages'
import { WithdrawPage } from '@/pages/WithdrawPage'
import { useAuthStore } from '@/stores/authStore'

/*
 * 회원 탈퇴 흐름 테스트: 실제 API 모드(Task 084). VITE_USE_MOCK을 켜지 않고, 계약(DELETE /api/me → 204, 401, 429, 500)과
 * 같은 응답을 이 파일의 MSW 핸들러가 직접 돌려준다. 로그인 상태는 앱의 목업 역할이 아니라 메모리 토큰(authStore)이 정한다.
 * 확인하는 것: 요청에 Access Token이 실리는지, 204면 토큰·화면 정리 후 첫 화면, 401이면 재발급을 한 번 시도하는 흐름
 * (재발급도 401이면 로그인 화면, 재발급이 되면 같은 요청을 다시 보내 성공), 429·500이면 대화상자 안 안내와 로그인 상태 유지.
 * 막으려는 사고: 실패했는데 로그인 상태만 지워지는 것, 401을 무한 재시도하는 것, 완료 토스트가 실패 때 뜨는 것.
 * (rt 쿠키 만료는 브라우저 몫이라 여기서 확인하지 못한다. 서버 테스트와 실서버 확인 대상이다.)
 */

const server = setupServer()
// 완료 토스트를 불렀는지 본다. sonner의 토스트 목록은 모듈 전역이라 앞 테스트의 토스트가 화면에 남아, 글자로는 "안 떴다"를 확인할 수 없다
let successToastSpy: ReturnType<typeof vi.spyOn>

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'false')
  successToastSpy = vi.spyOn(toast, 'success')
  useAuthStore.getState().setSession('real-access-token', 'USER')
})
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  successToastSpy.mockRestore()
  window.history.pushState({}, '', '/')
  useAuthStore.getState().clearSession()
  useAuthStore.getState().setSigningOut(false)
})
afterAll(() => server.close())

// 서버가 돌려주는 ProblemDetail 오류 응답(계약의 오류 형식)
function problem(status: number, errorCode: string) {
  return HttpResponse.json(
    { type: 'about:blank', title: 'error', status, detail: '서버 설명', errorCode },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

function renderWithdraw() {
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

describe('회원 탈퇴 흐름(실제 API 모드)', () => {
  it('204면 Access Token을 실어 보낸 요청이 성공해 토큰을 비우고 첫 화면으로 간다', async () => {
    let sentAuthorization: string | null = null
    server.use(
      http.delete('/api/me', ({ request }) => {
        sentAuthorization = request.headers.get('Authorization')
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const user = userEvent.setup()
    const router = renderWithdraw()

    await confirmWithdraw(user)

    expect(await screen.findByRole('heading', { name: '첫 화면' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
    expect(sentAuthorization).toBe('Bearer real-access-token')
    expect(await screen.findByText('탈퇴가 완료됐어요')).toBeInTheDocument()
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useAuthStore.getState().status).toBe('guest')
  })

  it('401이고 재발급도 401이면 로그인 화면으로 가며 완료 토스트는 뜨지 않는다', async () => {
    let deleteCount = 0
    server.use(
      http.delete('/api/me', () => {
        deleteCount += 1
        return problem(401, 'UNAUTHORIZED')
      }),
      http.post('/api/auth/refresh', () => problem(401, 'UNAUTHORIZED')),
    )
    const user = userEvent.setup()
    const router = renderWithdraw()

    await confirmWithdraw(user)

    // 재발급 실패로 가드가 비로그인을 보고 로그인 화면으로 보낸다(탈퇴가 끝난 것처럼 첫 화면으로 가지 않는다)
    expect(await screen.findByRole('heading', { name: '로그인 화면' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(deleteCount).toBe(1) // 재발급이 실패했으므로 같은 요청을 다시 보내지 않는다
    expect(successToastSpy).not.toHaveBeenCalled()
    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it('401이어도 재발급이 되면 새 토큰으로 한 번만 다시 보내 성공한다', async () => {
    const sentAuthorizations: Array<string | null> = []
    server.use(
      http.delete('/api/me', ({ request }) => {
        sentAuthorizations.push(request.headers.get('Authorization'))
        return sentAuthorizations.length === 1
          ? problem(401, 'UNAUTHORIZED')
          : new HttpResponse(null, { status: 204 })
      }),
      http.post('/api/auth/refresh', () =>
        HttpResponse.json({ success: true, data: { accessToken: 'renewed-token', role: 'USER' } }),
      ),
    )
    const user = userEvent.setup()
    renderWithdraw()

    await confirmWithdraw(user)

    expect(await screen.findByRole('heading', { name: '첫 화면' })).toBeInTheDocument()
    expect(sentAuthorizations).toEqual(['Bearer real-access-token', 'Bearer renewed-token'])
    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it.each([
    [429, 'RATE_LIMIT_EXCEEDED'],
    [500, 'INTERNAL_ERROR'],
  ] as const)(
    '%i이면 이동·정리 없이 대화상자 안에 안내가 남고 다시 시도할 수 있다',
    async (status, errorCode) => {
      server.use(http.delete('/api/me', () => problem(status, errorCode)))
      const user = userEvent.setup()
      const router = renderWithdraw()

      const dialog = await confirmWithdraw(user)

      expect(await within(dialog).findByRole('alert')).toHaveTextContent(getErrorMessage(errorCode))
      expect(router.state.location.pathname).toBe('/settings/withdraw')
      expect(useAuthStore.getState().accessToken).toBe('real-access-token')
      expect(successToastSpy).not.toHaveBeenCalled()
      expect(within(dialog).getByRole('button', { name: '탈퇴' })).toBeEnabled()
    },
  )
})
