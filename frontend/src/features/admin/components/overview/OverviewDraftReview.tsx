import { useId, useState } from 'react'
import { ChevronDownIcon, ChevronRightIcon } from 'lucide-react'
import { toast } from 'sonner'
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
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import type { OverviewDraft, OverviewDraftStatus } from '@/api/generated/model'
import { isApiError } from '@/api/http'
import { LlmWriteButton } from '@/features/admin/components/overview/LlmWriteButton'
import {
  OverviewDraftEditor,
  type DraftCheckResult,
} from '@/features/admin/components/overview/OverviewDraftEditor'
import { OverviewDraftCard } from '@/features/admin/components/overview/OverviewDraftCard'
import {
  OverviewDraftTable,
  type DraftBusyAction,
} from '@/features/admin/components/overview/OverviewDraftTable'
import { OverviewNoticeBand } from '@/features/admin/components/overview/OverviewNoticeBand'
import { OverviewPager } from '@/features/admin/components/overview/OverviewPager'
import {
  useDeleteOverviewDraft,
  useOverviewDraftSample,
  useOverviewDrafts,
} from '@/features/admin/hooks/useAdminOverviewTranslate'
import {
  useDraftEditSession,
  type DraftEditSession,
} from '@/features/admin/hooks/useDraftEditSession'
import { SAMPLE_SIZE, getDraftGrade } from '@/features/admin/overviewRules'
import { getErrorMessage } from '@/lib/errorMessages'

// 일괄 번역이 도는 동안 전부 반영·선택 반영을 막는 이유 문장
const BULK_RUNNING_APPLY_REASON = '일괄 번역이 실행 중이라 끝난 뒤에 반영할 수 있습니다.'
// 일괄 번역이 도는 동안 항목의 수정·반영·다시 번역·삭제와 수정 저장을 막는 이유 문장
const BULK_RUNNING_WRITE_REASON = '일괄 번역이 실행 중이라 끝난 뒤에 할 수 있습니다.'

// 탭 하나의 화면 정보: 탭 이름(개수 앞), 쪽 넘김 이름
const GRADE_VIEW: Record<OverviewDraftStatus, { tabName: string; pagerLabel: string }> = {
  PASSED: { tabName: '성공', pagerLabel: '성공 초안' },
  WARNING: { tabName: '승인 대기 · 주의', pagerLabel: '주의 초안' },
  FAILED: { tabName: '실패', pagerLabel: '실패 초안' },
}

// 초안 삭제 요청의 결과를 토스트로 알리는 핸들러를 만든다. 이미 없는 초안(404)은 오류가 아니라 안내다
function useDraftDeleteHandler() {
  const remove = useDeleteOverviewDraft()
  function handleDelete(draft: OverviewDraft) {
    // 연타 방지: 삭제 요청이 진행 중이면 새 요청을 보내지 않는다
    if (remove.isPending) return
    remove.mutate(
      { draftId: draft.draftId },
      {
        onSuccess: () => toast.success('초안을 삭제했습니다.'),
        onError: (error) => {
          if (isApiError(error) && error.errorCode === 'LLM_DRAFT_NOT_FOUND') {
            toast.info('이미 지워진 초안입니다. 목록을 새로 불러왔습니다.')
            return
          }
          toast.error(isApiError(error) ? error.userMessage : getErrorMessage(null))
        },
      },
    )
  }
  return {
    handleDelete,
    deletingDraftId: remove.isPending ? remove.variables.draftId : null,
  }
}

// 저장 응답의 초안에서 수정 칸의 "검사 결과 띠"에 쓸 값(등급과 이유)을 뽑는다
function toCheckResult(draft: OverviewDraft): DraftCheckResult {
  return {
    grade: getDraftGrade(draft),
    guardFailures: draft.guardFailures,
    warnings: draft.warnings,
  }
}

interface SampleTabProps {
  checkedSampleCount: number | null
  onCheckedSampleCountChange: (count: number | null) => void
  onDelete: (draft: OverviewDraft) => void
  deletingDraftId: string | null
}

