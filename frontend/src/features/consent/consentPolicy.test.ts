import { describe, expect, it } from 'vitest'
import { resolveConsentStatus, serializeConsent } from '@/features/consent/consentPolicy'

/*
 * 동의 상태 판정 테스트.
 * 확인하는 것: 거부는 6개월 전까지 유지되고 이후 다시 묻는다 / 동의는 오래돼도 유지된다 / 저장값이 없거나 손상되면 'unset'이다.
 * 막으려는 사고: 거부가 영구히 기억되거나(재질문 없음) 6개월 전에 다시 묻는 것, 손상된 값이 동의로 읽혀 GA가 켜지는 것.
 */
describe('resolveConsentStatus', () => {
  const decidedAt = new Date('2026-01-31T12:00:00.000Z')

  it('거부는 6개월이 지나기 전까지 denied, 지나면 unset이다', () => {
    const raw = serializeConsent('denied', decidedAt)
    expect(resolveConsentStatus(raw, new Date('2026-07-29T00:00:00.000Z'))).toBe('denied')
    expect(resolveConsentStatus(raw, new Date('2026-09-01T00:00:00.000Z'))).toBe('unset')
  })

  it('동의는 시간이 지나도 granted로 유지된다', () => {
    const raw = serializeConsent('granted', decidedAt)
    expect(resolveConsentStatus(raw, new Date('2030-01-01T00:00:00.000Z'))).toBe('granted')
  })

  it('저장값이 없거나 손상됐으면 unset이다', () => {
    const now = new Date('2026-02-01T00:00:00.000Z')
    expect(resolveConsentStatus(null, now)).toBe('unset')
    expect(resolveConsentStatus('not json', now)).toBe('unset')
    expect(resolveConsentStatus('"granted"', now)).toBe('unset')
    expect(resolveConsentStatus('{"status":"granted"}', now)).toBe('unset')
    expect(resolveConsentStatus('{"status":"yes","decidedAt":"2026-01-01"}', now)).toBe('unset')
    expect(resolveConsentStatus('{"status":"granted","decidedAt":"abc"}', now)).toBe('unset')
  })
})
