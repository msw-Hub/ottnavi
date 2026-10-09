import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { customInstance } from '@/api/http'
import { useWishlistItem } from '@/features/title-detail/hooks/useWishlistItem'
import { setMockRole } from '@/mocks/mockSession'

/*
 * 찜 훅의 로그인 가드 테스트. 화면(버튼)은 찜하지 않은 상태에서 우선순위·해제 버튼을 그리지 않으므로,
 * "캐시에 이전 찜이 남은 채 비로그인이 된" 상황은 훅을 직접 호출해 확인한다.
 * 서버 요청(customInstance)은 가짜로 바꿔 "호출되지 않았다"를 단언한다.
 */

vi.mock('@/api/http', () => ({ customInstance: vi.fn() }))

const WISHLIST_STATE_KEY = ['mock-wishlist-state']
const TARGET = { mediaType: 'movie', tmdbId: 496243, seasonNumber: null } as const
const FROM = '/titles/movie/496243?tab=info'

// 현재 주소(경로와 넘겨받은 state)를 화면에 적어, 이동 결과를 글자로 확인한다
function LocationProbe() {
  const location = useLocation()
  return <p data-testid="location">{`${location.pathname} ${JSON.stringify(location.state)}`}</p>
}

// 캐시에 이전 찜이 남아 있는 QueryClient와 라우터로 훅을 감싼다
function setup() {
  const queryClient = new QueryClient()
  queryClient.setQueryData(WISHLIST_STATE_KEY, {
    'movie:496243:all': { wishlistItemId: 7, priority: 'MUST' },
  })
  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[FROM]}>
          {children}
          <LocationProbe />
        </MemoryRouter>
      </QueryClientProvider>
    )
  }
  return renderHook(() => useWishlistItem(TARGET, '기생충'), { wrapper: Wrapper })
}

beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true')
  vi.mocked(customInstance).mockReset()
  vi.mocked(customInstance).mockResolvedValue({ data: { wishlistItemId: 7 } })
})
afterEach(() => {
  vi.unstubAllEnvs()
  setMockRole('GUEST')
})

describe('useWishlistItem 로그인 가드', () => {
  it('비로그인이면 캐시에 찜이 남아 있어도 찜 상태가 null이다', () => {
    setMockRole('GUEST')
    const { result } = setup()

    expect(result.current.priority).toBeNull()
  })

  it('비로그인이면 우선순위 변경·해제가 서버 요청 없이 로그인 화면으로 이동하고 from을 넘긴다', () => {
    setMockRole('GUEST')
    const { result } = setup()

    act(() => result.current.onChangePriority('WANT'))
    expect(customInstance).not.toHaveBeenCalled()
    expect(screen.getByTestId('location')).toHaveTextContent(`/login {"from":"${FROM}"}`)

    act(() => result.current.onRemove())
    expect(customInstance).not.toHaveBeenCalled()
  })

  it('회원이면 캐시의 찜 상태가 보이고 해제는 서버 요청을 보낸다', async () => {
    setMockRole('USER')
    const { result } = setup()

    expect(result.current.priority).toBe('MUST')
    act(() => result.current.onRemove())
    // mutation은 비동기로 시작하므로 요청이 나갈 때까지 기다린다
    await waitFor(() =>
      expect(customInstance).toHaveBeenCalledWith({
        url: '/me/wishlist-items/7',
        method: 'DELETE',
      }),
    )
  })
})
