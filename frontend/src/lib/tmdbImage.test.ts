import { afterEach, describe, expect, it, vi } from 'vitest'
import { getTmdbImageUrl, TMDB_IMAGE_BASE_URL } from '@/lib/tmdbImage'

afterEach(() => {
  vi.unstubAllEnvs()
})

describe('getTmdbImageUrl', () => {
  it('상대 경로와 크기로 TMDB 이미지 URL을 만든다', () => {
    expect(getTmdbImageUrl('/abc.jpg', 'w342')).toBe('https://image.tmdb.org/t/p/w342/abc.jpg')
    expect(getTmdbImageUrl('/logo.png', 'original')).toBe(`${TMDB_IMAGE_BASE_URL}original/logo.png`)
  })

  it('앞뒤 공백은 잘라 낸다', () => {
    expect(getTmdbImageUrl('  /abc.jpg ', 'w185')).toBe('https://image.tmdb.org/t/p/w185/abc.jpg')
  })

  // null이면 호출부(PosterCard 등)가 대체 이미지를 그린다
  it.each([null, undefined, '', '   '])('경로가 없으면(%s) null', (path) => {
    expect(getTmdbImageUrl(path, 'w342')).toBeNull()
  })

  // 다른 주소로 요청이 나가지 않도록 TMDB 상대 경로 형식이 아닌 값은 받지 않는다
  it.each(['abc.jpg', 'https://example.com/a.jpg', '//example.com/a.jpg'])(
    '상대 경로 형식이 아니면(%s) null',
    (path) => {
      expect(getTmdbImageUrl(path, 'w342')).toBeNull()
    },
  )

  // 목업 포스터는 외부 요청이 나가지 않게 SVG data URI를 posterPath 자리에 담는다. 목업 모드에서만 통과시킨다
  it('목업 모드에서만 SVG data URI를 그대로 돌려준다', () => {
    const dataUri = 'data:image/svg+xml;utf8,%3Csvg%3E%3C%2Fsvg%3E'
    vi.stubEnv('VITE_USE_MOCK', 'false')
    expect(getTmdbImageUrl(dataUri, 'w342')).toBeNull()
    vi.stubEnv('VITE_USE_MOCK', 'true')
    expect(getTmdbImageUrl(dataUri, 'w342')).toBe(dataUri)
    // 목업 모드여도 다른 종류의 data URI는 받지 않는다
    expect(getTmdbImageUrl('data:text/html,<b>x</b>', 'w342')).toBeNull()
  })
})
