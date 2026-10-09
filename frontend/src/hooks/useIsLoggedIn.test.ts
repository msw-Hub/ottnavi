import { renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useIsLoggedIn } from '@/hooks/useIsLoggedIn'
import { setMockRole } from '@/mocks/mockSession'

/*
 * 로그인 여부 판단 테스트. 머리글(HeaderAccountNav)과 같은 기준(목업 모드일 때만 목업 역할을 믿는다)인지 확인한다.
 * 목업 모드는 vi.stubEnv로 흉내 낸다(MockRoleSwitcher 테스트와 같은 방식, 테스트마다 되돌린다).
 */

beforeEach(() => vi.stubEnv('VITE_USE_MOCK', 'true'))
afterEach(() => {
  vi.unstubAllEnvs()
  setMockRole('GUEST') // 역할은 모듈 변수라 테스트마다 비로그인으로 되돌린다
})

describe('useIsLoggedIn', () => {
  it('목업 모드에서 회원이면 true, 비로그인이면 false다', () => {
    setMockRole('USER')
    expect(renderHook(() => useIsLoggedIn()).result.current).toBe(true)

    setMockRole('GUEST')
    expect(renderHook(() => useIsLoggedIn()).result.current).toBe(false)
  })

  it('목업 모드가 아니면 역할이 USER여도 false다 (운영에서는 목업 역할을 믿지 않는다)', () => {
    vi.stubEnv('VITE_USE_MOCK', 'false')
    setMockRole('USER')

    expect(renderHook(() => useIsLoggedIn()).result.current).toBe(false)
  })
})
