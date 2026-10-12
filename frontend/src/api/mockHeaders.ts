import type { InternalAxiosRequestConfig } from 'axios'
import { getMockRequestHeaders } from '@/lib/mockScenario'

/**
 * 목업 모드일 때 화면 검토용 전환 값(시나리오·안·페이지 크기)을 요청 헤더(X-Mock-*)로 붙이는 요청 인터셉터 함수다.
 *
 * 왜 인터셉터(공통 한 곳)인가: 이 값들은 API 계약에 없어 생성된 훅의 파라미터로 보낼 수 없다. 훅마다 헤더를 직접 넘기게
 * 두면 새 화면에서 빠뜨리기 쉬워, 모든 요청이 지나가는 axios 인스턴스에서 한 번에 붙인다(api/http.ts가 등록한다).
 *
 * 운영 안전: getMockRequestHeaders는 VITE_USE_MOCK이 true가 아니면 빈 객체를 돌려주므로 운영 요청에는 헤더가 붙지 않는다.
 * 이 파일은 인증(Access Token, 401 재발급)과 무관하다. Authorization 헤더는 건드리지 않고 X-Mock-* 헤더만 더한다.
 */
export function attachMockHeaders(config: InternalAxiosRequestConfig): InternalAxiosRequestConfig {
  for (const [name, value] of Object.entries(getMockRequestHeaders())) {
    config.headers.set(name, value)
  }
  return config
}
