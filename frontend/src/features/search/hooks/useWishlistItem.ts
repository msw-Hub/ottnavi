import { useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import {
  useAddWishlistItem,
  useListWishlistItems,
  useRemoveWishlistItem,
  useUpdateWishlistItem,
} from '@/api/generated/wishlist/wishlist'
import type { MediaType, WishlistPriority } from '@/api/generated/model'
import type { ApiError } from '@/api/http'
import { useIsLoggedIn } from '@/hooks/useIsLoggedIn'
import { useWishlistAddedDialog } from '@/hooks/useWishlistAddedDialog'
import { toastApiError } from '@/lib/errorToast'
import { getMockRequestParams } from '@/lib/mockScenario'

// 우선순위를 묻지 않고 찜할 때 쓰는 기본값(보고 싶음)
const DEFAULT_WISHLIST_PRIORITY: WishlistPriority = 'WANT'

interface WishlistTarget {
  mediaType: MediaType // 작품 구분
  tmdbId: number // TMDB ID
  seasonNumber: number | null // 시즌 단위 찜이면 시즌 번호, 영화는 null
}

// 찜 추가 팝업이 같은 작품(시즌)을 구분하는 키("movie:496243:all", "tv:93405:2")
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
  if (!error.hasNotifiedUser) toastApiError(error.errorCode, error.userMessage)
}

// 비로그인 요청에 서버가 돌려주는 상태 코드
const UNAUTHORIZED_STATUS = 401

// 활성 찜이 20개일 때 새 찜을 추가하면 서버가 409로 알리는 오류 코드(docs/api/error-codes.md)
const WISHLIST_LIMIT_EXCEEDED_ERROR_CODE = 'WISHLIST_LIMIT_EXCEEDED'

// 찜 목록 쿼리 키(생성 훅 useListWishlistItems의 기본 키와 같다). 변경 뒤 이 키를 무효화해 목록을 다시 받는다
const WISHLIST_LIST_QUERY_KEY = ['/me/wishlist-items'] as const
// 플랜 초안 쿼리 키(features/plan이 쓰는 키). 기능 폴더끼리 import하지 않으려고 문자열로 직접 적는다
const PLAN_DRAFTS_QUERY_KEY = ['/me/plan-drafts'] as const

