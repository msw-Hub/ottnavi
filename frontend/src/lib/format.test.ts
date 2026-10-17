import { describe, expect, it } from 'vitest'
import {
  EMPTY_DISPLAY,
  formatDataAsOf,
  formatDate,
  formatDateTime,
  formatDateTimeKorean,
  formatElapsed,
  formatIsoDate,
  formatMinutes,
  formatSeasonName,
  formatWon,
} from '@/lib/format'

// 시즌 표기 통일(HANDOFF 5-1 #1): 영어 기본 이름만 한국어로 바꾸고 작품이 붙인 고유 이름은 지키는지 본다
describe('formatSeasonName', () => {
  it('영어 기본 이름 "Season N"은 "시즌 N"으로 바꾼다', () => {
    expect(formatSeasonName('Season 1')).toBe('시즌 1')
    expect(formatSeasonName('Season 12')).toBe('시즌 12')
    // 앞자리 0과 앞뒤·가운데 여분 공백은 정리한다
    expect(formatSeasonName(' Season  03 ')).toBe('시즌 3')
  })

  it('이미 한국어인 이름과 고유 이름은 그대로 둔다', () => {
    expect(formatSeasonName('시즌 2')).toBe('시즌 2')
    expect(formatSeasonName('스페셜')).toBe('스페셜')
    // "Season N" 뒤에 부제가 붙으면 작품이 붙인 이름이라 바꾸지 않는다
    expect(formatSeasonName('Season 1: 새로운 시작')).toBe('Season 1: 새로운 시작')
    expect(formatSeasonName('Specials')).toBe('Specials')
    // 대소문자가 다르면 기본 이름 형식이 아니라고 본다
    expect(formatSeasonName('season 1')).toBe('season 1')
  })

  it('시즌이 없으면(null) 그대로 null', () => {
    expect(formatSeasonName(null)).toBeNull()
  })
})

describe('formatDate', () => {
  it('개봉일·방영일(YYYY-MM-DD)을 한국어 날짜로 바꾼다', () => {
    expect(formatDate('2021-09-17')).toBe('2021년 9월 17일')
    expect(formatDate('1995-11-22')).toBe('1995년 11월 22일')
  })

  it('일시는 한국 시간 기준 날짜로 바꾸고, 해석할 수 없는 값은 null', () => {
    expect(formatDate('2026-10-08T16:00:00Z')).toBe('2026년 10월 9일')
    expect(formatDate(null)).toBeNull()
    expect(formatDate('2026-02-30')).toBeNull()
    expect(formatDate('abc')).toBeNull()
  })

  it('formatDataAsOf와 같은 표기를 쓴다', () => {
    expect(formatDate('2026-10-09')).toBe(formatDataAsOf('2026-10-09'))
  })
})

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

describe('formatDateTime', () => {
  it('한국 시간 기준 "YYYY-MM-DD HH:mm"으로 바꾼다', () => {
    expect(formatDateTime('2026-10-10T03:24:00+09:00')).toBe('2026-10-10 03:24')
    // UTC 18:30은 한국 시간으로 다음 날 03:30
    expect(formatDateTime('2026-10-09T18:30:00Z')).toBe('2026-10-10 03:30')
  })

  it('자정은 24시가 아니라 00시로 보인다', () => {
    expect(formatDateTime('2026-10-10T00:05:00+09:00')).toBe('2026-10-10 00:05')
  })

  it('해석할 수 없거나 시각이 없는 값은 null', () => {
    expect(formatDateTime(null)).toBeNull()
    expect(formatDateTime('abc')).toBeNull()
    expect(formatDateTime('2026-10-10')).toBeNull()
  })
})

describe('formatDateTimeKorean', () => {
  it('한국 시간 기준 "YYYY년 M월 D일 HH:mm"으로 바꾼다', () => {
    expect(formatDateTimeKorean('2026-10-16T13:33:00+09:00')).toBe('2026년 10월 16일 13:33')
    // UTC 18:30은 한국 시간으로 다음 날 03:30, 월·일의 앞자리 0은 뗀다
    expect(formatDateTimeKorean('2026-10-08T18:30:00Z')).toBe('2026년 10월 9일 03:30')
  })

  it('해석할 수 없거나 시각이 없는 값은 null', () => {
    expect(formatDateTimeKorean(null)).toBeNull()
    expect(formatDateTimeKorean('2026-10-10')).toBeNull()
  })
})

describe('formatIsoDate', () => {
  it('날짜만 있는 값은 그대로 "YYYY-MM-DD"로 보인다', () => {
    expect(formatIsoDate('2026-10-08')).toBe('2026-10-08')
    expect(formatIsoDate('2026-01-05')).toBe('2026-01-05')
  })

  it('일시는 한국 시간 기준 날짜로 바꾼다', () => {
    // UTC 16:00은 한국 시간으로 다음 날 01:00
    expect(formatIsoDate('2026-10-08T16:00:00Z')).toBe('2026-10-09')
    expect(formatIsoDate(new Date('2026-12-31T15:30:00Z'))).toBe('2027-01-01')
  })

  it('없는 날짜·해석할 수 없는 값은 null', () => {
    expect(formatIsoDate(null)).toBeNull()
    expect(formatIsoDate(undefined)).toBeNull()
    expect(formatIsoDate('')).toBeNull()
    expect(formatIsoDate('2026-02-30')).toBeNull()
    expect(formatIsoDate('abc')).toBeNull()
  })
})

describe('formatElapsed', () => {
  const start = '2026-10-10T03:00:00+09:00'

  it.each([
    ['2026-10-10T03:24:00+09:00', '24분'],
    ['2026-10-10T04:05:00+09:00', '1시간 5분'],
    ['2026-10-10T04:00:00+09:00', '1시간'],
    ['2026-10-10T03:00:30+09:00', '1분 미만'],
  ])('%s까지 → %s', (end, expected) => {
    expect(formatElapsed(start, end)).toBe(expected)
  })

  it('끝난 시각이 없거나 시작보다 빠르거나 잘못된 값이면 "-"', () => {
    expect(formatElapsed(start, null)).toBe(EMPTY_DISPLAY)
    expect(formatElapsed(start, '2026-10-10T02:00:00+09:00')).toBe(EMPTY_DISPLAY)
    expect(formatElapsed(start, 'abc')).toBe(EMPTY_DISPLAY)
  })
})
