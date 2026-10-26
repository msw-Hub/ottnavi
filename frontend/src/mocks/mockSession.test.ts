import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  getMockRole,
  isMockOnboardingDone,
  resetMockSession,
  setMockRole,
  switchMockRole,
} from '@/mocks/mockSession'

afterEach(() => {
  resetMockSession() // 역할·온보딩 여부는 모듈 변수 + sessionStorage라 테스트마다 처음으로 되돌린다
  vi.resetModules()
})

describe('mockSession 새로고침 유지', () => {
  it('역할을 바꾸면 sessionStorage에도 적어 새로고침 뒤에 되살린다', async () => {
    switchMockRole('USER')
    expect(window.sessionStorage.getItem('ottnavi:mock-role')).toBe('USER')

    // 새로고침 = 모듈이 처음부터 다시 읽히는 것. 저장된 값에서 역할과 온보딩 완료 여부를 되살려야 한다
    vi.resetModules()
    const reloaded = await import('@/mocks/mockSession')
    expect(reloaded.getMockRole()).toBe('USER')
    expect(reloaded.isMockOnboardingDone()).toBe(true)
  })

  it('비로그인으로 바꾸면 저장된 역할을 지운다', () => {
    setMockRole('ADMIN')
    setMockRole('GUEST')
    expect(window.sessionStorage.getItem('ottnavi:mock-role')).toBeNull()
    expect(getMockRole()).toBe('GUEST')
  })

  it('resetMockSession은 저장된 값까지 지운다', () => {
    switchMockRole('USER')
    resetMockSession()
    expect(window.sessionStorage.getItem('ottnavi:mock-role')).toBeNull()
    expect(window.sessionStorage.getItem('ottnavi:mock-onboarding-done')).toBeNull()
    expect(isMockOnboardingDone()).toBe(false)
  })

  it('알 수 없는 저장 값은 비로그인으로 본다', async () => {
    window.sessionStorage.setItem('ottnavi:mock-role', 'SUPERUSER')
    vi.resetModules()
    const reloaded = await import('@/mocks/mockSession')
    expect(reloaded.getMockRole()).toBe('GUEST')
  })
})
