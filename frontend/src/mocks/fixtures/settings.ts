/*
 * 내 설정 목업 데이터다(Task 028). 온보딩을 마친 회원의 기본 설정으로, 두 곳에서 쓴다.
 * - 개발용 토글(switchMockRole)로 "이미 가입해 온보딩을 마친 회원"이 됐을 때 GET /me/settings가 돌려주는 값
 * - 플랜 계산이 쓰는 월 예산·월 시청 시간의 기본값
 * 서비스 ID는 서비스 목록 API(계약 example)의 ottServiceId와 같다: 1 넷플릭스, 2 디즈니+, 3 애플 TV+, 4 티빙, 5 웨이브, 6 왓챠, 7 쿠팡플레이.
 */
import type { UserSettings } from '@/features/onboarding/mockTypes'

// 서비스 7곳의 ID. 목록 순서가 곧 화면 순서다
export const MOCK_SERVICE_IDS = [1, 2, 3, 4, 5, 6, 7] as const

// 온보딩을 마친 회원의 기본 설정: 넷플릭스 구독 중(결제일 15일), 티빙은 가족 공유로 무료 이용, 나머지는 미구독, 예산 2만 원, 월 15시간(프리셋 '보통')
// 예전 값은 20시간(1200분, CUSTOM)이었다. 프리셋(8·15·30시간)에 없는 값이라 설정 화면이 '직접 입력'으로 열려서 프리셋 값으로 맞췄다
export const DEFAULT_USER_SETTINGS: UserSettings = {
  isOnboardingDone: true,
  subscriptions: MOCK_SERVICE_IDS.map((ottServiceId) => {
    if (ottServiceId === 1) {
      return { ottServiceId, status: 'SUBSCRIBED', planName: '스탠다드', billingDay: 15 }
    }
    if (ottServiceId === 4) {
      return { ottServiceId, status: 'FREE', planName: null, billingDay: null }
    }
    return { ottServiceId, status: 'NOT_SUBSCRIBED', planName: null, billingDay: null }
  }),
  monthlyBudget: 20000,
  monthlyWatchMinutes: 900,
  watchPreset: 'NORMAL',
  weeklyInput: null,
}

// 온보딩을 아직 하지 않은 회원에게 돌려주는 빈 설정. 화면은 모든 칸을 기본값으로 채워 보인다
export const EMPTY_USER_SETTINGS: UserSettings = {
  isOnboardingDone: false,
  subscriptions: [],
  monthlyBudget: null,
  monthlyWatchMinutes: null,
  watchPreset: null,
  weeklyInput: null,
}
