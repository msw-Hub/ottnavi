/*
 * 목업 핸들러들이 함께 쓰는 도우미와 메모리 저장소다.
 *
 * 왜 따로 두나: 공개 화면 핸들러(handlers.ts)와 회원 화면 핸들러(memberHandlers.ts)가 같은 찜 목록과 같은 오류 응답 모양을
 * 써야 한다. 한쪽 파일이 다른 쪽을 import하면 서로를 끌어와 순환 import가 되므로, 공통 부분을 이 파일로 뺐다.
 */
import { HttpResponse } from 'msw'
import type { ProblemDetail } from '@/api/generated/model'
import type { AddWishlistItemRequest } from '@/features/search/mockTypes'
import {
  MOCK_SCENARIO_PARAM,
  MOCK_VARIANT_PARAM,
  MOCK_VARIANT_WATCH_PARAM,
  parseMockVariant,
  type MockVariant,
} from '@/lib/mockScenario'
import { getMockRole, readStored, writeStored } from '@/mocks/mockSession'

// 요청 URL에 지정한 시나리오가 붙어 있는지 확인한다
export function hasMockScenario(request: Request, scenario: string): boolean {
  return new URL(request.url).searchParams.get(MOCK_SCENARIO_PARAM) === scenario
}

// 요청 URL의 R11-19 목업 안(?mockVariant=a|b)을 읽는다. 없으면 기본(a)이다
export function getRequestMockVariant(request: Request): MockVariant {
  return parseMockVariant(new URL(request.url).searchParams.get(MOCK_VARIANT_PARAM))
}

// 요청 URL의 R11-18 목업 안(?mockVariantWatch=a|b)을 읽는다. 없으면 기본(a)이다
export function getRequestMockWatchVariant(request: Request): MockVariant {
  return parseMockVariant(new URL(request.url).searchParams.get(MOCK_VARIANT_WATCH_PARAM))
}

