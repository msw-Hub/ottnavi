/*
 * 임시 타입 파일 (Task 028, SCR-10 플랜 결과).
 *
 * 플랜 계산·초안 조회·저장 API는 아직 계약(docs/api/openapi.yaml)에 없다. Task 031에서 생성 타입으로 바꾸고 이 파일을 지운다.
 * 경로와 필드는 제안이다(PRD 9절 API 후보 POST /api/plans/calculate, POST /api/plans/{id}/activate를 `/me/plans/...` 경로로 옮김).
 * - POST /me/plans/calculate         플랜 유형 3종 DRAFT를 새로 계산한다(기존 DRAFT 덮어씀)
 * - GET  /me/plans/drafts            지금 DRAFT들을 조회한다
 * - POST /me/plans/{planType}/save   고른 유형의 DRAFT를 ACTIVE로 저장한다(나머지 DRAFT는 삭제). 데이터가 갱신됐으면 409 PLAN_DRAFT_STALE
 */
import type { PriceSource, WishlistPriority } from '@/components/common/displayTypes'

export type { PriceSource, WishlistPriority }

// 임시: 계약 반영 후 생성 타입으로 교체 — 플랜 유형(ERD plan.plan_type, PRD 11절 35번)
// RECOMMENDED=추천형 / SAVER=절약형(추천형 점수의 70% 이상 중 가장 싸게) / SIMPLE=간편형(새로 가입하는 횟수 최소)
export type PlanType = 'RECOMMENDED' | 'SAVER' | 'SIMPLE'

// 임시: 계약 반영 후 생성 타입으로 교체 — 계산 방식(ERD plan.algorithm). 화면에 크게 보이지 않는 참고 정보
export type PlanAlgorithm = 'EXACT' | 'GREEDY'

// 임시: 계약 반영 후 생성 타입으로 교체 — 작품 구분(영화 / TV)
export type MediaType = 'movie' | 'tv'

