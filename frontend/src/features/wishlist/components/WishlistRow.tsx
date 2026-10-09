import { useId, useState } from 'react'
import { ServiceStatusChips } from '@/features/wishlist/components/ServiceStatusChips'
import { WishlistCard, type WishlistCardProps } from '@/features/wishlist/components/WishlistCard'
import type { WishlistListItem } from '@/features/wishlist/mockTypes'

// 행이 카드에 직접 넘기지 않는 값(펼침 상태는 행이 관리한다)을 뺀 카드 props
export type WishlistRowCardProps = Omit<
  WishlistCardProps,
  'isExpanded' | 'panelId' | 'onToggleExpanded'
>

interface WishlistRowProps {
  items: WishlistListItem[] // 이 행에 놓일 항목. 1~2개(데스크톱 2열의 한 행, 홀수 개의 마지막 행은 1개)
  getCardProps: (item: WishlistListItem) => WishlistRowCardProps // 항목 하나의 카드 props를 만드는 함수
}

/**
 * 찜 목록의 "한 행"이다. 카드 1~2장과, 펼친 카드의 서비스별 시청 가능 여부 패널을 함께 그린다.
 *
 * 왜 행 단위인가: 패널을 카드 안에 넣으면 그 카드만 커져서 옆 카드와 정렬이 깨진다. 그래서 패널은 카드 밖,
 * 같은 행 아래의 "전체 폭(2열 span)" 칸으로 둔다. 같은 행의 두 카드 높이는 변하지 않고 다음 행이 패널만큼 밀린다.
 *
 * 화면 폭별 배치는 CSS만으로 처리한다(JS로 폭을 재지 않는다). DOM 순서는 [카드A, 카드B, 패널A, 패널B]이고
 * - lg 이상: 2열 grid. order 그대로라 카드 두 장이 위, 패널이 그 아래 전체 폭(col-span-2).
 * - lg 미만: 한 열. order로 [카드A, 패널A, 카드B, 패널B] 순서로 바꿔 패널이 자기 카드 바로 아래에 온다.
 * 화면 낭독기·키보드 순서(DOM 순서)와 시각 순서가 다르지만, 패널은 aria-controls로 연결돼 있고 펼침 버튼 다음에 오는
 * 내용이 같은 카드의 것이라는 점은 패널 제목("○○의 서비스별 시청 가능 여부")이 알려 준다.
 *
 * 펼침 상태: 한 행에서 한 번에 하나만 펼친다(구현이 단순하고, 두 패널이 한꺼번에 열려 화면이 길어지는 것을 막는다).
 */
export function WishlistRow({ items, getCardProps }: WishlistRowProps) {
  // 펼쳐진 항목의 id(없으면 null). 이 행 안에서만 의미 있는 UI 상태라 useState로 둔다
  const [expandedId, setExpandedId] = useState<number | null>(null)
  // 같은 화면에 행이 여러 개라 id가 겹치지 않도록 useId로 접두어를 만든다
  const baseId = useId()

  // 카드/패널의 order. 앞 4개는 lg 미만(한 열), lg: 접두어는 lg 이상(2열) 값이다
  const CARD_ORDER = ['order-1 lg:order-1', 'order-3 lg:order-2']
  const PANEL_ORDER = ['order-2 lg:order-3', 'order-4 lg:order-4']

  return (
    <ul className="grid gap-3 lg:grid-cols-2">
      {items.map((item, index) => {
        const panelId = `${baseId}-panel-${item.wishlistItemId}`
        return (
          <li key={item.wishlistItemId} className={CARD_ORDER[index]}>
            <WishlistCard
              {...getCardProps(item)}
              isExpanded={expandedId === item.wishlistItemId}
              panelId={panelId}
              onToggleExpanded={() =>
                setExpandedId((current) =>
                  current === item.wishlistItemId ? null : item.wishlistItemId,
                )
              }
            />
          </li>
        )
      })}

      {items.map((item, index) => {
        if (expandedId !== item.wishlistItemId) return null
        const { statusEntries, item: cardItem } = getCardProps(item)
        const displayTitle = cardItem.seasonName
          ? `${cardItem.title} ${cardItem.seasonName}`
          : cardItem.title
        const panelId = `${baseId}-panel-${item.wishlistItemId}`
        const headingId = `${panelId}-heading`
        return (
          <li
            key={`panel-${item.wishlistItemId}`}
            className={`${PANEL_ORDER[index]} lg:col-span-2`}
          >
            {/* 패널 윤곽: 펼친 카드의 강조 테두리와 같은 primary 색 테두리 + 제목에 작품 이름으로 어느 카드의 것인지 밝힌다 */}
            <section
              id={panelId}
              role="region"
              aria-labelledby={headingId}
              className="grid gap-3 rounded-xl border-2 border-primary bg-card p-4"
            >
              <h4 id={headingId} className="text-sm font-bold">
                {displayTitle}의 서비스별 시청 가능 여부
              </h4>
              <ServiceStatusChips
                entries={statusEntries}
                label={`${displayTitle} 서비스별 제공 상태`}
              />
            </section>
          </li>
        )
      })}
    </ul>
  )
}
