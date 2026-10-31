import { useLocation } from 'react-router'
import { ConsentBanner } from '@/features/consent/components/ConsentBanner'
import { useConsent } from '@/features/consent/consentStore'
import { getMeasurementId, toTrackablePath } from '@/lib/analytics'

/**
 * 동의 안내 띠를 "보일 때만" 그려 주는 연결 컴포넌트다. RootLayout의 바깥 상자 맨 마지막(푸터 뒤)에 둔다
 * (ConsentBanner가 sticky bottom-0라 그 자리여야 푸터를 가리지 않는다).
 *
 * 보이는 조건(모두 만족):
 * 1. 측정 ID가 있다 - 목업·로컬처럼 방문 통계를 쓰지 않는 환경에서는 물을 이유가 없다
 * 2. 현재 경로가 집계 제외 경로가 아니다 - 관리자·로그인 확인 화면은 어차피 보내지 않으므로 묻지 않는다
 * 3. 상태가 'unset'이다 - 아직 고르지 않았거나, 거부한 지 6개월이 지났다
 * 경로를 useLocation으로 읽는 이유: 제외 경로(/admin)에서 일반 경로로 이동하면 그때부터 띠가 나타나야 하기 때문이다.
 */
export function ConsentBannerHost() {
  const { pathname } = useLocation()
  const { status, accept, reject } = useConsent()

  if (getMeasurementId() === null) return null
  if (toTrackablePath(pathname) === null) return null
  if (status !== 'unset') return null
  return <ConsentBanner onAccept={accept} onReject={reject} />
}
