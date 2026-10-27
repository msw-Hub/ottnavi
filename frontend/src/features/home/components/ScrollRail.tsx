import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

interface ScrollRailProps {
  label: string // 목록 이름(낭독기가 "○○, 목록, 항목 10개"처럼 읽는다)
  isOrdered?: boolean // 순위 목록이면 true(<ol>로 그려 순서가 있다는 것을 낭독기에 알린다)
  children: ReactNode // <li> 항목들(ScrollRailItem)
}

/**
 * 작품 카드를 가로로 한 줄 늘어놓고 옆으로 넘겨 보는 목록이다(넷플릭스·TVING의 작품 줄을 우리 토큰으로 옮긴 것).
 *
 * 조작 방법:
 * - 손가락·트랙패드: 옆으로 밀면 카드 단위로 멈춘다(snap-x snap-mandatory, 카드 왼쪽 끝 기준).
 * - 키보드: Tab으로 카드 제목 링크를 차례로 옮기면 브라우저가 그 카드가 보이게 알아서 옆으로 넘긴다. 따로 키를 배울 필요가 없다.
 * - 마우스(640px 이상): 줄 양 끝의 화살표 버튼으로 한 화면(보이는 폭의 80%)씩 넘긴다. 끝에 닿으면 그쪽 버튼은 비활성(disabled)이 되고 화면에서도 사라진다(카드를 가리지 않게).
 *   모바일에서는 손가락으로 미는 것이 자연스러워 버튼을 숨긴다.
 * - 움직임 줄이기(prefers-reduced-motion)를 켠 사용자에게는 부드러운 스크롤 대신 바로 넘긴다.
 *
 * 모양: 모바일에서는 줄이 화면 끝까지 닿게(-mx-4 px-4) 해서 "옆에 더 있다"는 것이 잘린 카드로 보이게 한다.
 * 위아래 py-2는 카드 포커스 링(2px + 간격 2px)이 가로 스크롤 영역에 잘리지 않게 하는 여백이다(py-1이면 서비스 탭처럼 아래쪽이 잘릴 수 있다)(가로 스크롤 영역은 세로로도 내용을 자른다).
 */
