import { useSyncExternalStore } from 'react'
import { getMockRole, subscribeMockSession, type MockRole } from '@/mocks/mockSession'

/**
 * 목업 로그인 역할(비로그인/회원/관리자)을 읽는 훅이다. 역할이 바뀌면 이 훅을 쓴 화면이 다시 그려진다.
 *
 * useSyncExternalStore를 쓴 이유: 역할은 React 밖의 모듈 변수(mocks/mockSession)에 있다. useState로 복사해 두면
 * 다른 곳(전환 토글, 콜백 화면)이 값을 바꿔도 모르고 낡은 값을 보여 준다. 이 훅은 "구독 → 바뀌면 다시 읽기"를 React가 안전하게 처리해 준다.
 * 목업 단계 전용이다. 실제 인증(Task 057)이 들어오면 stores/authStore를 읽는 훅으로 바뀐다.
 */
export function useMockRole(): MockRole {
  return useSyncExternalStore(subscribeMockSession, getMockRole)
}
