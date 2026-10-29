import { useState } from 'react'
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
import { OverviewNoticeBand } from '@/features/admin/components/overview/OverviewNoticeBand'
import { useIsDemo } from '@/hooks/useIsDemo'

// 데모 계정에게 확인 버튼이 막힌 이유 문장의 id
const APPLY_LOCK_NOTE_ID = 'apply-drafts-demo-note'

// 반영 대상. selected: 고른 초안 count편 / allPassed: 성공 초안 전부(count는 화면이 아는 개수).
// warningCount: selected면 고른 것 중 주의 편수, allPassed면 이번 반영에 들어가지 않는 주의 편수(null이면 아직 모름)
export type ApplyTarget = {
  mode: 'selected' | 'allPassed'
  count: number
  warningCount: number | null
}

interface ApplyDraftsDialogProps {
  target: ApplyTarget | null // 반영하려는 대상. null이면 닫힘
  checkedSampleCount: number | null // 표본을 읽어 봤다고 표시한 편수. 확인 전이면 null
  isPending: boolean // 반영 요청을 보내는 중인지
  onCancel: () => void // 닫기(취소·Esc)
  onConfirm: () => void // 반영을 확정했다
}

interface ApplyDialogBodyProps extends Omit<ApplyDraftsDialogProps, 'target' | 'onCancel'> {
  target: ApplyTarget
}

// 대화상자가 열려 있는 동안만 그려져, "표본 확인 없이 반영합니다" 체크가 열 때마다 처음 상태(해제)로 시작한다
function ApplyDialogBody({
  target,
  checkedSampleCount,
  isPending,
  onConfirm,
}: ApplyDialogBodyProps) {
  const isDemo = useIsDemo()
  const [isSkipAcknowledged, setIsSkipAcknowledged] = useState(false)
  const isAll = target.mode === 'allPassed'
  // 전부 반영인데 표본을 확인하지 않았으면 체크해야 확정 버튼이 켜진다
  const needsAcknowledge = isAll && checkedSampleCount === null
  const isConfirmBlocked = isPending || (needsAcknowledge && !isSkipAcknowledged)
  const confirmLabel = isPending
    ? '반영하는 중…'
    : isAll
      ? `${target.count}편 모두 반영`
      : `${target.count}편 반영`

  return (
    <AlertDialogContent className="sm:max-w-md">
      <AlertDialogHeader>
        <AlertDialogTitle>
          {isAll
            ? `성공 초안 ${target.count}편을 모두 반영할까요?`
            : `선택한 ${target.count}편을 반영할까요?`}
        </AlertDialogTitle>
        <AlertDialogDescription>
          {isAll
            ? '자동 검사에서 걸린 것이 없는 초안만 반영합니다. 사람이 읽지 않은 번역이 섞여 있을 수 있고, 반영하면 이용자 화면에 바로 보입니다. 잘못된 번역은 반영한 뒤에 작품별로 영어 줄거리로 되돌릴 수 있습니다.'
            : '반영하면 이 작품들의 줄거리가 바로 한국어 번역으로 바뀌고 이용자 화면에 보입니다. 번역한 뒤 원문이 바뀐 작품은 건너뜁니다.'}
        </AlertDialogDescription>
      </AlertDialogHeader>

      {isAll &&
        (checkedSampleCount !== null ? (
          <OverviewNoticeBand tone="info" role="note">
            ✓ 이번에 표본 {checkedSampleCount}편을 확인했습니다.
          </OverviewNoticeBand>
        ) : (
          <div className="grid gap-2">
            <OverviewNoticeBand
              tone="attention"
              title="표본을 아직 확인하지 않았습니다"
              role="note"
            >
              [성공] 탭의 "무작위 표본 10편 읽어 보기"에서 먼저 읽어 보길 권합니다.
            </OverviewNoticeBand>
            <label className="flex items-center gap-2 text-sm font-medium">
              <input
                type="checkbox"
                checked={isSkipAcknowledged}
                onChange={(event) => setIsSkipAcknowledged(event.target.checked)}
                className="size-4 accent-primary"
              />
              표본 확인 없이 반영합니다
            </label>
          </div>
        ))}

      {/* 전부 반영에는 주의 초안이 들어가지 않는다는 안내 / 선택 반영에 주의 초안이 섞였다는 안내 */}
      {isAll && target.warningCount !== null && target.warningCount > 0 && (
        <OverviewNoticeBand tone="info" role="note">
          주의 {target.warningCount}편은 이번 반영에 들어가지 않습니다. [승인 대기] 탭에서 한 편씩
          승인해 주세요.
        </OverviewNoticeBand>
      )}
      {!isAll && target.warningCount !== null && target.warningCount > 0 && (
        <OverviewNoticeBand tone="attention" role="note">
          고른 초안 중 주의 {target.warningCount}편이 있습니다. 읽어 보셨다면 그대로 반영하세요.
        </OverviewNoticeBand>
      )}

      {isAll && (
        <p className="text-xs text-muted-foreground">
          반영할 개수는 누르는 순간 서버에 있는 성공 초안 기준이라 이 숫자와 조금 다를 수 있습니다.
        </p>
      )}

      {/* 데모 계정에게만 보이는 막힌 이유 */}
      <DemoReadOnlyHint variant="inline" id={APPLY_LOCK_NOTE_ID}>
        데모 계정은 반영할 수 없어요
      </DemoReadOnlyHint>

      <AlertDialogFooter>
        <AlertDialogCancel disabled={isPending}>취소</AlertDialogCancel>
        {/* 데모 계정이면 누르는 즉시 닫히는 AlertDialogAction 대신 막힌 버튼을 둔다(RecoverRunDialog와 같은 방식) */}
        {isDemo ? (
          <DemoLockedButton lockedDescriptionId={APPLY_LOCK_NOTE_ID}>
            {confirmLabel}
          </DemoLockedButton>
        ) : (
          <AlertDialogAction
            disabled={isConfirmBlocked}
            onClick={(event) => {
              // 반영이 끝날 때까지 대화상자를 열어 둔다(성공하면 화면이 닫는다). 기본 동작은 누르는 즉시 닫는 것이다
              event.preventDefault()
              if (!isConfirmBlocked) onConfirm()
            }}
          >
            {confirmLabel}
          </AlertDialogAction>
        )}
      </AlertDialogFooter>
    </AlertDialogContent>
  )
}

