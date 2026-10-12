/*
 * 목업 시나리오(오류·예외 상황 재현) 도우미다.
 *
 * 왜 필요한가: 외부 수집 실패처럼 "서버가 특별한 응답을 주는 상황"을 화면에서 눈으로 보려면, 사용자가 주소창에
 * `?mockScenario=collect-failed`를 붙였을 때 그 값을 API 요청에도 실어 보내야 한다.
 *
 * 값을 요청 "헤더"로 싣는 이유(Task 031 6-0): 예전에는 API 요청 주소의 쿼리 파라미터로 보냈는데, 이 값은 API 계약
 * (docs/api/openapi.yaml)에 없는 화면 검토용 전환 값이다. 계약에 없는 파라미터를 요청 주소에 섞으면 생성된 훅의
 * 파라미터 타입과 어긋나고 실제 서버로 새어 나갈 위험도 있다. 그래서 목업 모드일 때만 공통 HTTP 설정(api/http.ts의 요청
 * 인터셉터 → api/mockHeaders.ts)이 이 값을 헤더(X-Mock-*)로 붙이고, MSW 핸들러(mocks/handlerUtils.ts)가 헤더를 읽는다.
 * 운영 빌드(VITE_USE_MOCK이 true가 아님)에서는 getMockRequestHeaders가 항상 빈 객체라 헤더가 붙지 않는다.
 * 화면 주소의 ?mockScenario= 등은 화면을 고르는 용도로 그대로 남고, 화면 사이 이동 링크에 이어 붙일 때도 쓴다.
 *
 * 이 파일을 mocks/가 아닌 lib/에 둔 이유: features의 훅이 import하는데, mocks/handlers.ts는 msw 코드를 끌고 온다.
 * 운영 번들에 msw가 섞이지 않게 msw를 모르는 이 작은 파일만 따로 뒀다.
 */

// 오류 상황을 재현하는 목업 시나리오를 고르는 쿼리 파라미터 이름. 예: /?q=카지노&mockScenario=collect-failed
export const MOCK_SCENARIO_PARAM = 'mockScenario'

/**
 * 목업 모드(VITE_USE_MOCK=true)인지 돌려준다. 목업 전용 화면 요소(로그인 상태 전환 토글 등)를 보일지 정할 때 쓴다.
 *
 * 함수로 둔 이유: 테스트가 vi.stubEnv로 값을 바꿔 "운영에서는 안 보인다"를 확인할 수 있고, 호출 시점에 읽어 항상 현재 값을 따른다.
 * 운영 빌드에서는 Vite가 이 비교를 상수로 바꿔, 목업 전용 분기가 사라지게 한다.
 */
export function isMockMode(): boolean {
  return import.meta.env.VITE_USE_MOCK === 'true'
}

/**
 * 화면 주소의 mockScenario 값을 객체로 돌려준다. 이동 링크에 이어 붙이거나 쿼리 키에 넣을 때 쓴다(API 요청에는 헤더로 실린다).
 *
 * 목업 모드(VITE_USE_MOCK=true)가 아니면 항상 빈 객체다. 운영 환경에서 누가 주소에 이 파라미터를 붙여도
 * 아무것도 읽히지 않게 하기 위함이다(Vite가 빌드 때 이 조건을 상수로 바꿔 분기째 지운다).
 */
export function getMockScenarioParams(): Record<string, string> {
  if (import.meta.env.VITE_USE_MOCK !== 'true') return {}
  const scenario = new URLSearchParams(window.location.search).get(MOCK_SCENARIO_PARAM)
  return scenario ? { [MOCK_SCENARIO_PARAM]: scenario } : {}
}

// 한 번에 가져오는 개수를 줄여 "더보기"를 눈으로 확인하는 쿼리 파라미터 이름. 예: /?q=카지노&mockPageSize=5
export const MOCK_PAGE_SIZE_PARAM = 'mockPageSize'

/**
 * 화면 주소의 mockPageSize 값을 숫자로 돌려준다. 없거나 올바르지 않으면 undefined다.
 *
 * 목업 전용 확인 장치. 픽스처 작품이 10편뿐이라 기본 size(10)로는 "더보기"가 보이지 않아 둔다.
 * 검색 요청의 size는 계약 파라미터라 그대로 두고, 이 값은 X-Mock-Page-Size 헤더로 실어 핸들러가 한 페이지 크기를 줄이게 한다.
 * getMockScenarioParams와 같은 이유로 목업 모드(VITE_USE_MOCK=true)가 아니면 항상 undefined라 운영·실제 API에 영향이 없다.
 * 1~100 정수만 받는다(그 밖의 값은 무시하고 기본값을 쓴다).
 */
export function getMockPageSize(): number | undefined {
  if (import.meta.env.VITE_USE_MOCK !== 'true') return undefined
  const raw = new URLSearchParams(window.location.search).get(MOCK_PAGE_SIZE_PARAM)
  const size = Number(raw)
  return raw !== null && Number.isInteger(size) && size >= 1 && size <= 100 ? size : undefined
}

/*
 * 참고: 예전에는 R11-18(환산 입력 저장)·R11-19(드라마 찜 단위)를 화면에서 비교하려고 ?mockVariantWatch=·?mockVariant= 전환 값을 뒀다.
 * 두 결정이 계약으로 확정돼(Task 031 6-3·6-4: 시즌 단위 찜, 환산 결과는 분으로만 저장) 전환 값과 안내 띠를 없앴다.
 */

/**
 * 화면 주소의 목업 전환 값(시나리오)을 객체로 돌려준다. 이동 링크에 이어 붙이거나(HeaderAccountNav 등) 회원 화면 훅의 쿼리 키에 넣어,
 * 주소의 값이 바뀌면 같은 API라도 새로 불러오게 한다. 목업 모드가 아니면 빈 객체라 운영에는 영향이 없다.
 */
export function getMockRequestParams(): Record<string, string> {
  return getMockScenarioParams()
}

// ===== 목업 전환 값을 실어 보내는 요청 헤더 이름(Task 031 6-0) =====
export const MOCK_SCENARIO_HEADER = 'X-Mock-Scenario' // ?mockScenario= 값
export const MOCK_PAGE_SIZE_HEADER = 'X-Mock-Page-Size' // ?mockPageSize= 값(검색 한 페이지 크기를 줄여 "더보기" 확인)

/**
 * 화면 주소의 목업 전환 값을 요청 헤더 객체로 돌려준다. api/mockHeaders.ts의 요청 인터셉터가 모든 요청에 붙인다.
 * 목업 모드가 아니면 항상 빈 객체다(운영 요청에는 X-Mock-* 헤더가 붙지 않는다).
 */
export function getMockRequestHeaders(): Record<string, string> {
  if (import.meta.env.VITE_USE_MOCK !== 'true') return {}
  const scenarioParams = getMockScenarioParams()
  const pageSize = getMockPageSize()
  return {
    ...(scenarioParams[MOCK_SCENARIO_PARAM]
      ? { [MOCK_SCENARIO_HEADER]: scenarioParams[MOCK_SCENARIO_PARAM] }
      : {}),
    ...(pageSize !== undefined ? { [MOCK_PAGE_SIZE_HEADER]: String(pageSize) } : {}),
  }
}
