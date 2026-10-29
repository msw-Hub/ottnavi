import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { customInstance } from '@/api/http'
import { Providers } from '@/app/providers'
import { PlanScreen } from '@/features/plan/components/PlanScreen'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { resetMockMemberState } from '@/mocks/memberHandlers'
import { resetMockNotificationState } from '@/mocks/notificationHandlers'
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
  resetMockNotificationState()
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
async function renderPlanAfterCalculate(scenario: string | null) {
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

  it('결과 위쪽에는 "다시 계산하기" 대신 찜 목록으로 가는 "찜 목록 고치러 가기" 링크가 있다', async () => {
    await renderPlanAfterCalculate(null)

    expect(screen.getByRole('link', { name: '찜 목록 고치러 가기' })).toHaveAttribute(
      'href',
      '/wishlist',
    )
    expect(screen.queryByRole('button', { name: '다시 계산하기' })).not.toBeInTheDocument()
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

/*
 * 알림 켜기 안내 띠. 플랜을 저장한 직후, 알림이 꺼진 일반 회원에게만 "알림 켜기"를 권한다.
 * 알림 설정 값은 목업 핸들러(notificationHandlers)가 정한다(기본 꺼짐, PUT으로 켜면 켜짐, 데모 계정은 항상 꺼짐).
 */
describe('알림 켜기 안내(저장 직후)', () => {
  // 알림 설정 조회 요청 수를 센다
  let settingRequestCount = 0
  beforeEach(() => {
    settingRequestCount = 0
    window.scrollTo = vi.fn() // jsdom에는 scrollTo가 없어 호출만 흉내 낸다
    server.events.on('request:start', ({ request }) => {
      if (
        request.method === 'GET' &&
        new URL(request.url).pathname === '/api/me/notification-setting'
      ) {
        settingRequestCount++
      }
    })
  })
  afterEach(() => {
    server.events.removeAllListeners()
  })

  // 계산해 둔 뒤 저장 버튼을 눌러 "저장 완료" 화면까지 간다
  async function saveAs(role: 'DEMO' | 'USER') {
    const user = userEvent.setup()
    setMockRole(role)
    await renderPlanAfterCalculate(null)
    await user.click(screen.getByRole('button', { name: /플랜으로 저장하기$/ }))
    await screen.findByText('추천형 플랜을 저장했어요')
    return user
  }

  it('알림이 꺼진 일반 회원이 저장하면 안내와 알림 설정으로 가는 "알림 켜기" 링크가 보인다', async () => {
    await saveAs('USER')

    const nudge = await screen.findByRole('group', { name: '알림 켜기 안내' })
    expect(within(nudge).getByRole('link', { name: '알림 켜기' })).toHaveAttribute(
      'href',
      '/settings/notifications',
    )
  })

  it('알림을 이미 켠 회원에게는 안내가 보이지 않는다', async () => {
    setMockRole('USER')
    await customInstance({
      url: '/me/notification-setting',
      method: 'PUT',
      data: { isNotificationEnabled: true },
    })
    await saveAs('USER')

    // 설정 조회가 끝날 때까지 기다린 뒤에도 안내가 없다
    await waitFor(() => expect(settingRequestCount).toBe(1))
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.queryByRole('group', { name: '알림 켜기 안내' })).not.toBeInTheDocument()
  })

  it('데모 계정에게는 안내가 보이지 않고 알림 설정 요청도 보내지 않는다', async () => {
    await saveAs('DEMO')

    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.queryByRole('group', { name: '알림 켜기 안내' })).not.toBeInTheDocument()
    expect(settingRequestCount).toBe(0)
  })

  it('알림 설정을 불러오지 못하면 안내가 보이지 않고 저장 완료 안내는 그대로 보인다', async () => {
    server.use(
      http.get('/api/me/notification-setting', () =>
        HttpResponse.json({ success: false }, { status: 500 }),
      ),
    )
    await saveAs('USER')

    await waitFor(() => expect(settingRequestCount).toBe(1))
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.queryByRole('group', { name: '알림 켜기 안내' })).not.toBeInTheDocument()
    expect(screen.getByText('추천형 플랜을 저장했어요')).toBeInTheDocument()
  })

  it('알림 설정을 불러오는 중에는 안내가 보이지 않는다', async () => {
    // 응답을 바깥의 release()로만 풀 수 있게 막아 로딩 중 상태를 만든다.
    // 영원히 끝나지 않는 Promise를 쓰면 테스트가 끝난 뒤에도 요청이 대기 상태로 남아 다음 테스트에 영향을 주므로(CI 간헐 실패 원인), 마지막에 반드시 풀어 준다
    let release: () => void = () => {}
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    server.use(
      http.get('/api/me/notification-setting', async () => {
        await gate
        return HttpResponse.json({
          success: true,
          data: { isNotificationEnabled: false, email: 'user@example.com' },
        })
      }),
    )
    await saveAs('USER')

    try {
      await waitFor(() => expect(settingRequestCount).toBe(1))
      expect(screen.queryByRole('group', { name: '알림 켜기 안내' })).not.toBeInTheDocument()
    } finally {
      // 검사가 실패해도 대기 요청이 남지 않게 푼다
      release()
    }
    // 풀린 응답이 화면에 반영(안내 표시)될 때까지 기다려, 테스트 종료 뒤 상태 갱신(act 경고)이 생기지 않게 한다
    expect(await screen.findByRole('group', { name: '알림 켜기 안내' })).toBeInTheDocument()
  })

  it('닫기(X)를 누르면 안내가 사라진다', async () => {
    const user = await saveAs('USER')
    await screen.findByRole('group', { name: '알림 켜기 안내' })

    await user.click(screen.getByRole('button', { name: '알림 안내 닫기' }))

    expect(screen.queryByRole('group', { name: '알림 켜기 안내' })).not.toBeInTheDocument()
  })
})

