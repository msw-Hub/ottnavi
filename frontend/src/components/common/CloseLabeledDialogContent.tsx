import type { ComponentProps } from 'react'
import { XIcon } from 'lucide-react'
import { Dialog as DialogPrimitive } from 'radix-ui'
import { Button } from '@/components/ui/button'
import { DialogContent } from '@/components/ui/dialog'

/**
 * 닫기 버튼의 접근 이름이 한국어 "닫기"인 대화상자 본문이다.
 *
 * 왜 만들었나: shadcn 원본 DialogContent의 우상단 X 버튼은 화면 낭독기용 글자가 영어 "Close"로 박혀 있다.
 * 원본(components/ui/dialog.tsx)은 수정하지 않는 규칙이라, 원본의 기본 닫기 버튼을 끄고(showCloseButton={false})
 * 같은 모양의 버튼을 한국어 이름으로 직접 그린다. 나머지 props와 동작은 DialogContent와 같다.
 */
export function CloseLabeledDialogContent({
  children,
  ...props
}: Omit<ComponentProps<typeof DialogContent>, 'showCloseButton'>) {
  return (
    <DialogContent showCloseButton={false} {...props}>
      {children}
      <DialogPrimitive.Close asChild>
        <Button variant="ghost" className="absolute top-1 right-1 size-11" size="icon-sm">
          <XIcon aria-hidden="true" />
          <span className="sr-only">닫기</span>
        </Button>
      </DialogPrimitive.Close>
    </DialogContent>
  )
}
