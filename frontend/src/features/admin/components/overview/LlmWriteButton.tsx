import type { ComponentProps } from 'react'
import { DemoLockedButton } from '@/components/common/DemoLockedButton'
import { DEMO_READONLY_NOTE_ID } from '@/components/common/DemoReadOnlyHint'
import { Button } from '@/components/ui/button'

interface LlmWriteButtonProps extends Omit<ComponentProps<typeof Button>, 'disabled'> {
  blockedReason?: string | null // 지금 쓸 수 없는 이유 문장. 있으면 버튼이 비활성이 된다(데모 자물쇠보다 우선)
  blockedReasonId?: string // 그 이유 문장이 화면에 놓인 요소의 id. 낭독기가 버튼과 함께 읽게 aria-describedby로 잇는다
  isBusy?: boolean // 요청을 보내는 중이라 막힌 상태
}

/**
 * "쓰기" 버튼(번역 생성, 일괄 시작, 반영, 삭제, 되돌리기)이 함께 쓰는 버튼이다.
 *
 * 막는 순서: 1. 쓸 수 없는 이유가 있으면(키 없음, 일괄 실행 중 등) 평범한 비활성 버튼 2. 아니면 DemoLockedButton
 * (데모 계정이면 자물쇠 버튼이라 눌러도 요청이 나가지 않고, 아니면 평범한 버튼).
 * 키가 없는 경우를 데모 자물쇠보다 앞에 두는 이유: 데모 계정에게도 "기능이 꺼져 있다"는 사실이 더 근본적인 이유라서다.
 * 이유 문장은 호출하는 쪽이 화면에 보이는 글자로 따로 놓는다(색이나 비활성 모양만으로 알리지 않는다).
 */
export function LlmWriteButton({
  blockedReason,
  blockedReasonId,
  isBusy = false,
  children,
  ...props
}: LlmWriteButtonProps) {
  if (blockedReason || isBusy) {
    return (
      <Button
        {...props}
        disabled
        aria-busy={isBusy || undefined}
        aria-describedby={blockedReason ? blockedReasonId : undefined}
        title={blockedReason ?? props.title}
      >
        {children}
      </Button>
    )
  }
  return (
    <DemoLockedButton {...props} lockedDescriptionId={DEMO_READONLY_NOTE_ID}>
      {children}
    </DemoLockedButton>
  )
}
