/*
 * 월 구독요금 규칙을 계산하는 순수 함수 모음이다(Task 029, SCR-14).
 *
 * 왜 따로 두나: 규칙(종료일 계산, 지금 적용 중인 줄 판정, 시작일 검사)은 화면(폼)과 목업 서버(adminHandlers)가 똑같이 써야 한다.
 * 화면에만 두면 서버가 규칙을 어겨도 모르고, 서버에만 두면 오류를 받아야 알 수 있다. 순수 함수라 날짜만 넘기면 테스트할 수 있다.
 *
 * 월 구독요금 모델(사용자 결정 2026-10-11): 줄 = (월 구독요금, 적용 시작일, 마지막 확인일).
 * - 적용 종료일은 저장하지 않는다. "다음 줄 시작일의 전날"로 계산하고, 마지막 줄은 끝이 없다("계속").
 * - 날짜 D의 요금 = 시작일이 D 이전(같은 날 포함)이면서 가장 늦은 줄.
 * - 첫 줄 시작일은 오늘 이하 / 같은 시작일 금지 / 새 줄 시작일은 마지막 줄 시작일보다 늦어야 한다.
 * - 가장 최근 줄만 수정·삭제할 수 있고, 줄이 하나뿐이면 삭제할 수 없다.
 * - 지난 날짜를 시작일로 넣는 것은 오류가 아니라 안내(isPastDate)만 한다.
 * 모든 날짜는 'YYYY-MM-DD' 문자열이며, 글자 순서가 날짜 순서와 같아 `<` 비교를 쓴다(lib/dateUtils 주석).
 */
import type { AdminProductPrice } from '@/features/admin/mockTypes'
import { addDays, diffInDays } from '@/lib/dateUtils'

// 마지막 확인일이 오늘로부터 이 일수를 넘으면 "요금 확인일 오래됨" 태그를 붙인다
export const STALE_VERIFIED_DAYS = 90

// 월 구독요금 상한(원). 오입력(0 하나 더 입력 등)을 막는 안전선이다. SCR-09의 사용자 금액 상한과 같다
export const MAX_MONTHLY_PRICE = 1_000_000

// 'YYYY-MM-DD' 모양 검사. <input type="date">는 비어 있거나 이 모양이다
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

// 월 구독요금 줄의 표시 구분: 지금 적용 중 / 앞으로 적용 예정 / 이미 끝난 줄
export type PriceRowKind = 'CURRENT' | 'UPCOMING' | 'PAST'

/** 적용 시작일이 빠른 순으로 정렬한 새 배열을 돌려준다(원본은 바꾸지 않는다). */
export function sortPricesAscending(prices: AdminProductPrice[]): AdminProductPrice[] {
  return [...prices].sort((a, b) => (a.effectiveFrom < b.effectiveFrom ? -1 : 1))
}

/** 줄의 적용 종료일(포함)을 돌려준다. 다음 줄 시작일의 전날이고, 마지막 줄이면 null("계속")이다. 입력은 시작일 오름차순이어야 한다. */
export function getPriceEndDate(sortedAsc: AdminProductPrice[], index: number): string | null {
  const next = sortedAsc[index + 1]
  return next ? addDays(next.effectiveFrom, -1) : null
}

/** 줄이 오늘 기준 지금 적용 중인지, 예정인지, 지난 줄인지 돌려준다. */
export function getPriceRowKind(
  sortedAsc: AdminProductPrice[],
  index: number,
  today: string,
): PriceRowKind {
  const row = sortedAsc[index]
  if (row.effectiveFrom > today) return 'UPCOMING'
  const next = sortedAsc[index + 1]
  return next && next.effectiveFrom <= today ? 'PAST' : 'CURRENT'
}

/** 오늘 적용 중인 줄을 돌려준다. 시작일이 오늘 이전인 줄이 없으면(전부 예정) null이다. */
export function getCurrentPrice(
  sortedAsc: AdminProductPrice[],
  today: string,
): AdminProductPrice | null {
  const started = sortedAsc.filter((row) => row.effectiveFrom <= today)
  return started.length > 0 ? started[started.length - 1] : null
}

/** 마지막 확인일이 오늘로부터 90일을 넘었는지 돌려준다. 날짜를 해석할 수 없으면 오래된 것으로 본다(확인을 유도). */
export function isVerifiedStale(lastVerifiedAt: string, today: string): boolean {
  if (!DATE_PATTERN.test(lastVerifiedAt)) return true
  return diffInDays(lastVerifiedAt, today) > STALE_VERIFIED_DAYS
}

