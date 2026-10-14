import { useRef, useState, type ReactNode } from 'react'
import { RefreshCwIcon } from 'lucide-react'
import { toast } from 'sonner'
import { isApiError } from '@/api/http'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { CollectRunForm } from '@/features/admin/components/collect/CollectRunForm'
import {
  CollectRunTable,
  type RunFocusRequest,
} from '@/features/admin/components/collect/CollectRunTable'
import { RecoverRunDialog } from '@/features/admin/components/collect/RecoverRunDialog'
import { noticeFromError, type RunNotice } from '@/features/admin/components/collect/runNotice'
import { COLLECT_TIER_LABELS, findLastSuccess } from '@/features/admin/collectRules'
import {
  useCollectExecutions,
  useRecoverCollect,
  useRestartCollect,
  useRunCollect,
} from '@/features/admin/hooks/useAdminCollect'
import type { CollectExecution, CollectTier } from '@/api/generated/model'
import { useOttServices } from '@/hooks/useOttServices'
import { getTodayDateString } from '@/lib/dateUtils'
import { getErrorMessage } from '@/lib/errorMessages'
import { formatIsoDate, formatDateTime } from '@/lib/format'
import { josa } from '@/lib/josa'

// 로딩 중 자리표시. 회색 줄은 장식이라 낭독기에는 숨기고 상태 문장을 따로 읽힌다
function CollectSkeleton() {
  return (
    <div role="status" className="grid gap-3">
      <span className="sr-only">수집 실행 이력을 불러오는 중입니다</span>
      <div aria-hidden="true" className="grid gap-3">
        {[0, 1, 2].map((i) => (
          <Skeleton key={i} className="h-14 w-full" />
        ))}
      </div>
    </div>
  )
}

/**
 * SCR-16 수집 실행·이력 화면의 본문이다(Task 029).
 *
 * 위에서 아래로: 제목 → 마지막 성공 수집 한 줄 → 수동 실행 양식 → 실행 이력(+[새로 고침]) → JustWatch 출처.
 *
 * 실행 결과 안내(notice)를 이 화면이 들고 있는 이유: 수동 실행(양식)과 [다시 실행](이력 행)이 같은 안내 띠를 써야 해서다.
 * 한 곳에서 접수·충돌 안내를 보여 주면 운영자가 "방금 누른 것이 어떻게 됐는지"를 같은 자리에서 본다.
 *
 * 복구는 확인 대화상자를 거치고(RecoverRunDialog), 성공하면 이력을 다시 불러와(쿼리 무효화) 그 행이 "실패"로 바뀌고 [복구] 버튼이 사라진다.
 * 복구 응답의 canRestart가 참이면 사라진 버튼 대신 같은 행의 [다시 실행]으로 포커스를 옮겨, 키보드 사용자가 이어서 누를 수 있게 한다.
 * 거짓이면(다른 수집이 실행 중 등) [다시 실행]이 없으므로 그 행으로 포커스를 옮긴다.
 */
