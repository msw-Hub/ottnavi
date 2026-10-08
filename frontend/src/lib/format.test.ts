import { describe, expect, it } from 'vitest'
import { EMPTY_DISPLAY, formatDataAsOf, formatMinutes, formatWon } from '@/lib/format'

// 화면 표시 형식이 바뀌면 사용자에게 바로 보이므로 경계값을 고정해 둔다(format.ts 함수 주석의 규칙과 짝을 이룬다).
describe('formatWon', () => {
  it.each([
    [15000, '15,000원'],
    [0, '0원'],
    [999, '999원'],
    [1234567, '1,234,567원'],
    [-3000, '-3,000원'],
    [13500.6, '13,501원'], // 소수는 원 단위로 반올림
    [-0.4, '0원'], // 반올림해 -0이 되는 값은 "-0원"이 아니라 "0원"
  ])('%s → %s', (amount, expected) => {
    expect(formatWon(amount)).toBe(expected)
  })

  it.each([NaN, Infinity, -Infinity, null, undefined])('%s는 "-"로 보인다', (amount) => {
    expect(formatWon(amount)).toBe(EMPTY_DISPLAY)
  })
})

describe('formatMinutes', () => {
  it.each([
    [750, '12시간 30분'],
    [0, '0분'],
    [1, '1분'],
    [59, '59분'],
    [60, '1시간'],
    [61, '1시간 1분'],
    [120, '2시간'],
    [1500, '25시간'], // 일 단위로 올리지 않는다
    [90.6, '1시간 31분'], // 소수는 분 단위로 반올림
    [59.6, '1시간'], // 반올림해 60분이 되면 정각으로 보인다
  ])('%s분 → %s', (minutes, expected) => {
    expect(formatMinutes(minutes)).toBe(expected)
  })

  it.each([-1, -60, NaN, Infinity, null, undefined])('%s는 "-"로 보인다', (minutes) => {
    expect(formatMinutes(minutes)).toBe(EMPTY_DISPLAY)
  })
})

describe('formatDataAsOf', () => {
  it('날짜(YYYY-MM-DD)를 한국어 날짜로 바꾼다', () => {
    expect(formatDataAsOf('2026-10-09')).toBe('2026년 10월 9일')
    expect(formatDataAsOf('2026-01-01')).toBe('2026년 1월 1일')
    expect(formatDataAsOf('2028-02-29')).toBe('2028년 2월 29일') // 윤년
  })

  it('일시(ISO-8601)는 한국 시간 기준 날짜로 바꾼다', () => {
    // UTC 10월 8일 16시 = 한국 10월 9일 01시
    expect(formatDataAsOf('2026-10-08T16:00:00Z')).toBe('2026년 10월 9일')
    expect(formatDataAsOf('2026-10-09T14:00:00+09:00')).toBe('2026년 10월 9일')
    expect(formatDataAsOf(new Date('2026-10-09T00:30:00+09:00'))).toBe('2026년 10월 9일')
  })

  it.each([
    null,
    undefined,
    '',
    '   ',
    'abc',
    '2026-02-30', // 없는 날짜
    '2027-02-29', // 윤년이 아닌 해의 2월 29일
    '2026-13-01',
  ])('해석할 수 없는 값(%s)은 null', (value) => {
    expect(formatDataAsOf(value)).toBeNull()
  })

  it('잘못된 Date 객체는 null', () => {
    expect(formatDataAsOf(new Date('invalid'))).toBeNull()
  })
})
