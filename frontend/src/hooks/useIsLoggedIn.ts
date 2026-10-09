import { useMockRole } from '@/hooks/useMockRole'

/**
 * 지금 로그인한 상태인지 돌려준다. 로그인이 필요한 동작(찜 등)을 서버에 보내기 전에 미리 확인하는 데 쓴다.
 *
 * 왜 요청 전에 확인하나: 비로그인으로 서버에 보내면 어차피 401이 오고, 브라우저는 401 응답을 개발자 도구 콘솔에 오류로 남긴다.
 * 로그인하지 않은 상태를 이미 아는데 오류가 남는 요청을 굳이 보낼 이유가 없다. 서버 판단(401)은 로그인이 만료된 경우 등의 안전망으로 남긴다.
 *
 * 목업 단계의 구현이다: 목업 역할(mocks/mockSession)이 GUEST가 아니면 로그인한 것으로 본다.
 * 목업 모드가 아닌 환경에서는 역할을 바꾸는 곳이 없어(로그인 버튼이 꺼져 있고 콜백도 실패로 처리한다) 항상 비로그인이다.
 * TODO(Task 057): stores/authStore의 인증 상태를 읽도록 바꾼다. 이 훅의 사용처는 그대로 둔다.
 */
export function useIsLoggedIn(): boolean {
  return useMockRole() !== 'GUEST'
}
