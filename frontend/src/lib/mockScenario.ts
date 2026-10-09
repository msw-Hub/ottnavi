/*
 * 목업 시나리오(오류·예외 상황 재현) 도우미다.
 *
 * 왜 필요한가: 외부 수집 실패처럼 "서버가 특별한 응답을 주는 상황"을 화면에서 눈으로 보려면, 사용자가 주소창에
 * `?mockScenario=collect-failed`를 붙였을 때 그 값을 API 요청에도 실어 보내야 한다.
 * 목업 핸들러(src/mocks/handlers.ts)는 API 요청 주소의 이 파라미터를 보고 응답을 바꾼다.
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
 * 화면 주소의 mockScenario 값을 API 요청 파라미터로 넘길 객체로 돌려준다.
 *
 * 목업 모드(VITE_USE_MOCK=true)가 아니면 항상 빈 객체다. 운영 환경에서 누가 주소에 이 파라미터를 붙여도
 * 실제 API 요청에는 아무것도 실리지 않게 하기 위함이다(Vite가 빌드 때 이 조건을 상수로 바꿔 분기째 지운다).
 */
export function getMockScenarioParams(): Record<string, string> {
  if (import.meta.env.VITE_USE_MOCK !== 'true') return {}
  const scenario = new URLSearchParams(window.location.search).get(MOCK_SCENARIO_PARAM)
  return scenario ? { [MOCK_SCENARIO_PARAM]: scenario } : {}
}

// 한 번에 가져오는 개수를 줄여 "더보기"를 눈으로 확인하는 쿼리 파라미터 이름. 예: /?q=카지노&mockPageSize=5
export const MOCK_PAGE_SIZE_PARAM = 'mockPageSize'

/**
 * 화면 주소의 mockPageSize 값을 검색 요청의 size로 쓸 숫자로 돌려준다. 없거나 올바르지 않으면 undefined다.
 *
 * 목업 전용 확인 장치, Task 031 이후 제거 가능. 픽스처 작품이 10편뿐이라 기본 size(10)로는 "더보기"가 보이지 않아 둔다.
 * getMockScenarioParams와 같은 이유로 목업 모드(VITE_USE_MOCK=true)가 아니면 항상 undefined라 운영·실제 API에 영향이 없다.
 * 1~100 정수만 받는다(그 밖의 값은 무시하고 기본값을 쓴다).
 */
export function getMockPageSize(): number | undefined {
  if (import.meta.env.VITE_USE_MOCK !== 'true') return undefined
  const raw = new URLSearchParams(window.location.search).get(MOCK_PAGE_SIZE_PARAM)
  const size = Number(raw)
  return raw !== null && Number.isInteger(size) && size >= 1 && size <= 100 ? size : undefined
}
