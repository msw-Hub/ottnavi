import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { DataInsufficientTag } from '@/components/common/DataInsufficientTag'
import { PosterImage } from '@/components/common/PosterImage'
import { ProviderStatusBadge } from '@/components/common/ProviderStatusBadge'
import { CardWishlistAction } from '@/features/home/components/CardWishlistAction'
import type { HomeService, HomeTitle } from '@/features/home/mockTypes'
import { cn } from '@/lib/utils'

// 작품 구분 글자(features/search/constants의 MEDIA_TYPE_LABELS와 같은 글자. 기능 폴더끼리 import하지 않아 따로 둔다)
const MEDIA_TYPE_TEXT = { movie: '영화', tv: '드라마' } as const

// "볼 수 있는 곳" 칩의 최대 개수. 넘치면 "+N"으로 줄여 좁은 카드에서 줄이 늘어나지 않게 한다
const MAX_SERVICE_CHIPS = 2

interface HomeTitleCardProps {
  title: HomeTitle // 보여 줄 작품
  services: HomeService[] // 서비스 7곳(상태 목록의 서비스 ID를 이름으로 바꾸는 데 쓴다)
  focusServiceId?: number // 이 목록이 한 서비스 기준이면 그 서비스 ID. 주면 그 서비스의 제공 상태 배지를, 안 주면 "볼 수 있는 곳" 칩을 보인다
  rank?: number // 순위 목록이면 순위(1부터). 주면 포스터 왼쪽에 큰 숫자로 보인다
  rankLabel?: string // 순위 앞에 낭독기가 읽을 말(예: "인기", "찜"). 화면에는 숫자만 보인다
  meta?: ReactNode // 카드 맨 아래 보조 한 줄(예: "찜 214명", "10월 15일 확인")
}

/**
 * 홈·랭킹·탐색이 함께 쓰는 작품 카드다(세로 구조: [순위] 포스터 → 제목 → "영화/드라마 · 연도" → 제공 상태 → 보조 한 줄).
 *
 * 검색 결과 카드(features/search의 SearchResultCard)와 같은 뼈대를 따르되 두 가지가 다르다.
 * 1. 찜 버튼이 포스터 오른쪽 위에 있다(CardWishlistAction). 홈은 둘러보다 바로 찜하는 곳이라 넣었다.
 * 2. 한 서비스 기준 목록(서비스 탭)에서는 그 서비스의 상태를 있음/없음/모름 배지(ProviderStatusBadge)로 보인다.
 *    배지는 글자·아이콘·테두리 모양이 함께 바뀌어 색만으로 구분하지 않는다.
 *
 * 카드 전체를 누를 수 있게 한 방법: 제목 링크(<a>)의 ::after를 카드 크기로 늘려 카드 위를 덮는다(검색 카드와 같다).
 * 낭독기에는 제목 링크 하나로 읽히고 탭 순서도 하나다. 찜 버튼은 relative z-10이라 늘어난 링크 위에 떠서 따로 눌린다.
 * 키보드 포커스 표시: 제목 링크(data-card-link)에 포커스가 오면 카드 전체에 링을 그린다. 찜 버튼의 포커스는 버튼 자체 링으로 보인다.
 *
 * 순위 숫자는 장식이 아니라 정보라 낭독기에도 읽힌다("인기 1위"). "위"와 앞말은 화면에서 숨기고(sr-only) 숫자만 크게 보인다.
 */
