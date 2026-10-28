import type { PlanDraft } from '@/api/generated/model'
import { DataAsOf } from '@/components/common/DataAsOf'
import { PageSection } from '@/components/common/PageSection'
import { SourceAttribution } from '@/components/common/SourceAttribution'
import { PlanMonthCard } from '@/features/plan/components/PlanMonthCard'
import { PlanSummary } from '@/features/plan/components/PlanSummary'
import { UnscheduledList } from '@/features/plan/components/UnscheduledList'
import { hasSeveralSeasonsOfOneSeries } from '@/features/plan/planDisplay'

interface PlanDetailProps {
  draft: PlanDraft // 보여 줄 플랜. 초안(PlanDraft)과 현재 플랜(ActivePlan은 PlanDraft에 저장 시각을 더한 것)이 모두 들어온다
  typeNames: string // 플랜 이름(합친 그룹이면 '추천형 · 절약형')
  serviceNames: Map<number, string> // 서비스 ID → 서비스 이름
}

/**
 * 플랜 한 개의 내용(요약 → 달별 카드 → 남은 작품 → 기준일·출처 안내)이다.
 * 초안 결과(PlanResult)와 "내 현재 플랜"이 같은 모양이라 한 컴포넌트로 뽑아 재사용한다(두 곳이 따로 놀아 출처·기준일 표기가 빠지는 일을 막는다).
 * 감싸는 요소 없이 조각(<>)으로 내보내므로, 구간 사이 간격은 부르는 쪽 부모의 gap-section 하나가 정한다(styles/tokens.css R2).
 */
export function PlanDetail({ draft, typeNames, serviceNames }: PlanDetailProps) {
  // 3개월 중 연도가 바뀌는 달이 있으면 달 이름에 연도를 붙인다(예: 2026년 12월 → 2027년 1월)
  const withYear = new Set(draft.months.map((month) => month.month.slice(0, 4))).size > 1

  return (
    <>
      <PlanSummary
        draft={draft}
        typeNames={typeNames}
        serviceNames={serviceNames}
        withYear={withYear}
      />

      {/* 구간 제목·간격은 공통 PageSection이 맡는다(styles/tokens.css R3·R4). 달 카드 사이 16px는 그대로다.
          이전: <section aria-labelledby="plan-months-heading" className="grid gap-3">
                  <h2 id="plan-months-heading" className="text-lg font-semibold">달별 구독과 볼 작품</h2> */}
      <PageSection headingId="plan-months-heading" title="달별 구독과 볼 작품">
        <div className="grid gap-4 lg:grid-cols-3">
          {draft.months.map((month) => (
            <PlanMonthCard
              key={month.month}
              month={month}
              serviceNames={serviceNames}
              withYear={withYear}
            />
          ))}
        </div>
      </PageSection>

      <UnscheduledList items={draft.unscheduled} />

      {/* 제공처 정보가 보이는 화면이라 출처와 기준일을 둔다. 새로 가입한 서비스의 해지 예약 안내도 여기에 둔다 */}
      <div className="grid gap-1.5 border-t border-border pt-4">
        <SourceAttribution />
        <DataAsOf value={draft.dataAsOf} />
        {/* 계산 기준 안내: 같은 드라마의 시즌이 둘 이상 들어 있으면 순서 규칙을 한 줄로 알린다(확정 문구, PRD SCR-08·11절 37번) */}
        {hasSeveralSeasonsOfOneSeries(draft) && (
          <p className="text-sm text-muted-foreground">
            같은 드라마는 앞 시즌부터 보는 순서로 계산해요.
          </p>
        )}
        <p className="text-sm text-muted-foreground">
          제공 여부와 요금은 확인된 범위이며, 실제와 다를 수 있습니다. 새로 가입한 서비스는 가입
          직후 해지 예약을 해 두면 다음 달 자동 결제를 피할 수 있는지 서비스에서 확인해 보세요.
        </p>
      </div>
    </>
  )
}
