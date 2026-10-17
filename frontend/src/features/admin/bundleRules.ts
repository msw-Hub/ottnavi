/*
 * 번들·광고형 1차안(Task 075)의 화면 규칙 모음이다. 컴포넌트 파일에 상수·함수를 함께 export하면 빠른 새로고침(react-refresh)이
 * 깨져 린트가 막으므로 따로 둔다(priceRules.ts와 같은 방식).
 */
import type { ProviderTier } from '@/api/generated/model'
import type { ProductComponentDraft } from '@/features/admin/mockTypes'

// 등급 선택지 글자. 제공처 매핑 화면의 등급 글자(STANDARD(광고 없음) / AD(광고형))와 뜻을 맞췄다
export const TIER_OPTION_LABELS: Record<ProviderTier, string> = {
  STANDARD: '일반(광고 없음)',
  AD: '광고형',
}

// 등급 고르기 칸(select)의 모양. 관리자 화면의 다른 select(상품 등록의 서비스 칸)와 같은 테두리·포커스 링을 쓴다
export const TIER_SELECT_CLASS =
  'h-9 rounded-lg border border-input bg-background px-2 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50'

/**
 * 번들 구성을 검사해 문제가 있으면 안내 문장을, 없으면 null을 돌려준다.
 * 2곳 이상이어야 하는 이유: 1곳뿐이면 단품과 같다(PRD FR-19 번들 = 여러 서비스를 묶은 하나의 가상 서비스).
 */
export function checkBundleComponents(components: ProductComponentDraft[]): string | null {
  return components.length >= 2 ? null : '번들은 서비스를 2곳 이상 골라 주세요.'
}
