import type { AccessTokenRole } from '@/api/generated/model'
import { useMockRole } from '@/hooks/useMockRole'
import { isMockMode } from '@/lib/mockScenario'
import { useAuthStore, type AuthStatus } from '@/stores/authStore'

/** 화면이 보는 로그인 상태다. 목업·실제 인증의 차이를 이 모양 뒤에 감춘다. */
export interface Session {
  status: AuthStatus // checking | authenticated | guest | error (뜻은 stores/authStore.ts)
  role: AccessTokenRole | null // 로그인했을 때만 값이 있다
}

/**
 * 지금 로그인 상태를 돌려준다. 머리글·가드·찜 버튼이 모두 이 훅 하나를 거친다.
 *
 * - 실제 환경: stores/authStore(메모리의 AT·역할)를 읽는다.
 * - 목업 모드(VITE_USE_MOCK=true): 개발용 전환 토글(mocks/mockSession)의 역할을 읽는다. 목업에는 토큰이 없고
 *   MSW 핸들러가 이 역할로 401을 흉내 내기 때문에, 화면도 같은 값을 따라야 서로 어긋나지 않는다.
 *   이때 authStore는 쓰지 않는다(http.ts의 재발급 인터셉터도 토큰이 없어 동작하지 않는다).
 *
 * 두 저장소 훅을 모두 먼저 부르는 이유: 조건부 훅 호출은 React 규칙 위반이다(호출 순서가 항상 같아야 한다).
 */
export function useSession(): Session {
  const mockRole = useMockRole()
  const status = useAuthStore((state) => state.status)
  const role = useAuthStore((state) => state.role)

  if (isMockMode()) {
    return mockRole === 'GUEST'
      ? { status: 'guest', role: null }
      : { status: 'authenticated', role: mockRole }
  }
  return { status, role }
}
