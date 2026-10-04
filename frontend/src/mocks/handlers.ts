import type { RequestHandler } from 'msw'

// MSW 요청 핸들러 모음. 목업 모드(VITE_USE_MOCK=true)에서 브라우저가 이 핸들러로 /api 요청을 가로챈다.
// 현재 계약(docs/api/openapi.yaml)에 operation이 없어 orval이 만든 핸들러가 없다.
// operation이 생기면(Task 019, 031) 태그별 생성 핸들러를 가져와 아래 배열에 펼친다.
// 예: import { getCatalogMock } from '@/api/generated/catalog/catalog.msw'
//     export const handlers: RequestHandler[] = [...getCatalogMock()]
// 화면 확인용 현실적인 데이터(실제 작품명, 7개 서비스, 3개월 플랜)는 src/mocks/fixtures/에 직접 작성한다.
export const handlers: RequestHandler[] = []
