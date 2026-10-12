import { http as mswHttp, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { AXIOS_INSTANCE } from '@/api/http'

/*
 * 목업 헤더 요청 인터셉터 테스트(Task 031 6-0). 화면 주소의 검토용 전환 값이 목업 모드에서만 X-Mock-* 헤더로 실리고,
 * 운영(목업 모드가 아님)에서는 실리지 않는지 확인한다. 서버 쪽은 MSW 핸들러 하나로 받은 헤더를 기록한다.
 */

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => {
  server.resetHandlers()
  vi.unstubAllEnvs()
  window.history.pushState({}, '', '/')
})
afterAll(() => server.close())

// 요청을 보내고, 서버(MSW)에 도착한 X-Mock-* 헤더를 돌려준다
async function captureMockHeaders(): Promise<Record<string, string>> {
  let received: Record<string, string> = {}
  server.use(
    mswHttp.get('/api/public/ott-services', ({ request }) => {
      received = Object.fromEntries(
        [...request.headers.entries()].filter(([name]) => name.startsWith('x-mock-')),
      )
      return HttpResponse.json({ success: true, data: [] })
    }),
  )
  await AXIOS_INSTANCE.get('/public/ott-services')
  return received
}

describe('목업 헤더 요청 인터셉터', () => {
  it('목업 모드에서 화면 주소의 전환 값을 X-Mock-* 헤더로 싣는다', async () => {
    vi.stubEnv('VITE_USE_MOCK', 'true')
    window.history.pushState(
      {},
      '',
      '/?mockScenario=collect-failed&mockVariantWatch=b&mockPageSize=5',
    )

    expect(await captureMockHeaders()).toEqual({
      'x-mock-scenario': 'collect-failed',
      'x-mock-variant-watch': 'b',
      'x-mock-page-size': '5',
    })
  })

  it('목업 모드가 아니면(운영) 주소에 값이 있어도 헤더를 싣지 않는다', async () => {
    vi.stubEnv('VITE_USE_MOCK', 'false')
    window.history.pushState({}, '', '/?mockScenario=error&mockPageSize=5')

    expect(await captureMockHeaders()).toEqual({})
  })
})
