import { Link } from 'react-router'
import { PosterImage } from '@/components/common/PosterImage'
// import { ProviderStatusBadge } from '@/components/common/ProviderStatusBadge' // 카드의 "있음" 배지를 다시 넣을 때 푼다
import type { ExclusiveTitle } from '@/features/exclusives/mockTypes'

// 작품 구분 글자(features/search/constants의 MEDIA_TYPE_LABELS와 같은 글자. 기능 폴더끼리 import하지 않아 따로 둔다)
const MEDIA_TYPE_TEXT = { movie: '영화', tv: '드라마' } as const

interface ExclusiveTitleCardProps {
  title: ExclusiveTitle // 보여 줄 독점 작품
  // serviceName: string // 지금 고른 서비스 이름. 카드의 "있음" 배지 줄을 다시 넣을 때 함께 되살린다
}

/**
 * 독점 목록의 작품 카드다(세로 구조: 포스터 → 제목 → "영화/드라마 · 연도 · 시즌" → 다른 곳 확인 결과).
 *
 * 홈 카드(features/home의 HomeTitleCard)와 뼈대가 같지만 찜 버튼이 없고, 대신 "확인된 범위"를 카드마다 숫자로 보인다.
 * 1. 이 서비스의 "있음" 배지는 넣지 않았다. 탭과 목록 요약 줄이 서비스를 이미 말해, 모든 카드에 같은 배지가 반복될 뿐이다.
 * 2. 맨 아래 "다른 6곳: 없음 5 · 모름 1"은 독점이 확정이 아니라는 근거다. 다른 곳이 "모름"이면 그곳에도 있을 수 있어
 *    숫자로 드러낸다(PRD FR-04 "확인된 범위의 독점"). 모름이 0이면 "다른 6곳 모두 없음"으로 짧게 쓴다.
 * 3. 드라마는 독점으로 확인된 시즌만 적는다(독점 판정이 시즌 단위라 일부 시즌만 독점일 수 있다).
 *
 * 카드 전체를 누를 수 있게 한 방법은 홈·검색 카드와 같다: 제목 링크의 ::after를 카드 크기로 늘린다.
 * 낭독기에는 제목 링크 하나로 읽히고 탭 순서도 하나다. 포커스가 오면 카드 전체에 링을 그린다.
 */
export function ExclusiveTitleCard({ title }: ExclusiveTitleCardProps) {
  const detailPath = `/titles/${title.mediaType}/${title.tmdbId}`
  const notAvailableCount = title.comparedServiceCount - title.unknownElsewhereCount

  return (
    <article className="group relative grid content-start gap-2 rounded-lg has-[[data-card-link]:focus-visible]:ring-2 has-[[data-card-link]:focus-visible]:ring-ring has-[[data-card-link]:focus-visible]:ring-offset-2 has-[[data-card-link]:focus-visible]:ring-offset-background">
      <PosterImage title={title.title} posterUrl={title.posterUrl} />

      <div className="grid min-w-0 content-start gap-1">
        <h3 className="line-clamp-2 text-sm leading-snug font-semibold">
          <Link
            to={detailPath}
            data-card-link=""
            title={title.title}
            className="text-inherit no-underline after:absolute after:inset-0 after:content-[''] group-hover:underline focus-visible:outline-none"
          >
            {title.title}
          </Link>
        </h3>
        <p className="text-xs text-muted-foreground tabular-nums">
          {MEDIA_TYPE_TEXT[title.mediaType]} · {title.releaseYear ?? '연도 미상'}
          {/* 시즌은 "시즌 1·2"처럼 가운뎃점으로 잇는다. 쉼표보다 좁은 카드에서 덜 끊긴다 */}
          {title.exclusiveSeasonNumbers && title.exclusiveSeasonNumbers.length > 0 && (
            <> · 시즌 {title.exclusiveSeasonNumbers.join('·')}</>
          )}
        </p>

        {/* 이전: 카드마다 "{서비스} [✓ 있음]" 배지를 보였다. 탭과 목록 요약 줄이 이미 서비스를 말하고 모든 카드가 같은 글자라
            정보 없이 줄만 늘어 뺐다(1차안 판단). 다시 넣으려면 아래 주석을 푼다
        <p className="flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-muted-foreground">
          <span>{serviceName}</span>
          <ProviderStatusBadge status="AVAILABLE" className="py-0 pr-2 pl-1.5 text-xs leading-5" />
        </p> */}

        {/* 다른 곳 확인 결과. 숫자가 정보라 tabular-nums로 자리를 맞춘다. 모름이 있으면 글자를 진하게 해 눈에 띄게 한다(색이 아니라 굵기) */}
        <p className="text-xs leading-snug text-muted-foreground tabular-nums">
          {title.unknownElsewhereCount === 0 ? (
            <>다른 {title.comparedServiceCount}곳 모두 없음</>
          ) : (
            <>
              다른 {title.comparedServiceCount}곳: 없음 {notAvailableCount} ·{' '}
              <strong className="font-semibold text-foreground">
                모름 {title.unknownElsewhereCount}
              </strong>
            </>
          )}
        </p>
      </div>
    </article>
  )
}