export function HomeTitleCard({
  title,
  services,
  focusServiceId,
  rank,
  rankLabel = '',
  meta,
}: HomeTitleCardProps) {
  const detailPath = `/titles/${title.mediaType}/${title.tmdbId}`
  const hasRank = rank !== undefined

  return (
    <article
      className={cn(
        'group relative grid content-start gap-2 rounded-lg has-[[data-card-link]:focus-visible]:ring-2 has-[[data-card-link]:focus-visible]:ring-ring has-[[data-card-link]:focus-visible]:ring-offset-2 has-[[data-card-link]:focus-visible]:ring-offset-background',
        // 순위가 있으면 [큰 숫자 | 포스터] 두 칸. 숫자 칸 폭을 3.25rem으로 고정해 1위와 10위의 포스터 크기가 같게 한다
        // (이전: grid-cols-[auto_minmax(0,1fr)] — 숫자 폭만큼 칸이 늘어 두 자리 순위의 포스터가 작아졌다)
        hasRank && 'grid-cols-[3.25rem_minmax(0,1fr)] gap-x-1',
      )}
    >
      {hasRank && (
        // text-5xl(48px) 굵은 숫자: TVING 등 OTT의 TOP 줄처럼 순위가 먼저 눈에 들어오게 한다. 큰 글자라 대비 기준은 3:1이고
        // 글자색(foreground)은 바탕 대비 약 18:1이다. tabular-nums로 1과 10의 숫자 폭을 맞춘다. 포스터 아래쪽에 붙인다(self-end)
        // 두 자리 순위(10위~)는 고정 칸에 들어가게 한 단계 작게(text-4xl) 쓴다. 오른쪽 맞춤으로 숫자가 포스터에 붙는다
        <p
          className={cn(
            'row-start-1 self-end text-right leading-none font-black tracking-tighter text-foreground tabular-nums',
            rank >= 10 ? 'text-4xl' : 'text-5xl',
          )}
        >
          <span className="sr-only">{rankLabel} </span>
          {rank}
          <span className="sr-only">위</span>
        </p>
      )}

      <div className={cn('relative', hasRank && 'col-start-2 row-start-1')}>
        <PosterImage title={title.title} posterUrl={title.posterUrl} />
        <div className="absolute top-1 right-1">
          <CardWishlistAction
            title={title.title}
            mediaType={title.mediaType}
            detailPath={detailPath}
          />
        </div>
      </div>

      <div className={cn('grid min-w-0 content-start gap-1', hasRank && 'col-start-2')}>
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
        </p>
        {focusServiceId !== undefined ? (
          <FocusServiceStatus title={title} services={services} serviceId={focusServiceId} />
        ) : (
          <AvailableServiceChips title={title} services={services} />
        )}
        {meta && <p className="text-xs font-semibold text-foreground tabular-nums">{meta}</p>}
      </div>
    </article>
  )
}

interface CardStatusProps {
  title: HomeTitle
  services: HomeService[]
}

// 한 서비스 기준 목록: "넷플릭스 [✓ 있음]"처럼 서비스 이름과 3상태 배지를 함께 보인다
function FocusServiceStatus({
  title,
  services,
  serviceId,
}: CardStatusProps & { serviceId: number }) {
  const service = services.find((entry) => entry.ottServiceId === serviceId)
  const status =
    title.availabilities.find((entry) => entry.ottServiceId === serviceId)?.status ?? 'UNKNOWN'
  if (!service) return null

  return (
    <p className="flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-muted-foreground">
      <span>{service.name}</span>
      {/* 카드가 좁아(약 128px) 공통 배지를 한 단계 작게(text-xs) 쓴다. 모양(채움·실선·점선)과 아이콘·글자는 그대로다 */}
      <ProviderStatusBadge status={status} className="py-0 pr-2 pl-1.5 text-xs leading-5" />
      {service.dataQuality === 'INSUFFICIENT' && <DataInsufficientTag />}
    </p>
  )
}

// 여러 서비스 목록: "있음"인 서비스 이름만 칩으로(최대 2개 + "+N"). 하나도 없으면 글자로 알린다(검색 카드와 같은 규칙)
function AvailableServiceChips({ title, services }: CardStatusProps) {
  const availableNames = services
    .filter((service) =>
      title.availabilities.some(
        (entry) => entry.ottServiceId === service.ottServiceId && entry.status === 'AVAILABLE',
      ),
    )
    .map((service) => service.name)

  if (availableNames.length === 0) {
    return <p className="text-xs text-muted-foreground">확인된 제공처가 없습니다</p>
  }

  const visibleNames = availableNames.slice(0, MAX_SERVICE_CHIPS)
  const hiddenCount = availableNames.length - visibleNames.length
  return (
    // 칩의 바탕·글자는 "있음" 토큰(초록 채움 + 짙은 글자)이다. 칩마다 서비스 이름 글자가 있어 색만으로 뜻을 전하지 않는다
    <ul aria-label="볼 수 있는 곳" className="flex flex-wrap gap-1">
      {visibleNames.map((name) => (
        <li
          key={name}
          className="rounded-md bg-status-available-bg px-1.5 py-0.5 text-xs leading-tight font-semibold text-status-available"
        >
          {name}
        </li>
      ))}
      {hiddenCount > 0 && (
        <li className="rounded-md border border-border px-1.5 py-0.5 text-xs leading-tight font-semibold text-foreground">
          +{hiddenCount}
          <span className="sr-only">곳 더</span>
        </li>
      )}
    </ul>
  )
}
