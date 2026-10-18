import {
  useEffect,
  useEffectEvent,
  useId,
  useRef,
  useState,
  type MouseEvent as ReactMouseEvent,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from 'react'
import { ChevronLeftIcon, ChevronRightIcon, CircleAlertIcon, RotateCwIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

// 카드 한 장의 폭. 홈 가로 줄(features/home의 ScrollRailItem)과 같은 값이다:
// 모바일 128px(375px 화면에 카드 2장 반이 보여 옆에 더 있다는 것이 드러난다) → 640px 이상 144px → 1024px 이상 160px(본문 폭 992px에 5장 반)
const RAIL_ITEM_WIDTH = 'w-32 sm:w-36 lg:w-40'

// 오른쪽 끝에서 이만큼 앞에 닿으면 다음 쪽을 미리 부른다(카드 두 장쯤). 끝에 딱 닿은 뒤 부르면 빈 끝을 잠깐 보게 되기 때문이다
const PRELOAD_MARGIN_PX = 320

// 마우스를 이만큼(px) 넘게 움직여야 "끌기"로 본다. 클릭하려다 살짝 흔들린 손을 끌기로 오인해 링크가 안 눌리는 것을 막는 값이다
const DRAG_THRESHOLD_PX = 5

// 줄의 scroll-px-4(왼쪽 맞춤 여백 16px)와 같은 값. 끌기를 놓을 때 카드 왼쪽 끝 위치를 직접 계산하는 데 쓴다
const SNAP_SCROLL_PADDING_PX = 16

// 처음·끝에 닿았다고 보는 오차(px). scrollLeft·scrollWidth는 소수 픽셀 때문에 끝에서도 1~2px 어긋날 수 있다
const EDGE_TOLERANCE_PX = 2

// 움직임 줄이기 설정을 켠 사용자인지(부드러운 이동 대신 바로 이동해야 한다)
function prefersReducedMotion() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
}

interface ExclusiveTitleRailProps {
  label: string // 목록 이름(낭독기가 "넷플릭스 독점 작품, 목록, 항목 24개"처럼 읽는다)
  itemCount: number // 지금 줄에 있는 작품 수. 늘어나면(다음 쪽을 받으면) 화살표의 끝 여부를 다시 잰다
  hasMore: boolean // 서버에 다음 쪽이 남았는지
  isLoadingMore: boolean // 다음 쪽을 받는 중인지
  isLoadMoreError: boolean // 다음 쪽 요청이 실패했는지(받아 둔 목록은 그대로 둔다)
  onLoadMore: () => void // 다음 쪽을 부른다(끝 근처에 닿았을 때, 실패 뒤 "다시 시도"를 눌렀을 때)
  children: ReactNode // <li> 항목들(ExclusiveTitleRailItem)
}

/**
 * 독점 작품을 가로 한 줄로 늘어놓고 옆으로 넘겨 보는 목록이다(SCR-03). 끝에 가까워지면 다음 쪽을 스스로 불러와 뒤에 이어 붙인다.
 *
 * 홈 가로 줄(features/home/components/ScrollRail)과 같은 모양·조작법이다. 기능 폴더끼리 import하지 않는 규칙(frontend.md) 때문에 따로 두었고,
 * 다른 점은 "끝에 닿으면 다음 쪽 불러오기"와 줄 끝의 불러오는 중·실패 표시뿐이다. 두 줄을 하나로 합칠지는 보고서의 결정 질문이다.
 *
 * 조작 방법:
 * - 손가락·트랙패드: 옆으로 밀면 카드 왼쪽 끝에서 멈춘다(snap-x snap-mandatory).
 * - 마우스 끌기: 줄을 왼쪽 버튼으로 누른 채 좌우로 끌면 따라 움직이고(5px 넘게 움직여야 끌기로 본다), 놓으면 가장 가까운 카드 왼쪽 끝으로 맞춘다.
 *   끌기 동안은 snap을 꺼 끌림이 끊기지 않게 하고, 끌고 난 직후의 click은 막아 카드 링크로 가지 않게 한다. 터치는 브라우저 기본 동작에 맡긴다.
 * - 가로 스크롤바는 숨겼다(화살표·끌기·터치로 넘기고, 위치는 카드의 순위 숫자로 가늠한다). 스크롤바가 없어도 Tab 포커스 이동에 따른 자동 넘김은 그대로 동작한다.
 * - 마우스 휠: Shift+휠이 브라우저 기본으로 옆으로 넘긴다. 세로 휠을 가로로 바꾸지 않은 이유: 줄 위에 마우스가 있을 때 페이지가
 *   아래로 내려가지 않는 "스크롤 갇힘"이 생겨 더 불편하다(넷플릭스 웹도 세로 휠을 가로로 바꾸지 않는다).
 * - 키보드: Tab으로 카드 제목 링크를 옮기면 브라우저가 그 카드가 보이게 옆으로 넘긴다. 화살표 버튼도 Tab으로 닿고 Enter·Space로 누른다.
 * - 마우스(640px 이상): 양 끝 화살표 버튼으로 보이는 폭의 80%씩 넘긴다. 처음·끝에 닿으면 그쪽 버튼은 비활성이 되고 화면에서도 사라진다.
 *   모바일에서는 손가락으로 미는 것이 자연스러워 버튼을 숨긴다.
 * - 움직임 줄이기(prefers-reduced-motion)를 켠 사용자에게는 부드러운 스크롤 대신 바로 넘긴다.
 *
 * 다음 쪽 불러오기(IntersectionObserver 센티널): 줄 맨 끝에 눈에 보이지 않는 표시(센티널)를 두고, 그것이 줄의 보이는 범위(+320px)에
 * 들어오면 다음 쪽을 부른다. 스크롤 이벤트마다 위치를 계산하는 방법보다 가볍고, 화살표·손가락·키보드 어느 방법으로 넘겨도 똑같이 동작한다.
 */
export function ExclusiveTitleRail({
  label,
  itemCount,
  hasMore,
  isLoadingMore,
  isLoadMoreError,
  onLoadMore,
  children,
}: ExclusiveTitleRailProps) {
  const listId = useId()
  const listRef = useRef<HTMLUListElement>(null) // 옆으로 밀리는 줄
  const sentinelRef = useRef<HTMLLIElement>(null) // 줄 맨 끝의 보이지 않는 표시(여기 닿으면 다음 쪽)
  const [edge, setEdge] = useState({ isAtStart: true, isAtEnd: false }) // 화살표 버튼 비활성 판단용(화면 표시 전용 상태)

  // 지금 스크롤 위치가 처음·끝인지 다시 잰다. 2px 여유(EDGE_TOLERANCE_PX)는 scrollLeft가 소수일 수 있어 끝에 닿아도 1~2px 모자라게 나오는 경우를 막는다
  // (이전: 1px. 화살표 클릭·끌기·터치 어느 방법으로 닿아도 이 한 함수가 판정하고, 스크롤·크기 변화·항목 추가 때마다 다시 불린다)
  function updateEdge() {
    const list = listRef.current
    if (!list) return
    setEdge({
      isAtStart: list.scrollLeft <= EDGE_TOLERANCE_PX,
      isAtEnd: list.scrollLeft + list.clientWidth >= list.scrollWidth - EDGE_TOLERANCE_PX,
    })
  }

  // 처음 그린 뒤, 작품 수가 늘었을 때(다음 쪽이 붙으면 줄이 길어져 "끝"이 아니게 된다), 창 크기가 바뀔 때 끝 여부를 다시 잰다.
  // 홈 줄과 달리 itemCount를 의존성에 둔 이유: 홈 줄은 내용이 바뀌면 새로 만들지만, 이 줄은 같은 줄 뒤에 작품이 이어 붙기 때문이다.
  // ResizeObserver가 없는 환경(테스트용 jsdom)에서는 잴 때만 잰다.
  useEffect(() => {
    updateEdge()
    const list = listRef.current
    if (!list || typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(() => updateEdge())
    observer.observe(list)
    return () => observer.disconnect()
  }, [itemCount])

  // 센티널이 보이는 범위에 들어왔을 때 할 일. useEffectEvent로 둔 이유: 실패 여부·부르는 함수는 매번 최신 값을 읽어야 하지만,
  // 그 값이 바뀔 때마다 관찰기를 새로 만들 필요는 없다(React 19.2 공식 문서의 "Effect Event").
  // 실패한 상태에서는 스스로 다시 부르지 않는다: 계속 실패하는 동안 요청이 반복되지 않게, 줄 끝의 "다시 시도"를 사람이 누를 때만 다시 부른다
  const handleSentinelVisible = useEffectEvent(() => {
    if (isLoadMoreError) return
    onLoadMore()
  })

  // 센티널 관찰기. 다음 쪽이 있고 받는 중이 아닐 때만 켠다.
  // isLoadingMore를 의존성에 둔 이유: 한 쪽을 다 받으면 관찰기를 새로 만들고, 새 관찰기는 처음에 한 번 "지금 보이는지"를 알려 준다.
  // 그래서 넓은 화면에서 받은 뒤에도 센티널이 여전히 보이면 사용자가 움직이지 않아도 다음 쪽을 이어 부른다.
  // 받는 중에는 관찰기를 끄는 이유: TanStack Query의 fetchNextPage는 받는 중에 다시 부르면 앞 요청을 취소하고 새로 보내기 때문이다(같은 쪽 중복 요청 방지).
  useEffect(() => {
    const list = listRef.current
    const sentinel = sentinelRef.current
    if (!hasMore || isLoadingMore || !list || !sentinel) return
    if (typeof IntersectionObserver === 'undefined') return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) handleSentinelVisible()
      },
      // root를 줄로 둬 "줄 안에서 보이는지"를 본다. 오른쪽으로만 범위를 넓혀 끝에 닿기 조금 전에 부른다
      { root: list, rootMargin: `0px ${PRELOAD_MARGIN_PX}px 0px 0px` },
    )
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasMore, isLoadingMore])

  // 화살표 버튼: 보이는 폭의 80%만큼 넘긴다(다음 화면 첫 카드가 이전 화면 끝 카드와 조금 겹쳐 이어 보기 쉽다)
  function scrollByPage(direction: -1 | 1) {
    const list = listRef.current
    if (!list) return
    list.scrollBy({
      left: direction * list.clientWidth * 0.8,
      behavior: prefersReducedMotion() ? 'auto' : 'smooth',
    })
  }

  // 마우스 드래그 상태. 렌더에 쓰이지 않는 값이라 state가 아닌 ref에 둔다(움직일 때마다 다시 그리지 않으려는 것).
  // startX/startScrollLeft: 누른 순간의 마우스 x와 줄 스크롤 위치. isDragging: 임계값(5px)을 넘겨 "끌기"로 확정됐는지
  const dragRef = useRef<{
    pointerId: number
    startX: number
    startScrollLeft: number
    isDragging: boolean
  } | null>(null)
  // 끌기가 끝난 직후 따라오는 click을 한 번 막기 위한 표시. 끌었는데 놓은 자리의 카드 링크가 눌려 상세로 가버리는 사고를 막는다
  const suppressClickRef = useRef(false)

  // 1. 마우스 왼쪽 버튼을 누른 순간: 기준 위치만 적어 둔다. 여기서 바로 포인터 캡처를 하면 click의 대상이 줄로 바뀌어 카드 링크가 안 눌린다
  function handlePointerDown(event: ReactPointerEvent<HTMLUListElement>) {
    if (event.pointerType !== 'mouse' || event.button !== 0) return // 터치·펜은 브라우저 기본 스와이프가 이미 동작한다
    dragRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startScrollLeft: event.currentTarget.scrollLeft,
      isDragging: false,
    }
  }

  // 2. 누른 채 움직이는 중: 5px을 넘기면 "끌기"로 확정하고(그 전엔 그냥 클릭일 수 있다) 줄을 마우스 움직임만큼 밀어 준다
  function handlePointerMove(event: ReactPointerEvent<HTMLUListElement>) {
    const drag = dragRef.current
    const list = event.currentTarget
    if (!drag || drag.pointerId !== event.pointerId) return
    const deltaX = event.clientX - drag.startX
    if (!drag.isDragging) {
      if (Math.abs(deltaX) < DRAG_THRESHOLD_PX) return
      drag.isDragging = true
      // 끌기가 확정된 뒤에야 캡처한다: 줄 밖으로 마우스가 나가도 끌기가 이어지게 하려는 것
      list.setPointerCapture?.(event.pointerId)
      // scroll-snap이 켜져 있으면 끄는 도중 카드 끝으로 계속 되돌려져 끌림이 뚝뚝 끊긴다. 끌기 동안만 끈다(인라인 값이 클래스보다 우선)
      list.style.scrollSnapType = 'none'
      list.dataset.dragging = 'true' // 커서를 grabbing으로 바꾸는 표시(CSS data 속성 변형)
    }
    list.scrollLeft = drag.startScrollLeft - deltaX
  }

  // 3. 놓았거나 취소됐을 때: 끌기였다면 가장 가까운 카드 왼쪽 끝으로 맞추고 snap을 되살린다. 클릭이었다면 아무것도 하지 않는다
  function finishDrag(event: ReactPointerEvent<HTMLUListElement>) {
    const drag = dragRef.current
    const list = event.currentTarget
    if (!drag || drag.pointerId !== event.pointerId) return
    dragRef.current = null
    if (!drag.isDragging) return

    list.releasePointerCapture?.(event.pointerId)
    delete list.dataset.dragging

    // 끌기 직후의 click 한 번을 막는다. click은 pointerup 바로 뒤 같은 턴에 오므로, 다음 턴(setTimeout 0)에 표시를 지운다
    suppressClickRef.current = true
    setTimeout(() => {
      suppressClickRef.current = false
    }, 0)

    // 가장 가까운 카드 왼쪽 끝 찾기. 카드의 왼쪽(줄 안 좌표) - 줄의 왼쪽 안쪽 여백(scroll-px-4 = 16px)이 맞춘 뒤의 scrollLeft다
    // 맞춤 위치는 [0, 최대 스크롤 위치]로 자른다. 자르지 않으면 끝 근처에서 마지막 카드의 왼쪽 끝(최대 위치보다 오른쪽)이 "가장 가까운 후보"에서
    // 밀려나, 최대 위치보다 왼쪽의 앞 카드 경계가 뽑혀 줄이 뒤로 되돌아갔다(브라우저의 snap도 같은 이치로 끝 위치를 하나의 맞춤 위치로 친다)
    const maxScrollLeft = Math.max(0, list.scrollWidth - list.clientWidth)
    const listLeft = list.getBoundingClientRect().left
    let target = Math.min(Math.max(list.scrollLeft, 0), maxScrollLeft)
    let nearestGap = Infinity
    for (const child of Array.from(list.children)) {
      if (!child.classList.contains('snap-start')) continue // 센티널·불러오는 표시는 맞춤 대상이 아니다
      const snapLeft = Math.min(
        Math.max(
          child.getBoundingClientRect().left - listLeft + list.scrollLeft - SNAP_SCROLL_PADDING_PX,
          0,
        ),
        maxScrollLeft,
      )
      const gap = Math.abs(snapLeft - list.scrollLeft)
      if (gap < nearestGap) {
        nearestGap = gap
        target = snapLeft
      }
    }

    // 맞추는 동안은 snap을 계속 꺼 두고(부드러운 이동 도중 snap이 끼어들어 튀는 것 방지), 이동이 끝나면 되살린다
    // (그 사이 새 끌기가 시작됐다면 되살리지 않는다. 늦게 온 scrollend가 새 끌기의 snap 끄기를 풀어 버리는 것을 막는다)
    const restoreSnap = () => {
      if (dragRef.current?.isDragging) return
      list.style.removeProperty('scroll-snap-type')
    }
    if (prefersReducedMotion() || typeof list.scrollTo !== 'function') {
      list.scrollLeft = target
      restoreSnap()
      updateEdge() // 바로 이동하는 길은 scroll 이벤트를 기다리지 않고 화살표 상태를 바로 다시 잰다
      return
    }
    list.scrollTo({ left: target, behavior: 'smooth' })
    list.addEventListener('scrollend', restoreSnap, { once: true })
    // scrollend를 모르는 브라우저·이미 그 자리인 경우 대비. 늦어도 0.6초 뒤에는 되살린다
    setTimeout(restoreSnap, 600)
  }

  // 끌기 직후의 click은 캡처 단계에서 막는다(카드 링크로 가지 않게)
  function handleClickCapture(event: ReactMouseEvent<HTMLUListElement>) {
    if (!suppressClickRef.current) return
    event.preventDefault()
    event.stopPropagation()
  }

  return (
    <div className="relative">
      <RailArrowButton
        direction="prev"
        label="이전 작품 보기"
        controls={listId}
        isDisabled={edge.isAtStart}
        onClick={() => scrollByPage(-1)}
      />
      {/* -mx-4 px-4: 모바일에서 줄을 화면 끝까지 늘여 오른쪽 카드가 잘린 모양으로 보이게 한다(옆에 더 있다는 신호).
          py-2는 카드 포커스 링(2px + 간격 2px)이 가로 스크롤 칸에 잘리지 않게 하는 여백이다(홈 줄과 같은 값) */}
      <ul
        id={listId}
        ref={listRef}
        aria-label={label}
        onScroll={updateEdge}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={finishDrag}
        onPointerCancel={finishDrag}
        onClickCapture={handleClickCapture}
        onDragStart={(event) => event.preventDefault()}
        className="-mx-4 flex cursor-grab snap-x snap-mandatory scroll-px-4 gap-3 overflow-x-auto overscroll-x-contain px-4 py-2 select-none [scrollbar-width:none] data-[dragging]:cursor-grabbing data-[dragging]:[&_a]:cursor-grabbing sm:gap-4 [&::-webkit-scrollbar]:hidden"
      >
        {children}

        {/* 다음 쪽을 받는 중: 줄 끝에 카드 모양 회색 칸 2개. 칸은 장식이라 숨기고, 무엇을 하는지는 role="status" 글자로 알린다 */}
        {isLoadingMore && (
          <li className="flex shrink-0 gap-3 sm:gap-4">
            <p role="status" className="sr-only">
              다음 작품을 불러오고 있어요
            </p>
            <RailCardSkeleton />
            <RailCardSkeleton />
          </li>
        )}

        {/* 다음 쪽 요청 실패: 줄 끝에 카드 한 장 크기의 안내와 "다시 시도". 받아 둔 카드는 지우지 않는다.
            경고 아이콘 + 글자로 알리고(색만으로 구분하지 않음), 버튼은 h-11(44px) 터치 크기다 */}
        {isLoadMoreError && !isLoadingMore && (
          <li className={cn('shrink-0 snap-start', RAIL_ITEM_WIDTH)}>
            <div
              role="alert"
              className="grid aspect-2/3 w-full content-center justify-items-center gap-2 rounded-lg border border-dashed border-input px-2 text-center"
            >
              <CircleAlertIcon aria-hidden="true" className="size-6 text-destructive" />
              <p className="text-xs break-keep text-muted-foreground">
                다음 작품을 불러오지 못했어요
              </p>
              <Button type="button" variant="outline" className="h-11" onClick={onLoadMore}>
                <RotateCwIcon aria-hidden="true" />
                다시 시도
              </Button>
            </div>
          </li>
        )}

        {/* 센티널: 폭 1px의 보이지 않는 표시. 낭독기 목록 항목 수에 섞이지 않게 숨긴다(role="presentation") */}
        {hasMore && (
          <li ref={sentinelRef} role="presentation" aria-hidden="true" className="w-px shrink-0" />
        )}
      </ul>
      <RailArrowButton
        direction="next"
        label="다음 작품 보기"
        controls={listId}
        isDisabled={edge.isAtEnd}
        onClick={() => scrollByPage(1)}
      />
    </div>
  )
}

