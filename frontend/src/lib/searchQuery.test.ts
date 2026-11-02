import { describe, expect, it } from 'vitest'
import { isSearchQueryTooShort } from '@/lib/searchQuery'

describe('검색어가 너무 짧은지', () => {
  it('검색어가 없으면 짧은 것이 아니라 없는 것이다', () => {
    expect(isSearchQueryTooShort('')).toBe(false)
    expect(isSearchQueryTooShort('   ')).toBe(false)
  })

  it('앞뒤 공백을 뺀 글자 수가 1이면 짧다', () => {
    expect(isSearchQueryTooShort('가')).toBe(true)
    expect(isSearchQueryTooShort(' a ')).toBe(true)
  })

  it('2글자 이상이면 짧지 않다', () => {
    expect(isSearchQueryTooShort('기생')).toBe(false)
    expect(isSearchQueryTooShort('ab')).toBe(false)
  })

  it('이모지 하나는 1글자로 센다', () => {
    expect(isSearchQueryTooShort('😀')).toBe(true)
  })
})
