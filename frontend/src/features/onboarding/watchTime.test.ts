import { describe, expect, it } from 'vitest'
import {
  convertWeeklyToMonthlyMinutes,
  describeWeeklyConversion,
  resolveMonthlyWatchMinutes,
  resolveWatchPreset,
  WATCH_TIME_PRESETS,
} from '@/features/onboarding/watchTime'

describe('convertWeeklyToMonthlyMinutes', () => {
  it('주 N회 x 회당 M시간 x 4.33을 분으로 환산해 반올림한다', () => {
    // 3 x 2 x 60 x 4.33 = 1558.8 -> 1559
    expect(convertWeeklyToMonthlyMinutes(3, 2)).toBe(1559)
    // 1 x 1 x 60 x 4.33 = 259.8 -> 260
    expect(convertWeeklyToMonthlyMinutes(1, 1)).toBe(260)
  })

  it('소수 시간도 환산한다', () => {
    // 2 x 1.5 x 60 x 4.33 = 779.4 -> 779
    expect(convertWeeklyToMonthlyMinutes(2, 1.5)).toBe(779)
  })

  it('0 이하이거나 숫자가 아니면 0을 돌려준다', () => {
    expect(convertWeeklyToMonthlyMinutes(0, 2)).toBe(0)
    expect(convertWeeklyToMonthlyMinutes(3, -1)).toBe(0)
    expect(convertWeeklyToMonthlyMinutes(Number.NaN, 2)).toBe(0)
    expect(convertWeeklyToMonthlyMinutes(3, Number.POSITIVE_INFINITY)).toBe(0)
  })
})

describe('describeWeeklyConversion', () => {
  it('환산 결과를 formatMinutes 형식으로 보여 준다', () => {
    expect(describeWeeklyConversion(3, 2)).toBe('월 약 25시간 59분')
  })
})

describe('resolveMonthlyWatchMinutes', () => {
  it('프리셋은 정해진 분을 돌려준다', () => {
    expect(resolveMonthlyWatchMinutes({ mode: 'preset', preset: 'LIGHT' })).toBe(
      WATCH_TIME_PRESETS.LIGHT,
    )
    expect(resolveMonthlyWatchMinutes({ mode: 'preset', preset: 'HEAVY' })).toBe(1800)
  })

  it('필요한 칸이 비어 있으면 null이다', () => {
    expect(resolveMonthlyWatchMinutes({ mode: 'preset' })).toBeNull()
    expect(resolveMonthlyWatchMinutes({ mode: 'weekly', timesPerWeek: 3 })).toBeNull()
    expect(resolveMonthlyWatchMinutes({ mode: 'direct' })).toBeNull()
  })

  it('환산과 직접 입력은 각자의 값을 쓴다', () => {
    expect(resolveMonthlyWatchMinutes({ mode: 'weekly', timesPerWeek: 1, hoursPerTime: 1 })).toBe(
      260,
    )
    expect(resolveMonthlyWatchMinutes({ mode: 'direct', directMinutes: 600 })).toBe(600)
  })
})

describe('resolveWatchPreset', () => {
  it('프리셋이면 그 값, 환산·직접 입력이면 CUSTOM이다', () => {
    expect(resolveWatchPreset({ mode: 'preset', preset: 'NORMAL' })).toBe('NORMAL')
    expect(resolveWatchPreset({ mode: 'weekly', timesPerWeek: 2, hoursPerTime: 1 })).toBe('CUSTOM')
    expect(resolveWatchPreset({ mode: 'direct', directMinutes: 300 })).toBe('CUSTOM')
  })
})
