import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import type { OverviewCandidate, OverviewDraft } from '@/api/generated/model'
import { isApiError } from '@/api/http'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { LoadingState } from '@/components/common/LoadingState'
import {
  ApplyDraftsDialog,
  type ApplyTarget,
} from '@/features/admin/components/overview/ApplyDraftsDialog'
import {
  OverviewApplyResult,
  type ApplyResultView,
} from '@/features/admin/components/overview/OverviewApplyResult'
import { OverviewBulkPanel } from '@/features/admin/components/overview/OverviewBulkPanel'
import { OverviewBulkRevertSection } from '@/features/admin/components/overview/OverviewBulkRevertSection'
import { OverviewCandidateTable } from '@/features/admin/components/overview/OverviewCandidateTable'
import { OverviewDraftReview } from '@/features/admin/components/overview/OverviewDraftReview'
import { OverviewNoticeBand } from '@/features/admin/components/overview/OverviewNoticeBand'
import { OverviewPager } from '@/features/admin/components/overview/OverviewPager'
import { OverviewSummaryPanel } from '@/features/admin/components/overview/OverviewSummaryPanel'
import { RevertByTitleIdForm } from '@/features/admin/components/overview/RevertByTitleIdForm'
import {
  RevertOverviewDialog,
  type RevertTarget,
} from '@/features/admin/components/overview/RevertOverviewDialog'
import {
  useApplyOverviewDrafts,
  useCreateOverviewDraft,
  useDraftTitleSnapshot,
  useInvalidateOverviewQueries,
  useOverviewCandidates,
  useOverviewDraftCounts,
  useRevertTitleOverview,
} from '@/features/admin/hooks/useAdminOverviewTranslate'
import { getDraftGradeToast } from '@/features/admin/overviewRules'
import { getErrorMessage } from '@/lib/errorMessages'

// 이 화면의 상자(섹션) 모양은 수집 화면(CollectScreen)의 카드와 같다
const SECTION_CLASS = 'grid gap-3 rounded-xl border border-border bg-card p-4'

// 쓸 수 없는 이유 문장
const DISABLED_REASON = '번역 기능이 꺼져 있어 초안을 만들 수 없습니다.'
const BULK_RUNNING_REASON = '일괄 번역이 실행 중이라 끝난 뒤에 할 수 있습니다.'
const CREATING_REASON = '다른 작품을 번역하는 중이라 끝난 뒤에 할 수 있습니다.'

// 섹션 하나: 제목(h2) + 내용
function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className={SECTION_CLASS}>
      <h2 id={id} className="text-lg font-semibold">
        {title}
      </h2>
      {children}
    </section>
  )
}

// 오류 객체에서 화면에 보일 문장을 꺼낸다(ApiError가 아니면 기본 문장)
function errorText(error: unknown): string {
  return isApiError(error) ? error.userMessage : getErrorMessage(null)
}

/**
 * 관리자 "LLM 줄거리 번역" 화면(/admin/llm)의 본문이다.
 *
 * 위에서 아래로: 제목·소개 → 번역 현황 → 일괄 번역 → 초안 검토(탭) → 반영 결과(반영 직후에만) → 번역 후보 → 작품 번호로 되돌리기.
 * 영어 줄거리만 있는 작품의 한국어 번역 초안을 Gemini(LLM)로 만들고, 자동 검사를 통과한 초안을 사람이 확인한 뒤 반영한다.
 * 반영하기 전에는 이용자 화면에 보이지 않는다.
 *
 * 이 컴포넌트가 상태를 들고 있는 이유(여러 상자가 함께 쓰는 것만 둔다):
 * - 번역 중인 작품(단건 생성)과 일괄 실행 여부: 서로 겹치면 호출 상한을 같이 쓰므로, 한쪽이 돌면 다른 쪽 버튼을 모두 막는다.
 * - 반영·되돌리기 확인 대화상자: 여러 상자(초안 검토, 반영 결과, 작품 번호 입력)가 같은 대화상자를 연다.
 * - 표본 확인 표시: 초안 검토(체크)와 전부 반영 대화상자(경고)가 같이 본다.
 * 일괄 진행 상황은 저장하지 않고 초안 목록 응답의 bulk를 읽는다(useOverviewDraftCounts). 실행 중이면 그 훅이 5초마다 다시 불러온다.
 */
