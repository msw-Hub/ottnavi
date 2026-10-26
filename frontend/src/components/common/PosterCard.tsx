import { Link } from 'react-router'
import { PosterImage } from '@/components/common/PosterImage'

interface PosterCardProps {
  title: string // 작품 제목. 길면 두 줄까지만 보이고 말줄임(…) 처리한다
  posterUrl: string | null // 포스터 이미지 주소. 없으면 null → "포스터 없음" 대체 표시
  subtitle?: string // 제목 아래 보조 정보(예: "드라마 · 2021")
  to?: string // 누르면 이동할 화면 주소(예: 작품 상세). 생략하면 링크 없이 카드만 그린다
}

/**
 * 작품 포스터와 제목을 보여 주는 카드다(검색 결과, 찜 목록 등에서 쓴다).
 *
 * 포스터 그림과 "포스터 없음"·불러오기 실패(onError) 처리는 PosterImage가 맡고,
 * 이 카드는 그 아래에 제목·부가 정보를 붙이는 역할만 한다.
 * 완성된 주소(posterUrl)를 받는 이유도 PosterImage 주석을 참고한다(주소 결정은 호출부 몫).
 */
export function PosterCard({ title, posterUrl, subtitle, to }: PosterCardProps) {
  const content = (
    <>
      <PosterImage title={title} posterUrl={posterUrl} />
      {/* line-clamp-2: 두 줄을 넘는 제목은 말줄임 처리한다. 잘린 부분은 title 속성(마우스를 올리면 보이는 말풍선)으로 전체를 보인다.
          글자 자체는 모두 남아 있어 화면 낭독기는 제목 전체를 읽는다 */}
      <span title={title} className="line-clamp-2 text-[0.9375rem] leading-snug font-bold">
        {title}
      </span>
      {subtitle && (
        <span className="text-[0.8125rem] text-muted-foreground tabular-nums">{subtitle}</span>
      )}
    </>
  )

  const cardClassName = 'grid content-start gap-1.5 rounded-lg'

  if (to) {
    return (
      <Link
        to={to}
        className={`${cardClassName} text-inherit no-underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring`}
      >
        {content}
      </Link>
    )
  }
  return <div className={cardClassName}>{content}</div>
}
