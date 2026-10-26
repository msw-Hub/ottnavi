/*
 * 번들·저가형 화면 규칙 모음이다(Task 075 1차안 → Task 076 계약 반영). 컴포넌트 파일에 상수·함수를 함께 export하면 빠른 새로고침(react-refresh)이
 * 깨져 린트가 막으므로 따로 둔다(priceRules.ts와 같은 방식).
 */

/* 이전(Task 075 1차안): 서비스마다 등급(일반/광고형)을 고르는 select의 글자·모양이었다.
 * Task 076에서 등급 선택을 없애고 상품 단위 "저가형" 체크로 바꿨다(저가형은 볼 수 있는 작품을 줄이지 않는다, 2026-10-17 확정).
export const TIER_OPTION_LABELS: Record<ProviderTier, string> = { STANDARD: '일반(광고 없음)', AD: '광고형' }
export const TIER_SELECT_CLASS = 'h-9 rounded-lg border border-input bg-background px-2 text-sm ...'
*/

// 판매 중 번들 상한(계약 createAdminProduct·updateAdminProduct). 넘기면 서버가 400 VALIDATION_FAILED를 준다
export const MAX_ACTIVE_BUNDLES = 13

// 번들 상한 오류 문장. 서버 fieldErrors의 field가 등록이면 productType, 판매 재개면 isActive로 온다(계약)
export const BUNDLE_LIMIT_MESSAGES = {
  create: `판매 중인 번들이 이미 ${MAX_ACTIVE_BUNDLES}개라 더 등록할 수 없어요. 다른 번들을 판매 종료한 뒤 등록해 주세요.`,
  reactivate: `판매 중인 번들이 이미 ${MAX_ACTIVE_BUNDLES}개라 판매를 다시 시작할 수 없어요. 다른 번들을 먼저 판매 종료해 주세요.`,
} as const

// "저가형" 체크 아래 설명. 등록·수정 대화상자가 같은 문장을 쓴다
export const LOW_COST_HELP =
  '광고형·베이직처럼 같은 서비스의 더 싼 요금제예요. 회원이 "가장 저렴한 요금제로 계산"을 켰을 때만 계산에 써요.'

/**
 * 번들 구성을 검사해 문제가 있으면 안내 문장을, 없으면 null을 돌려준다.
 * 2곳 이상이어야 하는 이유: 1곳뿐이면 단품과 같다(계약 CreateAdminProductRequest.ottServiceIds: 번들은 서로 다른 2개 이상).
 */
export function checkBundleServices(ottServiceIds: number[]): string | null {
  return ottServiceIds.length >= 2 ? null : '번들은 서비스를 2곳 이상 골라 주세요.'
}
