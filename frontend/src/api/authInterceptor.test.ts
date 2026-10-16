import { delay, http as mswHttp, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { AXIOS_INSTANCE, ApiError, refreshSession, restoreSession } from '@/api/http'
import { useAuthStore } from '@/stores/authStore'

/*
 * 401 재발급 인터셉터 테스트(Task 057). 목업 모드가 아닌 실제 인증 경로를 확인한다.
 * 서버는 이 파일 안의 작은 MSW 핸들러로 흉내 낸다:
 * - GET /api/protected: Authorization이 "Bearer new-token"일 때만 200, 그 밖에는 401(ProblemDetail)
 * - POST /api/auth/refresh: 테스트가 정한 응답(성공이면 new-token·USER)
 * 확인하는 것: 401 → 재발급 1회 → 원래 요청 재시도, 동시 401도 재발급 1회, 재발급 401이면 로그아웃 처리,
 * 재발급 500·네트워크 오류는 로그인 상태를 지우지 않고 오류 전달, 한 요청은 한 번만 재시도, 앱 시작 복원.
 */

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterAll(() => server.close())

// 호출 횟수·헤더 기록
let refreshCalls = 0
let protectedCalls = 0

beforeEach(() => {
  refreshCalls = 0
  protectedCalls = 0
  useAuthStore.getState().clearSession()
})
afterEach(() => {
  server.resetHandlers()
  useAuthStore.getState().clearSession()
})

// 백엔드와 같은 ProblemDetail 응답(Content-Type이 problem+json이어야 인터셉터가 정리한다)
function problem(status: number, errorCode: string) {
  return HttpResponse.json(
    { title: 'x', status, detail: 'd', instance: '/api/x', errorCode },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

// 보호된 API: 새 토큰이어야 통과한다
function useProtectedHandler(options?: { alwaysUnauthorized?: boolean }) {
  server.use(
    mswHttp.get('/api/protected', ({ request }) => {
      protectedCalls += 1
      const isNewToken = request.headers.get('Authorization') === 'Bearer new-token'
      if (options?.alwaysUnauthorized || !isNewToken) return problem(401, 'UNAUTHORIZED')
      return HttpResponse.json({ success: true, data: 'ok' })
    }),
  )
}

// 재발급: 성공(새 토큰)
function useRefreshSuccess(delayMs = 0) {
  server.use(
    mswHttp.post('/api/auth/refresh', async () => {
      refreshCalls += 1
      if (delayMs > 0) await delay(delayMs)
      return HttpResponse.json({ success: true, data: { accessToken: 'new-token', role: 'USER' } })
    }),
  )
}

describe('401 재발급 인터셉터', () => {
  it('401을 받으면 재발급을 한 번 하고 새 토큰으로 원래 요청을 다시 보낸다', async () => {
    useAuthStore.getState().setSession('old-token', 'USER')
    useProtectedHandler()
    useRefreshSuccess()

    const response = await AXIOS_INSTANCE.get('/protected')

    expect(response.data.data).toBe('ok')
    expect(refreshCalls).toBe(1)
    expect(protectedCalls).toBe(2) // 첫 시도(401) + 재시도
    expect(useAuthStore.getState().accessToken).toBe('new-token')
  })

  it('동시에 여러 요청이 401을 받아도 재발급 요청은 하나다', async () => {
    useAuthStore.getState().setSession('old-token', 'USER')
    useProtectedHandler()
    useRefreshSuccess(50) // 재발급이 끝나기 전에 나머지 401이 도착하도록 늦춘다

    const results = await Promise.all([
      AXIOS_INSTANCE.get('/protected'),
      AXIOS_INSTANCE.get('/protected'),
      AXIOS_INSTANCE.get('/protected'),
    ])

    expect(results.map((result) => result.data.data)).toEqual(['ok', 'ok', 'ok'])
    expect(refreshCalls).toBe(1)
  })

  it('재발급이 401이면 로그인 상태를 비우고 원래 401 오류를 돌려준다', async () => {
    useAuthStore.getState().setSession('old-token', 'USER')
    useProtectedHandler()
    server.use(
      mswHttp.post('/api/auth/refresh', () => {
        refreshCalls += 1
        return problem(401, 'UNAUTHORIZED')
      }),
    )

    const error = await AXIOS_INSTANCE.get('/protected').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(401)
    expect(refreshCalls).toBe(1)
    expect(useAuthStore.getState().status).toBe('guest')
    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it('재발급이 500이면 로그아웃 처리하지 않고 그 오류를 그대로 전달한다', async () => {
    useAuthStore.getState().setSession('old-token', 'USER')
    useProtectedHandler()
    server.use(
      mswHttp.post('/api/auth/refresh', () => {
        refreshCalls += 1
        return problem(500, 'INTERNAL_ERROR')
      }),
    )

    const error = await AXIOS_INSTANCE.get('/protected').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(500)
    expect((error as ApiError).errorCode).toBe('INTERNAL_ERROR')
    // 로그인 상태는 그대로다(서버 장애로 사용자를 로그아웃시키지 않는다)
    expect(useAuthStore.getState().status).toBe('authenticated')
    expect(useAuthStore.getState().accessToken).toBe('old-token')
  })

  it('재발급이 네트워크 오류여도 로그인 상태를 비우지 않는다', async () => {
    useAuthStore.getState().setSession('old-token', 'USER')
    useProtectedHandler()
    server.use(mswHttp.post('/api/auth/refresh', () => HttpResponse.error()))

    const error = await AXIOS_INSTANCE.get('/protected').catch((e: unknown) => e)

    expect((error as ApiError).kind).toBe('network')
    expect(useAuthStore.getState().status).toBe('authenticated')
  })

  it('재시도한 요청이 또 401이면 더 재발급하지 않고 실패시킨다(무한 반복 방지)', async () => {
    useAuthStore.getState().setSession('old-token', 'USER')
    useProtectedHandler({ alwaysUnauthorized: true })
    useRefreshSuccess()

    const error = await AXIOS_INSTANCE.get('/protected').catch((e: unknown) => e)

    expect((error as ApiError).status).toBe(401)
    expect(refreshCalls).toBe(1)
    expect(protectedCalls).toBe(2)
  })

  it('토큰 없이 보낸 요청(비로그인)의 401은 재발급하지 않는다', async () => {
    useProtectedHandler()
    useRefreshSuccess()

    const error = await AXIOS_INSTANCE.get('/protected').catch((e: unknown) => e)

    expect((error as ApiError).status).toBe(401)
    expect(refreshCalls).toBe(0)
  })

  it('토큰이 있으면 요청에 Authorization: Bearer 헤더를 붙인다', async () => {
    useAuthStore.getState().setSession('new-token', 'USER')
    useProtectedHandler()

    const response = await AXIOS_INSTANCE.get('/protected')

    expect(response.data.data).toBe('ok')
    expect(refreshCalls).toBe(0)
  })
})

describe('앱 시작 시 로그인 상태 복원(restoreSession)', () => {
  it('재발급이 성공하면 로그인 상태가 되고 토큰·역할이 메모리에 들어간다', async () => {
    useRefreshSuccess()

    await restoreSession()

    expect(useAuthStore.getState()).toMatchObject({
      status: 'authenticated',
      accessToken: 'new-token',
      role: 'USER',
    })
  })

  it('재발급이 401이면 비로그인(guest)이다', async () => {
    server.use(mswHttp.post('/api/auth/refresh', () => problem(401, 'UNAUTHORIZED')))

    await restoreSession()

    expect(useAuthStore.getState().status).toBe('guest')
  })

  it('재발급이 500이면 오류(error) 상태이고 비로그인으로 단정하지 않는다', async () => {
    server.use(mswHttp.post('/api/auth/refresh', () => problem(500, 'INTERNAL_ERROR')))

    await restoreSession()

    expect(useAuthStore.getState().status).toBe('error')
  })

  it('동시에 두 번 불러도 재발급 요청은 하나다(single-flight)', async () => {
    useRefreshSuccess(30)

    await Promise.all([refreshSession(), refreshSession()])

    expect(refreshCalls).toBe(1)
  })
})
