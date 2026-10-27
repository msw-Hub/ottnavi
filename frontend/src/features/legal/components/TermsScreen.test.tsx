import { render, screen } from '@testing-library/react'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { routes } from '@/app/router'
import { handlers } from '@/mocks/handlers'
import { resetMockSession, setMockRole } from '@/mocks/mockSession'

/*
 * 이용약관 화면 테스트.
 * 확인하는 것: 로그인하지 않아도 /terms가 열리고, 시행일·문의 이메일·주요 구역 제목이 보이며 푸터에서 찾아갈 수 있는지.
 * 막으려는 사고: 약관 주소가 로그인 가드 안에 들어가 방문자가 볼 수 없게 되는 것, 푸터 링크가 빠지는 것.
 */

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' })) // 핸들러 없는 요청이 실제 네트워크로 나가지 않게 막는다
beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true')
  setMockRole('GUEST')
})
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockSession()
})
afterAll(() => server.close())

describe('이용약관 화면', () => {
  it('비로그인으로 /terms를 열면 시행일·문의처·주요 구역 제목이 보이고 푸터에 링크가 있다', async () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/terms'] })
    render(
      <Providers>
        <RouterProvider router={router} />
      </Providers>,
    )

    // 1. lazy 페이지라 제목이 나타날 때까지 기다린다(로그인 화면으로 보내지지 않았다는 증거)
    expect(await screen.findByRole('heading', { level: 1, name: '이용약관' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/terms')

    // 2. 시행일과 문의 이메일
    expect(screen.getByText('2026년 10월 27일', { selector: 'time' })).toHaveAttribute(
      'dateTime',
      '2026-10-27',
    )
    expect(screen.getByRole('link', { name: 'dalsw7@gmail.com' })).toHaveAttribute(
      'href',
      'mailto:dalsw7@gmail.com',
    )

    // 3. 주요 구역 제목
    for (const name of ['1. 서비스의 내용', '2. 정보의 성격과 정확성', '3. 계정과 탈퇴']) {
      expect(screen.getByRole('heading', { level: 2, name })).toBeInTheDocument()
    }

    // 4. 푸터에 약관 링크
    expect(screen.getByRole('link', { name: '이용약관' })).toHaveAttribute('href', '/terms')
  })
})
