import { render, screen, within } from '@testing-library/react'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { RootLayout } from '@/app/RootLayout'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { resetMockSession, setMockRole } from '@/mocks/mockSession'

/*
 * 공통 틀(RootLayout) 푸터 링크 테스트.
 * 확인하는 것: 개인정보처리방침 링크는 누구에게나 보이고, 회원 탈퇴 링크는 로그인한 사용자에게만 보인다.
 * 막으려는 사고: 비로그인 방문자에게 탈퇴 링크가 보이는 것(눌러도 로그인 화면으로 갈 뿐이라 혼란), 방침 링크가 빠져 Google 동의 화면 게시 요건을 놓치는 것.
 * 로그인 상태는 목업 모드의 역할 전환(setMockRole)으로 만든다(hooks/useSession이 목업 모드에서는 이 값을 읽는다).
 */

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' })) // 핸들러 없는 요청이 실제 네트워크로 나가지 않게 막는다
beforeEach(() => vi.stubEnv('VITE_USE_MOCK', 'true'))
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockSession()
  resetMockWishlist()
})
afterAll(() => server.close())

// 공통 틀만 열고 본문은 빈 자리표시로 둔다
function renderLayout() {
  const router = createMemoryRouter(
    [{ element: <RootLayout />, children: [{ path: '/', element: <h1>본문</h1> }] }],
    { initialEntries: ['/'] },
  )
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
}

describe('푸터 링크', () => {
  it('비로그인이면 개인정보처리방침 링크는 보이고 회원 탈퇴 링크는 없다', async () => {
    setMockRole('GUEST')
    renderLayout()
    const footer = await screen.findByRole('contentinfo')

    expect(within(footer).getByRole('link', { name: '개인정보처리방침' })).toHaveAttribute(
      'href',
      '/privacy',
    )
    expect(within(footer).queryByRole('link', { name: '회원 탈퇴' })).not.toBeInTheDocument()
  })

  it('로그인했으면 개인정보처리방침과 회원 탈퇴 링크가 모두 보인다', async () => {
    setMockRole('USER')
    renderLayout()
    const footer = await screen.findByRole('contentinfo')

    expect(within(footer).getByRole('link', { name: '개인정보처리방침' })).toHaveAttribute(
      'href',
      '/privacy',
    )
    expect(within(footer).getByRole('link', { name: '회원 탈퇴' })).toHaveAttribute(
      'href',
      '/settings/withdraw',
    )
  })
})
