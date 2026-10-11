/*
 * 날짜만 있는 값('YYYY-MM-DD')을 계산하는 순수 함수 모음이다(Task 029 준비).
 *
 * 표시 형식은 format.ts가 맡고, 여기는 "오늘이 언제인가", "N일 뒤", "며칠 차이"만 계산한다.
 * 날짜 계산을 문자열 비교·UTC 기준 Date로 하는 이유: new Date('2026-10-09')를 로컬 시간대로 읽으면 사용자 시간대에 따라
 * 하루가 밀린다(format.ts formatDate 주석). 그래서 날짜만 있는 값은 항상 UTC 자정으로 만들어 더하고 뺀다.
 * 'YYYY-MM-DD'는 글자 순서와 날짜 순서가 같아 `a < b` 문자열 비교로 날짜 앞뒤를 가릴 수 있다.
 */

const SEOUL_DATE_FORMATTER = new Intl.DateTimeFormat('en-CA', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  timeZone: 'Asia/Seoul',
}) // en-CA는 'YYYY-MM-DD' 모양으로 읽힌다

const DAY_MS = 86_400_000

// 'YYYY-MM-DD'를 UTC 자정 Date로 만든다
function toUtcDate(dateText: string): Date {
  const [year, month, day] = dateText.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day))
}

/** 한국 시간 기준 오늘 날짜('YYYY-MM-DD')를 돌려준다. 서비스와 수집 배치가 한국 날짜 기준이라 컴퓨터 시간대와 무관하게 맞춘다. */
export function getTodayDateString(now: Date = new Date()): string {
  return SEOUL_DATE_FORMATTER.format(now)
}

/** 날짜('YYYY-MM-DD')에 일수를 더한다(음수면 뺀다). */
export function addDays(dateText: string, days: number): string {
  return new Date(toUtcDate(dateText).getTime() + days * DAY_MS).toISOString().slice(0, 10)
}

/** 두 날짜 사이의 일수(to - from)를 돌려준다. to가 더 늦으면 양수다. */
export function diffInDays(from: string, to: string): number {
  return Math.round((toUtcDate(to).getTime() - toUtcDate(from).getTime()) / DAY_MS)
}
