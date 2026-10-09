import { AuthCallbackScreen } from '@/features/auth/components/AuthCallbackScreen'

/**
 * SCR-06 로그인 콜백 화면(`/auth/callback`, 공개). 처리 흐름과 보안 주의는 features/auth의 AuthCallbackScreen 주석에 있다.
 *
 * 이 화면을 RequireAuth 밖(공개)에 두는 이유: 이 시점에는 Access Token이 아직 메모리에 없어서,
 * 가드 안에 두면 토큰을 받기도 전에 로그인 화면으로 되돌려 보내는 순환이 생긴다.
 */
export function AuthCallbackPage() {
  return <AuthCallbackScreen />
}
