import Axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { toast } from 'sonner'
import type { CommonResponseAccessToken, FieldError } from '@/api/generated/model'
import { attachMockHeaders } from '@/api/mockHeaders'
import { getErrorMessage, isKnownErrorCode, NETWORK_ERROR_MESSAGE } from '@/lib/errorMessages'
import { isMockMode } from '@/lib/mockScenario'
import { useAuthStore } from '@/stores/authStore'

// axios 인스턴스와 orval custom mutator.
// - 베이스 URL은 항상 상대 경로 '/api'다. 배포는 Vercel rewrite, 로컬은 Vite dev server proxy가 백엔드로 전달하므로
//   백엔드 주소를 코드나 환경변수에 직접 두지 않는다(같은 출처로 호출해야 HttpOnly 쿠키와 CORS 문제가 생기지 않는다).
// - 인증(Access Token 첨부, 401 재발급 1회, 동시 401은 재발급 요청 하나)은 아래 "인증" 절에, ProblemDetail 파싱(오류 정리)
//   인터셉터는 그 아래에 있다(Task 025·057).
export const AXIOS_INSTANCE = Axios.create({
  baseURL: '/api',
})

// 요청별 표시 필드(axios 요청 설정에 붙여 인터셉터가 읽는다).
declare module 'axios' {
  interface AxiosRequestConfig {
    // 재시도했던 요청인지 표시하는 필드. 한 요청은 재발급 후 "한 번만" 다시 보내고, 그래도 401이면 그대로 실패시킨다(무한 반복 방지).
    _authRetried?: boolean
    // 이 요청의 429(요청 제한)에는 전역 토스트를 띄우지 않는다(Task 109). 화면이 대기 시간을 직접 안내하는 요청(데모 로그인)만 켠다.
    // 토스트를 띄우지 않을 뿐 오류는 그대로 ApiError로 던져진다
    _skipRateLimitToast?: boolean
  }
}

// 목업 모드에서만 화면 검토용 전환 값을 X-Mock-* 헤더로 붙인다(Task 031 6-0). 계약에 없는 값을 쿼리 파라미터로 보내지 않기 위함이다.
// 운영(VITE_USE_MOCK이 true가 아님)에서는 붙일 헤더가 없다. 인증 로직과 무관하며, 아래 토큰 첨부 인터셉터와 따로 동작한다.
AXIOS_INSTANCE.interceptors.request.use(attachMockHeaders)

// orval이 생성한 함수가 호출하는 mutator. 응답 본문(body)을 그대로 돌려준다.
// 성공 응답의 CommonResponse 래퍼({ success, data })는 여기서 벗기지 않는다.
// 생성된 타입이 래퍼를 포함하므로 벗기면 타입과 실제 값이 어긋난다. 꺼내는 일은 features/{기능}/hooks에서
// select: (res) => res.data 로 한다(frontend.md).
// options 인자는 훅에서 요청별 추가 옵션(예: signal)을 넘길 때 쓴다.
export const customInstance = <T>(
  config: AxiosRequestConfig,
  options?: AxiosRequestConfig,
): Promise<T> => {
  return AXIOS_INSTANCE({ ...config, ...options }).then(({ data }) => data)
}

/*
 * ===== 오류 정리 (Task 025) =====
 *
 * 왜 오류를 한 번 정리하나:
 * axios가 그대로 던지는 AxiosError는 "서버가 응답한 오류", "응답 자체가 없는 네트워크 오류", "취소된 요청"을
 * 같은 모양으로 담고, 정작 화면에 필요한 errorCode는 error.response?.data?.errorCode 깊숙이 있다.
 * 화면마다 이걸 꺼내면 빠뜨리거나 다르게 처리하기 쉬워, 모든 요청이 지나가는 응답 인터셉터 한 곳에서
 * ApiError 하나로 바꿔 던진다. 화면은 error.userMessage(보여 줄 문장)와 error.errorCode(분기용)만 보면 된다.
 */

