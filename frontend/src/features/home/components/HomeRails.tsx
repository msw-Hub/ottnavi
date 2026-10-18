import { useState } from 'react'
import type { UseQueryResult } from '@tanstack/react-query'
import type { OttService, PopularityPeriod, TitleSearchItem } from '@/api/generated/model'
import { DataInsufficientTag } from '@/components/common/DataInsufficientTag'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { HomeTitleCard } from '@/features/home/components/HomeTitleCard'
import { PeriodToggle } from '@/features/home/components/PeriodToggle'
import { RailSection, RailSkeleton, type RailStatus } from '@/features/home/components/RailSection'
import { ScrollRail, ScrollRailItem } from '@/features/home/components/ScrollRail'
import { ServiceTabs } from '@/features/home/components/ServiceTabs'
import {
  ALL_SERVICES_TAB,
  buildPopularityBasisText,
  buildServiceTabOptions,
  findServiceByTab,
  formatCount,
  formatMonthDay,
  toPeriodParam,
} from '@/features/home/homeDisplay'
import {
  useNewlyAvailableTitles,
  usePopularityRanking,
  useWishlistedTitles,
} from '@/features/home/hooks/useHomeLists'
import { withJosa } from '@/lib/josa'

// 인기 순위 줄에 보일 개수(TOP 10)
const POPULAR_RANKING_LIMIT = 10
// 찜이 많은 작품 줄에 보일 개수
const WISHLISTED_LIMIT = 10
// 새로 볼 수 있게 된 작품 줄에 보일 개수
const NEW_ARRIVALS_LIMIT = 20

// 데이터 부족 서비스(쿠팡플레이)를 고른 탭 위에 한 번 붙이는 안내(랭킹 화면도 쓴다)
export function InsufficientServiceNote({ service, text }: { service: OttService; text: string }) {
  return (
    <p className="mb-3 flex flex-wrap items-center gap-x-1.5 gap-y-1 text-sm text-muted-foreground">
      <DataInsufficientTag />
      <span>
        {withJosa(service.name, '은/는')} {text}
      </span>
    </p>
  )
}

// 서비스 목록(탭 이름·카드의 서비스 이름에 필요) 조회 결과. 목록 줄마다 같은 결과를 받아 쓴다(요청은 한 번만 나간다)
type ServicesQuery = UseQueryResult<OttService[]>

/**
 * 조회 결과들을 줄 하나의 상태로 합친다. 하나라도 실패하면 오류, 하나라도 받는 중이면 불러오는 중, 아니면 ready다.
 * (빈 목록은 조회 성공이라 여기서 판단하지 않고 각 줄이 직접 'empty'를 정한다)
 */
function resolveRailStatus(...queries: { isPending: boolean; isError: boolean }[]): RailStatus {
  if (queries.some((query) => query.isError)) return 'error'
  if (queries.some((query) => query.isPending)) return 'loading'
  return 'ready'
}

interface PopularRailProps {
  servicesQuery: ServicesQuery // 서비스 7곳(탭과 카드의 서비스 이름)
}

/**
 * "서비스별 인기 순위" 줄. 서비스 탭을 고르면 그 서비스에서 볼 수 있는 작품을 인기도 순으로 큰 순위 숫자와 함께 보인다.
 *
 * 기준을 제목 아래에 밝힌다: OTT가 발표하는 시청 순위가 아니라 TMDB 인기도(popularity)로 정렬한 것이다(서버가 notice로 문구를 준다).
 * 이걸 숨기면 "넷플릭스 TOP 10"처럼 오해할 수 있다. 쿠팡플레이는 영화 정보가 없어(구조적 공백) 드라마만 남는다는 것을 탭 안에 알린다.
 *
 * 상태를 두 겹으로 나눈 이유: 서비스 목록이 없으면 탭 자체를 못 그리므로 줄 전체(RailSection)가 로딩·오류를 맡고,
 * 순위 조회의 로딩·오류·빈 상태는 탭 안쪽에서 그린다. 그래야 탭을 바꿔 새로 받는 동안에도 탭과 기간 전환이 사라지지 않는다.
 */
