import { CheckIcon } from 'lucide-react'
import { useSearchParams } from 'react-router'
import type { OttService, PopularityPeriod } from '@/api/generated/model'
import { DataAsOf } from '@/components/common/DataAsOf'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import { InsufficientServiceNote } from '@/features/home/components/HomeRails'
import { HomeTitleCard } from '@/features/home/components/HomeTitleCard'
import { PeriodToggle } from '@/features/home/components/PeriodToggle'
import { RailSkeleton } from '@/features/home/components/RailSection'
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
import { usePopularityRanking, useWishlistedTitles } from '@/features/home/hooks/useHomeLists'
import { useOttServices } from '@/hooks/useOttServices'
import { cn } from '@/lib/utils'

// 랭킹 화면에 보일 최대 개수(계약 최대 20)
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
 * 랭킹(`/ranking`) 화면 본문이다(홈 "더보기"의 목적지). 같은 작품 카드(HomeTitleCard)를 바둑판으로 늘어놓는다.
 *
 * 인기 순위에는 기간(?period=today|week, 오늘 / 최근 7일 평균) 전환이 서비스 탭 위에 따로 있다.
 * 고른 기준(?sort=)·기간(?period=)·서비스(?service=)를 주소에 두는 이유: 홈의 "더보기"가 고른 탭 그대로 이 화면을 열고,
 * 새로고침·뒤로 가기·주소 공유에도 같은 목록이 보여야 한다(frontend.md "화면 위치·필터는 URL").
 * "찜 많은 작품"은 서비스를 가리지 않고 모은 숫자라 서비스 탭을 보이지 않는다.
 *
 * 서비스 목록(탭 이름)은 서비스 목록 API에서 받는다. 순위 자료는 아래 PopularSection / WishlistedSection이 각각 불러오고,
 * 데이터 기준일도 각 자료의 응답 값을 그 구역 끝에 보인다(자료마다 기준일이 다르다).
 */
export function RankingScreen() {
  const [searchParams, setSearchParams] = useSearchParams()
  const servicesQuery = useOttServices()
  const services = servicesQuery.data ?? []
  const sort: RankingSort = searchParams.get('sort') === 'wishlist' ? 'wishlist' : 'popular'
  const tab = searchParams.get('service') ?? ALL_SERVICES_TAB
  const service = findServiceByTab(services, tab)
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
    // 본문(제목 → 순위 기준 → 목록)과 출처 묶음 사이를 gap-section(40/48px)으로 띄운다(styles/tokens.css R1·R2).
    // 본문 안은 고르는 버튼과 그 결과가 한 덩어리라 이전과 같은 24px(gap-6)로 붙인다(크게 띄우면 버튼과 목록이 떨어져 보인다).
    // 이전: <section … className="flex flex-col gap-6"> (출처 묶음까지 모두 24px)
    <section aria-labelledby="ranking-title" className="flex flex-col gap-section">
      <div className="flex flex-col gap-6">
        <div className="grid gap-1">
          <h1 id="ranking-title" className="text-2xl font-bold">
            랭킹
          </h1>
          <p className="text-muted-foreground">
            {/* 이전: "서비스마다 볼 수 있는 작품의 인기 순위와 …". 순위가 최근 2년 안 한국 작품만 세게 바뀌어 범위를 밝혔다 */}
            서비스마다 볼 수 있는 최근 한국 작품의 인기 순위와 OTT내비 사용자가 많이 찜한 작품을
            모았어요.
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

        {servicesQuery.isPending ? (
          <RailSkeleton heading="랭킹" />
        ) : servicesQuery.isError ? (
          <ErrorState
            title="랭킹을 불러오지 못했습니다"
            error={servicesQuery.error}
            onRetry={() => void servicesQuery.refetch()}
          />
        ) : sort === 'popular' ? (
          <div className="grid grid-cols-1 gap-3">
            {/* 기간 전환(오늘 / 최근 7일): 서비스 탭과 다른 모양으로 서비스 탭 위에 둔다 */}
            <PeriodToggle
              value={period}
              onChange={(next: PopularityPeriod) => updateParam('period', toPeriodParam(next))}
            />
            <ServiceTabs
              label="인기 순위 서비스 선택"
              value={tab}
              onValueChange={(value) => updateParam('service', value)}
              options={buildServiceTabOptions(services, true)}
            >
              <PopularSection service={service} services={services} period={period} />
            </ServiceTabs>
          </div>
        ) : (
          <WishlistedSection services={services} />
        )}
      </div>

      <div className="grid gap-1.5 border-t border-border pt-4">
        <SourceAttribution />
        <p className="text-sm text-muted-foreground">
          제공 여부는 확인된 범위이며, 실제 서비스 상황과 다를 수 있습니다.
        </p>
      </div>
    </section>
  )
}

