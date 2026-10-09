import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/*
 * 목업 서버의 찜·설정·요금 수정값이 sessionStorage에 적혀 "새로고침 후에도 유지"되는지 확인한다.
 * 새로고침은 vi.resetModules()로 모듈을 다시 읽어 흉내 낸다(sessionStorage는 그대로 남는다).
 */
async function loadFresh() {
  vi.resetModules()
  const utils = await import('@/mocks/handlerUtils')
  const member = await import('@/mocks/memberHandlers')
  return { utils, member }
}

beforeEach(() => window.sessionStorage.clear())
afterEach(() => {
  window.sessionStorage.clear()
  vi.restoreAllMocks()
})

describe('목업 찜 목록 유지', () => {
  it('새로고침(모듈 다시 읽기) 뒤에도 찜과 다음 ID가 남는다', async () => {
    const first = await loadFresh()
    const id = first.utils.issueWishlistItemId()
    first.utils.mockWishlist.set(id, {
      mediaType: 'movie',
      tmdbId: 496243,
      seasonNumber: null,
      priority: 'WANT',
      watchedAt: null,
    })

    const second = await loadFresh()
    expect([...second.utils.mockWishlist.keys()]).toEqual([id])
    expect(second.utils.issueWishlistItemId()).toBe(id + 1)
  })

  it('resetMockWishlist가 저장된 값도 지운다', async () => {
    const { utils } = await loadFresh()
    utils.mockWishlist.set(utils.issueWishlistItemId(), {
      mediaType: 'movie',
      tmdbId: 1,
      seasonNumber: null,
      priority: 'WANT',
      watchedAt: null,
    })
    utils.resetMockWishlist()
    expect(window.sessionStorage.getItem('ottnavi:mock-wishlist')).toBeNull()
    expect((await loadFresh()).utils.mockWishlist.size).toBe(0)
  })

  it('저장소 쓰기가 실패해도 메모리로 계속 동작한다', async () => {
    const { utils } = await loadFresh()
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('quota', 'QuotaExceededError')
    })
    utils.mockWishlist.set(utils.issueWishlistItemId(), {
      mediaType: 'movie',
      tmdbId: 2,
      seasonNumber: null,
      priority: 'WANT',
      watchedAt: null,
    })
    expect(utils.mockWishlist.size).toBe(1)
  })

  it('저장된 값이 깨져 있으면 빈 목록으로 시작한다', async () => {
    window.sessionStorage.setItem('ottnavi:mock-wishlist', '{깨진 값')
    expect((await loadFresh()).utils.mockWishlist.size).toBe(0)
  })
})

describe('목업 설정·요금 수정값 유지', () => {
  it('요금 수정값이 새로고침 뒤에도 남고 reset이 저장값을 지운다', async () => {
    window.sessionStorage.setItem('ottnavi:mock-price-overrides', JSON.stringify([[1, 9900]]))
    const { member } = await loadFresh()
    member.resetMockPricing()
    expect(window.sessionStorage.getItem('ottnavi:mock-price-overrides')).toBeNull()
  })

  it('resetMockSettings가 저장된 설정을 지운다', async () => {
    window.sessionStorage.setItem('ottnavi:mock-settings', JSON.stringify({ subscriptions: [] }))
    const { member } = await loadFresh()
    member.resetMockSettings()
    expect(window.sessionStorage.getItem('ottnavi:mock-settings')).toBeNull()
  })
})
