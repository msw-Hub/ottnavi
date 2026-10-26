import { useState } from 'react'
import { ChevronDownIcon, CircleCheckIcon, GiftIcon, TriangleAlertIcon } from 'lucide-react'
import type { OttService, ServicePrice } from '@/api/generated/model'
import { DataInsufficientTag } from '@/components/common/DataInsufficientTag'
import { SUBSCRIPTION_STATUS_LABELS } from '@/components/common/displayTypes'
import { PricingRow } from '@/features/pricing/components/PricingRow'
import { getTodayDateString } from '@/lib/dateUtils'
import { isVerifiedStale } from '@/lib/priceFreshness'

// 서비스 하나에 속한 단품 요금제 모음
interface PriceGroup {
  key: string
  name: string // 서비스 이름
  isInsufficient: boolean // 데이터 부족 서비스인지(쿠팡플레이)
  subscriptionStatus: ServicePrice['subscriptionStatus']
  plans: ServicePrice[] // 계산에 쓰는 요금제가 앞에 오도록 정렬한 목록
}

// 1. 서비스 ID별로 단품을 묶고, 번들은 따로 모은다. 묶음 안에서는 계산 후보를 앞에 둔다
function buildGroups(
  prices: ServicePrice[],
  services: OttService[],
): { groups: PriceGroup[]; bundles: ServicePrice[] } {
  const byService = new Map(services.map((s) => [s.ottServiceId, s]))
  const groups = new Map<string, PriceGroup>()
  const bundles: ServicePrice[] = []
  for (const price of prices) {
    if (price.productType === 'BUNDLE' || price.ottServiceId === null) {
      bundles.push(price)
      continue
    }
    const info = byService.get(price.ottServiceId)
    const key = `s${price.ottServiceId}`
    const group = groups.get(key) ?? {
      key,
      name: info?.name ?? price.productName,
      isInsufficient: info?.dataQuality === 'INSUFFICIENT',
      subscriptionStatus: price.subscriptionStatus,
      plans: [],
    }
    group.plans.push(price)
    groups.set(key, group)
  }
  // 번들에 데이터 부족 서비스가 들어 있어도 태그는 붙이지 않는다(번들 제목에 구성 서비스가 보인다)
  const result = [...groups.values()]
  // 정렬은 안정 정렬이라 같은 쪽끼리는 계약이 준 순서가 유지된다
  for (const group of result) {
    group.plans.sort((a, b) => Number(b.isCalcCandidate) - Number(a.isCalcCandidate))
  }
  return { groups: result, bundles }
}

// 번들 카드의 굵은 제목: 구성 서비스 이름을 "+"로 잇는다(예: "티빙 + 웨이브")
function bundleTitle(price: ServicePrice, services: OttService[]): string {
  const names = price.ottServiceIds.map(
    (id) => services.find((s) => s.ottServiceId === id)?.name ?? `서비스 ${id}`,
  )
  return names.join(' + ')
}

// 구독 상태(구독 중·무료 이용만). 서비스 이름 옆에 한 번만 둔다. 아이콘 + 글자
function SubscriptionMark({ status }: { status: ServicePrice['subscriptionStatus'] }) {
  if (status !== 'SUBSCRIBED' && status !== 'FREE') return null
  const Icon = status === 'SUBSCRIBED' ? CircleCheckIcon : GiftIcon
  return (
    <span className="inline-flex items-center gap-1 text-sm font-semibold text-info">
      <Icon aria-hidden="true" className="size-4" />
      {SUBSCRIPTION_STATUS_LABELS[status]}
    </span>
  )
}

interface GroupCardProps {
  group: PriceGroup
  services: OttService[]
  isLocked: boolean
}

/**
 * 서비스 한 곳의 요금 카드다. 계산에 쓰는 요금제를 위에 두고, 안 쓰는 요금제는 "계산에 안 쓰는 요금제 N개"로 접어 둔다.
 * 번들은 이 카드가 아니라 아래 번들 구역(BundleCard)이 맡는다.
 * 요금 확인일은 줄마다 보이지 않는다. 대신 위에 보이는 요금제가 90일 넘게 확인되지 않았으면(isVerifiedStale) 카드에 경고를 한 번 띄운다.
 * 계산은 막지 않는다. 주의는 색만으로 알리지 않고 아이콘과 글자를 함께 쓴다.
 */
function PricingGroupCard({ group, isLocked }: GroupCardProps) {
  const [primary, ...hidden] = group.plans
  const shown = [primary]
  const today = getTodayDateString()
  const isStale = shown.some((p) => isVerifiedStale(p.lastVerifiedAt, today))
  const [isOpen, setIsOpen] = useState(false) // 안 쓰는 요금제를 펼쳤는지
  const panelId = `hidden-plans-${group.key}`
  // 접기 버튼: 서비스 이름 줄 끝에 둔다(길면 이름 줄이 flex-wrap으로 내려간다)(카드 높이를 늘리지 않으려고 별도 줄을 만들지 않았다).
  // 눈에 보이는 크기는 작지만 ::after로 위아래 눌림 영역을 넓혀 터치하기 쉽게 한다
  const toggle = hidden.length > 0 && (
    <button
      type="button"
      aria-expanded={isOpen}
      aria-controls={panelId}
      onClick={() => setIsOpen((v) => !v)}
      className="relative inline-flex items-center gap-0.5 rounded-sm text-sm font-normal text-muted-foreground after:absolute after:-inset-x-1 after:-inset-y-3 hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
    >
      계산에 안 쓰는 요금제 {hidden.length}개
      <ChevronDownIcon
        aria-hidden="true"
        className={`size-3.5 transition-transform ${isOpen ? 'rotate-180' : ''}`}
      />
    </button>
  )
  return (
    <li className="grid gap-2 rounded-xl border border-border bg-card px-4 py-3">
      {shown.map((price) => (
        <PricingRow
          key={price.productId}
          price={price}
          title={price.productName}
          isLocked={isLocked}
          isPrimary
          head={
            <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-base font-bold">
              {group.name}
              {group.isInsufficient && <DataInsufficientTag />}
              <SubscriptionMark status={group.subscriptionStatus} />
              {toggle}
            </p>
          }
        />
      ))}
      {isStale && (
        <p className="flex items-start gap-1 text-sm font-semibold text-foreground">
          <TriangleAlertIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          요금이 달라졌을 수 있어요
        </p>
      )}
      {hidden.length > 0 && isOpen && (
        <div id={panelId} className="grid gap-3 border-t border-border pt-3">
          {hidden.map((price) => (
            <PricingRow
              key={price.productId}
              price={price}
              title={price.productName}
              isLocked={isLocked}
              isPrimary={false}
            />
          ))}
        </div>
      )}
    </li>
  )
}

