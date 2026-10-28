import type { RefObject } from 'react'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { DemoLockedButton } from '@/components/common/DemoLockedButton'
import { DemoReadOnlyHint } from '@/components/common/DemoReadOnlyHint'
import { COLLECT_TIER_LABELS } from '@/features/admin/collectRules'
import type { CollectExecution } from '@/api/generated/model'
import { useIsDemo } from '@/hooks/useIsDemo'
import { formatIsoDate } from '@/lib/format'

// 데모 계정에게 [복구]가 막힌 이유 문장의 id
const RECOVER_LOCK_NOTE_ID = 'recover-run-demo-note'

interface RecoverRunDialogProps {
  execution: CollectExecution | null // 복구할 중단됨 실행. null이면 닫힘
  openerRef: RefObject<HTMLElement | null> // [복구]를 누른 버튼. 취소·Esc로 닫히면 포커스를 여기로 돌려준다
  onCancel: () => void // 닫기(취소·Esc·바깥 클릭)
  onConfirm: (execution: CollectExecution) => void // [복구]를 확정했다
}

/**
 * 중단된 실행을 복구하기 전 확인 대화상자다(SCR-16).
 *
 * AlertDialog(확인용 대화상자)를 쓴 이유: 복구는 이력에 "실패"를 기록하는, 되돌리기 어려운 동작이라 한 번 더 확인받는다.
 * Radix AlertDialog는 열릴 때 처음 포커스를 [취소]에 둔다(안전한 쪽). 바깥 클릭으로는 닫히지 않고 Esc·[취소]로 닫는다.
 * 닫힐 때 포커스는 Trigger가 없어서 Radix가 돌려줄 곳을 모르므로, [복구]를 누른 버튼(openerRef)으로 직접 돌려준다.
 * 복구에 성공하면 그 버튼이 사라지므로(행이 실패로 바뀜), 그때는 화면(CollectScreen)이 [다시 실행]으로 포커스를 옮긴다.
 *
 * 본문에서 "복구 뒤 [다시 실행]으로 이어서 수집할 수 있다"고 약속하지 않는 이유: 복구 응답의 canRestart가 거짓일 수 있다
 * (다른 수집이 실행 중이거나, 운영에서 대상 날짜가 오늘이 아님). 그래서 버튼이 보이는지로 판단하라고만 안내한다.
 * 이전 문구(참고용): "그다음 [다시 실행]으로 같은 날짜·범위를 이어서 수집할 수 있습니다."
 *
 * 본문에 "아직 실제로 돌고 있는 실행이라면 복구하지 마세요"를 넣은 이유: 중단됨은 서버가 판정한 값이지만 판정이 틀릴 수 있어서다.
 */
export function RecoverRunDialog({
  execution,
  openerRef,
  onCancel,
  onConfirm,
}: RecoverRunDialogProps) {
  const isDemo = useIsDemo()
  return (
    <AlertDialog
      open={execution !== null}
      onOpenChange={(isOpen) => {
        if (!isOpen) onCancel()
      }}
    >
      <AlertDialogContent
        onCloseAutoFocus={(event) => {
          event.preventDefault()
          openerRef.current?.focus()
        }}
      >
        <AlertDialogHeader>
          <AlertDialogTitle>중단된 실행을 복구할까요?</AlertDialogTitle>
          <AlertDialogDescription>
            {execution
              ? `실행 ${execution.executionId}(${formatIsoDate(execution.targetDate) ?? execution.targetDate} · ${COLLECT_TIER_LABELS[execution.tier]})은 서버가 중간에 멈춰 '실행 중'으로 남아 있습니다. 복구하면 이 실행을 '실패'로 기록합니다. 이어서 실행할 수 있는지는 복구 뒤 [다시 실행] 버튼이 보이는지로 알 수 있습니다. 아직 실제로 돌고 있는 실행이라면 복구하지 마세요.`
              : ''}
          </AlertDialogDescription>
        </AlertDialogHeader>
        {/* 데모 계정에게만 보이는 막힌 이유(Task 109) */}
        <DemoReadOnlyHint variant="inline" id={RECOVER_LOCK_NOTE_ID}>
          데모 계정은 실행할 수 없어요
        </DemoReadOnlyHint>
        <AlertDialogFooter>
          <AlertDialogCancel>취소</AlertDialogCancel>
          {/*
            데모 계정이면 AlertDialogAction 대신 막힌 버튼을 둔다. Action은 누르는 즉시 대화상자를 닫는 버튼이라,
            막힌 상태에서 누르면 아무 일 없이 닫혀 "왜 안 됐는지"를 알 수 없다. 막힌 버튼은 닫지 않고 이유만 토스트로 알린다.
          */}
          {isDemo ? (
            <DemoLockedButton lockedDescriptionId={RECOVER_LOCK_NOTE_ID}>복구</DemoLockedButton>
          ) : (
            <AlertDialogAction
              onClick={() => {
                if (execution) onConfirm(execution)
              }}
            >
              복구
            </AlertDialogAction>
          )}
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
