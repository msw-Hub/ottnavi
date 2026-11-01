import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ConsentBannerHost } from '@/features/consent/components/ConsentBannerHost'
import { CONSENT_STORAGE_KEY } from '@/features/consent/consentPolicy'
import { resetConsentStoreForTest } from '@/features/consent/consentStore'

/*
 * 동의 띠 표시 조건 테스트.
 * 확인하는 것: 측정 ID가 있고 제외 경로가 아니며 아직 선택하지 않았을 때만 띠가 보인다 / 선택하면 사라지고 저장된다.
 * 막으려는 사고: 목업·로컬이나 관리자 화면에 띠가 뜨는 것, 이미 고른 방문자에게 계속 묻는 것.
 */
function renderAt(path: string) {
  // RootLayout처럼 포커스를 받을 수 있는 main(tabIndex=-1)을 함께 둔다(선택 뒤 포커스 이동 확인용)
  const router = createMemoryRouter(
    [
      {
        path: '*',
        element: (
          <>
            <main id="main" tabIndex={-1} />
            <ConsentBannerHost />
          </>
        ),
      },
    ],
    { initialEntries: [path] },
  )
  render(<RouterProvider router={router} />)
}

describe('ConsentBannerHost', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_USE_MOCK', 'false')
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123456')
    window.localStorage.clear()
    resetConsentStoreForTest()
  })
  afterEach(() => {
    vi.unstubAllEnvs()
    window.localStorage.clear()
  })

  it('측정 ID가 있고 선택 전이면 띠가 보이고, 거부하면 사라지고 저장된다', async () => {
    renderAt('/')
    expect(screen.getByRole('region', { name: '방문 통계 수집 동의' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '거부' }))

    expect(screen.queryByRole('region')).not.toBeInTheDocument()
    expect(window.localStorage.getItem(CONSENT_STORAGE_KEY)).toContain('"denied"')
  })

  it('동의를 누르면 띠가 사라지고 포커스가 본문(main)으로 옮겨진다', async () => {
    renderAt('/')
    await userEvent.click(screen.getByRole('button', { name: '동의' }))

    expect(screen.queryByRole('region')).not.toBeInTheDocument()
    expect(document.getElementById('main')).toHaveFocus()
  })

  it('측정 ID가 없으면(목업·로컬) 띠가 없다', () => {
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', '')
    renderAt('/')
    expect(screen.queryByRole('region')).not.toBeInTheDocument()
  })

  it('제외 경로(/admin)에서는 띠가 없다', () => {
    renderAt('/admin/collect')
    expect(screen.queryByRole('region')).not.toBeInTheDocument()
  })

  it('이미 동의했거나 거부한 상태면 띠가 없다', () => {
    window.localStorage.setItem(
      CONSENT_STORAGE_KEY,
      JSON.stringify({ status: 'granted', decidedAt: new Date().toISOString() }),
    )
    renderAt('/')
    expect(screen.queryByRole('region')).not.toBeInTheDocument()
  })
})
