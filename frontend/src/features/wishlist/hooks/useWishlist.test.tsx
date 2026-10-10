import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import { setupServer } from 'msw/node'
import type { ReactNode } from 'react'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { customInstance } from '@/api/http'
import { useUpdateWishlistItem } from '@/features/wishlist/hooks/useWishlist'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { resetMockMemberState } from '@/mocks/memberHandlers'
import { resetMockSession, setMockRole } from '@/mocks/mockSession'

/*
 * 찜 항목 변경 훅 테스트. 찜 목록에서 우선순위를 바꾸면 검색·상세의 찜 버튼이 읽는 캐시(mock-wishlist-state)도
 * 같이 바뀌는지 본다. API는 앱의 목업 핸들러(MSW)로 대체한다.
 */

const WISHLIST_STATE_KEY = ['mock-wishlist-state']

type WishlistState = Record<string, { wishlistItemId: number; priority: string }>

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
beforeEach(() => {
  vi.stubEnv('VITE_USE_MOCK', 'true')
  setMockRole('USER')
})
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  resetMockSession()
  resetMockMemberState()
  resetMockWishlist()
})
afterAll(() => server.close())

// 목업 서버에 영화 한 편을 찜하고 그 찜 항목 ID를 돌려준다
async function addMovie(tmdbId: number): Promise<number> {
  const res = await customInstance<{ data: { wishlistItemId: number } }>({
    url: '/me/wishlist-items',
    method: 'POST',
    data: { mediaType: 'movie', tmdbId, seasonNumber: null, priority: 'WANT' },
  })
  return res.data.wishlistItemId
}

function setup(state: WishlistState) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  queryClient.setQueryData(WISHLIST_STATE_KEY, state)
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  const { result } = renderHook(() => useUpdateWishlistItem(), { wrapper })
  return { queryClient, result }
}

describe('useUpdateWishlistItem', () => {
  it('우선순위를 바꾸면 검색·상세가 읽는 찜 상태 캐시의 같은 항목만 새 값이 된다', async () => {
    const targetId = await addMovie(496243)
    const otherId = await addMovie(524)
    const { queryClient, result } = setup({
      'movie:496243:all': { wishlistItemId: targetId, priority: 'WANT' },
      'movie:524:all': { wishlistItemId: otherId, priority: 'WANT' },
    })

    await act(async () => {
      result.current.mutate({ wishlistItemId: targetId, priority: 'MUST' })
    })

    await waitFor(() => {
      const state = queryClient.getQueryData<WishlistState>(WISHLIST_STATE_KEY)
      expect(state?.['movie:496243:all']).toEqual({ wishlistItemId: targetId, priority: 'MUST' })
    })
    const state = queryClient.getQueryData<WishlistState>(WISHLIST_STATE_KEY)
    expect(state?.['movie:524:all']).toEqual({ wishlistItemId: otherId, priority: 'WANT' })
  })

  it('다 봤음만 바꾸면 캐시의 우선순위는 그대로다', async () => {
    const targetId = await addMovie(496243)
    const { queryClient, result } = setup({
      'movie:496243:all': { wishlistItemId: targetId, priority: 'WANT' },
    })

    await act(async () => {
      result.current.mutate({ wishlistItemId: targetId, isWatched: true })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    const state = queryClient.getQueryData<WishlistState>(WISHLIST_STATE_KEY)
    expect(state?.['movie:496243:all']).toEqual({ wishlistItemId: targetId, priority: 'WANT' })
  })
})