export function PopularRail({ servicesQuery }: PopularRailProps) {
  const [tab, setTab] = useState(ALL_SERVICES_TAB) // 고른 탭(화면 안에서만 쓰는 값이라 useState. 랭킹 화면은 주소에 둔다)
  const [period, setPeriod] = useState<PopularityPeriod>('TODAY') // 고른 기간(기본 오늘)
  const services = servicesQuery.data ?? []
  const service = findServiceByTab(services, tab)
  const ranking = usePopularityRanking(period, service?.ottServiceId ?? null, POPULAR_RANKING_LIMIT)
  const scopeText = service
    ? `${service.name}에서 볼 수 있다고 확인된 작품만`
    : '7곳 중 한 곳 이상에서 볼 수 있다고 확인된 작품만'

  return (
    <RailSection
      heading="서비스별 인기 순위"
      description={buildPopularityBasisText(
        period,
        scopeText,
        ranking.data?.notice,
        ranking.data?.daysCounted,
      )}
      moreTo={`/ranking?service=${tab}&period=${toPeriodParam(period)}`}
      dataAsOf={ranking.data?.snapshotDate ?? null}
      status={resolveRailStatus(servicesQuery)}
      error={servicesQuery.error}
      onRetry={() => void servicesQuery.refetch()}
    >
      {/* 기간 전환은 서비스 탭보다 먼저 둔다: "언제 기준" → "어느 서비스" 순으로 좁혀 가는 순서다 */}
      <PeriodToggle value={period} onChange={setPeriod} />
      <ServiceTabs
        label="인기 순위 서비스 선택"
        value={tab}
        onValueChange={setTab}
        options={buildServiceTabOptions(services, true)}
      >
        {service?.dataQuality === 'INSUFFICIENT' && (
          <InsufficientServiceNote
            service={service}
            text="제공 정보가 부족해 영화는 순위에서 빠지고, 볼 수 있다고 확인된 드라마만 보여요."
          />
        )}
        {ranking.isPending ? (
          <RailSkeleton heading="서비스별 인기 순위" />
        ) : ranking.isError ? (
          <ErrorState
            title="서비스별 인기 순위를 불러오지 못했습니다"
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
          // key={tab}: 탭을 바꾸면 줄을 새로 만들어 스크롤 위치를 처음으로 되돌리고 화살표 상태도 다시 잰다
          // key에 기간도 넣어 기간을 바꿔도 처음 위치부터 다시 보이게 한다
          <ScrollRail
            key={`${tab}-${period}`}
            label={`${service?.name ?? '전체'} 인기 순위`}
            isOrdered
          >
            {ranking.data.items.map((item) => (
              <ScrollRailItem key={`${item.title.mediaType}-${item.title.tmdbId}`} isWide>
                <HomeTitleCard
                  title={item.title}
                  services={services}
                  focusServiceId={service?.ottServiceId}
                  rank={item.rank}
                  rankLabel="인기"
                />
              </ScrollRailItem>
            ))}
          </ScrollRail>
        )}
      </ServiceTabs>
    </RailSection>
  )
}

interface WishlistedRailProps {
  servicesQuery: ServicesQuery
}

/**
 * "찜이 많은 작품" 줄. OTT내비 사용자가 많이 찜한 작품을 사용자 수와 함께 보인다(누가 찜했는지는 보이지 않는다).
 * 서비스를 가리지 않는 목록이라 카드에는 "볼 수 있는 곳" 칩을 보인다.
 *
 * 목록이 비면 줄 전체를 숨긴다(Task 107 결정): 서비스 초기에는 최소 인원(minUserCount)을 채운 작품이 거의 없어
 * "아직 없습니다" 빈 상자를 홈에 오래 두는 것보다 줄을 보이지 않는 편이 낫다. 오류일 때는 숨기지 않고 안내와 "다시 시도"를 보인다.
 */
