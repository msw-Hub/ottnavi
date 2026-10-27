import { useState, type ReactNode } from 'react'
import { CalculatorIcon, InfoIcon, SearchIcon } from 'lucide-react'
import { Link, useLocation } from 'react-router'
import { toast } from 'sonner'
import type { OttService, WishlistItemList } from '@/api/generated/model'
import { isApiError } from '@/api/http'
import { DataAsOf } from '@/components/common/DataAsOf'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { PageSection } from '@/components/common/PageSection'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import {
  WISHLIST_PRIORITY_DESCRIPTIONS,
  WISHLIST_PRIORITY_LABELS,
  WISHLIST_PRIORITY_OPTIONS,
  type WishlistPriority,
} from '@/components/common/displayTypes'
import { PriorityBadge, PriorityIcon } from '@/components/common/WishlistPriority'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { WishlistRow, type WishlistRowCardProps } from '@/features/wishlist/components/WishlistRow'
import {
  useRemoveWishlistItem,
  useUpdateWishlistItem,
  useWishlist,
} from '@/features/wishlist/hooks/useWishlist'
import { useOttServices } from '@/hooks/useOttServices'
import { formatSeasonName } from '@/lib/format'
import { buildServiceStatusEntries } from '@/lib/serviceStatus'

// 우선순위 묶음이 비었을 때의 안내. 꼭 볼 작품이 비면 무엇을 하면 좋은지까지 알린다(계산이 가장 먼저 챙기는 단계라서)
const EMPTY_GROUP_MESSAGES: Record<WishlistPriority, string> = {
  MUST: "아직 없어요. 꼭 끝까지 보고 싶은 작품은 카드에서 '꼭 볼 작품'을 눌러 주세요.",
  WANT: '아직 없어요.',
  MAYBE: '아직 없어요.',
}

// 변경 요청이 실패했을 때 토스트로 알린다. 서버 문장(userMessage)이 있으면 그것을, 없으면 기본 문장을 쓴다
function notifyFailure(error: unknown) {
  toast.error(
    isApiError(error) ? error.userMessage : '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.',
  )
}

/**
 * SCR-08 찜 목록 화면의 본문이다. pages/WishlistPage는 이 컴포넌트를 연결만 한다(pages는 얇게, frontend.md).
 *
 * 찜 목록과 서비스 목록을 함께 불러와 로딩·오류·빈 상태·목록 중 하나를 그린다.
 * "계산하기"는 요금 확인(SCR-09)으로 가는 링크다. 모바일에서는 화면 아래에 고정해 목록이 길어도 항상 손이 닿게 하고,
 * 데스크톱에서는 목록 위 오른쪽에 둔다.
 */
export function WishlistScreen() {
  const wishlist = useWishlist()
  const services = useOttServices()

  let content: ReactNode
  if (wishlist.isPending || services.isPending) {
    content = <LoadingState message="찜 목록을 불러오고 있어요" />
  } else if (wishlist.isError) {
    content = <ErrorState error={wishlist.error} onRetry={() => wishlist.refetch()} />
  } else if (services.isError) {
    content = <ErrorState error={services.error} onRetry={() => services.refetch()} />
  } else {
    content = <WishlistContent list={wishlist.data} services={services.data} />
  }

  return (
    <section className="flex flex-col gap-5">
      <h1 className="text-2xl font-semibold">찜 목록</h1>
      {content}
    </section>
  )
}

interface CardListProps {
  items: WishlistItemList['items'] // 한 구역(계산에 쓸 작품 또는 다 봤음)의 항목
  getCardProps: (item: WishlistItemList['items'][number]) => WishlistRowCardProps // 항목 하나의 카드 props
  expandedId: number | null // 목록 전체에서 펼쳐진 항목의 id(두 구역을 통틀어 하나)
  onToggleExpanded: (wishlistItemId: number) => void // 카드의 "자세히 보기/접기"를 눌렀을 때
}

