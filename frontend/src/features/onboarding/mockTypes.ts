/*
 * 임시 타입 파일 (Task 028, SCR-07 온보딩·내 설정).
 *
 * 내 설정 API(GET·PUT /me/settings)는 아직 계약(docs/api/openapi.yaml)에 없어 orval 생성 타입이 없다.
 * frontend.md "목업 단계의 임시 타입 예외"에 따라 이 파일 한 곳에만 임시로 둔다.
 * Task 031에서 계약에 operation을 추가하고 `npm run api:generate`로 생성 타입을 만든 뒤, 이 파일을 지운다.
 * 경로와 필드는 "계약이 이럴 것"이라는 제안이다. Task 031에서 바뀔 수 있다.
 */
import type { ServiceSubscriptionStatus } from '@/components/common/displayTypes'

export type { ServiceSubscriptionStatus }

// 임시: 계약 반영 후 생성 타입으로 교체 — 시청 시간 프리셋(PRD 5.4). 가볍게 8시간 / 보통 15시간 / 많이 30시간 / 직접 입력(환산 포함)
export type WatchPreset = 'LIGHT' | 'NORMAL' | 'HEAVY' | 'CUSTOM'

// 임시: 계약 반영 후 생성 타입으로 교체 — 서비스 하나의 이용 상태(ERD user_subscription)
export interface UserSubscription {
  ottServiceId: number // 서비스 내부 ID(서비스 목록 API의 ottServiceId)
  status: ServiceSubscriptionStatus // 구독 중 / 무료 이용 / 미구독 / 모름
  planName: string | null // 요금제 이름(예: "스탠다드"). SUBSCRIBED일 때만 값이 있고 그 밖에는 null
  billingDay: number | null // 결제일(1~31). SUBSCRIBED일 때만 값이 있고 기본 1일. 그 밖에는 null
}

// 임시: 계약 반영 후 생성 타입으로 교체 — "주 N회 × 회당 M시간" 환산 입력값. R11-18 대안(안 b)에서만 서버가 저장한다
export interface WeeklyWatchInput {
  timesPerWeek: number // 주당 시청 횟수
  hoursPerTime: number // 회당 시청 시간(시간 단위, 소수 가능)
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 내 설정(조회 응답)
export interface UserSettings {
  isOnboardingDone: boolean // 온보딩을 마쳤는지. false면 아래 값들은 비어 있다
  subscriptions: UserSubscription[] // 서비스별 이용 상태. 비어 있으면 아직 입력 전
  monthlyBudget: number | null // 월 예산(원). 입력 전이면 null
  monthlyWatchMinutes: number | null // 월 시청 시간(분). 입력 전이면 null
  watchPreset: WatchPreset | null // 시청 시간을 어떻게 골랐는지. 입력 전이면 null
  weeklyInput: WeeklyWatchInput | null // 환산 입력값. 안 a(기본)는 항상 null, 안 b는 환산으로 골랐을 때만 값이 있다
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 내 설정 저장 요청(PUT /me/settings). 처음 저장이 곧 온보딩 완료다
export interface SaveUserSettingsRequest {
  subscriptions: UserSubscription[]
  monthlyBudget: number // 월 예산(원). 0 이상 정수, 필수
  monthlyWatchMinutes: number // 월 시청 시간(분). 120 이상 정수, 필수(PRD FR-10)
  watchPreset: WatchPreset // 환산·직접 입력으로 골랐으면 CUSTOM
  weeklyInput: WeeklyWatchInput | null // 환산 입력값. 서버가 안 a에서는 버리고 안 b에서만 저장한다
}

// 임시: 계약 반영 후 생성 타입으로 교체 — 성공 응답 래퍼(CommonResponse)
export interface CommonResponseUserSettings {
  success: boolean
  data: UserSettings
}