interface PopularSectionProps {
  service: OttService | null // 고른 서비스(전체면 null)
  services: OttService[] // 서비스 7곳(카드의 서비스 이름)
  period: PopularityPeriod // 고른 기간
}

/**
 * 인기 순위 구역: 기준 문구 → (데이터 부족 안내) → 순위 바둑판 → 기준일. 순위는 서비스 하나 또는 전체, 기간별로 서버가 계산해 준다.
 * 로딩·오류·빈 상태는 이 구역 안에서 그려, 탭을 바꿔도 기간 전환과 탭은 그대로 있다.
 */
function PopularSection({ service, services, period }: PopularSectionProps) {
  const ranking = usePopularityRanking(period, service?.ottServiceId ?? null, RANKING_LIMIT)
  const scopeText = service
    ? `${service.name}에서 볼 수 있다고 확인된 작품만`
    : '7곳 중 한 곳 이상에서 볼 수 있다고 확인된 작품만'

  return (
    <>
      <p className="mb-4 text-sm text-muted-foreground">
        {buildPopularityBasisText(
          period,
          scopeText,
          ranking.data?.notice,
          ranking.data?.daysCounted,
        )}
      </p>
      {service?.dataQuality === 'INSUFFICIENT' && (
        <InsufficientServiceNote
          service={service}
          text="제공 정보가 부족해 영화는 순위에서 빠지고, 볼 수 있다고 확인된 드라마만 보여요."
        />
      )}
      {ranking.isPending ? (
        <RailSkeleton heading="인기 순위" />
      ) : ranking.isError ? (
        <ErrorState
          title="인기 순위를 불러오지 못했습니다"
          error={ranking.error}
          onRetry={() => void ranking.refetch()}
        />
      ) : ranking.data.items.length === 0 ? (
        <EmptyState
          title={
            ranking.data.snapshotDate === null
              ? '아직 집계된 인기 순위가 없습니다'
              : '이 서비스에서 확인된 작품이 아직 없습니다'
          }
          description={
            ranking.data.snapshotDate === null
              ? '인기 순위는 하루에 한 번 집계해요. 집계가 시작되면 보여 드려요.'
              : undefined
          }
        />
      ) : (
        <div className="grid gap-4">
          <ol aria-label={`${service?.name ?? '전체'} 인기 순위`} className={GRID_CLASS_NAME}>
            {ranking.data.items.map((item) => (
              <li key={`${item.title.mediaType}-${item.title.tmdbId}`}>
                <HomeTitleCard
                  title={item.title}
                  services={services}
                  focusServiceId={service?.ottServiceId}
                  rank={item.rank}
                  rankLabel="인기"
                />
              </li>
            ))}
          </ol>
          <DataAsOf value={ranking.data.snapshotDate} />
        </div>
      )}
    </>
  )
}

/**
 * 찜 많은 작품 구역. 서비스를 가리지 않고 사용자 수 많은 순으로 보인다.
 * 최소 인원 미만이라 목록이 비면(서비스 초기) 홈과 달리 이 화면은 줄을 숨기지 않고 빈 상태로 이유를 안내한다.
 */
function WishlistedSection({ services }: { services: OttService[] }) {
  const wishlisted = useWishlistedTitles(RANKING_LIMIT)

  if (wishlisted.isPending) return <RailSkeleton heading="찜 많은 작품" />
  if (wishlisted.isError) {
    return (
      <ErrorState
        title="찜 많은 작품을 불러오지 못했습니다"
        error={wishlisted.error}
        onRetry={() => void wishlisted.refetch()}
      />
    )
  }

  return (
    <div className="grid gap-4">
      <p className="text-sm text-muted-foreground">
        OTT내비 사용자가 찜한 사람 수 기준(다 본 작품 제외) ·{' '}
        {formatCount(wishlisted.data.minUserCount)}명 이상 찜한 작품만 · 누가 찜했는지는 보이지
        않아요
      </p>
      {wishlisted.data.items.length === 0 ? (
        <EmptyState
          title="아직 찜이 모인 작품이 없습니다"
          description="찜한 사람이 일정 수 이상 모인 작품만 보여 드려요."
        />
      ) : (
        <ol aria-label="찜 많은 작품 순위" className={GRID_CLASS_NAME}>
          {wishlisted.data.items.map((item, index) => (
            <li key={`${item.title.mediaType}-${item.title.tmdbId}`}>
              <HomeTitleCard
                title={item.title}
                services={services}
                rank={index + 1}
                rankLabel="찜"
                meta={`찜 ${formatCount(item.wishlistUserCount)}명`}
              />
            </li>
          ))}
        </ol>
      )}
      <DataAsOf value={wishlisted.data.dataAsOf} />
    </div>
  )
}
