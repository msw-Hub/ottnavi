/*
 * 관리자 상품 화면(SCR-14)의 번들·광고형 1차안(Task 075)이 쓰는 임시 타입이다(frontend.md "목업 단계의 임시 타입 예외").
 *
 * 왜 임시인가: 계약(docs/api/openapi.yaml)의 OTT 구독상품은 아직 단품(ProductType = SINGLE)과 서비스 하나(ottServiceId)만 안다.
 * 번들(여러 서비스를 한 번에 결제하는 상품)과 등급(일반/광고형)을 담을 자리가 없어, 화면 모양을 먼저 정하려고 여기 적는다.
 * 계약 변경(Task 076: ProductType에 BUNDLE 추가, 상품에 구성 목록 components 추가)이 반영되면 이 파일을 지우고 생성 타입으로 바꾼다.
 * 이미 계약에 있는 값(등급 ProviderTier = STANDARD | AD)은 처음부터 생성 타입을 쓴다.
 */
import type { ProviderTier } from '@/api/generated/model'

// 임시: 계약 반영 후 생성 타입으로 교체
// 상품 유형. SINGLE 단품(서비스 1곳) / BUNDLE 번들(서비스 2곳 이상을 한 번에 결제). 계약 ProductType은 지금 SINGLE만 있다
export type ProductKind = 'SINGLE' | 'BUNDLE'

// 임시: 계약 반영 후 생성 타입으로 교체
// 상품에 들어 있는 서비스 한 곳과 그 등급(ERD product_component 한 행: product_id + ott_service_id + tier)
export interface ProductComponentDraft {
  ottServiceId: number // 볼 수 있는 서비스
  tier: ProviderTier // STANDARD 일반 / AD 광고형(광고형은 그 서비스 작품 중 광고형 제공으로 확인된 것만 볼 수 있다고 계산한다)
}

// 임시: 계약 반영 후 생성 타입으로 교체
// 상품의 구성(유형 + 들어 있는 서비스 목록). 단품은 구성이 1개, 번들은 2개 이상이다
export interface ProductCompositionDraft {
  productType: ProductKind
  components: ProductComponentDraft[] // 서비스 ID 순
}
