import { ConsentSettingsPanel } from '@/features/consent/components/ConsentSettingsPanel'
import { useConsent } from '@/features/consent/consentStore'

// 푸터의 "방문 통계 설정" 링크가 가리키는 위치(처리방침 화면 안). 링크(app)와 카드(이 파일)가 같은 값을 쓰도록 한 곳에 둔다
export const CONSENT_SETTINGS_ANCHOR_ID = 'visit-stats-settings'

/**
 * 방문 통계 설정 카드(현재 상태 + 동의/거부)에 실제 동의 상태를 연결한 것이다. 처리방침 화면의 4절에 놓인다.
 * 측정 ID가 없는 환경(목업·로컬)에서는 부르는 쪽이 이 카드를 아예 그리지 않는다.
 * scroll-mt: 푸터 링크의 #앵커로 이동했을 때 카드가 화면 맨 위에 딱 붙지 않고 약간 여유를 두게 한다.
 */
export function ConsentSettingsCard() {
  const { status, accept, reject } = useConsent()
  return (
    <div id={CONSENT_SETTINGS_ANCHOR_ID} className="scroll-mt-20">
      <ConsentSettingsPanel status={status} onAccept={accept} onReject={reject} />
    </div>
  )
}
