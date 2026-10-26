import { PageTitle } from '@/components/common/PageTitle'
import { ExclusivesScreen } from '@/features/exclusives/components/ExclusivesScreen'

/**
 * SCR-03 서비스별 독점 목록(`/exclusives`, 공개). 서비스별 독점 목록 API(Task 078)에 연결된 화면이다(Task 079).
 * 화면 조립은 features/exclusives에 있고, 이 파일은 연결만 한다(pages는 얇게, frontend.md).
 *
 * 이전: 독점 목록 API가 없던 동안 운영에서는 준비 중 화면(ComingSoon)을, 목업에서만 정적 샘플을 보였다. API 연결로 그 분기를 없앴다.
 */
export function ExclusivesPage() {
  return (
    <>
      <PageTitle name="독점 작품" />
      <ExclusivesScreen />
    </>
  )
}
