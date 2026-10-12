import { Fragment, useEffect, useRef, useState } from 'react'
import {
  ChevronDownIcon,
  ChevronRightIcon,
  CircleXIcon,
  RotateCcwIcon,
  WrenchIcon,
} from 'lucide-react'
import { cn } from 'cn'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { CollectRunDetail } from '@/features/admin/components/collect/CollectRunDetail'
import { RunStatusBadge } from '@/features/admin/components/RunStatusBadge'
import { canRecover, canRestart, COLLECT_TIER_LABELS } from '@/features/admin/collectRules'
import type { CollectExecution } from '@/api/generated/model'
import { formatIsoDate, formatDateTime, formatElapsed } from '@/lib/format'

// "이력에서 보기"가 요청한 행 포커스. nonce가 바뀌면 새 요청이다(같은 행을 다시 눌러도 다시 이동한다)
export interface RunFocusRequest {
  executionId: number
  nonce: number
}

interface CollectRunTableProps {
  executions: CollectExecution[] // 실행 이력(최신 순)
  serviceNames: Map<number, string> // 서비스 ID → 서비스 이름
  focusRequest: RunFocusRequest | null // 포커스를 옮길 행 요청
  restartingExecutionId: number | null // 지금 [다시 실행]을 접수 중인 실행 ID(그 버튼만 비활성)
  onRecover: (execution: CollectExecution, opener: HTMLElement) => void // [복구]를 눌렀다(확인 대화상자를 연다). opener는 포커스를 돌려줄 버튼
  onRestart: (execution: CollectExecution) => void // [다시 실행]을 눌렀다
}

type Variant = 'table' | 'card'

// 화면에 실제로 보이는(표/카드 중 CSS로 숨겨지지 않은) 행 요소를 찾는다. 표와 카드를 둘 다 그리므로 같은 data 속성이 두 개 있다
function findVisibleRunRow(executionId: number): HTMLElement | null {
  const rows = document.querySelectorAll<HTMLElement>(`[data-run-row="${executionId}"]`)
  return Array.from(rows).find((el) => el.getClientRects().length > 0) ?? rows[0] ?? null
}

// 실패 사유 안내 띠: 두 줄로 줄여 보이고 [전체 보기]로 펼친다. 줄임이 필요 없는 짧은 사유에도 버튼이 보이지만 해가 없다.
// 정렬 규칙(표의 서브 행·카드 둘 다 같다): 아이콘·글자·버튼을 모두 세로 가운데(items-center)에 맞추고, 띠 안쪽 여백은 px-3 py-2다.
// 예전에는 띠 없이 글자가 왼쪽 위에 붙고 [전체 보기]만 맨 오른쪽에 테두리 없이 떠 있어, 같은 표의 다른 버튼(테두리 있음)과 모양이 달랐다.
// 오류색은 색만으로 전달하지 않는다: X 아이콘 + "실패 사유:" 글자가 항상 함께 있다.
function FailureReason({ execution, variant }: { execution: CollectExecution; variant: Variant }) {
  const [isOpen, setIsOpen] = useState(false)
  if (execution.status !== 'FAILED' || !execution.failureReason) return null
  const textId = `failure-${variant}-${execution.executionId}`
  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-destructive/40 bg-destructive/5 px-3 py-2 text-sm">
      <CircleXIcon aria-hidden="true" className="size-4 shrink-0 text-destructive" />
      <p
        id={textId}
        className={cn(
          'min-w-0 flex-1 basis-48 whitespace-normal break-words text-muted-foreground',
          !isOpen && 'line-clamp-2',
        )}
      >
        <span className="font-semibold text-foreground">실패 사유: </span>
        {execution.failureReason}
      </p>
      <Button
        type="button"
        variant="outline"
        size="sm"
        className="shrink-0"
        aria-expanded={isOpen}
        aria-controls={textId}
        onClick={() => setIsOpen((prev) => !prev)}
      >
        {isOpen ? '접기' : '전체 보기'}
        {isOpen ? <ChevronDownIcon aria-hidden="true" /> : <ChevronRightIcon aria-hidden="true" />}
        <span className="sr-only"> (실행 {execution.executionId})</span>
      </Button>
    </div>
  )
}

