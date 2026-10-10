import { describe, expect, it } from 'vitest'
import { PLAN_TYPE_DESCRIPTIONS } from '@/features/plan/planDisplay'

describe('PLAN_TYPE_DESCRIPTIONS', () => {
  it('절약형은 본문과 보조 문구를 나눠 가지고, 다른 유형은 보조 문구가 없다', () => {
    expect(PLAN_TYPE_DESCRIPTIONS.SAVER.text).toBe('꼭 볼 작품은 지키면서 가장 저렴한 조합이에요')
    expect(PLAN_TYPE_DESCRIPTIONS.SAVER.note).toBe('(보고 싶은 작품 일부는 못 볼 수 있어요)')
    expect(PLAN_TYPE_DESCRIPTIONS.RECOMMENDED.note).toBeUndefined()
    expect(PLAN_TYPE_DESCRIPTIONS.SIMPLE.note).toBeUndefined()
  })
})