// 표본 상자의 안: 무작위 표본 카드 + "읽어 봤습니다" 체크 + [다른 표본 뽑기]. 상자를 펼칠 때 처음 그려져 그때 표본을 받는다
function SampleTab({
  checkedSampleCount,
  onCheckedSampleCountChange,
  onDelete,
  deletingDraftId,
}: SampleTabProps) {
  const sample = useOverviewDraftSample()

  if (sample.isPending) return <LoadingState message="번역 초안을 불러오는 중입니다" />
  if (sample.isError) {
    return <ErrorState error={sample.error} onRetry={() => sample.refetch()} />
  }

  const drafts = sample.data.content
  const pickedCount = sample.data.totalElements // 서버가 뽑아 준 표본 수(카드를 지워도 처음 값을 기준으로 안내한다)

  if (pickedCount === 0 && drafts.length === 0) {
    return (
      <div className="grid gap-3">
        <EmptyState
          title="표본으로 볼 초안이 없습니다"
          description="성공한 초안이 생기면 무작위로 골라 보여 드립니다."
        />
        <div>
          <Button type="button" variant="outline" size="sm" onClick={() => sample.refetch()}>
            다른 표본 뽑기
          </Button>
        </div>
      </div>
    )
  }

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="grid min-w-0 flex-1 gap-1">
          <p className="text-sm break-keep">
            성공 초안 중 무작위로 {pickedCount}편을 골랐습니다. 번역이 자연스러운지, 내용이 빠지지
            않았는지 읽어 보세요.
          </p>
          {pickedCount < SAMPLE_SIZE && (
            <p className="text-sm text-muted-foreground">
              성공 초안이 {pickedCount}편뿐이라 모두 보여 드립니다.
            </p>
          )}
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={sample.isFetching}
          onClick={() => {
            // 새 표본은 아직 읽지 않은 초안이므로 "읽어 봤습니다" 표시를 푼다
            onCheckedSampleCountChange(null)
            void sample.refetch()
          }}
        >
          {sample.isFetching ? '뽑는 중…' : '다른 표본 뽑기'}
        </Button>
      </div>

      <div className="grid gap-3">
        {drafts.map((draft) => (
          <OverviewDraftCard
            key={draft.draftId}
            draft={draft}
            isDeleting={deletingDraftId === draft.draftId}
            onDelete={onDelete}
          />
        ))}
      </div>

      {drafts.length > 0 && (
        <label className="flex w-fit items-center gap-2 text-sm font-medium">
          <input
            type="checkbox"
            checked={checkedSampleCount !== null}
            onChange={(event) =>
              onCheckedSampleCountChange(event.target.checked ? drafts.length : null)
            }
            className="size-4 accent-primary"
          />
          표본 {drafts.length}편을 읽어 봤습니다
        </label>
      )}
    </div>
  )
}

// 성공 탭 맨 위의 접힌 상자 "무작위 표본 10편 읽어 보기"(사용자 결정 Q1). 펼치기 전에는 표본을 받지 않는다
function SampleDisclosure(props: SampleTabProps) {
  const [isOpen, setIsOpen] = useState(false)
  const panelId = useId()
  return (
    <div className="rounded-lg border border-border bg-background">
      <Button
        type="button"
        variant="ghost"
        className="w-full justify-start"
        aria-expanded={isOpen}
        aria-controls={panelId}
        onClick={() => setIsOpen((prev) => !prev)}
      >
        {isOpen ? <ChevronDownIcon aria-hidden="true" /> : <ChevronRightIcon aria-hidden="true" />}
        무작위 표본 {SAMPLE_SIZE}편 읽어 보기
      </Button>
      <div id={panelId} hidden={!isOpen} className="p-3 pt-1">
        {isOpen && <SampleTab {...props} />}
      </div>
    </div>
  )
}