/**
 * 작품(영화) 또는 시즌(드라마) 하나의 찜 상태와 찜 추가·우선순위 변경·해제를 돌려준다.
 *
 * 생성 훅(useAddWishlistItem / useUpdateWishlistItem / useRemoveWishlistItem, 계약 addWishlistItem / updateWishlistItem / removeWishlistItem)을 감싼다.
 * - 추가:   POST   /me/wishlist-items          (우선순위는 묻지 않고 기본값 WANT로 추가)
 * - 변경:   PATCH  /me/wishlist-items/{id}
 * - 해제:   DELETE /me/wishlist-items/{id}
 *
 * 찜 상태는 서버의 찜 목록(GET /me/wishlist-items, 계약 listWishlistItems)에서 이 작품(시즌)에 해당하는 항목을 찾아 얻는다(Task 058).
 * 예전에는 "이 세션에서 찜한 것"을 캐시에 따로 적어 두었는데, 새로고침하면 사라지고 다른 기기에서 찜한 것을 몰라
 * 이미 찜한 작품이 "찜하기"로 보였다(실제 서버에서는 추가가 200으로 기존 항목을 돌려줘 우선순위가 화면과 어긋난다). 서버 목록을 기준으로 하면 새로고침·다른 기기에서도 같다.
 * 찜 목록 화면(features/wishlist)과 같은 쿼리 키라 요청 하나를 같이 쓰고, 한쪽에서 바꾸면 다른 쪽도 갱신된다.
 * 변경 요청이 성공하면 목록을 다시 받고(무효화), 다시 받는 동안은 mutation이 끝나지 않은 것으로 두어(onSuccess가 Promise를 돌려줌)
 * 화면에 낡은 상태가 번쩍이지 않게 한다. 계산 대상이 달라지므로 플랜 초안 캐시도 무효화한다.
 * "다 봤음" 항목도 목록에 있으므로 찜한 것으로 본다(추가 요청은 200으로 기존 항목을 돌려주고 아무것도 바꾸지 않는다).
 *
 * 비로그인이면 추가·우선순위 변경·해제 모두 요청을 보내지 않고 로그인 화면(/login)으로 보낸다(from 포함, Task 027). 서버가 401을 줘도 같다.
 * ① 요청 전: 공통 훅 useIsLoggedIn이 비로그인이면 바로 이동한다(401은 브라우저 콘솔에 오류로 남기 때문).
 * ② 요청 후: 로그인이 만료된 경우 등 서버가 401을 주면 같은 이동을 한다.
 * 비로그인이면 찜 목록도 요청하지 않는다(enabled: false). 어차피 401이라 콘솔에 오류만 남기 때문이다.
 * 화면 가드(RequireAuth)와 달리 찜은 공개 화면 안의 동작이라, 막는 쪽이 아니라 "로그인을 권하는" 쪽으로 처리한다.
 * mutation은 TanStack Query 기본값이 재시도 0번이라 같은 요청이 두 번 저장되지 않는다.
 * features/title-detail에도 같은 훅이 있는 이유: 기능 폴더끼리는 서로 import하지 않기 때문이다.
 * 현재 미사용: 검색 카드에서 찜 하트를 없앴다(찜은 상세에서만). 하트를 되살릴 때를 위해 파일은 남긴다.
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

  // 요청이 실패했을 때: 401이면 로그인 화면으로 보내고, 찜이 20개를 넘으면 [찜 목록 가기]가 있는 토스트를, 그 밖의 오류는 토스트로 알린다
  function handleError(error: ApiError) {
    if (error.status === UNAUTHORIZED_STATUS) {
      redirectToLogin()
      return
    }
    if (error.errorCode === WISHLIST_LIMIT_EXCEEDED_ERROR_CODE) {
      // 본문은 errorMessages의 WISHLIST_LIMIT_EXCEEDED 문구(error.userMessage)다. 버튼은 찜 목록으로 보내 자리를 만들게 한다(확정, PRD SCR-08).
      // 목업 확인용 주소 파라미터(?mockScenario=)는 이어 붙여 같은 시나리오로 이어 본다(운영에서는 빈 문자열이다)
      // 오류가 아니라 경고 토스트로 뜬다(경고 대상 코드는 lib/errorToast.ts 한곳에서 정한다)
      toastApiError(error.errorCode, error.userMessage, {
        action: {
          label: '찜 목록 가기',
          onClick: () =>
            navigate({
              pathname: '/wishlist',
              search: new URLSearchParams(getMockRequestParams()).toString(),
            }),
        },
      })
      return
    }
    notifyError(error)
  }

  // 로그인이 필요한 동작을 감싼다. 비로그인이면 요청을 보내지 않고 로그인 화면으로 보낸다(인자가 있는 동작도 감쌀 수 있게 제네릭으로 둔다)
  function requireLogin<Args extends unknown[]>(action: (...args: Args) => void) {
    return (...args: Args) => (isLoggedIn ? action(...args) : redirectToLogin())
  }

  // 서버의 찜 목록. 비로그인이면 요청하지 않는다
  const wishlist = useListWishlistItems({
    query: { enabled: isLoggedIn, select: (res) => res.data },
  })

  // 이 작품(시즌)에 해당하는 찜 항목. 상태로 따로 두지 않고 매 렌더에서 목록에서 찾는다(파생 값)
  const entry =
    isLoggedIn && wishlist.data
      ? (wishlist.data.items.find(
          (item) =>
            item.mediaType === target.mediaType &&
            item.tmdbId === target.tmdbId &&
            item.seasonNumber === target.seasonNumber,
        ) ?? null)
      : null

  // 변경 성공 뒤 목록·플랜 초안 캐시를 새로 받는다. Promise를 돌려주어 mutation이 갱신이 끝난 뒤에 완료되게 한다
  function refreshWishlistQueries() {
    return Promise.all([
      queryClient.invalidateQueries({ queryKey: WISHLIST_LIST_QUERY_KEY }),
      queryClient.invalidateQueries({ queryKey: PLAN_DRAFTS_QUERY_KEY }),
    ])
  }

  // 찜 추가(계약 addWishlistItem). 새로 추가면 201, 이미 찜한 작품(시즌)이면 200으로 같은 모양의 응답({ wishlistItemId })이 온다.
  // 두 경우 모두 성공이라 화면은 구분하지 않는다. 활성 찜이 20개일 때 새 항목은 409 WISHLIST_LIMIT_EXCEEDED이고,
  // handleError가 userMessage(errorMessages.ts의 getErrorMessage 결과)를 토스트로 알린다.
  const add = useAddWishlistItem({
    mutation: {
      onSuccess: async () => {
        await refreshWishlistQueries()
        showWishlistAddedDialog({
          entryKey,
          name: popup?.name ?? targetName,
          posterUrl: popup?.posterUrl ?? null,
        })
      },
      onError: handleError,
    },
  })

  const changePriority = useUpdateWishlistItem({
    mutation: {
      onSuccess: async () => {
        await refreshWishlistQueries()
      },
      onError: handleError,
    },
  })

  const remove = useRemoveWishlistItem({
    mutation: {
      onSuccess: async () => {
        await refreshWishlistQueries()
        toast.success(`${targetName} 찜을 해제했습니다.`)
      },
      onError: handleError,
    },
  })

  return {
    priority: entry?.priority ?? null, // 찜하지 않았으면 null
    // 목록을 처음 받는 중에도 눌리지 않게 한다(찜 여부를 모르는 채 추가하면 기존 찜을 건드릴 수 있다)
    isPending:
      add.isPending ||
      changePriority.isPending ||
      remove.isPending ||
      (isLoggedIn && wishlist.isPending),
    onAdd: requireLogin(() =>
      add.mutate({ data: { ...target, priority: DEFAULT_WISHLIST_PRIORITY } }),
    ),
    onChangePriority: requireLogin((priority: WishlistPriority) => {
      if (entry) changePriority.mutate({ wishlistItemId: entry.wishlistItemId, data: { priority } })
    }),
    onRemove: requireLogin(() => {
      if (entry) remove.mutate({ wishlistItemId: entry.wishlistItemId })
    }),
  }
}
