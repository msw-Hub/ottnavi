import Axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { toast } from 'sonner'
import type { FieldError } from '@/api/generated/model'
import { getErrorMessage, isKnownErrorCode, NETWORK_ERROR_MESSAGE } from '@/lib/errorMessages'

// axios 인스턴스와 orval custom mutator.
// - 베이스 URL은 항상 상대 경로 '/api'다. 배포는 Vercel rewrite, 로컬은 Vite dev server proxy가 백엔드로 전달하므로
//   백엔드 주소를 코드나 환경변수에 직접 두지 않는다(같은 출처로 호출해야 HttpOnly 쿠키와 CORS 문제가 생기지 않는다).
// - 인증(Access Token 첨부, 401 재발급 1회)은 Task 057에서, ProblemDetail 파싱(오류 정리) 인터셉터는 이 파일 아래쪽에 있다(Task 025).
export const AXIOS_INSTANCE = Axios.create({
  baseURL: '/api',
})

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
  const apiError = normalizeApiError(error)

  if (apiError.isRateLimited && !apiError.hasNotifiedUser) {
    toast.error(getErrorMessage('RATE_LIMIT_EXCEEDED'), { id: RATE_LIMIT_TOAST_ID })
    apiError.hasNotifiedUser = true
  }

  return Promise.reject(apiError)
}

// TODO(Task 057): 401 재발급 인터셉터를 이 줄 위에 등록한다.
// axios는 응답 인터셉터를 등록한 순서대로 실행한다. 재발급 인터셉터는 원래 요청 설정(config)이 든 AxiosError가
// 있어야 같은 요청을 다시 보낼 수 있는데, 아래 인터셉터를 지나면 ApiError로 바뀌어 config가 사라진다.
// 그래서 재발급 인터셉터가 먼저 실행되도록 이 등록보다 앞에 둔다. (재발급은 한 번만, 동시 401은 요청 하나로 묶기: frontend.md 인증 규칙)

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
