// 보류(미사용): 모니터링 화면에서 수집 요약을 뺐다(수집 탭과 내용이 겹침, 2026-10-31). 수집 알림 등을 붙일 때 다시 쓸 수 있어 남겨 둔다

import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { ArrowRightIcon, RefreshCwIcon } from 'lucide-react'
import { cn } from 'cn'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { RunStatusBadge } from '@/features/admin/components/RunStatusBadge'
import { COLLECT_TIER_LABELS, findLastSuccess } from '@/features/admin/collectRules'
import type { RunDisplayStatus } from '@/features/admin/types'
import type { CollectExecution } from '@/api/generated/model'
import { formatDateTime, formatElapsed, formatIsoDate } from '@/lib/format'

export interface CollectRunSummaryProps {
  status: 'pending' | 'error' | 'success' // 이력 조회 상태(TanStack Query의 status를 그대로 넘긴다)
  executions: CollectExecution[] // 보여 줄 최근 실행. 시작 시각이 늦은 순(최신 먼저)으로, 몇 건만 잘라서 넘긴다(예: 5건)
  error?: unknown // status가 'error'일 때의 원인. ErrorState가 안내 문장을 꺼낸다
  onRetry?: () => void // 오류 상태의 [다시 시도]
  onRefresh?: () => void // [새로 고침]. 없으면 버튼을 숨긴다
  isRefreshing?: boolean // 다시 불러오는 중이면 새로 고침 아이콘이 돌고 버튼이 잠긴다
  updatedAt?: Date | null // 마지막으로 불러온 시각(dataUpdatedAt). 있으면 "마지막 갱신"으로 보인다
}

// 최신 실행 상태별 한 줄 판정 문장과 띠 모양. 띠 색은 보조이고, 상태는 배지(글자+아이콘+모양)와 문장이 함께 알린다
const HEADLINE: Record<RunDisplayStatus, { text: string; bandClassName: string }> = {
  COMPLETED: {
    text: '최근 수집이 정상적으로 끝났습니다.',
    bandClassName: 'border-border bg-muted/40',
  },
  RUNNING: {
    text: '수집이 진행 중입니다. 끝나면 결과가 여기에 반영됩니다.',
    bandClassName: 'border-info/40 bg-info-bg/40',
  },
  FAILED: {
    text: '최근 수집이 실패했습니다. 수집 화면에서 실패 사유를 확인하세요.',
    bandClassName: 'border-destructive/40 bg-destructive/5',
  },
  STOPPED: {
    text: '최근 수집이 중단된 채 남아 있습니다. 수집 화면에서 복구하세요.',
    bandClassName: 'border-attention-border bg-attention-bg/40',
  },
}

// 상태별 개수 줄에 쓰는 글자와 순서. 이상 신호(실패·중단됨)를 앞에 둬 먼저 눈에 들어오게 한다
const COUNT_ORDER: { status: RunDisplayStatus; label: string }[] = [
  { status: 'FAILED', label: '실패' },
  { status: 'STOPPED', label: '중단됨' },
  { status: 'RUNNING', label: '실행 중' },
  { status: 'COMPLETED', label: '성공' },
]

// 소요 시간 글자. 끝난 실행만 계산하고, 실행 중은 "진행 중", 끝난 시각이 없는 실패·중단됨은 "-"다(복구한 시각은 실제 종료가 아니다)
function elapsedText(execution: CollectExecution): string {
  if (execution.endedAt) return formatElapsed(execution.startedAt, execution.endedAt)
  return execution.status === 'RUNNING' ? '진행 중' : '-'
}

// 로딩 중 자리표시. 회색 줄은 장식이라 낭독기에는 숨기고 상태 문장을 따로 읽힌다(수집 화면과 같은 방식)
function SummarySkeleton() {
  return (
    <div role="status" className="grid gap-3">
      <span className="sr-only">최근 수집 실행을 불러오는 중입니다</span>
      <div aria-hidden="true" className="grid gap-3">
        <Skeleton className="h-16 w-full" />
        {[0, 1, 2].map((i) => (
          <Skeleton key={i} className="h-12 w-full" />
        ))}
      </div>
    </div>
  )
}

