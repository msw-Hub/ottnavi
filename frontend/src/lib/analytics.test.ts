import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { initAnalytics, toTrackablePath, trackPageView } from '@/lib/analytics'

/*
 * 방문 통계(GA4) 모듈 테스트.
 * 확인하는 것: 측정 ID가 비면 스크립트·전송이 전혀 없다 / ID가 있으면 스크립트는 한 번만 들어간다 /
 *   page_view 경로에 쿼리·해시가 없다 / /admin은 보내지 않는다 / user_id가 설정되지 않는다.
 * 막으려는 사고: 로컬·목업에서 외부 요청이 나가는 것, 검색어·OAuth 값이 통계로 새는 것, 운영자 접속이 통계를 왜곡하는 것, 사용자 식별값 연결.
 * 실제 외부 로드는 하지 않는다: jsdom은 <script src>를 기본으로 불러오지 않는다(resources 옵션 기본값).
 */

// dataLayer에 쌓인 gtag 호출을 [command, ...args] 배열 목록으로 꺼낸다(gtag는 arguments 객체를 넣는다)
function readCalls(): unknown[][] {
  return (window.dataLayer ?? []).map((entry) => Array.from(entry as ArrayLike<unknown>))
}

function resetGlobals() {
  document.getElementById('ga4-gtag-script')?.remove()
  delete window.dataLayer
  delete window.gtag
}

describe('방문 통계(GA4)', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_USE_MOCK', 'false')
    resetGlobals()
  })
  afterEach(() => {
    vi.unstubAllEnvs()
    resetGlobals()
  })

  it('측정 ID가 비어 있으면 스크립트를 넣지도 전송하지도 않는다', () => {
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', '')

    expect(initAnalytics()).toBe(false)
    trackPageView('/search')

    expect(document.getElementById('ga4-gtag-script')).toBeNull()
    expect(window.dataLayer).toBeUndefined()
    expect(window.gtag).toBeUndefined()
  })

  it('목업 모드에서는 측정 ID가 있어도 아무것도 하지 않는다', () => {
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123456')
    vi.stubEnv('VITE_USE_MOCK', 'true')

    trackPageView('/')

    expect(document.getElementById('ga4-gtag-script')).toBeNull()
  })

  it('측정 ID가 있으면 스크립트는 한 번만 들어가고 광고 기능은 꺼지며 user_id는 없다', () => {
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123456')

    trackPageView('/')
    trackPageView('/ranking')

    // 1. 스크립트 태그가 하나, 주소에 측정 ID
    const scripts = document.querySelectorAll('script[src*="googletagmanager.com/gtag/js"]')
    expect(scripts).toHaveLength(1)
    expect(scripts[0].getAttribute('src')).toBe(
      'https://www.googletagmanager.com/gtag/js?id=G-TEST123456',
    )

    // 2. config는 한 번, 자동 page_view 끔·광고 기능 끔
    const configs = readCalls().filter((call) => call[0] === 'config')
    expect(configs).toHaveLength(1)
    expect(configs[0][2]).toMatchObject({
      send_page_view: false,
      allow_google_signals: false,
      allow_ad_personalization_signals: false,
    })

    // 3. 어떤 호출에도 user_id가 없다
    expect(JSON.stringify(readCalls())).not.toContain('user_id')
  })

  it('page_view 경로에는 쿼리와 해시가 없다', () => {
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123456')

    trackPageView('/search?q=비밀검색어#top')

    const pageViews = readCalls().filter((call) => call[0] === 'event')
    expect(pageViews).toHaveLength(1)
    const params = pageViews[0][2] as Record<string, string>
    expect(params.page_path).toBe('/search')
    expect(params.page_location).toBe(`${window.location.origin}/search`)
    expect(JSON.stringify(readCalls())).not.toContain('비밀검색어')
  })

  it('관리자·로그인 콜백 경로는 보내지 않는다', () => {
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123456')

    trackPageView('/admin/collect')
    trackPageView('/auth/callback')

    expect(readCalls().filter((call) => call[0] === 'event')).toHaveLength(0)
    // 비슷한 이름의 경로는 제외하지 않는다
    expect(toTrackablePath('/administrator')).toBe('/administrator')
  })
})
