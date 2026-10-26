import { create } from 'zustand'
import type { AccessTokenRole } from '@/api/generated/model'

/*
 * 인증 상태 저장소(Zustand). Access Token(AT)과 역할(role)을 "메모리"에만 둔다.
 *
 * [보안] 왜 localStorage·sessionStorage·쿠키에 두지 않나:
 * 이 저장소들은 같은 출처에서 실행되는 어떤 스크립트든 읽을 수 있다. 화면에 XSS(악성 스크립트 삽입)가 한 번이라도 생기면
 * 저장된 토큰이 그대로 탈취되고, 탈취된 AT는 만료(30분)까지 쓸 수 있다. 메모리(이 모듈 변수)에 두면 새로고침 때 사라지지만,
 * 그 대신 HttpOnly 쿠키의 Refresh Token으로 POST /api/auth/refresh를 불러 다시 받는다(TECH 4절, frontend.md 인증 규칙).
 * HttpOnly 쿠키는 자바스크립트가 읽을 수 없으므로 프론트는 Refresh Token을 다루지 않는다.
 * AT를 주소(URL)로 주고받는 것도 금지다(주소 기록·Referer·서버 로그에 남는다).
 *
 * 이 저장소에는 서버 데이터를 복사해 두지 않는다(frontend.md: Zustand는 인증 상태만). 사용자 정보·찜 등은 TanStack Query가 맡는다.
 *
 * 상태(status)의 뜻:
 * - checking: 앱을 막 열어 재발급을 시도하는 중이다. 새로고침 직후에는 AT가 비어 있어 이 상태로 시작한다.
 *             이때 로그인 화면으로 바로 보내면 로그인한 사용자가 튕겨 나가므로 가드는 결과를 기다린다.
 * - authenticated: AT와 role이 있다.
 * - guest: 로그인하지 않았다(재발급이 401이었거나 로그아웃했다).
 * - error: 재발급이 서버 오류·네트워크 오류로 끝나 로그인 여부를 알 수 없다. 로그인 화면으로 보내지 않고 "다시 시도"를 안내한다
 *          (서버 장애 때문에 로그인한 사용자를 로그아웃된 것처럼 대하지 않기 위함이다. TECH 4절: Redis 장애는 401이 아니라 500).
 */
export type AuthStatus = 'checking' | 'authenticated' | 'guest' | 'error'

interface AuthState {
  status: AuthStatus
  accessToken: string | null // 메모리에만 둔다(위 주석). 로그·오류 객체·화면에 그대로 내보내지 않는다
  role: AccessTokenRole | null // USER | ADMIN. 화면 메뉴·가드 표시용이고 실제 권한 검사는 백엔드가 한다
  // 사용자가 "로그아웃"을 눌러 진행 중인지. 보호 화면의 가드가 로그아웃 직후 비로그인 상태를 보고 로그인 화면으로 보내 버리는 것을 막는 표시다
  isSigningOut: boolean
  setSession: (accessToken: string, role: AccessTokenRole) => void
  clearSession: () => void // 로그아웃·재발급 401: 토큰과 역할을 비우고 guest로
  markChecking: () => void // 재발급을 (다시) 시도하기 시작함
  setSigningOut: (isSigningOut: boolean) => void
  markUnavailable: () => void // 앱 시작 재발급이 401이 아닌 이유로 실패함: 토큰은 없는 채로 error로
}

export const useAuthStore = create<AuthState>()((set) => ({
  status: 'checking',
  accessToken: null,
  role: null,
  isSigningOut: false,
  setSession: (accessToken, role) => set({ status: 'authenticated', accessToken, role }),
  clearSession: () => set({ status: 'guest', accessToken: null, role: null }),
  markChecking: () => set({ status: 'checking' }),
  markUnavailable: () => set({ status: 'error', accessToken: null, role: null }),
  setSigningOut: (isSigningOut) => set({ isSigningOut }),
}))