// 실행 한 건의 줄. 375px에서는 두 줄(배지·날짜 / 숫자들)로 접히고, 넓은 화면에서는 한 줄로 펼쳐진다
function SummaryRow({ execution }: { execution: CollectExecution }) {
  return (
    <li className="flex flex-wrap items-center gap-x-4 gap-y-1.5 px-4 py-3">
      <div className="flex min-w-0 flex-1 basis-56 flex-wrap items-center gap-2">
        <RunStatusBadge status={execution.status} />
        <span className="tabular-nums">
          {formatIsoDate(execution.targetDate) ?? execution.targetDate}
        </span>
        <span className="text-muted-foreground">· {COLLECT_TIER_LABELS[execution.tier]}</span>
      </div>
      {/* 숫자는 tabular-nums로 자리 폭을 고정해 위아래 줄끼리 훑어 비교하기 쉽게 한다 */}
      <dl className="flex flex-wrap gap-x-4 gap-y-1 text-sm tabular-nums">
        <div className="flex gap-1.5">
          <dt className="text-muted-foreground">시작</dt>
          <dd>{formatDateTime(execution.startedAt) ?? '-'}</dd>
        </div>
        <div className="flex gap-1.5">
          <dt className="text-muted-foreground">소요</dt>
          <dd>{elapsedText(execution)}</dd>
        </div>
        <div className="flex gap-1.5">
          <dt className="text-muted-foreground">변경</dt>
          <dd>{execution.changedCount.toLocaleString('ko-KR')}건</dd>
        </div>
      </dl>
    </li>
  )
}

/**
 * 관리자 모니터링(SCR-17)의 "수집이 정상적으로 돌았나" 요약이다(Task 097).
 *
 * 수집 화면(SCR-16)이 실행·복구·다시 실행을 하는 "작업" 화면이라면, 이 요약은 들어오자마자 이상 여부만 읽는 "확인" 화면이다.
 * 그래서 버튼(복구·다시 실행)과 변경 작품 펼침은 두지 않고, 자세히 볼 일이 있으면 [수집 화면에서 자세히 보기]로 보낸다.
 *
 * 위에서 아래로: 제목(+마지막 갱신·새로 고침) → 최신 실행 판정 띠 → 상태별 개수 → 최근 실행 목록 → 수집 화면 링크.
 * 판정 띠가 맨 위인 이유: 운영자가 가장 먼저 알고 싶은 것은 "지금 괜찮은가" 한 가지다. 숫자 목록은 그다음이다.
 *
 * 표 대신 목록(ul)을 쓴 이유: 열이 4개뿐이고 375px에서도 같은 마크업이 두 줄로 접히면 충분해서,
 * 수집 화면처럼 표·카드를 둘 다 그릴 필요가 없다.
 *
 * 데이터는 받지 않고 그리기만 한다. 조회·정렬·건수 자르기는 화면 조립 쪽(useCollectExecutions를 쓰는 곳)이 맡는다.
 */