// 오류 종류. 화면이 "다시 시도" 버튼을 보일지, 입력 오류를 필드 옆에 보일지 등을 고를 때 쓴다.
// - problem: 서버가 ProblemDetail(application/problem+json)로 답한 오류. errorCode가 있다
// - http: 서버(또는 중간의 Vercel·프록시)가 오류 상태로 답했지만 ProblemDetail 형식이 아님(예: HTML 오류 페이지)
// - network: 응답을 받지 못함(인터넷 끊김, 서버 미기동, 타임아웃)
// - canceled: 요청이 취소됨(화면을 떠나 TanStack Query가 요청을 취소한 경우 등). 사용자에게 알릴 필요가 없다
// - unknown: axios 오류가 아닌 예외(요청을 만들다 코드에서 난 오류 등)
export type ApiErrorKind = 'problem' | 'http' | 'network' | 'canceled' | 'unknown'

// ApiError를 만들 때 넘기는 값. 필드 의미는 아래 ApiError 클래스 주석과 같다
interface ApiErrorInit {
  kind: ApiErrorKind
  status: number | null
  errorCode: string | null
  title: string | null
  detail: string | null
  fieldErrors: FieldError[]
  userMessage: string
  retryAfterSeconds?: number | null // 응답의 Retry-After 헤더(초). 없거나 읽을 수 없으면 null(생략하면 null)
}

/**
 * 화면에서 쓰기 쉽게 정리한 API 오류다. 이 인스턴스의 모든 요청 오류는 이 클래스로 바뀌어 던져진다.
 *
 * 원래의 AxiosError(cause)를 담지 않는 이유(보안):
 * AxiosError에는 요청 설정(config)이 통째로 들어 있고, Task 057부터는 그 안의 headers에 Access Token(Authorization)이 실린다.
 * 화면이나 오류 추적 코드가 오류 객체를 console.log하거나 외부로 보내면 토큰이 함께 새어 나간다.
 * 그래서 화면에 필요한 값만 골라 담고 원본은 버린다.
 */
export class ApiError extends Error {
  readonly kind: ApiErrorKind
  readonly status: number | null // HTTP 상태 코드. 응답을 못 받았으면(network·canceled·unknown) null
  readonly errorCode: string | null // ProblemDetail의 errorCode(SCREAMING_SNAKE_CASE). ProblemDetail이 아니면 null
  readonly title: string | null // ProblemDetail의 title(HTTP 상태 문구, 예: 'Not Found'). 화면에 그대로 보이지 않는다
  readonly detail: string | null // ProblemDetail의 detail(서버가 쓴 설명). 화면 문구로 쓰지 말고 userMessage를 쓴다
  readonly fieldErrors: FieldError[] // VALIDATION_FAILED일 때 필드별 오류. 없으면 빈 배열이라 바로 map해도 된다
  readonly userMessage: string // 화면에 보일 한국어 문장(errorMessages.ts 표에서 찾은 값)
  // 다시 시도해도 되는 때까지 남은 초(응답의 Retry-After 헤더, 서버는 429에 싣는다). 헤더가 없거나 응답이 없으면 null.
  // 화면이 "약 N분 뒤에 다시 시도"처럼 남은 시간을 안내할 때 쓴다(Task 109 데모 로그인)
  readonly retryAfterSeconds: number | null
  // 인터셉터가 이미 사용자에게 알렸는지(예: 429 토스트). true면 화면이 같은 내용을 또 띄우지 않는다.
  // 화면이 오류를 직접 안내한 뒤 true로 바꿀 수 있게 readonly로 두지 않았다
  hasNotifiedUser = false

