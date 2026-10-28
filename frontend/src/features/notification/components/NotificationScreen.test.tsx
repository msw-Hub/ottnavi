import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { NotificationScreen } from '@/features/notification/components/NotificationScreen'
import { handlers } from '@/mocks/handlers'
import { resetMockNotificationState } from '@/mocks/notificationHandlers'
import { resetMockSession, setMockRole } from '@/mocks/mockSession'

/*
 * 알림 설정·발송 이력 화면 테스트(Task 071, SCR-12). API는 앱의 목업 핸들러(MSW)다.
 * 확인하는 것: 스위치를 켜고 끌 때 저장 요청이 가고 화면이 서버 값을 따르는지, 이력의 유형·상태가 "글자"로 보이는지(색만으로 구분하지 않음),
 * 더 보기로 이어 붙는지, 빈 이력, 409 ONBOARDING_REQUIRED 안내와 설정 화면 링크, 저장 실패 시 되돌아가는지.
 * 막으려는 사고: 상태 구분이 색에만 의존하게 되는 것, 실패했는데 스위치가 켜진 채로 남아 사용자가 켜졌다고 오해하는 것.
 * 시나리오(?mockScenario=)는 목업 모드의 요청 인터셉터가 window 주소에서 읽으므로 history.pushState로 고른다(PlanHistoryScreen.test.tsx와 같은 방식).
 */
const server = setupServer(...handlers)

beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' })
  // Radix 스위치가 크기 감시(ResizeObserver)를 쓰는데 jsdom에는 없어 아무 일도 하지 않는 대역을 둔다
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  )
})
beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true')
  setMockRole('USER')
})
afterEach(() => {
  cleanup()
  server.resetHandlers()
  vi.unstubAllEnvs()
  window.history.pushState({}, '', '/')
  resetMockSession()
  resetMockNotificationState()
})
afterAll(() => {
  server.close()
  vi.unstubAllGlobals()
})

function renderNotifications(scenario?: string) {
  window.history.pushState(
    {},
    '',
    scenario ? `/settings/notifications?mockScenario=${scenario}` : '/settings/notifications',
  )
  const router = createMemoryRouter(
    [{ path: '/settings/notifications', element: <NotificationScreen /> }],
    { initialEntries: ['/settings/notifications'] },
  )
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
}

describe('알림 설정', () => {
  it('기본은 꺼짐이고, 켜고 끄면 저장한 값대로 글자가 바뀐다', async () => {
    const user = userEvent.setup()
    renderNotifications()

    const toggle = await screen.findByRole('switch', { name: '이메일 알림 받기' })
    expect(toggle).toHaveAttribute('aria-checked', 'false')
    expect(screen.getByText('지금 꺼져 있어요')).toBeInTheDocument()
    // 수신 이메일은 읽기 전용 글자이고, 동의 없이 보내지 않는다는 안내가 있다
    expect(screen.getByText('user@example.com')).toBeInTheDocument()
    expect(screen.getByText('알림을 켜기 전에는 어떤 메일도 보내지 않아요')).toBeInTheDocument()

    await user.click(toggle)
    expect(await screen.findByText('지금 켜져 있어요')).toBeInTheDocument()
    expect(toggle).toHaveAttribute('aria-checked', 'true')

    await user.click(toggle)
    expect(await screen.findByText('지금 꺼져 있어요')).toBeInTheDocument()
    expect(toggle).toHaveAttribute('aria-checked', 'false')
  })

  it('저장이 실패하면 스위치가 저장 전 값으로 되돌아가고 오류가 글자로 보인다', async () => {
    const user = userEvent.setup()
    renderNotifications('save-error')

    const toggle = await screen.findByRole('switch', { name: '이메일 알림 받기' })
    await user.click(toggle)

    expect(await screen.findByText('알림 설정을 바꾸지 못했어요')).toBeInTheDocument()
    expect(toggle).toHaveAttribute('aria-checked', 'false')
    expect(screen.getByText('지금 꺼져 있어요')).toBeInTheDocument()
  })

  it('내 설정을 저장한 적이 없으면(409) 먼저 저장하라는 안내와 설정 화면 링크가 보인다', async () => {
    const user = userEvent.setup()
    renderNotifications('onboarding-required')

    await user.click(await screen.findByRole('switch', { name: '이메일 알림 받기' }))

    expect(await screen.findByText('내 설정을 먼저 저장해 주세요.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '내 설정 입력하러 가기' })).toHaveAttribute(
      'href',
      '/settings',
    )
    expect(screen.getByRole('switch', { name: '이메일 알림 받기' })).toHaveAttribute(
      'aria-checked',
      'false',
    )
  })
})

describe('발송 이력', () => {
  it('유형과 상태가 글자로 보이고, 더 보기로 다음 페이지가 이어 붙는다', async () => {
    const user = userEvent.setup()
    renderNotifications()

    // 첫 페이지 10건: 상태 3종과 유형이 모두 글자로 나온다(색만으로 구분하지 않는다)
    expect(await screen.findByText('전체 23건 · 최신순')).toBeInTheDocument()
    const list = screen.getByRole('list', { name: '발송 이력 목록' })
    expect(within(list).getAllByRole('listitem')).toHaveLength(10)
    expect(within(list).getByText('발송 대기')).toBeInTheDocument()
    expect(within(list).getByText('발송 실패')).toBeInTheDocument()
    expect(within(list).getAllByText('발송됨').length).toBeGreaterThan(0)
    expect(within(list).getByText('가입 안내')).toBeInTheDocument()
    expect(within(list).getAllByText('플랜 변경 알림')).toHaveLength(2)
    // 발송 전·실패는 발송 시각이 없으므로 글자로 상황을 알린다
    expect(within(list).getByText('아직 발송 전이에요')).toBeInTheDocument()
    expect(within(list).getByText('발송하지 못했어요')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '발송 이력 더 보기' }))
    await vi.waitFor(() => expect(within(list).getAllByRole('listitem')).toHaveLength(20))

    await user.click(screen.getByRole('button', { name: '발송 이력 더 보기' }))
    await vi.waitFor(() => expect(within(list).getAllByRole('listitem')).toHaveLength(23))
    // 마지막 페이지까지 불러오면 버튼이 사라진다
    expect(screen.queryByRole('button', { name: '발송 이력 더 보기' })).not.toBeInTheDocument()
  })

  it('이력이 없으면 빈 상태 안내가 보인다', async () => {
    renderNotifications('empty')

    expect(await screen.findByText('아직 받은 알림이 없어요')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '발송 이력 더 보기' })).not.toBeInTheDocument()
  })

  it('이력 조회에 실패하면 오류 안내와 다시 시도 버튼이 보인다', async () => {
    renderNotifications('error')

    expect(await screen.findByText('발송 이력을 불러오지 못했어요')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /다시 시도/ }).length).toBeGreaterThan(0)
  })
})
