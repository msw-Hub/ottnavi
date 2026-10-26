import { ArrowRightIcon } from 'lucide-react'
import { ProviderStatusBadge } from '@/components/common/ProviderStatusBadge'
import type { AvailabilityStatus } from '@/components/common/displayTypes'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { PhaseLaterTag } from '@/features/admin/components/PhaseLaterTag'
import { useCollectChanges } from '@/features/admin/hooks/useAdminCollect'
import type { CollectExecution } from '@/api/generated/model'
import { isApiError } from '@/api/http'
import { getErrorMessage } from '@/lib/errorMessages'

interface CollectRunDetailProps {
  id: string // 펼침 영역의 DOM id. 펼침 버튼의 aria-controls가 가리킨다
  execution: CollectExecution // 펼친 실행
  serviceNames: Map<number, string> // 서비스 ID → 서비스 이름. 없으면 "서비스 N"
}

// 이전 → 이후 상태 한 쌍. 이전이 null이면 이번에 처음 확인된 신규 작품이라 "신규" 글자를 보인다
function StatusChange({
  previous,
  current,
}: {
  previous: AvailabilityStatus | null
  current: AvailabilityStatus
}) {
  return (
    <span className="inline-flex flex-wrap items-center gap-1.5">
      <span className="sr-only">이전 상태 </span>
      {previous === null ? (
        <span className="rounded-full border border-dashed border-input px-2 py-0.5 text-sm font-semibold text-muted-foreground">
          신규
        </span>
      ) : (
        <ProviderStatusBadge status={previous} />
      )}
      {/* 화살표는 장식이다. 낭독기에는 "이전 상태 … 바뀐 상태 …"로 읽힌다 */}
      <ArrowRightIcon aria-hidden="true" className="size-4 text-muted-foreground" />
      <span className="sr-only">바뀐 상태 </span>
      <ProviderStatusBadge status={current} />
    </span>
  )
}

/**
 * 수집 실행 한 건의 펼침 영역이다(SCR-16): 이 실행에서 제공 상태가 바뀐 작품 목록과, 재계산 대상 플랜 수와 2단계부터 제공할 항목 자리.
 *
 * 실행 중인 행은 목록을 불러오지 않고 "끝나면 보인다"고만 알린다(요청도 보내지 않는다).
 * 성공한 행은 열릴 때 처음 불러온다(이 컴포넌트는 펼쳤을 때만 그려져서, 펼치기 전에는 요청이 없다).
 * 로딩은 작은 회색 줄, 실패는 이 영역 안의 짧은 메시지와 [다시 시도], 0건은 안내 문장이다(로딩·오류·빈 상태).
 */
export function CollectRunDetail({ id, execution, serviceNames }: CollectRunDetailProps) {
  const isRunning = execution.status === 'RUNNING'
  const changes = useCollectChanges(execution.executionId, !isRunning)

  let body
  if (isRunning) {
    body = <p className="text-sm text-muted-foreground">실행이 끝나면 변경된 작품이 보입니다.</p>
  } else if (changes.isPending) {
    body = (
      <div role="status" className="grid gap-2">
        <span className="sr-only">변경된 작품을 불러오는 중입니다</span>
        <div aria-hidden="true" className="grid gap-2">
          <Skeleton className="h-6 w-full" />
          <Skeleton className="h-6 w-4/5" />
        </div>
      </div>
    )
  } else if (changes.isError) {
    body = (
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <p role="alert" className="text-destructive">
          {isApiError(changes.error) ? changes.error.userMessage : getErrorMessage(null)}
        </p>
        <Button type="button" variant="outline" size="sm" onClick={() => changes.refetch()}>
          다시 시도
        </Button>
      </div>
    )
  } else if (changes.data.content.length === 0) {
    body = <p className="text-sm text-muted-foreground">이 실행에서 바뀐 작품이 없습니다.</p>
  } else {
    body = (
      <ul className="grid gap-2">
        {changes.data.content.map((change) => (
          <li
            key={`${change.mediaType}-${change.tmdbId}-${change.seasonNumber ?? 0}-${change.ottServiceId}`}
            className="flex flex-wrap items-center gap-x-3 gap-y-1.5"
          >
            <span className="font-medium">
              {change.title}
              {change.seasonNumber !== null && ` 시즌 ${change.seasonNumber}`}
            </span>
            <span className="text-sm text-muted-foreground">
              {serviceNames.get(change.ottServiceId) ?? `서비스 ${change.ottServiceId}`}
            </span>
            <StatusChange previous={change.previousStatus} current={change.currentStatus} />
          </li>
        ))}
      </ul>
    )
  }

  return (
    <div id={id} className="grid gap-3 rounded-lg bg-muted/40 p-3">
      <h3 className="text-sm font-semibold">변경된 작품</h3>
      {body}
      <dl className="grid gap-1.5 border-t border-border pt-3 text-sm sm:grid-cols-[auto_1fr] sm:gap-x-4">
        <dt className="text-muted-foreground">재계산 대상 플랜</dt>
        {/* 수집 뒤 이어 돈 재계산의 대상 플랜 수(계약 recalcTargetCount). null이면 재계산이 없거나 아직 끝나지 않았다 */}
        <dd>
          {execution.recalcTargetCount === null ? (
            <span className="text-muted-foreground">집계 전</span>
          ) : (
            <span className="tabular-nums">{execution.recalcTargetCount}개</span>
          )}
        </dd>
        <dt className="text-muted-foreground">알림 발송 상태</dt>
        <dd>
          <PhaseLaterTag label="2단계부터 제공" />
        </dd>
      </dl>
    </div>
  )
}
