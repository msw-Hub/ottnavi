import Axios from 'axios'
import type { AxiosError, AxiosRequestConfig } from 'axios'

// axios 인스턴스와 orval custom mutator.
// - 베이스 URL은 항상 상대 경로 '/api'다. 배포는 Vercel rewrite, 로컬은 Vite dev server proxy가 백엔드로 전달하므로
//   백엔드 주소를 코드나 환경변수에 직접 두지 않는다(같은 출처로 호출해야 HttpOnly 쿠키와 CORS 문제가 생기지 않는다).
// - 인증(Access Token 첨부, 401 재발급 1회)과 ProblemDetail 파싱 인터셉터는 이 인스턴스에 붙이며,
//   각각 Task 057(인증 연동), Task 025(에러 인터셉터)에서 구현한다.
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

// orval 생성 훅의 오류 타입. 실제 오류 본문은 ProblemDetail(application/problem+json)이다.
export type ErrorType<Error> = AxiosError<Error>

// 요청 본문 타입. 별도 변환 없이 그대로 쓴다(필드명이 백엔드 camelCase와 같다).
export type BodyType<BodyData> = BodyData
