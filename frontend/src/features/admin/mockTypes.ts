/*
 * 임시 타입 파일 (Task 029, 관리자 화면 SCR-14 OTT 구독상품·월 구독요금 / SCR-15 제공처 매핑 / SCR-16 수집).
 *
 * 아래 관리자 API는 아직 계약(docs/api/openapi.yaml)에 없다. 경로와 필드는 모두 "제안"이며 Task 031에서 계약으로 확정되면
 * 생성 타입으로 바꾸고 이 파일을 지운다(frontend.md "목업 단계의 임시 타입 예외").
 *
 * 경로 규칙(api-contract.md): 관리자 API는 /api/admin/** , 성공은 CommonResponse 래퍼, 오류는 ProblemDetail(errorCode)이다.
 * 용어(사용자 결정 2026-10-11): 화면과 주석에서 "상품"은 "OTT 구독상품", "가격/요금"은 "월 구독요금"이다. 코드 식별자는 영어(product, price)를 그대로 쓴다.
 *
 * ----- 제안 API 목록 -----
 * OTT 구독상품·월 구독요금
 *   GET    /api/admin/products                                     OTT 구독상품 목록(월 구독요금 이력 포함)
 *   POST   /api/admin/products                                     OTT 구독상품 등록(첫 월 구독요금도 함께 입력)
 *   PATCH  /api/admin/products/{productId}                         이름 수정·판매 종료/재개(active)
 *   POST   /api/admin/products/{productId}/prices                  월 구독요금 줄 추가
 *   PATCH  /api/admin/products/{productId}/prices/{priceId}        가장 최근 줄 수정(마지막 확인일도 오늘로 갱신)
 *   DELETE /api/admin/products/{productId}/prices/{priceId}        가장 최근 줄 삭제(줄이 하나뿐이면 불가)
 *   POST   /api/admin/products/{productId}/prices/{priceId}/verify [요금 확인함]: 마지막 확인일만 오늘로 갱신(요청 본문 없음). 지금 적용 중인 줄만 가능, 아니면 409 PRICE_NOT_CURRENT(제안)
 *   → 쓰기 API는 모두 바뀐 OTT 구독상품 하나(AdminProduct)를 돌려준다. 화면이 목록 캐시를 바로 고칠 수 있게 하기 위함이다
 * 제공처 매핑
 *   GET    /api/admin/providers                                    TMDB 제공처 목록(미매핑 포함)
 *   PATCH  /api/admin/providers/{providerId}                        매핑 저장(서비스에 연결 / 관련 없음 / 미매핑으로 되돌림). 바뀐 제공처 하나를 돌려준다
 * 수집
 *   GET    /api/admin/collect                                      수집 실행 이력(최근 순)
 *   POST   /api/admin/collect                                      수동 실행. 202 + executionId (TECH 3절)
 *   POST   /api/admin/collect/{executionId}/recover                중단됨 실행을 실패로 바꿈(TECH 3절 제안). 중단됨이 아니면 409 BATCH_NOT_STOPPED(제안)
 *   POST   /api/admin/collect/{executionId}/restart                실패한 실행을 같은 날짜·범위로 다시 실행(제안). 202 + 새 executionId. 실패가 아니면 409
 *     (restart는 Spring Batch가 같은 JobInstance에 새 JobExecution을 만드는 것과 같아 새 executionId를 준다. 실패 기록은 이력에 남는다)
 *   GET    /api/admin/collect/{executionId}/changes                실행별 변경된 작품 목록
 *
 * 월 구독요금 모델(사용자 결정 2026-10-11): 줄 = (월 구독요금, 적용 시작일, 마지막 확인일). 적용 종료일은 저장하지 않고
 * 화면이 "다음 줄 시작일의 전날"로 계산하며 마지막 줄은 "계속"이다. 날짜 D의 요금은 시작일이 D 이전이면서 가장 늦은 줄이다.
 * (ERD의 product_price.effective_to는 이 모델과 다르다. 계약·ERD 정리는 Task 031·032에서 확정한다)
 */
import type { AvailabilityStatus } from '@/components/common/displayTypes'

/* ===== 공통 ===== */

// 임시: 계약 반영 후 생성 타입으로 교체 — OTT 구독상품 종류. MVP는 단품(SINGLE)만 다룬다(번들은 2단계)
export type AdminProductType = 'SINGLE'

