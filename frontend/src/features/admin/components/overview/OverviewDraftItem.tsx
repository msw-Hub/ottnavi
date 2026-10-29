import type { ReactNode } from 'react'
import { ChevronDownIcon, ChevronRightIcon, UserPenIcon } from 'lucide-react'
import type { OverviewDraft, OverviewDraftStatus } from '@/api/generated/model'
import { Button } from '@/components/ui/button'
import { AdminStatusPill } from '@/features/admin/components/AdminStatusPill'
import {
  DraftGradePill,
  DraftWarningTags,
} from '@/features/admin/components/overview/DraftGradePill'
import { GuardFailureTags } from '@/features/admin/components/overview/GuardFailureTags'
import { LlmWriteButton } from '@/features/admin/components/overview/LlmWriteButton'
import { OverviewDraftSides } from '@/features/admin/components/overview/OverviewDraftCard'
import { MEDIA_TYPE_LABELS } from '@/features/admin/overviewRules'
import { formatDateTime } from '@/lib/format'

// 실패 초안의 [반영]이 막힌 이유 문장
const FAILED_APPLY_REASON =
  '검사 실패가 남아 있어 반영할 수 없습니다. 번역문을 고쳐 다시 검사하거나 다시 번역하세요.'

interface OverviewDraftItemProps {
  draft: OverviewDraft
  grade: OverviewDraftStatus // 이 초안의 등급(실패 있음 → 실패, 경고만 있음 → 주의, 둘 다 없음 → 성공)
  isSelectable: boolean // 선택 체크박스를 보일지(성공·주의 탭만. 실패는 반영할 수 없어 없다)
  isSelected: boolean
  onToggle: () => void
  isExpanded: boolean // 원문·번역문을 나란히 펼쳤는지
  onToggleExpanded: () => void
  editor: ReactNode | null // 수정 중이면 수정 칸(OverviewDraftEditor). 수정 칸이 열리면 펼침 대신 보인다
  onEdit: () => void
  onApply: () => void // 이 한 편만 반영(성공) / 승인하고 반영(주의). draftIds: [이 초안]으로 보낸다
  onRetranslate: () => void
  onDelete: () => void
  busyAction: 'apply' | 'retranslate' | 'delete' | null // 지금 요청 중인 동작(그 버튼만 "…중"으로 바뀐다)
  blockedReason?: string | null // 일괄 번역 실행 중 등 쓰기를 막는 공통 이유(있으면 쓰기 버튼 모두 비활성)
  retranslateBlockedReason?: string | null // [다시 번역]만 막는 이유(번역 기능 꺼짐, 다른 작품 번역 중). 수정·반영·삭제는 막지 않는다
}

/**
 * 초안 한 줄의 구조(위→아래):
 * 1. 머리 줄: [체크] 제목 · 구분 · #번호 / 등급 배지 / "수정됨" 배지 / "n번 시도"
 * 2. 이유 줄: 실패 이유(실패 탭) 또는 주의 이유(주의 탭). 성공은 없음
 * 3. 본문: 접힘이면 번역문 2줄, 펼침이면 원문·번역문 나란히, 수정 중이면 수정 칸
 * 4. 꼬리 줄: 만든 시각·모델 / 버튼 [펼치기] [수정] [반영 또는 승인하고 반영] [다시 번역] [삭제]
 *
 * 버튼을 꼬리 줄에 모은 이유: 375px에서 제목 옆에 버튼 5개를 두면 제목이 한두 글자씩 끊긴다. 아래 줄에 두면 줄바꿈만 생긴다.
 * [반영] 버튼 모양은 등급마다 다르다: 성공은 기본(채움), 주의는 "승인하고 반영"(테두리) — 한 번 더 읽고 누르라는 뜻.
 * 실패는 비활성 + 이유 문장을 버튼 아래에 글자로 둔다(비활성 모양만으로 알리지 않는다).
 */
