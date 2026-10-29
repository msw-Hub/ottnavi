import type { ReactNode } from 'react'
import { InfoIcon, LockIcon } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { useIsDemo } from '@/hooks/useIsDemo'
import { cn } from '@/lib/utils'

// 관리자 화면 위 조회 전용 안내 띠의 id. 표의 행 안처럼 옆에 안내를 둘 자리가 없는 막힌 버튼이 aria-describedby로 이 띠를 가리킨다
export const DEMO_READONLY_NOTE_ID = 'demo-readonly-note'

interface DemoReadOnlyHintProps {
  variant: 'banner' | 'inline' // banner: 관리자 화면 맨 위 안내 띠 / inline: 막힌 버튼 옆의 짧은 한 줄
  id: string // 막힌 버튼이 aria-describedby로 가리킬 id
  children?: ReactNode // inline의 문장(예: "데모 계정은 저장할 수 없어요"). banner는 정해진 문장을 쓰므로 넘기지 않는다
  className?: string // 놓일 자리에 맞춘 여백·정렬
}

/**
 * 데모 계정(DEMO)에게만 보이는 "조회 전용" 안내다(Task 109). 데모 계정이 아니면 아무것도 그리지 않는다.
 *
 * - banner: 관리자 화면 메뉴 아래에 한 번 둔다. 등록 폼은 열 수 있지만 저장·삭제·실행은 안 된다는 것을 먼저 알린다.
 *   role="note"(보충 설명)라 화면을 옮길 때마다 알림처럼 읽히지 않는다(shadcn Alert의 기본 role="alert"를 덮어쓴다).
 * - inline: 막힌 버튼 바로 옆·대화상자 안에 둔다. 자물쇠 아이콘 + 글자라 색만으로 알리지 않는다.
 */
export function DemoReadOnlyHint({ variant, id, children, className }: DemoReadOnlyHintProps) {
  const isDemo = useIsDemo()
  if (!isDemo) return null

  if (variant === 'banner') {
    return (
      <Alert role="note" id={id} className={className}>
        <InfoIcon aria-hidden="true" />
        <AlertTitle>조회 전용</AlertTitle>
        <AlertDescription className="break-keep">
          데모 계정은 관리자 화면을 조회만 할 수 있어요. 등록 폼은 열어 볼 수 있지만
          저장·삭제·실행은 되지 않아요.
        </AlertDescription>
      </Alert>
    )
  }

  return (
    <p
      id={id}
      className={cn(
        'flex items-center gap-1.5 text-sm break-keep text-muted-foreground',
        className,
      )}
    >
      <LockIcon aria-hidden="true" className="size-4 shrink-0" />
      {children}
    </p>
  )
}
