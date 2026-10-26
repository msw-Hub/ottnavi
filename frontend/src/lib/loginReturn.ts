import { getSafeRedirectPath } from '@/lib/safeRedirect'

/*
 * 로그인 뒤 돌아갈 화면 경로(returnTo)를 잠깐 보관하는 도우미다.
 *
 * 왜 필요한가: 실제 로그인은 브라우저가 Google 로그인 화면을 거쳐 돌아오는 "페이지 전체 이동"이다.
 * 이때 앱이 새로 시작되어 router state(메모리)에 담아 둔 돌아갈 곳이 사라진다(TECH 4절). 그래서 이동 직전에 경로만
 * sessionStorage에 적어 두고, 콜백 화면이 꺼내 쓴다.
 *
 * 저장해도 안전한 이유: 담는 값은 "/wishlist" 같은 이 사이트 안의 화면 경로뿐이고 토큰·개인 정보가 아니다.
 * 읽을 때 getSafeRedirectPath로 다시 검사하므로, 누가 값을 바꿔 넣어도 외부 주소로는 가지 못한다(오픈 리다이렉트 방지).
 * Access Token은 어떤 저장소에도 두지 않는다(stores/authStore 주석).
 */

// 저장 키. 다른 값과 섞이지 않게 서비스 이름을 앞에 붙인다
const RETURN_TO_KEY = 'ottnavi:login-return-to'

/** 돌아갈 경로를 보관한다. 안전하지 않은 값이면 보관하지 않는다(이미 있던 값은 지운다). */
export function saveLoginReturnTo(path: string): void {
  try {
    const safePath = getSafeRedirectPath(path, '')
    if (safePath) window.sessionStorage.setItem(RETURN_TO_KEY, safePath)
    else window.sessionStorage.removeItem(RETURN_TO_KEY)
  } catch {
    // 저장소를 쓸 수 없는 환경이면 돌아갈 곳만 포기하고 첫 화면으로 간다
  }
}

/** 보관한 돌아갈 경로를 읽는다(지우지 않는다). 없거나 안전하지 않으면 fallback이다. */
export function readLoginReturnTo(fallback = '/'): string {
  try {
    return getSafeRedirectPath(window.sessionStorage.getItem(RETURN_TO_KEY), fallback)
  } catch {
    return fallback
  }
}

/** 보관한 돌아갈 경로를 지운다. 이동을 마친 뒤 부른다(개발 모드의 효과 이중 실행에서 값을 잃지 않으려고 읽기와 나눴다). */
export function clearLoginReturnTo(): void {
  try {
    window.sessionStorage.removeItem(RETURN_TO_KEY)
  } catch {
    // 지울 수 없어도 다음 로그인에서 덮어쓰므로 무시한다
  }
}