export function WishlistedRail({ servicesQuery }: WishlistedRailProps) {
  const wishlisted = useWishlistedTitles(WISHLISTED_LIMIT)
  const services = servicesQuery.data ?? []
  const status = resolveRailStatus(servicesQuery, wishlisted)

  // 1. 조회가 끝났고 항목이 없으면 줄을 그리지 않는다
  if (status === 'ready' && (wishlisted.data?.items.length ?? 0) === 0) return null

  return (
    <RailSection
      heading="찜이 많은 작품"
      description={`OTT내비 사용자가 찜한 사람 수 기준(다 본 작품 제외)${
        wishlisted.data ? ` · ${formatCount(wishlisted.data.minUserCount)}명 이상 찜한 작품만` : ''
      }`}
      moreTo="/ranking?sort=wishlist"
      dataAsOf={wishlisted.data?.dataAsOf ?? null}
      status={status}
      error={wishlisted.error ?? servicesQuery.error}
      onRetry={() => {
        if (wishlisted.isError) void wishlisted.refetch()
        if (servicesQuery.isError) void servicesQuery.refetch()
      }}
    >
      <ScrollRail label="찜이 많은 작품" isOrdered>
        {(wishlisted.data?.items ?? []).map((item) => (
          <ScrollRailItem key={`${item.title.mediaType}-${item.title.tmdbId}`}>
            <HomeTitleCard
              title={item.title}
              services={services}
              meta={`찜 ${formatCount(item.wishlistUserCount)}명`}
            />
          </ScrollRailItem>
        ))}
      </ScrollRail>
    </RailSection>
  )
}

interface NewArrivalsRailProps {
  servicesQuery: ServicesQuery
}

/**
 * "최근 새로 볼 수 있게 된 작품" 줄. 서비스 탭별로 보이고, 날짜 옆 글자가 이유를 알려 준다.
 * - 입고(ARRIVAL): 우리가 "있음"으로 바뀐 것을 확인한 날 → "10월 15일 확인". 실제 서비스에 들어온 날이 아니라 하루 단위 수집에서 처음 확인한 날이라 며칠 늦을 수 있다.
 * - 공개(RELEASE): 첫 수집부터 있음이지만 공개일이 최근인 작품 → "10월 3일 공개".
 */
export function NewArrivalsRail({ servicesQuery }: NewArrivalsRailProps) {
  const [tab, setTab] = useState(ALL_SERVICES_TAB)
  const services = servicesQuery.data ?? []
  const service = findServiceByTab(services, tab)
  const arrivals = useNewlyAvailableTitles(service?.ottServiceId ?? null, NEW_ARRIVALS_LIMIT)

  return (
    <RailSection
      heading="최근 새로 볼 수 있게 된 작품"
      description="최근 '볼 수 있음'으로 새로 확인됐거나 막 공개된 작품"
      moreTo="/explore?view=new"
      dataAsOf={arrivals.data?.dataAsOf ?? null}
      status={resolveRailStatus(servicesQuery)}
      error={servicesQuery.error}
      onRetry={() => void servicesQuery.refetch()}
    >
      <ServiceTabs
        label="새로 볼 수 있게 된 작품 서비스 선택"
        value={tab}
        onValueChange={setTab}
        options={buildServiceTabOptions(services, true)}
      >
        {service?.dataQuality === 'INSUFFICIENT' && (
          <InsufficientServiceNote
            service={service}
            text="제공 정보가 부족해 새로 들어온 작품을 놓칠 수 있어요."
          />
        )}
        {arrivals.isPending ? (
          <RailSkeleton heading="최근 새로 볼 수 있게 된 작품" />
        ) : arrivals.isError ? (
          <ErrorState
            title="최근 새로 볼 수 있게 된 작품을 불러오지 못했습니다"
            error={arrivals.error}
            onRetry={() => void arrivals.refetch()}
          />
        ) : arrivals.data.items.length === 0 ? (
          <EmptyState title="최근 새로 확인된 작품이 없습니다" />
        ) : (
          <ScrollRail key={tab} label={`${service?.name ?? '전체'} 새로 볼 수 있게 된 작품`}>
            {arrivals.data.items.map((arrival) => (
              <ScrollRailItem
                key={`${arrival.title.mediaType}-${arrival.title.tmdbId}-${arrival.ottServiceId}`}
              >
                <HomeTitleCard
                  title={arrival.title}
                  services={services}
                  focusServiceId={arrival.ottServiceId}
                  meta={`${formatMonthDay(arrival.availableSince)} ${
                    arrival.arrivalType === 'ARRIVAL' ? '확인' : '공개'
                  }`}
                />
              </ScrollRailItem>
            ))}
          </ScrollRail>
        )}
      </ServiceTabs>
    </RailSection>
  )
}