// 임시: 계약 반영 후 생성 타입으로 교체 — 그 달에 구독할 상품 하나(ERD plan_month_product)
export interface PlanMonthProduct {
  productId: number // 상품 ID
  productName: string // 상품(요금제) 이름
  ottServiceIds: number[] // 상품이 포함하는 서비스 ID들(단품은 1개, 번들은 여러 개)
  appliedPrice: number // 적용 월 요금(원). ALREADY_PAID·FREE면 이번 달 비용에 넣지 않는다
  priceSource: PriceSource // 금액 출처 ADMIN / USER_OVERRIDE / ALREADY_PAID / FREE
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 그 달에 볼 작품 배정 하나(ERD plan_assignment + 작품 정보)
// 긴 시즌은 연속된 달에 걸쳐 여러 조각으로 나뉘어 달마다 하나씩 들어간다(PRD 5.4 긴 시즌)
export interface PlanAssignment {
  planItemId: number // 플랜 항목 ID(같은 시즌의 조각들은 같은 값 → 달을 가로질러 묶는 키)
  mediaType: MediaType // 영화 / 드라마(tv)
  tmdbId: number // TMDB ID
  title: string // 한국어 제목
  seasonNumber: number | null // 드라마 시즌 번호. 영화는 null
  priority: WishlistPriority // 우선순위
  ottServiceId: number // 이 달에 이 조각을 보는 서비스(달마다 다를 수 있다)
  minutes: number // 이 달에 배정된 시청 시간(분)
  segmentIndex: number // 몇 번째 조각인지(1부터). 나뉘지 않으면 1
  segmentCount: number // 전체 조각 수. 나뉘지 않으면 1
  isContinuation: boolean // 앞 달에서 이어 보는 조각인지 → "이어 보기" 표시 (segmentIndex > 1)
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 플랜의 한 달(ERD plan_month)
export interface PlanMonth {
  monthIndex: 0 | 1 | 2 // 0=이번 달(확정), 1·2=다음 두 달(예상)
  month: string // 해당 달(YYYY-MM)
  isConfirmed: boolean // 이번 달만 true. 나머지는 "예상"
  cost: number // 그 달 비용(원). 구독 상품이 없으면 0
  products: PlanMonthProduct[] // 그 달에 구독할 상품들(없으면 빈 배열 = 그 달은 구독하지 않음)
  assignments: PlanAssignment[] // 그 달에 볼 작품들
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 계산에서 빠진 이유(ERD plan_item.reason)
// BUDGET=예산 부족 / TIME=시청 시간 초과 / UNKNOWN=제공처 정보 없음(모름) / NO_PROVIDER=7개 서비스에 없음
export type UnscheduledReason = 'BUDGET' | 'TIME' | 'UNKNOWN' | 'NO_PROVIDER'

// 임시: 계약 반영 후 생성 타입으로 교체 — 이번 3개월 안에 배정하지 못한 작품 하나("남은 작품과 이유")
export interface UnscheduledItem {
  planItemId: number // 플랜 항목 ID
  mediaType: MediaType
  tmdbId: number
  title: string
  seasonNumber: number | null
  priority: WishlistPriority
  reason: UnscheduledReason // 빠진 이유
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 플랜 초안 하나(ERD plan, status = DRAFT)
export interface PlanDraft {
  planId: number // 플랜 ID
  planType: PlanType // 이 초안의 유형. 서버는 항상 유형마다 초안 하나씩(3개)을 응답하고, 선택이 같으면 프론트가 합쳐 보여 준다(mergeIdenticalDrafts)
  startMonth: string // 첫 달(YYYY-MM)
  algorithm: PlanAlgorithm // 계산 방식
  monthlyBudget: number // 계산에 쓴 월 예산(스냅샷, 원)
  monthlyWatchMinutes: number // 계산에 쓴 월 시청 시간(스냅샷, 분)
  mustTotal: number // 꼭 볼 작품 수
  mustCompleted: number // 그중 3개월 안에 다 볼 수 있는 수
  scheduledCount: number // 배정된 작품 수(찜 전체 중)
  totalCount: number // 계산 대상 찜 작품 수
  score: number // 점수(보고 싶음 2, 여유될 때 1의 합. 꼭 작품은 mustCompleted로만 센다)
  totalCost: number // 3개월 총비용(원)
  // 기준(추천안, 사용자 결정 대기 - Task 030에서 확정): 찜한(안 다 본) 작품을 볼 수 있는 서비스 전부를 3개월 내내
  // 적용 금액(기본 요금, 사용자 수정 금액이 있으면 그 금액)으로 구독했을 때 비용(원). 모든 플랜 유형에 같은 기준이라 세 유형의 값이 같다
  allSubscribeCost: number
  savedAmount: number // 절감액 = allSubscribeCost - totalCost
  months: PlanMonth[] // 3개월(monthIndex 0, 1, 2 순)
  unscheduled: UnscheduledItem[] // 남은 작품과 이유
  dataAsOf: string // 데이터 기준일(YYYY-MM-DD)
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 초안 목록(계산·조회 응답). 계산 전이면 빈 배열
export interface PlanDraftList {
  drafts: PlanDraft[]
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 초안 목록 응답 래퍼
export interface CommonResponsePlanDraftList {
  success: boolean
  data: PlanDraftList
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 저장 요청. 어느 초안을 저장하려는지 알리는 planId(그 사이 데이터가 갱신됐는지 서버가 판단하는 기준)
export interface SavePlanRequest {
  planId: number
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 저장 응답(ERD plan, status = ACTIVE)
export interface SavedPlan {
  planId: number
  planType: PlanType // 저장한 유형
  status: 'ACTIVE'
  activatedAt: string // 저장 시각(ISO-8601)
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 저장 응답 래퍼
export interface CommonResponseSavedPlan {
  success: boolean
  data: SavedPlan
}
