import { useEffect } from 'react'
import { useLocation } from 'react-router'
import { setAnalyticsConsent, trackPageView } from '@/lib/analytics'

/**
 * 화면 주소가 바뀔 때마다 방문 통계(GA4)에 page_view를 보낸다. RootLayout에서 한 번만 쓴다.
 *
 * 왜 필요한가: 이 앱은 SPA라 링크를 눌러도 브라우저가 페이지를 새로 불러오지 않는다. 그래서 GA가 처음 한 번만 세고
 * 이후 이동은 놓친다. 라우터의 위치 변경을 보고 직접 보낸다.
 * 의존성 [pathname, isConsentGranted]: 경로가 바뀔 때만 보낸다. 검색어(?q=)만 바뀌는 이동은 같은 화면으로 보고 다시 보내지 않으며,
 * 쿼리·해시는 개인 입력이 섞일 수 있어 pathname만 읽는다(lib/analytics.ts).
 * 동의 여부도 의존성에 넣은 이유: 방문자가 [동의]를 누른 순간 현재 화면의 page_view를 한 번 보내고(스크립트도 이때 처음 불러온다),
 * 철회([거부])하면 같은 효과 안에서 전송 차단과 쿠키 삭제가 실행되도록 하기 위해서다.
 * 순서가 중요하다: 동의 여부를 먼저 analytics에 알린 뒤에 page_view를 요청해야 동의 직후 첫 전송이 막히지 않는다.
 * 측정 ID가 비어 있거나 동의 전이면 trackPageView가 아무 일도 하지 않는다.
 *
 * @param isConsentGranted 방문 통계 동의가 유효한가(features/consent의 useConsent().analyticsEnabled)
 */
export function usePageViewTracking(isConsentGranted: boolean) {
  const { pathname } = useLocation()

  useEffect(() => {
    setAnalyticsConsent(isConsentGranted)
    trackPageView(pathname)
  }, [pathname, isConsentGranted])
}
