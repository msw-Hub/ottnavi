import { http as mswHttp, HttpResponse } from 'msw'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { Providers } from '@/app/providers'
import { SettingsScreen } from '@/features/onboarding/components/SettingsScreen'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { resetMockMemberState } from '@/mocks/memberHandlers'
import {
  getMockRole,
  resetMockSession,
  setMockOnboardingDone,
  setMockRole,
} from '@/mocks/mockSession'

/*
 * 온보딩·내 설정 화면 테스트(Task 028). 저장에 성공하면 로그인 전에 보던 화면(location.state.from)으로 돌아가고,
 * 돌아갈 곳이 없으면 첫 화면(/)으로 가는지 본다. API는 앱의 목업 핸들러(MSW)로 대체한다.
 */

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
// 회원 상태 판단과 목업 핸들러는 목업 모드에서만 동작하므로 목업 모드를 흉내 낸다
beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true')
  setMockRole('USER') // 온보딩은 아직 하지 않은 첫 방문 회원
})
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockSession()
  resetMockMemberState()
  resetMockWishlist()
})
afterAll(() => server.close())

// 설정 화면을 열고, 돌아갈 곳을 확인할 수 있게 도착 화면 자리표시를 둔다. state가 있으면 로그인 전에 보던 화면(from)을 흉내 낸다
// hash가 있으면 다른 화면에서 특정 구역으로 바로 이동해 온 경우를 흉내 낸다(예: 요금 확인 화면의 "#price-mode")
function renderSettings(state?: unknown, hash = '') {
  const router = createMemoryRouter(
    [
      { path: '/settings', element: <SettingsScreen /> },
      { path: '/', element: <h1>첫 화면</h1> },
      { path: '/wishlist', element: <h1>찜 목록 화면</h1> },
    ],
    { initialEntries: [{ pathname: '/settings', state, hash }] },
  )
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
  return router
}

// 첫 방문 폼에서 필수인 월 예산만 채우고 저장한다(시청 시간은 '보통'이 미리 골라져 있다)
async function fillBudgetAndSave(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole('heading', { level: 1, name: '시작하기' })
  await user.type(screen.getByLabelText(/월 예산/), '20000')
  await user.click(screen.getByRole('button', { name: '저장하기' }))
}

describe('내 설정 저장 후 이동', () => {
  it('로그인 전에 보던 화면(from)이 있으면 저장 뒤 그 화면으로 돌아간다', async () => {
    const user = userEvent.setup()
    const router = renderSettings({ from: '/wishlist' })

    await fillBudgetAndSave(user)

    expect(await screen.findByRole('heading', { name: '찜 목록 화면' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/wishlist')
    expect(getMockRole()).toBe('USER')
  })

  it('돌아갈 화면이 없으면 저장 뒤 첫 화면(/)으로 간다', async () => {
    const user = userEvent.setup()
    const router = renderSettings()

    await fillBudgetAndSave(user)

    expect(await screen.findByRole('heading', { name: '첫 화면' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })

  it('from이 사이트 밖 주소면 따르지 않고 첫 화면(/)으로 간다', async () => {
    const user = userEvent.setup()
    renderSettings({ from: 'https://evil.example/' })

    await fillBudgetAndSave(user)

    expect(await screen.findByRole('heading', { name: '첫 화면' })).toBeInTheDocument()
  })
})

describe('계산할 요금제 구역으로 바로 이동', () => {
  it('주소가 #price-mode면 설정을 불러온 뒤 "계산할 요금제" 구역으로 스크롤하고 체크박스에 포커스를 준다', async () => {
    // jsdom에는 scrollIntoView가 없어 빈 함수로 대신하고, 불렸는지만 본다
    const scrollIntoView = vi.fn()
    Element.prototype.scrollIntoView = scrollIntoView
    setMockOnboardingDone(true)
    renderSettings(undefined, '#price-mode')

    const checkbox = await screen.findByRole('checkbox', {
      name: /가장 저렴한 요금제로 계산할래요/,
    })
    await waitFor(() => expect(checkbox).toHaveFocus())
    expect(scrollIntoView).toHaveBeenCalled()
    expect(scrollIntoView.mock.contexts[0]).toHaveAttribute('id', 'price-mode')
  })

  it('hash가 없으면 포커스를 옮기지 않는다', async () => {
    setMockOnboardingDone(true)
    renderSettings()

    const checkbox = await screen.findByRole('checkbox', {
      name: /가장 저렴한 요금제로 계산할래요/,
    })
    expect(checkbox).not.toHaveFocus()
  })
})

describe('회원 탈퇴 링크(Task 081)', () => {
  // 이전: '가입을 마친 회원의 내 설정 화면 맨 아래에 탈퇴 화면으로 가는 링크가 있다'(링크를 찾아 href가 /settings/withdraw인지 확인).
  // 탈퇴 링크를 푸터(app/RootLayout)로 옮겨(사용자 확정) 내 설정에서는 사라진 것을 확인하도록 바꿨다. 푸터 쪽은 app/RootLayout.test.tsx가 본다
  it('가입을 마친 회원의 내 설정 화면에도 탈퇴 링크가 없다(푸터로 옮김)', async () => {
    setMockOnboardingDone(true) // 목업 서버가 기본 설정(isOnboardingDone=true)을 돌려준다
    renderSettings()
    // 제목은 불러오는 중에도 "내 설정"이라, 폼이 다 그려진 표시인 저장 버튼을 기다린 뒤 링크가 없는지 본다
    await screen.findByRole('button', { name: '저장하기' })

    expect(screen.queryByRole('link', { name: '회원 탈퇴' })).not.toBeInTheDocument()
  })

  it('첫 방문(온보딩) 화면에는 탈퇴 링크가 없다', async () => {
    renderSettings()
    await screen.findByRole('heading', { level: 1, name: '시작하기' })

    expect(screen.queryByRole('link', { name: '회원 탈퇴' })).not.toBeInTheDocument()
  })
})

describe('내 설정 저장 검증(Task 058)', () => {
  it('월 예산을 비운 채 저장하면 서버에 보내지 않고 칸 아래에 안내가 보인다', async () => {
    // jsdom에는 scrollIntoView가 없어 오류 칸으로 화면을 옮기는 코드가 실패하므로 빈 함수로 대신한다
    Element.prototype.scrollIntoView = vi.fn()
    const user = userEvent.setup()
    const router = renderSettings()
    await screen.findByRole('heading', { level: 1, name: '시작하기' })

    await user.click(screen.getByRole('button', { name: '저장하기' }))

    expect(await screen.findByText('월 예산을 입력해 주세요.')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/settings')
  })

  it('서버가 400 VALIDATION_FAILED로 거절하면 저장 실패 안내와 필드별 이유가 보이고 화면에 머문다', async () => {
    server.use(
      mswHttp.put('/api/me/settings', () =>
        HttpResponse.json(
          {
            title: 'Bad Request',
            status: 400,
            detail: '요청 값이 올바르지 않습니다.',
            instance: '/api/me/settings',
            errorCode: 'VALIDATION_FAILED',
            fieldErrors: [{ field: 'monthlyBudget', message: '0 이상의 정수여야 합니다.' }],
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    const user = userEvent.setup()
    const router = renderSettings()

    await fillBudgetAndSave(user)

    expect(await screen.findByText('저장하지 못했어요')).toBeInTheDocument()
    expect(screen.getByText(/월 예산: 0 이상의 정수여야 합니다./)).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/settings')
  })
})
