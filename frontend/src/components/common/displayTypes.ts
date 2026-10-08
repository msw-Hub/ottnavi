/*
 * 공통 표시 컴포넌트가 받는 값의 종류와 화면 문구를 모은 파일이다.
 *
 * 왜 따로 두나:
 * - 제공 상태와 가격 출처는 아직 API 계약(docs/api/openapi.yaml)에 없어 orval 생성 타입이 없다.
 *   그래서 ERD에 적힌 값 그대로 문자열 유니온 타입을 임시로 둔다. 계약에 반영되면(Task 031) 생성 타입으로 바꾼다.
 *   한 파일에 모아 두면 바꿀 때 이 파일만 고치면 된다.
 * - 문구 표(Record)를 컴포넌트 파일에서 export하면 개발 서버의 빠른 새로고침(react-refresh) 규칙에 걸려,
 *   컴포넌트 파일과 분리했다.
 */

// 임시: 계약 반영 후 생성 타입으로 교체 (ERD availability.status 값: AVAILABLE / NOT_AVAILABLE / UNKNOWN)
// 작품(또는 시즌)이 어느 서비스에서 구독(정액제)으로 제공되는지의 3상태
export type AvailabilityStatus = 'AVAILABLE' | 'NOT_AVAILABLE' | 'UNKNOWN'

// 제공 상태별 화면 글자. 색을 구분하지 못해도 이 글자로 상태를 읽을 수 있어야 한다(frontend.md 화면 표시 규칙)
export const AVAILABILITY_LABELS: Record<AvailabilityStatus, string> = {
  AVAILABLE: '있음',
  NOT_AVAILABLE: '없음',
  UNKNOWN: '모름',
}

// 임시: 계약 반영 후 생성 타입으로 교체 (ERD plan_month_product.price_source 값)
// 플랜 계산에 쓴 금액이 어디서 왔는지
// - ADMIN: 관리자가 등록한 요금표의 기본 금액
// - USER_OVERRIDE: 사용자가 직접 고친 금액
// - ALREADY_PAID: 이미 구독 중이라 이번 달 비용에 넣지 않음
// - FREE: 가족 공유 등으로 무료로 이용 중
export type PriceSource = 'ADMIN' | 'USER_OVERRIDE' | 'ALREADY_PAID' | 'FREE'

// 가격 출처별 화면 글자(사용자 확정 문구, 2026-10-09)
export const PRICE_SOURCE_LABELS: Record<PriceSource, string> = {
  ADMIN: '기본 요금',
  USER_OVERRIDE: '사용자 수정',
  ALREADY_PAID: '이미 결제',
  FREE: '무료',
}
