import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { customInstance, type ApiError } from '@/api/http'
import type {
  AddWishlistItemRequest,
  CommonResponseAddWishlistItem,
  MediaType,
  UpdateWishlistPriorityRequest,
  WishlistPriority,
} from '@/features/search/mockTypes'

// 현재 미사용: 검색 카드에서 찜 하트를 없앴다(찜은 상세에서만). 하트를 되살릴 때를 위해 파일은 남긴다.
// 지우거나 주석 처리하지 않은 이유: 빈 모듈이 되면 TypeScript가 모듈로 인식하지 못할 수 있다.

// 우선순위를 묻지 않고 찜할 때 쓰는 기본값(보고 싶음)
const DEFAULT_WISHLIST_PRIORITY: WishlistPriority = 'WANT'

// 이 세션에서 찜한 항목을 보관하는 캐시 키. 다른 기능 폴더(features/*)의 같은 이름 훅과 키 문자열이 같아야 화면 사이에 상태가 공유된다
const WISHLIST_STATE_KEY = ['mock-wishlist-state'] as const

interface WishlistEntry {
  wishlistItemId: number // 찜 항목 ID(우선순위 변경·해제 요청의 주소에 쓴다)
  priority: WishlistPriority // 지금 우선순위
}

// 찜 항목 키("movie:496243:all", "tv:93405:2") → 항목
type WishlistState = Record<string, WishlistEntry>

interface WishlistTarget {
  mediaType: MediaType // 작품 구분
  tmdbId: number // TMDB ID
  seasonNumber: number | null // 시즌 단위 찜이면 시즌 번호, 영화는 null
}

function toEntryKey({ mediaType, tmdbId, seasonNumber }: WishlistTarget): string {
  return `${mediaType}:${tmdbId}:${seasonNumber ?? 'all'}`
}

// 요청 제한(429)은 인터셉터가 이미 토스트로 알렸으므로 같은 안내를 또 띄우지 않는다
function notifyError(error: ApiError) {
  if (!error.hasNotifiedUser) toast.error(error.userMessage)
}

/**
 * 작품(영화) 또는 시즌(드라마) 하나의 찜 상태와 찜 추가·우선순위 변경·해제를 돌려준다.
 *
 * 임시 구현: 이 API들은 아직 계약에 없어 생성 훅이 없다. 경로는 임시이며 Task 031에서 확정되고 생성 훅으로 바뀐다.
 * - 추가:   POST   /me/wishlist-items          (우선순위는 묻지 않고 기본값 WANT로 추가)
 * - 변경:   PATCH  /me/wishlist-items/{id}
 * - 해제:   DELETE /me/wishlist-items/{id}
 * 찜 상태를 TanStack Query 캐시에 두는 이유: 목업 단계에는 "내 찜 목록 조회" API가 아직 없다. 요청이 성공할 때마다 캐시를 고쳐
 * 새로고침 전까지 화면(검색 카드, 상세)이 같은 상태를 보이게 한다. 캐시는 QueryClient(앱 전체) 하나에 있어 화면을 옮겨도 남는다.
 * queryFn을 skipToken으로 둔 이유: 서버에서 가져올 값이 없고 initialData(빈 객체)가 시작값이라 요청을 보내지 않기 위함이다.
 * 비로그인이면 서버가 401을 주어 오류 토스트("로그인이 필요합니다.")가 뜨고 캐시는 바뀌지 않는다.
 * mutation은 TanStack Query 기본값이 재시도 0번이라 같은 요청이 두 번 저장되지 않는다.
 * features/title-detail에도 같은 훅이 있는 이유: 기능 폴더끼리는 서로 import하지 않기 때문이다.
 */
export function useWishlistItem(target: WishlistTarget, targetName: string) {
  const queryClient = useQueryClient()
  const entryKey = toEntryKey(target)

  const { data: state } = useQuery<WishlistState>({
    queryKey: WISHLIST_STATE_KEY,
    queryFn: skipToken,
    initialData: {},
    staleTime: Infinity,
    gcTime: Infinity,
  })

  // 캐시의 한 항목을 바꾼다. entry가 null이면 지운다
  function writeEntry(entry: WishlistEntry | null) {
    queryClient.setQueryData<WishlistState>(WISHLIST_STATE_KEY, (current = {}) => {
      const next = { ...current }
      if (entry === null) delete next[entryKey]
      else next[entryKey] = entry
      return next
    })
  }

  const add = useMutation<CommonResponseAddWishlistItem, ApiError, void>({
    mutationFn: () => {
      const body: AddWishlistItemRequest = { ...target, priority: DEFAULT_WISHLIST_PRIORITY }
      return customInstance<CommonResponseAddWishlistItem>({
        url: '/me/wishlist-items',
        method: 'POST',
        data: body,
      })
    },
    onSuccess: (res) => {
      writeEntry({ wishlistItemId: res.data.wishlistItemId, priority: DEFAULT_WISHLIST_PRIORITY })
      toast.success(`${targetName}을(를) 찜했습니다.`)
    },
    onError: notifyError,
  })

  const entry = state?.[entryKey] ?? null

  const changePriority = useMutation<unknown, ApiError, WishlistPriority>({
    mutationFn: (priority) => {
      const body: UpdateWishlistPriorityRequest = { priority }
      return customInstance({
        url: `/me/wishlist-items/${entry?.wishlistItemId}`,
        method: 'PATCH',
        data: body,
      })
    },
    onSuccess: (_res, priority) => {
      if (entry) writeEntry({ ...entry, priority })
    },
    onError: notifyError,
  })

  const remove = useMutation<unknown, ApiError, void>({
    mutationFn: () =>
      customInstance({ url: `/me/wishlist-items/${entry?.wishlistItemId}`, method: 'DELETE' }),
    onSuccess: () => {
      writeEntry(null)
      toast.success(`${targetName} 찜을 해제했습니다.`)
    },
    onError: notifyError,
  })

  return {
    priority: entry?.priority ?? null, // 찜하지 않았으면 null
    isPending: add.isPending || changePriority.isPending || remove.isPending,
    onAdd: () => add.mutate(),
    onChangePriority: (priority: WishlistPriority) => changePriority.mutate(priority),
    onRemove: () => remove.mutate(),
  }
}
