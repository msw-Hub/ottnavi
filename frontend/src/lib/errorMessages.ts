/*
 * API 오류 코드(errorCode) → 화면 메시지 표다.
 *
 * 백엔드 오류 응답(ProblemDetail)의 detail은 서버가 쓴 설명이라 문구가 화면에 맞지 않거나 바뀔 수 있다.
 * 그래서 화면에는 errorCode로 이 표에서 찾은 메시지를 보인다(frontend.md 에러 응답 규칙).
 *
 * IMPORTANT: 이 표는 docs/api/error-codes.md의 "화면 메시지" 열과 글자까지 같아야 한다.
 * errorMessages.test.ts가 문서를 직접 읽어 코드 목록과 메시지를 비교하므로, 문서에 코드를 추가·수정하면
 * 이 표도 같은 PR에서 고쳐야 테스트가 통과한다.
 */

// 공통 코드(error-codes.md "공통 코드" 표)
const COMMON_ERROR_MESSAGES = {
  VALIDATION_FAILED: '입력한 내용을 다시 확인해 주세요.',
  INVALID_REQUEST: '요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.',
  UNAUTHORIZED: '로그인이 필요합니다.',
  FORBIDDEN: '접근 권한이 없습니다.',
  RESOURCE_NOT_FOUND: '요청한 페이지를 찾을 수 없습니다.',
  METHOD_NOT_ALLOWED: '요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.',
  TITLE_NOT_FOUND: '작품을 찾을 수 없습니다.',
  RATE_LIMIT_EXCEEDED: '요청이 많습니다. 잠시 후 다시 시도해 주세요.',
  INTERNAL_ERROR: '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.',
  EXTERNAL_API_ERROR: '외부 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
} as const

// TECH 신규 코드(error-codes.md "TECH 신규 코드" 표). 문서상 "(제안)" 상태이며 해당 기능 Task에서 확정된다
const TECH_ERROR_MESSAGES = {
  PLAN_DRAFT_STALE: '데이터가 갱신되어 플랜을 다시 계산하고 있습니다.',
  PLAN_DRAFT_NOT_FOUND: '계산된 플랜을 찾을 수 없습니다. 다시 계산해 주세요.',
  BATCH_ALREADY_RUNNING: '같은 날짜의 수집이 이미 실행 중입니다.',
  BATCH_ALREADY_COMPLETED: '해당 날짜의 수집은 이미 완료되었습니다.',
} as const

// 두 표를 합친 전체 매핑. 화면 코드는 이 값이 아니라 getErrorMessage를 쓴다
export const ERROR_MESSAGES = { ...COMMON_ERROR_MESSAGES, ...TECH_ERROR_MESSAGES } as const

// 문서에 있는 오류 코드의 문자열 유니온 타입(예: 'TITLE_NOT_FOUND'). 특정 코드로 분기할 때 오타를 막는다
export type ErrorCode = keyof typeof ERROR_MESSAGES

// 표에 없는 코드(백엔드가 새 코드를 먼저 배포한 경우 등)이거나 errorCode가 없을 때 보이는 메시지
export const DEFAULT_ERROR_MESSAGE = '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'

// 서버 응답을 아예 받지 못했을 때(인터넷 끊김, 서버 미기동 등)의 메시지. 서버가 준 errorCode가 아니므로 표와 따로 둔다
export const NETWORK_ERROR_MESSAGE =
  '서버에 연결할 수 없습니다. 인터넷 연결을 확인한 뒤 다시 시도해 주세요.'

/** 문자열이 문서에 있는 오류 코드인지 확인한다(타입 가드). */
export function isKnownErrorCode(errorCode: string | null | undefined): errorCode is ErrorCode {
  // `errorCode in ERROR_MESSAGES`를 쓰지 않는 이유: 'toString'처럼 모든 객체가 물려받는 이름도 true가 된다.
  // Object.hasOwn은 표에 직접 적힌 키만 인정한다
  return errorCode != null && Object.hasOwn(ERROR_MESSAGES, errorCode)
}

/** errorCode에 맞는 화면 메시지를 돌려준다. 모르는 코드이거나 값이 없으면 기본 메시지를 돌려준다. */
export function getErrorMessage(errorCode?: string | null): string {
  return isKnownErrorCode(errorCode) ? ERROR_MESSAGES[errorCode] : DEFAULT_ERROR_MESSAGE
}
