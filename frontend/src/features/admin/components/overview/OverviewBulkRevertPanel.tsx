/*
 * "번역 통계와 오래된 번역 되돌리기" 상자와 확정 대화상자다(POST /api/admin/llm/overview-reverts).
 * 입력값·미리보기 결과·요청 상태는 모두 부모(OverviewBulkRevertSection)가 props로 넘긴다.
 */
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
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { DemoLockedButton } from '@/components/common/DemoLockedButton'
import { DemoReadOnlyHint } from '@/components/common/DemoReadOnlyHint'
import { LlmWriteButton } from '@/features/admin/components/overview/LlmWriteButton'
import { OverviewNoticeBand } from '@/features/admin/components/overview/OverviewNoticeBand'
import { useIsDemo } from '@/hooks/useIsDemo'
import { REVERT_MONTHS_MAX, REVERT_MONTHS_MIN } from '@/features/admin/overviewRules'
import { formatDate } from '@/lib/format'

interface OverviewBulkRevertPanelProps {
  translatedCount: number // 관리자가 반영한 번역 줄거리 작품 수(summary.translatedCount)
  oldestTranslatedAt: string | null // 가장 오래된 번역의 반영 시각. 번역이 없으면 null
  monthsText: string // "몇 개월 지난" 입력값(문자열 그대로)
  onMonthsTextChange: (next: string) => void // 바뀌면 부모가 미리보기 결과를 비운다
  cutoffDate: string | null // 입력값으로 계산한 기준 날짜(이 날짜보다 먼저 반영한 번역이 대상). 입력이 틀리면 null
  previewCount: number | null // 미리보기 결과 편수. 아직 미리보기 전이면 null
  isPreviewing: boolean // 미리보기 요청 중인지
  onPreview: () => void // [대상 미리보기] isPreview=true로 요청
  onRequestRevert: () => void // [n편 되돌리기] 확정 대화상자를 연다
  blockedReason?: string | null // 쓰기를 막는 공통 이유(있으면 두 버튼 모두 비활성)
}

/**
 * 번역 통계(반영한 번역 수, 가장 오래된 반영일)와 "N개월 지난 번역 되돌리기"를 한 상자에 둔다.
 *
 * 왜 두 단계(미리보기 → 확정)인가: 되돌리면 줄거리가 비워져 며칠~일주일 동안 이용자 화면에 줄거리가 안 보인다.
 * 몇 편이 영향을 받는지 먼저 숫자로 보고 누르게 한다. 미리보기는 DB를 바꾸지 않는다(계약 isPreview=true).
 * 확정 버튼은 미리보기 숫자가 1편 이상일 때만 켜지고, 입력을 바꾸면 미리보기가 풀려 다시 봐야 한다.
 * 데모 계정은 미리보기도 쓰기 요청(POST)이라 서버가 막으므로 두 버튼 모두 자물쇠다(대화상자가 열리지 않는다).
 */
