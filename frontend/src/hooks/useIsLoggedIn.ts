import { useSession } from '@/hooks/useSession'

/**
 * 지금 로그인한 상태인지 돌려준다. 로그인이 필요한 동작(찜 등)을 서버에 보내기 전에 미리 확인하는 데 쓴다.
 *
 * 왜 요청 전에 확인하나: 비로그인으로 서버에 보내면 어차피 401이 오고, 브라우저는 401 응답을 개발자 도구 콘솔에 오류로 남긴다.
 * 로그인하지 않은 상태를 이미 아는데 오류가 남는 요청을 굳이 보낼 이유가 없다. 서버 판단(401)은 로그인이 만료된 경우 등의 안전망으로 남긴다.
 *
 * 판단 기준은 useSession 한 곳이다(실제 환경은 authStore, 목업 모드는 목업 역할). 재발급을 확인하는 중(checking)이나
 * 오류(error)일 때는 "로그인함"으로 보지 않는다.
 */
export function useIsLoggedIn(): boolean {
  return useSession().status === 'authenticated'
}
