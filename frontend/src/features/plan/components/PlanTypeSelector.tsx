import { CircleCheckIcon, CircleIcon, EqualIcon } from 'lucide-react'
import { PLAN_TYPE_DESCRIPTIONS, PLAN_TYPE_LABELS } from '@/features/plan/planDisplay'
import type { MergedPlanGroup } from '@/features/plan/mergeDrafts'
import type { PlanType } from '@/features/plan/mockTypes'
import { formatWon } from '@/lib/format'
import { cn } from '@/lib/utils'

interface PlanTypeSelectorProps {
  groups: MergedPlanGroup[] // 선택이 같은 유형을 합친 그룹들(보통 3개, 모두 같으면 1개)
  selectedType: PlanType // 지금 고른 그룹의 대표 유형(그룹의 첫 유형)
  onSelect: (planType: PlanType) => void // 카드를 골랐을 때
  isBusy: boolean // 계산·저장 중인지. true면 카드를 흐리게 하고 고르지 못하게 막는다
}

// 합쳐진 그룹에 붙이는 안내. 몇 개 유형이 같은 결과인지 글자로 알린다
function getMergedNote(typeCount: number): string | null {
  if (typeCount === 2) return '두 기준의 결과가 같아요'
  if (typeCount >= 3) return '세 기준 모두 같은 결과'
  return null
}

/**
 * 플랜 유형(추천형·절약형·간편형)을 고르는 카드 묶음이다(SCR-10). 한 번에 하나만 고르는 radiogroup이다.
 *
 * 선택이 같은 유형은 mergeIdenticalDrafts가 한 그룹으로 합쳐 준다. 합친 그룹은 카드 한 장에 '추천형 · 절약형'처럼 이름을 함께 쓰고
 * "두 기준의 결과가 같아요"를 알려, 3장이 똑같은 내용으로 반복되는 것을 막는다.
 * 선택된 카드는 색뿐 아니라 ① 테두리 색(--plan-selected-border) ② 체크 아이콘 ③ "선택됨" 글자로 표시해 색을 못 보아도 구분된다.
 * 네이티브 라디오(input type=radio)를 숨겨 두고 카드 전체를 label로 감싸 클릭·키보드(화살표)·낭독기를 브라우저에 맡긴다.
 */
export function PlanTypeSelector({
  groups,
  selectedType,
  onSelect,
  isBusy,
}: PlanTypeSelectorProps) {
  return (
    <section aria-labelledby="plan-type-heading" className="grid gap-3">
      <h2 id="plan-type-heading" className="text-lg font-semibold">
        플랜 유형 고르기
      </h2>
      <div
        role="radiogroup"
        aria-labelledby="plan-type-heading"
        aria-busy={isBusy}
        className={cn(
          'grid gap-3 transition-opacity motion-reduce:transition-none',
          groups.length >= 3 ? 'md:grid-cols-3' : groups.length === 2 ? 'md:grid-cols-2' : '',
          isBusy && 'opacity-60',
        )}
      >
        {groups.map((group) => {
          const first = group.planTypes[0]
          const { draft } = group
          const mergedNote = getMergedNote(group.planTypes.length)
          return (
            <label key={first} className="group/plan relative block cursor-pointer">
              <input
                type="radio"
                name="plan-type"
                value={first}
                checked={selectedType === first}
                disabled={isBusy}
                onChange={() => onSelect(first)}
                className="peer sr-only"
              />
              <div className="grid h-full content-start gap-3 rounded-xl border-2 border-border bg-card p-4 transition-colors group-hover/plan:bg-muted peer-checked:border-plan-selected-border peer-focus-visible:ring-3 peer-focus-visible:ring-ring/50 peer-disabled:cursor-not-allowed">
                <div className="flex items-start justify-between gap-2">
                  <h3 className="text-lg leading-snug font-bold">
                    {group.planTypes.map((planType) => PLAN_TYPE_LABELS[planType]).join(' · ')}
                  </h3>
                  {/* 선택됨 표시: 안 골랐을 때는 빈 동그라미, 골랐을 때는 체크 + "선택됨" 글자 */}
                  <span className="inline-flex shrink-0 items-center gap-1 text-sm font-semibold">
                    <CircleIcon
                      aria-hidden="true"
                      className="size-5 text-muted-foreground group-has-[:checked]/plan:hidden"
                    />
                    <CircleCheckIcon
                      aria-hidden="true"
                      className="hidden size-5 text-primary group-has-[:checked]/plan:block"
                    />
                    <span className="hidden group-has-[:checked]/plan:inline">선택됨</span>
                  </span>
                </div>

                {mergedNote && (
                  <p className="inline-flex w-fit items-center gap-1.5 rounded-full border border-dashed border-info bg-info-bg px-2.5 py-0.5 text-sm font-semibold text-info">
                    <EqualIcon aria-hidden="true" className="size-4 shrink-0" />
                    {mergedNote}
                  </p>
                )}

                {/* 한 줄 설명: 합친 그룹이면 유형마다 한 줄씩 */}
                <ul className="grid gap-1 text-sm text-muted-foreground">
                  {group.planTypes.map((planType) => (
                    <li key={planType} className="text-foreground">
                      {group.planTypes.length > 1 && (
                        <strong className="font-semibold text-foreground">
                          {PLAN_TYPE_LABELS[planType]}:{' '}
                        </strong>
                      )}
                      {PLAN_TYPE_DESCRIPTIONS[planType].text}
                      {/* 보조 문구는 줄을 바꿔 보인다. 글자색은 muted-foreground(본문 대비 4.5:1 기준 충족 토큰) */}
                      {PLAN_TYPE_DESCRIPTIONS[planType].note && (
                        <span className="block text-muted-foreground">
                          {PLAN_TYPE_DESCRIPTIONS[planType].note}
                        </span>
                      )}
                    </li>
                  ))}
                </ul>

                <dl className="grid gap-1.5 border-t border-border pt-3 text-sm">
                  <div className="flex items-baseline justify-between gap-2">
                    <dt className="text-muted-foreground">3개월 총비용</dt>
                    <dd className="text-lg font-extrabold tabular-nums">
                      {formatWon(draft.totalCost)}
                    </dd>
                  </div>
                  <div className="flex items-baseline justify-between gap-2">
                    <dt className="text-muted-foreground">꼭 볼 작품 시청 완료</dt>
                    <dd className="font-bold tabular-nums">
                      {draft.mustTotal === 0
                        ? '꼭 볼 작품 없음'
                        : `${draft.mustCompleted} / ${draft.mustTotal}편`}
                    </dd>
                  </div>
                </dl>
              </div>
            </label>
          )
        })}
      </div>
    </section>
  )
}