// 임시: 계약 반영 후 생성 타입으로 교체 — 제공처 요금제 등급(ERD tmdb_provider.tier)
export type AdminProviderTier = 'STANDARD' | 'AD'

/* ===== SCR-14 OTT 구독상품·월 구독요금 ===== */

// 임시: 계약 반영 후 생성 타입으로 교체 — 월 구독요금 한 줄
export interface AdminProductPrice {
  id: number // 월 구독요금 줄 ID
  monthlyPrice: number // 월 구독요금(원, 정수)
  effectiveFrom: string // 적용 시작일('YYYY-MM-DD'). 같은 OTT 구독상품 안에서 겹치지 않는다
  lastVerifiedAt: string // 마지막 확인일('YYYY-MM-DD'). [요금 확인함]과 줄 수정이 오늘로 갱신한다. 90일을 넘으면 화면이 주의 태그를 붙인다
}

// 임시: 계약 반영 후 생성 타입으로 교체 — OTT 구독상품(단품) 하나와 월 구독요금 이력
export interface AdminProduct {
  id: number // OTT 구독상품 ID
  name: string // 이름. 예: "넷플릭스 스탠다드"
  productType: AdminProductType
  ottServiceId: number // 이 OTT 구독상품이 속한 서비스 ID(서비스 목록 API의 ottServiceId)
  active: boolean // false면 판매 종료(삭제하지 않고 남긴다, ERD)
  prices: AdminProductPrice[] // 월 구독요금 이력. 적용 시작일이 빠른 순(마지막 줄이 가장 최근)
}

// 임시: 계약 반영 후 생성 타입으로 교체 — OTT 구독상품 목록 응답
export interface AdminProductList {
  products: AdminProduct[]
}

// 임시: 계약 반영 후 생성 타입으로 교체 — OTT 구독상품 목록 응답 래퍼
export interface CommonResponseAdminProductList {
  success: boolean
  data: AdminProductList
}

// 임시: 계약 반영 후 생성 타입으로 교체 — OTT 구독상품 하나(등록·수정·월 구독요금 변경 응답) 래퍼
export interface CommonResponseAdminProduct {
  success: boolean
  data: AdminProduct
}

// 임시: 계약 반영 후 생성 타입으로 교체 — OTT 구독상품 등록 요청. 첫 월 구독요금을 함께 입력한다
export interface CreateAdminProductRequest {
  name: string // 이름(비어 있으면 안 된다)
  productType: AdminProductType
  ottServiceId: number // 속할 서비스 ID
  monthlyPrice: number // 첫 월 구독요금(원, 0 이상 정수)
  effectiveFrom: string // 첫 줄의 적용 시작일. 오늘 이하여야 한다(첫 줄 규칙)
}

