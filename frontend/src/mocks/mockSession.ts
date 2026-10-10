/*
 * 목업 모드의 "로그인 상태"를 담는 임시 저장소다(Task 027 결정 ①).
 *
 * 왜 따로 두나: 실제 인증(Access Token, stores/authStore)은 Task 057에서 만든다. 그 전에도 목업 핸들러는
 * "비로그인이면 찜에 401, 회원이면 성공"처럼 로그인 상태에 따라 다르게 답해야 한다.
 * 그래서 실제 인증 코드와 완전히 분리한 모듈 변수로 역할만 기억한다. 토큰·쿠키·localStorage·sessionStorage는 쓰지 않는다.
 *
 * 값이 바뀌면 화면이 다시 그려져야 해서(머리글 표시, 전환 토글) 구독 방식(subscribe)을 함께 둔다.
 * React에서는 hooks/useMockRole이 useSyncExternalStore로 이 구독을 읽는다.
 * 새로고침해도 역할이 풀리지 않게 sessionStorage에 역할·온보딩 여부만 적어 둔다(Task 028 보정). 모듈 변수만 쓰면 새로고침마다
 * 비로그인으로 돌아가 회원 화면(/wishlist 등)을 확인할 수 없고 로그인 필요 요청이 401로 끝난다.
 * 이 값은 토큰이 아니라 화면 확인용 역할 이름('USER' 등)뿐이다(실제 인증 정보가 아니다). sessionStorage라 탭을 닫으면 사라진다.
 * 저장소를 쓸 수 없는 환경이면 try/catch로 건너뛰고 예전처럼 모듈 변수만 쓴다.
 * 테스트는 setMockRole('USER')로 회원 상태의 응답을 확인하고, 끝나면 resetMockSession()으로 되돌린다.
 */

// 목업에서 구분하는 역할. GUEST=비로그인, USER=회원, ADMIN=관리자
export type MockRole = 'GUEST' | 'USER' | 'ADMIN'

// sessionStorage 키. 목업 전용 값이라 서비스 이름을 앞에 붙여 다른 값과 섞이지 않게 한다
const ROLE_STORAGE_KEY = 'ottnavi:mock-role'
const ONBOARDING_STORAGE_KEY = 'ottnavi:mock-onboarding-done'

// 저장된 값을 읽는다. 저장소를 못 쓰거나 값이 없으면 null이다(찜·설정 같은 다른 목업 저장소도 같은 방식으로 쓰려고 내보낸다)
export function readStored(key: string): string | null {
  try {
    return window.sessionStorage.getItem(key)
  } catch {
    return null
  }
}

// 값을 저장한다(null이면 지운다). 저장소를 못 쓰는 환경이면(접근 차단·용량 초과) 조용히 건너뛴다
export function writeStored(key: string, value: string | null): void {
  try {
    if (value === null) window.sessionStorage.removeItem(key)
    else window.sessionStorage.setItem(key, value)
  } catch {
    // 저장소를 쓸 수 없으면 새로고침 유지만 포기하고 모듈 변수로 계속 동작한다
  }
}

// 저장된 역할을 되살린다. 알 수 없는 값이면 비로그인이다(손으로 바뀐 값이 화면을 깨지 않게 한다)
function readStoredRole(): MockRole {
  const stored = readStored(ROLE_STORAGE_KEY)
  return stored === 'USER' || stored === 'ADMIN' ? stored : 'GUEST'
}

let currentRole: MockRole = readStoredRole()

// 온보딩(SCR-07)을 마쳤는지. 로그인 콜백이 "온보딩 미완료면 SCR-07, 아니면 원래 화면"을 고를 때 본다(PRD SCR-06).
// 기본은 미완료라 처음 로그인하면 SCR-07 경로로 가는 흐름이 보인다. 온보딩 화면(Task 028)이 저장하면 true로 바꾼다
let currentOnboardingDone = readStored(ONBOARDING_STORAGE_KEY) === 'true'

// 값이 바뀌었을 때 불러 줄 구독자 목록
const listeners = new Set<() => void>()

function notifyListeners(): void {
  listeners.forEach((listener) => listener())
}

/**
 * 값이 바뀔 때마다 listener를 부른다. 구독을 끊는 함수를 돌려준다.
 * useSyncExternalStore가 요구하는 모양(subscribe)이다.
 */
export function subscribeMockSession(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

// 지금 목업의 로그인 역할을 돌려준다
export function getMockRole(): MockRole {
  return currentRole
}

// 목업의 로그인 역할만 바꾼다(콜백 화면의 "로그인 성공", 머리글의 "로그아웃", 테스트에서 쓴다)
export function setMockRole(role: MockRole): void {
  if (currentRole === role) return
  currentRole = role
  writeStored(ROLE_STORAGE_KEY, role === 'GUEST' ? null : role)
  notifyListeners()
}

/**
 * 개발용 전환 토글이 쓰는 역할 변경이다. setMockRole과 달리 "이미 가입해 온보딩을 마친 회원"으로 본다.
 *
 * 왜 따로 두나: 토글로 회원/관리자를 고르는 목적은 가입이 끝난 사람의 화면을 보는 것이다. 이때 온보딩 미완료로 두면
 * 로그아웃 → 다시 로그인할 때마다 SCR-07로 가서 "원래 화면으로 돌아가기"를 확인하기 어렵다.
 * 비로그인(GUEST)으로 바꿀 때는 온보딩 여부를 건드리지 않는다.
 */
export function switchMockRole(role: MockRole): void {
  if (role !== 'GUEST') setMockOnboardingDone(true)
  setMockRole(role)
}

// 온보딩을 마쳤는지 돌려준다
export function isMockOnboardingDone(): boolean {
  return currentOnboardingDone
}

// 온보딩 완료 여부를 바꾼다(온보딩 화면 Task 028, 테스트에서 쓴다)
export function setMockOnboardingDone(isDone: boolean): void {
  currentOnboardingDone = isDone
  writeStored(ONBOARDING_STORAGE_KEY, isDone ? 'true' : null)
}

// 목업 로그인 상태를 처음(비로그인, 온보딩 미완료)으로 되돌린다. 테스트가 서로 영향을 주지 않게 쓴다
export function resetMockSession(): void {
  setMockOnboardingDone(false)
  setMockRole('GUEST')
}
