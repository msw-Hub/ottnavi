import { useLocation } from 'react-router'
import { ConsentBanner } from '@/features/consent/components/ConsentBanner'
import { useConsent } from '@/features/consent/consentStore'
import { getMeasurementId, toTrackablePath } from '@/lib/analytics'

// 본문 영역의 id. RootLayout의 <main id="main" tabIndex={-1}>과 같은 값이다(건너뛰기 링크가 쓰는 그 영역)
const MAIN_LANDMARK_ID = 'main'

/**
 * 동의 안내 띠를 "보일 때만" 그려 주는 연결 컴포넌트다. RootLayout의 바깥 상자 맨 마지막(푸터 뒤)에 둔다
 * (ConsentBanner가 sticky bottom-0라 그 자리여야 푸터를 가리지 않는다).
 *
 * 보이는 조건(모두 만족):
 * 1. 측정 ID가 있다 - 목업·로컬처럼 방문 통계를 쓰지 않는 환경에서는 물을 이유가 없다
 * 2. 현재 경로가 집계 제외 경로가 아니다 - 관리자·로그인 확인 화면은 어차피 보내지 않으므로 묻지 않는다
 * 3. 상태가 'unset'이다 - 아직 고르지 않았거나, 거부·철회한 지 6개월이 지났다
 * 경로를 useLocation으로 읽는 이유: 제외 경로(/admin)에서 일반 경로로 이동하면 그때부터 띠가 나타나야 하기 때문이다.
 */
export function ConsentBannerHost() {
  const { pathname } = useLocation()
  const { status, accept, reject } = useConsent()

  // 선택 직후 포커스를 본문(main)으로 옮긴다.
  // 왜: [동의]/[거부] 버튼에 있던 키보드 포커스가 띠가 사라지면서 문서 맨 앞(body)으로 떨어지면,
  //     키보드 사용자는 다시 처음부터 Tab을 눌러야 하고 낭독기 사용자는 위치를 잃는다(WCAG 2.4.3 포커스 순서).
  // 왜 main인가: 이 앱이 이미 "본문으로 건너뛰기" 링크의 도착지로 쓰는 안정적인 자리이고(tabIndex=-1이라 코드로 포커스 가능,
  //     Tab 순서에는 안 들어감), 띠 바로 앞의 푸터로 돌려보내는 것보다 읽던 흐름을 이어 가기 쉽다.
  // preventScroll: 포커스만 옮기고 화면이 맨 위로 튀지 않게 한다.
  function moveFocusToMain() {
    document.getElementById(MAIN_LANDMARK_ID)?.focus({ preventScroll: true })
  }
  function handleAccept() {
    accept()
    moveFocusToMain()
  }
  function handleReject() {
    reject()
    moveFocusToMain()
  }

  if (getMeasurementId() === null) return null
  if (toTrackablePath(pathname) === null) return null
  if (status !== 'unset') return null
  return <ConsentBanner onAccept={handleAccept} onReject={handleReject} />
}
