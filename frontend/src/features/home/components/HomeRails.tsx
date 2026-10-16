import { useState } from 'react'
import { DataInsufficientTag } from '@/components/common/DataInsufficientTag'
import { EmptyState } from '@/components/common/EmptyState'
import { HomeTitleCard } from '@/features/home/components/HomeTitleCard'
import { PeriodToggle } from '@/features/home/components/PeriodToggle'
import { RailSection, type RailStatus } from '@/features/home/components/RailSection'
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
import { withJosa } from '@/lib/josa'
import type {
  HomeNewArrival,
  HomePopularityPeriod,
  HomeService,
  HomeTitle,
  HomeWishlistedTitle,
} from '@/features/home/mockTypes'

// 데이터 부족 서비스(쿠팡플레이)를 고른 탭 위에 한 번 붙이는 안내(랭킹 화면도 쓴다)
export function InsufficientServiceNote({ service, text }: { service: HomeService; text: string }) {
  return (
    <p className="mb-3 flex flex-wrap items-center gap-x-1.5 gap-y-1 text-sm text-muted-foreground">
      <DataInsufficientTag />
      <span>
        {withJosa(service.name, '은/는')} {text}
      </span>
    </p>
  )
}

interface CommonRailProps {
  services: HomeService[] // 서비스 7곳(탭과 카드의 서비스 이름)
  dataAsOf: string // 데이터 기준일
  status?: RailStatus // 불러오기 상태(1차안 기본 ready)
}

interface PopularRailProps extends CommonRailProps {
  getRanking: (serviceId: number | null, period: HomePopularityPeriod) => HomeTitle[] // 서비스(전체면 null)·기간별 인기 순위 상위 목록
}

/**
 * "서비스별 인기 순위" 줄. 서비스 탭을 고르면 그 서비스에서 볼 수 있는 작품을 인기도 순으로 큰 순위 숫자와 함께 보인다.
 *
 * 기준을 제목 아래에 밝힌다: OTT가 발표하는 시청 순위가 아니라 TMDB 인기도(popularity)로 정렬한 것이다.
 * 이걸 숨기면 "넷플릭스 TOP 10"처럼 오해할 수 있다. 쿠팡플레이는 영화 정보가 없어(구조적 공백) 드라마만 남는다는 것을 탭 안에 알린다.
 */
