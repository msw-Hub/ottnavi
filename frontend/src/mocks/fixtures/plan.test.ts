import { describe, expect, it } from 'vitest'
import type { PlanDraft } from '@/features/plan/mockTypes'
import { buildPlanDrafts, type PlanPriceInfo, type PlanScenario } from '@/mocks/fixtures/plan'
import { DEFAULT_USER_SETTINGS } from '@/mocks/fixtures/settings'

// 서비스마다 금액이 다른 요금표(사용자 수정 금액이 있는 경우를 흉내 낸다)
function getPriceInfo(ottServiceId: number): PlanPriceInfo {
  const price = ottServiceId === 1 ? 17000 : 13500
  return {
    productId: 100 + ottServiceId,
    productName: `상품 ${ottServiceId}`,
    price,
    adminPrice: price,
    source: 'ADMIN',
  }
}

function createDrafts(scenario: PlanScenario): PlanDraft[] {
  return buildPlanDrafts(scenario, {
    settings: DEFAULT_USER_SETTINGS,
    calculationSeq: 1,
    getPriceInfo,
  })
}

describe('플랜 목업: 같은 시리즈의 시즌 순서', () => {
  // 시즌 순서 규칙은 엔진 미정, 사용자 결정 대기 - 이 테스트는 목업 데이터가 보기에 어색하지 않은지만 확인한다
  it.each<PlanScenario>(['different', 'same'])(
    '%s 시나리오: 앞 시즌의 시청 완료 달이 뒤 시즌보다 늦지 않다',
    (scenario) => {
      createDrafts(scenario).forEach((draft) => {
        // 시리즈(tmdbId)별로 시즌 번호 → 마지막으로 보는 달(monthIndex)
        const lastMonthBySeries = new Map<number, Map<number, number>>()
        draft.months.forEach((month) =>
          month.assignments.forEach((a) => {
            if (a.seasonNumber === null) return
            const seasons = lastMonthBySeries.get(a.tmdbId) ?? new Map<number, number>()
            seasons.set(
              a.seasonNumber,
              Math.max(seasons.get(a.seasonNumber) ?? 0, month.monthIndex),
            )
            lastMonthBySeries.set(a.tmdbId, seasons)
          }),
        )
        lastMonthBySeries.forEach((seasons) => {
          const ordered = [...seasons.entries()].sort(([a], [b]) => a - b)
          ordered.forEach(([, lastMonth], i) => {
            if (i > 0) expect(ordered[i - 1][1]).toBeLessThanOrEqual(lastMonth)
          })
        })
      })
    },
  )

  it('긴 시즌 분할 예시(예시 드라마 A 시즌 1)는 여러 달에 걸쳐 이어진다', () => {
    const draft = createDrafts('different')[0]
    const months = draft.months.filter((m) => m.assignments.some((a) => a.tmdbId === 900001))
    expect(months.length).toBeGreaterThan(1)
  })
})

describe('플랜 목업: 절감액 기준', () => {
  it('같은 계산 안에서 세 유형의 allSubscribeCost가 같다', () => {
    ;(['different', 'same'] as PlanScenario[]).forEach((scenario) => {
      const costs = createDrafts(scenario).map((d) => d.allSubscribeCost)
      expect(new Set(costs).size).toBe(1)
    })
  })

  it('기준은 찜 작품을 볼 수 있는 서비스 전부를 3개월 구독한 금액이고, 절감액 = 기준 - 총비용이다', () => {
    createDrafts('different').forEach((draft) => {
      expect(draft.allSubscribeCost).toBe((17000 + 13500) * 3)
      expect(draft.savedAmount).toBe(draft.allSubscribeCost - draft.totalCost)
    })
  })
})