  constructor(init: ApiErrorInit) {
    // Error.message에는 개발자가 로그로 볼 요약(상태·코드)만 둔다. 응답 본문(detail 등)은 넣지 않는다(아래 "로그" 주석)
    super(`API 오류: ${init.kind} ${init.status ?? '응답 없음'} ${init.errorCode ?? ''}`.trim())
    this.name = 'ApiError'
    this.kind = init.kind
    this.status = init.status
    this.errorCode = init.errorCode
    this.title = init.title
    this.detail = init.detail
    this.fieldErrors = init.fieldErrors
    this.userMessage = init.userMessage
    this.retryAfterSeconds = init.retryAfterSeconds ?? null
  }

  /** 요청 제한(429) 오류인지 확인한다. */
  get isRateLimited(): boolean {
    return this.status === 429 || this.errorCode === 'RATE_LIMIT_EXCEEDED'
  }
}

/** 값이 ApiError인지 확인한다. TanStack Query의 error처럼 타입이 unknown일 때 쓴다. */
export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError
}

// ProblemDetail 응답의 Content-Type. 'application/problem+json; charset=UTF-8'처럼 뒤에 붙는 값이 있을 수 있어 포함 여부로 본다
const PROBLEM_JSON_CONTENT_TYPE = 'application/problem+json'

// ProblemDetail에서 꺼낸 값. 형식이 어긋난 필드는 null·빈 배열로 둔다
interface ParsedProblem {
  status: number | null
  title: string | null
  detail: string | null
  errorCode: string | null
  fieldErrors: FieldError[]
}

// 객체인지(배열·null 제외) 확인한다. 아래에서 응답 본문의 필드를 안전하게 읽기 위한 준비 단계다
function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

// 문자열이면 그대로, 아니면 null
function readString(value: unknown): string | null {
  return typeof value === 'string' ? value : null
}

/**
 * 응답 본문을 ProblemDetail로 해석한다. 객체가 아니면 null을 돌려준다.
 *
 * 생성 타입(ProblemDetail)으로 바로 단언(as)하지 않고 필드마다 확인하는 이유:
 * 응답 본문은 네트워크에서 온 값이라 타입이 보장되지 않는다. 백엔드 버그나 중간 프록시의 응답으로
 * errorCode가 숫자이거나 fieldErrors가 배열이 아니면, 단언만 한 코드는 화면에서 .map 등을 하다 터진다.
 */
function parseProblemDetail(body: unknown): ParsedProblem | null {
  if (!isRecord(body)) return null
  const fieldErrors = Array.isArray(body.fieldErrors)
    ? body.fieldErrors.flatMap((item): FieldError[] => {
        if (!isRecord(item)) return []
        const field = readString(item.field)
        const message = readString(item.message)
        // 계약상 field·message 둘 다 필수다. 하나라도 없으면 그 항목만 버리고 나머지는 살린다
        return field !== null && message !== null ? [{ field, message }] : []
      })
    : []
  return {
    status: typeof body.status === 'number' ? body.status : null,
    title: readString(body.title),
    detail: readString(body.detail),
    errorCode: readString(body.errorCode),
    fieldErrors,
  }
}

// 응답 헤더에서 Content-Type을 읽는다. 브라우저(XHR)는 헤더 이름을 소문자로 주지만, 방어적으로 대문자도 본다
function readContentType(headers: unknown): string {
  if (!isRecord(headers)) return ''
  const value = headers['content-type'] ?? headers['Content-Type']
  return typeof value === 'string' ? value.toLowerCase() : ''
}

// 응답 헤더에서 Retry-After(초)를 읽는다. 서버는 429에 정수 초로 싣는다(error-codes.md RATE_LIMIT_EXCEEDED).
// HTTP 날짜 형식이나 음수·소수처럼 초로 읽을 수 없는 값은 null로 두고, 화면은 기본 안내 문장을 쓴다
function readRetryAfterSeconds(headers: unknown): number | null {
  if (!isRecord(headers)) return null
  const value = headers['retry-after'] ?? headers['Retry-After']
  const text = typeof value === 'number' ? String(value) : value
  if (typeof text !== 'string' || !/^\d+$/.test(text.trim())) return null
  return Number(text.trim())
}

