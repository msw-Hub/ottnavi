import { useId } from 'react'
import { cn } from 'cn'
import type { OverviewDraftStatus } from '@/api/generated/model'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import {
  DraftGradePill,
  DraftWarningTags,
} from '@/features/admin/components/overview/DraftGradePill'
import { GuardFailureTags } from '@/features/admin/components/overview/GuardFailureTags'
import { LlmWriteButton } from '@/features/admin/components/overview/LlmWriteButton'
import { OverviewNoticeBand } from '@/features/admin/components/overview/OverviewNoticeBand'

// 번역문 최대 글자 수(계약 EditOverviewDraftRequest.translatedText maxLength, 자동 검사 TOO_LONG 기준과 같다)
const MAX_LENGTH = 1000

// 저장 직후 서버가 다시 검사한 결과(저장 응답의 OverviewDraft에서 등급·이유만 뽑은 것)
export interface DraftCheckResult {
  grade: OverviewDraftStatus
  guardFailures: string[]
  warnings: string[]
}

interface OverviewDraftEditorProps {
  original: string // 영어 원문(읽기 전용)
  value: string // 수정 중인 번역문
  onChange: (next: string) => void
  isChanged: boolean // 처음 번역문과 다른지(같으면 저장할 것이 없어 저장 버튼을 막는다)
  isSaving: boolean // 저장 요청 중인지
  checkResult: DraftCheckResult | null // 마지막 저장 뒤 다시 검사한 결과. 아직 저장하지 않았으면 null
  onSave: () => void
  onClose: () => void // [닫기] 수정 칸을 접는다(저장하지 않은 내용은 버린다)
  blockedReason?: string | null // 저장을 막는 공통 이유(일괄 번역 실행 중 등). 칸은 열어 둔 채 저장만 막아 입력한 글이 사라지지 않게 한다
  errorText?: string | null // 저장 요청이 실패했을 때의 문장(수정 칸 아래 빨간 글자)
}

/**
 * 원문(영어)과 번역문(한국어 입력 칸)을 나란히 두고 고치는 칸이다. md 이상은 2칸, 좁은 화면은 위아래로 쌓는다.
 *
 * - 글자 수: "312 / 1,000자"를 입력 칸 바로 아래에 둔다. 넘으면 빨간 글자 + "1,000자를 넘었습니다" 문장(색만으로 알리지 않는다).
 * - 저장하면 서버가 자동 검사를 다시 돌려 새 등급을 준다. 결과는 role="status" 띠로 한 번 읽힌다.
 *   실패가 남아도 저장은 되지만 반영은 막힌다는 점을 띠 안에 글자로 쓴다.
 * - 직접 고친 초안은 일괄 번역의 자동 재시도가 덮어쓰지 않는다는 안내를 늘 보인다(계약 OverviewDraft.isEdited).
 */
