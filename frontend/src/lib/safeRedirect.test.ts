import { describe, expect, it } from 'vitest'
import { getSafeRedirectPath } from '@/lib/safeRedirect'

describe('getSafeRedirectPath', () => {
  it('이 사이트 안의 경로(검색어·해시 포함)는 그대로 돌려준다', () => {
    expect(getSafeRedirectPath('/titles/movie/496243')).toBe('/titles/movie/496243')
    expect(getSafeRedirectPath('/?q=기생충#top')).toBe('/?q=기생충#top')
  })

  it('외부 주소나 외부로 해석될 수 있는 값은 fallback으로 바꾼다', () => {
    expect(getSafeRedirectPath('https://evil.example/x')).toBe('/')
    expect(getSafeRedirectPath('//evil.example')).toBe('/')
    expect(getSafeRedirectPath('/\\evil.example')).toBe('/')
    expect(getSafeRedirectPath('javascript:alert(1)')).toBe('/')
    expect(getSafeRedirectPath('/a\nb')).toBe('/')
    expect(getSafeRedirectPath(undefined)).toBe('/')
    expect(getSafeRedirectPath(42, '/home')).toBe('/home')
  })

  it('로그인 흐름 경로는 돌아갈 곳으로 쓰지 않는다(같은 곳을 맴돌기 때문)', () => {
    expect(getSafeRedirectPath('/login')).toBe('/')
    expect(getSafeRedirectPath('/auth/callback?x=1')).toBe('/')
  })
})