export function CollectRunSummary({
  status,
  executions,
  error,
  onRetry,
  onRefresh,
  isRefreshing = false,
  updatedAt,
}: CollectRunSummaryProps) {
  let body: ReactNode
  if (status === 'pending') {
    body = <SummarySkeleton />
  } else if (status === 'error') {
    body = <ErrorState error={error} onRetry={onRetry} />
  } else if (executions.length === 0) {
    body = (
      <EmptyState
        title="아직 수집 실행 이력이 없습니다"
        description="첫 수집이 끝나면 여기에 결과가 요약됩니다."
      />
    )
  } else {
    // 1. 최신 실행으로 한 줄 판정을 고른다(목록은 최신 먼저로 받는다)
    const latest = executions[0]
    const headline = HEADLINE[latest.status]
    // 2. 최신이 성공이 아니면 "마지막 성공"을 함께 보여 줘 얼마나 오래 성공이 없었는지 알린다
    const lastSuccess = latest.status === 'COMPLETED' ? null : findLastSuccess(executions)
    // 3. 상태별 개수를 센다. 0건인 상태는 줄에서 뺀다(글자가 줄어 이상 신호가 더 잘 보인다)
    const counts = COUNT_ORDER.map((item) => ({
      ...item,
      count: executions.filter((e) => e.status === item.status).length,
    })).filter((item) => item.count > 0)

    body = (
      <div className="grid gap-3">
        {/* 판정 띠. role="status"를 주지 않는 이유: 새로 고침마다 낭독기가 끼어들어 읽으면 방해된다. 제목 바로 아래라 순서대로 읽힌다 */}
        <div className={cn('grid gap-2 rounded-lg border px-4 py-3', headline.bandClassName)}>
          <div className="flex flex-wrap items-center gap-2">
            <RunStatusBadge status={latest.status} />
            <p className="font-semibold break-keep">{headline.text}</p>
          </div>
          <p className="text-sm text-muted-foreground tabular-nums">
            최근 실행: {formatDateTime(latest.startedAt) ?? '-'} 시작 · 소요 {elapsedText(latest)} ·{' '}
            {COLLECT_TIER_LABELS[latest.tier]}
          </p>
          {latest.status !== 'COMPLETED' && (
            <p className="text-sm text-muted-foreground tabular-nums">
              {lastSuccess
                ? `마지막 성공: ${formatDateTime(lastSuccess.endedAt ?? lastSuccess.startedAt) ?? '-'}`
                : `최근 ${executions.length}건 중 성공한 수집이 없습니다.`}
            </p>
          )}
        </div>

        {/* 상태별 개수. 글자("실패 1건")로 쓰므로 색 없이도 읽힌다 */}
        <p className="text-sm">
          <span className="text-muted-foreground">최근 {executions.length}건: </span>
          {counts.map((item, index) => (
            <span key={item.status} className="tabular-nums">
              {index > 0 && <span aria-hidden="true"> · </span>}
              {index > 0 && <span className="sr-only">, </span>}
              <span
                className={cn(
                  item.status === 'FAILED' || item.status === 'STOPPED'
                    ? 'font-semibold'
                    : undefined,
                )}
              >
                {item.label} {item.count}건
              </span>
            </span>
          ))}
        </p>

        <ul
          aria-label="최근 수집 실행"
          className="divide-y divide-border rounded-xl border border-border bg-card"
        >
          {executions.map((execution) => (
            <SummaryRow key={execution.executionId} execution={execution} />
          ))}
        </ul>
      </div>
    )
  }

  return (
    <section aria-labelledby="collect-summary-heading" className="grid content-start gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="collect-summary-heading" className="text-lg font-semibold">
          수집 실행 요약
        </h2>
        <div className="flex flex-wrap items-center gap-3">
          {status === 'success' && updatedAt && (
            <span className="text-sm text-muted-foreground tabular-nums">
              마지막 갱신 {formatDateTime(updatedAt) ?? '-'}
            </span>
          )}
          {onRefresh && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={isRefreshing}
              onClick={onRefresh}
            >
              {/* motion-reduce: "움직임 줄이기"를 켠 사람에게는 회전을 멈춘다. 잠긴 버튼 모양으로도 진행 중이 읽힌다 */}
              <RefreshCwIcon
                aria-hidden="true"
                className={isRefreshing ? 'animate-spin motion-reduce:animate-none' : undefined}
              />
              새로 고침
            </Button>
          )}
        </div>
      </div>

      {body}

      {/* 오류·로딩 중에도 링크는 남겨 둔다. 요약이 안 보여도 수집 화면으로 바로 갈 수 있어야 한다 */}
      <Link
        to="/admin/collect"
        className="inline-flex min-h-10 w-fit items-center gap-1 rounded-sm text-sm text-primary underline underline-offset-3 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
      >
        수집 화면에서 자세히 보기
        <ArrowRightIcon aria-hidden="true" className="size-4" />
      </Link>
    </section>
  )
}
