import { LoginScreen } from '@/features/auth/components/LoginScreen'

/** SCR-06 로그인 화면(`/login`, 공개). 내용은 features/auth가 맡고, 이 파일은 라우트에 연결만 한다. */
export function LoginPage() {
  return <LoginScreen />
}
