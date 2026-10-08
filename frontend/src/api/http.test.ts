import { AxiosError, AxiosHeaders, CanceledError } from 'axios'
import { http as mswHttp, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { toast } from 'sonner'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { AXIOS_INSTANCE, ApiError, normalizeApiError, RATE_LIMIT_TOAST_ID } from '@/api/http'
import { handlers } from '@/mocks/handlers'
import { DEFAULT_ERROR_MESSAGE, NETWORK_ERROR_MESSAGE } from '@/lib/errorMessages'

// 토스트가 실제로 그려지는지가 아니라 "인터셉터가 토스트를 요청했는지"를 본다. 그래서 sonner의 toast를 가짜 함수로 바꾼다
vi.mock('sonner', () => ({ toast: { error: vi.fn() } }))

// 앱의 목업 핸들러(src/mocks/handlers.ts)를 그대로 써서, 브라우저 목업과 같은 응답으로 인터셉터를 확인한다
const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledFrame: 'error' })) // 핸들러 없는 요청이 실제 네트워크로 나가지 않게 막는다
afterEach(() => server.resetHandlers()) // 테스트 안에서 server.use로 덧붙인 핸들러를 지운다
afterAll(() => server.close())
beforeEach(() => vi.mocked(toast.error).mockClear())

// 요청을 보내고, 실패하면 던져진 값을 돌려준다(성공하면 테스트 실패)
async function catchRequestError(url: string): Promise<unknown> {
  try {
    await AXIOS_INSTANCE.get(url)
  } catch (error) {
    return error
  }
  throw new Error('요청이 실패해야 하는데 성공했다')
}

