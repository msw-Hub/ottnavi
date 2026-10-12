import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { getTitleDetail, searchTitles } from '@/api/generated/catalog/catalog'
import type { AddWishlistItemRequest } from '@/api/generated/model'
import {
  addWishlistItem,
  removeWishlistItem,
  updateWishlistItem,
} from '@/api/generated/wishlist/wishlist'
import { AXIOS_INSTANCE } from '@/api/http'
import { getErrorMessage } from '@/lib/errorMessages'
import { handlers, resetMockWishlist } from '@/mocks/handlers'
import { mockWishlist } from '@/mocks/handlerUtils'
import { resetMockSession, setMockRole } from '@/mocks/mockSession'

/*
 * 공개 화면·찜 추가/변경/삭제 목업 핸들러 테스트(Task 031 6-2). 생성 함수로 호출해, 핸들러 응답이 계약 모양
 * (posterPath, 찜 추가 응답 { wishlistItemId }, 200/201 구분, 20개 상한 409)과 같은지 확인한다.
 */

const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
beforeEach(() => setMockRole('USER'))
afterEach(() => {
  server.resetHandlers()
  resetMockSession()
  resetMockWishlist()
})
afterAll(() => server.close())

const MOVIE: AddWishlistItemRequest = {
  mediaType: 'movie',
  tmdbId: 496243,
  seasonNumber: null,
  priority: 'WANT',
}

// 활성 찜(= "다 봤음"이 아닌 찜) 20개를 목업 저장소에 채운다
function fillActiveWishlist() {
  for (let i = 1; i <= 20; i++) {
    mockWishlist.set(1000 + i, { ...MOVIE, tmdbId: 7000 + i, watchedAt: null })
  }
}

// 상태 코드(200/201)까지 보려고 생성 함수 대신 axios 인스턴스로 같은 요청을 보낸다
async function postWishlist(data: AddWishlistItemRequest, headers?: Record<string, string>) {
  return AXIOS_INSTANCE.post('/me/wishlist-items', data, { headers })
}

describe('검색·상세 (계약 모양)', () => {
  it('검색 결과는 posterPath를 쓰고 posterUrl은 없다', async () => {
    const res = await searchTitles({ q: '기생충' })
    const item = res.data.content[0]
    expect(item).toHaveProperty('posterPath')
    expect(item).not.toHaveProperty('posterUrl')
    expect(res.data).toMatchObject({ page: 0, totalElements: 1 })
  })

  it('X-Mock-Page-Size 헤더가 한 페이지 크기를 줄인다', async () => {
    const res = await AXIOS_INSTANCE.get('/public/titles', {
      params: { q: '*', page: 0, size: 10 },
      headers: { 'X-Mock-Page-Size': '3' },
    })
    expect(res.data.data.content).toHaveLength(3)
    expect(res.data.data.size).toBe(3)
  })

  it('상세는 posterPath를 쓰고, 없는 작품은 404 TITLE_NOT_FOUND다', async () => {
    const detail = await getTitleDetail('movie', 496243)
    expect(detail.data).toHaveProperty('posterPath')
    await expect(getTitleDetail('movie', 1)).rejects.toMatchObject({
      status: 404,
      errorCode: 'TITLE_NOT_FOUND',
    })
  })
})

