import type { ConsentStatus } from '@/features/consent/components/ConsentSettingsPanel'

/*
 * 방문 통계(GA4) 동의의 저장 형식과 판정 규칙이다(순수 함수만 둔다: 브라우저 저장소·시계에 직접 닿지 않아 단위 테스트가 쉽다).
 * 사용자 결정(2026-10-31): 동의 항목은 방문 통계 하나 / 동의는 유지 / 거부는 6개월 뒤 다시 묻는다.
 */

// 브라우저 localStorage에 선택을 저장하는 키. 값은 JSON 문자열({ status, decidedAt })이다.
// 동의 상태는 로그인 정보가 아니라 "이 브라우저의 선택"이라 로그인과 무관하게 저장한다(Access Token 저장소와는 다른 값이다)
export const CONSENT_STORAGE_KEY = 'ottnavi.consent.analytics'

// 거부한 뒤 다시 묻기까지의 개월 수. 기간을 바꿀 때 이 상수 하나만 고친다(처리방침 문구도 함께 확인한다)
export const DENIED_RECHECK_MONTHS = 6

// 저장하는 선택값. 'unset'은 저장하지 않는다(= 저장값이 없거나 유효하지 않은 상태)
export interface StoredConsent {
  status: 'granted' | 'denied'
  decidedAt: string // 선택한 시각(ISO 8601)
}

/** 저장할 문자열을 만든다. */
export function serializeConsent(status: 'granted' | 'denied', now: Date): string {
  const stored: StoredConsent = { status, decidedAt: now.toISOString() }
  return JSON.stringify(stored)
}

/** 저장값 문자열을 읽는다. 형식이 맞지 않거나 손상됐으면 null이다. */
export function parseStoredConsent(raw: string | null): StoredConsent | null {
  // 1. 저장값이 없으면 선택한 적이 없다
  if (raw === null) return null
  try {
    // 2. JSON이 아니거나 모양이 다르면 손상된 값으로 보고 버린다(동의로 잘못 읽히면 안 된다)
    const value: unknown = JSON.parse(raw)
    if (typeof value !== 'object' || value === null) return null
    const { status, decidedAt } = value as Record<string, unknown>
    if (status !== 'granted' && status !== 'denied') return null
    if (typeof decidedAt !== 'string' || Number.isNaN(new Date(decidedAt).getTime())) return null
    return { status, decidedAt }
  } catch {
    return null
  }
}

/**
 * 저장값과 현재 시각으로 동의 상태를 정한다.
 * - 저장값 없음·손상 → 'unset'(다시 묻는다. 손상된 값을 동의로 읽는 것보다 한 번 더 묻는 쪽이 안전하다)
 * - 'granted' → 계속 'granted'(기간 없음. 철회는 설정에서 사용자가 한다)
 * - 'denied' → 선택 후 DENIED_RECHECK_MONTHS개월이 지나면 'unset', 그 전에는 'denied'
 */
export function resolveConsentStatus(raw: string | null, now: Date): ConsentStatus {
  // 1. 저장값을 읽는다
  const stored = parseStoredConsent(raw)
  if (stored === null) return 'unset'

  // 2. 동의는 그대로 유지한다
  if (stored.status === 'granted') return 'granted'

  // 3. 거부는 선택일에 6개월을 달력 기준으로 더한 시각이 지났는지 본다
  const recheckAt = new Date(stored.decidedAt)
  recheckAt.setMonth(recheckAt.getMonth() + DENIED_RECHECK_MONTHS)
  return now.getTime() >= recheckAt.getTime() ? 'unset' : 'denied'
}
