import { describe, expect, it } from 'vitest'
import { hasSeveralSeasonsOfOneSeries, PLAN_TYPE_DESCRIPTIONS } from '@/features/plan/planDisplay'

describe('PLAN_TYPE_DESCRIPTIONS', () => {
  it('절약형은 본문과 보조 문구를 나눠 가지고, 다른 유형은 보조 문구가 없다', () => {
    expect(PLAN_TYPE_DESCRIPTIONS.SAVER.text).toBe('꼭 볼 작품은 지키면서 가장 저렴한 조합이에요')
    expect(PLAN_TYPE_DESCRIPTIONS.SAVER.note).toBe('(보고 싶은 작품 일부는 못 볼 수 있어요)')
    expect(PLAN_TYPE_DESCRIPTIONS.RECOMMENDED.note).toBeUndefined()
    expect(PLAN_TYPE_DESCRIPTIONS.SIMPLE.note).toBeUndefined()
  })
})

describe('hasSeveralSeasonsOfOneSeries', () => {
  // 시즌 순서 안내를 보일지 판정하는 데 필요한 값만 가진 최소 초안
  function draftOf(
    assignments: { tmdbId: number; seasonNumber: number | null }[],
    unscheduled: { tmdbId: number; seasonNumber: number | null }[] = [],
  ) {
    const toItem = (item: { tmdbId: number; seasonNumber: number | null }, index: number) => ({
      planItemId: index,
      mediaType: 'tv' as const,
      title: '드라마',
      priority: 'WANT' as const,
      ...item,
    })
    return {
      months: [
        {
          monthIndex: 0,
          month: '2026-10',
          isConfirmed: true,
          cost: 0,
          products: [],
          assignments: assignments.map((item, index) => ({
            ...toItem(item, index),
            ottServiceId: 1,
            watchMin: 60,
            segmentIndex: 1,
            segmentCount: 1,
            isContinuation: false,
          })),
        },
      ],
      unscheduled: unscheduled.map((item, index) => ({
        ...toItem(item, 100 + index),
        reason: 'TIME' as const,
      })),
    }
  }

  it('같은 드라마의 서로 다른 시즌이 둘 이상이면 true(배정과 남은 작품을 함께 본다)', () => {
    expect(
      hasSeveralSeasonsOfOneSeries(
        draftOf([{ tmdbId: 1, seasonNumber: 1 }], [{ tmdbId: 1, seasonNumber: 2 }]),
      ),
    ).toBe(true)
  })

  it('다른 드라마의 시즌이거나, 같은 시즌의 조각이 여럿이면 false', () => {
    expect(
      hasSeveralSeasonsOfOneSeries(
        draftOf([
          { tmdbId: 1, seasonNumber: 1 },
          { tmdbId: 2, seasonNumber: 1 },
        ]),
      ),
    ).toBe(false)
    expect(
      hasSeveralSeasonsOfOneSeries(
        draftOf([
          { tmdbId: 1, seasonNumber: 1 },
          { tmdbId: 1, seasonNumber: 1 },
        ]),
      ),
    ).toBe(false)
  })
})
