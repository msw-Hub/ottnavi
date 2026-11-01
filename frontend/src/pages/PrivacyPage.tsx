import { PageTitle } from '@/components/common/PageTitle'
import { ConsentSettingsCard } from '@/features/consent/components/ConsentSettingsCard'
import { PrivacyPolicyScreen } from '@/features/legal/components/PrivacyPolicyScreen'
import { getMeasurementId } from '@/lib/analytics'

/**
 * 개인정보처리방침 화면(`/privacy`, 공개). 내용은 features/legal이 맡고, 이 파일은 라우트 연결과 조립만 한다.
 * 방문 통계 설정 카드는 features/consent 소속이라 legal이 직접 import할 수 없어(기능 폴더끼리 import 금지) 여기서 끼워 넘긴다.
 * 측정 ID가 없는 환경(목업·로컬)에서는 카드를 넘기지 않아 방침 문구만 보인다.
 */
export function PrivacyPage() {
  return (
    <>
      <PageTitle name="개인정보처리방침" />
      <PrivacyPolicyScreen
        analyticsSettingsSlot={getMeasurementId() !== null ? <ConsentSettingsCard /> : undefined}
      />
    </>
  )
}
