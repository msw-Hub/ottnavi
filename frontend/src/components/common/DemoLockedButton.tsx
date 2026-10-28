import type { ComponentProps, MouseEvent, ReactNode } from 'react'
import { LockIcon } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { useIsDemo } from '@/hooks/useIsDemo'
import { cn } from '@/lib/utils'

// 막힌 버튼을 눌렀을 때 띄우는 토스트의 고정 id. 같은 id로 다시 띄우면 sonner가 새로 쌓지 않고 기존 토스트를 갱신한다(여러 번 눌러도 하나만 보인다)
const DEMO_LOCKED_TOAST_ID = 'demo-read-only'

// 막힌 버튼을 눌렀을 때 기본 안내 문장(관리자 화면)
const DEFAULT_LOCKED_MESSAGE = '데모 계정은 조회만 할 수 있어요'

// 변형(variant)별로 마우스를 올렸을 때 바뀌는 바탕을 원래 바탕으로 되돌린다. 막힌 버튼이 "눌릴 것처럼" 반응하지 않게 하려는 것이다.
// 값은 components/ui/button.tsx의 각 변형 기본 바탕과 같다(원본은 고치지 않고 덮어쓴다)
const LOCKED_HOVER_RESET: Record<string, string> = {
  default: 'aria-disabled:hover:bg-primary',
  outline: 'aria-disabled:hover:bg-background aria-disabled:hover:text-inherit',
  secondary: 'aria-disabled:hover:bg-secondary',
  ghost: 'aria-disabled:hover:bg-transparent aria-disabled:hover:text-inherit',
  destructive: 'aria-disabled:hover:bg-destructive/10',
  link: 'aria-disabled:hover:no-underline',
}

interface DemoLockedButtonProps extends ComponentProps<typeof Button> {
  icon?: ReactNode // 글자 앞 아이콘. 데모 계정이면 자물쇠 아이콘으로 바뀐다(아이콘이 없던 버튼도 자물쇠가 붙는다)
  lockedDescriptionId?: string // 데모 계정일 때 aria-describedby로 이을 "왜 막혔는지" 문장의 id(조회 전용 안내 띠나 버튼 옆 안내)
  lockedMessage?: string // 데모 계정이 눌렀을 때 토스트 문장. 기본은 "데모 계정은 조회만 할 수 있어요"
}

/**
 * 데모 계정(DEMO)에게만 막히는 버튼이다(Task 109). 데모 계정이 아니면 shadcn Button과 똑같이 동작한다.
 *
 * 데모 계정일 때 disabled 대신 aria-disabled를 쓰는 이유:
 * - disabled 버튼은 키보드 포커스를 받지 못해, 낭독기 사용자는 버튼이 있다는 것도 "왜 막혔는지"도 알 수 없다.
 *   aria-disabled는 포커스는 받되 "사용할 수 없음"으로 읽히고, aria-describedby로 이유 문장까지 읽힌다.
 * - 터치 화면에서는 안내 문장을 못 볼 수 있어, 누르면 이유를 토스트로 알린다(고정 id라 여러 번 눌러도 하나만 보인다).
 * 클릭은 preventDefault로 막아 폼 제출(Enter 포함)·대화상자 닫힘이 일어나지 않고, 넘겨받은 onClick도 부르지 않는다(요청이 나가지 않는다).
 *
 * 보안상 이 버튼은 편의 기능이다. 실제로 쓰기를 막는 것은 서버다(데모 계정의 관리자 쓰기·탈퇴·알림 설정 변경은 403).
 * shadcn 원본(components/ui/button)은 고치지 않고 감싸서 쓴다(frontend.md).
 */
export function DemoLockedButton({
  icon,
  lockedDescriptionId,
  lockedMessage = DEFAULT_LOCKED_MESSAGE,
  className,
  variant,
  onClick,
  children,
  ...props
}: DemoLockedButtonProps) {
  const isDemo = useIsDemo()

  // 데모 계정이 아니면 평범한 버튼이다
  if (!isDemo) {
    return (
      <Button className={className} variant={variant} onClick={onClick} {...props}>
        {icon}
        {children}
      </Button>
    )
  }

  function handleLockedClick(event: MouseEvent<HTMLButtonElement>) {
    // 1. 기본 동작(폼 제출·대화상자 닫힘)을 막는다
    event.preventDefault()
    // 2. 왜 안 되는지 알린다. 넘겨받은 onClick은 부르지 않는다
    toast.info(lockedMessage, { id: DEMO_LOCKED_TOAST_ID })
  }

  // 원래 설명(aria-describedby)이 있으면 지우지 않고 막힌 이유를 뒤에 덧붙인다
  const describedBy =
    [props['aria-describedby'], lockedDescriptionId].filter(Boolean).join(' ') || undefined

  return (
    <Button
      {...props}
      variant={variant}
      aria-disabled="true"
      aria-describedby={describedBy}
      onClick={handleLockedClick}
      className={cn(
        className,
        'aria-disabled:cursor-not-allowed aria-disabled:opacity-70 aria-disabled:active:translate-y-0',
        LOCKED_HOVER_RESET[variant ?? 'default'],
      )}
    >
      <LockIcon aria-hidden="true" />
      {children}
    </Button>
  )
}