export function PopularRail({ services, dataAsOf, status, getRanking }: PopularRailProps) {
  const [tab, setTab] = useState(ALL_SERVICES_TAB) // 고른 탭(화면 안에서만 쓰는 값이라 useState. 랭킹 화면은 주소에 둔다)
  const [period, setPeriod] = useState<HomePopularityPeriod>('TODAY') // 고른 기간(기본 오늘)
  const service = findServiceByTab(services, tab)
  const ranking = getRanking(service?.ottServiceId ?? null, period)
  const scopeText = service
    ? `${service.name}에서 볼 수 있다고 확인된 작품만`
    : '7곳 중 한 곳 이상에서 볼 수 있다고 확인된 작품만'

  return (
    <RailSection
      heading="서비스별 인기 순위"
      // 이전: description="TMDB 인기도 기준 · 그 서비스에서 볼 수 있다고 확인된 작품만"
      description={buildPopularityBasisText(period, scopeText)}
      moreTo={`/ranking?service=${tab}&period=${toPeriodParam(period)}`}
      dataAsOf={dataAsOf}
      status={status}
      emptyTitle="순위를 낼 작품이 없습니다"
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
        {ranking.length === 0 ? (
          <EmptyState title="이 서비스에서 확인된 작품이 아직 없습니다" />
        ) : (
          // key={tab}: 탭을 바꾸면 줄을 새로 만들어 스크롤 위치를 처음으로 되돌리고 화살표 상태도 다시 잰다
          // key에 기간도 넣어 기간을 바꿔도 처음 위치부터 다시 보이게 한다
          <ScrollRail
            key={`${tab}-${period}`}
            label={`${service?.name ?? '전체'} 인기 순위`}
            isOrdered
          >
            {ranking.map((title, index) => (
              <ScrollRailItem key={`${title.mediaType}-${title.tmdbId}`} isWide>
                <HomeTitleCard
                  title={title}
                  services={services}
                  focusServiceId={service?.ottServiceId}
                  rank={index + 1}
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

interface WishlistedRailProps extends CommonRailProps {
  items: HomeWishlistedTitle[] // 찜이 많은 작품(많은 순)
}

/**
 * "찜이 많은 작품" 줄. OTT내비 사용자가 많이 찜한 작품을 사용자 수와 함께 보인다(누가 찜했는지는 보이지 않는다).
 * 서비스를 가리지 않는 목록이라 카드에는 "볼 수 있는 곳" 칩을 보인다.
 */
export function WishlistedRail({ services, dataAsOf, status, items }: WishlistedRailProps) {
  return (
    <RailSection
      heading="찜이 많은 작품"
      description="OTT내비 사용자가 찜한 사람 수 기준(다 본 작품 제외)"
      moreTo="/ranking?sort=wishlist"
      dataAsOf={dataAsOf}
      status={status}
      emptyTitle="아직 찜이 모인 작품이 없습니다"
      emptyDescription="찜한 사람이 일정 수 이상 모인 작품만 보여 드려요."
    >
      <ScrollRail label="찜이 많은 작품" isOrdered>
        {items.map((item) => (
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

interface NewArrivalsRailProps extends CommonRailProps {
  getNewArrivals: (serviceId: number | null) => HomeNewArrival[] // 서비스(전체면 null)별 새로 볼 수 있게 된 작품(최근 순)
}

/**
 * "최근 새로 볼 수 있게 된 작품" 줄. 서비스 탭별로, 우리가 "있음"으로 처음 확인한 날과 함께 보인다.
 * 날짜를 "확인"이라고 쓰는 이유: 실제로 서비스에 들어온 날이 아니라 하루 단위 수집에서 처음 확인한 날이라, 며칠 늦을 수 있다.
 */
export function NewArrivalsRail({
  services,
  dataAsOf,
  status,
  getNewArrivals,
}: NewArrivalsRailProps) {
  const [tab, setTab] = useState(ALL_SERVICES_TAB)
  const service = findServiceByTab(services, tab)
  const arrivals = getNewArrivals(service?.ottServiceId ?? null)

  return (
    <RailSection
      heading="최근 새로 볼 수 있게 된 작품"
      description="최근 2주 안에 '볼 수 있음'으로 새로 확인된 작품"
      moreTo="/explore?view=new"
      dataAsOf={dataAsOf}
      status={status}
      emptyTitle="최근 새로 확인된 작품이 없습니다"
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
        {arrivals.length === 0 ? (
          <EmptyState title="이 서비스에서 최근 새로 확인된 작품이 없습니다" />
        ) : (
          <ScrollRail key={tab} label={`${service?.name ?? '전체'} 새로 볼 수 있게 된 작품`}>
            {arrivals.map((arrival) => (
              <ScrollRailItem
                key={`${arrival.title.mediaType}-${arrival.title.tmdbId}-${arrival.ottServiceId}`}
              >
                <HomeTitleCard
                  title={arrival.title}
                  services={services}
                  focusServiceId={arrival.ottServiceId}
                  meta={`${formatMonthDay(arrival.availableSince)} 확인`}
                />
              </ScrollRailItem>
            ))}
          </ScrollRail>
        )}
      </ServiceTabs>
    </RailSection>
  )
}

interface ExclusivesRailProps extends CommonRailProps {
  getExclusives: (serviceId: number) => HomeTitle[] // 서비스별 독점 작품(확인된 범위)
}

/**
 * "서비스 독점 작품" 줄. 확인된 범위에서 7곳 중 그 서비스에서만 볼 수 있는 작품이다(PRD FR-04 정의).
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