/*
 * 알림 메일 예시 팝업(Task 110). 데모 계정이 플랜을 저장하면 실제 회원이 받을 메일 예시를 팝업으로 보여 준다.
 * 시나리오(mail-*)는 화면 주소의 ?mockScenario= 로 고른다(위 renderPlanAfterCalculate와 같은 방식).
 */
describe('알림 메일 예시 팝업(데모 계정)', () => {
  // 메일 예시 요청 수를 센다(MSW의 요청 이벤트로 본다)
  let mailRequestCount = 0
  beforeEach(() => {
    mailRequestCount = 0
    window.scrollTo = vi.fn() // jsdom에는 scrollTo가 없어 호출만 흉내 낸다
    server.events.on('request:start', ({ request }) => {
      if (new URL(request.url).pathname === '/api/me/plans/current/mail-preview') mailRequestCount++
    })
  })
  afterEach(() => {
    server.events.removeAllListeners()
  })

  // 역할과 시나리오를 고르고 계산해 둔 뒤 결과 화면에서 저장 버튼을 누른다
  async function saveAs(role: 'DEMO' | 'USER', scenario: string | null) {
    const user = userEvent.setup()
    setMockRole(role)
    await renderPlanAfterCalculate(scenario)
    await user.click(screen.getByRole('button', { name: /플랜으로 저장하기$/ }))
    await screen.findByText('추천형 플랜을 저장했어요')
    return user
  }

  it('데모 계정이 저장하면 팝업에 제목·탭 2개·메일 본문 칸(iframe)이 보인다', async () => {
    await saveAs('DEMO', null)

    const dialog = await screen.findByRole('dialog', { name: '알림 메일 예시' })
    expect(within(dialog).getAllByRole('tab')).toHaveLength(2)
    expect(within(dialog).getByRole('tab', { name: '저장하면 오는 메일' })).toBeVisible()
    expect(within(dialog).getByRole('tab', { name: '매월 1일에 오는 메일' })).toBeVisible()
    expect(within(dialog).getByTitle('구독 안내 메일 본문 예시')).toBeInTheDocument()
    // 자동 열기에서 받은 데이터를 팝업이 재사용해 요청은 한 번뿐이다
    expect(mailRequestCount).toBe(1)
  })

  it('iframe sandbox에는 allow-popups만 있고 스크립트·같은 출처 허용은 없다', async () => {
    await saveAs('DEMO', null)

    const frame = await screen.findByTitle('구독 안내 메일 본문 예시')
    const sandbox = frame.getAttribute('sandbox') ?? ''
    expect(sandbox).toContain('allow-popups')
    expect(sandbox).not.toContain('allow-scripts')
    expect(sandbox).not.toContain('allow-same-origin')
    // 링크가 새 탭에서 열리도록 <base target="_blank">가 끼워졌다
    expect(frame.getAttribute('srcdoc')).toContain('<base target="_blank">')
  })

  it('일반 회원이 저장하면 요청도 팝업도 없다', async () => {
    await saveAs('USER', null)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '알림 메일 예시 보기' })).not.toBeInTheDocument()
    expect(mailRequestCount).toBe(0)
  })

  it('구독 안내가 없으면(mail-monthly-only) 탭 없이 월초 메일과 안내 줄을 보인다', async () => {
    await saveAs('DEMO', 'mail-monthly-only')

    const dialog = await screen.findByRole('dialog', { name: '알림 메일 예시' })
    expect(within(dialog).queryByRole('tab')).not.toBeInTheDocument()
    expect(within(dialog).getByTitle('월초 안내 메일 본문 예시')).toBeInTheDocument()
    expect(within(dialog).getByText(/만들어지지\s*않았어요/)).toBeInTheDocument()
  })

  it.each(['mail-error', 'mail-forbidden', 'mail-none'])(
    '%s이면 팝업 없이 저장 완료 안내가 그대로 보인다',
    async (scenario) => {
      await saveAs('DEMO', scenario)

      // 요청이 끝난 뒤에도 팝업은 없다
      await waitFor(() => expect(mailRequestCount).toBe(1))
      await new Promise((resolve) => setTimeout(resolve, 50))
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(screen.getByText('추천형 플랜을 저장했어요')).toBeInTheDocument()
    },
  )

  it('저장 중 409로 다시 계산했다면 메일 예시를 요청하지 않는다', async () => {
    const user = userEvent.setup()
    setMockRole('DEMO')
    await renderPlanAfterCalculate('stale')
    await user.click(screen.getByRole('button', { name: /플랜으로 저장하기$/ }))

    await screen.findByText('데이터가 바뀌어 다시 계산했어요. 결과를 확인하고 저장해 주세요')
    expect(mailRequestCount).toBe(0)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('자동으로 열린 팝업을 닫으면 "알림 메일 예시 보기" 버튼으로 포커스가 간다', async () => {
    const user = await saveAs('DEMO', null)
    await screen.findByRole('dialog', { name: '알림 메일 예시' })

    await user.keyboard('{Escape}')

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByRole('button', { name: '알림 메일 예시 보기' })).toHaveFocus()
  })

  it('"알림 메일 예시 보기" 버튼은 데모 계정에게만 보이고 누르면 팝업이 다시 열린다', async () => {
    const user = await saveAs('DEMO', null)
    const dialog = await screen.findByRole('dialog', { name: '알림 메일 예시' })
    await user.click(within(dialog).getAllByRole('button', { name: '닫기' })[0])
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: '알림 메일 예시 보기' }))

    expect(await screen.findByRole('dialog', { name: '알림 메일 예시' })).toBeVisible()
    expect(await screen.findByTitle('구독 안내 메일 본문 예시')).toBeInTheDocument()
    // 자동 열기 1번 + 버튼으로 다시 열기 1번
    expect(mailRequestCount).toBe(2)
  })

  it('버튼으로 연 팝업에서 실패하면 오류와 "다시 시도"가 보이고, 누르면 다시 요청한다', async () => {
    // 자동 열기는 조용히 실패해 팝업이 없고, 버튼은 현재 플랜 화면에 보인다
    const user = await saveAs('DEMO', 'mail-error')
    await user.click(await screen.findByRole('button', { name: '알림 메일 예시 보기' }))

    const dialog = await screen.findByRole('dialog', { name: '알림 메일 예시' })
    expect(await within(dialog).findByText('메일 예시를 불러오지 못했어요')).toBeInTheDocument()
    const before = mailRequestCount

    await user.click(within(dialog).getByRole('button', { name: '다시 시도' }))

    await waitFor(() => expect(mailRequestCount).toBe(before + 1))
  })

  it('탭은 화살표 키로 옮길 수 있다', async () => {
    const user = await saveAs('DEMO', null)
    const dialog = await screen.findByRole('dialog', { name: '알림 메일 예시' })
    within(dialog).getByRole('tab', { name: '저장하면 오는 메일' }).focus()

    await user.keyboard('{ArrowRight}')

    const second = within(dialog).getByRole('tab', { name: '매월 1일에 오는 메일' })
    expect(second).toHaveFocus()
    expect(second).toHaveAttribute('aria-selected', 'true')
    expect(within(dialog).getByTitle('월초 안내 메일 본문 예시')).toBeInTheDocument()
  })
})
