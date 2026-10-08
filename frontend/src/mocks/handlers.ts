import { http, HttpResponse, type RequestHandler } from 'msw'
import { getProductMock } from '@/api/generated/product/product.msw'
import type { ProblemDetail } from '@/api/generated/model'

// MSW 요청 핸들러 모음. 목업 모드(VITE_USE_MOCK=true)에서 브라우저가 이 핸들러로 /api 요청을 가로챈다.
// orval이 계약(docs/api/openapi.yaml)의 operation마다 태그별 핸들러를 생성하고, 여기서 한 배열로 펼친다.
// 생성된 목업 응답은 계약의 example을 그대로 쓴다. 그래서 서비스 목록(listOttServices)은 별도 픽스처 없이
// 실제 7개 서비스 값으로 응답한다(Task 019).
// 새 태그의 operation이 생기면(Task 031 등) 아래 배열에 그 태그의 목업을 추가한다.
// 예: import { getCatalogMock } from '@/api/generated/catalog/catalog.msw'
//     export const handlers: RequestHandler[] = [...scenarioHandlers, ...getProductMock(), ...getCatalogMock()]
// 화면 확인용 현실적인 데이터(실제 작품명, 3개월 플랜)는 src/mocks/fixtures/에 직접 작성한다.

// 오류 상황을 재현하는 목업 시나리오를 고르는 쿼리 파라미터 이름. 예: /api/public/ott-services?mockScenario=rate-limit
// 계약에 없는 확인용 API를 새로 만들지 않고, 이미 있는 API에 이 파라미터를 붙여 오류 응답을 받는다.
export const MOCK_SCENARIO_PARAM = 'mockScenario'

// 요청 URL에 지정한 시나리오가 붙어 있는지 확인한다
function hasMockScenario(request: Request, scenario: string): boolean {
  return new URL(request.url).searchParams.get(MOCK_SCENARIO_PARAM) === scenario
}

/*
 * 시나리오 핸들러. 생성 핸들러(getProductMock)보다 배열 앞에 둔다.
 * MSW는 배열 앞에서부터 주소가 맞는 핸들러를 찾고, 핸들러가 아무것도 돌려주지 않으면(undefined) 다음 핸들러로 넘어간다.
 * 그래서 시나리오 파라미터가 없을 때는 아무것도 돌려주지 않아 원래의 생성 목업(정상 응답)이 그대로 쓰인다.
 */
const scenarioHandlers: RequestHandler[] = [
  // rate-limit: 서비스 목록(listOttServices)이 429 요청 제한 오류를 돌려준다(Task 025, 429 토스트 확인용)
  http.get('/api/public/ott-services', ({ request }) => {
    if (!hasMockScenario(request, 'rate-limit')) return

    // 백엔드 ProblemDetail과 같은 모양(docs/api/error-codes.md). 생성 타입으로 검사해 계약과 어긋나지 않게 한다
    const body: ProblemDetail = {
      title: 'Too Many Requests',
      status: 429,
      detail: '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.',
      instance: '/api/public/ott-services',
      errorCode: 'RATE_LIMIT_EXCEEDED',
    }
    // HttpResponse.json은 기본 Content-Type이 application/json이다. 실제 백엔드처럼 problem+json으로 명시해야
    // 인터셉터(api/http.ts)가 ProblemDetail로 인식한다
    return HttpResponse.json(body, {
      status: 429,
      headers: { 'Content-Type': 'application/problem+json' },
    })
  }),
]

export const handlers: RequestHandler[] = [...scenarioHandlers, ...getProductMock()]
