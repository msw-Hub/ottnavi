import {
  CalendarClockIcon,
  CircleCheckIcon,
  CornerDownRightIcon,
  MoonIcon,
  ScissorsIcon,
} from 'lucide-react'
import { WISHLIST_PRIORITY_LABELS, PRICE_SOURCE_LABELS } from '@/components/common/displayTypes'
import { PriceSourceLabel } from '@/components/common/PriceDisplay'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import {
  comparePriority,
  formatAssignmentTitle,
  formatMonthLabel,
} from '@/features/plan/planDisplay'
import type { PlanAssignment, PlanMonth } from '@/features/plan/mockTypes'
import { formatMinutes, formatWon } from '@/lib/format'
import { cn } from '@/lib/utils'

interface PlanMonthCardProps {
  month: PlanMonth // 한 달의 구독 상품과 볼 작품
  serviceNames: Map<number, string> // 서비스 ID → 서비스 이름(서비스 목록 API)
  withYear: boolean // 달 이름에 연도를 붙일지(연도가 바뀌는 구간)
}

// 이번 비용에서 빠지는 금액 출처. 금액이 보여도 "이번 달 비용"에는 넣지 않는다는 점을 글자로 알린다
const NOT_COUNTED_SOURCES = new Set(['ALREADY_PAID', 'FREE'])

interface SegmentBadgeProps {
  assignment: PlanAssignment // 나뉜 조각 하나
}

/**
 * 긴 시즌이 여러 달로 나뉠 때의 배지다. 첫 조각은 "1/2 앞부분", 이어지는 조각은 "이어 보기 2/2"로 보인다.
 * 점선 테두리 + 아이콘 + 글자를 함께 써서 색 없이도 "나뉜 조각"임을 알 수 있다(점선 = 아직 끝나지 않고 이어진다는 뜻).
 */
function SegmentBadge({ assignment }: SegmentBadgeProps) {
  if (assignment.segmentCount <= 1) return null
  const position = `${assignment.segmentIndex}/${assignment.segmentCount}`
  const isFirst = !assignment.isContinuation
  const Icon = isFirst ? ScissorsIcon : CornerDownRightIcon

  return (
    <span className="inline-flex items-center gap-1 rounded-full border border-dashed border-info bg-info-bg px-2 py-0.5 text-xs font-bold whitespace-nowrap text-info">
      <Icon aria-hidden="true" className="size-3.5 shrink-0" />
      {isFirst ? `${position} 앞부분` : `이어 보기 ${position}`}
    </span>
  )
}

/**
 * 플랜의 한 달 카드다(SCR-10). 달 이름과 "이번 달 확정" / "예상" 표시, 그 달 비용, 구독할 상품, 볼 작품 순서로 쌓인다.
 *
 * 이번 달만 "확정"이라고 부르는 이유: 이번 달은 곧 결제하므로 바뀔 수 없는 기준이지만, 나머지 두 달은 다음 달 데이터 갱신에 따라
 * 달라질 수 있어 "예상"이다. 두 표시는 색이 아니라 실선/점선 테두리와 아이콘(체크/달력)·글자로 구분한다.
 * 구독할 상품이 없는 달은 "구독하지 않아도 돼요"를 글자로 알려, 카드가 비어 보이지 않게 한다.
 */
export function PlanMonthCard({ month, serviceNames, withYear }: PlanMonthCardProps) {
  const assignments = [...month.assignments].sort(comparePriority)

  return (
    <Card className="bg-card ring-1 ring-border">
      <CardHeader className="gap-2">
        <div className="flex items-center justify-between gap-2">
          <h3 className="text-xl font-extrabold">{formatMonthLabel(month.month, withYear)}</h3>
          <span
            className={cn(
              'inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-sm font-bold',
              month.isConfirmed
                ? 'border-2 border-solid border-primary bg-primary text-primary-foreground'
                : 'border-2 border-dashed border-input text-foreground',
            )}
          >
            {month.isConfirmed ? (
              <CircleCheckIcon aria-hidden="true" className="size-4" />
            ) : (
              <CalendarClockIcon aria-hidden="true" className="size-4" />
            )}
            {month.isConfirmed ? '이번 달 확정' : '예상'}
          </span>
        </div>
        <p className="flex items-baseline justify-between gap-2">
          <span className="text-sm text-muted-foreground">이달 비용</span>
          <span className="text-2xl font-extrabold tabular-nums">{formatWon(month.cost)}</span>
        </p>
      </CardHeader>

      <CardContent className="grid gap-4">
        <section
          aria-label={`${formatMonthLabel(month.month)} 구독할 서비스`}
          className="grid gap-2"
        >
          <h4 className="text-sm font-semibold text-muted-foreground">구독할 서비스</h4>
          {month.products.length === 0 ? (
            <p className="flex items-center gap-2 rounded-lg border border-dashed border-border px-3 py-2.5 text-sm">
              <MoonIcon aria-hidden="true" className="size-4 shrink-0" />이 달은 새로 구독하지
              않아도 돼요
            </p>
          ) : (
            <ul className="grid gap-2">
              {month.products.map((product) => (
                <li
                  key={product.productId}
                  className="grid gap-1 rounded-lg border border-border bg-background px-3 py-2"
                >
                  <p className="font-semibold">{product.productName}</p>
                  <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
                    <span className="font-bold tabular-nums">
                      {formatWon(product.appliedPrice)}
                    </span>
                    <PriceSourceLabel priceSource={product.priceSource} />
                    {NOT_COUNTED_SOURCES.has(product.priceSource) && (
                      <span className="text-sm text-muted-foreground">
                        ({PRICE_SOURCE_LABELS[product.priceSource]}라 이달 비용에는 넣지 않아요)
                      </span>
                    )}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section aria-label={`${formatMonthLabel(month.month)} 볼 작품`} className="grid gap-2">
          <h4 className="text-sm font-semibold text-muted-foreground">볼 작품</h4>
          {assignments.length === 0 ? (
            <p className="text-sm text-muted-foreground">이 달에 배정된 작품이 없어요</p>
          ) : (
            <ul className="grid gap-2">
              {assignments.map((assignment) => (
                <li
                  key={`${assignment.planItemId}-${assignment.segmentIndex}`}
                  className="grid gap-1 rounded-lg border border-border bg-background px-3 py-2"
                >
                  <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
                    <span className="font-semibold">{formatAssignmentTitle(assignment)}</span>
                    <SegmentBadge assignment={assignment} />
                  </p>
                  <p className="flex flex-wrap items-center gap-x-2 text-sm text-muted-foreground tabular-nums">
                    <span>{serviceNames.get(assignment.ottServiceId) ?? '서비스'}</span>
                    <span aria-hidden="true">·</span>
                    <span>{formatMinutes(assignment.minutes)}</span>
                    <span aria-hidden="true">·</span>
                    <span>{WISHLIST_PRIORITY_LABELS[assignment.priority]}</span>
                  </p>
                </li>
              ))}
            </ul>
          )}
        </section>
      </CardContent>
    </Card>
  )
}
