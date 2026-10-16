import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { customInstance } from '@/api/http'
import { Providers } from '@/app/providers'
import { PlanScreen } from '@/features/plan/components/PlanScreen'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { resetMockMemberState } from '@/mocks/memberHandlers'
import { resetMockSession, setMockOnboardingDone, setMockRole } from '@/mocks/mockSession'

/*
 * 플랜 결과 화면 테스트(Task 028). 저장 중 409(데이터 갱신)가 오면 자동으로 다시 계산해 안내 띠를 보이고 두 번째 저장이 성공하는지,
 * 세 유형의 선택이 같으면 카드 한 장으로 합쳐 보이는지를 본다. API는 앱의 목업 핸들러(MSW)다.
 *
 * 시나리오(?mockScenario=)는 목업 모드에서 요청 인터셉터(api/mockHeaders.ts)가 화면 주소(window.location)에서 읽어 헤더(X-Mock-Scenario)로 싣는다. 메모리 라우터는 window 주소를 바꾸지 않으므로
 * 테스트에서는 history.pushState로 주소를 직접 바꿔 시나리오를 고른다.
 */

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true')
  setMockRole('USER')
  setMockOnboardingDone(true)
})
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  window.history.pushState({}, '', '/') // 시나리오 주소를 지운다
  resetMockSession()
  resetMockMemberState()
  resetMockWishlist()
})
afterAll(() => server.close())

// 결과 화면만 연다. 새로고침·재방문을 흉내 내려고 같은 서버 상태(목업)에서 새 화면(새 QueryClient)을 다시 그릴 때도 쓴다
function renderPlanScreen() {
  const router = createMemoryRouter([{ path: '/plan', element: <PlanScreen /> }], {
    initialEntries: ['/plan'],
  })
  return render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
}

// 시나리오를 고른 뒤, 찜 하나를 만들고 플랜을 한 번 계산해 둔 상태에서 결과 화면을 연다(계산 전이면 "아직 계산한 플랜이 없어요"만 보인다)
async function renderPlanAfterCalculate(scenario: 'stale' | 'same' | null) {
  window.history.pushState({}, '', scenario ? `/plan?mockScenario=${scenario}` : '/plan')
  await customInstance({
    url: '/me/wishlist-items',
    method: 'POST',
    data: { mediaType: 'movie', tmdbId: 496243, seasonNumber: null, priority: 'MUST' },
  })
  await customInstance({ url: '/me/plan-drafts', method: 'POST' })

  renderPlanScreen()
  await screen.findByRole('heading', { level: 2, name: '플랜 유형 고르기' })
}

describe('플랜 결과 화면', () => {
  it('저장 중 데이터가 갱신되면(409) 자동으로 다시 계산해 안내 띠를 보이고, 다시 저장하면 성공한다', async () => {
    const user = userEvent.setup()
    window.scrollTo = vi.fn() // jsdom에는 scrollTo가 없어 호출만 흉내 낸다(재계산 뒤 맨 위로 올리는 동작)
    await renderPlanAfterCalculate('stale')

    await user.click(screen.getByRole('button', { name: /플랜으로 저장하기$/ }))

    // 첫 저장은 409 → 훅이 같은 자리에서 다시 계산한다. 저장이 끝난 것이 아니라 확인을 요청하는 안내가 뜬다
    expect(
      await screen.findByText('데이터가 바뀌어 다시 계산했어요. 결과를 확인하고 저장해 주세요'),
    ).toBeInTheDocument()
    expect(screen.queryByText('추천형 플랜을 저장했어요')).not.toBeInTheDocument()

    // 새 결과를 확인하고 다시 저장하면 이번에는 성공한다
    await user.click(screen.getByRole('button', { name: /플랜으로 저장하기$/ }))
    expect(await screen.findByText('추천형 플랜을 저장했어요')).toBeInTheDocument()
  })

  it('세 유형의 선택이 같으면 카드 한 장에 이름을 함께 쓰고 "세 기준 모두 같은 결과"를 알린다', async () => {
    await renderPlanAfterCalculate('same')

    expect(screen.getAllByRole('radio')).toHaveLength(1)
    expect(
      screen.getByRole('heading', { level: 3, name: '추천형 · 절약형 · 간편형' }),
    ).toBeVisible()
    expect(screen.getByText('세 기준 모두 같은 결과')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: '추천형 · 절약형 · 간편형 플랜으로 저장하기' }),
    ).toBeEnabled()
  })

  it('저장하면 완료 안내와 "내 현재 플랜"이 보이고, 새로고침해도 서버의 현재 플랜이 복원된다', async () => {
    const user = userEvent.setup()
    window.scrollTo = vi.fn()
    await renderPlanAfterCalculate(null)

    await user.click(screen.getByRole('button', { name: /플랜으로 저장하기$/ }))
    expect(await screen.findByText('추천형 플랜을 저장했어요')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: '내 현재 플랜 · 추천형' })).toBeVisible()

    // 새로고침: 화면을 지우고 새 QueryClient로 다시 그려도 서버 상태(GET)에서 같은 플랜이 나온다. 저장 직후 안내 띠는 없다
    cleanup()
    renderPlanScreen()
    expect(
      await screen.findByRole('heading', { level: 2, name: '내 현재 플랜 · 추천형' }),
    ).toBeVisible()
    expect(screen.queryByText('추천형 플랜을 저장했어요')).not.toBeInTheDocument()
  })

  it('저장하려던 초안이 서버에 없으면(404) 안내를 보이고 목록을 다시 불러온다', async () => {
    const user = userEvent.setup()
    await renderPlanAfterCalculate(null)

    // 화면이 보는 사이 다른 곳에서 다시 계산해 서버의 초안 ID가 바뀐 상황
    await customInstance({ url: '/me/plan-drafts', method: 'POST' })
    await user.click(screen.getByRole('button', { name: /플랜으로 저장하기$/ }))

    expect(await screen.findByText('저장하려던 플랜을 찾지 못했어요')).toBeInTheDocument()
    // 새로 불러온 초안으로 다시 고를 수 있다
    await user.click(await screen.findByRole('button', { name: /플랜으로 저장하기$/ }))
    expect(await screen.findByText('추천형 플랜을 저장했어요')).toBeInTheDocument()
  })

  it('초안도 현재 플랜도 없으면 계산 안내를 보인다', async () => {
    renderPlanScreen()

    expect(await screen.findByText('아직 계산한 플랜이 없어요')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '요금 확인하고 계산하기' })).toBeVisible()
  })

  it('선택이 서로 다르면 유형마다 카드가 3장이다', async () => {
    await renderPlanAfterCalculate(null)

    expect(screen.getAllByRole('radio')).toHaveLength(3)
    expect(screen.queryByText('세 기준 모두 같은 결과')).not.toBeInTheDocument()
  })
})