/**
 * 어떤 오류든 ApiError로 바꾼다. 응답 인터셉터가 쓰고, 테스트에서도 직접 호출한다.
 *
 * 판단 순서:
 * 1) 이미 ApiError면 그대로(인터셉터가 여러 개일 때 두 번 감싸지 않게)
 * 2) 취소된 요청 → canceled
 * 3) axios 오류가 아님 → unknown
 * 4) 응답이 없음 → network
 * 5) Content-Type이 application/problem+json이고 본문이 객체 → problem
 * 6) 그 밖의 오류 응답 → http (Vercel·Cloudtype이 백엔드 대신 HTML 오류 페이지를 주는 경우 등)
 */
export function normalizeApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error

  if (Axios.isCancel(error)) {
    return new ApiError({
      kind: 'canceled',
      status: null,
      errorCode: null,
      title: null,
      detail: null,
      fieldErrors: [],
      userMessage: '요청이 취소되었습니다.',
    })
  }

  if (!Axios.isAxiosError(error)) {
    return new ApiError({
      kind: 'unknown',
      status: null,
      errorCode: null,
      title: null,
      detail: null,
      fieldErrors: [],
      userMessage: getErrorMessage(null),
    })
  }

  const { response } = error
  if (!response) {
    return new ApiError({
      kind: 'network',
      status: null,
      errorCode: null,
      title: null,
      detail: null,
      fieldErrors: [],
      userMessage: NETWORK_ERROR_MESSAGE,
    })
  }

  const status = response.status
  const problem = readContentType(response.headers).includes(PROBLEM_JSON_CONTENT_TYPE)
    ? parseProblemDetail(response.data)
    : null
  // Retry-After는 ProblemDetail 여부와 상관없이 헤더에서 읽는다(앞단 프록시의 429에도 붙을 수 있다)
  const retryAfterSeconds = readRetryAfterSeconds(response.headers)

  if (problem) {
    return new ApiError({
      kind: 'problem',
      // HTTP 상태 줄의 값을 본문의 status보다 우선한다. 본문 값은 서버가 쓴 것이라 틀릴 수 있지만 상태 줄은 실제 응답이다
      status,
      errorCode: problem.errorCode,
      title: problem.title,
      detail: problem.detail,
      fieldErrors: problem.fieldErrors,
      userMessage: getUserMessage(status, problem.errorCode),
      retryAfterSeconds,
    })
  }

  return new ApiError({
    kind: 'http',
    status,
    errorCode: null,
    title: null,
    detail: null,
    fieldErrors: [],
    userMessage: getUserMessage(status, null),
    retryAfterSeconds,
  })
}

// 화면 메시지를 고른다. 아는 errorCode면 그 메시지, 코드는 없지만 429면 요청 제한 메시지, 그 밖에는 기본 메시지다.
// 429를 따로 보는 이유: 요청 제한은 백엔드뿐 아니라 앞단(Vercel 등)에서도 걸 수 있고, 그 응답에는 errorCode가 없다
function getUserMessage(status: number, errorCode: string | null): string {
  if (isKnownErrorCode(errorCode)) return getErrorMessage(errorCode)
  if (status === 429) return getErrorMessage('RATE_LIMIT_EXCEEDED')
  return getErrorMessage(errorCode)
}

// 429 토스트의 고정 id. 같은 id로 다시 띄우면 sonner가 새로 쌓지 않고 기존 토스트를 갱신한다.
// 한 화면이 여러 API를 동시에 부르다 모두 429를 받아도 토스트가 하나만 보이게 하기 위함이다.
export const RATE_LIMIT_TOAST_ID = 'api-rate-limit'