// 백엔드 ProblemDetail과 같은 모양의 오류 응답을 만든다(Content-Type이 problem+json이어야 api/http.ts가 인식한다)
export function problemResponse(
  request: Request,
  status: number,
  title: string,
  errorCode: string,
  detail: string,
) {
  const body: ProblemDetail = {
    title,
    status,
    detail,
    instance: new URL(request.url).pathname,
    errorCode,
  }
  return HttpResponse.json(body, {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

/**
 * 비로그인(GUEST)이면 401 응답을 돌려주고, 로그인했으면 undefined를 돌려준다.
 * 핸들러 첫 줄에서 `const denied = denyGuest(request); if (denied) return denied` 형태로 쓴다.
 * 실제 인증과 연결하지 않고 목업 로그인 역할(mockSession)만 본다.
 */
export function denyGuest(request: Request) {
  if (getMockRole() !== 'GUEST') return undefined
  return problemResponse(request, 401, 'Unauthorized', 'UNAUTHORIZED', '로그인이 필요합니다.')
}

// 목업 서버가 메모리에 들고 있는 찜 한 건. 추가 요청 내용에 "다 봤음" 시각이 붙는다
export type MockWishlistEntry = AddWishlistItemRequest & { watchedAt: string | null }

/*
 * 찜 목록은 sessionStorage에 함께 적어 새로고침 후에도 남긴다(Task 028 보정, mockSession과 같은 방식).
 * 왜: 개발 중 주소창에 /wishlist를 직접 입력하면 페이지가 새로 로드되어 모듈 메모리의 찜이 사라져 화면을 확인할 수 없었다.
 * 탭을 닫으면 사라지는 sessionStorage라 오래 남지 않고, 저장소를 못 쓰거나 용량이 넘치면 메모리만으로 동작한다(writeStored가 삼킨다).
 * 내용은 작품 식별자·우선순위뿐이라 민감 정보가 아니다.
 */
const WISHLIST_STORAGE_KEY = 'ottnavi:mock-wishlist'

interface StoredWishlist {
  nextId: number // 다음에 발급할 찜 항목 ID
  entries: [number, MockWishlistEntry][] // [찜 항목 ID, 내용]
}

let nextWishlistItemId = 1

// 저장된 찜을 읽는다. 없거나 모양이 다르면(손으로 바뀐 값 등) null이라 빈 목록으로 시작한다
function readStoredWishlist(): StoredWishlist | null {
  const raw = readStored(WISHLIST_STORAGE_KEY)
  if (raw === null) return null
  try {
    const parsed: unknown = JSON.parse(raw)
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      'nextId' in parsed &&
      typeof parsed.nextId === 'number' &&
      'entries' in parsed &&
      Array.isArray(parsed.entries)
    ) {
      return parsed as StoredWishlist
    }
  } catch {
    // 깨진 JSON이면 무시하고 빈 목록으로 시작한다
  }
  return null
}

// 지금 찜 목록과 다음 ID를 저장소에 적는다. 비어 있고 ID도 처음이면 키를 지운다
function persistWishlist(): void {
  if (mockWishlist.size === 0 && nextWishlistItemId === 1) {
    writeStored(WISHLIST_STORAGE_KEY, null)
    return
  }
  const stored: StoredWishlist = { nextId: nextWishlistItemId, entries: [...mockWishlist] }
  writeStored(WISHLIST_STORAGE_KEY, JSON.stringify(stored))
}

/**
 * 변경(set·delete·clear)될 때마다 저장소에도 적는 Map이다.
 * Map을 확장한 이유: handlers.ts가 mockWishlist.set/delete를 직접 부르고 있어, 호출하는 쪽을 고치지 않고 저장을 한곳에 모으기 위함이다.
 */
class PersistedWishlistMap extends Map<number, MockWishlistEntry> {
  override set(key: number, value: MockWishlistEntry): this {
    super.set(key, value)
    persistWishlist()
    return this
  }

  override delete(key: number): boolean {
    const deleted = super.delete(key)
    persistWishlist()
    return deleted
  }

  override clear(): void {
    super.clear()
    persistWishlist()
  }
}

// 찜 목록(찜 항목 ID → 내용). 새로고침해도 sessionStorage에서 되살아난다.
// 화면의 찜 상태는 요청이 성공할 때마다 프론트 캐시를 고쳐 맞춘다(features/*/hooks/useWishlistItem)
export const mockWishlist = new PersistedWishlistMap()

// 저장된 값을 되살린다. 생성자에서 하지 않고 Map.prototype.set으로 직접 넣는 이유: 되살리는 중에 저장소를 다시 쓰지 않으려는 것이다
const storedWishlist = readStoredWishlist()
if (storedWishlist) {
  nextWishlistItemId = storedWishlist.nextId
  for (const [id, entry] of storedWishlist.entries) Map.prototype.set.call(mockWishlist, id, entry)
}

// 새 찜 항목 ID를 발급한다. 다음 ID도 저장해 새로고침 뒤에 ID가 겹치지 않게 한다(찜이 추가되면 set이 저장하지만, 발급만 되고 실패해도 안전하다)
export function issueWishlistItemId(): number {
  const issued = nextWishlistItemId++
  persistWishlist()
  return issued
}

// 목업 찜 목록을 비운다(테스트가 서로 영향을 주지 않게 쓴다). 저장된 값도 함께 지운다
export function resetMockWishlist(): void {
  nextWishlistItemId = 1
  mockWishlist.clear()
}

/*
 * 설정·요금 수정값처럼 JSON으로 바꿀 수 있는 목업 값을 sessionStorage에 읽고 쓰는 도우미다(memberHandlers가 쓴다).
 * 읽기는 모양 검사를 호출한 쪽이 맡고, 여기서는 JSON 해석 실패만 null로 처리한다.
 */
export function readMockJson(key: string): unknown {
  const raw = readStored(key)
  if (raw === null) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

// 값을 JSON으로 적는다. null이면 저장된 값을 지운다
export function writeMockJson(key: string, value: unknown): void {
  writeStored(key, value === null ? null : JSON.stringify(value))
}