export function OverviewBulkRevertPanel({
  translatedCount,
  oldestTranslatedAt,
  monthsText,
  onMonthsTextChange,
  cutoffDate,
  previewCount,
  isPreviewing,
  onPreview,
  onRequestRevert,
  blockedReason = null,
}: OverviewBulkRevertPanelProps) {
  const isMonthsValid = cutoffDate !== null
  const previewBlockedReason =
    blockedReason ??
    (isMonthsValid
      ? null
      : `${REVERT_MONTHS_MIN}부터 ${REVERT_MONTHS_MAX} 사이의 숫자를 입력해 주세요.`)
  const revertBlockedReason =
    blockedReason ??
    (previewCount === null
      ? '먼저 [대상 미리보기]로 몇 편인지 확인해 주세요.'
      : previewCount === 0
        ? '되돌릴 번역이 없습니다.'
        : null)

  return (
    <div className="grid gap-4">
      {/* 통계 두 칸(번역 현황 상자와 같은 dl 모양) */}
      <dl className="grid grid-cols-2 gap-x-4 gap-y-3">
        <div className="grid content-start gap-1">
          <dt className="text-xs text-muted-foreground">반영한 번역 줄거리</dt>
          <dd className="text-sm font-semibold tabular-nums">
            {translatedCount.toLocaleString('ko-KR')}편
          </dd>
        </div>
        <div className="grid content-start gap-1">
          <dt className="text-xs text-muted-foreground">가장 오래된 반영</dt>
          <dd className="text-sm font-semibold tabular-nums">
            {oldestTranslatedAt ? (formatDate(oldestTranslatedAt) ?? '-') : '없음'}
          </dd>
        </div>
      </dl>
      <p className="text-xs break-keep text-muted-foreground">
        TMDB가 원래 준 한국어 줄거리는 세지 않고, 이 화면에서 반영한 번역만 셉니다. 번역은 6개월쯤
        지나면 되돌려 TMDB의 최신 줄거리로 바꾸길 권합니다.
      </p>

      {translatedCount === 0 ? (
        <p className="text-sm text-muted-foreground">아직 반영한 번역 줄거리가 없습니다.</p>
      ) : (
        <div className="grid gap-3 border-t border-border pt-4">
          <div className="grid gap-1.5">
            <Label htmlFor="llm-revert-months">몇 개월 지난 번역을 영어로 되돌릴까요</Label>
            <div className="flex flex-wrap items-center gap-2">
              <Input
                id="llm-revert-months"
                type="number"
                inputMode="numeric"
                min={REVERT_MONTHS_MIN}
                max={REVERT_MONTHS_MAX}
                value={monthsText}
                onChange={(event) => onMonthsTextChange(event.target.value)}
                aria-invalid={!isMonthsValid || undefined}
                aria-describedby="llm-revert-months-help"
                className="w-20"
              />
              <span className="text-sm">개월 지난 번역</span>
            </div>
            <p id="llm-revert-months-help" className="text-xs text-muted-foreground">
              {cutoffDate
                ? `${cutoffDate}보다 먼저 반영한 번역이 대상입니다.`
                : `${REVERT_MONTHS_MIN}부터 ${REVERT_MONTHS_MAX} 사이의 숫자를 입력해 주세요.`}
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
            <LlmWriteButton
              type="button"
              variant="outline"
              size="sm"
              blockedReason={previewBlockedReason}
              isBusy={isPreviewing}
              onClick={onPreview}
            >
              {isPreviewing ? '세는 중…' : '대상 미리보기'}
            </LlmWriteButton>
            <LlmWriteButton
              type="button"
              variant="outline"
              size="sm"
              blockedReason={revertBlockedReason}
              blockedReasonId="llm-bulk-revert-reason"
              onClick={onRequestRevert}
            >
              {previewCount ? `${previewCount.toLocaleString('ko-KR')}편 되돌리기` : '되돌리기'}
            </LlmWriteButton>
          </div>

          {/* 미리보기 결과는 바뀔 때 한 번 읽힌다 */}
          <p role="status" className="text-sm">
            {previewCount !== null &&
              (previewCount > 0
                ? `미리보기: ${previewCount.toLocaleString('ko-KR')}편이 대상입니다. 아직 아무것도 바뀌지 않았습니다.`
                : '미리보기: 대상인 번역이 없습니다.')}
          </p>
          {revertBlockedReason && previewCount !== null && (
            <p id="llm-bulk-revert-reason" className="sr-only">
              {revertBlockedReason}
            </p>
          )}
          {revertBlockedReason && previewCount === null && (
            <p id="llm-bulk-revert-reason" className="text-xs text-muted-foreground">
              {revertBlockedReason}
            </p>
          )}
        </div>
      )}
    </div>
  )
}

interface BulkRevertDialogProps {
  target: { count: number; cutoffDate: string } | null // 되돌릴 편수와 기준 날짜. null이면 닫힘
  isPending: boolean
  onCancel: () => void
  onConfirm: () => void // isPreview 없이(실제로) 같은 translatedBefore로 요청
}

/**
 * 대량 되돌리기 확정 대화상자다. 단건 되돌리기(RevertOverviewDialog)와 같은 모양·문장 흐름을 쓴다.
 * 처음 포커스는 [취소], 바깥 클릭으로 닫히지 않고, 요청 중에는 Esc로도 닫히지 않는다.
 */
export function BulkRevertDialog({
  target,
  isPending,
  onCancel,
  onConfirm,
}: BulkRevertDialogProps) {
  const isDemo = useIsDemo()
  const countText = target ? target.count.toLocaleString('ko-KR') : ''
  const confirmLabel = isPending ? '되돌리는 중…' : `${countText}편 영어로 되돌리기`
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
            <AlertDialogTitle>번역 줄거리 {countText}편을 영어로 되돌릴까요?</AlertDialogTitle>
            <AlertDialogDescription>
              {target.cutoffDate}보다 먼저 반영한 번역 {countText}편의 한국어 줄거리를 지웁니다.
              영어 줄거리는 다음 수집(매일 또는 매주)이 TMDB에서 다시 채우므로, 그때까지 며칠에서
              최대 일주일 동안 이 작품들의 줄거리가 비어 보일 수 있습니다.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <OverviewNoticeBand tone="attention" role="note">
            되돌린 뒤 바로 영어 줄거리가 보이지 않습니다. 다시 한국어로 바꾸려면 초안을 새로
            만들어야 합니다.
          </OverviewNoticeBand>
          <p className="text-xs text-muted-foreground">
            누르는 순간의 기준으로 되돌려 미리보기 숫자와 조금 다를 수 있습니다.
          </p>
          <DemoReadOnlyHint variant="inline" id="bulk-revert-demo-note">
            데모 계정은 되돌릴 수 없어요
          </DemoReadOnlyHint>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isPending}>취소</AlertDialogCancel>
            {isDemo ? (
              <DemoLockedButton lockedDescriptionId="bulk-revert-demo-note">
                {confirmLabel}
              </DemoLockedButton>
            ) : (
              <AlertDialogAction
                variant="destructive"
                disabled={isPending}
                onClick={(event) => {
                  // 요청이 끝날 때까지 열어 둔다(성공하면 부모가 닫는다)
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