// 탭마다 맨 위에 놓는 한 줄 설명
function GradeIntro({ grade }: { grade: OverviewDraftStatus }) {
  if (grade === 'PASSED') {
    return (
      <p className="text-sm break-keep text-muted-foreground">
        자동 검사에서 걸린 것이 없는 초안입니다. 아래 [성공 초안 전부 반영]으로 한꺼번에 반영할 수
        있습니다.
      </p>
    )
  }
  if (grade === 'WARNING') {
    return (
      <OverviewNoticeBand tone="attention" title="사람이 읽고 승인해야 합니다" role="note">
        실패는 아니지만 확인이 필요한 초안입니다. [전부 반영]에는 들어가지 않으니 한 편씩 읽고
        [승인하고 반영]을 누르거나, 고른 뒤 [선택한 n편 반영]을 누르세요.
      </OverviewNoticeBand>
    )
  }
  return (
    <p className="text-sm break-keep text-muted-foreground">
      검사에 걸린 초안은 그대로 반영할 수 없습니다. [수정]으로 고쳐 다시 검사하거나 [다시 번역]을
      누르세요.
    </p>
  )
}

// 등급별 빈 상태 문구
function GradeEmpty({ grade }: { grade: OverviewDraftStatus }) {
  if (grade === 'PASSED') {
    return (
      <EmptyState
        title="성공한 초안이 없습니다"
        description="위 일괄 번역이나 아래 번역 후보에서 초안을 만들어 주세요."
      />
    )
  }
  if (grade === 'WARNING') {
    return (
      <EmptyState
        title="승인을 기다리는 초안이 없습니다"
        description="자동 검사에서 주의가 나온 초안이 생기면 여기에 모입니다."
      />
    )
  }
  return <EmptyState title="검사에 걸린 초안이 없습니다" />
}

interface DraftListTabProps {
  grade: OverviewDraftStatus
  edit: ReturnType<typeof useDraftEditSession> // 수정 칸 상태(한 번에 하나, 부모가 들고 있다)
  guardDiscard: (action: () => void) => void // 고친 내용이 있으면 확인을 거쳐 action을 실행한다
  onApplySelected: (draftIds: string[], warningCount: number) => void
  onApplyOne: (draft: OverviewDraft) => void
  applyingDraftId: string | null
  onRetranslate: (draft: OverviewDraft) => void
  retranslatingTitleId: number | null
  retranslateBlockedReason: string | null
  isBulkRunning: boolean
  isApplyPending: boolean
  onDelete: (draft: OverviewDraft) => void
  deletingDraftId: string | null
}

