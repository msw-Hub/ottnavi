import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { toast } from 'sonner'
import { customInstance } from '@/api/http'
import { useWishlistItem } from '@/features/title-detail/hooks/useWishlistItem'
import { setMockRole } from '@/mocks/mockSession'

/*
 * 찜 훅의 로그인 가드 테스트. 화면(버튼)은 찜하지 않은 상태에서 우선순위·해제 버튼을 그리지 않으므로,
 * "캐시에 이전 찜이 남은 채 비로그인이 된" 상황은 훅을 직접 호출해 확인한다.
 * 서버 요청(customInstance)은 가짜로 바꿔 "호출되지 않았다"를 단언한다.
 */

vi.mock('@/api/http', () => ({ customInstance: vi.fn() }))
// 토스트가 실제로 그려지는지가 아니라 "어떤 문구와 버튼으로 요청했는지"를 본다
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn(), warning: vi.fn() } }))

// 서버의 찜 목록 응답(계약 listWishlistItems). 이 작품이 이미 우선순위 MUST로 찜돼 있다
const WISHLIST_LIST_RESPONSE = {
  success: true,
  data: {
    items: [
      {
        wishlistItemId: 7,
        mediaType: 'movie',
        tmdbId: 496243,
        seasonNumber: null,
        priority: 'MUST',
        watchedAt: null,
      },
    ],
    activeItemCount: 1,
    maxActiveItemCount: 20,
  },
}
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
  // 찜 목록 조회(GET)는 위 응답을, 그 밖의 요청(추가·변경)은 항목 ID만 돌려준다
  vi.mocked(customInstance).mockImplementation((config) =>
    Promise.resolve(
      config.method === 'GET' ? WISHLIST_LIST_RESPONSE : { data: { wishlistItemId: 7 } },
    ),
  )
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

  it('회원이면 서버 찜 목록의 찜 상태가 보이고 해제는 서버 요청을 보낸다', async () => {
    setMockRole('USER')
    const { result } = setup()

    // 새로고침 직후에도 서버 목록에서 상태를 얻는다(목록이 올 때까지는 null이고 버튼은 눌리지 않는다)
    expect(result.current.isPending).toBe(true)
    await waitFor(() => expect(result.current.priority).toBe('MUST'))
    act(() => result.current.onRemove())
    // mutation은 비동기로 시작하므로 요청이 나갈 때까지 기다린다
    // 생성 훅이 signal 같은 값을 함께 넘기므로 주소와 메서드만 확인한다
    await waitFor(() =>
      expect(vi.mocked(customInstance).mock.calls.map((call) => call[0])).toContainEqual(
        expect.objectContaining({ url: '/me/wishlist-items/7', method: 'DELETE' }),
      ),
    )
  })

  it('비로그인이면 찜 목록을 요청하지 않는다', () => {
    setMockRole('GUEST')
    setup()

    expect(customInstance).not.toHaveBeenCalled()
  })
})

describe('useWishlistItem 찜 20개 제한(409)', () => {
  beforeEach(() => {
    vi.mocked(toast.error).mockClear()
    vi.mocked(toast.warning).mockClear()
  })

  it('찜 추가가 409 WISHLIST_LIMIT_EXCEEDED면 오류가 아닌 경고 토스트로 문구와 [찜 목록 가기] 버튼을 띄운다', async () => {
    setMockRole('USER')
    const userMessage = '찜은 20개까지 담을 수 있어요. 찜 목록에서 정리해 주세요.'
    vi.mocked(customInstance).mockImplementation((config) =>
      config.method === 'GET'
        ? Promise.resolve({ success: true, data: { items: [] } })
        : Promise.reject({ status: 409, errorCode: 'WISHLIST_LIMIT_EXCEEDED', userMessage }),
    )
    const { result } = setup()
    await waitFor(() => expect(result.current.isPending).toBe(false))

    act(() => result.current.onAdd())

    await waitFor(() => expect(toast.warning).toHaveBeenCalled())
    expect(toast.error).not.toHaveBeenCalled()
    const [message, options] = vi.mocked(toast.warning).mock.calls[0]
    expect(message).toBe(userMessage)
    expect(options).toMatchObject({ action: { label: '찜 목록 가기' } })

    // 버튼을 누르면 찜 목록으로 이동한다
    const { action } = options as unknown as { action: { onClick: () => void } }
    act(() => action.onClick())
    expect(screen.getByTestId('location')).toHaveTextContent('/wishlist')
  })

  it('다른 오류는 [찜 목록 가기] 없이 문구만 토스트로 알린다', async () => {
    setMockRole('USER')
    vi.mocked(customInstance).mockImplementation((config) =>
      config.method === 'GET'
        ? Promise.resolve({ success: true, data: { items: [] } })
        : Promise.reject({
            status: 500,
            errorCode: 'INTERNAL_ERROR',
            userMessage: '일시적인 오류가 발생했습니다.',
          }),
    )
    const { result } = setup()
    await waitFor(() => expect(result.current.isPending).toBe(false))

    act(() => result.current.onAdd())

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('일시적인 오류가 발생했습니다.'))
    expect(toast.warning).not.toHaveBeenCalled()
  })
})
