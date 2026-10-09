import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { customInstance, type ApiError } from '@/api/http'
import { useIsLoggedIn } from '@/hooks/useIsLoggedIn'
import { useWishlistAddedDialog } from '@/hooks/useWishlistAddedDialog'
import type {
  AddWishlistItemRequest,
  CommonResponseAddWishlistItem,
  MediaType,
  UpdateWishlistPriorityRequest,
  WishlistPriority,
} from '@/features/title-detail/mockTypes'

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

// 찜 추가 팝업에 쓸 표시 정보. 생략하면 이름은 targetName, 포스터는 없음으로 보인다
interface WishlistAddedPopupInfo {
  name: string // 팝업 문장의 이름(드라마 시즌이면 "제목 시즌 2")
  posterUrl: string | null // 팝업 썸네일 포스터 주소
}

// 요청 제한(429)은 인터셉터가 이미 토스트로 알렸으므로 같은 안내를 또 띄우지 않는다
function notifyError(error: ApiError) {
  if (!error.hasNotifiedUser) toast.error(error.userMessage)
}

// 비로그인 요청에 서버가 돌려주는 상태 코드
const UNAUTHORIZED_STATUS = 401

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
 * 비로그인이면 오류 토스트 대신 로그인 화면(/login)으로 보내고, 지금 화면을 from으로 넘겨 로그인이 끝나면 이 작품 상세로 돌아오게 한다(Task 027).
 * 판단은 두 겹이다. ① 요청 전: 공통 훅 useIsLoggedIn이 비로그인이면 추가·우선순위 변경·해제 모두 요청을 보내지 않고 바로 이동한다(401은 브라우저 콘솔에 오류로 남기 때문).
 * ② 요청 후: 서버가 401을 주면(로그인이 만료된 경우 등) 같은 이동을 한다. 목업 역할(mockSession)을 직접 보지 않고 공통 훅을 쓰므로
 * features가 목업 모듈에 의존하지 않고, Task 057에서 훅 내부만 authStore로 바꾸면 된다.
 * 화면 가드(RequireAuth)와 달리 찜은 공개 화면 안의 동작이라, 막는 쪽이 아니라 "로그인을 권하는" 쪽으로 처리한다.
 * mutation은 TanStack Query 기본값이 재시도 0번이라 같은 요청이 두 번 저장되지 않는다.
 * features/search에도 같은 훅이 있는 이유: 기능 폴더끼리는 서로 import하지 않기 때문이다.
 */
export function useWishlistItem(
  target: WishlistTarget,
  targetName: string,
  popup?: WishlistAddedPopupInfo,
) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const location = useLocation()
  const entryKey = toEntryKey(target)

  const isLoggedIn = useIsLoggedIn()
  // 찜을 추가했을 때의 선택지 팝업(더 둘러보기 / 찜 목록 보고 계산하기). 해제할 때는 쓰지 않는다
  const showWishlistAddedDialog = useWishlistAddedDialog()

  // 로그인 화면으로 보낸다. 지금 화면을 from으로 넘겨 로그인 뒤 돌아오게 한다(로그인 화면이 내부 경로만 허용하도록 다시 검사한다)
  function redirectToLogin() {
    navigate('/login', { state: { from: `${location.pathname}${location.search}` } })
  }

  // 요청이 실패했을 때: 401이면 로그인 화면으로 보내고, 그 밖의 오류는 토스트로 알린다
  function handleError(error: ApiError) {
    if (error.status === UNAUTHORIZED_STATUS) {
      redirectToLogin()
      return
    }
    notifyError(error)
  }

  // 로그인이 필요한 동작을 감싼다. 비로그인이면 요청을 보내지 않고 로그인 화면으로 보낸다(인자가 있는 동작도 감쌀 수 있게 제네릭으로 둔다)
  function requireLogin<Args extends unknown[]>(action: (...args: Args) => void) {
    return (...args: Args) => (isLoggedIn ? action(...args) : redirectToLogin())
  }

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
      showWishlistAddedDialog({
        entryKey,
        name: popup?.name ?? targetName,
        posterUrl: popup?.posterUrl ?? null,
      })
    },
    onError: handleError,
  })

  // 비로그인이면 캐시에 이전 찜이 남아 있어도 "찜하지 않음"으로 본다.
  // 상태로 따로 두지 않고 매 렌더에서 파생하는 이유: 로그아웃·역할 전환 때 따로 동기화할 곳이 없어야 낡은 "찜함"이 남지 않는다.
  // 로그인 판단은 useIsLoggedIn 한 곳만 거치므로 Task 057에서 훅 내부를 authStore로 바꾸면 이 가드도 그대로 따라간다.
  const entry = isLoggedIn ? (state?.[entryKey] ?? null) : null

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
    onError: handleError,
  })

  const remove = useMutation<unknown, ApiError, void>({
    mutationFn: () =>
      customInstance({ url: `/me/wishlist-items/${entry?.wishlistItemId}`, method: 'DELETE' }),
    onSuccess: () => {
      writeEntry(null)
      toast.success(`${targetName} 찜을 해제했습니다.`)
    },
    onError: handleError,
  })

  return {
    priority: entry?.priority ?? null, // 찜하지 않았으면 null
    isPending: add.isPending || changePriority.isPending || remove.isPending,
    onAdd: requireLogin(() => add.mutate()),
    onChangePriority: requireLogin((priority: WishlistPriority) => changePriority.mutate(priority)),
    onRemove: requireLogin(() => remove.mutate()),
  }
}
