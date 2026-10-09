import { describe, expect, it } from 'vitest'
import { mergeIdenticalDrafts } from '@/features/plan/mergeDrafts'
import type { PlanDraft, PlanType } from '@/features/plan/mockTypes'
import { buildPlanDrafts, type PlanScenario } from '@/mocks/fixtures/plan'
import { DEFAULT_USER_SETTINGS } from '@/mocks/fixtures/settings'

// 목업 픽스처로 초안 3개를 만든다
function createDrafts(scenario: PlanScenario): PlanDraft[] {
  return buildPlanDrafts(scenario, {
    settings: DEFAULT_USER_SETTINGS,
    calculationSeq: 1,
    getPriceInfo: (ottServiceId) => ({
      productId: 100 + ottServiceId,
      productName: `상품 ${ottServiceId}`,
      price: 10000,
      adminPrice: 10000,
      source: 'ADMIN',
    }),
  })
}

describe('mergeIdenticalDrafts', () => {
  it('선택이 모두 다르면 합치지 않고 유형 순서를 지킨다', () => {
    const groups = mergeIdenticalDrafts(createDrafts('different'))
    expect(groups.map((g) => g.planTypes)).toEqual([['RECOMMENDED'], ['SAVER'], ['SIMPLE']])
  })

  it('선택이 모두 같으면 한 그룹으로 합치고 첫 유형의 초안을 대표로 쓴다', () => {
    const drafts = createDrafts('same')
    const groups = mergeIdenticalDrafts(drafts)
    expect(groups).toHaveLength(1)
    expect(groups[0].planTypes).toEqual(['RECOMMENDED', 'SAVER', 'SIMPLE'])
    expect(groups[0].draft.planId).toBe(drafts[0].planId)
  })

  it('입력 순서가 뒤섞여도 유형 순서로 정렬하고 입력 배열은 바꾸지 않는다', () => {
    const drafts = createDrafts('same')
    const shuffled = [drafts[2], drafts[0], drafts[1]]
    const groups = mergeIdenticalDrafts(shuffled)
    expect(groups[0].planTypes).toEqual(['RECOMMENDED', 'SAVER', 'SIMPLE'])
    expect(shuffled.map((d) => d.planType)).toEqual(['SIMPLE', 'RECOMMENDED', 'SAVER'])
  })

  it('두 유형만 같으면 그 둘만 합치고 나머지는 따로 둔다', () => {
    const different = createDrafts('different')
    const same = createDrafts('same')
    // 절약형을 추천형과 같은 선택으로 바꾼 경우
    const drafts = [different[0], { ...same[1], planType: 'SAVER' as PlanType }, different[2]]
    const groups = mergeIdenticalDrafts(drafts)
    expect(groups.map((g) => g.planTypes)).toEqual([['RECOMMENDED', 'SAVER'], ['SIMPLE']])
  })

  it('빈 배열은 빈 배열을 돌려준다', () => {
    expect(mergeIdenticalDrafts([])).toEqual([])
  })
})
