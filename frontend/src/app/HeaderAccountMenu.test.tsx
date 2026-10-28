import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http as mswHttp, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { HeaderAccountMenu } from '@/app/HeaderAccountMenu'
import { Providers } from '@/app/providers'
import { useAuthStore } from '@/stores/authStore'

/*
 * "내 메뉴" 드롭다운 테스트. 확인하는 것: 열면 항목과 이동 경로가 보인다, 관리 화면은 ADMIN에게만 보인다,
 * 현재 화면 항목만 aria-current="page"이고 앞부분이 겹치는 경로는 정확히 같을 때만 현재로 본다, 로그아웃 항목이 로그아웃을 부른다.
 * (비로그인이면 메뉴가 없다는 확인은 auth/authFlow.test의 머리글 전환 테스트에 있다)
 */

const server = setupServer()
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterAll(() => server.close())
beforeEach(() => useAuthStore.getState().setSession('t', 'USER'))
afterEach(() => {
  server.resetHandlers()
  useAuthStore.getState().clearSession()
})

// 메뉴만 단독으로 그린다. 첫 화면('/')은 로그아웃 뒤 이동 확인용 자리표시다
function renderMenu(path: string, isAdmin = false, isDemo = false) {
  const router = createMemoryRouter(
    [
      { path: '/', element: <h1>첫 화면</h1> },
      {
        path: '*',
        element: (
          <HeaderAccountMenu isAdmin={isAdmin} isDemo={isDemo} wishlistCount={12} search="" />
        ),
      },
    ],
    { initialEntries: [path] },
  )
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
  return router
}

describe('HeaderAccountMenu', () => {
  it('열면 묶음별 항목이 보이고 각 항목이 알맞은 경로로 연결된다(관리 화면은 없다)', async () => {
    const user = userEvent.setup()
    renderMenu('/somewhere')

    await user.click(screen.getByRole('button', { name: '내 메뉴' }))

    const hrefOf = (name: string) => screen.getByRole('menuitem', { name }).getAttribute('href')
    expect(hrefOf('내 플랜')).toBe('/plan')
    expect(hrefOf('플랜 변경 내역')).toBe('/plan/history')
    expect(hrefOf('내 설정')).toBe('/settings')
    expect(hrefOf('요금 확인')).toBe('/pricing')
    expect(hrefOf('찜 목록, 12개')).toBe('/wishlist')
    expect(hrefOf('알림 설정, 준비 중')).toBe('/settings/notifications')
    expect(screen.getByRole('menuitem', { name: '로그아웃' })).toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: '관리 화면' })).not.toBeInTheDocument()
  })

  it('ADMIN이면 관리 화면 항목이 더해진다', async () => {
    const user = userEvent.setup()
    renderMenu('/somewhere', true)

    await user.click(screen.getByRole('button', { name: '내 메뉴' }))

    expect(screen.getByRole('menuitem', { name: '관리 화면' })).toHaveAttribute(
      'href',
      '/admin/collect',
    )
  })

  // 데모 계정(Task 109): 지금 공용 계정이라는 표시와, 조회만 되는 관리 화면 항목이 보인다
  it('데모 계정이면 맨 위에 "데모 계정" 표시가 있고 관리 화면이 "조회만"으로 보인다', async () => {
    const user = userEvent.setup()
    renderMenu('/somewhere', false, true)

    await user.click(screen.getByRole('button', { name: '내 메뉴' }))

    expect(screen.getByText('데모 계정')).toBeInTheDocument()
    expect(screen.getByText('공용 계정으로 로그인했어요')).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: '관리 화면, 조회만 가능' })).toHaveAttribute(
      'href',
      '/admin/collect',
    )
  })

  it('현재 화면 항목만 aria-current="page"이고, 앞부분이 겹치는 경로는 정확히 같을 때만 현재로 본다', async () => {
    const user = userEvent.setup()
    renderMenu('/settings/notifications')

    await user.click(screen.getByRole('button', { name: '내 메뉴' }))

    expect(screen.getByRole('menuitem', { name: '알림 설정, 준비 중' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    // /settings는 /settings/notifications의 앞부분이지만 현재 화면이 아니다
    expect(screen.getByRole('menuitem', { name: '내 설정' })).not.toHaveAttribute('aria-current')
    expect(screen.getByRole('menuitem', { name: '내 플랜' })).not.toHaveAttribute('aria-current')
  })

  it('로그아웃 항목을 고르면 로그아웃하고 토큰을 비운 뒤 첫 화면으로 간다', async () => {
    const user = userEvent.setup()
    let logoutCalls = 0
    server.use(
      mswHttp.post('/api/auth/logout', () => {
        logoutCalls += 1
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderMenu('/somewhere')

    await user.click(screen.getByRole('button', { name: '내 메뉴' }))
    await user.click(screen.getByRole('menuitem', { name: '로그아웃' }))

    expect(await screen.findByRole('heading', { name: '첫 화면' })).toBeInTheDocument()
    // 서버에 Refresh Token 폐기를 요청했는지(핸들러만 있으면 호출 여부는 알 수 없다)
    expect(logoutCalls).toBe(1)
    expect(useAuthStore.getState()).toMatchObject({ status: 'guest', accessToken: null })
  })
})
