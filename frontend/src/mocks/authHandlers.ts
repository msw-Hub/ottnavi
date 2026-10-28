import { http, HttpResponse, type RequestHandler } from 'msw'
import type { CommonResponseAccessToken, ProblemDetail } from '@/api/generated/model'
import { hasMockScenario, problemResponse } from '@/mocks/handlerUtils'
import { getMockRole, setMockRole } from '@/mocks/mockSession'

/*
 * ===== 인증 목업 핸들러 (Task 057, 데모 로그인 Task 109) =====
 *
 * 계약(docs/api/openapi.yaml)의 /auth/refresh·/auth/logout·/auth/demo-login과 같은 모양이다.
 * 실제 서버는 HttpOnly 쿠키(rt)로 사용자를 알아보지만, 목업은 쿠키가 없어 개발용 전환 토글의 역할(mockSession)을 대신 쓴다.
 * - POST /api/auth/refresh: 비로그인이면 401 UNAUTHORIZED, 회원·관리자·데모 계정이면 200 { accessToken, role }
 *   (X-Mock-Scenario: refresh-error 이면 500 INTERNAL_ERROR. "재발급이 500이어도 로그아웃되지 않는다"를 눈으로 보는 용도)
 * - POST /api/auth/logout: 항상 204(쿠키가 없거나 이미 무효여도 204, 계약)
 * - POST /api/auth/demo-login: 기본은 200 { accessToken, role: 'DEMO' }이고 목업 역할을 DEMO로 바꾼다(실제 서버가 rt 쿠키를 심는 것을 흉내 낸다).
 *   아이디·비밀번호 값은 검사하지 않는다(목업에는 비밀번호가 없다). 오류 화면은 화면 주소의 ?mockScenario= 값으로 본다:
 *   demo-login-failed(401 DEMO_LOGIN_FAILED) / demo-login-rate-limited(429, Retry-After: 840초 = 14분)
 *   / demo-login-disabled(404, 데모 로그인 꺼짐) / demo-login-error(500)
 * 돌려주는 토큰 문자열은 JWT가 아닌 고정 값이다. 목업 모드에서 화면은 토큰을 쓰지 않는다(hooks/useSession).
 */

// 429 시나리오의 대기 시간(초). 840초는 화면에서 "약 14분"으로 보인다(서버는 IP당 15분 5회 제한이라 최대 900초)
const MOCK_DEMO_RETRY_AFTER_SECONDS = '840'

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

  http.post('/api/auth/demo-login', ({ request }) => {
    // 1. 시나리오별 실패. 서버의 확인 순서(꺼짐 404 → Origin 403 → 요청 제한 429 → 아이디·비밀번호 401)와 같은 응답 모양이다
    if (hasMockScenario(request, 'demo-login-disabled')) {
      return problemResponse(
        request,
        404,
        'Not Found',
        'RESOURCE_NOT_FOUND',
        '요청한 리소스를 찾을 수 없습니다.',
      )
    }
    if (hasMockScenario(request, 'demo-login-rate-limited')) {
      const body: ProblemDetail = {
        title: 'Too Many Requests',
        status: 429,
        detail: '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.',
        instance: new URL(request.url).pathname,
        errorCode: 'RATE_LIMIT_EXCEEDED',
      }
      return HttpResponse.json(body, {
        status: 429,
        headers: {
          'Content-Type': 'application/problem+json',
          'Retry-After': MOCK_DEMO_RETRY_AFTER_SECONDS,
        },
      })
    }
    if (hasMockScenario(request, 'demo-login-failed')) {
      return problemResponse(
        request,
        401,
        'Unauthorized',
        'DEMO_LOGIN_FAILED',
        '아이디 또는 비밀번호가 올바르지 않습니다.',
      )
    }
    if (hasMockScenario(request, 'demo-login-error')) {
      return problemResponse(
        request,
        500,
        'Internal Server Error',
        'INTERNAL_ERROR',
        '일시적인 오류가 발생했습니다.',
      )
    }

    // 2. 성공: 목업 로그인 역할을 데모 계정으로 바꾼다(실제 서버가 rt 쿠키를 심어 이후 재발급이 DEMO를 돌려주는 것과 같은 효과)
    setMockRole('DEMO')
    const body: CommonResponseAccessToken = {
      success: true,
      data: { accessToken: 'mock-access-token', role: 'DEMO' },
    }
    return HttpResponse.json(body)
  }),
]
