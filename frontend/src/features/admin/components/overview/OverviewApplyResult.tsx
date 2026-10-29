import { useState } from 'react'
import { CheckIcon, CircleSlashIcon } from 'lucide-react'
import type { ApplyOverviewDraftItem, ApplyOverviewDraftsResult } from '@/api/generated/model'
import { Button } from '@/components/ui/button'
import { AdminStatusPill } from '@/features/admin/components/AdminStatusPill'
import { LlmWriteButton } from '@/features/admin/components/overview/LlmWriteButton'
import type { RevertTarget } from '@/features/admin/components/overview/RevertOverviewDialog'
import { APPLY_SKIP_REASON_LABELS } from '@/features/admin/overviewRules'

// 방금 한 반영의 결과와, 그때 화면이 알고 있던 초안 제목(응답에는 제목이 없어서 반영 직전에 모아 둔다)
export interface ApplyResultView {
  result: ApplyOverviewDraftsResult
  titlesByDraftId: ReadonlyMap<string, string>
}

interface OverviewApplyResultProps {
  view: ApplyResultView // 방금 한 반영의 결과
  onRequestRevert: (target: RevertTarget) => void // [영어로 되돌리기]를 눌렀다(확인 대화상자를 연다)
}

// 결과 한 줄. 제목을 모르면 "작품 #{titleId}", 작품 번호도 없으면(초안이 없어 건너뜀) 초안 ID를 보인다
function ResultRow({
  item,
  title,
  onRequestRevert,
}: {
  item: ApplyOverviewDraftItem
  title: string | null
  onRequestRevert: (target: RevertTarget) => void
}) {
  const name = title ?? (item.titleId !== null ? `작품 #${item.titleId}` : `초안 ${item.draftId}`)
  return (
    <li className="flex flex-wrap items-center gap-x-3 gap-y-1.5 py-2">
      {item.outcome === 'APPLIED' ? (
        <AdminStatusPill icon={CheckIcon} label="반영함" tone="success" size="sm" />
      ) : (
        <AdminStatusPill icon={CircleSlashIcon} label="건너뜀" tone="neutral" size="sm" />
      )}
      <span className="min-w-0 flex-1 text-sm break-keep">
        <span className="font-medium">{name}</span>
        {title && item.titleId !== null && (
          <span className="ml-1.5 text-xs text-muted-foreground">#{item.titleId}</span>
        )}
        {item.outcome === 'SKIPPED' && item.reason && (
          <span className="ml-2 text-xs text-muted-foreground">
            {APPLY_SKIP_REASON_LABELS[item.reason]}
          </span>
        )}
      </span>
      {item.outcome === 'APPLIED' && item.titleId !== null && (
        <LlmWriteButton
          type="button"
          variant="outline"
          size="sm"
          onClick={() => onRequestRevert({ titleId: item.titleId!, title })}
        >
          영어로 되돌리기
        </LlmWriteButton>
      )}
    </li>
  )
}

/**
 * 반영 결과 상자다. 반영을 누른 직후에만 화면 상태로 보이고 새로고침하면 사라진다(서버에 저장된 결과 목록은 없다).
 *
 * 건너뜀을 위에 두어 운영자가 이유를 먼저 보게 하고, 반영한 건은 접어 둔다(수백 건일 수 있어 화면을 밀어내지 않게).
 * 반영한 줄마다 [영어로 되돌리기]가 있어, 방금 반영한 번역이 이상하면 바로 되돌릴 수 있다.
 */
export function OverviewApplyResult({ view, onRequestRevert }: OverviewApplyResultProps) {
  const [isAppliedOpen, setIsAppliedOpen] = useState(false)
  const { result, titlesByDraftId } = view
  const skipped = result.results.filter((r) => r.outcome === 'SKIPPED')
  const applied = result.results.filter((r) => r.outcome === 'APPLIED')

  const toTitle = (item: ApplyOverviewDraftItem) => titlesByDraftId.get(item.draftId) ?? null

  return (
    <div className="grid gap-3">
      <p className="text-xs text-muted-foreground">이 결과는 새로고침하면 사라집니다.</p>
      <p role="status" className="text-sm font-semibold">
        반영 {result.appliedCount}편 · 건너뜀 {result.skippedCount}편
      </p>

      {skipped.length > 0 && (
        <ul aria-label="건너뛴 초안" className="divide-y divide-border">
          {skipped.map((item) => (
            <ResultRow
              key={item.draftId}
              item={item}
              title={toTitle(item)}
              onRequestRevert={onRequestRevert}
            />
          ))}
        </ul>
      )}

      {applied.length > 0 && (
        <div className="grid gap-2">
          <div>
            <Button
              type="button"
              variant="outline"
              size="sm"
              aria-expanded={isAppliedOpen}
              aria-controls="llm-applied-list"
              onClick={() => setIsAppliedOpen((prev) => !prev)}
            >
              {isAppliedOpen
                ? `반영한 ${applied.length}편 접기`
                : `반영한 ${applied.length}편 보기`}
            </Button>
          </div>
          {isAppliedOpen && (
            <ul id="llm-applied-list" aria-label="반영한 초안" className="divide-y divide-border">
              {applied.map((item) => (
                <ResultRow
                  key={item.draftId}
                  item={item}
                  title={toTitle(item)}
                  onRequestRevert={onRequestRevert}
                />
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
