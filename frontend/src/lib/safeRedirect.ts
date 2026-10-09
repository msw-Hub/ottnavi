/*
 * 로그인 뒤 돌아갈 경로(from)를 안전하게 거르는 함수다.
 *
 * 왜 필요한가: 돌아갈 경로는 router state나 주소(쿼리)로 받는데, 이 값은 사용자가 바꿀 수 있다.
 * 검사 없이 그대로 이동시키면 `https://악성사이트` 같은 외부 주소로 보내는 "오픈 리다이렉트" 공격에 쓰인다.
 * 그래서 이 사이트 안의 경로만 허용한다(TECH 4절, RequireAuth 주석과 같은 원칙).
 */

// 로그인 화면 자신이나 콜백으로 돌아가면 같은 곳을 맴돌게 되므로 돌아갈 곳으로 쓰지 않는 경로
const LOGIN_FLOW_PATHS = ['/login', '/auth/callback']

/**
 * value가 이 사이트 안의 경로면 그대로, 아니면 fallback을 돌려준다.
 *
 * 허용 조건: 문자열이고, "/"로 시작하며, "//"(프로토콜 상대 주소)나 "/\"(브라우저가 "//"처럼 읽음)로 시작하지 않고,
 * 줄바꿈 같은 제어 문자가 없으며, 로그인 흐름 경로(/login, /auth/callback)가 아니다.
 */
export function getSafeRedirectPath(value: unknown, fallback = '/'): string {
  if (typeof value !== 'string') return fallback
  if (!value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) return fallback
  // eslint-disable-next-line no-control-regex -- 제어 문자(줄바꿈 등)가 든 주소를 걸러내려는 의도적인 검사다
  if (/[\u0000-\u001f\u007f]/.test(value)) return fallback
  const pathname = value.split(/[?#]/)[0]
  if (LOGIN_FLOW_PATHS.includes(pathname)) return fallback
  return value
}
