/*
 * 공통 표시 컴포넌트가 받는 값의 종류와 화면 문구를 모은 파일이다.
 *
 * 왜 따로 두나:
 * - 값의 종류(제공 상태·가격 출처·찜 우선순위·이용 상태)는 계약(docs/api/openapi.yaml)의 orval 생성 타입을 그대로 쓴다
 *   (Task 031에서 임시 타입을 생성 타입으로 교체했고, Task 037에서 남은 "임시" 표시를 정리했다). 이 파일에는 손으로 만든 타입이 없다.
 *   한 파일에 모아 두면 화면 문구를 바꿀 때 이 파일만 고치면 된다.
 * - 문구 표(Record)를 컴포넌트 파일에서 export하면 개발 서버의 빠른 새로고침(react-refresh) 규칙에 걸려,
 *   컴포넌트 파일과 분리했다.
 */
import type {
  AvailabilityStatus,
  PriceSource,
  ServiceSubscriptionStatus,
  WishlistPriority,
} from '@/api/generated/model'

// 작품(또는 시즌)이 어느 서비스에서 구독(정액제)으로 제공되는지의 3상태(확정 계약의 생성 타입을 그대로 쓴다, Task 031 6-6)
export type { AvailabilityStatus }

// 제공 상태별 화면 글자. 색을 구분하지 못해도 이 글자로 상태를 읽을 수 있어야 한다(frontend.md 화면 표시 규칙)
export const AVAILABILITY_LABELS: Record<AvailabilityStatus, string> = {
  AVAILABLE: '있음',
  NOT_AVAILABLE: '없음',
  UNKNOWN: '모름',
}

// 플랜 계산에 쓴 금액이 어디서 왔는지(확정 계약의 생성 타입 PriceSource를 그대로 쓴다, Task 031 6-5)
// - ADMIN: 관리자가 등록한 요금표의 기본 금액
// - USER_OVERRIDE: 사용자가 직접 고친 금액
// - ALREADY_PAID: 이미 구독 중이라 이번 달 비용에 넣지 않음
// - FREE: 가족 공유 등으로 무료로 이용 중
export type { PriceSource }

// 가격 출처별 화면 글자(사용자 확정 문구, 2026-10-09)
export const PRICE_SOURCE_LABELS: Record<PriceSource, string> = {
  ADMIN: '기본 요금',
  USER_OVERRIDE: '사용자 수정',
  ALREADY_PAID: '이미 결제',
  FREE: '무료',
}

// 찜 우선순위(PRD FR-09: 꼭 볼 작품 / 보고 싶음 / 여유될 때). 확정 계약의 생성 타입을 그대로 쓴다(Task 031 6-6)
export type { WishlistPriority }

// 우선순위 선택지. 순서가 화면에 보이는 순서다
export const WISHLIST_PRIORITY_OPTIONS: { value: WishlistPriority; label: string }[] = [
  { value: 'MUST', label: '꼭 볼 작품' },
  { value: 'WANT', label: '보고 싶음' },
  { value: 'MAYBE', label: '여유될 때' },
]

// 우선순위별 화면 글자
export const WISHLIST_PRIORITY_LABELS: Record<WishlistPriority, string> = {
  MUST: '꼭 볼 작품',
  WANT: '보고 싶음',
  MAYBE: '여유될 때',
}

/*
 * 우선순위별 한 줄 설명. 계산이 이 단계를 어떻게 다루는지 쉬운 말로 알린다(PRD 5.4·11절 35번).
 * - 꼭 볼 작품: 모든 플랜 유형이 "꼭 볼 작품 시청 완료 수"를 가장 먼저 최대화한다
 * - 보고 싶음: 그다음 점수(보고 싶음 2점)로 챙긴다
 * - 여유될 때: 점수 1점이라 예산·시간이 남을 때 들어간다
 * 결과를 약속하는 말("반드시 보게 해요")은 쓰지 않고 "최대한"·"남으면"처럼 수위를 낮춘다(frontend.md 표현 수위).
 */
export const WISHLIST_PRIORITY_DESCRIPTIONS: Record<WishlistPriority, string> = {
  MUST: '계산이 가장 먼저 챙겨요. 예산·시간 안에서 최대한 끝까지 보게 짜요.',
  WANT: '꼭 볼 작품 다음으로 챙겨요.',
  MAYBE: '예산과 시간이 남으면 넣어요.',
}

// 온보딩에서 사용자가 서비스마다 고르는 이용 상태. 확정 계약의 생성 타입을 그대로 쓴다(Task 031 6-4).
// - SUBSCRIBED: 구독 중(요금제·결제일을 입력) / FREE: 가족 공유 등으로 무료 이용 중 / NOT_SUBSCRIBED: 미구독 / UNKNOWN: 아직 모르겠음
export type { ServiceSubscriptionStatus }

// 이용 상태별 화면 글자
export const SUBSCRIPTION_STATUS_LABELS: Record<ServiceSubscriptionStatus, string> = {
  SUBSCRIBED: '구독 중',
  FREE: '무료 이용',
  NOT_SUBSCRIBED: '미구독',
  UNKNOWN: '잘 모르겠음',
}