// 임시: 계약 반영 후 생성 타입으로 교체 — OTT 구독상품 수정 요청. 보낸 필드만 바꾼다
export interface UpdateAdminProductRequest {
  name?: string // 이름 수정
  active?: boolean // false=판매 종료, true=판매 재개
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 월 구독요금 줄 추가 요청
export interface AddProductPriceRequest {
  monthlyPrice: number // 월 구독요금(원, 0 이상 정수)
  effectiveFrom: string // 적용 시작일. 마지막 줄의 시작일보다 늦어야 한다. 지난 날짜는 오류가 아니라 화면이 안내만 한다
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 월 구독요금 줄 수정 요청(가장 최근 줄만 가능). 추가 요청과 같은 모양이다
export type UpdateProductPriceRequest = AddProductPriceRequest

/* ===== SCR-15 제공처 매핑 ===== */

// 임시: 계약 반영 후 생성 타입으로 교체 — 제공처 매핑 상태. IGNORED(관련 없음)는 ERD에 아직 없다
// TODO(Task 032 안건): 관련 없음을 ERD에 어떻게 둘지 정한다(예: tmdb_provider에 상태 컬럼 추가). 지금 ERD는 "관심 없는 제공처는 ott_service_id NULL"이라 미매핑과 구분되지 않는다
export type AdminProviderMappingState = 'UNMAPPED' | 'MAPPED' | 'IGNORED'

// 임시: 계약 반영 후 생성 타입으로 교체 — TMDB 제공처 한 곳
export interface AdminProvider {
  providerId: number // TMDB 제공처 ID(그대로 PK). 예: 8(Netflix)
  tmdbName: string // TMDB 원본 라벨. 운영자 확인용이며 판정에는 쓰지 않는다
  ottServiceId: number | null // 연결된 서비스 ID. 미매핑·관련 없음이면 null
  tier: AdminProviderTier // 우리 판정 등급. 미매핑·관련 없음은 기본값 STANDARD
  firstSeenAt: string // 처음 발견된 시각(ISO 일시). 미매핑을 확인하는 데 쓴다
  mappingState: AdminProviderMappingState
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 제공처 목록 응답
export interface AdminProviderList {
  providers: AdminProvider[]
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 제공처 목록 응답 래퍼
export interface CommonResponseAdminProviderList {
  success: boolean
  data: AdminProviderList
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 제공처 하나(매핑 저장 응답) 래퍼
export interface CommonResponseAdminProvider {
  success: boolean
  data: AdminProvider
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 매핑 저장 요청
export interface SaveProviderMappingRequest {
  mappingState: AdminProviderMappingState // MAPPED면 ottServiceId 필수, IGNORED·UNMAPPED면 ottServiceId는 null
  ottServiceId: number | null
  tier: AdminProviderTier
}

/* ===== SCR-16 수집 ===== */

// 임시: 계약 반영 후 생성 타입으로 교체 — 수집 실행 상태. 화면 배지(RunDisplayStatus)와 같은 4가지다
// STOPPED(중단됨)는 서버가 갑자기 멈춰 기록만 "실행 중"으로 남은 실행을 서버가 판정해 내려준 값이라는 전제다(사용자 결정 2026-10-11)
export type CollectExecutionStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'STOPPED'

// 임시: 계약 반영 후 생성 타입으로 교체 — 수집 범위(TECH 3절 tier). MVP는 ALL만 실행할 수 있고 DAILY·WEEKLY는 2단계(O3)에서 연다
export type CollectTier = 'ALL' | 'DAILY' | 'WEEKLY'

// 임시: 계약 반영 후 생성 타입으로 교체 — 수집 실행 한 건
export interface CollectExecution {
  executionId: number // 실행 ID(Spring Batch jobExecutionId)
  targetDate: string // 수집 기준 날짜('YYYY-MM-DD'). 같은 날짜+범위가 실행 중이면 409
  tier: CollectTier
  status: CollectExecutionStatus
  startedAt: string // 시작 시각(ISO 일시)
  endedAt: string | null // 끝난 시각. 실행 중·중단됨이면 null. 복구로 실패가 된 실행도 null이다(서버가 복구 시 종료 시각을 기록할지는 Task 031 안건)
  processedCount: number // 처리한 작품 수(실행 중이면 지금까지)
  changedCount: number // 제공 상태가 바뀐 작품 수
  failureReason: string | null // 실패 사유. 실패가 아니면 null
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 수집 이력 응답
export interface CollectExecutionList {
  executions: CollectExecution[] // 최근 시작한 순
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 수집 이력 응답 래퍼
export interface CommonResponseCollectExecutionList {
  success: boolean
  data: CollectExecutionList
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 수집 실행 하나(복구 응답) 래퍼
export interface CommonResponseCollectExecution {
  success: boolean
  data: CollectExecution
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 실행에서 제공 상태가 바뀐 작품 한 건
export interface CollectChange {
  mediaType: 'movie' | 'tv' // 작품 종류(검색 목업의 mediaType과 같은 표기)
  tmdbId: number
  title: string // 작품 제목
  ottServiceId: number // 상태가 바뀐 서비스
  previousStatus: AvailabilityStatus | null // 이전 상태. null이면 이번에 처음 확인된 신규 작품
  currentStatus: AvailabilityStatus // 바뀐 뒤 상태
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 변경된 작품 목록 응답
export interface CollectChangeList {
  executionId: number
  changes: CollectChange[]
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 변경된 작품 목록 응답 래퍼
export interface CommonResponseCollectChangeList {
  success: boolean
  data: CollectChangeList
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 수동 실행 요청
export interface RunCollectRequest {
  targetDate: string // 수집 기준 날짜('YYYY-MM-DD')
  tier: CollectTier // MVP 화면은 ALL만 고를 수 있다
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 수동 실행 접수 응답(202). 실행은 비동기로 시작되고 결과는 이력에서 본다
export interface RunCollectAccepted {
  executionId: number
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 수동 실행 접수 응답 래퍼(202)
export interface CommonResponseRunCollectAccepted {
  success: boolean
  data: RunCollectAccepted
}
