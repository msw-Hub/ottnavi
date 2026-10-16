import { http, HttpResponse, type RequestHandler } from 'msw'
import type { CommonResponseAccessToken } from '@/api/generated/model'
import { hasMockScenario, problemResponse } from '@/mocks/handlerUtils'
import { getMockRole } from '@/mocks/mockSession'

/*
 * ===== 인증 목업 핸들러 (Task 057) =====
 *
 * 계약(docs/api/openapi.yaml)의 /auth/refresh·/auth/logout과 같은 모양이다.
 * 실제 서버는 HttpOnly 쿠키(rt)로 사용자를 알아보지만, 목업은 쿠키가 없어 개발용 전환 토글의 역할(mockSession)을 대신 쓴다.
 * - POST /api/auth/refresh: 비로그인이면 401 UNAUTHORIZED, 회원·관리자면 200 { accessToken, role }
 *   (X-Mock-Scenario: refresh-error 이면 500 INTERNAL_ERROR. "재발급이 500이어도 로그아웃되지 않는다"를 눈으로 보는 용도)
 * - POST /api/auth/logout: 항상 204(쿠키가 없거나 이미 무효여도 204, 계약)
 * 돌려주는 토큰 문자열은 JWT가 아닌 고정 값이다. 목업 모드에서 화면은 토큰을 쓰지 않는다(hooks/useSession).
 */
export const authHandlers: RequestHandler[] = [
  http.post('/api/auth/refresh', ({ request }) => {
    if (hasMockScenario(request, 'refresh-error')) {
      return problemResponse(
        request,
        500,
        'Internal Server Error',
        'INTERNAL_ERROR',
        '일시적인 오류가 발생했습니다.',
      )
    }
    const role = getMockRole()
    if (role === 'GUEST') {
      return problemResponse(request, 401, 'Unauthorized', 'UNAUTHORIZED', '로그인이 필요합니다.')
    }
    const body: CommonResponseAccessToken = {
      success: true,
      data: { accessToken: 'mock-access-token', role },
    }
    return HttpResponse.json(body)
  }),

  http.post('/api/auth/logout', () => new HttpResponse(null, { status: 204 })),
]