export function ScrollRail({ label, isOrdered = false, children }: ScrollRailProps) {
  const listId = useId()
  const listRef = useRef<HTMLOListElement & HTMLUListElement>(null)
  const [edge, setEdge] = useState({ isAtStart: true, isAtEnd: false }) // 화살표 버튼 비활성 판단용(화면 표시 전용 상태)

  // 지금 스크롤 위치가 처음·끝인지 다시 잰다. 1px 여유는 소수점 픽셀 반올림 때문에 끝에 닿아도 1px 모자라게 나오는 경우를 막는다
  function updateEdge() {
    const list = listRef.current
    if (!list) return
    setEdge({
      isAtStart: list.scrollLeft <= 1,
      isAtEnd: list.scrollLeft + list.clientWidth >= list.scrollWidth - 1,
    })
  }

  // 처음 그린 뒤와 줄의 폭이 바뀔 때(창 크기 변경) 끝 여부를 다시 잰다.
  // 스크롤 이벤트만으로는 "처음부터 카드가 모두 보여 넘길 것이 없는" 경우를 알 수 없어 필요하다.
  // 의존성이 빈 배열인 이유: 목록 내용이 바뀌면(탭 전환) 부모가 key를 바꿔 이 컴포넌트를 새로 만들므로 처음 한 번이면 된다.
  // ResizeObserver가 없는 환경(테스트용 jsdom)에서는 처음 한 번만 잰다.
  useEffect(() => {
    updateEdge()
    const list = listRef.current
    if (!list || typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(() => updateEdge())
    observer.observe(list)
    return () => observer.disconnect()
  }, [])

  // 화살표 버튼: 보이는 폭의 80%만큼 넘긴다(다음 화면 첫 카드가 이전 화면 끝 카드와 조금 겹쳐 이어 보기 쉽다)
  function scrollByPage(direction: -1 | 1) {
    const list = listRef.current
    if (!list) return
    const prefersReducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    list.scrollBy({
      left: direction * list.clientWidth * 0.8,
      behavior: prefersReducedMotion ? 'auto' : 'smooth',
    })
  }

  const ListTag = isOrdered ? 'ol' : 'ul'

  return (
    <div className="relative">
      <RailArrowButton
        direction="prev"
        label={`${label} 이전 작품 보기`}
        controls={listId}
        isDisabled={edge.isAtStart}
        onClick={() => scrollByPage(-1)}
      />
      {/* 가로 스크롤바를 숨긴다(독점 줄 ExclusiveTitleRail과 같은 방식). [scrollbar-width:none]은 Firefox·최신 Chromium,
          [&::-webkit-scrollbar]:hidden은 Safari·예전 Chromium용이다. 숨겨도 넘길 수단은 그대로 남는다:
          손가락·트랙패드 밀기, Tab 이동 시 자동 스크롤, 640px 이상의 양 끝 화살표 버튼, Shift+휠.
          이전: [scrollbar-width:thin](얇은 스크롤바가 카드 아래에 보였다) */}
      <ListTag
        id={listId}
        ref={listRef}
        aria-label={label}
        onScroll={updateEdge}
        className="-mx-4 flex snap-x snap-mandatory scroll-px-4 gap-3 overflow-x-auto overscroll-x-contain px-4 py-2 [scrollbar-width:none] sm:gap-4 [&::-webkit-scrollbar]:hidden"
      >
        {children}
      </ListTag>
      <RailArrowButton
        direction="next"
        label={`${label} 다음 작품 보기`}
        controls={listId}
        isDisabled={edge.isAtEnd}
        onClick={() => scrollByPage(1)}
      />
    </div>
  )
}

interface ScrollRailItemProps {
  isWide?: boolean // 순위 카드처럼 큰 숫자가 붙어 폭이 더 필요한 항목이면 true
  children: ReactNode
}

// 가로 줄의 항목 하나. 폭을 고정(shrink-0)해 카드 크기가 화면마다 흔들리지 않게 하고, 카드 왼쪽 끝에서 멈추게 한다(snap-start)
export function ScrollRailItem({ isWide = false, children }: ScrollRailItemProps) {
  return (
    <li
      className={cn(
        'shrink-0 snap-start',
        // 모바일 128px(375px 화면에 카드 2장 반이 보여 옆에 더 있다는 것이 드러난다) → 넓은 화면 160px
        isWide ? 'w-40 sm:w-44 lg:w-48' : 'w-32 sm:w-36 lg:w-40',
      )}
    >
      {children}
    </li>
  )
}

interface RailArrowButtonProps {
  direction: 'prev' | 'next'
  label: string // 버튼 이름(아이콘만 있어 낭독기용 이름이 필요하다)
  controls: string // 이 버튼이 움직이는 목록의 id(aria-controls)
  isDisabled: boolean // 그쪽 끝에 닿아 더 넘길 수 없으면 true
  onClick: () => void
}

// 줄 양 끝의 원형 화살표 버튼(640px 이상에서만). 포스터 높이의 가운데쯤(top-1/3)에 떠 있다
function RailArrowButton({
  direction,
  label,
  controls,
  isDisabled,
  onClick,
}: RailArrowButtonProps) {
  const Icon = direction === 'prev' ? ChevronLeftIcon : ChevronRightIcon
  return (
    <button
      type="button"
      aria-label={label}
      aria-controls={controls}
      disabled={isDisabled}
      onClick={onClick}
      className={cn(
        // size-10(40px) 원형. 카드 위에 떠 있어 테두리(border-input, 카드 위 3:1 이상)와 card 바탕으로 경계를 만든다
        'absolute top-1/3 z-20 hidden size-10 -translate-y-1/2 items-center justify-center rounded-full border border-input bg-card text-foreground shadow-md hover:bg-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:pointer-events-none disabled:opacity-0 sm:inline-flex',
        direction === 'prev' ? '-left-3' : '-right-3',
      )}
    >
      <Icon aria-hidden="true" className="size-5" />
    </button>
  )
}