/**
 * 응답 인터셉터의 오류 처리부다. 오류를 ApiError로 정리하고, 429면 토스트를 띄운 뒤 다시 던진다(reject).
 *
 * 토스트를 인터셉터에서 띄우는 이유:
 * 429는 비로그인 공개 API(검색·상세 등) 어디서든 올 수 있다. 화면마다 처리하게 두면 새 화면을 만들 때 빠뜨리기 쉬워,
 * 모든 요청이 지나가는 이곳 한 군데에서 안내한다. sonner의 toast()는 React 바깥에서도 부를 수 있는 함수라 가능하다.
 *
 * 한계(알고 쓴다):
 * - <Toaster />가 화면에 붙어 있어야 보인다(app/providers.tsx에 한 번 배치). 붙기 전에 난 오류의 토스트는 보이지 않는다.
 * - 오류를 화면이 직접 처리하더라도 토스트는 이미 떴다. 그래서 hasNotifiedUser를 true로 표시해 화면이 같은 안내를
 *   또 띄우지 않게 한다.
 * - 429 말고는 토스트를 띄우지 않는다. 어떤 오류를 어떻게 보일지(빈 화면 안내, 필드 옆 문구 등)는 화면마다 달라서다.
 *
 * 로그를 남기지 않는 이유(보안·개인정보):
 * 응답 본문(detail, fieldErrors)에는 사용자가 입력한 값이나 이메일 같은 개인 정보가 섞일 수 있고, 브라우저 콘솔은
 * 운영 환경에서도 누구나 볼 수 있다. 오류 내용은 ApiError로 호출한 쪽에 넘기기만 하고 여기서 console에 찍지 않는다.
 */
export function handleResponseError(error: unknown): Promise<never> {
  // 요청 설정에 _skipRateLimitToast가 켜져 있는지는 정리하기 전의 AxiosError에서 읽는다(ApiError에는 요청 설정을 담지 않는다, 위 보안 주석)
  const isToastSkipped = Axios.isAxiosError(error) && error.config?._skipRateLimitToast === true
  const apiError = normalizeApiError(error)

  // 화면이 대기 시간을 직접 안내하는 요청(데모 로그인, Task 109)은 토스트를 띄우지 않는다. 그 화면이 알리므로 hasNotifiedUser는 false로 둔다
  if (apiError.isRateLimited && !apiError.hasNotifiedUser && !isToastSkipped) {
    toast.error(getErrorMessage('RATE_LIMIT_EXCEEDED'), { id: RATE_LIMIT_TOAST_ID })
    apiError.hasNotifiedUser = true
  }

  return Promise.reject(apiError)
}

/*
 * ===== 인증: Access Token 첨부와 401 재발급 (Task 057) =====
 *
 * [보안] 이 절은 사용자가 직접 검토하는 인증 코드다. 지키는 원칙:
 * - Access Token(AT)은 stores/authStore의 메모리에만 있고 여기서도 저장소·주소·로그에 내보내지 않는다.
 * - Refresh Token은 HttpOnly 쿠키라 자바스크립트가 읽지 못한다. 재발급 요청에 쿠키가 실리도록 withCredentials만 켠다.
 * - 재발급 요청은 이 파일의 인터셉터가 없는 별도 axios 인스턴스(REFRESH_CLIENT)로 보낸다. 같은 인스턴스를 쓰면
 *   재발급 요청이 401을 받았을 때 다시 재발급을 부르는 재귀가 생길 수 있다.
 * - 화면 가드는 편의일 뿐이고 실제 권한은 백엔드가 검사한다(frontend.md).
 */

// 재발급 전용 axios 인스턴스. 인터셉터(401 재시도, 오류 정리)를 일부러 달지 않아 재귀가 생길 수 없다.
// withCredentials: Refresh Token 쿠키(Path=/api/auth)를 실어 보낸다. 같은 출처(/api)라 브라우저가 어차피 보내지만,
// 이후 백엔드를 다른 출처로 옮겨도 쿠키가 빠지지 않게 명시한다. 목업 전환 헤더만 붙인다(재발급 실패 시나리오 확인용).
const REFRESH_CLIENT = Axios.create({ baseURL: '/api', withCredentials: true })
REFRESH_CLIENT.interceptors.request.use(attachMockHeaders)

