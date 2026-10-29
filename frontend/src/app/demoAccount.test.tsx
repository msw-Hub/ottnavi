import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminLayout } from '@/app/AdminLayout'
import { RequireAdmin } from '@/app/guards/RequireAdmin'
import { Providers } from '@/app/providers'
import { RootLayout } from '@/app/RootLayout'
import { CollectScreen } from '@/features/admin/components/collect/CollectScreen'
import { resetMockAdminState } from '@/mocks/adminHandlers'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { resetMockSession, switchMockRole } from '@/mocks/mockSession'
import { WithdrawPage } from '@/pages/WithdrawPage'

/*
 * 데모 계정으로 로그인한 뒤의 화면 테스트(Task 109). 목업 역할을 DEMO로 두고 실제 머리글(RootLayout)과 화면을 연결해 본다.
 *
 * | 확인하는 것 | 막으려는 사고 |
 * | 관리자 화면에 들어가고 조회 전용 안내가 보이며 [수집 실행]이 aria-disabled이고 눌러도 요청이 없다 | 공용 계정으로 수집을 실행해 운영 데이터를 바꾸는 것 |
 * | 탈퇴 화면에서 [탈퇴하기]가 막혀 요청이 없고 막힌 이유가 보인다, 푸터의 탈퇴 링크가 없다 | 한 방문자가 공용 계정을 지워 다른 사람이 못 쓰게 되는 것 |
 * 서버도 같은 요청을 403으로 막지만(목업 핸들러도 같다), 화면에서 먼저 막아 요청 자체가 나가지 않는지를 본다.
 */

const server = setupServer(...handlers)

// GET이 아닌 요청(쓰기·실행·삭제) 목록. "요청이 나가지 않았는지"를 보려고 모은다
let writeRequests: string[] = []

beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' })
  server.events.on('request:start', ({ request }) => {
    if (request.method !== 'GET') {
      writeRequests.push(`${request.method} ${new URL(request.url).pathname}`)
    }
  })
  // Radix 라디오 그룹(수집 범위)이 크기 감시(ResizeObserver)를 쓰는데 jsdom에는 없어 아무 일도 하지 않는 대역을 둔다
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
  switchMockRole('DEMO')
  writeRequests = []
})
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockSession()
  resetMockWishlist()
  resetMockAdminState()
})
afterAll(() => {
  server.close()
  vi.unstubAllGlobals()
})

const testRoutes: RouteObject[] = [
  {
    path: '/',
    Component: RootLayout,
    children: [
      { index: true, element: <h1>첫 화면</h1> },
      { path: 'settings/withdraw', element: <WithdrawPage /> },
      {
        Component: RequireAdmin,
        children: [
          {
            Component: AdminLayout,
            children: [{ path: 'admin/collect', element: <CollectScreen /> }],
          },
        ],
      },
    ],
  },
]

function renderAt(path: string) {
  const router = createMemoryRouter(testRoutes, { initialEntries: [path] })
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
}

describe('데모 계정의 관리자 화면', () => {
  it('들어가면 조회 전용 안내가 보이고, [수집 실행]은 막혀 눌러도 요청이 나가지 않는다', async () => {
    const user = userEvent.setup()
    renderAt('/admin/collect')

    expect(await screen.findByText('조회 전용')).toBeInTheDocument()
    const runButton = await screen.findByRole('button', { name: '수집 실행' })
    expect(runButton).toHaveAttribute('aria-disabled', 'true')
    expect(runButton).toHaveAccessibleDescription('데모 계정은 실행할 수 없어요')

    await user.click(runButton)
    // 막힌 이유를 토스트로도 알린다(터치 화면 대안)
    expect(await screen.findByText('데모 계정은 조회만 할 수 있어요')).toBeInTheDocument()
    expect(writeRequests).toEqual([])
  })
})

describe('데모 계정의 회원 탈퇴', () => {
  it('[탈퇴하기]가 막혀 확인 대화상자가 열리지 않고 요청도 나가지 않으며, 막힌 이유가 보인다', async () => {
    const user = userEvent.setup()
    renderAt('/settings/withdraw')

    expect(await screen.findByText('데모 계정은 탈퇴할 수 없어요')).toBeInTheDocument()
    expect(
      screen.getByRole('checkbox', { name: '위 내용을 확인했고 탈퇴에 동의해요' }),
    ).toBeDisabled()
    const withdrawButton = screen.getByRole('button', { name: '탈퇴하기' })
    expect(withdrawButton).toHaveAttribute('aria-disabled', 'true')

    await user.click(withdrawButton)
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(writeRequests).toEqual([])
    // 푸터의 회원 탈퇴 링크는 데모 계정에게 보이지 않는다
    expect(screen.queryByRole('link', { name: '회원 탈퇴' })).not.toBeInTheDocument()
  })
})
