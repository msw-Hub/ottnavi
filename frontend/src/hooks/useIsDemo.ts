import { useSession } from '@/hooks/useSession'

/**
 * 지금 로그인한 계정이 데모 계정(role DEMO)인지 돌려준다(Task 109).
 *
 * 데모 계정은 여러 방문자가 함께 쓰는 공용 계정이다. 화면은 이 값으로 안내 띠를 보이고, 관리자 화면의 쓰기 버튼·탈퇴·알림 설정 변경을 막는다.
 * 여러 기능(app, admin, auth, notification)이 함께 쓰므로 기능 폴더가 아니라 공용 hooks/에 둔다(기능 폴더끼리 import 금지).
 * 표시를 바꾸는 편의 기능일 뿐이고, 실제로 쓰기를 막는 것은 서버다(데모 계정의 관리자 쓰기·탈퇴·알림 설정 변경은 403).
 */
export function useIsDemo(): boolean {
  return useSession().role === 'DEMO'
}
