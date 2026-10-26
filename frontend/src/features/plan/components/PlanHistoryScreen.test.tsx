import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { PlanHistoryScreen } from '@/features/plan/components/PlanHistoryScreen'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { resetMockMemberState } from '@/mocks/memberHandlers'
import { resetMockSession, setMockRole } from '@/mocks/mockSession'

/*
 * 플랜 변경 내역 화면 테스트(Task 070). API는 앱의 목업 핸들러(MSW, GET /api/me/plan-changes)다.
 * 확인하는 것: API 값대로 최신순 기록이 보이고 첫 건만 펼쳐지는지빈 응답과 오류 응답에서 각 상태 화면이 나오는지,
 * 다 봤음 안내 문장과 출처가 남아 있는지.
 * 막으려는 사고: 레이아웃을 바꾸다 출처·기준일·사용자 확정 안내 문장이 빠지는 것(frontend.md 화면 표시 규칙).
 * 시나리오(?mockScenario=)는 목업 모드의 요청 인터셉터가 window 주소에서 읽으므로 history.pushState로 고른다(PlanScreen.test.tsx와 같은 방식).
 */
const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
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
  resetMockMemberState()
  resetMockWishlist()
})
afterAll(() => server.close())

function renderHistory(scenario?: 'empty' | 'error') {
  window.history.pushState(
    {},
    '',
    scenario ? `/plan/history?mockScenario=${scenario}` : '/plan/history',
  )
  const router = createMemoryRouter([{ path: '/plan/history', element: <PlanHistoryScreen /> }], {
    initialEntries: ['/plan/history'],
  })
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
}

describe('플랜 변경 내역', () => {
  it('API 값대로 최신 1건만 펼쳐져 이유·달별 변화·기준일이 보이고, 절감액 변화는 없다', async () => {
    const user = userEvent.setup()
    renderHistory()

    expect(screen.getByRole('heading', { level: 1, name: '플랜 변경 내역' })).toBeInTheDocument()
    // 사용자 확정 안내 문장과 찜 목록 이동 링크
    expect(
      screen.getByText('다 봤음으로 체크하면 보다 정확한 구독계산이 가능해요'),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '찜 목록에서 체크하기' })).toHaveAttribute(
      'href',
      '/wishlist',
    )
    expect(screen.getByText(/제공처 정보 출처/)).toBeInTheDocument()

    // 최신순 3건: 첫 건만 펼침, 나머지 2건은 접힘
    expect(await screen.findByText('변경 3건 · 최신순')).toBeInTheDocument()
    const expanded = screen.getAllByRole('button', { expanded: true })
    const collapsed = screen.getAllByRole('button', { expanded: false })
    expect(expanded).toHaveLength(1)
    expect(collapsed).toHaveLength(2)
    expect(expanded[0]).toHaveTextContent('10월 구독이 넷플릭스에서 디즈니+로 바뀌었어요')

    const panel = document.getElementById(expanded[0].getAttribute('aria-controls') ?? '')
    expect(panel).not.toBeNull()
    const region = within(panel as HTMLElement)
    expect(region.getByRole('heading', { level: 3, name: '바뀐 이유' })).toBeInTheDocument()
    expect(region.getByText(/카지노 시즌 1'을 10월부터 디즈니\+에서/)).toBeInTheDocument()
    expect(region.getByText('결제 달')).toBeInTheDocument()
    expect(region.getByText(/3,600원 줄었어요/)).toBeInTheDocument()
    // 새 플랜에만 있는 달(12월): 이전 쪽은 "해당 없음"이고 차이를 내지 않는다. 번들 상품은 이름만 보인다
    expect(region.getByText('새로 생긴 달')).toBeInTheDocument()
    expect(region.getByText('해당 없음')).toBeInTheDocument()
    expect(region.getByText(/새로 생긴 달이라 비교하지 않아요/)).toBeInTheDocument()
    expect(region.getByText('넷플릭스·티빙 번들')).toBeInTheDocument()
    expect(region.queryByText('넷플릭스 + 티빙')).not.toBeInTheDocument()
    expect(region.getByText(/데이터 기준일/)).toBeInTheDocument()
    expect(screen.queryByText(/절감액/)).not.toBeInTheDocument()

    // 접힌 항목을 누르면 펼쳐진다
    await user.click(collapsed[0])
    expect(collapsed[0]).toHaveAttribute('aria-expanded', 'true')
  })

  it('변경 내역이 없으면 빈 상태 안내가 보인다', async () => {
    renderHistory('empty')

    expect(await screen.findByText('아직 변경 내역이 없어요')).toBeInTheDocument()
    expect(screen.queryAllByRole('button', { expanded: false })).toHaveLength(0)
    // 빈 상태에서도 다 봤음 안내는 남는다
    expect(
      screen.getByText('다 봤음으로 체크하면 보다 정확한 구독계산이 가능해요'),
    ).toBeInTheDocument()
  })

  it('조회에 실패하면 오류 안내와 다시 시도 버튼이 보인다', async () => {
    renderHistory('error')

    expect(await screen.findByText('변경 내역을 불러오지 못했어요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /다시 시도/ })).toBeInTheDocument()
  })
})