export function CollectScreen() {
  const executions = useCollectExecutions()
  const services = useOttServices()
  const run = useRunCollect()
  const restart = useRestartCollect()
  const recover = useRecoverCollect()

  const [notice, setNotice] = useState<RunNotice | null>(null)
  const [recoverTarget, setRecoverTarget] = useState<CollectExecution | null>(null)
  const [focusRequest, setFocusRequest] = useState<RunFocusRequest | null>(null)
  const recoverOpenerRef = useRef<HTMLElement | null>(null) // [복구]를 누른 버튼. 취소로 닫히면 포커스를 돌려줄 곳
  const focusNonce = useRef(0)

  const today = getTodayDateString()
  const list = executions.data?.content ?? [] // 이력은 페이지 형태라 content가 목록이다
  const serviceNames = new Map((services.data ?? []).map((s) => [s.ottServiceId, s.name]))
  const lastSuccess = findLastSuccess(list)

  function viewRun(executionId: number) {
    focusNonce.current += 1
    setFocusRequest({ executionId, nonce: focusNonce.current })
  }

  // 수동 실행과 다시 실행이 공유하는 결과 처리
  function handleAccepted(executionId: number, targetDate: string, tier: CollectTier) {
    setNotice({ kind: 'ACCEPTED', executionId, targetDate, tier })
  }

  function submitRun(targetDate: string, tier: CollectTier) {
    setNotice(null)
    run.mutate(
      { targetDate, tier },
      {
        onSuccess: (accepted) => handleAccepted(accepted.executionId, targetDate, tier),
        onError: (error) => setNotice(noticeFromError(error, targetDate, tier, list)),
      },
    )
  }

  function restartRun(execution: CollectExecution) {
    setNotice(null)
    restart.mutate(
      { executionId: execution.executionId },
      {
        onSuccess: (accepted) =>
          handleAccepted(accepted.executionId, execution.targetDate, execution.tier),
        onError: (error) => {
          const next = noticeFromError(error, execution.targetDate, execution.tier, list)
          // 다시 실행에는 날짜 입력이 없다. 날짜 거절(운영에서 오늘이 아닌 실행)은 양식의 날짜 칸이 아니라 오류 문장으로 보인다
          setNotice(next.kind === 'DATE_INVALID' ? { kind: 'ERROR', message: next.message } : next)
        },
      },
    )
  }

  function confirmRecover(execution: CollectExecution) {
    recover.mutate(
      { executionId: execution.executionId },
      {
        onSuccess: (recovered) => {
          const label = `실행 ${execution.executionId}${josa(String(execution.executionId), '을/를')} 실패로 기록했습니다.`
          // 복구 응답의 canRestart로 이어서 다시 실행할 수 있는지 안다(다른 수집이 돌거나, 운영에서 오늘이 아니면 거짓)
          if (!recovered.canRestart) {
            toast.success(label)
            // [복구] 버튼이 사라지므로 포커스를 그 행으로 옮겨 키보드 사용자가 위치를 잃지 않게 한다
            viewRun(execution.executionId)
            return
          }
          toast.success(`${label} [다시 실행]으로 이어서 수집하세요.`)
          // 이력을 다시 불러와 행이 바뀐 뒤에 [다시 실행]으로 포커스를 옮긴다(requestAnimationFrame은 다음 그리기 뒤에 한 번 더 시도)
          const moveFocus = (triesLeft: number) => {
            const buttons = document.querySelectorAll<HTMLElement>(
              `[data-restart-for="${execution.executionId}"]`,
            )
            const visible = Array.from(buttons).find((el) => el.getClientRects().length > 0)
            if (visible) visible.focus()
            else if (triesLeft > 0) setTimeout(() => moveFocus(triesLeft - 1), 100)
          }
          moveFocus(10)
        },
        onError: (error) =>
          toast.error(isApiError(error) ? error.userMessage : getErrorMessage(null)),
      },
    )
  }

  let history: ReactNode
  if (executions.isPending) {
    history = <CollectSkeleton />
  } else if (executions.isError) {
    history = <ErrorState error={executions.error} onRetry={() => executions.refetch()} />
  } else if (list.length === 0) {
    history = (
      <EmptyState
        title="아직 수집 실행 이력이 없습니다"
        description="위 양식에서 첫 수집을 실행하세요."
      />
    )
  } else {
    history = (
      <CollectRunTable
        executions={list}
        serviceNames={serviceNames}
        focusRequest={focusRequest}
        restartingExecutionId={restart.isPending ? (restart.variables?.executionId ?? null) : null}
        onRecover={(execution, opener) => {
          recoverOpenerRef.current = opener
          setRecoverTarget(execution)
        }}
        onRestart={restartRun}
      />
    )
  }

  return (
    <section className="flex flex-col gap-5">
      <h1 className="text-2xl font-semibold">수집 실행·이력</h1>

      <p className="text-sm text-muted-foreground">
        {lastSuccess
          ? `마지막 성공 수집: ${formatDateTime(lastSuccess.endedAt ?? lastSuccess.startedAt) ?? '-'} (${COLLECT_TIER_LABELS[lastSuccess.tier]} · 대상 ${formatIsoDate(lastSuccess.targetDate) ?? lastSuccess.targetDate})`
          : executions.isSuccess
            ? '아직 성공한 수집이 없습니다.'
            : '마지막 성공 수집을 확인하는 중입니다.'}
      </p>

      <CollectRunForm
        today={today}
        isPending={run.isPending}
        notice={notice}
        onSubmit={submitRun}
        onViewRun={viewRun}
      />

      <section aria-labelledby="collect-history-heading" className="grid gap-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="collect-history-heading" className="text-lg font-semibold">
            실행 이력
          </h2>
          <div className="flex flex-wrap items-center gap-3">
            {executions.isSuccess && (
              <span className="text-sm text-muted-foreground">
                마지막 갱신 {formatDateTime(new Date(executions.dataUpdatedAt)) ?? '-'}
              </span>
            )}
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={executions.isFetching}
              onClick={() => executions.refetch()}
            >
              <RefreshCwIcon
                aria-hidden="true"
                className={executions.isFetching ? 'animate-spin' : undefined}
              />
              새로 고침
            </Button>
          </div>
        </div>
        {history}
      </section>

      <RecoverRunDialog
        execution={recoverTarget}
        openerRef={recoverOpenerRef}
        onCancel={() => setRecoverTarget(null)}
        onConfirm={confirmRecover}
      />

      <SourceAttribution />
    </section>
  )
}
