import { delay, http, HttpResponse, type RequestHandler } from 'msw'
import type {
  CommonResponseAdminNotificationSummary,
  CommonResponseNotificationHistoryPage,
  CommonResponseNotificationSetting,
  UpdateMyNotificationSettingRequest,
} from '@/api/generated/model'
import { MOCK_ADMIN_NOTIFICATION_SUMMARY, MOCK_NOTIFICATIONS } from '@/mocks/fixtures/notifications'
import {
  denyGuest,
  getRequestMockPageSize,
  hasMockScenario,
  problemResponse,
  readMockJson,
  writeMockJson,
} from '@/mocks/handlerUtils'

/*
 * ===== 알림 설정·발송 이력 목업 핸들러 (Task 071) =====
 * 경로·요청·응답은 제안 계약(docs/api/openapi.yaml, x-planned)과 같다. 응답 타입은 생성 모델이라 계약과 어긋나면 컴파일에서 걸린다.
 * 실제 API는 Task 073(백엔드)·074(연결)에서 생긴다.
 *
 * - 알림 설정 조회: GET /api/me/notification-setting   (기본 꺼짐. 켜고 끈 값은 sessionStorage에 남겨 새로고침 뒤에도 유지)
 * - 알림 설정 저장: PUT /api/me/notification-setting   ({ isNotificationEnabled }. 저장한 값을 돌려주고 이후 조회에 반영)
 * - 알림 이력:      GET /api/me/notifications           (최신순 페이지네이션. page/size, 목업 페이지 크기 헤더 지원)
 * - 관리자 요약:    GET /api/admin/notifications/summary (최근 7일 상태별 건수. 역할은 검사하지 않는다: adminHandlers와 같은 결정)
 *
 * 시나리오(주소의 ?mockScenario= 값을 목업 모드가 X-Mock-Scenario 요청 헤더로 보낸다):
 * - empty:               이력이 비어 있다. 관리자 요약은 모두 0건·마지막 발송 없음
 * - error:               조회(설정·이력·관리자 요약)와 저장이 500 오류를 돌려준다
 * - save-error:          설정 저장만 500 오류를 돌려준다(저장 실패 시 스위치가 되돌아가는지 확인)
 * - onboarding-required: 설정 저장이 409 ONBOARDING_REQUIRED를 돌려준다(내 설정을 한 번도 저장하지 않은 계정)
 */

// 목업 계정의 이메일. 계약에서 수신 이메일은 로그인한 계정의 이메일이다
const MOCK_ACCOUNT_EMAIL = 'user@example.com'

// 알림 켬 여부를 새로고침 뒤에도 남기는 sessionStorage 키(mockSession·찜 목록과 같은 방식)
const ENABLED_STORAGE_KEY = 'ottnavi:mock-notification-enabled'

// 계약 기본값은 꺼짐이다(동의 없이 메일을 보내지 않는다). 저장된 값이 true일 때만 켜짐으로 되살린다
let isNotificationEnabled = readMockJson(ENABLED_STORAGE_KEY) === true

/** 알림 목업 상태를 처음(꺼짐)으로 되돌린다. 테스트가 서로 영향을 주지 않게 쓴다. */
export function resetMockNotificationState(): void {
  isNotificationEnabled = false
  writeMockJson(ENABLED_STORAGE_KEY, null)
}

// 요청 처리에 걸리는 시간. 테스트(Vitest)에서는 기다리지 않고, 브라우저에서는 로딩 상태가 눈에 보이게 잠깐 기다린다
function wait() {
  return delay(import.meta.env.MODE === 'test' ? 0 : undefined)
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

export const notificationHandlers: RequestHandler[] = [
  http.get('/api/me/notification-setting', async ({ request }) => {
    await wait()
    const denied = denyGuest(request)
    if (denied) return denied
    if (hasMockScenario(request, 'error')) return internalError(request)

    const body: CommonResponseNotificationSetting = {
      success: true,
      data: { isNotificationEnabled, email: MOCK_ACCOUNT_EMAIL },
    }
    return HttpResponse.json(body)
  }),

  http.put('/api/me/notification-setting', async ({ request }) => {
    await wait()
    const denied = denyGuest(request)
    if (denied) return denied

    // 1. 본문 검사: isNotificationEnabled는 true/false여야 한다(서버 Bean Validation, 400)
    const requestBody = (await request
      .json()
      .catch(() => null)) as UpdateMyNotificationSettingRequest | null
    if (requestBody === null || typeof requestBody.isNotificationEnabled !== 'boolean') {
      return problemResponse(
        request,
        400,
        'Bad Request',
        'VALIDATION_FAILED',
        '요청 값이 올바르지 않습니다.',
        [{ field: 'isNotificationEnabled', message: 'true 또는 false여야 합니다.' }],
      )
    }
    // 2. 시나리오별 실패: 저장할 설정 행이 없는 계정(409) / 서버 오류(500)
    if (hasMockScenario(request, 'onboarding-required')) {
      return problemResponse(
        request,
        409,
        'Conflict',
        'ONBOARDING_REQUIRED',
        '내 설정을 먼저 저장해 주세요.',
      )
    }
    if (hasMockScenario(request, 'save-error') || hasMockScenario(request, 'error')) {
      return internalError(request)
    }

    // 3. 저장한 값을 이후 조회에 반영하고, 저장된 설정을 돌려준다
    isNotificationEnabled = requestBody.isNotificationEnabled
    writeMockJson(ENABLED_STORAGE_KEY, isNotificationEnabled ? true : null)
    const body: CommonResponseNotificationSetting = {
      success: true,
      data: { isNotificationEnabled, email: MOCK_ACCOUNT_EMAIL },
    }
    return HttpResponse.json(body)
  }),

  http.get('/api/me/notifications', async ({ request }) => {
    await wait()
    const denied = denyGuest(request)
    if (denied) return denied
    if (hasMockScenario(request, 'error')) return internalError(request)

    // 페이지 파라미터 검증(api-contract.md): page는 0 이상 정수(기본 0), size는 1~100 정수(기본 20). 벗어나면 400
    const searchParams = new URL(request.url).searchParams
    const page = Number(searchParams.get('page') ?? 0)
    // 목업 확인용: X-Mock-Page-Size 헤더가 있으면 한 페이지 크기를 그 값으로 줄여 "더 보기"를 눈으로 확인한다
    const size = getRequestMockPageSize(request) ?? Number(searchParams.get('size') ?? 20)
    if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 100) {
      return problemResponse(
        request,
        400,
        'Bad Request',
        'VALIDATION_FAILED',
        'page는 0 이상, size는 1~100 사이여야 합니다.',
      )
    }

    const all = hasMockScenario(request, 'empty') ? [] : MOCK_NOTIFICATIONS
    const body: CommonResponseNotificationHistoryPage = {
      success: true,
      data: {
        content: all.slice(page * size, (page + 1) * size),
        page,
        size,
        totalElements: all.length,
        totalPages: Math.ceil(all.length / size),
      },
    }
    return HttpResponse.json(body)
  }),

  http.get('/api/admin/notifications/summary', async ({ request }) => {
    await wait()
    if (hasMockScenario(request, 'error')) return internalError(request)

    const data = hasMockScenario(request, 'empty')
      ? {
          ...MOCK_ADMIN_NOTIFICATION_SUMMARY,
          pendingCount: 0,
          sentCount: 0,
          failedCount: 0,
          latestSentAt: null,
        }
      : MOCK_ADMIN_NOTIFICATION_SUMMARY
    const body: CommonResponseAdminNotificationSummary = { success: true, data }
    return HttpResponse.json(body)
  }),
]
