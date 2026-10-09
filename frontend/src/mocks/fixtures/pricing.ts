/*
 * 서비스별 상품·기본 요금 목업 데이터다(Task 028, SCR-09 요금 확인).
 *
 * 주의: 금액은 화면 확인용 예시이며 실제 요금과 다를 수 있다. 실제 요금은 관리자가 등록한 요금표(Task 029·계약 이후)를 따른다.
 * 상품은 서비스마다 하나(단품)로 단순화했다. 번들 상품은 2단계 이후에 다룬다.
 */

export interface MockProduct {
  ottServiceId: number // 서비스 내부 ID
  productId: number // 상품 ID
  productName: string // 상품(요금제) 이름
  adminPrice: number // 관리자 기본 월 요금(원)
}

// 요금 기준일(관리자 요금표의 적용 시작일)
export const MOCK_PRICE_EFFECTIVE_FROM = '2026-10-01'

export const MOCK_PRODUCTS: MockProduct[] = [
  { ottServiceId: 1, productId: 101, productName: '넷플릭스 스탠다드', adminPrice: 13500 },
  { ottServiceId: 2, productId: 102, productName: '디즈니+ 스탠다드', adminPrice: 9900 },
  { ottServiceId: 3, productId: 103, productName: '애플 TV+', adminPrice: 9900 },
  { ottServiceId: 4, productId: 104, productName: '티빙 스탠다드', adminPrice: 9500 },
  { ottServiceId: 5, productId: 105, productName: '웨이브 스탠다드', adminPrice: 10900 },
  { ottServiceId: 6, productId: 106, productName: '왓챠 프리미엄', adminPrice: 7900 },
  { ottServiceId: 7, productId: 107, productName: '쿠팡플레이', adminPrice: 7900 },
]

// 서비스 ID로 상품을 찾는다. 없으면 undefined
export function findMockProduct(ottServiceId: number): MockProduct | undefined {
  return MOCK_PRODUCTS.find((product) => product.ottServiceId === ottServiceId)
}
