import { useEffect, useId, useRef, useState } from 'react'
import { ChevronDownIcon, HeartIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { rememberWishlistFocusTarget } from '@/hooks/useWishlistAddedDialog'
import { cn } from '@/lib/utils'
import { WISHLIST_PRIORITY_LABELS, type WishlistPriority } from '@/components/common/displayTypes'
import { PriorityIcon, PriorityRadioCards } from '@/components/common/WishlistPriority'

interface WishlistButtonProps {
  targetName: string // 무엇을 찜하는지 이름(예: "기생충", "시즌 2"). 화면 낭독기가 여러 찜 버튼을 구분하는 데 쓴다
  priority: WishlistPriority | null // 찜한 상태의 우선순위. 아직 찜하지 않았으면 null
  isPending: boolean // 요청 중인지(중복 클릭 방지)
  onAdd: () => void // "찜하기"를 눌렀을 때. 우선순위는 묻지 않고 기본값으로 바로 추가한다
  onChangePriority: (priority: WishlistPriority) => void // 우선순위를 바꿨을 때
  onRemove: () => void // "찜 해제"를 눌렀을 때
  variant?: 'default' | 'icon' // 모양. default는 글자가 있는 버튼, icon은 하트 아이콘만 있는 44x44 버튼(검색 카드 오른쪽 위처럼 좁은 자리용). 동작과 접근 가능한 이름은 같다
  panelAlign?: 'start' | 'end' // 설정 상자를 버튼의 왼쪽 끝(start)에 맞출지 오른쪽 끝(end)에 맞출지. 화면 오른쪽 끝에 놓인 버튼은 end로 해 상자가 화면 밖으로 나가지 않게 한다
}

/**
 * 찜 버튼 하나다. 찜 전에는 "찜하기", 찜한 뒤에는 "찜함 · 보고 싶음"으로 바뀌고, 누르면 우선순위 변경과 찜 해제를 고르는 상자가 열린다.
 *
 * 우선순위를 찜하기 전에 묻지 않는 이유: 처음 쓰는 사람은 "찜"부터 누르지 보고 싶은 정도부터 고르지 않는다.
 * 그래서 기본값(보고 싶음)으로 바로 추가하고, 바꾸고 싶을 때만 찜한 뒤 이 버튼에서 바꾼다.
 * 상자를 네이티브 radio로 만든 이유: 화살표·Tab 키 조작과 낭독기 지원이 브라우저에 이미 있어 직접 만든 메뉴보다 안전하다.
 * 상자는 버튼 아래에 떠서(absolute) 시즌 줄처럼 좁은 자리에서도 줄 높이를 밀어내지 않는다.
 * 닫는 방법: 버튼을 다시 누름 / Esc / 바깥을 누름 / 우선순위를 고름.
 * icon 모양은 글자만 하트 아이콘으로 바꾼 것이다. 아이콘만 있어도 aria-label이 "작품 찜하기"/"작품 찜함 · 보고 싶음"을 읽어 주고,
 * 누르면 같은 설정 상자가 아이콘 아래에 열려 우선순위 변경·해제 흐름이 그대로 유지된다.
 */
export function WishlistButton({
  targetName,
  priority,
  isPending,
  onAdd,
  onChangePriority,
  onRemove,
  variant = 'default',
  panelAlign = 'start',
}: WishlistButtonProps) {
  const baseId = useId()
  const panelId = `${baseId}-panel`
  const [isOpen, setIsOpen] = useState(false) // 상자가 열려 있는지(이 버튼 안에서만 의미가 있는 값이라 useState)
  const containerRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)

  // 열려 있는 동안 바깥을 누르면 닫는다. 닫혀 있을 때는 이벤트를 걸지 않는다(정리 함수로 항상 해제)
  useEffect(() => {
    if (!isOpen) return
    function handlePointerDown(event: PointerEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('pointerdown', handlePointerDown)
    return () => document.removeEventListener('pointerdown', handlePointerDown)
  }, [isOpen])

  // 상자 안의 버튼(우선순위 라디오·찜 해제)을 눌러 상자가 닫힌 뒤 포커스를 트리거 버튼으로 돌려줘야 하는지(Task 090 키보드 점검).
  // 값이 바뀌어도 다시 그릴 필요가 없어 useRef다. 눌린 버튼은 상자와 함께 사라지고, 요청 중에는 트리거도 disabled라 포커스를 잃는다.
  const shouldRestoreFocusRef = useRef(false)
  const wasPendingRef = useRef(false) // 직전 렌더에서 요청 중이었는지(요청이 "끝나는 순간"을 알아내려는 값)

  // 상자가 닫히고 요청이 끝나 트리거가 다시 눌릴 수 있게 되면 포커스를 돌려준다. 의존성은 이 두 값이 바뀔 때만 확인하려는 것이다.
  // 상자가 닫힌 직후에 한 번 주고(요청이 시작되기 전 짧은 순간), 요청이 시작되면 트리거가 disabled가 되어 브라우저가 포커스를 거두므로,
  // 요청이 끝나는 순간(요청 중 → 아님)에 다시 주고 마친다. 한 번만 주면 요청 중에 포커스가 사라져 Tab이 문서 맨 앞부터 다시 시작했다.
  // 트리거를 눌러 상자를 열 때 이 값을 꺼 두므로(아래 onClick), 요청이 없던 경우에도 나중에 포커스를 가로채지 않는다.
  useEffect(() => {
    const wasPending = wasPendingRef.current
    wasPendingRef.current = isPending
    if (!shouldRestoreFocusRef.current || isOpen || isPending) return
    // 요청이 길어 사용자가 그사이 Tab이나 클릭으로 포커스를 다른 곳에 두었다면 그 포커스를 뺏지 않는다(코드래빗 지적).
    // 포커스가 body이면 눌렀던 버튼이 사라져 포커스를 잃은 것이고, 이 버튼 안이면 되돌릴 필요가 없으니 그 두 경우만 복원한다.
    const active = document.activeElement
    if (active && active !== document.body && !containerRef.current?.contains(active)) {
      shouldRestoreFocusRef.current = false
      return
    }
    triggerRef.current?.focus()
    if (wasPending) shouldRestoreFocusRef.current = false
  }, [isOpen, isPending])

  const isIcon = variant === 'icon'
  // 아이콘 모양: 터치 영역 44x44px, 아이콘 20px. 배경 없는 ghost라 카드 위에서 하트만 보인다
  const triggerClassName = isIcon ? 'size-11 rounded-full p-0' : 'h-11'

  if (priority === null) {
    // 감싸는 div와 버튼의 자리를 "찜함" 상태(아래 return)와 똑같이 맞췄다(Task 090 키보드 점검).
    // 구조가 다르면 찜이 된 순간 React가 버튼을 새 요소로 다시 만들어, 팝업(WishlistAddedDialog)이 닫힐 때
    // Radix가 포커스를 돌려줄 "처음 눌렀던 버튼"이 사라진다. 그러면 키보드 사용자는 포커스를 잃고 Tab이 문서 맨 앞부터 다시 시작한다.
    // 구조가 같으면 같은 버튼 요소가 글자·이름만 바뀌어 유지되므로 포커스가 그대로 돌아온다.
    return (
      <div ref={containerRef} className="relative w-fit">
        <Button
          ref={triggerRef}
          type="button"
          variant={isIcon ? 'ghost' : 'outline'}
          className={triggerClassName}
          // 화면 글자는 "찜하기"이고, 접근 가능한 이름에 작품(시즌) 이름을 더한다(보이는 글자가 이름 안에 그대로 있어 음성 제어도 된다)
          aria-label={`${targetName} 찜하기`}
          onClick={(event) => {
            // 팝업이 닫힐 때 이 버튼으로 포커스를 돌려주려고 누른 버튼을 기억한다(hooks/useWishlistAddedDialog)
            rememberWishlistFocusTarget(event.currentTarget)
            onAdd()
          }}
          disabled={isPending}
        >
          <HeartIcon aria-hidden="true" className={cn(isIcon && 'size-5')} />
          {!isIcon && '찜하기'}
        </Button>
      </div>
    )
  }

  function handleKeyDown(event: React.KeyboardEvent) {
    if (event.key === 'Escape' && isOpen) {
      setIsOpen(false)
      triggerRef.current?.focus() // 닫은 뒤 키보드 위치가 사라지지 않게 버튼으로 돌려준다
    }
  }

  return (
    <div ref={containerRef} onKeyDown={handleKeyDown} className="relative w-fit">
      <Button
        ref={triggerRef}
        type="button"
        variant={isIcon ? 'ghost' : 'outline'}
        className={cn(triggerClassName, isIcon && 'text-primary')}
        aria-label={`${targetName} 찜함 · ${WISHLIST_PRIORITY_LABELS[priority]}`}
        aria-expanded={isOpen}
        aria-controls={panelId}
        onClick={() => {
          shouldRestoreFocusRef.current = false
          setIsOpen((current) => !current)
        }}
        disabled={isPending}
      >
        <HeartIcon aria-hidden="true" className={cn('fill-current', isIcon && 'size-5')} />
        {!isIcon && (
          <>
            찜함 ·
            {/* 지금 단계의 아이콘(별/북마크/시계)을 이름 앞에 둬 단계가 버튼에서도 보이게 한다(이름은 aria-label이 읽는다) */}
            <PriorityIcon priority={priority} />
            {WISHLIST_PRIORITY_LABELS[priority]}
            <ChevronDownIcon aria-hidden="true" />
          </>
        )}
      </Button>

      {isOpen && (
        <div
          id={panelId}
          role="group"
          aria-label={`${targetName} 찜 설정`}
          // w-72(288px): 단계마다 한 줄 설명이 들어가 이전 w-56(224px)보다 넓혔다. 375px 화면의 본문 폭(343px) 안에 들어간다
          className={cn(
            'absolute z-20 mt-1 grid w-72 max-w-[calc(100vw-2rem)] gap-1 rounded-lg border border-border bg-card p-2 shadow-lg',
            panelAlign === 'end' ? 'right-0' : 'left-0',
          )}
        >
          {/* 이전: 이름만 있는 라디오 3줄(모양이 같아 단계 차이를 느끼기 어려웠다). 지금: 아이콘·모양·한 줄 설명이 있는 라디오 카드 3장 */}
          {/* <fieldset className="grid gap-0.5"> ... WISHLIST_PRIORITY_OPTIONS.map(라디오 + 이름) ... </fieldset> */}
          <PriorityRadioCards
            legend="우선순위 · 계산이 챙기는 순서"
            value={priority}
            onChange={(nextPriority) => {
              shouldRestoreFocusRef.current = true
              onChangePriority(nextPriority)
              setIsOpen(false)
            }}
          />
          <Button
            type="button"
            variant="ghost"
            className="h-11 justify-start"
            onClick={() => {
              shouldRestoreFocusRef.current = true
              onRemove()
              setIsOpen(false)
            }}
          >
            찜 해제
            <span className="sr-only"> ({targetName})</span>
          </Button>
        </div>
      )}
    </div>
  )
}