/**
 * 수집 실행 이력(SCR-16)이다. 넓은 화면(md 이상)은 표, 좁은 화면은 카드 목록이다.
 *
 * 표와 카드를 둘 다 그리고 CSS로 하나만 보이게 한다(OTT 구독상품·제공처 화면과 같은 방식). 375px에서 7열 표는 넘치고 가로 스크롤은 뒤쪽 열을 숨긴다.
 *
 * 행 동작: 중단됨은 [복구](확인 대화상자), 실패는 [다시 실행]. 서버가 "중단됨"으로 판정해 내려준 상태만 보고, 시간으로 추측하지 않는다.
 * 변경된 작품 펼침은 한 번에 하나만 열린다(expandedId 하나). 여러 개가 열리면 긴 목록이 쌓여 이력을 훑기 어려워서다.
 * 표와 카드에서 같은 펼침 상태를 쓰므로 화면 폭을 바꿔도 열린 행이 유지된다.
 */
export function CollectRunTable({
  executions,
  serviceNames,
  focusRequest,
  restartingExecutionId,
  onRecover,
  onRestart,
}: CollectRunTableProps) {
  const [expandedId, setExpandedId] = useState<number | null>(null)

  // [이력에서 보기]로 요청한 행으로 포커스를 옮긴다. 새로 접수한 행은 이력을 다시 불러온 뒤에야 생기므로,
  // executions가 바뀔 때마다 다시 시도하고 한 번 옮기면 같은 요청은 더 처리하지 않는다(처리한 nonce를 ref에 기억)
  const handledNonce = useRef(0)
  useEffect(() => {
    if (!focusRequest || handledNonce.current === focusRequest.nonce) return
    const row = findVisibleRunRow(focusRequest.executionId)
    if (!row) return
    handledNonce.current = focusRequest.nonce
    row.scrollIntoView?.({ block: 'center' }) // jsdom에는 scrollIntoView가 없어 있을 때만 부른다
    row.focus()
  }, [focusRequest, executions])

  const changesToggle = (execution: CollectExecution, variant: Variant) => {
    const isExpandable = execution.status === 'COMPLETED' || execution.status === 'RUNNING'
    // 모든 상태가 같은 크기·테두리의 칸(h-7, 테두리)을 쓴다. 펼칠 수 없는 상태(중단됨·실패)는 눌러지지 않는 칸이라
    // 바탕만 옅게 깔고, 화살표 자리는 보이지 않는 같은 크기 빈칸으로 맞춰 폭도 같게 한다.
    if (!isExpandable) {
      return (
        <span className="inline-flex h-7 items-center gap-1 rounded-md border border-border bg-muted px-2.5 text-[0.8rem] font-medium">
          변경 {execution.changedCount}건
          <span aria-hidden="true" className="size-4" />
        </span>
      )
    }
    const isOpen = expandedId === execution.executionId
    const detailId = `changes-${variant}-${execution.executionId}`
    return (
      <Button
        type="button"
        variant="outline"
        size="sm"
        aria-expanded={isOpen}
        aria-controls={isOpen ? detailId : undefined}
        onClick={() => setExpandedId(isOpen ? null : execution.executionId)}
      >
        변경 {execution.changedCount}건
        {isOpen ? <ChevronDownIcon aria-hidden="true" /> : <ChevronRightIcon aria-hidden="true" />}
        <span className="sr-only"> (실행 {execution.executionId})</span>
      </Button>
    )
  }

  const actions = (execution: CollectExecution, variant: Variant) => (
    <>
      {canRecover(execution.status) && (
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={(event) => onRecover(execution, event.currentTarget)}
        >
          <WrenchIcon aria-hidden="true" />
          복구
          <span className="sr-only"> (실행 {execution.executionId})</span>
        </Button>
      )}
      {canRestart(execution.status) && (
        <Button
          type="button"
          variant="outline"
          size="sm"
          data-restart-for={execution.executionId}
          data-variant={variant}
          disabled={restartingExecutionId === execution.executionId}
          onClick={() => onRestart(execution)}
        >
          <RotateCcwIcon aria-hidden="true" />
          다시 실행
          <span className="sr-only"> (실행 {execution.executionId})</span>
        </Button>
      )}
    </>
  )

  const endedText = (execution: CollectExecution) =>
    execution.endedAt
      ? `${formatDateTime(execution.endedAt) ?? '-'} (${formatElapsed(execution.startedAt, execution.endedAt)})`
      : execution.status === 'RUNNING'
        ? '진행 중'
        : '—'

  const dateAndTier = (execution: CollectExecution) =>
    `${formatIsoDate(execution.targetDate) ?? execution.targetDate} · ${COLLECT_TIER_LABELS[execution.tier]}`

  return (
    <>
      {/* 넓은 화면: 표 */}
      <div className="hidden rounded-xl border border-border md:block">
        <Table>
          <TableCaption className="sr-only">수집 실행 이력</TableCaption>
          <TableHeader>
            <TableRow>
              <TableHead>실행 ID</TableHead>
              <TableHead>대상 날짜 · 범위</TableHead>
              <TableHead>상태</TableHead>
              <TableHead>시작</TableHead>
              <TableHead>종료 (소요)</TableHead>
              <TableHead>처리 건수</TableHead>
              <TableHead>변경된 작품</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {executions.map((execution) => (
              <Fragment key={execution.executionId}>
                <TableRow
                  data-run-row={execution.executionId}
                  tabIndex={-1}
                  className={cn(
                    'outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
                    // 아래에 실패 사유 띠가 붙는 행은 사이 구분선을 지워 한 덩어리로 보이게 한다
                    execution.status === 'FAILED' && execution.failureReason && 'border-b-0',
                  )}
                >
                  <TableCell className="tabular-nums">{execution.executionId}</TableCell>
                  <TableCell>{dateAndTier(execution)}</TableCell>
                  <TableCell>
                    <div className="flex flex-wrap items-center gap-2">
                      <RunStatusBadge status={execution.status} />
                      {actions(execution, 'table')}
                    </div>
                  </TableCell>
                  <TableCell className="tabular-nums">
                    {formatDateTime(execution.startedAt) ?? '-'}
                  </TableCell>
                  <TableCell className="tabular-nums">{endedText(execution)}</TableCell>
                  <TableCell className="tabular-nums">
                    {execution.processedCount.toLocaleString('ko-KR')}건
                  </TableCell>
                  <TableCell>{changesToggle(execution, 'table')}</TableCell>
                </TableRow>
                {execution.status === 'FAILED' && execution.failureReason && (
                  <TableRow data-static-row className="hover:bg-transparent">
                    <TableCell colSpan={7} className="pt-0">
                      <FailureReason execution={execution} variant="table" />
                    </TableCell>
                  </TableRow>
                )}
                {expandedId === execution.executionId && (
                  <TableRow data-static-row className="hover:bg-transparent">
                    <TableCell colSpan={7}>
                      <CollectRunDetail
                        id={`changes-table-${execution.executionId}`}
                        execution={execution}
                        serviceNames={serviceNames}
                      />
                    </TableCell>
                  </TableRow>
                )}
              </Fragment>
            ))}
          </TableBody>
        </Table>
      </div>

      {/* 좁은 화면: 카드 목록 */}
      <ul aria-label="수집 실행 이력" className="grid gap-3 md:hidden">
        {executions.map((execution) => (
          <li
            key={execution.executionId}
            data-run-row={execution.executionId}
            tabIndex={-1}
            className="grid gap-2.5 rounded-xl border border-border bg-card p-4 outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="font-semibold">
                실행 <span className="tabular-nums">{execution.executionId}</span>
              </p>
              <RunStatusBadge status={execution.status} />
            </div>
            <p>{dateAndTier(execution)}</p>
            <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
              <dt className="text-muted-foreground">시작</dt>
              <dd className="tabular-nums">{formatDateTime(execution.startedAt) ?? '-'}</dd>
              <dt className="text-muted-foreground">종료</dt>
              <dd className="tabular-nums">{endedText(execution)}</dd>
              <dt className="text-muted-foreground">처리</dt>
              <dd className="tabular-nums">{execution.processedCount.toLocaleString('ko-KR')}건</dd>
            </dl>
            <FailureReason execution={execution} variant="card" />
            <div className="flex flex-wrap items-center gap-2">
              {changesToggle(execution, 'card')}
              {actions(execution, 'card')}
            </div>
            {expandedId === execution.executionId && (
              <CollectRunDetail
                id={`changes-card-${execution.executionId}`}
                execution={execution}
                serviceNames={serviceNames}
              />
            )}
          </li>
        ))}
      </ul>
    </>
  )
}