// 성공 / 승인 대기 / 실패 탭 하나: 쪽 넘김 있는 목록. 성공·주의 탭에는 체크와 선택 반영이 있다
function DraftListTab({
  grade,
  edit,
  guardDiscard,
  onApplySelected,
  onApplyOne,
  applyingDraftId,
  onRetranslate,
  retranslatingTitleId,
  retranslateBlockedReason,
  isBulkRunning,
  isApplyPending,
  onDelete,
  deletingDraftId,
}: DraftListTabProps) {
  const [page, setPage] = useState(0)
  const [selectedIds, setSelectedIds] = useState<ReadonlySet<string>>(new Set())
  const drafts = useOverviewDrafts(grade, page)
  const isSelectable = grade !== 'FAILED'

  if (drafts.isPending) return <LoadingState message="번역 초안을 불러오는 중입니다" />
  if (drafts.isError) return <ErrorState error={drafts.error} onRetry={() => drafts.refetch()} />

  // 방금 저장한 초안은 서버가 돌려준 새 값(등급 포함)으로 보인다. 목록은 수정 칸을 닫을 때 새로 받는다
  const content = drafts.data.content.map(edit.withSaved)
  if (content.length === 0 && drafts.data.totalElements === 0) {
    return (
      <div className="grid gap-3">
        {grade === 'PASSED' && <GradeIntro grade={grade} />}
        <GradeEmpty grade={grade} />
      </div>
    )
  }

  // 선택은 지금 쪽에 보이는 초안만 센다. 반영·삭제로 사라진 초안의 ID가 남아 있어도 개수에 들지 않는다
  const selectedDrafts = content.filter((d) => selectedIds.has(d.draftId))
  const warningSelectedCount = selectedDrafts.filter((d) => getDraftGrade(d) === 'WARNING').length
  const applyBlockedReason = isBulkRunning
    ? BULK_RUNNING_APPLY_REASON
    : selectedDrafts.length === 0
      ? '반영할 초안을 먼저 선택하세요.'
      : null

  function toggle(draftId: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(draftId)) next.delete(draftId)
      else next.add(draftId)
      return next
    })
  }

  function toggleAll(isChecked: boolean) {
    setSelectedIds(isChecked ? new Set(content.map((d) => d.draftId)) : new Set())
  }

  function changePage(next: number) {
    // 쪽을 넘기면 선택을 비운다(보이지 않는 쪽의 초안이 선택된 채 반영되는 일을 막는다). 고친 내용이 있으면 먼저 확인한다
    guardDiscard(() => {
      edit.close()
      setSelectedIds(new Set())
      setPage(next)
    })
  }

  // [수정]: 다른 칸에 고친 내용이 있으면 확인한 뒤 연다(칸은 한 번에 하나)
  function handleEdit(draft: OverviewDraft) {
    guardDiscard(() => {
      edit.close()
      edit.start(draft)
    })
  }

  function getBusyAction(draftId: string): DraftBusyAction {
    if (applyingDraftId === draftId) return 'apply'
    if (deletingDraftId === draftId) return 'delete'
    const draft = content.find((d) => d.draftId === draftId)
    if (draft && retranslatingTitleId === draft.titleId) return 'retranslate'
    return null
  }

  function renderEditor(draft: OverviewDraft) {
    const session: DraftEditSession | null = edit.session
    if (!session || session.draftId !== draft.draftId) return null
    return (
      <OverviewDraftEditor
        original={draft.original}
        value={session.value}
        onChange={edit.setValue}
        isChanged={session.value !== session.baseline}
        isSaving={edit.isSaving}
        checkResult={session.saved ? toCheckResult(session.saved) : null}
        onSave={edit.save}
        onClose={() => guardDiscard(edit.close)}
        blockedReason={isBulkRunning ? BULK_RUNNING_WRITE_REASON : null}
        errorText={edit.errorText}
      />
    )
  }

  return (
    <div className="grid gap-3">
      <GradeIntro grade={grade} />
      <OverviewDraftTable
        drafts={content}
        isSelectable={isSelectable}
        selectedIds={selectedIds}
        onToggle={toggle}
        onToggleAll={toggleAll}
        renderEditor={renderEditor}
        onEdit={handleEdit}
        onApply={onApplyOne}
        onRetranslate={onRetranslate}
        onDelete={onDelete}
        getBusyAction={getBusyAction}
        blockedReason={isBulkRunning ? BULK_RUNNING_WRITE_REASON : null}
        retranslateBlockedReason={retranslateBlockedReason}
      />
      <OverviewPager
        page={drafts.data.page}
        totalPages={drafts.data.totalPages}
        label={GRADE_VIEW[grade].pagerLabel}
        onPageChange={changePage}
      />
      {isSelectable && (
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
          <LlmWriteButton
            type="button"
            variant="outline"
            blockedReason={applyBlockedReason}
            blockedReasonId={`llm-apply-selected-reason-${grade}`}
            isBusy={isApplyPending}
            onClick={() =>
              onApplySelected(
                selectedDrafts.map((d) => d.draftId),
                warningSelectedCount,
              )
            }
          >
            선택한 {selectedDrafts.length}편 반영
          </LlmWriteButton>
          {applyBlockedReason && (
            <p id={`llm-apply-selected-reason-${grade}`} className="text-sm text-muted-foreground">
              {applyBlockedReason}
            </p>
          )}
        </div>
      )}
    </div>
  )
}

