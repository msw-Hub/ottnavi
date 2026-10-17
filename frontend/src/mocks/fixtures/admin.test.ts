import { describe, expect, it } from 'vitest'
import { withActionFlags } from '@/features/admin/collectRules'
import { isVerifiedStale } from '@/features/admin/priceRules'
import {
  buildMockAdminProducts,
  buildMockAdminProviders,
  buildMockCollectChanges,
  buildMockCollectExecutions,
  MOCK_EXTRA_PRODUCTS,
  MOCK_PRODUCTS,
} from '@/mocks/fixtures/admin'

// 기준일을 고정해 두면 실제 오늘이 언제든 같은 결과가 나온다(fixture가 기준일 상대 날짜라서 가능하다)
const TODAY = '2026-10-11'

describe('관리자 목업 fixture', () => {
  it('OTT 구독상품의 지금 적용 중인 월 구독요금이 SCR-09 기본 요금(MOCK_PRODUCTS)과 같다', () => {
    for (const product of buildMockAdminProducts(TODAY)) {
      const current = product.prices.filter((p) => p.effectiveFrom <= TODAY).at(-1)
      // 기본 7개 단품(MOCK_PRODUCTS)과 Task 076에서 덧붙인 저가형·번들(MOCK_EXTRA_PRODUCTS 108~112)을 함께 찾는다
      const expected = [...MOCK_PRODUCTS, ...MOCK_EXTRA_PRODUCTS].find(
        (m) => m.productId === product.productId,
      )?.adminPrice
      expect(current?.monthlyPrice).toBe(expected)
    }
  })

  it('월 구독요금 줄이 시작일 오름차순이고 같은 시작일이 없다', () => {
    for (const product of buildMockAdminProducts(TODAY)) {
      const dates = product.prices.map((p) => p.effectiveFrom)
      expect(dates).toEqual([...new Set(dates)].sort())
    }
  })

  it('이력 2줄 이상·예정 줄·확인일 90일 초과·판매 종료가 각각 있다', () => {
    const products = buildMockAdminProducts(TODAY)
    expect(products.some((p) => p.prices.length >= 2)).toBe(true)
    expect(products.some((p) => p.prices.some((row) => row.effectiveFrom > TODAY))).toBe(true)
    expect(products.some((p) => isVerifiedStale(p.prices.at(-1)!.lastVerifiedAt, TODAY))).toBe(true)
    expect(products.some((p) => !p.isActive)).toBe(true)
  })

  it('제공처는 매핑됨 8·관련 없음·미매핑을 모두 포함하고 미매핑은 서비스가 없다', () => {
    const providers = buildMockAdminProviders(TODAY)
    expect(providers.filter((p) => p.mappingState === 'MAPPED')).toHaveLength(8)
    expect(providers.filter((p) => p.mappingState === 'IGNORED').length).toBeGreaterThan(0)
    const unmapped = providers.filter((p) => p.mappingState === 'UNMAPPED')
    expect(unmapped.length).toBeGreaterThan(0)
    expect(unmapped.every((p) => p.ottServiceId === null)).toBe(true)
  })

  it('수집 이력에 성공·실패·중단됨이 있고(실행 중은 없다) 성공 실행의 변경 건수가 목록과 같다', () => {
    const executions = buildMockCollectExecutions(TODAY)
    // 실행 중이 있으면 전체 동시 실행 금지 규칙 때문에 모든 실행·다시 실행이 막혀 기본 이력에 두지 않는다(fixture 주석)
    expect(new Set(executions.map((e) => e.status))).toEqual(
      new Set(['COMPLETED', 'FAILED', 'STOPPED']),
    )
    for (const e of executions.filter((x) => x.status === 'COMPLETED')) {
      expect(buildMockCollectChanges(e.executionId)).toHaveLength(e.changedCount)
    }
  })

  it('미리 적어 둔 canRecover·canRestart가 서버 규칙으로 계산한 값과 같다', () => {
    const executions = buildMockCollectExecutions(TODAY)
    expect(executions).toEqual(withActionFlags(executions))
    // 중단됨(3번)만 복구, 최신 실패(2번)만 다시 실행
    expect(executions.filter((e) => e.canRecover).map((e) => e.executionId)).toEqual([3])
    expect(executions.filter((e) => e.canRestart).map((e) => e.executionId)).toEqual([2])
  })
})
