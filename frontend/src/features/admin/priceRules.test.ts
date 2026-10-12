import { describe, expect, it } from 'vitest'
import {
  checkAddPriceStartDate,
  checkEditLastPriceStartDate,
  checkFirstPriceStartDate,
  checkMonthlyPriceText,
  getCurrentPrice,
  getPriceEndDate,
  getPriceRowKind,
  isVerifiedStale,
  sortPricesAscending,
} from '@/features/admin/priceRules'
import type { AdminProductPrice } from '@/api/generated/model'

const TODAY = '2026-10-11'

function row(priceId: number, effectiveFrom: string): AdminProductPrice {
  return { priceId, monthlyPrice: 10000, effectiveFrom, lastVerifiedAt: '2026-10-01' }
}

// 지난 줄 / 지금 적용 중 / 예정
const ROWS = [row(1, '2025-09-06'), row(2, '2026-10-01'), row(3, '2026-11-10')]

describe('종료일·적용 구분', () => {
  it('종료일은 다음 줄 시작일의 전날이고 마지막 줄은 null(계속)이다', () => {
    expect(getPriceEndDate(ROWS, 0)).toBe('2026-09-30')
    expect(getPriceEndDate(ROWS, 1)).toBe('2026-11-09')
    expect(getPriceEndDate(ROWS, 2)).toBeNull()
  })

  it('월 경계를 넘는 전날도 맞게 계산한다', () => {
    expect(getPriceEndDate([row(1, '2026-01-01'), row(2, '2026-03-01')], 0)).toBe('2026-02-28')
  })

  it('지난 줄·지금 적용 중·예정을 구분한다', () => {
    expect(ROWS.map((_, i) => getPriceRowKind(ROWS, i, TODAY))).toEqual([
      'PAST',
      'CURRENT',
      'UPCOMING',
    ])
  })

  it('시작일이 오늘인 줄도 지금 적용 중이다', () => {
    expect(getPriceRowKind([row(1, TODAY)], 0, TODAY)).toBe('CURRENT')
  })

  it('지금 적용 중인 줄을 찾고, 전부 예정이면 null이다', () => {
    expect(getCurrentPrice(ROWS, TODAY)?.priceId).toBe(2)
    expect(getCurrentPrice([row(1, '2026-12-01')], TODAY)).toBeNull()
  })

  it('시작일 오름차순으로 정렬한 새 배열을 돌려준다', () => {
    const shuffled = [ROWS[2], ROWS[0], ROWS[1]]
    expect(sortPricesAscending(shuffled).map((r) => r.priceId)).toEqual([1, 2, 3])
    expect(shuffled[0].priceId).toBe(3)
  })
})

describe('마지막 확인일 오래됨', () => {
  it('90일까지는 괜찮고 91일부터 오래된 것이다', () => {
    expect(isVerifiedStale('2026-07-13', TODAY)).toBe(false) // 90일
    expect(isVerifiedStale('2026-07-12', TODAY)).toBe(true) // 91일
  })

  it('해석할 수 없는 날짜는 오래된 것으로 본다', () => {
    expect(isVerifiedStale('', TODAY)).toBe(true)
  })
})

describe('시작일 검사', () => {
  it('첫 줄: 오늘 이하여야 하고, 지난 날짜는 안내만 한다', () => {
    expect(checkFirstPriceStartDate('2026-10-12', TODAY).issue).toBe('FIRST_IN_FUTURE')
    expect(checkFirstPriceStartDate(TODAY, TODAY)).toEqual({ issue: null, isPastDate: false })
    expect(checkFirstPriceStartDate('2026-09-01', TODAY)).toEqual({ issue: null, isPastDate: true })
    expect(checkFirstPriceStartDate('', TODAY).issue).toBe('REQUIRED')
  })

  it('추가: 마지막 줄과 같으면 중복, 더 이르면 순서 오류, 늦으면 통과', () => {
    expect(checkAddPriceStartDate(ROWS, '2026-11-10', TODAY).issue).toBe('DUPLICATE')
    expect(checkAddPriceStartDate(ROWS, '2026-11-09', TODAY).issue).toBe('NOT_AFTER_PREVIOUS')
    expect(checkAddPriceStartDate(ROWS, '2026-12-01', TODAY)).toEqual({
      issue: null,
      isPastDate: false,
    })
  })

  it('추가: 마지막 줄이 과거이면 그보다 늦은 지난 날짜는 오류가 아니라 안내다', () => {
    const rows = [row(1, '2026-01-01')]
    expect(checkAddPriceStartDate(rows, '2026-05-01', TODAY)).toEqual({
      issue: null,
      isPastDate: true,
    })
  })

  it('최근 줄 수정: 앞 줄이 있으면 그 시작일보다 늦어야 하고, 하나뿐이면 첫 줄 규칙이다', () => {
    expect(checkEditLastPriceStartDate(ROWS, '2026-10-01', TODAY).issue).toBe('DUPLICATE')
    expect(checkEditLastPriceStartDate(ROWS, '2026-09-01', TODAY).issue).toBe('NOT_AFTER_PREVIOUS')
    expect(checkEditLastPriceStartDate(ROWS, '2026-11-20', TODAY).issue).toBeNull()
    expect(checkEditLastPriceStartDate([row(1, '2026-01-01')], '2026-12-01', TODAY).issue).toBe(
      'FIRST_IN_FUTURE',
    )
  })
})

describe('월 구독요금 입력', () => {
  it('0 이상 정수만 받는다', () => {
    expect(checkMonthlyPriceText('13500')).toBeNull()
    expect(checkMonthlyPriceText('0')).toBeNull()
    expect(checkMonthlyPriceText('')).not.toBeNull()
    expect(checkMonthlyPriceText('13.5')).not.toBeNull()
    expect(checkMonthlyPriceText('-1')).not.toBeNull()
    expect(checkMonthlyPriceText('1000001')).not.toBeNull()
  })
})