/**
 * 카드를 2개씩 묶어 "행"으로 쌓는다. 데스크톱(lg 이상)에서는 한 행이 2열이고, 좁은 폭에서는 한 열이다.
 * 2개씩 묶는 이유: 펼침 패널을 "그 카드가 속한 행 아래 전체 폭"에 넣으려면 어느 카드끼리 같은 행인지 알아야 한다.
 * 단순히 전체를 하나의 2열 grid로 두면 한쪽 카드의 패널이 다음 카드를 밀어 열이 어긋난다.
 * 묶는 일은 순수 계산이라 상태 없이 렌더링 중에 한다.
 */
function CardList({ items, getCardProps, expandedId, onToggleExpanded }: CardListProps) {
  const rows: WishlistItemList['items'][] = []
  for (let index = 0; index < items.length; index += 2) {
    rows.push(items.slice(index, index + 2))
  }
  return (
    <div className="flex flex-col gap-3">
      {rows.map((rowItems) => (
        <WishlistRow
          key={rowItems.map((item) => item.wishlistItemId).join('-')}
          items={rowItems}
          getCardProps={getCardProps}
          expandedId={expandedId}
          onToggleExpanded={onToggleExpanded}
        />
      ))}
    </div>
  )
}

interface WishlistContentProps {
  list: WishlistItemList // 불러온 찜 목록(최근에 찜한 순)과 데이터 기준일
  services: OttService[] // 서비스 목록 API의 7개 서비스
}

