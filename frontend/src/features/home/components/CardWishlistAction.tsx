import { useState } from 'react'
import { HeartIcon, HeartPlusIcon } from 'lucide-react'
import { Link, useLocation } from 'react-router'
import type { MediaType } from '@/api/generated/model'
import { useSession } from '@/hooks/useSession'
import { cn } from '@/lib/utils'

interface CardWishlistActionProps {
  title: string // 작품 제목(낭독기가 여러 카드의 찜 버튼을 구분하는 이름)
  mediaType: MediaType // 영화는 바로 찜, 드라마는 시즌을 골라야 해 상세로 보낸다
  detailPath: string // 작품 상세 주소(드라마일 때 이동할 곳)
}

// 카드 포스터 오른쪽 위에 얹는 원형 버튼 공통 모양. size-11(44px)은 손가락 터치 크기 기준이다.
// 포스터 그림 위에서도 보이게 반투명 바탕(bg-background/80)을 깐다. 아이콘 색은 글자색(foreground)이라 바탕 대비가 충분하다
const ACTION_CLASS_NAME =
  'relative z-10 inline-flex size-11 items-center justify-center rounded-full bg-background/80 text-foreground backdrop-blur-sm hover:bg-background focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring'

/**
 * 홈 목록 카드의 찜 버튼(1차안 시안)이다. 세 가지로 나뉜다.
 *
 * 1. 비로그인: 찜은 회원 기능이라 로그인 화면으로 보내는 링크다. 로그인 뒤 이 화면으로 돌아오게 from을 넘긴다.
 * 2. 드라마: 찜이 시즌 단위라(PRD FR-09) 목록에서 바로 찜하지 않고 상세 화면으로 보낸다. 아이콘을 "하트+"로 바꿔 동작이 다르다는 것을 보인다.
 * 3. 영화(회원): 누르면 찜함/찜 안 함이 바뀌는 토글이다. 상태는 aria-pressed로 낭독기에 알리고, 화면에서는 빈 하트 → 꽉 찬 하트로
 *    모양이 바뀐다(색만으로 구분하지 않음).
 *
 * 공통 WishlistButton을 쓰지 않은 이유: 그 버튼은 누르면 우선순위 상자(약 300px)가 버튼 아래로 열리는데, 가로 스크롤 줄 안에서는
 * 상자가 줄 밖으로 나가지 못하고 잘린다(가로 스크롤 영역은 세로로도 내용을 자른다). 목록에서는 찜 여부만 바꾸고, 우선순위는 찜 목록에서 바꾼다.
 *
 * 주의(1차안 한계): 3번의 찜 상태는 이 버튼 안의 임시 상태(useState)라 서버에 저장되지 않고, 탭을 바꾸면 사라진다.
 * 실제 찜 연결(찜 추가·해제 API, 찜 추가 팝업)은 계약이 정해진 뒤 frontend-dev가 붙인다.
 */
export function CardWishlistAction({ title, mediaType, detailPath }: CardWishlistActionProps) {
  const session = useSession()
  const location = useLocation()
  const [isWished, setIsWished] = useState(false) // 시안용 임시 찜 상태(서버 저장 없음)
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
  return (
    <button
      type="button"
      aria-label={`${title} 찜하기`}
      aria-pressed={isWished}
      onClick={() => setIsWished((current) => !current)}
      className={cn(ACTION_CLASS_NAME, isWished && 'text-primary')}
    >
      <HeartIcon aria-hidden="true" className={cn('size-5', isWished && 'fill-current')} />
    </button>
  )
}
