import { useEffect } from 'react'
import { useLocation } from 'react-router'
import { trackPageView } from '@/lib/analytics'

/**
 * 화면 주소가 바뀔 때마다 방문 통계(GA4)에 page_view를 보낸다. RootLayout에서 한 번만 쓴다.
 *
 * 왜 필요한가: 이 앱은 SPA라 링크를 눌러도 브라우저가 페이지를 새로 불러오지 않는다. 그래서 GA가 처음 한 번만 세고
 * 이후 이동은 놓친다. 라우터의 위치 변경을 보고 직접 보낸다.
 * 의존성 [pathname]: 경로가 바뀔 때만 보낸다. 검색어(?q=)만 바뀌는 이동은 같은 화면으로 보고 다시 보내지 않으며,
 * 쿼리·해시는 개인 입력이 섞일 수 있어 pathname만 읽는다(lib/analytics.ts).
 * 측정 ID가 비어 있으면 trackPageView가 아무 일도 하지 않는다.
 */
export function usePageViewTracking() {
  const { pathname } = useLocation()

  useEffect(() => {
    trackPageView(pathname)
  }, [pathname])
}
