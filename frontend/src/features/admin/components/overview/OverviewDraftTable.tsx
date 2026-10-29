import { useState, type ReactNode } from 'react'
import type { OverviewDraft } from '@/api/generated/model'
import { OverviewDraftItem } from '@/features/admin/components/overview/OverviewDraftItem'
import { getDraftGrade } from '@/features/admin/overviewRules'

// 지금 요청 중인 동작(그 버튼만 "…중"으로 바뀐다). 없으면 null
export type DraftBusyAction = 'apply' | 'retranslate' | 'delete' | null

interface DraftRowProps {
  draft: OverviewDraft
  isSelectable: boolean
  isSelected: boolean
  onToggle: (draftId: string) => void
  editor: ReactNode | null // 이 초안의 수정 칸. 수정 중이 아니면 null
  onEdit: (draft: OverviewDraft) => void
  onApply: (draft: OverviewDraft) => void
  onRetranslate: (draft: OverviewDraft) => void
  onDelete: (draft: OverviewDraft) => void
  busyAction: DraftBusyAction
  blockedReason: string | null
  retranslateBlockedReason: string | null
}

// 초안 한 줄. 펼침 여부는 줄마다 자기 상태로 들고, 나머지는 부모가 넘겨 준 값과 동작을 그대로 잇는다
function DraftRow({
  draft,
  isSelectable,
  isSelected,
  onToggle,
  editor,
  onEdit,
  onApply,
  onRetranslate,
  onDelete,
  busyAction,
  blockedReason,
  retranslateBlockedReason,
}: DraftRowProps) {
  const [isExpanded, setIsExpanded] = useState(false)
  return (
    <OverviewDraftItem
      draft={draft}
      grade={getDraftGrade(draft)}
      isSelectable={isSelectable}
      isSelected={isSelected}
      onToggle={() => onToggle(draft.draftId)}
      isExpanded={isExpanded}
      onToggleExpanded={() => setIsExpanded((prev) => !prev)}
      editor={editor}
      onEdit={() => onEdit(draft)}
      onApply={() => onApply(draft)}
      onRetranslate={() => onRetranslate(draft)}
      onDelete={() => onDelete(draft)}
      busyAction={busyAction}
      blockedReason={blockedReason}
      retranslateBlockedReason={retranslateBlockedReason}
    />
  )
}

interface OverviewDraftTableProps {
  drafts: OverviewDraft[] // 이 쪽의 초안들(방금 저장한 초안은 서버가 돌려준 새 값으로 바꿔서 넘긴다)
  isSelectable: boolean // 선택 체크박스를 둘지(성공·주의 탭 true, 실패 탭 false)
  selectedIds: ReadonlySet<string> // 선택된 초안 ID
  onToggle: (draftId: string) => void // 한 줄 선택/해제
  onToggleAll: (isChecked: boolean) => void // 이 쪽 전체 선택/해제
  renderEditor: (draft: OverviewDraft) => ReactNode | null // 수정 중인 초안이면 수정 칸, 아니면 null
  onEdit: (draft: OverviewDraft) => void
  onApply: (draft: OverviewDraft) => void
  onRetranslate: (draft: OverviewDraft) => void
  onDelete: (draft: OverviewDraft) => void
  getBusyAction: (draftId: string) => DraftBusyAction
  blockedReason: string | null // 일괄 번역 실행 중 등 모든 쓰기 버튼을 막는 이유
  retranslateBlockedReason: string | null // [다시 번역]만 막는 이유
}

/**
 * 성공·주의·실패 탭의 초안 목록이다. 표 대신 줄 목록으로 만든 이유: 줄거리가 길어 열이 많은 표는 좁은 화면에서
 * 가로 스크롤이 생기는데, 이 화면은 가로 스크롤을 쓰지 않는다. 줄 목록은 넓은 화면과 375px 모두 같은 구조로 읽힌다.
 * 실패 탭에는 선택 체크박스를 두지 않는다(검사에 걸린 초안은 반영할 수 없다).
 */
export function OverviewDraftTable({
  drafts,
  isSelectable,
  selectedIds,
  onToggle,
  onToggleAll,
  renderEditor,
  onEdit,
  onApply,
  onRetranslate,
  onDelete,
  getBusyAction,
  blockedReason,
  retranslateBlockedReason,
}: OverviewDraftTableProps) {
  const isAllSelected = drafts.length > 0 && drafts.every((d) => selectedIds.has(d.draftId))
  return (
    <div className="grid gap-3">
      {isSelectable && (
        <label className="flex w-fit items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={isAllSelected}
            onChange={(event) => onToggleAll(event.target.checked)}
            className="size-4 accent-primary"
          />
          전체 선택(이 쪽)
        </label>
      )}
      <ul className="divide-y divide-border">
        {drafts.map((draft) => (
          <DraftRow
            key={draft.draftId}
            draft={draft}
            isSelectable={isSelectable}
            isSelected={selectedIds.has(draft.draftId)}
            onToggle={onToggle}
            editor={renderEditor(draft)}
            onEdit={onEdit}
            onApply={onApply}
            onRetranslate={onRetranslate}
            onDelete={onDelete}
            busyAction={getBusyAction(draft.draftId)}
            blockedReason={blockedReason}
            retranslateBlockedReason={retranslateBlockedReason}
          />
        ))}
      </ul>
    </div>
  )
}
