/*
 * 관리자 상품 화면(SCR-14)의 번들·광고형 1차안(Task 075) 정적 샘플이다. 주소에 ?productsPreview=bundle을 붙인 목업 모드에서만 쓴다.
 *
 * - 실제 목업 API(MSW)의 상품 목록 뒤에 이 샘플 상품 3개를 덧붙여 "번들·광고형이 섞인 목록"을 보여 준다.
 * - 상품 이름·금액은 화면 확인용으로 지어낸 값이다(실제 판매 중인 결합 상품·가격이 아니다).
 * - 샘플 상품 ID(9001~)는 목업 API가 모르는 값이라 이 상품의 요금 추가·수정 버튼을 누르면 "찾을 수 없음" 오류가 난다(1차안 한계).
 * - 계약(Task 076)이 반영되면 이 파일은 지운다. 그때는 API 응답에 구성(components)이 함께 온다.
 */
import type { AdminProduct } from '@/api/generated/model'
import type { ProductCompositionDraft } from '@/features/admin/mockTypes'

/*
 * 샘플 상품. 계약의 AdminProduct 모양에 맞추느라 productType은 SINGLE, ottServiceId는 첫 구성 서비스로 채웠다.
 * 화면은 이 두 값 대신 아래 SAMPLE_COMPOSITIONS의 구성을 읽는다(계약이 바뀌기 전 임시 방편).
 */
export const BUNDLE_SAMPLE_PRODUCTS: AdminProduct[] = [
  {
    productId: 9001,
    name: '넷플릭스 광고형 스탠다드',
    productType: 'SINGLE',
    ottServiceId: 1,
    isActive: true,
    prices: [
      {
        priceId: 90011,
        monthlyPrice: 7000,
        effectiveFrom: '2026-06-01',
        lastVerifiedAt: '2026-10-10T09:00:00+09:00',
      },
    ],
  },
  {
    productId: 9002,
    name: '티빙·웨이브 결합 이용권',
    productType: 'SINGLE',
    ottServiceId: 4,
    isActive: true,
    prices: [
      {
        priceId: 90021,
        monthlyPrice: 16000,
        effectiveFrom: '2026-09-01',
        lastVerifiedAt: '2026-10-12T09:00:00+09:00',
      },
    ],
  },
  {
    productId: 9003,
    name: '디즈니+·티빙 광고형 번들',
    productType: 'SINGLE',
    ottServiceId: 2,
    isActive: true,
    prices: [
      {
        priceId: 90031,
        monthlyPrice: 11900,
        effectiveFrom: '2026-08-01',
        lastVerifiedAt: '2026-06-20T09:00:00+09:00', // 90일이 지나 "요금 확인일 오래됨" 태그도 함께 보인다
      },
    ],
  },
]

// 샘플 상품 ID → 구성. 광고형 단품 1개, 일반 등급 번들 1개, 광고형 번들 1개다
export const SAMPLE_COMPOSITIONS = new Map<number, ProductCompositionDraft>([
  [9001, { productType: 'SINGLE', components: [{ ottServiceId: 1, tier: 'AD' }] }],
  [
    9002,
    {
      productType: 'BUNDLE',
      components: [
        { ottServiceId: 4, tier: 'STANDARD' },
        { ottServiceId: 5, tier: 'STANDARD' },
      ],
    },
  ],
  [
    9003,
    {
      productType: 'BUNDLE',
      components: [
        { ottServiceId: 2, tier: 'AD' },
        { ottServiceId: 4, tier: 'AD' },
      ],
    },
  ],
])

/**
 * 상품의 구성을 돌려준다. 구성 표(compositions)에 있으면 그 값, 없으면 계약 값으로 "단품 · 서비스 1곳 · 일반"을 만든다.
 * 계약에 등급이 없어도 일반(STANDARD)으로 보는 이유: MVP는 STANDARD 단품만 등록할 수 있다(PRD FR-19, ERD product_component).
 */
export function getProductComposition(
  product: AdminProduct,
  compositions: Map<number, ProductCompositionDraft> | undefined,
): ProductCompositionDraft {
  return (
    compositions?.get(product.productId) ?? {
      productType: 'SINGLE',
      components: [{ ottServiceId: product.ottServiceId, tier: 'STANDARD' }],
    }
  )
}