interface OverviewDraftReviewProps {
  passedTotal: number | null // 성공 초안 수. 아직 모르면 null
  warningTotal: number | null // 주의(승인 대기) 초안 수. 아직 모르면 null
  failedTotal: number | null // 실패 초안 수. 아직 모르면 null
  checkedSampleCount: number | null // 표본을 읽어 봤다고 표시한 편수. 확인 전이면 null
  onCheckedSampleCountChange: (count: number | null) => void
  isBulkRunning: boolean // 일괄 번역이 실행 중인지(반영·수정 저장을 막는다)
  isApplyPending: boolean // 반영 요청을 보내는 중인지(버튼을 막는다)
  onApplySelected: (draftIds: string[], warningCount: number) => void // 고른 초안 반영 확인 대화상자를 연다(warningCount: 그중 주의 편수)
  onApplyAll: () => void // 성공 초안 전부 반영 확인 대화상자를 연다
  onApplyOne: (draft: OverviewDraft) => void // 한 편만 바로 반영(확인창 없이)
  applyingDraftId: string | null // 한 편 반영 요청 중인 초안(그 버튼만 "반영하는 중…")
  onRetranslate: (draft: OverviewDraft) => void // 같은 작품을 다시 번역
  retranslatingTitleId: number | null // 다시 번역 요청 중인 작품
  retranslateBlockedReason: string | null // [다시 번역]만 막는 이유(번역 기능 꺼짐, 다른 작품 번역 중)
}

/**
 * "초안 검토" 상자다: 탭 3개(성공 / 승인 대기 · 주의 / 실패)와 맨 아래 [성공 초안 전부 반영].
 *
 * 탭을 이 상자 안에서만 쓴 이유: 화면 전체를 탭으로 나누면 번역 현황·일괄 진행·반영 결과를 왔다 갔다 해야 한다.
 * 한 화면에 위에서 아래로 두고, 초안 보는 방법만 탭으로 바꾼다(사용자 결정).
 * 사용하지 않는 탭의 내용은 그려지지 않아(TabsContent), 열어 보기 전에는 그 탭의 목록을 불러오지 않는다.
 * 개수 숫자는 부모가 size=1 조회의 totalElements로 알려 준다.
 *
 * 수정 칸 상태를 이 컴포넌트가 들고 있는 이유: 탭 이동·쪽 넘김·다른 항목의 [수정]처럼 수정 칸을 버리게 되는 동작이
 * 여러 곳에 있어, 한 곳에서 "고친 내용을 저장하지 않고 닫을까요?"를 묻기 위해서다.
 */