export function OverviewDraftEditor({
  original,
  value,
  onChange,
  isChanged,
  isSaving,
  checkResult,
  onSave,
  onClose,
  blockedReason = null,
  errorText = null,
}: OverviewDraftEditorProps) {
  const baseId = useId()
  const textareaId = `${baseId}-text`
  const countId = `${baseId}-count`
  const length = value.length
  const isOver = length > MAX_LENGTH
  const isBlank = value.trim() === ''
  // 저장을 막는 이유(우선순위: 비어 있음 > 너무 김 > 바뀐 것 없음)
  const saveBlockedReason =
    blockedReason ??
    (isBlank
      ? '번역문을 입력해 주세요.'
      : isOver
        ? `${MAX_LENGTH.toLocaleString('ko-KR')}자를 넘었습니다. 줄여 주세요.`
        : !isChanged
          ? '고친 내용이 없습니다.'
          : null)

  return (
    <div className="grid gap-3 rounded-lg border border-border bg-muted/40 p-3">
      <div className="grid gap-3 md:grid-cols-2">
        {/* 왼쪽(좁은 화면은 위): 영어 원문. 고칠 수 없어 입력 칸이 아니라 글로 둔다 */}
        <div className="grid content-start gap-1">
          <p id={`${baseId}-original`} className="text-xs font-semibold text-muted-foreground">
            영어 원문
          </p>
          <p
            lang="en"
            aria-labelledby={`${baseId}-original`}
            className="text-sm leading-relaxed break-words whitespace-pre-wrap"
          >
            {original}
          </p>
        </div>
        {/* 오른쪽(좁은 화면은 아래): 한국어 번역문 입력 칸 */}
        <div className="grid content-start gap-1">
          <label htmlFor={textareaId} className="text-xs font-semibold text-muted-foreground">
            한국어 번역문 (고치는 중)
          </label>
          <Textarea
            id={textareaId}
            lang="ko"
            value={value}
            onChange={(event) => onChange(event.target.value)}
            rows={8}
            aria-invalid={isOver || undefined}
            aria-describedby={countId}
            className="min-h-40 bg-background leading-relaxed"
          />
          <p
            id={countId}
            className={cn(
              'text-right text-xs tabular-nums',
              isOver ? 'font-semibold text-destructive' : 'text-muted-foreground',
            )}
          >
            {length.toLocaleString('ko-KR')} / {MAX_LENGTH.toLocaleString('ko-KR')}자
            {isOver && ' · 너무 깁니다'}
          </p>
        </div>
      </div>

      <p className="text-xs break-keep text-muted-foreground">
        직접 고친 초안은 일괄 번역을 다시 돌려도 새 번역으로 덮어쓰지 않습니다. 저장하면 자동 검사를
        다시 합니다.
      </p>

      {/* 저장 뒤 다시 검사한 결과. 내용이 바뀔 때 한 번 읽힌다 */}
      <div role="status">{checkResult && <CheckResultBand result={checkResult} />}</div>
      {errorText && (
        <p role="alert" className="text-sm text-destructive">
          {errorText}
        </p>
      )}

      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
        <LlmWriteButton
          type="button"
          size="sm"
          blockedReason={saveBlockedReason}
          blockedReasonId={`${baseId}-save-reason`}
          isBusy={isSaving}
          onClick={onSave}
        >
          {isSaving ? '저장하고 검사하는 중…' : '저장하고 다시 검사'}
        </LlmWriteButton>
        <Button type="button" variant="ghost" size="sm" disabled={isSaving} onClick={onClose}>
          닫기
        </Button>
        {saveBlockedReason && (
          <p id={`${baseId}-save-reason`} className="text-xs text-muted-foreground">
            {saveBlockedReason}
          </p>
        )}
      </div>
    </div>
  )
}

// 다시 검사한 결과 띠: 등급마다 띠 종류와 문장이 다르다
function CheckResultBand({ result }: { result: DraftCheckResult }) {
  if (result.grade === 'PASSED') {
    return (
      <OverviewNoticeBand tone="info" title="저장했습니다 · 검사 결과: 성공">
        <span className="inline-flex flex-wrap items-center gap-2">
          <DraftGradePill grade="PASSED" />
          바로 반영할 수 있습니다.
        </span>
      </OverviewNoticeBand>
    )
  }
  if (result.grade === 'WARNING') {
    return (
      <OverviewNoticeBand tone="attention" title="저장했습니다 · 검사 결과: 주의">
        <div className="grid gap-1.5">
          <p>주의할 점이 남아 있습니다. 읽어 보고 괜찮으면 [승인하고 반영]을 누르세요.</p>
          <DraftWarningTags codes={result.warnings} showsDescriptions />
        </div>
      </OverviewNoticeBand>
    )
  }
  return (
    <OverviewNoticeBand tone="danger" title="저장했습니다 · 검사 결과: 실패">
      <div className="grid gap-1.5">
        <p>
          검사 실패가 남아 있어 아직 반영할 수 없습니다. 아래 이유를 고쳐 다시 저장하거나 [다시
          번역]을 누르세요.
        </p>
        <GuardFailureTags codes={result.guardFailures} showsDescriptions />
      </div>
    </OverviewNoticeBand>
  )
}
