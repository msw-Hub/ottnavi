import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { RouteErrorBoundary } from '@/app/RouteErrorBoundary'

/*
 * 화면 오류 경계 테스트. 화면을 그리다 오류가 나면 "새로고침"(주) → "홈으로 이동"(보조) 순서로 다음 행동을 보이는지,
 * 404 응답 오류면 새로고침 없이 "홈으로 이동"만 보이는지 확인한다.
 * 막으려는 사고: 오류 화면에 갈 곳이 없어 사용자가 막히는 일, Tab 순서가 보이는 순서와 달라지는 일.
 */

// 그리자마자 오류를 던지는 화면(오류 경계로 보내기 위한 대역)
function BrokenScreen(): never {
  throw new Error('일부러 낸 렌더 오류')
}

// path: 처음 열 주소. 경로표에 없는 주소를 열면 라우터가 404 응답 오류(isRouteErrorResponse)를 만들어 경계로 보낸다
function renderBoundary(path = '/') {
  const router = createMemoryRouter(
    [{ path: '/', Component: BrokenScreen, ErrorBoundary: RouteErrorBoundary }],
    { initialEntries: [path] },
  )
  render(<RouterProvider router={router} />)
}

beforeEach(() => {
  // 경계가 원인을 console.error로 남기고 React도 오류를 찍는다. 예상된 출력이라 테스트 로그를 어지럽히지 않게 막는다
  vi.spyOn(console, 'error').mockImplementation(() => {})
})
afterEach(() => vi.restoreAllMocks())

describe('화면 오류 경계', () => {
  it('렌더 오류면 새로고침 → 홈으로 이동 순서로 Tab이 가고, 새로고침을 누르면 페이지를 다시 불러온다', async () => {
    const user = userEvent.setup()
    // jsdom의 location.reload는 바꿀 수 없어 location 객체를 통째로 대역으로 바꾼다(이 테스트 안에서만)
    const reload = vi.fn()
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, reload })
    renderBoundary()

    expect(
      await screen.findByRole('heading', { level: 1, name: '화면을 표시하지 못함' }),
    ).toBeInTheDocument()
    const refreshButton = screen.getByRole('button', { name: '새로고침' })
    const homeLink = screen.getByRole('link', { name: '홈으로 이동' })
    expect(homeLink).toHaveAttribute('href', '/')

    await user.tab()
    expect(refreshButton).toHaveFocus()
    await user.tab()
    expect(homeLink).toHaveFocus()

    await user.click(refreshButton)
    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('404 응답 오류면 새로고침 없이 홈으로 이동만 보인다', async () => {
    renderBoundary('/no-such-page')

    expect(
      await screen.findByRole('heading', { level: 1, name: '페이지를 찾을 수 없음' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '홈으로 이동' })).toHaveAttribute('href', '/')
    expect(screen.queryByRole('button', { name: '새로고침' })).not.toBeInTheDocument()
  })
})