describe('응답 인터셉터 (MSW 목업 응답)', () => {
  it('성공 응답은 래퍼({ success, data })를 벗기지 않고 그대로 돌려준다', async () => {
    const response = await AXIOS_INSTANCE.get('/public/ott-services')
    expect(response.data.success).toBe(true)
    expect(response.data.data).toHaveLength(7)
    expect(toast.error).not.toHaveBeenCalled()
  })

  it('429 ProblemDetail을 ApiError로 정리하고 요청 제한 토스트를 한 번 띄운다', async () => {
    const error = await catchRequestError('/public/ott-services?mockScenario=rate-limit')

    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.kind).toBe('problem')
    expect(apiError.status).toBe(429)
    expect(apiError.errorCode).toBe('RATE_LIMIT_EXCEEDED')
    expect(apiError.title).toBe('Too Many Requests')
    expect(apiError.userMessage).toBe('요청이 많습니다. 잠시 후 다시 시도해 주세요.')
    expect(apiError.isRateLimited).toBe(true)
    expect(apiError.hasNotifiedUser).toBe(true)
    expect(toast.error).toHaveBeenCalledTimes(1)
    expect(toast.error).toHaveBeenCalledWith('요청이 많습니다. 잠시 후 다시 시도해 주세요.', {
      id: RATE_LIMIT_TOAST_ID,
    })
  })

  it('VALIDATION_FAILED의 fieldErrors를 꺼내고, 형식이 어긋난 항목은 버린다. 429가 아니면 토스트를 띄우지 않는다', async () => {
    server.use(
      mswHttp.get('/api/me/settings', () =>
        HttpResponse.json(
          {
            title: 'Bad Request',
            status: 400,
            detail: '요청 값이 올바르지 않습니다.',
            errorCode: 'VALIDATION_FAILED',
            fieldErrors: [
              { field: 'monthlyBudget', message: '0 이상이어야 합니다.' },
              { field: 'broken' }, // message 없음 → 버린다
              'not-an-object', // 객체가 아님 → 버린다
            ],
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )

    const apiError = (await catchRequestError('/me/settings')) as ApiError

    expect(apiError.kind).toBe('problem')
    expect(apiError.status).toBe(400)
    expect(apiError.errorCode).toBe('VALIDATION_FAILED')
    expect(apiError.detail).toBe('요청 값이 올바르지 않습니다.')
    expect(apiError.fieldErrors).toEqual([
      { field: 'monthlyBudget', message: '0 이상이어야 합니다.' },
    ])
    expect(apiError.userMessage).toBe('입력한 내용을 다시 확인해 주세요.')
    expect(toast.error).not.toHaveBeenCalled()
  })

  it('problem+json이 아닌 오류(HTML 오류 페이지 등)는 http 종류로 정리하고 기본 메시지를 쓴다', async () => {
    server.use(
      mswHttp.get('/api/public/ott-services', () =>
        HttpResponse.text('<html>Bad Gateway</html>', {
          status: 502,
          headers: { 'Content-Type': 'text/html' },
        }),
      ),
    )

    const apiError = (await catchRequestError('/public/ott-services')) as ApiError

    expect(apiError.kind).toBe('http')
    expect(apiError.status).toBe(502)
    expect(apiError.errorCode).toBeNull()
    expect(apiError.fieldErrors).toEqual([])
    expect(apiError.userMessage).toBe(DEFAULT_ERROR_MESSAGE)
    expect(toast.error).not.toHaveBeenCalled()
  })

  it('application/json으로 온 ProblemDetail 모양 본문은 problem으로 보지 않는다(Content-Type이 기준)', async () => {
    server.use(
      mswHttp.get('/api/public/ott-services', () =>
        HttpResponse.json({ status: 404, errorCode: 'TITLE_NOT_FOUND' }, { status: 404 }),
      ),
    )

    const apiError = (await catchRequestError('/public/ott-services')) as ApiError

    expect(apiError.kind).toBe('http')
    expect(apiError.errorCode).toBeNull()
  })

  it('ProblemDetail이 아닌 429(앞단 요청 제한)도 요청 제한 메시지와 토스트를 보인다', async () => {
    server.use(
      mswHttp.get('/api/public/ott-services', () => new HttpResponse(null, { status: 429 })),
    )

    const apiError = (await catchRequestError('/public/ott-services')) as ApiError

    expect(apiError.kind).toBe('http')
    expect(apiError.errorCode).toBeNull()
    expect(apiError.userMessage).toBe('요청이 많습니다. 잠시 후 다시 시도해 주세요.')
    expect(toast.error).toHaveBeenCalledTimes(1)
  })

  it('응답을 받지 못한 네트워크 오류는 network 종류로 정리한다', async () => {
    server.use(mswHttp.get('/api/public/ott-services', () => HttpResponse.error()))

    const apiError = (await catchRequestError('/public/ott-services')) as ApiError

    expect(apiError.kind).toBe('network')
    expect(apiError.status).toBeNull()
    expect(apiError.userMessage).toBe(NETWORK_ERROR_MESSAGE)
    expect(toast.error).not.toHaveBeenCalled()
  })
})

describe('normalizeApiError (직접 호출)', () => {
  // 응답이 있는 AxiosError를 만든다. 실제 axios가 만드는 것과 같은 생성자 인자 순서다
  function createAxiosError(status: number, data: unknown, contentType?: string): AxiosError {
    const config = { headers: new AxiosHeaders() }
    const headers = new AxiosHeaders()
    if (contentType) headers.set('Content-Type', contentType)
    return new AxiosError(
      'Request failed',
      'ERR_BAD_RESPONSE',
      config,
      {},
      {
        status,
        statusText: '',
        headers,
        config,
        data,
      },
    )
  }

  it('본문이 없는 problem+json 오류도 터지지 않고 http 종류로 정리한다', () => {
    const apiError = normalizeApiError(createAxiosError(500, '', 'application/problem+json'))
    expect(apiError.kind).toBe('http')
    expect(apiError.status).toBe(500)
    expect(apiError.userMessage).toBe(DEFAULT_ERROR_MESSAGE)
  })

  it('charset이 붙은 Content-Type도 problem으로 인식하고, 필드 타입이 어긋나면 null로 둔다', () => {
    const apiError = normalizeApiError(
      createAxiosError(
        404,
        { title: 404, errorCode: 'TITLE_NOT_FOUND', fieldErrors: 'x' },
        'application/problem+json; charset=UTF-8',
      ),
    )
    expect(apiError.kind).toBe('problem')
    expect(apiError.title).toBeNull()
    expect(apiError.errorCode).toBe('TITLE_NOT_FOUND')
    expect(apiError.fieldErrors).toEqual([])
    expect(apiError.userMessage).toBe('작품을 찾을 수 없습니다.')
  })

  it('모르는 errorCode는 기본 메시지를 쓰되 errorCode 값은 남긴다', () => {
    const apiError = normalizeApiError(
      createAxiosError(409, { errorCode: 'BRAND_NEW_CODE' }, 'application/problem+json'),
    )
    expect(apiError.errorCode).toBe('BRAND_NEW_CODE')
    expect(apiError.userMessage).toBe(DEFAULT_ERROR_MESSAGE)
  })

  it('취소된 요청은 canceled, axios 오류가 아니면 unknown이다', () => {
    expect(normalizeApiError(new CanceledError()).kind).toBe('canceled')
    expect(normalizeApiError(new TypeError('boom')).kind).toBe('unknown')
  })

  it('이미 ApiError면 그대로 돌려준다', () => {
    const original = normalizeApiError(new TypeError('boom'))
    expect(normalizeApiError(original)).toBe(original)
  })
})