/**
 * 초안 반영 전 확인 대화상자다(선택 반영 / 전부 반영). 반영은 이용자 화면의 줄거리를 바로 바꾸므로 항상 한 번 더 확인받는다.
 *
 * AlertDialog를 쓴 이유: 처음 포커스가 안전한 [취소]에 놓이고 바깥 클릭으로는 닫히지 않는다.
 * 확정 버튼은 요청 중에 비활성 + "반영하는 중…"이 되고, 핸들러도 한 번 더 막아 두 번 눌러도 요청이 1번만 나간다.
 * 전부 반영은 사람이 읽지 않은 번역이 섞일 수 있어, 표본을 확인하지 않았다면 경고와 "표본 확인 없이 반영합니다" 체크를 요구한다.
 */
export function ApplyDraftsDialog({
  target,
  checkedSampleCount,
  isPending,
  onCancel,
  onConfirm,
}: ApplyDraftsDialogProps) {
  return (
    <AlertDialog
      open={target !== null}
      onOpenChange={(isOpen) => {
        // 반영 요청 중에는 Esc로도 닫지 않는다(결과를 보지 못하고 닫히는 것을 막는다)
        if (!isOpen && !isPending) onCancel()
      }}
    >
      {target && (
        <ApplyDialogBody
          target={target}
          checkedSampleCount={checkedSampleCount}
          isPending={isPending}
          onConfirm={onConfirm}
        />
      )}
    </AlertDialog>
  )
}
