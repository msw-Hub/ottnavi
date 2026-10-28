import { HeartIcon, HeartPlusIcon } from 'lucide-react'
import { Link, useLocation } from 'react-router'
import type { MediaType } from '@/api/generated/model'
import { useSession } from '@/hooks/useSession'
import { useWishlistItem } from '@/hooks/useWishlistItem'
import { getTmdbImageUrl } from '@/lib/tmdbImage'
import { cn } from '@/lib/utils'

interface CardWishlistActionProps {
  title: string // 작품 제목(낭독기가 여러 카드의 찜 버튼을 구분하는 이름)
  mediaType: MediaType // 영화는 바로 찜, 드라마는 시즌을 골라야 해 상세로 보낸다
  tmdbId: number // TMDB ID(영화 찜 요청에 쓴다)
  posterPath: string | null // 포스터 상대 경로(찜 추가 팝업의 작은 그림에 쓴다)
  detailPath: string // 작품 상세 주소(드라마일 때 이동할 곳)
}

// 카드 포스터 오른쪽 위에 얹는 원형 버튼 공통 모양. size-11(44px)은 손가락 터치 크기 기준이다.
// 포스터 그림 위에서도 보이게 반투명 바탕(bg-background/80)을 깐다. 아이콘 색은 글자색(foreground)이라 바탕 대비가 충분하다
const ACTION_CLASS_NAME =
  'relative z-10 inline-flex size-11 items-center justify-center rounded-full bg-background/80 text-foreground backdrop-blur-sm hover:bg-background focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring'

/**
 * 현재 사용처 없음(H-2, 사용자 확정): 홈·랭킹·탐색 카드(HomeTitleCard)의 찜 버튼 자리를 "볼 수 있는 곳" OTT 로고로 바꿔 이 부품을 쓰지 않는다.
 * 홈에서 영화를 바로 찜하는 기능이 빠지는 것은 사용자가 감수하기로 했다. 다시 쓸 수 있게 지우지 않고 둔다.
 *
 * 홈 목록 카드의 찜 버튼이다. 세 가지로 나뉜다.
 *
 * 1. 비로그인: 찜은 회원 기능이라 로그인 화면으로 보내는 링크다. 로그인 뒤 이 화면으로 돌아오게 from을 넘긴다.
 *    (링크로 두면 로그인 여부를 알고 있는 채로 서버에 401 요청을 보내 콘솔에 오류를 남기는 일이 없다)
 * 2. 드라마: 찜이 시즌 단위라(PRD FR-09) 목록에서 바로 찜하지 않고 상세 화면으로 보낸다. 아이콘을 "하트+"로 바꿔 동작이 다르다는 것을 보인다.
 * 3. 영화(회원): MovieWishlistToggle. 누르면 찜 추가/해제 API를 부르는 토글이다.
 *
 * 공통 WishlistButton을 쓰지 않은 이유: 그 버튼은 누르면 우선순위 상자(약 300px)가 버튼 아래로 열리는데, 가로 스크롤 줄 안에서는
 * 상자가 줄 밖으로 나가지 못하고 잘린다(가로 스크롤 영역은 세로로도 내용을 자른다). 목록에서는 찜 여부만 바꾸고(추가는 기본 우선순위
 * "보고 싶음"), 추가된 뒤에는 공통 찜 추가 팝업이 우선순위 변경을 안내한다.
 */
export function CardWishlistAction({
  title,
  mediaType,
  tmdbId,
  posterPath,
  detailPath,
}: CardWishlistActionProps) {
  const session = useSession()
  const location = useLocation()
  const isGuest = session.status !== 'authenticated'

  // 1. 비로그인(재발급 확인 중·오류 포함): 로그인 화면으로
  if (isGuest) {
    return (
      <Link
        to="/login"
        state={{ from: `${location.pathname}${location.search}` }}
        aria-label={`${title} 찜하기 (로그인 필요)`}
        className={ACTION_CLASS_NAME}
      >
        <HeartIcon aria-hidden="true" className="size-5" />
      </Link>
    )
  }

  // 2. 드라마: 시즌을 골라야 하므로 상세 화면으로
  if (mediaType === 'tv') {
    return (
      <Link
        to={detailPath}
        aria-label={`${title} 시즌 골라 찜하기 (상세로 이동)`}
        className={ACTION_CLASS_NAME}
      >
        <HeartPlusIcon aria-hidden="true" className="size-5" />
      </Link>
    )
  }

  // 3. 영화(회원): 찜 토글
  return <MovieWishlistToggle title={title} tmdbId={tmdbId} posterPath={posterPath} />
}

interface MovieWishlistToggleProps {
  title: string
  tmdbId: number
  posterPath: string | null
}

/**
 * 영화 카드의 찜 토글이다(회원 전용). 별도 컴포넌트로 나눈 이유: useWishlistItem은 찜 목록을 불러오는 훅이라
 * 비로그인·드라마 카드에서는 부르지 않아야 한다(훅은 조건문 안에서 부를 수 없어 컴포넌트를 나눠 "필요한 카드만" 부른다).
 *
 * 찜 상태는 서버의 찜 목록에서 온다(useWishlistItem의 priority가 null이면 찜하지 않음). 목록 요청은 모든 카드가 같은 키를 써서 한 번만 나간다.
 * 상태는 aria-pressed로 낭독기에 알리고, 화면에서는 빈 하트 → 꽉 찬 하트로 모양이 바뀐다(색만으로 구분하지 않음).
 * 찜 목록을 처음 받는 중이거나 요청 중에는 누를 수 없다(이미 찜한 작품을 모르는 채 추가하지 않게).
 */
function MovieWishlistToggle({ title, tmdbId, posterPath }: MovieWishlistToggleProps) {
  const wishlist = useWishlistItem({ mediaType: 'movie', tmdbId, seasonNumber: null }, title, {
    name: title,
    posterUrl: getTmdbImageUrl(posterPath, 'w92'),
  })
  const isWished = wishlist.priority !== null

  return (
    <button
      type="button"
      aria-label={`${title} 찜하기`}
      aria-pressed={isWished}
      disabled={wishlist.isPending}
      onClick={isWished ? wishlist.onRemove : wishlist.onAdd}
      className={cn(ACTION_CLASS_NAME, isWished && 'text-primary', 'disabled:opacity-60')}
    >
      <HeartIcon aria-hidden="true" className={cn('size-5', isWished && 'fill-current')} />
    </button>
  )
}
