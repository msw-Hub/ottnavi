import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CONSENT_STORAGE_KEY } from '@/features/consent/consentPolicy'
import {
  readConsentStatus,
  resetConsentStoreForTest,
  useConsent,
  writeConsent,
} from '@/features/consent/consentStore'

/*
 * 동의 저장소 테스트.
 * 확인하는 것: 저장소 접근이 막혀도 죽지 않고 안전한 값을 돌려준다 / 다른 탭의 변경을 반영한다 / 철회 후 재동의가 되고 선택 시각이 갱신된다.
 * 막으려는 사고: 저장소 예외로 화면이 깨지는 것, 저장하지 못했는데 GA가 켜지는 것, 철회했는데 옛 시각이 남아 재질문 시점이 틀어지는 것.
 */
describe('consentStore', () => {
  beforeEach(() => {
    window.localStorage.clear()
    resetConsentStoreForTest()
  })
  afterEach(() => {
    vi.restoreAllMocks()
    window.localStorage.clear()
    resetConsentStoreForTest()
  })

  it('getItem이 예외를 던지면 unset이고 GA는 켜지지 않는다', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('저장소 차단')
    })
    expect(readConsentStatus()).toBe('unset')
    const { result } = renderHook(() => useConsent())
    expect(result.current.status).toBe('unset')
    expect(result.current.analyticsEnabled).toBe(false)
  })

  it('setItem이 예외를 던지면 이번 세션 메모리에만 두고 동의해도 GA는 켜지지 않는다', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('저장 공간 부족')
    })
    const { result } = renderHook(() => useConsent())
    act(() => result.current.accept())
    expect(result.current.status).toBe('granted')
    expect(result.current.analyticsEnabled).toBe(false)
  })

  it('다른 탭에서 저장소가 바뀌면(storage 이벤트) 상태를 반영한다', () => {
    const { result } = renderHook(() => useConsent())
    expect(result.current.status).toBe('unset')
    act(() => {
      window.localStorage.setItem(
        CONSENT_STORAGE_KEY,
        JSON.stringify({ status: 'granted', decidedAt: new Date().toISOString() }),
      )
      window.dispatchEvent(new StorageEvent('storage', { key: CONSENT_STORAGE_KEY }))
    })
    expect(result.current.status).toBe('granted')
    expect(result.current.analyticsEnabled).toBe(true)
  })

  it('철회하면 거부로 저장되며 선택 시각이 그 시점으로 갱신되고, 다시 동의할 수 있다', () => {
    const { result } = renderHook(() => useConsent())
    act(() => writeConsent('granted', new Date('2026-01-01T00:00:00Z')))
    expect(result.current.analyticsEnabled).toBe(true)

    // 철회(= 거부 저장): 시각이 철회한 날로 바뀐다
    act(() => writeConsent('denied', new Date('2026-03-01T00:00:00Z')))
    const stored = JSON.parse(window.localStorage.getItem(CONSENT_STORAGE_KEY) ?? '{}')
    expect(stored.status).toBe('denied')
    expect(stored.decidedAt).toBe('2026-03-01T00:00:00.000Z')
    expect(result.current.analyticsEnabled).toBe(false)
    // 철회일 기준 6개월(2026-09-01) 전에는 아직 거부, 이후에는 다시 묻는다
    expect(readConsentStatus(new Date('2026-08-31T00:00:00Z'))).toBe('denied')
    expect(readConsentStatus(new Date('2026-09-02T00:00:00Z'))).toBe('unset')

    // 철회 뒤 다시 동의
    act(() => result.current.accept())
    expect(result.current.status).toBe('granted')
    expect(result.current.analyticsEnabled).toBe(true)
  })
})
