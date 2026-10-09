import { Link } from 'react-router'
import type { OttService } from '@/api/generated/model'
import { PosterImage } from '@/components/common/PosterImage'
import { MEDIA_TYPE_LABELS } from '@/features/search/constants'
import type { TitleSearchItem } from '@/features/search/mockTypes'
import { buildServiceStatusEntries } from '@/lib/serviceStatus'

// 포스터 위에 얹는 서비스 칩의 최대 개수. 넘치면 "+N"으로 줄여 포스터를 가리지 않게 한다
const MAX_CHIPS = 2

interface SearchResultCardProps {
  item: TitleSearchItem // 검색 결과의 작품 하나
  services: OttService[] // 서비스 목록 API의 7개 서비스(이름을 상태와 합치는 데 쓴다)
}

/**
 * 검색 결과 카드 하나(세로 구조: 포스터 → 제목 → "연도 · 영화/드라마"). 포스터 왼쪽 위에 볼 수 있는 곳 칩이 얹힌다.
 *
 * 카드에 찜 버튼이 없는 이유: 카드는 "눌러서 상세로"만 맡는다. 찜은 상세에서만 한다(영화는 작품 단위, 드라마는 시즌 단위라
 * 목록에서는 일관되게 다룰 수 없다). 그래서 카드 안에 버튼이 없어 링크 하나로 카드 전체를 덮어도 충돌하지 않는다.
 *
 * 카드 전체를 누를 수 있게 한 방법: 제목 링크(<a>)의 ::after를 카드 크기로 늘려(after:absolute after:inset-0) 카드 위를 덮는다.
 * 낭독기에는 제목 링크 하나로만 읽히고 탭 순서도 하나다. 포스터와 칩은 링크를 따로 두지 않는다(같은 링크가 여러 번 잡히지 않게).
 * 칩은 pointer-events-none이라 눌러도 아래의 늘어난 링크가 받는다.
 * 상세 주소에 mediaType을 넣는 이유: 영화 "카지노"와 드라마 "카지노"처럼 제목이 같은 작품을 구분하기 위함이다.
 *
 * 칩에 서비스 이름 글자가 있어 색만으로 뜻을 전하지 않는다. 칩의 접근 가능한 이름은 목록 라벨("볼 수 있는 곳")이 맡는다.
 * 제공처가 하나도 없으면 칩 자리 대신 포스터 아래 글자로 알려, 정보가 빠진 것과 구분한다.
 */
export function SearchResultCard({ item, services }: SearchResultCardProps) {
  const detailPath = `/titles/${item.mediaType}/${item.tmdbId}`
  const releaseYearText = item.releaseYear ?? '연도 미상'

  const available = buildServiceStatusEntries(services, item.availabilities).filter(
    (entry) => entry.status === 'AVAILABLE',
  )
  const visibleChips = available.slice(0, MAX_CHIPS)
  const hiddenCount = available.length - visibleChips.length

  return (
    // relative: 늘어난 제목 링크(::after)의 기준점이다.
    // has-[a:focus-visible]: 안쪽 제목 링크에 키보드 포커스가 오면 카드 전체에 링을 그린다(늘어난 링크 영역과 같은 크기)
    <article className="group relative grid content-start gap-2 rounded-lg transition-opacity hover:opacity-90 has-[a:focus-visible]:ring-2 has-[a:focus-visible]:ring-ring has-[a:focus-visible]:ring-offset-2 has-[a:focus-visible]:ring-offset-background">
      <div className="relative">
        <PosterImage title={item.title} posterUrl={item.posterUrl} />
        {available.length > 0 && (
          <ul
            aria-label="볼 수 있는 곳"
            className="pointer-events-none absolute top-2 left-2 flex max-w-[calc(100%-1rem)] flex-wrap gap-1"
          >
            {visibleChips.map(({ service }) => (
              <li
                key={service.ottServiceId}
                className="rounded-md bg-background/85 px-1.5 py-0.5 text-xs leading-tight font-semibold text-foreground"
              >
                {service.name}
              </li>
            ))}
            {hiddenCount > 0 && (
              <li className="rounded-md bg-background/85 px-1.5 py-0.5 text-xs leading-tight font-semibold text-foreground">
                +{hiddenCount}
              </li>
            )}
          </ul>
        )}
      </div>

      <div className="grid min-w-0 gap-0.5">
        {available.length === 0 && (
          <p className="text-xs text-muted-foreground">확인된 제공처가 없습니다</p>
        )}
        <h3 className="line-clamp-2 text-sm leading-snug font-semibold">
          <Link
            to={detailPath}
            className="text-inherit no-underline after:absolute after:inset-0 after:content-[''] group-hover:underline focus-visible:outline-none"
          >
            {item.title}
          </Link>
        </h3>
        <p className="text-xs text-muted-foreground tabular-nums">
          {releaseYearText} · {MEDIA_TYPE_LABELS[item.mediaType]}
        </p>
      </div>
    </article>
  )
}

/*
 * 이전 구조(가로 카드 + 영화 하트 찜 버튼)의 하트 부분. 카드에서 찜을 뺐으므로 쓰지 않지만 참고용으로 남긴다.
 * 이를 되살리려면 useWishlistItem(features/search/hooks), WishlistButton(variant="icon")을 다시 import해야 한다.
 *
 * function MovieWishlist({ item }: { item: TitleSearchItem }) {
 *   const wishlist = useWishlistItem(
 *     { mediaType: item.mediaType, tmdbId: item.tmdbId, seasonNumber: null },
 *     item.title,
 *   )
 *   return (
 *     <div className="absolute top-1 right-1 z-10">
 *       <WishlistButton targetName={item.title} variant="icon" panelAlign="end" {...wishlist} />
 *     </div>
 *   )
 * }
 */