export function OverviewDraftReview({
  passedTotal,
  warningTotal,
  failedTotal,
  checkedSampleCount,
  onCheckedSampleCountChange,
  isBulkRunning,
  isApplyPending,
  onApplySelected,
  onApplyAll,
  onApplyOne,
  applyingDraftId,
  onRetranslate,
  retranslatingTitleId,
  retranslateBlockedReason,
}: OverviewDraftReviewProps) {
  const [tab, setTab] = useState<OverviewDraftStatus>('PASSED')
  const { handleDelete, deletingDraftId } = useDraftDeleteHandler()
  const edit = useDraftEditSession()
  // 고친 내용을 버리기 전에 물어볼 때, 확인되면 실행할 동작을 담아 둔다(함수를 state에 넣으려면 () => 로 감싼다)
  const [pendingDiscard, setPendingDiscard] = useState<(() => void) | null>(null)
  // 다시 번역하려는 초안이 직접 고친 것이면 확인을 거친다(고친 글을 되살릴 수 없다)
  const [retranslateTarget, setRetranslateTarget] = useState<OverviewDraft | null>(null)

  function guardDiscard(action: () => void) {
    if (edit.isDirty) setPendingDiscard(() => action)
    else action()
  }

  function handleRetranslate(draft: OverviewDraft) {
    if (draft.isEdited) setRetranslateTarget(draft)
    else onRetranslate(draft)
  }

  const applyAllBlockedReason = isBulkRunning
    ? BULK_RUNNING_APPLY_REASON
    : passedTotal === null
      ? '초안 개수를 불러오는 중입니다.'
      : passedTotal === 0
        ? '반영할 성공 초안이 없습니다.'
        : null

  const counts: Record<OverviewDraftStatus, number | null> = {
    PASSED: passedTotal,
    WARNING: warningTotal,
    FAILED: failedTotal,
  }
  const sampleProps: SampleTabProps = {
    checkedSampleCount,
    onCheckedSampleCountChange,
    onDelete: handleDelete,
    deletingDraftId,
  }

  return (
    <div className="grid gap-4">
      <Tabs
        value={tab}
        onValueChange={(value) =>
          guardDiscard(() => {
            edit.close()
            setTab(value as OverviewDraftStatus)
          })
        }
      >
        <TabsList variant="line" className="h-auto w-full flex-wrap justify-start">
          {(['PASSED', 'WARNING', 'FAILED'] as const).map((grade) => (
            <TabsTrigger key={grade} value={grade}>
              {GRADE_VIEW[grade].tabName}
              {counts[grade] !== null ? ` (${counts[grade]})` : ''}
            </TabsTrigger>
          ))}
        </TabsList>
        {(['PASSED', 'WARNING', 'FAILED'] as const).map((grade) => (
          <TabsContent key={grade} value={grade} className="grid min-w-0 grid-cols-1 gap-3 pt-2">
            {grade === 'PASSED' && <SampleDisclosure {...sampleProps} />}
            <DraftListTab
              grade={grade}
              edit={edit}
              guardDiscard={guardDiscard}
              onApplySelected={onApplySelected}
              onApplyOne={onApplyOne}
              applyingDraftId={applyingDraftId}
              onRetranslate={handleRetranslate}
              retranslatingTitleId={retranslatingTitleId}
              retranslateBlockedReason={retranslateBlockedReason}
              isBulkRunning={isBulkRunning}
              isApplyPending={isApplyPending}
              onDelete={handleDelete}
              deletingDraftId={deletingDraftId}
            />
          </TabsContent>
        ))}
      </Tabs>

      <p className="text-xs text-muted-foreground">
        초안은 서버에 최대 7일 보관되며, 그 전에 오래된 것부터 지워질 수 있습니다.
      </p>

      <div className="grid gap-2 border-t border-border pt-4">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          <LlmWriteButton
            type="button"
            size="lg"
            blockedReason={applyAllBlockedReason}
            blockedReasonId="llm-apply-all-reason"
            isBusy={isApplyPending}
            onClick={onApplyAll}
          >
            성공 초안 전부 반영 ({passedTotal ?? 0}편)
          </LlmWriteButton>
          <p className="text-sm text-muted-foreground">
            성공 {passedTotal ?? '-'}편 · 표본 확인 {checkedSampleCount !== null ? '완료' : '안 함'}
          </p>
          {applyAllBlockedReason && (
            <p id="llm-apply-all-reason" className="text-sm text-muted-foreground">
              {applyAllBlockedReason}
            </p>
          )}
        </div>
        <p className="text-sm break-keep text-muted-foreground">
          성공 초안만 반영합니다. 주의 {warningTotal ?? '-'}편과 실패 {failedTotal ?? '-'}편은
          들어가지 않습니다.
        </p>
      </div>

      {/* 고친 내용을 저장하지 않고 떠나려 할 때(탭·쪽 이동, 다른 항목의 [수정], [닫기]) 묻는 대화상자 */}
      <AlertDialog
        open={pendingDiscard !== null}
        onOpenChange={(isOpen) => {
          if (!isOpen) setPendingDiscard(null)
        }}
      >
        <AlertDialogContent className="sm:max-w-md">
          <AlertDialogHeader>
            <AlertDialogTitle>고친 내용을 저장하지 않고 닫을까요?</AlertDialogTitle>
            <AlertDialogDescription>
              저장하지 않은 번역문은 사라지고 되살릴 수 없습니다.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>계속 고치기</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                const action = pendingDiscard
                setPendingDiscard(null)
                action?.()
              }}
            >
              닫기
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* 직접 고친 초안을 다시 번역하면 고친 글이 새 번역으로 바뀐다는 확인(사용자 결정 Q3) */}
      <AlertDialog
        open={retranslateTarget !== null}
        onOpenChange={(isOpen) => {
          if (!isOpen) setRetranslateTarget(null)
        }}
      >
        <AlertDialogContent className="sm:max-w-md">
          <AlertDialogHeader>
            <AlertDialogTitle>다시 번역할까요?</AlertDialogTitle>
            <AlertDialogDescription>
              {retranslateTarget ? `「${retranslateTarget.title}」의 ` : ''}고친 번역문이 새
              번역으로 바뀝니다. 고친 내용은 되살릴 수 없습니다.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>취소</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                const target = retranslateTarget
                setRetranslateTarget(null)
                if (target) onRetranslate(target)
              }}
            >
              다시 번역
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