// 가로 줄의 항목 하나. 폭을 고정(shrink-0)해 카드 크기가 화면마다 흔들리지 않게 하고, 카드 왼쪽 끝에서 멈추게 한다(snap-start)
export function ExclusiveTitleRailItem({ children }: { children: ReactNode }) {
  return <li className={cn('shrink-0 snap-start', RAIL_ITEM_WIDTH)}>{children}</li>
}

// 카드 모양 회색 칸 하나(포스터 + 제목 줄 + 정보 줄). 깜빡임은 움직임 줄이기를 켜지 않은 사용자에게만 보인다(motion-safe)
function RailCardSkeleton() {
  return (
    <div aria-hidden="true" className={cn('grid shrink-0 content-start gap-2', RAIL_ITEM_WIDTH)}>
      <div className="aspect-2/3 w-full rounded-lg bg-muted motion-safe:animate-pulse" />
      <div className="h-3.5 w-4/5 rounded-full bg-muted motion-safe:animate-pulse" />
      <div className="h-3 w-1/2 rounded-full bg-muted motion-safe:animate-pulse" />
    </div>
  )
}

/**
 * 첫 쪽을 받는 동안 줄 자리에 보이는 회색 카드 6장이다(격자 대신 가로 한 줄 모양으로 맞췄다).
 * 넘치는 칸은 잘려 보이게(overflow-hidden) 해 실제 줄과 같은 "옆에 더 있다" 모양을 미리 보인다.
 */
export function ExclusiveTitleRailSkeleton({ message }: { message: string }) {
  return (
    <div>
      <p role="status" className="sr-only">
        {message}
      </p>
      <div className="-mx-4 flex gap-3 overflow-hidden px-4 py-2 sm:gap-4">
        {[0, 1, 2, 3, 4, 5].map((index) => (
          <RailCardSkeleton key={index} />
        ))}
      </div>
    </div>
  )
}

interface RailArrowButtonProps {
  direction: 'prev' | 'next'
  label: string // 버튼 이름(아이콘만 있어 낭독기용 이름이 필요하다)
  controls: string // 이 버튼이 움직이는 목록의 id(aria-controls)
  isDisabled: boolean // 그쪽 끝에 닿아 더 넘길 수 없으면 true
  onClick: () => void
}

// 줄 양 끝의 원형 화살표 버튼(640px 이상에서만). 포스터 높이의 가운데쯤(top-1/3)에 떠 있다(홈 줄과 같은 모양)
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
