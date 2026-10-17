/*
 * 요금 확인 화면(SCR-09)의 번들·광고형 표시 1차안(Task 075) 정적 샘플이다. 주소에 ?pricingPreview=bundle을 붙인 목업 모드에서만 쓴다.
 *
 * - 목업 API(MSW)의 요금 목록 뒤에 광고형 단품 1개, 번들 2개를 덧붙여 "번들·광고형이 섞인 요금표"를 보여 준다.
 * - 상품 이름·금액은 관리자 화면 샘플(features/admin/bundleSampleData.ts)과 같게 맞췄다(기능 폴더끼리 import하지 않아 따로 둔다).
 * - 샘플 상품 ID(9001~)는 목업 API가 모르는 값이라, 이 줄에서 "적용"을 누르면 "요금 정보를 찾을 수 없습니다" 오류가 난다(1차안 한계).
 * - 계약(Task 076)이 반영되면 이 파일은 지운다. 그때는 요금 응답에 구성이 함께 온다.
 */
import type { PricingComposition, PricingSampleRow } from '@/features/pricing/mockTypes'

export const PRICING_BUNDLE_SAMPLE_ROWS: PricingSampleRow[] = [
  {
    price: {
      ottServiceId: 1,
      productId: 9001,
      productName: '넷플릭스 광고형 스탠다드',
      adminPrice: 7000,
      userPrice: null,
      appliedPrice: 7000,
      priceSource: 'ADMIN',
      subscriptionStatus: 'NOT_SUBSCRIBED',
      effectiveFrom: '2026-06-01',
      lastVerifiedAt: '2026-10-10T09:00:00+09:00',
    },
    composition: { productType: 'SINGLE', components: [{ ottServiceId: 1, tier: 'AD' }] },
  },
  {
    price: {
      ottServiceId: 4, // 계약이 서비스 하나만 받아 첫 구성 서비스로 채웠다. 화면은 composition을 읽는다
      productId: 9002,
      productName: '티빙·웨이브 결합 이용권',
      adminPrice: 16000,
      userPrice: null,
      appliedPrice: 16000,
      priceSource: 'ADMIN',
      subscriptionStatus: 'NOT_SUBSCRIBED',
      effectiveFrom: '2026-09-01',
      lastVerifiedAt: '2026-10-12T09:00:00+09:00',
    },
    composition: {
      productType: 'BUNDLE',
      components: [
        { ottServiceId: 4, tier: 'STANDARD' },
        { ottServiceId: 5, tier: 'STANDARD' },
      ],
    },
  },
  {
    price: {
      ottServiceId: 2,
      productId: 9003,
      productName: '디즈니+·티빙 광고형 번들',
      adminPrice: 11900,
      userPrice: 10900,
      appliedPrice: 10900,
      priceSource: 'USER_OVERRIDE',
      subscriptionStatus: 'SUBSCRIBED',
      effectiveFrom: '2026-08-01',
      lastVerifiedAt: '2026-06-20T09:00:00+09:00', // 90일이 지나 "요금이 달라졌을 수 있어요" 안내도 함께 보인다
    },
    composition: {
      productType: 'BUNDLE',
      components: [
        { ottServiceId: 2, tier: 'AD' },
        { ottServiceId: 4, tier: 'AD' },
      ],
    },
  },
]

// 상품 ID → 구성(샘플만). 실제 목업 API 상품은 여기 없고, 화면은 이때 "단품 · 일반"으로 본다
export const PRICING_SAMPLE_COMPOSITIONS = new Map<number, PricingComposition>(
  PRICING_BUNDLE_SAMPLE_ROWS.map((row) => [row.price.productId, row.composition]),
)