// 요청에 Access Token을 붙인다. 로그인 전(토큰 없음)에는 아무것도 붙이지 않는다.
// 이미 Authorization이 있으면(재시도 요청이 새 토큰을 직접 넣은 경우) 덮어쓰지 않는다.
AXIOS_INSTANCE.interceptors.request.use((config) => {
  const { accessToken } = useAuthStore.getState()
  if (accessToken && !config.headers.has('Authorization')) {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return config
})

// 진행 중인 재발급 요청. 있으면 새로 보내지 않고 이 요청의 결과를 함께 기다린다(single-flight).
// 동시에 여러 요청이 401을 받아도 재발급은 하나만 나간다. Refresh Token은 회전(1회용)이라 동시에 여러 번 보내면
// 두 번째부터 "이미 쓴 토큰 재사용"으로 보여 계열이 폐기될 수 있어, 한 번에 하나만 보내는 것이 안전하다.
let refreshInFlight: Promise<void> | null = null

// 재발급 요청 한 번을 보내고 결과를 authStore에 반영한다.
// 401이면 로그인 상태를 비우고(guest), 그 밖의 오류(500·네트워크 등)는 상태를 건드리지 않고 ApiError로 던진다.
async function requestRefresh(): Promise<void> {
  try {
    // 1. 쿠키만 보내고 본문은 없다(계약: POST /auth/refresh)
    const response = await REFRESH_CLIENT.post<CommonResponseAccessToken>('/auth/refresh')
    // 2. 응답은 네트워크에서 온 값이라 타입을 믿지 않고 확인한다
    const data = response.data?.data
    if (!data || typeof data.accessToken !== 'string' || data.accessToken === '') {
      throw new Error('재발급 응답 형식이 올바르지 않습니다')
    }
    // 3. AT와 역할을 메모리에만 저장한다(역할은 재발급 때마다 서버가 DB에서 다시 읽은 값이다)
    useAuthStore.getState().setSession(data.accessToken, data.role)
  } catch (error) {
    const apiError = normalizeApiError(error)
    // 4. 401은 "다시 로그인해야 함"이다(쿠키 없음·만료·재사용 감지). 그 밖은 서버 사정이라 로그인 상태를 지우지 않는다
    if (apiError.status === 401) useAuthStore.getState().clearSession()
    throw apiError
  }
}

/**
 * Access Token을 재발급받아 authStore에 넣는다. 진행 중인 요청이 있으면 그 결과를 함께 기다린다(single-flight).
 *
 * 실패하면 ApiError를 던진다. status가 401이면 이미 authStore가 guest로 바뀐 상태다.
 * 로그인 콜백 화면, 앱 시작(restoreSession), 401 인터셉터가 모두 이 함수를 쓰므로 같은 순간에 겹쳐도 요청은 하나다.
 */
export function refreshSession(): Promise<void> {
  if (!refreshInFlight) {
    refreshInFlight = requestRefresh().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

/**
 * 앱을 열 때(새로고침 포함) 로그인 상태를 복원한다. 쿠키의 Refresh Token으로 Access Token을 다시 받는다.
 *
 * - 성공: authenticated. 401: guest(원래 로그아웃 상태였다). 그 밖의 실패: error(로그인 여부를 알 수 없음).
 * - 목업 모드에서는 하지 않는다(로그인 상태는 개발용 전환 토글이 정한다, hooks/useSession).
 * "다시 시도" 버튼도 이 함수를 다시 부른다.
 */
export async function restoreSession(): Promise<void> {
  if (isMockMode()) return
  useAuthStore.getState().markChecking()
  try {
    await refreshSession()
  } catch (error) {
    if (isApiError(error) && error.status === 401) return // 이미 guest로 바뀌어 있다
    useAuthStore.getState().markUnavailable()
  }
}

/**
 * 401 응답을 받으면 재발급 후 같은 요청을 한 번 다시 보내는 인터셉터 처리부다.
 *
 * 흐름:
 * 1. 401이 아니거나, 이미 한 번 재시도한 요청이면 그대로 실패시킨다(무한 반복 방지)
 * 2. 이 요청에 Access Token을 붙여 보내지 않았다면(비로그인 요청) 재발급할 대상이 아니라 그대로 실패시킨다
 * 3. 그 사이 다른 요청이 이미 토큰을 갱신했다면(보낸 토큰 ≠ 지금 토큰) 재발급 없이 새 토큰으로 바로 재시도한다
 * 4. 아니면 재발급한다(동시 401은 refreshSession이 요청 하나로 묶는다)
 *    - 재발급이 401: 로그인 상태가 비워졌다. 원래 401을 그대로 던지고, 화면은 가드가 로그인 화면으로 보낸다
 *    - 재발급이 500·네트워크 오류: 로그인 화면으로 보내지 않는다(상태 유지). 재발급의 오류를 그대로 던진다
 * 5. 새 토큰을 붙여 원래 요청을 한 번 다시 보낸다
 *
 * 이 인터셉터가 오류 정리 인터셉터(handleResponseError)보다 먼저 실행돼야 한다. 다시 보내려면 원래 요청 설정(config)이 든
 * AxiosError가 필요한데, 정리 단계를 지나면 ApiError로 바뀌어 config가 사라진다(axios는 등록한 순서대로 실행한다).
 */
async function retryAfterRefresh(error: unknown): Promise<unknown> {
  // 1.
  if (!Axios.isAxiosError(error) || error.response?.status !== 401 || !error.config) throw error
  const config = error.config
  if (config._authRetried) throw error

  // 2.
  const sentAuthorization = config.headers.get('Authorization')
  if (!sentAuthorization) throw error

  // 3. + 4.
  const currentToken = useAuthStore.getState().accessToken
  const alreadyRefreshed = currentToken !== null && sentAuthorization !== `Bearer ${currentToken}`
  if (!alreadyRefreshed) {
    try {
      await refreshSession()
    } catch (refreshError) {
      if (isApiError(refreshError) && refreshError.status === 401) throw error
      throw refreshError
    }
  }

  // 5. 로그아웃 등으로 그 사이 토큰이 비워졌다면 다시 보내지 않고 원래 401을 돌려준다
  const newToken = useAuthStore.getState().accessToken
  if (!newToken) throw error
  config._authRetried = true
  config.headers.set('Authorization', `Bearer ${newToken}`)
  return AXIOS_INSTANCE(config)
}

AXIOS_INSTANCE.interceptors.response.use((response) => response, retryAfterRefresh)

// 성공 응답(2xx)은 그대로 통과시키고(래퍼를 벗기지 않음), 실패만 위 함수로 정리한다
AXIOS_INSTANCE.interceptors.response.use((response) => response, handleResponseError)

// orval 생성 훅의 오류 타입. 위 인터셉터 때문에 이 인스턴스의 오류는 항상 ApiError다.
// orval은 ErrorType<TooManyRequestsResponse | ...>처럼 계약의 오류 스키마를 타입 인자로 넘기지만,
// 그 본문은 이미 ApiError의 필드(errorCode, fieldErrors 등)로 정리돼 있어 타입 인자는 쓰지 않는다.
// 이전 정의(인터셉터가 없던 Task 018 시점)는 AxiosError였다:
// export type ErrorType<Error> = AxiosError<Error>
// eslint-disable-next-line @typescript-eslint/no-unused-vars -- orval이 타입 인자를 넘기므로 자리는 남겨 둔다
export type ErrorType<_ProblemBody> = ApiError

// 요청 본문 타입. 별도 변환 없이 그대로 쓴다(필드명이 백엔드 camelCase와 같다).
export type BodyType<BodyData> = BodyData
