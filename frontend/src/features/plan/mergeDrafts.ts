import type { PlanDraft, PlanType } from '@/features/plan/mockTypes'

/*
 * 선택이 같은 플랜 초안을 하나로 합쳐 보여 주기 위한 순수 함수다(PRD 11절 35번 "세 유형의 선택이 같으면 화면에서 하나로 합쳐 유형 이름을 함께 보여준다").
 *
 * 왜 프론트가 합치나: 서버는 항상 유형마다 초안 하나씩(3개)을 응답한다(Task 031 방침). 합치는 일은 표시 규칙이라 화면 쪽이 맡는다.
 * 순수 함수(같은 입력이면 항상 같은 출력)라 화면 없이 테스트할 수 있다.
 */

// 표시·정렬 순서. 합친 그룹의 planTypes도 이 순서를 지킨다
const PLAN_TYPE_ORDER: PlanType[] = ['RECOMMENDED', 'SAVER', 'SIMPLE']

// 합쳐 보이는 그룹 하나
export interface MergedPlanGroup {
  planTypes: PlanType[] // 이 그룹이 대표하는 유형들(유형 순서). 합치지 않았으면 하나다
  draft: PlanDraft // 그룹의 대표 초안 = 첫 유형의 초안. 저장할 때 이 초안의 planId와 planTypes[0]을 보낸다
}

// "선택"의 지문: 달별 상품과 작품 배정(어느 서비스에서 몇 분). 비용·점수는 선택에서 나오는 값이라 따로 비교하지 않는다
function toChoiceKey(draft: PlanDraft): string {
  return JSON.stringify(
    draft.months.map((month) => ({
      products: month.products.map((product) => product.productId).sort((a, b) => a - b),
      assignments: month.assignments
        .map((a) => [a.planItemId, a.ottServiceId, a.minutes])
        .sort((x, y) => x[0] - y[0] || x[1] - y[1] || x[2] - y[2]),
    })),
  )
}

/**
 * 선택(달별 상품·배정)이 같은 초안을 한 그룹으로 합친다.
 * 유형 순서(추천형, 절약형, 간편형)를 유지하고, 그룹은 가장 앞선 유형 기준으로 놓는다. 입력 배열은 바꾸지 않는다.
 */
export function mergeIdenticalDrafts(drafts: PlanDraft[]): MergedPlanGroup[] {
  const sorted = [...drafts].sort(
    (a, b) => PLAN_TYPE_ORDER.indexOf(a.planType) - PLAN_TYPE_ORDER.indexOf(b.planType),
  )

  const groups = new Map<string, MergedPlanGroup>()
  for (const draft of sorted) {
    const key = toChoiceKey(draft)
    const group = groups.get(key)
    if (group) group.planTypes.push(draft.planType)
    else groups.set(key, { planTypes: [draft.planType], draft })
  }
  return [...groups.values()]
}
