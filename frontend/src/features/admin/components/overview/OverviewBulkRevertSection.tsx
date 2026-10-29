import { useState } from 'react'
import { toast } from 'sonner'
import { isApiError } from '@/api/http'
import {
  BulkRevertDialog,
  OverviewBulkRevertPanel,
} from '@/features/admin/components/overview/OverviewBulkRevertPanel'
import { useRevertTranslatedOverviews } from '@/features/admin/hooks/useAdminOverviewTranslate'
import {
  REVERT_MONTHS_DEFAULT,
  getRevertCutoff,
  parseRevertMonths,
} from '@/features/admin/overviewRules'
import { getErrorMessage } from '@/lib/errorMessages'
import { formatDate } from '@/lib/format'

interface OverviewBulkRevertSectionProps {
  translatedCount: number // 관리자가 반영한 번역 줄거리 작품 수(번역 현황 summary)
  oldestTranslatedAt: string | null // 가장 오래된 반영 시각(번역이 없으면 null)
  blockedReason: string | null // 쓰기를 막는 이유(일괄 번역 실행 중). 있으면 미리보기·되돌리기 모두 비활성
  onConflict: () => void // 수집·재계산 등과 겹쳤을 때(409 LLM_APPLY_CONFLICT) 화면의 안내 띠를 띄운다
}

// 미리보기를 마친 결과. cutoff는 미리보기 때 보낸 기준 시각이고, 확정 때 같은 값을 다시 보낸다
interface RevertPreview {
  cutoff: string
  count: number
}

/**
 * "N개월 지난 번역 되돌리기"의 상태와 요청을 맡는다. 화면 모양은 OverviewBulkRevertPanel, 확정 대화상자는 BulkRevertDialog다.
 *
 * 흐름: 개월 수 입력 → [대상 미리보기](isPreview=true, DB 변경 없음) → 편수 확인 → [n편 되돌리기] → 확정 대화상자 → 요청.
 * 미리보기와 확정에 같은 translatedBefore를 보내는 이유: 기준 시각을 확정 때 다시 계산하면 그 사이 시계가 흘러
 * 미리보기에서 본 대상과 실제 대상이 달라진다. 그래서 미리보기 때 시각을 한 번 정해 상태에 두고 확정에도 그대로 쓴다.
 * 입력을 바꾸면 미리보기를 지운다(바뀐 기준으로 다시 세어 보고 눌러야 한다).
 */
export function OverviewBulkRevertSection({
  translatedCount,
  oldestTranslatedAt,
  blockedReason,
  onConflict,
}: OverviewBulkRevertSectionProps) {
  const revert = useRevertTranslatedOverviews()
  const [monthsText, setMonthsText] = useState(String(REVERT_MONTHS_DEFAULT))
  // 기준 날짜 도움말에 쓰는 "지금". 렌더 중에 Date.now()를 부르지 않으려고 상태로 두고, 미리보기를 누를 때 갱신한다
  const [now, setNow] = useState(() => new Date())
  const [preview, setPreview] = useState<RevertPreview | null>(null)
  const [isDialogOpen, setIsDialogOpen] = useState(false)

  const months = parseRevertMonths(monthsText)
  const cutoffDate = months === null ? null : formatDate(getRevertCutoff(months, now))
  const isPreviewing = revert.isPending && revert.variables.isPreview === true
  const isReverting = revert.isPending && revert.variables.isPreview !== true

  function handleMonthsChange(next: string) {
    setMonthsText(next)
    setPreview(null)
  }

  function showError(error: unknown) {
    if (isApiError(error) && error.errorCode === 'LLM_APPLY_CONFLICT') {
      onConflict()
      return
    }
    toast.error(isApiError(error) ? error.userMessage : getErrorMessage(null))
  }

  function handlePreview() {
    // 1. 연타 방지와 입력 확인
    if (revert.isPending || months === null) return
    // 2. 기준 시각을 지금으로 정한다(확정 때도 이 값을 쓴다)
    const current = new Date()
    const cutoff = getRevertCutoff(months, current)
    setNow(current)
    // 3. 미리보기 요청: DB를 바꾸지 않고 대상 수만 센다
    revert.mutate(
      { translatedBefore: cutoff, isPreview: true },
      {
        onSuccess: (result) => setPreview({ cutoff, count: result.revertedCount }),
        onError: showError,
      },
    )
  }

  function handleConfirm() {
    // 1. 연타 방지, 미리보기 없이 확정할 수 없다
    if (revert.isPending || !preview) return
    // 2. 확정 요청: 미리보기와 같은 기준 시각을 보낸다(isPreview는 생략 = 실제로 되돌린다)
    revert.mutate(
      { translatedBefore: preview.cutoff },
      {
        onSuccess: (result) => {
          setIsDialogOpen(false)
          setPreview(null)
          if (result.revertedCount > 0) {
            toast.success(
              `번역 줄거리 ${result.revertedCount.toLocaleString('ko-KR')}편을 지웠습니다. 다음 수집 때 영어 줄거리가 다시 채워집니다.`,
            )
          } else {
            toast.info('되돌릴 번역이 없었습니다.')
          }
        },
        onError: (error) => {
          setIsDialogOpen(false)
          showError(error)
        },
      },
    )
  }

  return (
    <>
      <OverviewBulkRevertPanel
        translatedCount={translatedCount}
        oldestTranslatedAt={oldestTranslatedAt}
        monthsText={monthsText}
        onMonthsTextChange={handleMonthsChange}
        cutoffDate={cutoffDate}
        previewCount={preview?.count ?? null}
        isPreviewing={isPreviewing}
        onPreview={handlePreview}
        onRequestRevert={() => setIsDialogOpen(true)}
        blockedReason={blockedReason}
      />
      <BulkRevertDialog
        target={
          isDialogOpen && preview
            ? { count: preview.count, cutoffDate: formatDate(preview.cutoff) ?? preview.cutoff }
            : null
        }
        isPending={isReverting}
        onCancel={() => setIsDialogOpen(false)}
        onConfirm={handleConfirm}
      />
    </>
  )
}
