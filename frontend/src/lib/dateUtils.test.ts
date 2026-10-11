import { describe, expect, it } from 'vitest'
import { addDays, diffInDays, getTodayDateString } from '@/lib/dateUtils'

describe('getTodayDateString', () => {
  it('한국 시간 기준 날짜를 돌려준다', () => {
    // UTC 15:30은 한국 시간으로 다음 날 00:30
    expect(getTodayDateString(new Date('2026-10-10T15:30:00Z'))).toBe('2026-10-11')
    expect(getTodayDateString(new Date('2026-10-10T14:30:00Z'))).toBe('2026-10-10')
  })
})

describe('addDays / diffInDays', () => {
  it('월·연 경계를 넘어 더하고 뺀다', () => {
    expect(addDays('2026-10-30', 3)).toBe('2026-11-02')
    expect(addDays('2026-01-02', -3)).toBe('2025-12-30')
  })

  it('날짜 차이를 일 단위로 센다', () => {
    expect(diffInDays('2026-07-12', '2026-10-10')).toBe(90)
    expect(diffInDays('2026-10-10', '2026-10-09')).toBe(-1)
  })
})