describe('찜 추가', () => {
  it('새로 추가하면 201과 { wishlistItemId }, 같은 작품을 다시 추가하면 200과 같은 ID다', async () => {
    const first = await postWishlist(MOVIE)
    expect(first.status).toBe(201)
    expect(first.data).toEqual({ success: true, data: { wishlistItemId: expect.any(Number) } })

    const again = await postWishlist(MOVIE)
    expect(again.status).toBe(200)
    expect(again.data.data.wishlistItemId).toBe(first.data.data.wishlistItemId)
  })

  it('비로그인이면 401이다', async () => {
    setMockRole('GUEST')
    await expect(addWishlistItem(MOVIE)).rejects.toMatchObject({ status: 401 })
  })

  it('드라마는 시즌 번호가 필요하다(400), 없는 시즌은 404다', async () => {
    await expect(
      addWishlistItem({ ...MOVIE, mediaType: 'tv', tmdbId: 93405 }),
    ).rejects.toMatchObject({ status: 400, errorCode: 'VALIDATION_FAILED' })
    await expect(
      addWishlistItem({ ...MOVIE, mediaType: 'tv', tmdbId: 93405, seasonNumber: 99 }),
    ).rejects.toMatchObject({ status: 404, errorCode: 'TITLE_NOT_FOUND' })
  })

  it('활성 찜이 20개면 새 항목은 409 WISHLIST_LIMIT_EXCEEDED, 중복은 200이다', async () => {
    fillActiveWishlist()
    // 20개 중 하나가 MOVIE와 같은 작품이면(중복) 상한과 상관없이 200이다
    mockWishlist.set(1001, { ...MOVIE, watchedAt: null })

    const duplicate = await postWishlist(MOVIE)
    expect(duplicate.status).toBe(200)

    const error = await addWishlistItem({ ...MOVIE, tmdbId: 838209 }).catch((e: unknown) => e)
    expect(error).toMatchObject({ status: 409, errorCode: 'WISHLIST_LIMIT_EXCEEDED' })
    // 화면 메시지는 errorMessages.ts의 getErrorMessage 결과와 같다
    expect((error as { userMessage: string }).userMessage).toBe(
      getErrorMessage('WISHLIST_LIMIT_EXCEEDED'),
    )
  })

  it('"다 봤음" 찜은 20개 상한에 세지 않는다', async () => {
    for (let i = 1; i <= 20; i++) {
      mockWishlist.set(1000 + i, { ...MOVIE, tmdbId: 7000 + i, watchedAt: '2026-10-01T00:00:00Z' })
    }
    const res = await postWishlist({ ...MOVIE, tmdbId: 838209 })
    expect(res.status).toBe(201)
  })

  it('X-Mock-Scenario: wishlist-full이면 새 항목이 409다', async () => {
    await expect(postWishlist(MOVIE, { 'X-Mock-Scenario': 'wishlist-full' })).rejects.toMatchObject(
      { status: 409, errorCode: 'WISHLIST_LIMIT_EXCEEDED' },
    )
  })
})

describe('찜 변경·삭제', () => {
  it('우선순위와 "다 봤음"을 바꾸고 응답은 { wishlistItemId, priority, watchedAt }이다', async () => {
    const added = await addWishlistItem(MOVIE)
    const id = added.data.wishlistItemId

    const changed = await updateWishlistItem(id, { priority: 'MUST' })
    expect(changed.data).toEqual({ wishlistItemId: id, priority: 'MUST', watchedAt: null })

    const watched = await updateWishlistItem(id, { isWatched: true })
    expect(watched.data.watchedAt).not.toBeNull()
    // 이미 "다 봤음"인 항목에 true를 다시 보내도 처음 시각을 유지한다
    const again = await updateWishlistItem(id, { isWatched: true })
    expect(again.data.watchedAt).toBe(watched.data.watchedAt)
  })

  it('없는 항목은 404 WISHLIST_ITEM_NOT_FOUND다', async () => {
    await expect(updateWishlistItem(999, { priority: 'MUST' })).rejects.toMatchObject({
      status: 404,
      errorCode: 'WISHLIST_ITEM_NOT_FOUND',
    })
  })

  it('활성 찜이 20개일 때 "다 봤음"을 되돌리면 409다', async () => {
    fillActiveWishlist()
    mockWishlist.set(2000, { ...MOVIE, tmdbId: 8000, watchedAt: '2026-10-01T00:00:00Z' })

    await expect(updateWishlistItem(2000, { isWatched: false })).rejects.toMatchObject({
      status: 409,
      errorCode: 'WISHLIST_LIMIT_EXCEEDED',
    })
  })

  it('삭제는 항목이 없어도 204다', async () => {
    const added = await addWishlistItem(MOVIE)
    await removeWishlistItem(added.data.wishlistItemId)
    expect(mockWishlist.size).toBe(0)
    // 이미 없는 항목을 다시 지워도 오류 없이 끝난다
    await removeWishlistItem(added.data.wishlistItemId)
  })
})
