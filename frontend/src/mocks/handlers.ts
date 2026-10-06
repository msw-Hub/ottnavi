import type { RequestHandler } from 'msw'
import { getProductMock } from '@/api/generated/product/product.msw'

// MSW 요청 핸들러 모음. 목업 모드(VITE_USE_MOCK=true)에서 브라우저가 이 핸들러로 /api 요청을 가로챈다.
// orval이 계약(docs/api/openapi.yaml)의 operation마다 태그별 핸들러를 생성하고, 여기서 한 배열로 펼친다.
// 생성된 목업 응답은 계약의 example을 그대로 쓴다. 그래서 서비스 목록(listOttServices)은 별도 픽스처 없이
// 실제 7개 서비스 값으로 응답한다(Task 019).
// 새 태그의 operation이 생기면(Task 031 등) 아래 배열에 그 태그의 목업을 추가한다.
// 예: import { getCatalogMock } from '@/api/generated/catalog/catalog.msw'
//     export const handlers: RequestHandler[] = [...getProductMock(), ...getCatalogMock()]
// 화면 확인용 현실적인 데이터(실제 작품명, 3개월 플랜)는 src/mocks/fixtures/에 직접 작성한다.
export const handlers: RequestHandler[] = [...getProductMock()]
