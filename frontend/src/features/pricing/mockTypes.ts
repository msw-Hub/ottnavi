/*
 * 임시 타입 파일 (Task 028, SCR-09 요금 확인·수정).
 *
 * 내 요금 조회·수정 API(GET /me/pricing, PATCH /me/pricing/{ottServiceId})는 아직 계약(docs/api/openapi.yaml)에 없다.
 * Task 031에서 생성 타입으로 바꾸고 이 파일을 지운다. 경로와 필드는 제안이다.
 */
import type { PriceSource, ServiceSubscriptionStatus } from '@/components/common/displayTypes'

// 임시: 계약 반영 후 생성 타입으로 교체 — 서비스 하나의 요금 줄
export interface PricingService {
  ottServiceId: number // 서비스 내부 ID(서비스 목록 API의 ottServiceId). 수정 요청의 주소에 쓴다
  productId: number // 상품 ID(ERD subscription_product). 사용자 금액은 상품 단위로 저장된다
  productName: string // 상품(요금제) 이름. 예: "넷플릭스 스탠다드"
  adminPrice: number // 관리자가 등록한 기본 월 요금(원)
  userPrice: number | null // 사용자가 고친 월 요금(원). 고치지 않았으면 null
  appliedPrice: number // 계산에 쓰는 금액 = userPrice ?? adminPrice
  priceSource: Extract<PriceSource, 'ADMIN' | 'USER_OVERRIDE'> // 적용 금액의 출처
  subscriptionStatus: ServiceSubscriptionStatus // 온보딩에서 고른 이용 상태(구독 중·무료 서비스는 화면에서 따로 표시)
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 요금 확인 화면 응답
export interface PricingOverview {
  services: PricingService[] // 서비스 7곳의 요금(서비스 ID 순)
  priceEffectiveFrom: string // 요금 기준일(YYYY-MM-DD). 관리자 요금표의 적용 시작일
  monthlyBudget: number | null // 월 예산 요약(내 설정 값). 설정 전이면 null
  monthlyWatchMinutes: number | null // 월 시청 시간 요약(분). 설정 전이면 null
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 요금 응답 래퍼
export interface CommonResponsePricingOverview {
  success: boolean
  data: PricingOverview
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 사용자 금액 수정 요청. null이면 고친 금액을 지우고 기본 요금으로 되돌린다
export interface UpdatePricingRequest {
  monthlyPrice: number | null // 0 이상 정수(원)
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 사용자 금액 수정 응답 래퍼(바뀐 줄 하나)
export interface CommonResponsePricingService {
  success: boolean
  data: PricingService
}
