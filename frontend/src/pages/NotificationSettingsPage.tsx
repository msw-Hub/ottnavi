import { PageTitle } from '@/components/common/PageTitle'
import { NotificationScreen } from '@/features/notification/components/NotificationScreen'

/**
 * SCR-12 알림 설정·발송 이력(`/settings/notifications`, 로그인 필요).
 * 화면 조립은 features/notification에 있고, 이 파일은 연결만 한다(pages는 얇게, frontend.md).
 *
 * 데이터는 실제 API(Task 073)로 불러온다. 목업 모드(VITE_USE_MOCK=true)에서만 MSW 핸들러가 대신 답한다(Task 074).
 *
 * 이전(Task 071 전): 2단계 화면이라 "준비 중" 자리표시(ComingSoon)만 보였다. 목업 화면(Task 071)으로 바꿨다.
 */
export function NotificationSettingsPage() {
  return (
    <>
      <PageTitle name="알림 설정" />
      <NotificationScreen />
    </>
  )
}