interface ExclusivesRailProps {
  services: OttService[] // 서비스 7곳
  dataAsOf: string | null // 데이터 기준일
  status?: RailStatus // 불러오기 상태
  getExclusives: (serviceId: number) => TitleSearchItem[] // 서비스별 독점 작품(확인된 범위)
}

/**
 * "서비스 독점 작품" 줄. 확인된 범위에서 7곳 중 그 서비스에서만 볼 수 있는 작품이다(PRD FR-04 정의).
 *
 * 현재 홈(HomeScreen)은 이 줄을 그리지 않는다: 독점 목록 API(Task 078)가 아직 없어 보여 줄 실제 데이터가 없기 때문이다.
 * Task 078이 끝나면 getExclusives를 그 API 훅으로 바꿔 HomeScreen에 다시 넣는다(컴포넌트 모양은 그때 쓰려고 남겨 둔다).
 *
 * "전체" 탭이 없는 이유: 독점은 서비스마다의 개념이라 모아 보면 뜻이 흐려진다.
 * 쿠팡플레이는 데이터가 부족해 목록 대신 안내만 보인다(PRD SCR-03). 더보기는 독점 목록 화면(SCR-03, 2단계)으로 간다.
 */
export function ExclusivesRail({ services, dataAsOf, status, getExclusives }: ExclusivesRailProps) {
  const [tab, setTab] = useState<string>(services[0]?.code ?? '') // 첫 서비스(넷플릭스)부터. 독점에는 "전체" 탭이 없다
  const service = findServiceByTab(services, tab)
  const exclusives = service ? getExclusives(service.ottServiceId) : []

  return (
    <RailSection
      heading="서비스 독점 작품"
      description="확인된 범위에서 7곳 중 이 서비스에서만 볼 수 있는 작품"
      moreTo="/exclusives"
      dataAsOf={dataAsOf}
      status={status}
      emptyTitle="독점 작품 정보를 준비 중입니다"
    >
      <ServiceTabs
        label="독점 작품 서비스 선택"
        value={tab}
        onValueChange={setTab}
        options={buildServiceTabOptions(services, false)}
      >
        {service?.dataQuality === 'INSUFFICIENT' ? (
          <EmptyState
            title={`${withJosa(service.name, '은/는')} 독점 여부를 판단하지 않아요`}
            description="제공 정보가 부족해 다른 서비스에 없는지 확인할 수 없기 때문이에요. 작품마다의 제공 상태는 상세 화면에서 볼 수 있어요."
          />
        ) : exclusives.length === 0 ? (
          <EmptyState title="이 서비스의 독점으로 확인된 작품이 없습니다" />
        ) : (
          <ScrollRail key={tab} label={`${service?.name ?? ''} 독점 작품`}>
            {exclusives.map((title) => (
              <ScrollRailItem key={`${title.mediaType}-${title.tmdbId}`}>
                <HomeTitleCard
                  title={title}
                  services={services}
                  focusServiceId={service?.ottServiceId}
                />
              </ScrollRailItem>
            ))}
          </ScrollRail>
        )}
      </ServiceTabs>
    </RailSection>
  )
}
