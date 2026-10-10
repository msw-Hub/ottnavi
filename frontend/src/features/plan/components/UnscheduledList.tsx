import { CircleCheckIcon } from 'lucide-react'
import { WISHLIST_PRIORITY_LABELS } from '@/components/common/displayTypes'
import {
  comparePriority,
  formatAssignmentTitle,
  UNSCHEDULED_REASON_DISPLAY,
} from '@/features/plan/planDisplay'
import type { UnscheduledItem } from '@/features/plan/mockTypes'

interface UnscheduledListProps {
  items: UnscheduledItem[] // 이번 3개월에 배정하지 못한 작품과 이유
}

/**
 * 이번 3개월 안에 담지 못한 작품과 그 이유다(SCR-10 "남은 작품과 이유"). 꼭 볼 작품이 먼저 보인다.
 *
 * 이유 4가지(예산 부족 / 시청 시간 부족 / 제공처 정보 없음 / 국내 서비스에 없음)는 아이콘 + 글자 + 보조 설명으로 알린다.
 * 사용자가 "왜 빠졌지?"에서 멈추지 않고 무엇을 바꾸면 되는지(예산·시청 시간 조정, 제공처 확인)를 짐작할 수 있게 하기 위함이다.
 * 하나도 남지 않았으면 비워 두지 않고 "모두 배정됐어요"를 글자로 알린다.
 */
export function UnscheduledList({ items }: UnscheduledListProps) {
  const sorted = [...items].sort(comparePriority)

  return (
    <section aria-labelledby="plan-unscheduled-heading" className="grid gap-3">
      <h2 id="plan-unscheduled-heading" className="text-lg font-semibold">
        남은 작품과 이유
      </h2>
      {sorted.length === 0 ? (
        <p className="flex items-center gap-2 rounded-xl border border-border bg-card px-4 py-3 text-sm">
          <CircleCheckIcon aria-hidden="true" className="size-4 shrink-0" />
          찜한 작품이 모두 이 플랜에 배정됐어요
        </p>
      ) : (
        <ul className="grid gap-2">
          {sorted.map((item) => {
            const reason = UNSCHEDULED_REASON_DISPLAY[item.reason]
            const ReasonIcon = reason.icon
            return (
              <li
                key={item.planItemId}
                className="grid gap-1 rounded-xl border border-border bg-card px-4 py-3 sm:grid-cols-[1fr_auto] sm:items-center sm:gap-4"
              >
                <p className="grid gap-0.5">
                  <span className="font-semibold">{formatAssignmentTitle(item)}</span>
                  <span className="text-sm text-muted-foreground">
                    {WISHLIST_PRIORITY_LABELS[item.priority]}
                  </span>
                </p>
                <p className="grid gap-0.5 sm:justify-items-end">
                  <span className="inline-flex items-center gap-1.5 text-sm font-bold">
                    <ReasonIcon aria-hidden="true" className="size-4 shrink-0" />
                    {reason.label}
                  </span>
                  <span className="text-sm text-muted-foreground">{reason.hint}</span>
                </p>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
