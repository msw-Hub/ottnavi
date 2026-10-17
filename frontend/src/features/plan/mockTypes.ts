/*
 * 플랜 변경 내역(SCR-11, Task 068) 1차안이 쓰는 임시 타입이다(frontend.md "목업 단계의 임시 타입 예외").
 *
 * 왜 임시인가: 변경 내역 API는 아직 계약(docs/api/openapi.yaml)에 없다. 1차안은 화면 모양을 먼저 정해 계약 초안의 근거를 만드는 단계라,
 * 화면이 실제로 쓰는 값의 모양만 여기 적고 정적 샘플(historySampleData.ts)로 채운다.
 * 계약이 정해지면 이 파일을 지우고 생성 타입으로 바꾼다(그때 찾기 쉽게 이 파일 하나에만 둔다).
 * 이미 계약에 있는 값(플랜 유형 PlanType)은 처음부터 생성 타입을 쓴다.
 */
import type { PlanType } from '@/api/generated/model'

// 임시: 계약 반영 후 생성 타입으로 교체
// 플랜이 바뀐 이유의 종류(사용자 결정 1차 4종). 찜·예산 변경은 이유로 쓰지 않는다
export type PlanChangeReasonType =
  | 'PRICE_CHANGED' // 구독 상품의 요금이 바뀜
  | 'TITLE_LEFT_SERVICE' // 작품이 어느 서비스에서 빠짐(확인되지 않게 됨)
  | 'TITLE_JOINED_SERVICE' // 작품이 어느 서비스에 새로 들어옴(있음으로 확인됨)
  | 'MARKED_WATCHED' // 사용자가 "다 봤음"으로 체크해 계산에서 빠짐

// 임시: 계약 반영 후 생성 타입으로 교체
// 바뀐 이유 한 건
export interface PlanChangeReason {
  reasonType: PlanChangeReasonType
  ottServiceId: number | null // 관련 서비스(다 봤음 체크처럼 서비스와 무관하면 null)
  title: string | null // 관련 작품 제목(가격 변경처럼 작품과 무관하면 null)
  seasonNumber: number | null // 관련 시즌 번호(영화·시즌 없는 작품은 null)
  description: string // 한 줄 설명(서버가 만든 문장). 예: "'카지노 시즌 1'이 10월부터 디즈니+에서 볼 수 있는 것으로 확인됐어요"
}

// 임시: 계약 반영 후 생성 타입으로 교체
// 달별 구독 변화에 보이는 상품 한 개(이름과 그 달 적용 금액)
export interface PlanChangeProduct {
  productId: number
  ottServiceId: number
  productName: string // 상품 이름(예: "디즈니+ 스탠다드")
}

// 임시: 계약 반영 후 생성 타입으로 교체
// 한 달의 구독 변화. 두 플랜이 함께 다루는 달 중 구독 상품이나 비용이 달라진 달, 그리고 새 플랜에만 있는 달(새로 생긴 셋째 달 등)이 온다
// 이전: 함께 다루는 달 중 달라진 달만 왔다(새 플랜에만 있는 달 표시는 2026-10-17 사용자 결정)
export interface PlanMonthChange {
  month: string // 'YYYY-MM'
  isConfirmed: boolean // 재계산 당시 새 플랜의 첫 달(곧 결제하는 달)인지. 화면은 "결제 달", 나머지 달은 "예상"이다
  previousProducts: PlanChangeProduct[] // 이전 플랜의 이 달 구독 상품(빈 배열 = 구독 안 함, 또는 이전 플랜에 없던 달)
  currentProducts: PlanChangeProduct[] // 새 플랜의 이 달 구독 상품
  // 이전 플랜의 이 달 비용(원). 이전 플랜에 없던 달이면 null이다(이때 previousProducts는 빈 배열).
  // 0과 null을 나눈 이유: 0은 "이전에도 이 달이 있었고 구독하지 않았음", null은 "이전 플랜이 이 달을 다루지 않았음"이라 화면 표시가 다르다
  // 이전: previousCost: number
  previousCost: number | null
  currentCost: number // 새 플랜의 이 달 비용(원)
}

// 임시: 계약 반영 후 생성 타입으로 교체
// 작품 변화의 종류
export type PlanTitleChangeType =
  | 'ADDED' // 새로 플랜에 들어감
  | 'REMOVED' // 플랜에서 빠짐(다 봤음, 볼 곳 없음, 예산·시간 부족 등)
  | 'MOVED_MONTH' // 같은 서비스에서 보는 달만 바뀜
  | 'CHANGED_SERVICE' // 보는 서비스가 바뀜(달이 같이 바뀌어도 이 값)

// 임시: 계약 반영 후 생성 타입으로 교체
// 작품을 어느 달·어느 서비스에서 보는지
export interface PlanTitlePlacement {
  month: string // 'YYYY-MM'
  ottServiceId: number
}

// 임시: 계약 반영 후 생성 타입으로 교체
// 작품(시즌) 하나의 변화
export interface PlanTitleChange {
  changeType: PlanTitleChangeType
  title: string // 작품 제목
  seasonNumber: number | null // 시즌 번호(영화·시즌 없는 작품은 null)
  previous: PlanTitlePlacement | null // 이전 플랜에서의 자리(ADDED면 null)
  current: PlanTitlePlacement | null // 새 플랜에서의 자리(REMOVED면 null)
}

// 임시: 계약 반영 후 생성 타입으로 교체
// 변경 내역 한 건(매월 재계산으로 플랜이 바뀐 한 번). 바뀌지 않은 달은 기록하지 않는다(사용자 결정 3)
export interface PlanChangeHistoryEntry {
  changeId: number // 목록 key와 펼침 영역 id에 쓴다
  changedAt: string // 재계산해 플랜이 바뀐 시각(ISO-8601)
  planType: PlanType // 저장해 둔 플랜 유형(재계산도 같은 유형으로 한다)
  summary: string // 한 줄 요약(서버가 만든 문장). 예: "10월 구독이 넷플릭스에서 디즈니+로 바뀌었어요"
  reasons: PlanChangeReason[] // 바뀐 이유(1건 이상)
  monthChanges: PlanMonthChange[] // 달별 구독 변화(구독이 그대로이고 작품만 옮겨졌다면 빈 배열일 수 있다)
  titleChanges: PlanTitleChange[] // 작품 변화
  // 절감액 변화는 두 플랜이 다루는 3개월이 달라 비교가 부정확해 화면에서 뺐다(2026-10-17 사용자 결정). 계약 초안에도 넣지 않는다
  // previousSavings: number // 이전 플랜의 절감액(원, 전체 구독 비용 − 플랜 총비용)
  // currentSavings: number // 새 플랜의 절감액(원)
  dataAsOf: string // 이 재계산에 쓴 데이터의 기준일('YYYY-MM-DD')
}
