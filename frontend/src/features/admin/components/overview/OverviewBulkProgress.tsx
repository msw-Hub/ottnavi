import { CheckCircle2Icon, CirclePauseIcon, LoaderCircleIcon } from 'lucide-react'
import type { BulkDraftRun } from '@/api/generated/model'
import { AdminStatusPill } from '@/features/admin/components/AdminStatusPill'
import { OverviewNoticeBand } from '@/features/admin/components/overview/OverviewNoticeBand'
import {
  getBulkProgress,
  getBulkStopMessage,
  getBulkSuccessCount,
} from '@/features/admin/overviewRules'
import { formatDateTime } from '@/lib/format'

interface OverviewBulkProgressProps {
  bulkRun: BulkDraftRun | null // 최근 일괄 번역 기록. 한 번도 돌린 적이 없으면 null
  dailyLimit: number | null // 오늘 호출 상한(중단 사유 문장에 들어간다). 현황을 못 불러왔으면 null
  lastCheckedAt: number // 진행 상황을 서버에서 마지막으로 받은 시각(밀리초). 0이면 아직 없음
  announcement: string // 상태가 바뀐 순간 한 번 낭독기에 알릴 문장(없으면 빈 문자열)
}

/**
 * 일괄 번역의 진행 상황을 보인다: 상태 알약, 진행 막대, 숫자 줄, 멈춘 이유.
 *
 * 숫자가 바뀔 때마다 낭독기가 읽지 않도록 숫자 줄에는 aria-live를 달지 않는다. 대신 상태가 바뀔 때(실행 중 → 완료·중단)만
 * 아래의 보이지 않는 알림 문장(role="status")이 한 번 읽힌다.
 * 막대는 role="progressbar"(처리 수 / 전체)이고, 값은 숫자 줄에도 글자로 있어 색이나 길이만으로 알리지 않는다.
 */
export function OverviewBulkProgress({
  bulkRun,
  dailyLimit,
  lastCheckedAt,
  announcement,
}: OverviewBulkProgressProps) {
  return (
    <div className="grid gap-3">
      {/* 상태가 바뀐 순간 한 번만 읽히는 알림(눈에는 보이지 않는다) */}
      <p role="status" className="sr-only">
        {announcement}
      </p>

      {bulkRun === null ? (
        <p className="text-sm text-muted-foreground">아직 일괄 번역을 실행한 적이 없습니다.</p>
      ) : (
        <BulkDetail bulkRun={bulkRun} dailyLimit={dailyLimit} lastCheckedAt={lastCheckedAt} />
      )}
    </div>
  )
}

function BulkDetail({
  bulkRun,
  dailyLimit,
  lastCheckedAt,
}: {
  bulkRun: BulkDraftRun
  dailyLimit: number | null
  lastCheckedAt: number
}) {
  const { processed, ratio } = getBulkProgress(bulkRun)
  const isRunning = bulkRun.state === 'RUNNING'

  return (
    <>
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        {bulkRun.state === 'RUNNING' && (
          <AdminStatusPill
            icon={LoaderCircleIcon}
            label="실행 중"
            tone="progress"
            iconClassName="loading-spin"
          />
        )}
        {bulkRun.state === 'COMPLETED' && (
          <AdminStatusPill icon={CheckCircle2Icon} label="완료" tone="success" />
        )}
        {bulkRun.state === 'STOPPED' && (
          <AdminStatusPill icon={CirclePauseIcon} label="중단됨" tone="neutral" />
        )}
        <span className="text-xs text-muted-foreground">
          시작 {formatDateTime(bulkRun.startedAt) ?? '-'}
          {bulkRun.finishedAt ? ` · 끝 ${formatDateTime(bulkRun.finishedAt) ?? '-'}` : ''}
        </span>
      </div>

      <div
        role="progressbar"
        aria-label="일괄 번역 진행"
        aria-valuemin={0}
        aria-valuemax={bulkRun.total}
        aria-valuenow={Math.min(processed, bulkRun.total)}
        className="h-2 w-full overflow-hidden rounded-full bg-muted"
      >
        <div
          className="h-full rounded-full bg-primary motion-safe:transition-[width]"
          style={{ width: `${ratio * 100}%` }}
        />
      </div>

      <p className="text-sm tabular-nums">
        {processed}/{bulkRun.total}편 처리 · 초안 만듦 {bulkRun.doneCount} (성공{' '}
        {getBulkSuccessCount(bulkRun)} · 주의 {bulkRun.warningCount} · 실패{' '}
        {bulkRun.guardFailedCount}) · 만들지 못함 {bulkRun.failedCount}
      </p>

      {isRunning && (
        <p className="text-xs text-muted-foreground">
          5초마다 새로 확인합니다
          {lastCheckedAt > 0
            ? ` · 마지막 확인 ${formatDateTime(new Date(lastCheckedAt)) ?? '-'}`
            : ''}
        </p>
      )}

      {bulkRun.state === 'STOPPED' && bulkRun.stopReason && (
        <OverviewNoticeBand tone="attention" title="일괄 번역이 멈췄습니다" role="note">
          {getBulkStopMessage(bulkRun.stopReason, dailyLimit)}
        </OverviewNoticeBand>
      )}
    </>
  )
}