// 시작일 검사 결과 종류
export type PriceStartDateIssue =
  | 'REQUIRED' // 비어 있거나 날짜 모양이 아니다
  | 'DUPLICATE' // 이미 있는 줄과 같은 시작일이다
  | 'NOT_AFTER_PREVIOUS' // 앞 줄(추가라면 마지막 줄)의 시작일보다 늦지 않다
  | 'FIRST_IN_FUTURE' // 첫 줄인데 시작일이 오늘보다 뒤다

export interface PriceStartDateCheck {
  issue: PriceStartDateIssue | null // 오류 종류. 없으면 null
  isPastDate: boolean // 시작일이 오늘보다 이전이다. 오류가 아니라 안내만 한다(오류가 있으면 false)
}

// 시작일 오류 → 화면 문구. 입력 칸 아래 한 자리에 보인다
export const PRICE_START_DATE_MESSAGES: Record<PriceStartDateIssue, string> = {
  REQUIRED: '적용 시작일을 입력해 주세요.',
  DUPLICATE: '같은 적용 시작일의 월 구독요금이 이미 있어요.',
  NOT_AFTER_PREVIOUS: '적용 시작일은 앞 줄의 시작일보다 늦어야 해요.',
  FIRST_IN_FUTURE: '첫 월 구독요금의 적용 시작일은 오늘 또는 그 이전이어야 해요.',
}

// 지난 날짜 안내 문구(오류가 아니다)
export const PRICE_PAST_DATE_NOTICE = '이미 지난 날짜예요. 저장하면 바로 적용돼요.'

/** 첫 월 구독요금(OTT 구독상품 등록 때)의 시작일을 검사한다: 오늘 이하여야 한다. */
export function checkFirstPriceStartDate(startDate: string, today: string): PriceStartDateCheck {
  if (!DATE_PATTERN.test(startDate)) return { issue: 'REQUIRED', isPastDate: false }
  if (startDate > today) return { issue: 'FIRST_IN_FUTURE', isPastDate: false }
  return { issue: null, isPastDate: startDate < today }
}

/**
 * 새 줄(마지막 줄 뒤에 추가)의 시작일을 검사한다. 마지막 줄 시작일과 같으면 DUPLICATE, 더 이르면 NOT_AFTER_PREVIOUS다.
 * 줄이 하나도 없으면 첫 줄 규칙을 따른다.
 */
export function checkAddPriceStartDate(
  sortedAsc: AdminProductPrice[],
  startDate: string,
  today: string,
): PriceStartDateCheck {
  if (sortedAsc.length === 0) return checkFirstPriceStartDate(startDate, today)
  if (!DATE_PATTERN.test(startDate)) return { issue: 'REQUIRED', isPastDate: false }
  const last = sortedAsc[sortedAsc.length - 1]
  if (startDate === last.effectiveFrom) return { issue: 'DUPLICATE', isPastDate: false }
  if (startDate < last.effectiveFrom) return { issue: 'NOT_AFTER_PREVIOUS', isPastDate: false }
  return { issue: null, isPastDate: startDate < today }
}

/**
 * 가장 최근 줄을 수정할 때의 새 시작일을 검사한다. 앞 줄이 있으면 그 시작일보다 늦어야 하고, 줄이 하나뿐이면 첫 줄 규칙(오늘 이하)이다.
 */
export function checkEditLastPriceStartDate(
  sortedAsc: AdminProductPrice[],
  startDate: string,
  today: string,
): PriceStartDateCheck {
  if (sortedAsc.length <= 1) return checkFirstPriceStartDate(startDate, today)
  if (!DATE_PATTERN.test(startDate)) return { issue: 'REQUIRED', isPastDate: false }
  const previous = sortedAsc[sortedAsc.length - 2]
  if (startDate === previous.effectiveFrom) return { issue: 'DUPLICATE', isPastDate: false }
  if (startDate < previous.effectiveFrom) return { issue: 'NOT_AFTER_PREVIOUS', isPastDate: false }
  return { issue: null, isPastDate: startDate < today }
}

/** 월 구독요금 입력 글자를 검사한다. 올바르면 null, 아니면 안내 문장을 돌려준다(0 이상 정수, 상한 이내). */
export function checkMonthlyPriceText(text: string): string | null {
  const trimmed = text.trim()
  if (trimmed === '') return '월 구독요금을 입력해 주세요.'
  if (!/^\d+$/.test(trimmed)) return '월 구독요금은 숫자만 입력해 주세요.'
  if (Number(trimmed) > MAX_MONTHLY_PRICE) return '월 구독요금은 100만 원 이하로 입력해 주세요.'
  return null
}
