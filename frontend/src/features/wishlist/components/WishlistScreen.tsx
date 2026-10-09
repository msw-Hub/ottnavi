import type { ReactNode } from 'react'
import { CalculatorIcon, SearchIcon } from 'lucide-react'
import { Link, useLocation } from 'react-router'
import { toast } from 'sonner'
import type { OttService } from '@/api/generated/model'
import { isApiError } from '@/api/http'
import { DataAsOf } from '@/components/common/DataAsOf'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import { MockVariantBanner } from '@/components/common/MockVariantBanner'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import { Button } from '@/components/ui/button'
import { WishlistRow, type WishlistRowCardProps } from '@/features/wishlist/components/WishlistRow'
import {
  useRemoveWishlistItem,
  useUpdateWishlistItem,
  useWishlist,
} from '@/features/wishlist/hooks/useWishlist'
import type { WishlistList } from '@/features/wishlist/mockTypes'
import { useOttServices } from '@/hooks/useOttServices'
import { getMockVariant } from '@/lib/mockScenario'
import { buildServiceStatusEntries } from '@/lib/serviceStatus'

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
  items: WishlistList['items'] // 한 구역(계산에 쓸 작품 또는 다 봤음)의 항목
  getCardProps: (item: WishlistList['items'][number]) => WishlistRowCardProps // 항목 하나의 카드 props
}

/**
 * 카드를 2개씩 묶어 "행"으로 쌓는다. 데스크톱(lg 이상)에서는 한 행이 2열이고, 좁은 폭에서는 한 열이다.
 * 2개씩 묶는 이유: 펼침 패널을 "그 카드가 속한 행 아래 전체 폭"에 넣으려면 어느 카드끼리 같은 행인지 알아야 한다.
 * 단순히 전체를 하나의 2열 grid로 두면 한쪽 카드의 패널이 다음 카드를 밀어 열이 어긋난다.
 * 묶는 일은 순수 계산이라 상태 없이 렌더링 중에 한다.
 */
function CardList({ items, getCardProps }: CardListProps) {
  const rows: WishlistList['items'][] = []
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
        />
      ))}
    </div>
  )
}

interface WishlistContentProps {
  list: WishlistList // 불러온 찜 목록(최근에 찜한 순)과 데이터 기준일
  services: OttService[] // 서비스 목록 API의 7개 서비스
}

// 데이터를 불러온 뒤의 화면: 빈 상태 또는 "계산에 쓸 작품" + "다 봤음" 두 구역
function WishlistContent({ list, services }: WishlistContentProps) {
  const location = useLocation()
  const update = useUpdateWishlistItem()
  const remove = useRemoveWishlistItem()

  // 다 본 작품은 계산에서 빠지므로(PRD FR-09) 구역을 나눈다. 같은 목록에서 걸러내는 값이라 상태로 두지 않고 계산한다
  const activeItems = list.items.filter((item) => item.watchedAt === null)
  const watchedItems = list.items.filter((item) => item.watchedAt !== null)

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

  function getCardProps(item: WishlistList['items'][number]): WishlistRowCardProps {
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
      onToggleWatched: () =>
        update.mutate(
          { wishlistItemId: item.wishlistItemId, isWatched: item.watchedAt === null },
          {
            onSuccess: () =>
              toast.success(
                item.watchedAt === null ? '다 봤음으로 표시했어요' : '다 봤음 표시를 취소했어요',
              ),
            onError: notifyFailure,
          },
        ),
      onRemove: () =>
        remove.mutate(item.wishlistItemId, {
          // 바로 삭제되므로(되돌리기 없음) 무엇을 뺐는지 이름으로 알린다
          onSuccess: () =>
            toast.success(
              `${item.seasonName ? `${item.title} ${item.seasonName}` : item.title} 찜을 해제했어요`,
            ),
          onError: notifyFailure,
        }),
    }
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-muted-foreground tabular-nums">
          계산에 쓸 작품 {activeItems.length}개
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

      <MockVariantBanner
        decisionLabel="R11-19 드라마 찜 단위"
        variant={getMockVariant()}
        description={{
          a: '드라마를 시즌 단위로 찜해요. 같은 드라마도 시즌마다 한 줄씩 보이고, 필요 시간은 그 시즌만 더한 값이에요.',
          b: '드라마를 작품 단위로 묶어 한 줄로 보여요. 필요 시간은 모든 시즌을 더한 값이에요.',
        }}
      />

      <section aria-labelledby="wishlist-active-heading" className="grid gap-3">
        <h2 id="wishlist-active-heading" className="text-lg font-semibold">
          계산에 쓸 작품
        </h2>
        {activeItems.length > 0 ? (
          <CardList items={activeItems} getCardProps={getCardProps} />
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
      </section>

      {watchedItems.length > 0 && (
        <section aria-labelledby="wishlist-watched-heading" className="grid gap-3">
          <h2 id="wishlist-watched-heading" className="text-lg font-semibold text-muted-foreground">
            다 봤음
          </h2>
          <p className="-mt-1 text-sm text-muted-foreground">
            다 본 작품은 다음 계산에서 빠져요. 카드의 '다 봤음 취소'로 되돌릴 수 있어요.
          </p>
          <CardList items={watchedItems} getCardProps={getCardProps} />
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
