import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

interface ScrollRailProps {
  label: string // 목록 이름(낭독기가 "○○, 목록, 항목 10개"처럼 읽는다)
  isOrdered?: boolean // 순위 목록이면 true(<ol>로 그려 순서가 있다는 것을 낭독기에 알린다)
  isFeatured?: boolean // 항목이 큰 카드(ScrollRailItem isFeatured)면 true. 화살표 버튼의 세로 위치·길이를 큰 포스터에 맞춘다
  children: ReactNode // <li> 항목들(ScrollRailItem)
}

// 화살표 버튼의 세로 위치와 길이(640px 이상에서만 보인다). 포스터(2:3) 높이의 가운데에 오게 미리 계산한 값이다.
// 가운데 = 줄 위 여백 py-2(8px) + 포스터 높이 ÷ 2. 포스터 높이 = ScrollRailItem 폭 × 1.5 이므로 항목 폭을 바꾸면 여기도 같이 고친다.
// - 보통 카드: 폭 144px(640px~)·160px(1024px~) → 포스터 216·240px → 가운데 116px(top-29)·128px(top-32), 길이 112·120px(포스터의 약 절반)
// - 큰 카드:   폭 224px(640px~)·240px(1024px~) → 포스터 336·360px → 가운데 176px(top-44)·188px(top-47), 길이 160·176px(포스터의 약 절반)
// 카드 전체(제목·칩 포함)의 1/3 지점(이전: top-1/3)이 아니라 포스터 기준이라 제목 줄 길이가 달라도 화살표가 포스터 가운데에 있다
const ARROW_SIZE_CLASS = {
  default: 'top-29 h-28 lg:top-32 lg:h-30',
  featured: 'top-44 h-40 lg:top-47 lg:h-44',
} as const

// 카드 왼쪽 끝이 스크롤 내용 안에서 시작하는 위치(px). offsetLeft를 쓰지 않는 이유: 줄(<ol>)이 위치 기준(relative) 요소가 아니라서
// offsetLeft가 줄이 아닌 바깥 래퍼(div.relative) 기준으로 나오고, 줄이 -mx-4로 16px 넓어서 값이 16px 틀어진다. 화면 좌표 차이로 직접 잰다
function getItemStart(list: HTMLElement, item: HTMLElement) {
  return item.getBoundingClientRect().left - list.getBoundingClientRect().left + list.scrollLeft
}

/**
 * 작품 카드를 가로로 한 줄 늘어놓고 옆으로 넘겨 보는 목록이다(넷플릭스·TVING의 작품 줄을 우리 토큰으로 옮긴 것).
 *
 * 조작 방법:
 * - 손가락·트랙패드: 옆으로 밀면 카드 단위로 멈춘다(snap-x snap-mandatory, 카드 왼쪽 끝 기준).
 * - 키보드: Tab으로 카드 제목 링크를 차례로 옮기면 브라우저가 그 카드가 보이게 알아서 옆으로 넘긴다. 따로 키를 배울 필요가 없다.
 * - 마우스(640px 이상): 줄 양 끝의 화살표 버튼으로 한 화면(보이는 카드 수 - 1장, 카드 시작점에 맞춰)씩 넘긴다. 이전: 보이는 폭의 80%. 끝에 닿으면 그쪽 버튼은 비활성(disabled)이 되고 화면에서도 사라진다(카드를 가리지 않게).
 *   모바일에서는 손가락으로 미는 것이 자연스러워 버튼을 숨긴다.
 * - 움직임 줄이기(prefers-reduced-motion)를 켠 사용자에게는 부드러운 스크롤 대신 바로 넘기고, 줄의 밝기를 잠깐 되살려 넘어간 것을 알린다(scrollByPage).
 *
 * 모양: 모바일에서는 줄이 화면 끝까지 닿게(-mx-4 px-4) 해서 "옆에 더 있다"는 것이 잘린 카드로 보이게 한다.
 * 위아래 py-2는 카드 포커스 링(2px + 간격 2px)이 가로 스크롤 영역에 잘리지 않게 하는 여백이다(py-1이면 서비스 탭처럼 아래쪽이 잘릴 수 있다)(가로 스크롤 영역은 세로로도 내용을 자른다).
 */
