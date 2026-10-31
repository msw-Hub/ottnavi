import { delay, http, HttpResponse, type RequestHandler } from 'msw'
import type {
  CommonResponseAdminUserStats,
  CommonResponseAdminVisitorStats,
} from '@/api/generated/model'
import {
  buildMockEmptyUserStats,
  buildMockUserStats,
  buildMockVisitorStats,
  buildMockVisitorStatsWithoutNumbers,
} from '@/mocks/fixtures/stats'
import { hasMockScenario, problemResponse } from '@/mocks/handlerUtils'

/*
 * ===== 관리자 운영 현황 목업 핸들러 (SCR-17 모니터링) =====
 * 경로·응답은 계약(docs/api/openapi.yaml)과 같다. 역할은 검사하지 않는다(adminHandlers와 같은 결정: 화면 가드가 막는다).
 *
 * - 회원 현황:   GET /api/admin/user-stats
 * - 방문자 수:   GET /api/admin/visitor-stats
 *
 * 시나리오(주소의 ?mockScenario= 값을 목업 모드가 X-Mock-Scenario 요청 헤더로 보낸다):
 * - error:               두 API 모두 500 오류(요청 자체가 실패한 카드 모양)
 * - empty:               회원 현황이 모두 0명
 * - visitor-disabled:    방문자 수가 200 + DISABLED(서버에 방문 통계 설정 없음)
 * - visitor-unavailable: 방문자 수가 200 + UNAVAILABLE(방문 통계 조회가 잠깐 실패)
 * - visitor-error:       방문자 수만 500 오류(회원 현황은 정상, 카드끼리 영향이 없는지 확인)
 */

// 요청 처리에 걸리는 시간. 테스트에서는 기다리지 않고, 브라우저에서는 로딩 상태가 눈에 보이게 잠깐 기다린다
function wait() {
  return delay(import.meta.env.MODE === 'test' ? 0 : 300)
}

function internalError(request: Request) {
  return problemResponse(
    request,
    500,
    'Internal Server Error',
    'INTERNAL_ERROR',
    '일시적인 오류가 발생했습니다.',
  )
}

export const statsHandlers: RequestHandler[] = [
  http.get('/api/admin/user-stats', async ({ request }) => {
    await wait()
    if (hasMockScenario(request, 'error')) return internalError(request)
    const body: CommonResponseAdminUserStats = {
      success: true,
      data: hasMockScenario(request, 'empty') ? buildMockEmptyUserStats() : buildMockUserStats(),
    }
    return HttpResponse.json(body)
  }),

  http.get('/api/admin/visitor-stats', async ({ request }) => {
    await wait()
    if (hasMockScenario(request, 'error') || hasMockScenario(request, 'visitor-error')) {
      return internalError(request)
    }
    const data = hasMockScenario(request, 'visitor-disabled')
      ? buildMockVisitorStatsWithoutNumbers('DISABLED')
      : hasMockScenario(request, 'visitor-unavailable')
        ? buildMockVisitorStatsWithoutNumbers('UNAVAILABLE')
        : buildMockVisitorStats()
    const body: CommonResponseAdminVisitorStats = { success: true, data }
    return HttpResponse.json(body)
  }),
]