interface BundleCardProps {
  price: ServicePrice
  services: OttService[]
  isLocked: boolean
}

/**
 * 번들 한 개의 카드다. 단품 카드와 같은 구조(제목 줄 / 금액 / 입력)이고, 굵은 제목은 구성 서비스 이름("티빙 + 웨이브"),
 * 그 아래 작은 글씨가 상품명과 "번들"·"저가형" 표시다. 구독 상태·데이터 부족 태그는 번들에 붙이지 않는다.
 */
function BundleCard({ price, services, isLocked }: BundleCardProps) {
  const isStale = isVerifiedStale(price.lastVerifiedAt, getTodayDateString())
  return (
    <li className="grid gap-2 rounded-xl border border-border bg-card px-4 py-3">
      <PricingRow
        price={price}
        title={price.productName}
        isLocked={isLocked}
        isPrimary
        head={<p className="text-base font-bold">{bundleTitle(price, services)}</p>}
      />
      {isStale && (
        <p className="flex items-start gap-1 text-sm font-semibold text-foreground">
          <TriangleAlertIcon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          요금이 달라졌을 수 있어요
        </p>
      )}
    </li>
  )
}

// 번들 구역: 계산에 쓰는 번들은 카드로 보이고, 안 쓰는 번들은 구역 맨 아래 한 줄 버튼으로 접어 둔다
function BundleSection({
  bundles,
  services,
  isLocked,
}: Omit<PricingListProps, 'prices'> & { bundles: ServicePrice[] }) {
  const [isOpen, setIsOpen] = useState(false) // 안 쓰는 번들을 펼쳤는지
  const shown = bundles.filter((p) => p.isCalcCandidate)
  const hidden = bundles.filter((p) => !p.isCalcCandidate)
  const panelId = 'hidden-bundles'
  return (
    <section aria-labelledby="bundle-heading" className="mt-4 grid gap-3">
      <h3 id="bundle-heading" className="text-base font-semibold">
        번들 상품
      </h3>
      {shown.length > 0 && (
        <ul className="grid gap-3">
          {shown.map((price) => (
            <BundleCard
              key={price.productId}
              price={price}
              services={services}
              isLocked={isLocked}
            />
          ))}
        </ul>
      )}
      {hidden.length > 0 && (
        <button
          type="button"
          aria-expanded={isOpen}
          aria-controls={panelId}
          onClick={() => setIsOpen((v) => !v)}
          className="relative ml-4 inline-flex w-fit items-center gap-0.5 rounded-sm text-sm text-muted-foreground after:absolute after:-inset-x-1 after:-inset-y-3 hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        >
          계산에 안 쓰는 번들 {hidden.length}개
          <ChevronDownIcon
            aria-hidden="true"
            className={`size-3.5 transition-transform ${isOpen ? 'rotate-180' : ''}`}
          />
        </button>
      )}
      {hidden.length > 0 && isOpen && (
        <ul id={panelId} className="grid gap-3">
          {hidden.map((price) => (
            <BundleCard
              key={price.productId}
              price={price}
              services={services}
              isLocked={isLocked}
            />
          ))}
        </ul>
      )}
    </section>
  )
}

interface PricingListProps {
  prices: ServicePrice[] // 요금 전체(계약이 주는 순서: 단품 → 번들)
  services: OttService[] // 서비스 이름·데이터 충분도
  isLocked: boolean
}

/**
 * 서비스별 요금 목록이다. 목록 위 한 줄 안내에 줄마다 반복하던 문장("비워서 적용하면…", 번들 사용 안내)을 모았다.
 */
export function PricingList({ prices, services, isLocked }: PricingListProps) {
  const { groups, bundles } = buildGroups(prices, services)
  const hasBundle = bundles.length > 0
  return (
    <section aria-label="서비스별 요금" className="grid gap-3">
      <p className="text-sm break-keep text-muted-foreground">
        금액을 비워서 적용하면 기본 요금을 써요.
        {hasBundle &&
          ' 번들을 쓰고 있다면 설정에서 포함된 서비스를 각각 "구독 중"으로 입력해 주세요.'}
      </p>
      <ul className="grid gap-3">
        {groups.map((group) => (
          <PricingGroupCard key={group.key} group={group} services={services} isLocked={isLocked} />
        ))}
      </ul>
      {hasBundle && <BundleSection bundles={bundles} services={services} isLocked={isLocked} />}
    </section>
  )
}