export function ScrollRail({
  label,
  isOrdered = false,
  isFeatured = false,
  children,
}: ScrollRailProps) {
  const arrowSizeClass = ARROW_SIZE_CLASS[isFeatured ? 'featured' : 'default']
  const listId = useId()
  const listRef = useRef<HTMLOListElement & HTMLUListElement>(null)
  const [edge, setEdge] = useState({ isAtStart: true, isAtEnd: false }) // 화살표 버튼 비활성 판단용(화면 표시 전용 상태)

  // 지금 스크롤 위치가 처음·끝인지 다시 잰다. 1px 여유는 소수점 픽셀 반올림 때문에 끝에 닿아도 1px 모자라게 나오는 경우를 막는다
  // 끝 판단: 맨 끝까지 스크롤했거나, 마지막 카드가 줄 안에 온전히 보이면 끝으로 본다. 카드 단위로 넘기면(scrollByPage) 마지막 페이지가
  // 맨 끝보다 16px(줄 오른쪽 여백) 앞에서 멈춰 "맨 끝까지"만 보면 다음 버튼이 계속 켜져 있게 되기 때문이다.
  function updateEdge() {
    const list = listRef.current
    if (!list) return
    const lastItem = list.lastElementChild as HTMLElement | null
    const isLastItemVisible = lastItem
      ? getItemStart(list, lastItem) + lastItem.offsetWidth - list.scrollLeft <=
        list.clientWidth + 1
      : true
    setEdge({
      isAtStart: list.scrollLeft <= 1,
      isAtEnd: isLastItemVisible || list.scrollLeft + list.clientWidth >= list.scrollWidth - 1,
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

  // 화살표 버튼: 한 번에 "보이는 카드 수 - 1"장을 넘긴다(다음 화면 첫 카드가 이전 화면 끝 카드와 1장 겹쳐 이어 보기 쉽다).
  // 이전: 보이는 폭의 80%만큼 scrollBy 한 뒤 브라우저의 스냅(snap-mandatory)에 맡겼다. 그러면 마지막 페이지만 스냅이 카드 시작점이 아닌
  // 맨 끝(16px 뒤)에 멈춰 카드 줄이 1~4위·4~7위와 16px 어긋났다(운영 피드백 "화살표는 고정인데 좌우 정렬이 안 맞아 보인다").
  // 그래서 이동 위치를 "카드 시작점"으로 직접 계산해 모든 페이지가 같은 왼쪽 정렬을 갖게 한다.
  function scrollByPage(direction: -1 | 1) {
    const list = listRef.current
    if (!list) return
    // 1. 동작 줄이기 설정인지 본다(Windows "애니메이션 효과" 끔도 브라우저에서는 이 설정으로 잡힌다)
    const prefersReducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    const behavior = prefersReducedMotion ? 'auto' : 'smooth'
    const items = Array.from(list.children) as HTMLElement[]
    const firstWidth = items[0]?.offsetWidth ?? 0
    // 2. 카드 크기를 잴 수 없는 환경(테스트용 jsdom)에서는 이전처럼 보이는 폭의 80%만큼 넘긴다
    if (items.length === 0 || firstWidth === 0 || typeof list.scrollTo !== 'function') {
      list.scrollBy({ left: direction * list.clientWidth * 0.8, behavior })
    } else {
      // 3. 스냅 위치: 카드 왼쪽 끝이 줄 왼쪽에서 scroll-padding(16px)만큼 안쪽에 오는 scrollLeft. 첫 카드는 0이다
      const snapPadding = parseFloat(getComputedStyle(list).scrollPaddingLeft) || 0
      const snapOf = (item: HTMLElement) => getItemStart(list, item) - snapPadding
      // 4. 지금 맨 왼쪽에 있는 카드: 스냅 위치가 현재 스크롤 위치에 가장 가까운 카드
      let currentIndex = 0
      items.forEach((item, index) => {
        if (
          Math.abs(snapOf(item) - list.scrollLeft) <
          Math.abs(snapOf(items[currentIndex]) - list.scrollLeft)
        ) {
          currentIndex = index
        }
      })
      // 5. 한 번에 보이는 카드 수(첫 카드 폭 + 카드 사이 간격 기준). 줄의 오른쪽 여백까지 카드가 차지하는 것이 이 줄의 디자인이다
      const gap =
        items.length > 1
          ? getItemStart(list, items[1]) - getItemStart(list, items[0]) - firstWidth
          : 0
      const visibleCount = Math.max(
        1,
        Math.floor((list.clientWidth - snapPadding + gap) / (firstWidth + gap)),
      )
      // 6. 넘기는 장수는 1장 겹치게 (보이는 수 - 1), 최소 1장. 마지막 페이지는 "마지막 카드가 오른쪽 끝에 맞는" 시작 카드까지만 간다
      const step = Math.max(1, visibleCount - 1)
      const lastStartIndex = Math.max(0, items.length - visibleCount)
      const targetIndex =
        direction === 1
          ? Math.min(currentIndex + step, Math.max(lastStartIndex, currentIndex))
          : Math.max(currentIndex - step, 0)
      // 7. 카드 시작점으로 이동한다(맨 끝을 넘지 않게 자른다). 스냅 위치와 같아서 브라우저 스냅이 다시 움직이지 않는다
      const maxScroll = list.scrollWidth - list.clientWidth
      list.scrollTo({
        left: Math.min(Math.max(snapOf(items[targetIndex]), 0), maxScroll),
        behavior,
      })
    }
    // 이전(80% 방식):
    // list.scrollBy({
    //   left: direction * list.clientWidth * 0.8,
    //   behavior: prefersReducedMotion ? 'auto' : 'smooth',
    // })
    // 8. 동작 줄이기에서 바로 넘기면 카드가 "뚝" 바뀌어 넘어간 것을 알아채기 어렵다(운영 피드백 "효과 없이 바로 넘어간다").
    //    위치는 움직이지 않고 줄 전체의 밝기만 35% → 100%로 0.28초 동안 되살려 "바뀌었다"는 것만 알린다(진행 창의 animate-breathe와 같은 원칙).
    //    한 번 누를 때 한 번뿐이라 깜빡임 기준(초당 3회)과도 멀다. Web Animations API(element.animate)는 끝나면 스스로 사라져 정리할 것이 없다.
    //    animate가 없는 환경(테스트용 jsdom)에서는 건너뛴다
    if (prefersReducedMotion && typeof list.animate === 'function') {
      list.animate([{ opacity: 0.35 }, { opacity: 1 }], { duration: 280, easing: 'ease-out' })
    }
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
        sizeClass={arrowSizeClass}
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
        sizeClass={arrowSizeClass}
      />
    </div>
  )
}

interface ScrollRailItemProps {
  isWide?: boolean // 순위 카드처럼 큰 숫자가 붙어 폭이 더 필요한 항목이면 true
  isFeatured?: boolean // 홈 첫 줄(서비스별 인기 순위)처럼 시선을 먼저 끌어야 하는 큰 카드면 true. isWide보다 우선한다
  children: ReactNode
}

// 가로 줄의 항목 하나. 폭을 고정(shrink-0)해 카드 크기가 화면마다 흔들리지 않게 하고, 카드 왼쪽 끝에서 멈추게 한다(snap-start)
export function ScrollRailItem({
  isWide = false,
  isFeatured = false,
  children,
}: ScrollRailItemProps) {
  return (
    <li
      className={cn(
        'shrink-0 snap-start',
        // 모바일 128px(375px 화면에 카드 2장 반이 보여 옆에 더 있다는 것이 드러난다) → 넓은 화면 160px
        // 큰 카드(isFeatured): 순위 숫자 칸을 빼고 포스터 폭이 모바일 136px · 640px 이상 164px · 1024px 이상 212px이 된다.
        // 375px 화면에 카드 1장 반 남짓(약 1.7장)이 보여 첫 카드가 크게 눈에 들어오면서도 옆에 더 있다는 것이 드러난다.
        // 1280px 화면에는 3장 남짓이 보인다(다른 줄은 6장 남짓이라 이 줄이 홈에서 가장 크게 보인다)
        // B안: 큰 카드는 순위 숫자를 포스터 위에 겹쳐 항목 폭이 곧 포스터 폭이다.
        // 모바일은 줄 안쪽 폭(375px 화면에서 343px)의 82%(약 281px)라 포스터 한 장이 화면 폭 가까이 크게 보이고 옆 카드가 약 50px 보인다.
        // 640px 이상 224px · 1024px 이상 240px(포스터 높이 360px): 1280×800 첫 화면에 포스터 전체와 제목이 들어오는 크기이고 3장 반 남짓이 보인다.
        // 이전(B안 전): 'w-48 sm:w-60 lg:w-72' (숫자 칸 포함 폭, 포스터는 136/164/212px)
        isFeatured
          ? 'w-[82%] sm:w-56 lg:w-60'
          : isWide
            ? 'w-40 sm:w-44 lg:w-48'
            : 'w-32 sm:w-36 lg:w-40',
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
  sizeClass: string // 세로 위치(top)와 길이(h). 포스터 높이 가운데에 맞춘 값(ARROW_SIZE_CLASS)
}

// 줄 양 끝의 세로로 긴 사각 화살표 버튼(640px 이상에서만). 포스터 높이의 가운데에 떠 있다(H-1-1 사용자 요청 "사각으로 길게, 포스터 가운데")
function RailArrowButton({
  direction,
  label,
  controls,
  isDisabled,
  onClick,
  sizeClass,
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
        // w-9(36px) × 포스터 높이의 약 절반, 모서리 rounded-md. 폭 36px은 마우스로 누르기 충분하면서(24px 최소 기준을 넘는다) 포스터를 적게 가린다.
        // 줄의 스크롤 영역 끝(-left-4/-right-4, 줄의 -mx-4와 같은 거리)에 붙여, 첫 카드나 잘린 끝 카드의 가장자리 약 20px만 덮는다.
        // 카드 위에 떠 있어 테두리(border-input, 카드 위 3:1 이상)와 card 바탕으로 경계를 만든다. 포커스 링·끝에서 숨기기(disabled:opacity-0)는 그대로다
        // 이전: 'absolute top-1/3 z-20 hidden size-10 -translate-y-1/2 items-center justify-center rounded-full ...', direction === 'prev' ? '-left-3' : '-right-3'
        // 이전(불투명 버튼): 'absolute z-20 hidden w-9 -translate-y-1/2 items-center justify-center rounded-md border border-input bg-card text-foreground shadow-md hover:bg-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:pointer-events-none disabled:opacity-0 sm:inline-flex'
        // 반투명 버튼(사용자 요청 "더 투명하게, 마우스를 올리면 진하게"):
        // - 평소: 바탕 card 60%(bg-card/60) + 뒤 포스터 흐림(backdrop-blur-sm), 테두리 input 40%로 포스터가 비쳐 보인다.
        //   바탕 투명도 하한이 60%인 이유: 가장 밝은 흰 포스터 위에서도 바탕이 충분히 어두워 화살표(foreground)와의 대비가 약 4:1로 3:1 기준을 넘는다(50%면 약 3:1로 아슬아슬하다).
        //   화살표 아이콘에도 어두운 그림자(drop-shadow-background)를 둘러 밝은 포스터 위 윤곽을 한 번 더 지킨다.
        // - 마우스를 올리거나(hover) 키보드 포커스가 오면(focus-visible): 바탕 accent 95%·테두리 input 100%·그림자 md로 거의 불투명해진다.
        //   hover 효과는 "진해지는" 꾸밈일 뿐 정보를 숨기지 않는다(평소에도 화살표가 보인다). 터치 기기 폭(640px 미만)에서는 버튼 자체가 숨겨진다.
        // - 전환은 0.2초로 부드럽게 하되 움직임 줄이기 설정에서는 끈다(motion-safe 접두사).
        'absolute z-20 hidden w-9 -translate-y-1/2 items-center justify-center rounded-md border border-input/40 bg-card/60 text-foreground shadow-sm backdrop-blur-sm hover:border-input hover:bg-accent/95 hover:shadow-md focus-visible:border-input focus-visible:bg-accent/95 focus-visible:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:pointer-events-none disabled:opacity-0 motion-safe:transition-[background-color,border-color,box-shadow] motion-safe:duration-200 sm:inline-flex',
        sizeClass,
        direction === 'prev' ? '-left-4' : '-right-4',
      )}
    >
      {/* 이전: className="size-5" */}
      <Icon aria-hidden="true" className="size-5 drop-shadow-sm drop-shadow-background" />
    </button>
  )
}
