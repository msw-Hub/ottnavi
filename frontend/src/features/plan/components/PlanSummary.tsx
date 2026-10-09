import { PiggyBankIcon, SplitIcon } from 'lucide-react'
import {
  collectSplitSeasons,
  formatAssignmentTitle,
  formatMonthLabel,
} from '@/features/plan/planDisplay'
import type { PlanDraft } from '@/features/plan/mockTypes'
import { formatWon } from '@/lib/format'

// 한 달에 최소로 배정하는 시청 시간 안내(PRD 5.4 긴 시즌 규칙). 120분 = 2시간
const MIN_SEGMENT_NOTICE = '한 달에 최소 2시간씩'

interface PlanSummaryProps {
  draft: PlanDraft // 지금 고른 플랜 초안
  typeNames: string // 고른 플랜의 이름(합친 그룹이면 '추천형 · 절약형')
  serviceNames: Map<number, string> // 서비스 ID → 서비스 이름
  withYear: boolean // 달 이름에 연도를 붙일지
}

/**
 * 고른 플랜의 요약이다(SCR-10): 3개월 총비용, 절감액, 볼 수 있는 작품 수, 그리고 나눠 보는 시즌이 있으면 그 요약 박스.
 *
 * 문장을 "확정"으로 쓰지 않는다: 숫자 옆에 "확인된 범위에서"를 붙여 계산이 알려진 데이터 안의 결과임을 알린다(frontend.md 표현 수위).
 * 절감액은 "찜한 작품을 볼 수 있는 서비스를 전부 3개월 내내 구독했을 때"와의 차이다(세 유형이 같은 기준). 0 이하면 아낀 것이 없으므로 "절감 없음"으로 쓴다(음수를 아낀 금액처럼 보이지 않게).
 */
export function PlanSummary({ draft, typeNames, serviceNames, withYear }: PlanSummaryProps) {
  const splitSeasons = collectSplitSeasons(draft)
  const hasSaving = draft.savedAmount > 0

  return (
    <section aria-labelledby="plan-summary-heading" className="grid gap-4">
      <h2 id="plan-summary-heading" className="text-lg font-semibold">
        {typeNames} 플랜 요약
      </h2>

      <dl className="grid gap-3 sm:grid-cols-3">
        <div className="grid content-start gap-0.5 rounded-xl border border-border bg-card p-4">
          <dt className="text-sm text-muted-foreground">3개월 총비용</dt>
          <dd className="text-2xl font-extrabold tabular-nums">{formatWon(draft.totalCost)}</dd>
        </div>
        <div className="grid content-start gap-0.5 rounded-xl border border-border bg-card p-4">
          <dt className="flex items-center gap-1.5 text-sm text-muted-foreground">
            <PiggyBankIcon aria-hidden="true" className="size-4" />
            절감액
          </dt>
          <dd className="text-2xl font-extrabold tabular-nums">
            {hasSaving ? formatWon(draft.savedAmount) : '절감 없음'}
          </dd>
          <dd className="text-sm text-muted-foreground">
            찜한 작품을 볼 수 있는 서비스를 전부 3개월 내내 구독할 때(
            {formatWon(draft.allSubscribeCost)})와 비교해요
          </dd>
        </div>
        <div className="grid content-start gap-0.5 rounded-xl border border-border bg-card p-4">
          <dt className="text-sm text-muted-foreground">3개월 안에 볼 수 있는 작품</dt>
          <dd className="text-2xl font-extrabold tabular-nums">
            {draft.scheduledCount} / {draft.totalCount}개
          </dd>
          <dd className="text-sm text-muted-foreground">
            꼭 볼 작품은 {draft.mustTotal}개 중 {draft.mustCompleted}개를 시청 완료해요
          </dd>
        </div>
      </dl>
      <p className="-mt-1 text-sm text-muted-foreground">
        확인된 범위의 데이터로 계산한 예상이에요. 실제 요금과 제공 현황은 달라질 수 있어요.
      </p>

      {splitSeasons.length > 0 && (
        <section
          aria-labelledby="plan-split-heading"
          className="grid gap-2 rounded-xl border border-dashed border-info bg-info-bg p-4 text-info"
        >
          <h3 id="plan-split-heading" className="flex items-center gap-2 text-base font-bold">
            <SplitIcon aria-hidden="true" className="size-5 shrink-0" />
            나눠 보는 시즌
          </h3>
          <ul className="grid gap-1.5 text-sm">
            {splitSeasons.map((season) => (
              <li key={season.planItemId}>
                <strong className="font-bold">{formatAssignmentTitle(season)}</strong>:{' '}
                {season.segments
                  .map((segment, index) => {
                    const serviceName = serviceNames.get(segment.ottServiceId) ?? '서비스'
                    const month = formatMonthLabel(segment.month, withYear)
                    return index === 0
                      ? `${month} ${serviceName} 앞부분`
                      : `${month} ${serviceName}에서 이어서`
                  })
                  .join(' → ')}
              </li>
            ))}
          </ul>
          <p className="text-sm">긴 시즌은 {MIN_SEGMENT_NOTICE} 나눠 배정해요.</p>
        </section>
      )}
    </section>
  )
}
