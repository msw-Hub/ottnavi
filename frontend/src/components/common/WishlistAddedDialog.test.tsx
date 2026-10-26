import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { MemoryRouter, useLocation } from 'react-router'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { customInstance } from '@/api/http'
import { Providers } from '@/app/providers'
import { WishlistAddedDialog } from '@/components/common/WishlistAddedDialog'
import { closeWishlistAddedDialog, openWishlistAddedDialog } from '@/hooks/useWishlistAddedDialog'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { resetMockMemberState } from '@/mocks/memberHandlers'
import { resetMockSession, setMockRole } from '@/mocks/mockSession'

// 팝업이 우선순위를 읽고 바꾸려면 찜 목록 요청이 필요해 앱의 목업 핸들러(MSW)와 Providers(QueryClient)를 함께 쓴다
const server = setupServer(...handlers)

// 현재 주소를 화면에 적어 이동 결과를 글자로 확인한다
function LocationProbe() {
  const location = useLocation()
  return <p data-testid="location">{location.pathname + location.search}</p>
}

function setup() {
  return render(
    <Providers>
      <MemoryRouter initialEntries={['/search?q=기생충']}>
        <WishlistAddedDialog />
        <LocationProbe />
      </MemoryRouter>
    </Providers>,
  )
}

const INFO = { entryKey: 'movie:496243:all', wishlistItemId: 1, name: '기생충', posterUrl: null }

// 목업 서버에 기생충을 보고 싶음(기본값)으로 찜해 두고, 서버가 돌려준 항목 ID를 쓰는 팝업 정보를 만든다
async function addParasite() {
  const response = await customInstance<{ data: { wishlistItemId: number } }>({
    url: '/me/wishlist-items',
    method: 'POST',
    data: { mediaType: 'movie', tmdbId: 496243, seasonNumber: null, priority: 'WANT' },
  })
  return { ...INFO, wishlistItemId: response.data.wishlistItemId }
}

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true')
  setMockRole('USER')
})
afterEach(() => {
  act(() => closeWishlistAddedDialog())
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockSession()
  resetMockMemberState()
  resetMockWishlist()
  window.history.replaceState(null, '', '/')
})
afterAll(() => server.close())

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

  it('우선순위는 현재 단계(보고 싶음)로 시작하고, 팝업에서 바로 바꿀 수 있다', async () => {
    const info = await addParasite()
    const user = userEvent.setup()
    setup()
    act(() => openWishlistAddedDialog(info))

    // 서버 목록에서 현재 단계를 읽어 보고 싶음이 골라져 있다
    const want = await screen.findByRole('radio', { name: '보고 싶음' })
    await vi.waitFor(() => expect(want).toBeChecked())

    // 꼭 볼 작품을 고르면 서버에 반영되고 다시 받은 목록에서도 그 단계가 골라진다
    await user.click(screen.getByRole('radio', { name: '꼭 볼 작품' }))
    await vi.waitFor(() => expect(screen.getByRole('radio', { name: '꼭 볼 작품' })).toBeChecked())
    expect(screen.getByRole('radio', { name: '보고 싶음' })).not.toBeChecked()

    // 서버 쪽 값도 실제로 바뀌었는지 목록을 직접 확인한다
    const list = await customInstance<{
      data: { items: { wishlistItemId: number; priority: string }[] }
    }>({
      url: '/me/wishlist-items',
      method: 'GET',
    })
    expect(
      list.data.items.find((item) => item.wishlistItemId === info.wishlistItemId)?.priority,
    ).toBe('MUST')
  })
})