export function OverviewTranslateScreen() {
  const [candidatePage, setCandidatePage] = useState(0)
  const candidates = useOverviewCandidates(candidatePage)
  const counts = useOverviewDraftCounts()
  const createDraft = useCreateOverviewDraft()
  const applyDrafts = useApplyOverviewDrafts()
  const revertOverview = useRevertTitleOverview()
  const invalidate = useInvalidateOverviewQueries()
  const snapshotTitles = useDraftTitleSnapshot()

  const [checkedSampleCount, setCheckedSampleCount] = useState<number | null>(null)
  const [announcement, setAnnouncement] = useState('')
  const [applyTarget, setApplyTarget] = useState<ApplyTarget | null>(null)
  const [selectedDraftIds, setSelectedDraftIds] = useState<string[]>([])
  const [applyResult, setApplyResult] = useState<ApplyResultView | null>(null)
  const [revertTarget, setRevertTarget] = useState<RevertTarget | null>(null)
  const [hasApplyConflict, setHasApplyConflict] = useState(false)
  const [cooldownTitleId, setCooldownTitleId] = useState<number | null>(null)

  const summary = candidates.data?.summary ?? null
  const bulkRun = counts.bulkRun
  const isBulkRunning = bulkRun?.state === 'RUNNING'
  const bulkState = bulkRun?.state ?? null

  // 일괄 번역의 상태가 바뀐 순간을 알아챈다. 렌더 중에 이전 값과 비교하는 방식(effect가 아니라)이라 불필요한 한 번 더 그리기가 없다.
  // 실행 중 → 완료·중단으로 바뀌면: 낭독기 알림 문장을 한 번 정하고, 새 초안이 생겼으니 "표본을 읽어 봤습니다" 표시를 푼다.
  // 새로고침 직후에는 이전 값과 지금 값이 같아(처음부터 완료) 알리지 않는다
  const [previousBulkState, setPreviousBulkState] = useState(bulkState)
  if (previousBulkState !== bulkState) {
    setPreviousBulkState(bulkState)
    if (previousBulkState === 'RUNNING') {
      setCheckedSampleCount(null)
      const warningCount = bulkRun?.warningCount ?? 0
      setAnnouncement(
        bulkState === 'COMPLETED'
          ? warningCount > 0
            ? `일괄 번역을 마쳤습니다. 주의 ${warningCount}편은 [승인 대기] 탭에서 승인해 주세요.`
            : '일괄 번역을 마쳤습니다. 아래 초안 검토에서 결과를 확인하세요.'
          : '일괄 번역이 멈췄습니다. 위 일괄 번역 상자에서 이유를 확인하세요.',
      )
    }
  }

  // 같은 전환을 감지해 후보·현황·초안 개수를 한 번 다시 불러온다. 서버 요청이라 effect에서 한다.
  // 폴링 중에는 개수가 계속 갱신되지만, 후보 목록과 현황(대기 수·사용량)은 폴링하지 않아 끝난 순간에 한 번 맞춰야 한다
  const lastBulkStateRef = useRef(bulkState)
  useEffect(() => {
    if (lastBulkStateRef.current === 'RUNNING' && bulkState !== 'RUNNING') {
      void invalidate()
    }
    lastBulkStateRef.current = bulkState
  }, [bulkState, invalidate])

  // 번역 초안 만들기/일괄 시작을 막는 이유(우선순위: 기능 꺼짐 > 일괄 실행 중 > 다른 작품 번역 중). 조회·반영·삭제는 막지 않는다
  const createBlockedReason =
    summary && !summary.isEnabled
      ? DISABLED_REASON
      : isBulkRunning
        ? BULK_RUNNING_REASON
        : createDraft.isPending
          ? CREATING_REASON
          : null
  const pendingTitleId = createDraft.isPending ? createDraft.variables.titleId : null
  // 초안의 [다시 번역]만 막는 이유(일괄 실행 중은 항목 쪽이 따로 모든 쓰기를 막는다). 수정·반영·삭제는 키가 없어도 된다
  const retranslateBlockedReason =
    summary && !summary.isEnabled ? DISABLED_REASON : createDraft.isPending ? CREATING_REASON : null
  // 한 편 반영 요청 중인 초안 ID(그 항목의 버튼만 "반영하는 중…"이 된다)
  const applyingDraftId =
    applyDrafts.isPending && applyDrafts.variables.draftIds?.length === 1
      ? applyDrafts.variables.draftIds[0]
      : null

  // 줄거리를 바꾸는 작업이 수집·재계산 등과 겹쳤을 때(409): 빨간 오류가 아니라 "지금 상태 안내"로 보인다
  function showApplyConflict() {
    setHasApplyConflict(true)
    toast.info(getErrorMessage('LLM_APPLY_CONFLICT'))
  }

  // 작품 하나의 번역 초안을 만든다(번역 후보의 [만들기]와 초안의 [다시 번역]이 함께 쓴다).
  // 결과는 등급 3종(성공 / 주의 / 실패) 토스트로 알린다. verb는 토스트 문장의 서술이다
  function runCreate(titleId: number, verb: string, isFromCandidate: boolean) {
    // 연타 방지: 번역 중이면 새 요청을 보내지 않는다
    if (createDraft.isPending) return
    setCooldownTitleId(null)
    createDraft.mutate(
      { titleId },
      {
        onSuccess: (draft) => {
          const result = getDraftGradeToast(draft, verb)
          if (result.level === 'success') toast.success(result.text)
          else toast.warning(result.text)
        },
        onError: (error) => {
          const code = isApiError(error) ? error.errorCode : null
          if (code === 'LLM_TITLE_COOLDOWN' && isFromCandidate) {
            // 같은 작품을 방금 번역했다는 짧은 안내는 그 행의 버튼 옆에 둔다
            setCooldownTitleId(titleId)
            return
          }
          if (code === 'LLM_NOT_TRANSLATABLE') void invalidate() // 이미 한국어가 됐거나 줄거리가 없는 작품이라 목록을 새로 맞춘다
          toast.error(errorText(error))
        },
      },
    )
  }

  function handleCreate(candidate: OverviewCandidate) {
    runCreate(candidate.titleId, '번역 초안으로 만들었습니다', true)
  }

  function handleRetranslate(draft: OverviewDraft) {
    runCreate(draft.titleId, '다시 번역했습니다', false)
  }

  function openApplySelected(draftIds: string[], warningCount: number) {
    setSelectedDraftIds(draftIds)
    setApplyTarget({ mode: 'selected', count: draftIds.length, warningCount })
  }

  function openApplyAll() {
    setApplyTarget({
      mode: 'allPassed',
      count: counts.passedTotal ?? 0,
      warningCount: counts.warningTotal,
    })
  }

  // 반영 요청이 실패했을 때: 수집·재계산 등과 겹친 409는 안내 띠, 나머지는 오류 토스트
  function handleApplyError(error: unknown) {
    if (isApiError(error) && error.errorCode === 'LLM_APPLY_CONFLICT') {
      showApplyConflict()
      return
    }
    toast.error(errorText(error))
  }

  // 한 편만 바로 반영한다(확인 대화상자 없이, 사용자 결정 Q2). 한 편이고 반영 뒤에도 반영 결과 상자에서 되돌릴 수 있어서다
  function handleApplyOne(draft: OverviewDraft) {
    // 1. 연타 방지
    if (applyDrafts.isPending) return
    // 2. 반영 결과 상자에 쓸 제목(이 한 편)
    const titlesByDraftId = new Map([[draft.draftId, draft.title]])
    // 3. 요청. 반영되면 토스트, 건너뛰면 이유를 반영 결과 상자에서 보인다(두 경우 모두 결과 상자를 갱신한다)
    applyDrafts.mutate(
      { draftIds: [draft.draftId] },
      {
        onSuccess: (result) => {
          setApplyResult({ result, titlesByDraftId })
          setHasApplyConflict(false)
          if (result.appliedCount > 0) toast.success(`「${draft.title}」을 반영했습니다.`)
          else toast.info('반영하지 못하고 건너뛰었습니다. 아래 반영 결과에서 이유를 확인하세요.')
        },
        onError: handleApplyError,
      },
    )
  }

  function confirmApply() {
    // 1. 연타 방지: 반영 중이면 두 번째 요청을 보내지 않는다
    if (applyDrafts.isPending || !applyTarget) return
    // 2. 반영하면 반영된 초안이 목록에서 사라지므로, 결과 화면에 쓸 제목을 먼저 떠 둔다
    const titlesByDraftId = snapshotTitles()
    // 3. 요청 본문: 전부 반영은 서버가 반영 순간의 성공 초안을 모두 고른다(주의·실패는 들어가지 않는다)
    const body =
      applyTarget.mode === 'allPassed' ? { allPassed: true } : { draftIds: selectedDraftIds }
    applyDrafts.mutate(body, {
      onSuccess: (result) => {
        setApplyResult({ result, titlesByDraftId })
        setApplyTarget(null)
        setHasApplyConflict(false)
        if (result.appliedCount > 0) toast.success(`${result.appliedCount}편을 반영했습니다.`)
        if (result.skippedCount > 0) {
          toast.info(
            `${result.skippedCount}편은 건너뛰었습니다. 아래 반영 결과에서 이유를 확인하세요.`,
          )
        }
      },
      onError: (error) => {
        setApplyTarget(null)
        handleApplyError(error)
      },
    })
  }

  function confirmRevert() {
    if (revertOverview.isPending || !revertTarget) return
    const { titleId } = revertTarget
    revertOverview.mutate(
      { titleId },
      {
        onSuccess: () => {
          setRevertTarget(null)
          setHasApplyConflict(false)
          toast.success(
            `작품 #${titleId}의 한국어 줄거리를 지웠습니다. 다음 수집 때 영어 줄거리가 다시 채워집니다.`,
          )
        },
        onError: (error) => {
          setRevertTarget(null)
          if (isApiError(error) && error.errorCode === 'LLM_APPLY_CONFLICT') {
            showApplyConflict()
            return
          }
          toast.error(errorText(error))
        },
      },
    )
  }

  // 번역 현황 상자의 내용
  let summaryContent: ReactNode
  if (candidates.isPending) {
    summaryContent = <LoadingState message="번역 현황을 불러오는 중입니다" />
  } else if (candidates.isError) {
    summaryContent = <ErrorState error={candidates.error} onRetry={() => candidates.refetch()} />
  } else {
    summaryContent = <OverviewSummaryPanel summary={candidates.data.summary} />
  }

  // 번역 후보 상자의 내용
  let candidateContent: ReactNode
  if (candidates.isPending) {
    candidateContent = <LoadingState message="번역 후보를 불러오는 중입니다" />
  } else if (candidates.isError) {
    candidateContent = <ErrorState error={candidates.error} onRetry={() => candidates.refetch()} />
  } else if (candidates.data.content.length === 0 && candidates.data.totalElements === 0) {
    candidateContent = (
      <EmptyState
        title="번역할 작품이 없습니다"
        description="영어 줄거리만 있는 작품이 모두 처리됐습니다. 새로 수집된 작품이 생기면 여기에 나타납니다."
      />
    )
  } else {
    candidateContent = (
      <>
        <p className="text-sm text-muted-foreground">
          찜 많은 순, 같으면 인기순으로 정렬합니다. 전체{' '}
          {candidates.data.totalElements.toLocaleString('ko-KR')}편
        </p>
        <OverviewCandidateTable
          candidates={candidates.data.content}
          pendingTitleId={pendingTitleId}
          blockedReason={createBlockedReason}
          cooldownTitleId={cooldownTitleId}
          cooldownMessage={getErrorMessage('LLM_TITLE_COOLDOWN')}
          onCreate={handleCreate}
        />
        <OverviewPager
          page={candidates.data.page}
          totalPages={candidates.data.totalPages}
          label="번역 후보"
          onPageChange={(next) => {
            setCooldownTitleId(null)
            setCandidatePage(next)
          }}
        />
      </>
    )
  }

  // 번역 통계와 오래된 번역 되돌리기 상자의 내용(번역 수·가장 오래된 반영은 번역 현황 응답에 들어 있다)
  let bulkRevertContent: ReactNode
  if (candidates.isPending) {
    bulkRevertContent = <LoadingState message="번역 통계를 불러오는 중입니다" />
  } else if (candidates.isError) {
    bulkRevertContent = <ErrorState error={candidates.error} onRetry={() => candidates.refetch()} />
  } else {
    bulkRevertContent = (
      <OverviewBulkRevertSection
        translatedCount={candidates.data.summary.translatedCount}
        oldestTranslatedAt={candidates.data.summary.oldestTranslatedAt}
        blockedReason={isBulkRunning ? BULK_RUNNING_REASON : null}
        onConflict={showApplyConflict}
      />
    )
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="grid gap-2">
        <h1 className="text-2xl font-semibold">LLM 줄거리 번역</h1>
        <p className="text-sm break-keep text-muted-foreground">
          영어 줄거리만 있는 작품의 한국어 번역 초안을 Gemini(LLM)로 만들고, 자동 검사 결과(성공 /
          주의 / 실패)를 보며 확인한 뒤 반영합니다. 반영하기 전에는 이용자 화면에 보이지 않습니다.
        </p>
        <details className="text-sm">
          <summary className="w-fit cursor-pointer font-medium focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none">
            진행 순서 안내
          </summary>
          <p className="mt-1 break-keep text-muted-foreground">
            처음에는 20편을 만들어 하나씩 직접 읽어 보세요. 그다음부터는 무작위 표본 10편을 확인하고
            성공 초안을 한꺼번에 반영합니다. 주의 초안은 한 편씩 읽고 승인하며, 실패 초안은 고치거나
            다시 번역합니다. 잘못 반영된 작품은 영어 줄거리로 되돌릴 수 있습니다.
          </p>
        </details>
      </div>

      <Section id="llm-summary-heading" title="번역 현황">
        {summaryContent}
      </Section>

      <Section id="llm-bulk-heading" title="일괄 번역">
        {counts.isError && (
          <ErrorState
            title="일괄 번역 진행 상황을 불러오지 못했습니다"
            error={counts.error}
            onRetry={() => counts.refetch()}
          />
        )}
        <OverviewBulkPanel
          summary={summary}
          bulkRun={bulkRun}
          lastCheckedAt={counts.lastCheckedAt}
          announcement={announcement}
          blockedReason={createBlockedReason}
          isProgressUnavailable={counts.isError || counts.isPending}
        />
      </Section>

      <Section id="llm-review-heading" title="초안 검토">
        {hasApplyConflict && (
          <OverviewNoticeBand tone="info" role="status">
            <span>{getErrorMessage('LLM_APPLY_CONFLICT')} </span>
            수집 화면에서 실행 상태를 확인할 수 있습니다.{' '}
            <Link to="/admin/collect" className="font-semibold underline underline-offset-2">
              수집 화면으로 가기
            </Link>
          </OverviewNoticeBand>
        )}
        <OverviewDraftReview
          passedTotal={counts.passedTotal}
          warningTotal={counts.warningTotal}
          failedTotal={counts.failedTotal}
          checkedSampleCount={checkedSampleCount}
          onCheckedSampleCountChange={setCheckedSampleCount}
          isBulkRunning={isBulkRunning}
          isApplyPending={applyDrafts.isPending}
          onApplySelected={openApplySelected}
          onApplyAll={openApplyAll}
          onApplyOne={handleApplyOne}
          applyingDraftId={applyingDraftId}
          onRetranslate={handleRetranslate}
          retranslatingTitleId={pendingTitleId}
          retranslateBlockedReason={retranslateBlockedReason}
        />
      </Section>

      {applyResult && (
        <Section id="llm-result-heading" title="반영 결과">
          <OverviewApplyResult view={applyResult} onRequestRevert={setRevertTarget} />
        </Section>
      )}

      <Section id="llm-candidates-heading" title="번역 후보">
        {candidateContent}
      </Section>

      <Section id="llm-bulk-revert-heading" title="번역 통계와 오래된 번역 되돌리기">
        {bulkRevertContent}
      </Section>

      <RevertByTitleIdForm
        onRequestRevert={(titleId) => setRevertTarget({ titleId, title: null })}
      />

      <ApplyDraftsDialog
        target={applyTarget}
        checkedSampleCount={checkedSampleCount}
        isPending={applyDrafts.isPending}
        onCancel={() => setApplyTarget(null)}
        onConfirm={confirmApply}
      />
      <RevertOverviewDialog
        target={revertTarget}
        isPending={revertOverview.isPending}
        onCancel={() => setRevertTarget(null)}
        onConfirm={confirmRevert}
      />
    </div>
  )
}
