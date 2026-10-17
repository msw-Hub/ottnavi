/*
 * 요금 확인 화면(SCR-09)의 번들·광고형 표시 1차안(Task 075)이 쓰는 임시 타입이다(frontend.md "목업 단계의 임시 타입 예외").
 *
 * 왜 임시인가: 계약의 요금 항목(ServicePrice)에는 상품 유형과 구성(어느 서비스를 어떤 등급으로 보는지)이 없다.
 * 계약 변경(Task 076: ServicePrice에 productType·components 추가)이 반영되면 이 파일을 지우고 생성 타입으로 바꾼다.
 * 이미 계약에 있는 값(요금 항목 ServicePrice, 등급 ProviderTier)은 처음부터 생성 타입을 쓴다.
 */
import type { ProviderTier, ServicePrice } from '@/api/generated/model'

// 임시: 계약 반영 후 생성 타입으로 교체
// 요금 항목 상품의 구성. 단품은 구성 1개, 번들은 2개 이상(서비스 ID 순)
export interface PricingComposition {
  productType: 'SINGLE' | 'BUNDLE' // 단품 / 번들
  components: { ottServiceId: number; tier: ProviderTier }[] // 볼 수 있는 서비스와 등급(STANDARD 일반 / AD 광고형)
}

// 임시: 계약 반영 후 생성 타입으로 교체
// 1차안 샘플 한 줄: 계약 요금 항목 + 구성
export interface PricingSampleRow {
  price: ServicePrice
  composition: PricingComposition
}
