import { CheckIcon } from 'lucide-react'
import { useSearchParams } from 'react-router'
import { DataAsOf } from '@/components/common/DataAsOf'
import { EmptyState } from '@/components/common/EmptyState'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import { InsufficientServiceNote } from '@/features/home/components/HomeRails'
import { HomeTitleCard } from '@/features/home/components/HomeTitleCard'
import { PeriodToggle } from '@/features/home/components/PeriodToggle'
import { ServiceTabs } from '@/features/home/components/ServiceTabs'
import {
  ALL_SERVICES_TAB,
  buildPopularityBasisText,
  buildServiceTabOptions,
  findServiceByTab,
  formatCount,
  parsePopularityPeriod,
  toPeriodParam,
} from '@/features/home/homeDisplay'
import type { HomePopularityPeriod } from '@/features/home/mockTypes'
import {
  getSamplePopularRanking,
  HOME_DATA_AS_OF,
  HOME_MOST_WISHLISTED,
  HOME_SERVICES,
} from '@/features/home/sampleData'
import { cn } from '@/lib/utils'

// 랭킹 화면에 보일 최대 개수
const RANKING_LIMIT = 20

// 순위 기준. 주소의 ?sort= 값과 화면 글자
const SORT_OPTIONS = [
  { value: 'popular', label: '인기 순위' },
  { value: 'wishlist', label: '찜 많은 작품' },
] as const
type RankingSort = (typeof SORT_OPTIONS)[number]['value']

// 순위 목록의 바둑판 배치. 모바일 2칸 → 넓은 화면 5칸(검색 결과 화면과 같은 칸 수)
const GRID_CLASS_NAME = 'grid grid-cols-2 gap-x-3 gap-y-6 sm:grid-cols-3 sm:gap-x-4 lg:grid-cols-5'

/**
 * 랭킹(`/ranking`) 화면 본문이다(홈 1차안, 홈 "더보기"의 목적지). 같은 작품 카드(HomeTitleCard)를 바둑판으로 늘어놓는다.
 *
 * 인기 순위에는 기간(?period=today|week, 오늘 / 최근 7일 평균) 전환이 서비스 탭 위에 따로 있다(홈 1차안 피드백).
 * 고른 기준(?sort=)·기간(?period=)·서비스(?service=)를 주소에 두는 이유: 홈의 "더보기"가 고른 탭 그대로 이 화면을 열고,
 * 새로고침·뒤로 가기·주소 공유에도 같은 목록이 보여야 한다(frontend.md "화면 위치·필터는 URL").
 * "찜 많은 작품"은 서비스를 가리지 않고 모은 숫자라 서비스 탭을 보이지 않는다.
 */
