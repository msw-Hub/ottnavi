import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { WishlistAddedDialog } from '@/components/common/WishlistAddedDialog'
import { closeWishlistAddedDialog, openWishlistAddedDialog } from '@/hooks/useWishlistAddedDialog'

// 현재 주소를 화면에 적어 이동 결과를 글자로 확인한다
function LocationProbe() {
  const location = useLocation()
  return <p data-testid="location">{location.pathname + location.search}</p>
}

function setup() {
  return render(
    <MemoryRouter initialEntries={['/search?q=기생충']}>
      <WishlistAddedDialog />
      <LocationProbe />
    </MemoryRouter>,
  )
}

const INFO = { entryKey: 'movie:496243:all', name: '기생충', posterUrl: null }

beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true')
})
afterEach(() => {
  act(() => closeWishlistAddedDialog())
  vi.unstubAllEnvs()
  window.history.replaceState(null, '', '/')
})

describe('WishlistAddedDialog', () => {
  it('열기 전에는 보이지 않고, 열면 작품 이름과 두 버튼이 보인다', () => {
    setup()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    act(() => openWishlistAddedDialog(INFO))
    expect(screen.getByRole('dialog', { name: '기생충을 찜했어요' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '더 둘러보기' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '찜 목록 보고 계산하기' })).toBeInTheDocument()
  })

  it('같은 작품을 연달아 열어도 팝업은 하나만 뜬다', () => {
    setup()
    act(() => openWishlistAddedDialog(INFO))
    act(() => openWishlistAddedDialog(INFO))
    expect(screen.getAllByRole('dialog')).toHaveLength(1)
  })

  it('더 둘러보기를 누르면 팝업이 닫히고 주소는 그대로다', async () => {
    setup()
    act(() => openWishlistAddedDialog(INFO))
    await userEvent.click(screen.getByRole('button', { name: '더 둘러보기' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/search?q=기생충')
  })

  it('Esc를 누르면 닫힌다', async () => {
    setup()
    act(() => openWishlistAddedDialog(INFO))
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('찜 목록 버튼은 /wishlist로 이동하며 목업 시나리오 파라미터만 유지하고 팝업을 닫는다', async () => {
    window.history.replaceState(null, '', '/search?q=기생충&mockScenario=empty')
    setup()
    act(() => openWishlistAddedDialog(INFO))
    await userEvent.click(screen.getByRole('link', { name: '찜 목록 보고 계산하기' }))
    expect(screen.getByTestId('location')).toHaveTextContent('/wishlist?mockScenario=empty')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
