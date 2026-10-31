import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { initAnalytics, setAnalyticsConsent, toTrackablePath, trackPageView } from '@/lib/analytics'

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
  delete (window as unknown as Record<string, unknown>)['ga-disable-G-TEST123456']
  window.history.replaceState(null, '', '/') // 테스트가 만든 쿼리를 지운다
}

// 차단 표식 값을 읽는다
function readDisableFlag(): unknown {
  return (window as unknown as Record<string, unknown>)['ga-disable-G-TEST123456']
}

describe('방문 통계(GA4)', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_USE_MOCK', 'false')
    resetGlobals()
    setAnalyticsConsent(true) // 기존 테스트는 동의한 방문자 기준이다(동의 관문은 맨 아래 묶음에서 따로 확인한다)
  })
  afterEach(() => {
    setAnalyticsConsent(false) // 모듈 안의 동의 값을 기본(동의 전)으로 되돌린다
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

  // 확인: 제외 경로에서는 차단 표식이 켜지고 GA가 초기화되지 않으며, 공개 경로로 돌아오면 표식이 꺼지고 page_view가 나간다.
  // 막으려는 사고: 관리자 화면에서 GA 자동 이벤트가 나가는 것 / 한 번 차단된 뒤 공개 화면 집계가 계속 막히는 것.
  it('제외 경로에서는 차단 표식을 켜고, 공개 경로로 돌아오면 끈다', () => {
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123456')

    trackPageView('/admin')
    trackPageView('/admin/collect')
    expect(readDisableFlag()).toBe(true)
    expect(window.gtag).toBeUndefined()
    expect(document.getElementById('ga4-gtag-script')).toBeNull()

    trackPageView('/search')
    expect(readDisableFlag()).toBe(false)
    expect(readCalls().filter((call) => call[0] === 'event')).toHaveLength(1)
  })

  // 확인: 이동 때 'set'으로 기본 주소를 정리하고, 초기 config에도 정리된 주소가 있으며, 현재 주소에 쿼리가 있어도 어디에도 새지 않는다.
  // 막으려는 사고: GA 자동 이벤트(user_engagement 등)가 document.location 전체(?q=검색어)를 page_location으로 보내는 것.
  it('주소에 쿼리가 있어도 set·config·page_view 어디에도 쿼리가 실리지 않는다', () => {
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123456')
    window.history.pushState(null, '', '/search?q=비밀검색어#top')

    trackPageView('/search')

    const calls = readCalls()
    const config = calls.find((call) => call[0] === 'config')![2] as Record<string, string>
    expect(config.page_location).toBe(`${window.location.origin}/search`)
    expect(config.page_path).toBe('/search')

    const set = calls.find((call) => call[0] === 'set')![1] as Record<string, string>
    expect(set.page_location).toBe(`${window.location.origin}/search`)
    expect(set.page_path).toBe('/search')

    const serialized = JSON.stringify(calls)
    expect(serialized).not.toContain('?')
    expect(serialized).not.toContain('비밀검색어')
  })

  // 확인: 측정 ID가 비어 있으면 차단 표식도 만들지 않는다.
  // 막으려는 사고: 로컬·목업에서 전역 객체가 변경되는 것.
  it('측정 ID가 비어 있으면 차단 표식도 만들지 않는다', () => {
    vi.stubEnv('VITE_GA_MEASUREMENT_ID', '')

    trackPageView('/admin')

    expect(Object.keys(window).some((key) => key.startsWith('ga-disable-'))).toBe(false)
  })

  describe('동의 관문', () => {
    // 확인: 동의 전에는 스크립트·dataLayer·gtag·차단 표식·쿠키가 하나도 생기지 않는다.
    // 막으려는 사고: 동의 없이 GA가 로드되어 쿠키가 만들어지는 것(사용자 결정 위반).
    it('동의 전에는 어떤 호출도 스크립트·전역·쿠키를 만들지 않는다', () => {
      setAnalyticsConsent(false)
      vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123456')

      expect(initAnalytics()).toBe(false)
      trackPageView('/search')
      trackPageView('/admin')

      expect(document.getElementById('ga4-gtag-script')).toBeNull()
      expect(window.dataLayer).toBeUndefined()
      expect(window.gtag).toBeUndefined()
      expect(readDisableFlag()).toBeUndefined()
      expect(document.cookie).not.toContain('_ga')
    })

    // 확인: 동의한 뒤 같은 화면에서 trackPageView를 부르면 스크립트가 로드되고 page_view가 한 번 나간다.
    it('동의하면 스크립트를 불러오고 현재 화면의 page_view를 보낸다', () => {
      setAnalyticsConsent(false)
      vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123456')
      trackPageView('/ranking')
      expect(document.getElementById('ga4-gtag-script')).toBeNull()

      setAnalyticsConsent(true)
      trackPageView('/ranking')

      expect(document.getElementById('ga4-gtag-script')).not.toBeNull()
      expect(readCalls().filter((call) => call[0] === 'event')).toHaveLength(1)
    })

    // 확인: 철회하면 차단 표식이 켜지고 _ga 쿠키가 지워지며, 이후 page_view는 나가지 않는다(표식도 유지).
    // 막으려는 사고: 철회했는데 이미 로드된 gtag.js가 계속 전송하거나 쿠키가 남는 것.
    it('철회하면 차단 표식을 켜고 GA 쿠키를 지우며 이후 전송을 막는다', () => {
      vi.stubEnv('VITE_GA_MEASUREMENT_ID', 'G-TEST123456')
      trackPageView('/')
      document.cookie = '_ga=GA1.1.1; path=/'
      document.cookie = '_ga_TEST123456=GS1; path=/'
      document.cookie = 'other=keep; path=/'
      const eventCountBefore = readCalls().filter((call) => call[0] === 'event').length

      setAnalyticsConsent(false)
      trackPageView('/ranking')

      expect(readDisableFlag()).toBe(true)
      expect(document.cookie).not.toContain('_ga')
      expect(document.cookie).toContain('other=keep') // 다른 쿠키는 건드리지 않는다
      expect(readCalls().filter((call) => call[0] === 'event')).toHaveLength(eventCountBefore)
      document.cookie = 'other=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    })
  })
})
