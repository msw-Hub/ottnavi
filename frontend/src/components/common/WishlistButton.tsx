import { useEffect, useId, useRef, useState } from 'react'
import { ChevronDownIcon, HeartIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import {
  WISHLIST_PRIORITY_LABELS,
  WISHLIST_PRIORITY_OPTIONS,
  type WishlistPriority,
} from '@/components/common/displayTypes'

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

  const isIcon = variant === 'icon'
  // 아이콘 모양: 터치 영역 44x44px, 아이콘 20px. 배경 없는 ghost라 카드 위에서 하트만 보인다
  const triggerClassName = isIcon ? 'size-11 rounded-full p-0' : 'h-10'

  if (priority === null) {
    return (
      <Button
        type="button"
        variant={isIcon ? 'ghost' : 'outline'}
        className={triggerClassName}
        // 화면 글자는 "찜하기"이고, 접근 가능한 이름에 작품(시즌) 이름을 더한다(보이는 글자가 이름 안에 그대로 있어 음성 제어도 된다)
        aria-label={`${targetName} 찜하기`}
        onClick={onAdd}
        disabled={isPending}
      >
        <HeartIcon aria-hidden="true" className={cn(isIcon && 'size-5')} />
        {!isIcon && '찜하기'}
      </Button>
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
        onClick={() => setIsOpen((current) => !current)}
        disabled={isPending}
      >
        <HeartIcon aria-hidden="true" className={cn('fill-current', isIcon && 'size-5')} />
        {!isIcon && (
          <>
            찜함 · {WISHLIST_PRIORITY_LABELS[priority]}
            <ChevronDownIcon aria-hidden="true" />
          </>
        )}
      </Button>

      {isOpen && (
        <div
          id={panelId}
          role="group"
          aria-label={`${targetName} 찜 설정`}
          className={cn(
            'absolute z-20 mt-1 grid w-56 gap-1 rounded-lg border border-border bg-card p-2 shadow-lg',
            panelAlign === 'end' ? 'right-0' : 'left-0',
          )}
        >
          <fieldset className="grid gap-0.5">
            <legend className="px-2 pb-1 text-xs text-muted-foreground">우선순위</legend>
            {WISHLIST_PRIORITY_OPTIONS.map((option) => (
              <label
                key={option.value}
                className="flex min-h-10 cursor-pointer items-center gap-2 rounded-md px-2 text-sm hover:bg-muted has-focus-visible:outline-2 has-focus-visible:outline-ring"
              >
                <input
                  type="radio"
                  name={`${baseId}-priority`}
                  value={option.value}
                  checked={priority === option.value}
                  onChange={() => {
                    onChangePriority(option.value)
                    setIsOpen(false)
                  }}
                  className="size-4 accent-primary"
                />
                {option.label}
              </label>
            ))}
          </fieldset>
          <Button
            type="button"
            variant="ghost"
            className="h-10 justify-start"
            onClick={() => {
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
