import { useState } from 'react'
import { ImageOffIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

interface PosterImageProps {
  title: string // 작품 제목. 화면에는 보이지 않고 이미지 대체 텍스트("{제목} 포스터")에만 쓴다
  posterUrl: string | null // 포스터 이미지 주소. 없으면 null → "포스터 없음" 대체 표시
  className?: string // 크기·비율 조정용. 기본은 "가로 100% + 2:3"이고 호출부가 덮어쓸 수 있다(예: 상세 화면의 고정 너비)
}

/**
 * 제목 없이 포스터 그림만 보여 주는 공통 컴포넌트다(작품 상세 화면, PosterCard 안에서 쓴다).
 *
 * PosterCard에서 분리한 이유: 상세 화면은 포스터 옆에 제목을 따로 크게 보여 주므로
 * 제목이 포함된 카드를 그대로 쓰면 제목이 두 번 나온다. 그림 부분만 따로 쓸 수 있게 했다.
 *
 * posterPath(TMDB 상대 경로)가 아니라 완성된 주소(posterUrl)를 받는 이유:
 * - 화면마다 알맞은 크기가 다르다(목록은 w342, 상세는 w500 등). 크기는 호출부가 getTmdbImageUrl(lib/tmdbImage.ts)로 고른다.
 * - 목업에서 외부(TMDB) 이미지를 실제로 부를지는 Task 027에서 정한다. 이 컴포넌트가 직접 TMDB 주소를 만들면
 *   목업·테스트에서도 외부 요청이 나가므로, 주소 결정은 호출부에 맡긴다.
 *
 * 이미지가 없을 때의 대체 표시:
 * - posterUrl이 null이면(TMDB에 포스터가 없는 작품이 실제로 있다) 처음부터 "포스터 없음"을 그린다.
 * - 주소가 있어도 불러오기에 실패하면(onError) 같은 대체 표시로 바꾼다. 깨진 이미지 아이콘이 보이지 않게 하기 위함이다.
 */
export function PosterImage({ title, posterUrl, className }: PosterImageProps) {
  // 이미지 불러오기에 실패했는지. 실패하면 대체 표시로 바꾸려고 상태로 둔다.
  // 실패한 주소를 함께 기억하는 이유: 같은 자리에 다른 posterUrl이 들어오면(목록 재사용 등) 새 주소는 다시 시도해야 한다.
  // 그래서 "실패 여부"가 아니라 "실패한 주소"를 두고, 지금 주소와 같을 때만 실패로 본다(별도 useEffect로 초기화할 필요가 없다).
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  // 실제로 그릴 이미지 주소. 없거나 이미 실패한 주소면 null(대체 표시)
  const imageUrl = posterUrl !== failedUrl ? posterUrl : null

  return (
    // 2:3은 영화 포스터의 표준 비율이다. 어두운 포스터가 어두운 바탕에 묻히지 않게 얇은 테두리로 경계를 만든다(시안 D2c).
    // cn(tailwind-merge)을 쓰는 이유: 호출부가 넘긴 w-*, aspect-* 가 기본값과 충돌할 때 호출부 값이 이기게 하기 위함이다
    <div
      className={cn(
        'aspect-2/3 w-full overflow-hidden rounded-lg border border-border bg-muted',
        className,
      )}
    >
      {imageUrl ? (
        <img
          src={imageUrl}
          alt={`${title} 포스터`}
          // 화면 밖 이미지는 스크롤해 가까워질 때 불러온다(검색 결과처럼 카드가 많을 때 처음 로딩을 줄인다)
          loading="lazy"
          onError={() => setFailedUrl(imageUrl)}
          className="block size-full object-cover"
        />
      ) : (
        // 그림이 아니라 "포스터가 없다"는 정보이므로 role="img"와 이름을 붙여 낭독기가 한 덩어리로 읽게 한다
        <div
          role="img"
          aria-label="포스터 이미지 없음"
          className="grid size-full place-content-center justify-items-center gap-1.5 p-2 text-center text-[0.8125rem] text-muted-foreground"
        >
          <ImageOffIcon aria-hidden="true" strokeWidth={1.75} className="size-7" />
          <span>포스터 없음</span>
        </div>
      )}
    </div>
  )
}