export function OverviewDraftItem({
  draft,
  grade,
  isSelectable,
  isSelected,
  onToggle,
  isExpanded,
  onToggleExpanded,
  editor,
  onEdit,
  onApply,
  onRetranslate,
  onDelete,
  busyAction,
  blockedReason = null,
  retranslateBlockedReason = null,
}: OverviewDraftItemProps) {
  const idPrefix = `draft-${draft.draftId}`
  const detailId = `${idPrefix}-detail`
  const applyReasonId = `${idPrefix}-apply-reason`
  const isEditing = editor !== null
  const applyBlockedReason = grade === 'FAILED' ? FAILED_APPLY_REASON : blockedReason
  const applyLabel =
    busyAction === 'apply' ? '반영하는 중…' : grade === 'WARNING' ? '승인하고 반영' : '반영'

  return (
    <li className="grid gap-2 py-3 first:pt-0 last:pb-0">
      {/* 1. 머리 줄 */}
      <div className="flex items-start gap-3">
        {isSelectable && (
          <input
            type="checkbox"
            checked={isSelected}
            onChange={onToggle}
            aria-label={`${draft.title} 선택`}
            className="mt-1 size-4 shrink-0 accent-primary"
          />
        )}
        <div className="grid min-w-0 flex-1 gap-1.5">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span className="font-semibold break-keep">{draft.title}</span>
            <span className="text-xs text-muted-foreground">
              {MEDIA_TYPE_LABELS[draft.mediaType]} · #{draft.titleId}
            </span>
            <DraftGradePill grade={grade} />
            {draft.isEdited && (
              <AdminStatusPill icon={UserPenIcon} label="수정됨" tone="progress" size="sm" />
            )}
            {/* 시도 횟수: 2번 이상일 때만 눈에 띄게(1번은 평범한 경우라 작은 회색 글자) */}
            <span
              className={
                draft.attempts > 1
                  ? 'text-xs font-semibold text-foreground tabular-nums'
                  : 'text-xs text-muted-foreground tabular-nums'
              }
            >
              {draft.attempts}번 시도
            </span>
          </div>

          {/* 2. 이유 줄 */}
          {grade === 'FAILED' && <GuardFailureTags codes={draft.guardFailures} showsDescriptions />}
          {grade === 'WARNING' && <DraftWarningTags codes={draft.warnings} showsDescriptions />}

          {/* 3. 본문 */}
          <div id={detailId}>
            {isEditing ? (
              editor
            ) : isExpanded ? (
              <OverviewDraftSides draft={draft} idPrefix={idPrefix} />
            ) : (
              <p
                lang="ko"
                className="line-clamp-2 text-sm break-words whitespace-pre-wrap text-muted-foreground"
              >
                {draft.translated || '(번역문이 비어 있습니다)'}
              </p>
            )}
          </div>

          {/* 4. 꼬리 줄 */}
          <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2">
            <p className="text-xs text-muted-foreground">
              만든 시각 {formatDateTime(draft.createdAt) ?? '-'} · 모델 {draft.model}
            </p>
            {!isEditing && (
              <div className="flex flex-wrap items-center gap-1.5">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  aria-expanded={isExpanded}
                  aria-controls={detailId}
                  onClick={onToggleExpanded}
                >
                  {isExpanded ? (
                    <ChevronDownIcon aria-hidden="true" />
                  ) : (
                    <ChevronRightIcon aria-hidden="true" />
                  )}
                  {isExpanded ? '접기' : '펼치기'}
                </Button>
                {/* [수정]은 쓰기 버튼이 아니라 일반 버튼이다: 데모 계정도 수정 칸을 열어 볼 수 있어야 하고, 막는 것은 저장(칸 안의 버튼)이다 */}
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={blockedReason !== null}
                  title={blockedReason ?? undefined}
                  onClick={onEdit}
                >
                  수정
                </Button>
                <LlmWriteButton
                  type="button"
                  variant={grade === 'PASSED' ? 'default' : 'outline'}
                  size="sm"
                  blockedReason={applyBlockedReason}
                  blockedReasonId={grade === 'FAILED' ? applyReasonId : undefined}
                  isBusy={busyAction === 'apply'}
                  onClick={onApply}
                >
                  {applyLabel}
                </LlmWriteButton>
                <LlmWriteButton
                  type="button"
                  variant="outline"
                  size="sm"
                  blockedReason={blockedReason ?? retranslateBlockedReason}
                  isBusy={busyAction === 'retranslate'}
                  onClick={onRetranslate}
                >
                  {busyAction === 'retranslate' ? '번역하는 중…' : '다시 번역'}
                </LlmWriteButton>
                <LlmWriteButton
                  type="button"
                  variant="ghost"
                  size="sm"
                  blockedReason={blockedReason}
                  isBusy={busyAction === 'delete'}
                  onClick={onDelete}
                >
                  삭제
                </LlmWriteButton>
              </div>
            )}
          </div>
          {grade === 'FAILED' && !isEditing && (
            <p id={applyReasonId} className="text-xs break-keep text-muted-foreground">
              {FAILED_APPLY_REASON}
            </p>
          )}
          {draft.isEdited && !isEditing && (
            <p className="text-xs break-keep text-muted-foreground">
              직접 고친 번역문입니다. [다시 번역]을 누르면 고친 내용이 새 번역으로 바뀝니다.
            </p>
          )}
        </div>
      </div>
    </li>
  )
}
