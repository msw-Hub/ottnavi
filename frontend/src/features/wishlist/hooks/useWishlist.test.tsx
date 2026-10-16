import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import { setupServer } from 'msw/node'
import type { ReactNode } from 'react'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { customInstance } from '@/api/http'
import {
  useRemoveWishlistItem,
  useUpdateWishlistItem,
  useWishlist,
} from '@/features/wishlist/hooks/useWishlist'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { resetMockMemberState } from '@/mocks/memberHandlers'
import { resetMockSession, setMockRole } from '@/mocks/mockSession'

/*
 * 찜 목록·변경 훅 테스트(Task 058). 변경이 성공하면 같은 목록 쿼리(['/me/wishlist-items'])가 다시 받아져
 * 화면(찜 목록, 작품 상세의 찜 버튼)이 서버 값 그대로 보이는지 본다. API는 앱의 목업 핸들러(MSW)로 대체한다.
 */

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

// 목록 조회와 변경 훅을 같은 QueryClient에서 함께 쓴다
function setup() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return renderHook(
    () => ({
      list: useWishlist(),
      update: useUpdateWishlistItem(),
      remove: useRemoveWishlistItem(),
    }),
    { wrapper },
  )
}

describe('찜 목록 변경 훅', () => {
  it('우선순위를 바꾸면 목록을 다시 받아 같은 항목만 새 우선순위가 된다', async () => {
    const targetId = await addMovie(496243)
    await addMovie(524)
    const { result } = setup()
    await waitFor(() => expect(result.current.list.data?.items).toHaveLength(2))

    await act(async () => {
      result.current.update.mutate({ wishlistItemId: targetId, priority: 'MUST' })
    })

    await waitFor(() => {
      const items = result.current.list.data?.items ?? []
      expect(items.find((item) => item.wishlistItemId === targetId)?.priority).toBe('MUST')
    })
    const others = (result.current.list.data?.items ?? []).filter(
      (item) => item.wishlistItemId !== targetId,
    )
    expect(others.map((item) => item.priority)).toEqual(['WANT'])
  })

  it('다 봤음으로 바꾸면 활성 개수가 줄고 삭제하면 목록에서 사라진다', async () => {
    const targetId = await addMovie(496243)
    const { result } = setup()
    await waitFor(() => expect(result.current.list.data?.activeItemCount).toBe(1))

    await act(async () => {
      result.current.update.mutate({ wishlistItemId: targetId, isWatched: true })
    })
    await waitFor(() => expect(result.current.list.data?.activeItemCount).toBe(0))
    expect(result.current.list.data?.items[0]?.watchedAt).not.toBeNull()

    await act(async () => {
      result.current.remove.mutate(targetId)
    })
    await waitFor(() => expect(result.current.list.data?.items).toHaveLength(0))
  })
})