// 데이터를 불러온 뒤의 화면: 빈 상태 또는 "계산에 쓸 작품" + "다 봤음" 두 구역
function WishlistContent({ list, services }: WishlistContentProps) {
  const location = useLocation()
  const update = useUpdateWishlistItem()
  const remove = useRemoveWishlistItem()
  // 펼쳐진 항목의 id(없으면 null). 목록 전체에서 한 번에 하나만 펼치므로 행이 아니라 여기서 든다.
  // 행마다 들고 있으면 다른 행의 패널이 남는다. 펼침 여부는 컴포넌트 내부 UI 상태라 useState로 둔다(frontend.md 상태 관리 원칙)
  const [rawExpandedId, setRawExpandedId] = useState<number | null>(null)

  // 같은 버튼을 다시 누르면 닫히고, 다른 카드를 누르면 그 카드로 바뀐다(이전 패널은 자동으로 닫힌다)
  function toggleExpanded(wishlistItemId: number) {
    setRawExpandedId((current) => (current === wishlistItemId ? null : wishlistItemId))
  }

  // 찜 해제 등으로 항목이 목록에서 사라졌다면 펼침도 없는 것으로 본다. 상태를 따로 정리하는 effect 대신
  // 렌더링 중 계산으로 처리한다(파생 가능한 값은 state로 두지 않는다). 사라진 id는 다시 쓰이지 않아 남은 값은 무해하다
  const expandedId = list.items.some((item) => item.wishlistItemId === rawExpandedId)
    ? rawExpandedId
    : null

  // 다 본 작품은 계산에서 빠지므로(PRD FR-09) 구역을 나눈다. 같은 목록에서 걸러내는 값이라 상태로 두지 않고 계산한다
  const activeItems = list.items.filter((item) => item.watchedAt === null)
  const watchedItems = list.items.filter((item) => item.watchedAt !== null)
  // 계산에 쓸 작품을 우선순위 단계별로 나눈 묶음(화면 표시용). 서버 순서(최근 찜한 순)는 묶음 안에서 그대로 유지된다.
  // 위 activeItems와 같은 "이미 받은 목록 걸러내기"라 상태로 두지 않고 렌더링 중에 계산한다
  const activeGroups = WISHLIST_PRIORITY_OPTIONS.map((option) => ({
    priority: option.value,
    items: activeItems.filter((item) => item.priority === option.value),
  }))
  // 활성 찜이 2개 이상인데 모두 같은 단계인지. 이때만 "우선순위를 나누면…" 권유를 보인다(1개는 나눌 수 없고, 이미 나눴으면 권유가 필요 없다)
  const isAllSamePriority =
    activeItems.length >= 2 &&
    activeItems.every((item) => item.priority === activeItems[0].priority)
  // 상한(20개)에 닿았는지. 서버가 센 개수와 상한을 그대로 비교한다(화면이 다시 세지 않는다)
  const isFull = list.activeItemCount >= list.maxActiveItemCount
  // 계산에 쓸 작품 중 같은 드라마의 시즌이 둘 이상인지. 다 본 시즌은 계산에서 빠져 순서 제약이 없으므로 세지 않는다
  const seasonCountBySeries = new Map<string, number>()
  for (const item of activeItems) {
    if (item.mediaType !== 'tv' || item.seasonNumber === null) continue
    const key = `${item.mediaType}:${item.tmdbId}`
    seasonCountBySeries.set(key, (seasonCountBySeries.get(key) ?? 0) + 1)
  }
  const hasSeveralSeasonsOfOneSeries = [...seasonCountBySeries.values()].some((count) => count >= 2)

  if (list.items.length === 0) {
    return (
      <EmptyState
        title="아직 찜한 작품이 없어요"
        description="보고 싶은 작품을 찜하면 어느 달에 어디를 구독하면 좋을지 계산해 드려요."
        action={
          <Button asChild size="lg" className="mt-1 h-11">
            <Link to={`/${location.search}`}>
              <SearchIcon aria-hidden="true" />
              작품 찾아보기
            </Link>
          </Button>
        }
      />
    )
  }

  const sortedServices = [...services].sort((a, b) => a.displayOrder - b.displayOrder)
  const pricingPath = `/pricing${location.search}`

  // 이 항목에 대한 요청이 진행 중인지(진행 중이면 카드 조작을 잠시 막아 같은 요청이 겹치지 않게 한다)
  function isBusy(wishlistItemId: number): boolean {
    return (
      (update.isPending && update.variables?.wishlistItemId === wishlistItemId) ||
      (remove.isPending && remove.variables === wishlistItemId)
    )
  }

  function getCardProps(item: WishlistItemList['items'][number]): WishlistRowCardProps {
    return {
      item,
      statusEntries: buildServiceStatusEntries(sortedServices, item.availabilities),
      detailSearch: location.search,
      isBusy: isBusy(item.wishlistItemId),
      onChangePriority: (priority) =>
        update.mutate(
          { wishlistItemId: item.wishlistItemId, priority },
          { onError: notifyFailure },
        ),
      onToggleWatched: () => {
        // 다 봤음 이동은 카드가 다른 구역으로 옮겨 가므로 펼침을 닫는다(패널이 엉뚱한 구역에 남지 않게)
        if (expandedId === item.wishlistItemId) setRawExpandedId(null)
        update.mutate(
          { wishlistItemId: item.wishlistItemId, isWatched: item.watchedAt === null },
          {
            onSuccess: () =>
              toast.success(
                item.watchedAt === null ? '다 봤음으로 표시했어요' : '다 봤음 표시를 취소했어요',
              ),
            onError: notifyFailure,
          },
        )
      },
      onRemove: () =>
        remove.mutate(item.wishlistItemId, {
          // 바로 삭제되므로(되돌리기 없음) 무엇을 뺐는지 이름으로 알린다. 시즌 이름은 카드와 같은 표기("Season N" → "시즌 N")다
          onSuccess: () => {
            const seasonName = formatSeasonName(item.seasonName)
            toast.success(
              `${seasonName ? `${item.title} ${seasonName}` : item.title} 찜을 해제했어요`,
            )
          },
          onError: notifyFailure,
        }),
    }
  }

  return (
    // 구간(머리 안내 → 계산에 쓸 작품 → 다 봤음 → 출처) 사이를 gap-section(40/48px) 하나로 띄운다(styles/tokens.css R1·R2).
    // 이전: <div className="flex flex-col gap-5"> (구간 사이 20px가 묶음 사이 12px와 비슷해 구간 경계가 약했다)
    <div className="flex flex-col gap-section">
      {/* 머리 안내 묶음: 개수 줄 + 상한 안내 + 시즌 순서 안내. 서로 같은 이야기라 16px로 붙인다.
          이전에는 셋이 바깥 묶음의 직접 자식이었다(구간 사이 간격을 크게 하면 서로 멀어져서 감쌌다) */}
      <div className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          {/* 개수 줄 "찜 N/20"(확정, PRD SCR-08). N은 서버가 센 현재 개수(다 봤음 제외), 20은 서버가 준 상한이다 */}
          <p className="text-muted-foreground tabular-nums">
            찜 {list.activeItemCount}/{list.maxActiveItemCount}
            {watchedItems.length > 0 && <> · 다 봤음 {watchedItems.length}개</>}
          </p>
          {/* 데스크톱의 계산하기 버튼: 목록 위 오른쪽. 모바일에서는 아래 고정 막대가 대신한다 */}
          {activeItems.length > 0 && (
            <Button asChild size="lg" className="hidden h-11 px-5 text-base md:inline-flex">
              <Link to={pricingPath}>
                <CalculatorIcon aria-hidden="true" />
                계산하기
              </Link>
            </Button>
          )}
        </div>

        {/* 20개에 닿으면 상단 안내 띠(확정 문구, PRD SCR-08). 상한 숫자는 서버가 준 값이다.
          role="status": shadcn Alert의 기본 role="alert"는 낭독기가 하던 말을 끊고 즉시 읽는다. 상한 안내는 오류가 아닌 참고 정보라 한 단계 낮춘다(058 검수 제안 6) */}
        {isFull && (
          <Alert role="status" className="border-info bg-info-bg text-info">
            <InfoIcon aria-hidden="true" />
            <AlertDescription className="text-info">
              찜은 {list.maxActiveItemCount}개까지 담을 수 있어요. 다 본 작품을 &apos;다
              봤음&apos;으로 옮기면 자리가 생겨요.
            </AlertDescription>
          </Alert>
        )}

        {/* 같은 드라마의 시즌이 둘 이상이면 계산 순서를 한 줄로 알린다(확정 문구, PRD SCR-08·11절 37번) */}
        {hasSeveralSeasonsOfOneSeries && (
          <p className="text-sm text-muted-foreground">
            같은 드라마는 앞 시즌부터 보는 순서로 계산해요.
          </p>
        )}
      </div>

      {/* 구간 제목·간격은 공통 PageSection이 맡는다(제목 h2 text-xl bold, 제목과 내용 사이 16px. styles/tokens.css R3·R4).
          이전: <section aria-labelledby="wishlist-active-heading" className="grid gap-3">
                  <h2 id="wishlist-active-heading" className="text-lg font-semibold">계산에 쓸 작품</h2> */}
      <PageSection headingId="wishlist-active-heading" title="계산에 쓸 작품">
        {activeItems.length > 0 ? (
          // 분포 요약과 우선순위 묶음(꼭 볼 작품 / 보고 싶음 / 여유될 때) 사이를 32px로 띄운다.
          // 묶음 안(소제목과 카드 12px, 카드 사이 12px)보다 2.5배 이상 넓어 묶음 경계가 간격으로 읽힌다(R1과 같은 비율).
          // 이전: 조각(<>)이라 바깥 section의 gap-3(12px)이 묶음 사이에도 그대로 쓰였다
          <div className="grid gap-8">
            {/* 우선순위 분포 요약: 세 단계에 몇 개씩 들어 있는지 배지로 보이고, 계산이 단계를 어떤 순서로 챙기는지 한 줄로 알린다.
                앞 문장은 늘 보이는 고정 안내, "우선순위를 나누면…" 권유는 모두 같은 단계일 때만 보인다 */}
            <div className="grid gap-2 rounded-xl border border-border bg-card p-3">
              <ul aria-label="우선순위별 작품 수" className="flex flex-wrap gap-2">
                {activeGroups.map((group) => (
                  <li key={group.priority}>
                    <PriorityBadge priority={group.priority} count={group.items.length} />
                  </li>
                ))}
              </ul>
              <p className="text-sm break-keep text-muted-foreground">
                계산은 꼭 볼 작품을 가장 먼저 챙기고, 그다음 보고 싶음, 여유될 때 순으로 넣어요.
                {/* 권유 문장은 모두 같은 단계일 때만 보인다(이미 나눴다면 불필요한 잔소리라서) */}
                {isAllSamePriority && ' 우선순위를 나누면 계산이 더 의미 있어져요.'}
              </p>
            </div>

            {/* 우선순위 묶음(꼭 볼 작품 → 보고 싶음 → 여유될 때). 계산이 챙기는 순서와 같은 순서로 위에서 아래로 쌓는다 */}
            {activeGroups.map((group) => {
              const headingId = `wishlist-group-${group.priority}`
              return (
                <div
                  key={group.priority}
                  role="group"
                  aria-labelledby={headingId}
                  // 이전: className="grid gap-2" (소제목과 카드 사이 8px)
                  className="grid gap-3"
                >
                  <div className="grid gap-0.5">
                    <h3
                      id={headingId}
                      className="inline-flex items-center gap-1.5 text-base font-bold"
                    >
                      <PriorityIcon priority={group.priority} className="size-5" />
                      {WISHLIST_PRIORITY_LABELS[group.priority]}
                      <span className="font-semibold text-muted-foreground tabular-nums">
                        {group.items.length}개
                      </span>
                    </h3>
                    <p className="text-sm break-keep text-muted-foreground">
                      {WISHLIST_PRIORITY_DESCRIPTIONS[group.priority]}
                    </p>
                  </div>
                  {group.items.length > 0 ? (
                    <CardList
                      items={group.items}
                      getCardProps={getCardProps}
                      expandedId={expandedId}
                      onToggleExpanded={toggleExpanded}
                    />
                  ) : (
                    // 빈 묶음도 제목을 남겨 세 단계가 있다는 것을 늘 보이고, 비었다는 사실은 점선 상자 + 글자로 알린다
                    <p className="rounded-lg border border-dashed border-border px-3 py-2.5 text-sm break-keep text-muted-foreground">
                      {EMPTY_GROUP_MESSAGES[group.priority]}
                    </p>
                  )}
                </div>
              )
            })}
          </div>
        ) : (
          <EmptyState
            title="계산할 작품이 없어요"
            description="다 봤음을 취소하거나 새 작품을 찜하면 계산할 수 있어요."
            action={
              <Button asChild variant="outline" size="lg" className="mt-1 h-11">
                <Link to={`/${location.search}`}>
                  <SearchIcon aria-hidden="true" />
                  작품 찾아보기
                </Link>
              </Button>
            }
          />
        )}
      </PageSection>

      {watchedItems.length > 0 && (
        // 다 봤음 구간은 계산에서 빠진 작품이라 제목을 흐린 색(text-muted-foreground)으로 둔다. 그래서 PageSection 대신 같은 규칙을 직접 적었다
        // (제목 h2 text-xl bold, 제목·설명 4px, 제목 묶음과 내용 16px. styles/tokens.css R3·R4).
        // 이전: <section … className="grid gap-3"><h2 className="text-lg font-semibold text-muted-foreground">…</h2><p className="-mt-1 …">
        <section aria-labelledby="wishlist-watched-heading" className="grid gap-section-inner">
          <div className="grid gap-1">
            <h2 id="wishlist-watched-heading" className="text-xl font-bold text-muted-foreground">
              다 봤음
            </h2>
            <p className="text-sm text-muted-foreground">
              다 본 작품은 계산에서 빠져요. '다 봤음 취소'로 되돌릴 수 있어요.
            </p>
          </div>
          <CardList
            items={watchedItems}
            getCardProps={getCardProps}
            expandedId={expandedId}
            onToggleExpanded={toggleExpanded}
          />
        </section>
      )}

      {/* 제공처 정보가 보이는 화면이라 출처와 기준일을 둔다 */}
      <div className="grid gap-1.5 border-t border-border pt-4">
        <SourceAttribution />
        <DataAsOf value={list.dataAsOf} />
        <p className="text-sm text-muted-foreground">
          제공 여부는 확인된 범위이며, 실제 서비스 상황과 다를 수 있습니다.
        </p>
      </div>

      {/* 모바일 하단 고정(sticky) "계산하기". 내용 끝에서는 막대가 푸터를 가리지 않고 함께 스크롤된다. 안전 영역(노치) 여백을 pb-[env(safe-area-inset-bottom)]로 더한다 */}
      {activeItems.length > 0 && (
        <div className="sticky bottom-0 z-20 -mx-4 border-t border-border bg-card px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] md:hidden">
          <Button asChild size="lg" className="h-12 w-full text-base">
            <Link to={pricingPath}>
              <CalculatorIcon aria-hidden="true" />
              계산하기
            </Link>
          </Button>
        </div>
      )}
    </div>
  )
}