export function RankingScreen() {
  const [searchParams, setSearchParams] = useSearchParams()
  const sort: RankingSort = searchParams.get('sort') === 'wishlist' ? 'wishlist' : 'popular'
  const tab = searchParams.get('service') ?? ALL_SERVICES_TAB
  const service = findServiceByTab(HOME_SERVICES, tab)
  const period = parsePopularityPeriod(searchParams.get('period'))

  // 주소 파라미터 하나만 바꾼다. 다른 파라미터(목업 확인용 ?mockScenario 등)는 그대로 둔다. replace: 탭을 바꿀 때마다 뒤로 가기 기록이 쌓이지 않게 한다
  function updateParam(key: 'sort' | 'service' | 'period', value: string) {
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current)
        next.set(key, value)
        return next
      },
      { replace: true },
    )
  }

  return (
    <section aria-labelledby="ranking-title" className="flex flex-col gap-6">
      <div className="grid gap-1">
        <h1 id="ranking-title" className="text-2xl font-bold">
          랭킹
        </h1>
        <p className="text-muted-foreground">
          서비스마다 볼 수 있는 작품의 인기 순위와 OTT내비 사용자가 많이 찜한 작품을 모았어요.
        </p>
      </div>

      {/* 순위 기준 고르기. 두 선택지라 탭 대신 눌림 상태(aria-pressed) 버튼 묶음으로 둔다. 고른 버튼은 채움 + 체크 아이콘(색만으로 구분하지 않음) */}
      <div role="group" aria-label="순위 기준" className="flex flex-wrap gap-2">
        {SORT_OPTIONS.map((option) => {
          const isSelected = option.value === sort
          return (
            <button
              key={option.value}
              type="button"
              aria-pressed={isSelected}
              onClick={() => updateParam('sort', option.value)}
              className={cn(
                'inline-flex h-11 items-center gap-1 rounded-lg border px-4 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
                isSelected
                  ? 'border-foreground bg-foreground text-background'
                  : 'border-input text-muted-foreground hover:text-foreground',
              )}
            >
              {isSelected && <CheckIcon aria-hidden="true" strokeWidth={3} className="size-4" />}
              {option.label}
            </button>
          )
        })}
      </div>

      {sort === 'popular' ? (
        <div className="grid grid-cols-1 gap-3">
          {/* 기간 전환(오늘 / 최근 7일): 서비스 탭과 다른 모양으로 서비스 탭 위에 둔다 */}
          <PeriodToggle
            value={period}
            onChange={(next: HomePopularityPeriod) => updateParam('period', toPeriodParam(next))}
          />
          <ServiceTabs
            label="인기 순위 서비스 선택"
            value={tab}
            onValueChange={(value) => updateParam('service', value)}
            options={buildServiceTabOptions(HOME_SERVICES, true)}
          >
            {/* 이전: "TMDB 인기도 기준 · ○○에서 볼 수 있다고 확인된 작품만 · OTT 공식 순위가 아닙니다"(기간 표시 없음) */}
            <p className="mb-4 text-sm text-muted-foreground">
              {buildPopularityBasisText(
                period,
                service
                  ? `${service.name}에서 볼 수 있다고 확인된 작품만`
                  : '7곳 중 한 곳 이상에서 볼 수 있다고 확인된 작품만',
              )}
            </p>
            {service?.dataQuality === 'INSUFFICIENT' && (
              <InsufficientServiceNote
                service={service}
                text="제공 정보가 부족해 영화는 순위에서 빠지고, 볼 수 있다고 확인된 드라마만 보여요."
              />
            )}
            <PopularGrid serviceId={service?.ottServiceId ?? null} period={period} />
          </ServiceTabs>
        </div>
      ) : (
        <div className="grid gap-4">
          <p className="text-sm text-muted-foreground">
            OTT내비 사용자가 찜한 사람 수 기준(다 본 작품 제외) · 누가 찜했는지는 보이지 않아요
          </p>
          <ol aria-label="찜 많은 작품 순위" className={GRID_CLASS_NAME}>
            {HOME_MOST_WISHLISTED.map((item, index) => (
              <li key={`${item.title.mediaType}-${item.title.tmdbId}`}>
                <HomeTitleCard
                  title={item.title}
                  services={HOME_SERVICES}
                  rank={index + 1}
                  rankLabel="찜"
                  meta={`찜 ${formatCount(item.wishlistUserCount)}명`}
                />
              </li>
            ))}
          </ol>
        </div>
      )}

      <div className="grid gap-1.5 border-t border-border pt-4">
        <SourceAttribution />
        <DataAsOf value={HOME_DATA_AS_OF} />
        <p className="text-sm text-muted-foreground">
          제공 여부는 확인된 범위이며, 실제 서비스 상황과 다를 수 있습니다.
        </p>
      </div>
    </section>
  )
}

// 인기 순위 바둑판(서비스 하나 또는 전체, 기간별). 작품이 없으면 빈 상태 안내
function PopularGrid({
  serviceId,
  period,
}: {
  serviceId: number | null
  period: HomePopularityPeriod
}) {
  const ranking = getSamplePopularRanking(serviceId, RANKING_LIMIT, period)
  const service = HOME_SERVICES.find((entry) => entry.ottServiceId === serviceId)

  if (ranking.length === 0) {
    return <EmptyState title="이 서비스에서 확인된 작품이 아직 없습니다" />
  }
  return (
    <ol aria-label={`${service?.name ?? '전체'} 인기 순위`} className={GRID_CLASS_NAME}>
      {ranking.map((title, index) => (
        <li key={`${title.mediaType}-${title.tmdbId}`}>
          <HomeTitleCard
            title={title}
            services={HOME_SERVICES}
            focusServiceId={serviceId ?? undefined}
            rank={index + 1}
            rankLabel="인기"
          />
        </li>
      ))}
    </ol>
  )
}
