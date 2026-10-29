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
const REVERT_LOCK_NOTE_ID = 'revert-overview-demo-note'

// 되돌릴 작품. title은 화면이 아는 경우에만 있다(반영 결과 행에서 열면 제목을 알고, 작품 번호 입력에서 열면 모른다)
export interface RevertTarget {
  titleId: number
  title: string | null
}

interface RevertOverviewDialogProps {
  target: RevertTarget | null // 되돌릴 작품. null이면 닫힘
  isPending: boolean // 되돌리기 요청을 보내는 중인지
  onCancel: () => void // 닫기(취소·Esc)
  onConfirm: () => void // 되돌리기를 확정했다
}

/**
 * 작품의 한국어 줄거리를 영어로 되돌리기 전 확인 대화상자다.
 *
 * 되돌리면 한국어 줄거리가 지워지고, 영어 줄거리는 다음 수집(매일 또는 매주)이 TMDB에서 다시 채운다.
 * 그 사이(최대 일주일)에는 이용자 화면에서 이 작품의 줄거리가 비어 보일 수 있어, 확인 전에 분명히 알린다.
 * 확정 버튼은 요청 중 비활성이고 핸들러도 한 번 더 막아 두 번 눌러도 요청이 1번만 나간다.
 */
export function RevertOverviewDialog({
  target,
  isPending,
  onCancel,
  onConfirm,
}: RevertOverviewDialogProps) {
  const isDemo = useIsDemo()
  const confirmLabel = isPending ? '되돌리는 중…' : '영어로 되돌리기'
  return (
    <AlertDialog
      open={target !== null}
      onOpenChange={(isOpen) => {
        if (!isOpen && !isPending) onCancel()
      }}
    >
      {target && (
        <AlertDialogContent className="sm:max-w-md">
          <AlertDialogHeader>
            <AlertDialogTitle>
              {target.title
                ? `「${target.title}」(#${target.titleId})의 줄거리를 영어로 되돌릴까요?`
                : `작품 #${target.titleId}의 줄거리를 영어로 되돌릴까요?`}
            </AlertDialogTitle>
            <AlertDialogDescription>
              한국어 줄거리를 지웁니다. 영어 줄거리는 다음 수집(매일 또는 매주)이 TMDB에서 다시
              채우므로, 그때까지 최대 일주일 동안 이 작품의 줄거리가 비어 보일 수 있습니다.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <OverviewNoticeBand tone="attention" role="note">
            되돌린 뒤 바로 영어 줄거리가 보이지 않습니다.
          </OverviewNoticeBand>
          <DemoReadOnlyHint variant="inline" id={REVERT_LOCK_NOTE_ID}>
            데모 계정은 되돌릴 수 없어요
          </DemoReadOnlyHint>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isPending}>취소</AlertDialogCancel>
            {isDemo ? (
              <DemoLockedButton lockedDescriptionId={REVERT_LOCK_NOTE_ID}>
                {confirmLabel}
              </DemoLockedButton>
            ) : (
              <AlertDialogAction
                disabled={isPending}
                onClick={(event) => {
                  // 요청이 끝날 때까지 대화상자를 열어 둔다(성공하면 화면이 닫는다)
                  event.preventDefault()
                  if (!isPending) onConfirm()
                }}
              >
                {confirmLabel}
              </AlertDialogAction>
            )}
          </AlertDialogFooter>
        </AlertDialogContent>
      )}
    </AlertDialog>
  )
}
