import { delay, http, HttpResponse, type RequestHandler } from 'msw'
import type {
  ApplyOverviewDraftItem,
  ApplyOverviewDraftsRequest,
  ApplyOverviewDraftsResult,
  CommonResponseApplyOverviewDraftsResult,
  CommonResponseOverviewCandidatePage,
  CommonResponseOverviewDraft,
  CommonResponseBulkDraftRun,
  CommonResponseOverviewDraftPage,
  CommonResponseOverviewBulkRevertResult,
  CommonResponseOverviewRevertResult,
  CreateBulkOverviewDraftsRequest,
  CreateOverviewDraftRequest,
  EditOverviewDraftRequest,
  MediaType,
  OverviewBulkRevertRequest,
  OverviewCandidate,
  OverviewDraft,
  OverviewDraftStatus,
  OverviewDraftWarning,
  BulkDraftRun,
  BulkDraftRunStopReason,
  OverviewTranslateSummary,
} from '@/api/generated/model'
import { LIST_PAGE_SIZE, getDraftGrade } from '@/features/admin/overviewRules'
import { MOCK_SCENARIO_HEADER } from '@/lib/mockScenario'
import { denyDemo, problemResponse } from '@/mocks/handlerUtils'

/*
 * ===== 관리자 "LLM 줄거리 번역" 목업 핸들러 (adminHandlers 패턴) =====
 * 경로·요청·응답은 확정된 계약(docs/api/openapi.yaml)과 같다. 응답 타입은 생성 모델이라 계약과 어긋나면 컴파일에서 걸린다.
 * 오류 코드는 docs/api/error-codes.md의 LLM_* 코드다.
 *
 * - GET    /api/admin/llm/overview-candidates      번역 후보(+현황 summary). 정상: 2,512편, 찜 많은 순
 * - POST   /api/admin/llm/drafts                   작품 하나 번역 초안 만들기(교체)
 * - GET    /api/admin/llm/drafts                   초안 목록(status=PASSED|WARNING|FAILED/page/size) 또는 표본(sample, 성공만), 최근 일괄 진행 상황(bulkRun) 포함
 * - POST   /api/admin/llm/bulk-drafts            일괄 번역 시작
 * - POST   /api/admin/llm/drafts/apply             초안 반영(draftIds 또는 allPassed). 전부 반영은 성공 초안만, 주의는 draftIds로 승인
 * - PATCH  /api/admin/llm/drafts/:draftId          초안 번역문 수정(저장하면 등급을 다시 매긴다: http 포함 → 실패, "합니다/해요" 포함 → 주의, 너무 짧음 → 실패)
 * - DELETE /api/admin/llm/drafts/:draftId          초안 삭제(204)
 * - POST   /api/admin/llm/titles/:titleId/overview-revert  영어로 되돌리기
 * - POST   /api/admin/llm/overview-reverts         오래된 번역 대량 되돌리기(isPreview=true면 개수만 센다)
 *
 * 시나리오(X-Mock-Scenario 헤더 = 주소의 ?mockScenario=, 값은 정확히 일치할 때만 반응한다. 다른 핸들러는 llm-* 값에 반응하지 않는다):
 *   (없음) 정상 / llm-bulk-running / llm-bulk-stopped-disabled·-rate-limited·-daily-limit·-draft-limit·-call-failed·-server-restarted
 *   llm-many-failed / llm-disabled / llm-rate-limited / llm-cooldown / llm-daily-limit / llm-call-failed / llm-draft-limit
 *   llm-apply-conflict / llm-partial-apply / llm-not-revertible / llm-draft-gone / llm-empty / error(조회 500)
 *   llm-usage-near(사용량 340/400, 곧 상한) / llm-usage-reached(400/400, 생성 429) / llm-no-translated(반영한 번역 0편)
 * 데모 계정(DEMO) 역할의 쓰기 요청은 403 FORBIDDEN이다.
 *
 * 상태는 메모리에만 둔다(새로고침하면 처음으로 돌아간다). 시나리오가 바뀌면 처음 상태를 다시 만든다.
 */

const CANDIDATE_TOTAL = 2512 // 정상 시나리오의 번역 후보 수
const MODEL_NAME = 'gemini-2.5-flash'
const DAILY_LIMIT = 400
const PER_MINUTE_LIMIT = 12
const WRITE_DELAY_MS = 1200 // 번역 생성이 오래 걸리는 모습을 보이려는 지연(테스트는 0)

const MOCK_TITLES = [
  '기생충',
  '오징어 게임',
  '부산행',
  '올드보이',
  '더 글로리',
  '킹덤',
  '미나리',
  '밀수',
  '파묘',
  '살인의 추억',
  '범죄도시',
  '이상한 변호사 우영우',
]

const MOCK_ORIGINALS = [
  'All unemployed, Ki-taek and his family take peculiar interest in the wealthy and glamorous Parks, as they ingratiate themselves into their lives and get entangled in an unexpected incident.',
  'Hundreds of cash-strapped players accept a strange invitation to compete in children’s games. Inside, a tempting prize awaits with deadly high stakes: a survival game that has a 45.6 billion won prize at stake.',
  'A man, his estranged daughter and other passengers try to survive a zombie outbreak on a train to Busan in 2016.',
  'After being kidnapped and imprisoned for fifteen years, Oh Dae-Su is released, only to find that he must find his captor in five days.',
]

const MOCK_TRANSLATIONS = [
  '모두 백수인 기택의 가족은 부유하고 화려한 박 사장네 가족에게 묘한 관심을 갖고, 그들의 삶에 스며들다가 예상치 못한 사건에 휘말린다.',
  '돈에 쫓기는 수백 명의 참가자가 낯선 초대를 받아들여 어린이 놀이로 겨룬다. 그 안에는 목숨을 건 달콤한 상금, 456억 원이 걸린 생존 게임이 기다리고 있다.',
  '한 남자와 소원했던 딸, 그리고 다른 승객들이 부산행 열차 안에서 좀비 사태를 견뎌 내려 애쓴다.',
  '납치되어 15년을 갇혀 지낸 오대수는 풀려난 뒤 닷새 안에 자신을 가둔 자를 찾아야 한다는 사실을 알게 된다.',
]

// 검사 실패 이유 코드 9종(계약 OverviewDraft.guardFailures)
const ALL_GUARD_CODES = [
  'EMPTY',
  'TOO_LONG',
  'LENGTH_RATIO_LOW',
  'LENGTH_RATIO_HIGH',
  'HANGUL_RATIO_LOW',
  'CONTAINS_URL',
  'CONTAINS_MARKDOWN',
  'CONTAINS_PREAMBLE',
  'NUMBER_MISSING',
]

interface MockLlmState {
  scenario: string // 이 상태를 만든 시나리오(바뀌면 새로 만든다)
  drafts: OverviewDraft[] // 대기 중인 초안(만든 순서 오래된 것 먼저)
  appliedTitleIds: Set<number> // 반영해서 한국어가 된 작품
  appliedAt: Map<number, number> // 이 세션에 반영한 작품의 반영 시각(밀리초). 대량 되돌리기 대상 계산에 쓴다
  seedReverted: number // 대량 되돌리기로 지운 기존 번역 편수(가장 오래된 것부터 지운다)
  seedTotal: number // 처음부터 있던 반영 번역 편수(llm-no-translated는 0)
  revertedTitleIds: Set<number> // 영어로 되돌려 줄거리가 비워진 작품
  usedToday: number
  bulkRun: BulkDraftRun | null
  bulkCount: number // 진행 중인 일괄이 만들어야 하는 편수
  nextDraftSeq: number
  createdTitleIds: Set<number> // 이 세션에 새로 초안이 생긴 작품(후보의 "초안 있음" 표시에 쓴다)
}

let state: MockLlmState | null = null

function wait(ms = WRITE_DELAY_MS) {
  return delay(import.meta.env.MODE === 'test' ? 0 : ms)
}

function scenarioOf(request: Request): string {
  return request.headers.get(MOCK_SCENARIO_HEADER) ?? ''
}

// 후보 index(0부터)의 작품 ID. 번역 후보는 찜 많은 순이라 index가 작을수록 찜이 많다
function titleIdAt(index: number): number {
  return index + 1
}

function candidateAt(index: number, hasDraft: boolean): OverviewCandidate {
  const mediaType: MediaType = index % 3 === 2 ? 'tv' : 'movie'
  return {
    titleId: titleIdAt(index),
    mediaType,
    title: `${MOCK_TITLES[index % MOCK_TITLES.length]}${index >= MOCK_TITLES.length ? ` ${Math.floor(index / MOCK_TITLES.length) + 1}` : ''}`,
    overview: MOCK_ORIGINALS[index % MOCK_ORIGINALS.length],
    wishlistCount: Math.max(0, 180 - Math.floor(index / 8)),
    hasDraft,
  }
}

// 주의 이유 코드 4종(계약 OverviewDraft.warnings)
const ALL_WARNING_CODES: OverviewDraftWarning[] = [
  'SENTENCE_COUNT_MISMATCH',
  'STYLE_NOT_PLAIN',
  'SUSPICIOUS_SPACING',
  'LENGTH_RATIO_NEAR_LIMIT',
]

// 대량 되돌리기 목업: 반영한 번역 312편이 가장 오래된 반영일부터 12시간 간격으로 쌓여 있다고 본다
const SEED_TRANSLATED_COUNT = 312
const SEED_OLDEST_AT = Date.parse('2026-04-02T01:00:00Z')
const SEED_STEP_MS = 12 * 60 * 60 * 1000

// 처음 상태의 주의(승인 대기) 초안 수
const WARNING_DRAFT_COUNT = 6

// 가짜 초안 하나를 만든다. 등급별로: 성공은 이유 없음 / 주의는 주의 이유 1~2개(실패는 없음) / 실패는 검사 실패 이유를 failureCount개 단다
function buildDraft(
  index: number,
  grade: OverviewDraftStatus,
  seq: number,
  failureCount = 1,
): OverviewDraft {
  const candidate = candidateAt(index, true)
  const guardFailures =
    grade === 'FAILED'
      ? Array.from(
          { length: failureCount },
          (_, k) => ALL_GUARD_CODES[(seq + k) % ALL_GUARD_CODES.length],
        )
      : []
  const warnings: OverviewDraftWarning[] =
    grade === 'WARNING'
      ? [
          ALL_WARNING_CODES[seq % ALL_WARNING_CODES.length],
          ...(seq % 3 === 0 ? [ALL_WARNING_CODES[(seq + 1) % ALL_WARNING_CODES.length]] : []),
        ]
      : []
  const passedText = MOCK_TRANSLATIONS[index % MOCK_TRANSLATIONS.length]
  return {
    draftId: `00000000-0000-4000-8000-${String(seq).padStart(12, '0')}`,
    titleId: candidate.titleId,
    mediaType: candidate.mediaType,
    title: candidate.title,
    original: candidate.overview,
    translated:
      grade === 'PASSED'
        ? passedText
        : grade === 'WARNING'
          ? passedText.replace(/\.$/, '요.') // 말투가 섞인 번역처럼 보이게 어미를 바꾼다
          : index % 2 === 0
            ? 'Hundreds of players. **Here is** the translation: https://example.com'
            : '',
    isGuardPassed: grade !== 'FAILED',
    guardFailures,
    warnings,
    model: MODEL_NAME,
    createdAt: new Date(Date.UTC(2026, 9, 28, 1, 0, 0) + seq * 60_000).toISOString(),
    attempts: seq % 4 === 0 ? 2 : 1,
    isEdited: false,
  }
}

// 수정한 번역문을 다시 검사하는 흉내(실제 규칙이 아니라 화면 확인용): 주소가 있거나 너무 짧으면 실패, 존댓말이 섞이면 주의, 아니면 성공
function recheckTranslation(
  text: string,
): Pick<OverviewDraft, 'isGuardPassed' | 'guardFailures' | 'warnings'> {
  if (text.includes('http')) {
    return { isGuardPassed: false, guardFailures: ['CONTAINS_URL'], warnings: [] }
  }
  if (text.trim().length < 15) {
    return { isGuardPassed: false, guardFailures: ['LENGTH_RATIO_LOW'], warnings: [] }
  }
  if (/(합니다|해요)/.test(text)) {
    return { isGuardPassed: true, guardFailures: [], warnings: ['STYLE_NOT_PLAIN'] }
  }
  return { isGuardPassed: true, guardFailures: [], warnings: [] }
}

// 시나리오에 맞는 처음 상태를 만든다
function createInitialState(scenario: string): MockLlmState {
  const isEmpty = scenario === 'llm-empty'
  const failedCount = scenario === 'llm-many-failed' ? 40 : 5
  const drafts: OverviewDraft[] = []
  let seq = 1
  if (!isEmpty) {
    for (let i = 0; i < 30; i++) drafts.push(buildDraft(i, 'PASSED', seq++))
    for (let i = 0; i < WARNING_DRAFT_COUNT; i++) drafts.push(buildDraft(30 + i, 'WARNING', seq++))
    for (let i = 0; i < failedCount; i++) {
      // 여러 이유가 섞인 행을 만들려고 3번째마다 이유를 3개 단다
      drafts.push(buildDraft(30 + WARNING_DRAFT_COUNT + i, 'FAILED', seq++, i % 3 === 2 ? 3 : 1))
    }
  }

  let bulkRun: BulkDraftRun | null = isEmpty
    ? null
    : {
        bulkRunId: 'bulk-0001',
        state: 'COMPLETED',
        total: 20,
        doneCount: 18,
        failedCount: 1,
        guardFailedCount: 3,
        stopReason: null,
        startedAt: '2026-10-28T01:00:00Z',
        finishedAt: '2026-10-28T01:02:00Z',
        warningCount: 2,
      }
  if (scenario === 'llm-bulk-running') {
    bulkRun = {
      bulkRunId: 'bulk-0002',
      state: 'RUNNING',
      total: 20,
      doneCount: 0,
      failedCount: 0,
      guardFailedCount: 0,
      warningCount: 0,
      stopReason: null,
      startedAt: new Date().toISOString(),
      finishedAt: null,
    }
  }
  if (scenario.startsWith('llm-bulk-stopped-')) {
    const reasonByScenario: Record<string, NonNullable<BulkDraftRunStopReason>> = {
      'llm-bulk-stopped-disabled': 'DISABLED',
      'llm-bulk-stopped-rate-limited': 'RATE_LIMITED',
      'llm-bulk-stopped-daily-limit': 'DAILY_LIMIT',
      'llm-bulk-stopped-draft-limit': 'DRAFT_LIMIT',
      'llm-bulk-stopped-call-failed': 'CALL_FAILED_REPEATED',
      'llm-bulk-stopped-server-restarted': 'SERVER_RESTARTED',
    }
    const stopReason = reasonByScenario[scenario]
    if (stopReason) {
      bulkRun = {
        bulkRunId: 'bulk-0003',
        state: 'STOPPED',
        total: 20,
        doneCount: 9,
        failedCount: 3,
        guardFailedCount: 2,
        warningCount: 1,
        stopReason,
        startedAt: '2026-10-28T01:00:00Z',
        finishedAt: '2026-10-28T01:01:10Z',
      }
    }
  }

  return {
    scenario,
    drafts,
    appliedTitleIds: new Set(),
    appliedAt: new Map(),
    seedReverted: 0,
    seedTotal: scenario === 'llm-no-translated' ? 0 : SEED_TRANSLATED_COUNT,
    revertedTitleIds: new Set(),
    usedToday:
      scenario === 'llm-daily-limit' || scenario === 'llm-usage-reached'
        ? DAILY_LIMIT
        : scenario === 'llm-usage-near'
          ? 340
          : 37,
    bulkRun,
    bulkCount: 20,
    nextDraftSeq: seq,
    createdTitleIds: new Set(),
  }
}

function getState(request: Request): MockLlmState {
  const scenario = scenarioOf(request)
  if (!state || state.scenario !== scenario) state = createInitialState(scenario)
  return state
}

/** 테스트가 서로 영향을 주지 않게 목업 상태를 처음으로 돌린다. */
export function resetMockLlmState(): void {
  state = null
}

// 번역 후보 index 목록(반영·되돌린 작품은 영어가 아니라 후보에서 빠진다)
function candidateIndexes(s: MockLlmState): number[] {
  if (s.scenario === 'llm-empty') return []
  const indexes: number[] = []
  for (let i = 0; i < CANDIDATE_TOTAL; i++) {
    const id = titleIdAt(i)
    if (!s.appliedTitleIds.has(id) && !s.revertedTitleIds.has(id)) indexes.push(i)
  }
  return indexes
}

// 지금 반영돼 있는 번역 편수(처음부터 있던 것 + 이 세션에 반영한 것)
function translatedCountOf(s: MockLlmState): number {
  return s.seedTotal - s.seedReverted + s.appliedAt.size
}

// 가장 오래된 반영 시각. 번역이 하나도 없으면 null
function oldestTranslatedAtOf(s: MockLlmState): string | null {
  if (s.seedTotal - s.seedReverted > 0) {
    return new Date(SEED_OLDEST_AT + s.seedReverted * SEED_STEP_MS).toISOString()
  }
  const times = [...s.appliedAt.values()]
  return times.length > 0 ? new Date(Math.min(...times)).toISOString() : null
}

function summaryOf(s: MockLlmState): OverviewTranslateSummary {
  return {
    pendingTotal: candidateIndexes(s).length,
    usedToday: s.usedToday,
    dailyLimit: DAILY_LIMIT,
    perMinuteLimit: PER_MINUTE_LIMIT,
    model: MODEL_NAME,
    isEnabled: s.scenario !== 'llm-disabled',
    translatedCount: translatedCountOf(s),
    oldestTranslatedAt: oldestTranslatedAtOf(s),
  }
}

function toPageMeta(total: number, page: number, size: number) {
  return { page, size, totalElements: total, totalPages: Math.ceil(total / size) }
}

function readPage(request: Request): { page: number; size: number } {
  const params = new URL(request.url).searchParams
  const page = Number(params.get('page') ?? 0)
  const size = Number(params.get('size') ?? LIST_PAGE_SIZE)
  return {
    page: Number.isInteger(page) && page >= 0 ? page : 0,
    size: Number.isInteger(size) && size >= 1 && size <= 100 ? size : LIST_PAGE_SIZE,
  }
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

// 일괄 번역을 한 걸음 진행한다(목업 규칙: 초안 목록의 "검사 통과 개수" 조회(status=PASSED, size=1)를 읽을 때마다 진행).
// 후보 중 초안이 없는 작품에 초안을 만들고, 5번째마다 검사에 걸리게 한다. 다 하면 COMPLETED로 바꾼다
function advanceBulk(s: MockLlmState): void {
  const bulkRun = s.bulkRun
  if (!bulkRun || bulkRun.state !== 'RUNNING') return
  const step = Math.max(1, Math.ceil(bulkRun.total / 4))
  const draftTitleIds = new Set(s.drafts.map((d) => d.titleId))
  const targets = candidateIndexes(s).filter((i) => !draftTitleIds.has(titleIdAt(i)))
  for (let n = 0; n < step && bulkRun.doneCount + bulkRun.failedCount < bulkRun.total; n++) {
    const index = targets[n]
    if (index === undefined) break
    // 5번째마다 검사 실패, 7번째마다 주의, 나머지는 성공
    const order = bulkRun.doneCount + 1
    const grade: OverviewDraftStatus =
      order % 5 === 0 ? 'FAILED' : order % 7 === 0 ? 'WARNING' : 'PASSED'
    s.drafts.push(buildDraft(index, grade, s.nextDraftSeq++))
    s.createdTitleIds.add(titleIdAt(index))
    bulkRun.doneCount += 1
    if (grade === 'FAILED') bulkRun.guardFailedCount += 1
    if (grade === 'WARNING') bulkRun.warningCount += 1
    s.usedToday += 1
  }
  if (bulkRun.doneCount + bulkRun.failedCount >= bulkRun.total) {
    bulkRun.state = 'COMPLETED'
    bulkRun.finishedAt = new Date().toISOString()
  }
}

// 번역 호출을 막는 시나리오(생성·일괄 시작이 함께 쓴다). 막을 응답이 있으면 돌려주고, 아니면 undefined
function blockedCreateResponse(request: Request, scenario: string, isBulk: boolean) {
  switch (scenario) {
    case 'llm-disabled':
      return problemResponse(
        request,
        503,
        'Service Unavailable',
        'LLM_DISABLED',
        '번역 기능이 꺼져 있습니다.',
      )
    case 'llm-rate-limited':
      return problemResponse(
        request,
        429,
        'Too Many Requests',
        'LLM_RATE_LIMITED',
        '번역 호출이 분당 상한을 넘었습니다.',
      )
    case 'llm-cooldown':
      if (isBulk) return undefined
      return problemResponse(
        request,
        429,
        'Too Many Requests',
        'LLM_TITLE_COOLDOWN',
        '같은 작품은 잠시 뒤에 다시 번역할 수 있습니다.',
      )
    case 'llm-daily-limit':
    case 'llm-usage-reached':
      return problemResponse(
        request,
        429,
        'Too Many Requests',
        'LLM_DAILY_LIMIT_EXCEEDED',
        '오늘 번역 호출 상한을 모두 사용했습니다.',
      )
    case 'llm-call-failed':
      if (isBulk) return undefined
      return problemResponse(
        request,
        502,
        'Bad Gateway',
        'LLM_CALL_FAILED',
        '번역 호출에 실패했습니다.',
      )
    case 'llm-draft-limit':
      return problemResponse(
        request,
        409,
        'Conflict',
        'LLM_DRAFT_LIMIT_EXCEEDED',
        '대기 중인 초안이 가득 찼습니다.',
      )
    default:
      return undefined
  }
}

export const llmHandlers: RequestHandler[] = [
  // 번역 후보 + 번역 현황
  http.get('/api/admin/llm/overview-candidates', async ({ request }) => {
    await wait(300)
    if (scenarioOf(request) === 'error') return internalError(request)
    const s = getState(request)
    const { page, size } = readPage(request)
    const indexes = candidateIndexes(s)
    const draftTitleIds = new Set(s.drafts.map((d) => d.titleId))
    const content = indexes
      .slice(page * size, (page + 1) * size)
      .map((i) => candidateAt(i, draftTitleIds.has(titleIdAt(i))))
    const body: CommonResponseOverviewCandidatePage = {
      success: true,
      data: { ...toPageMeta(indexes.length, page, size), content, summary: summaryOf(s) },
    }
    return HttpResponse.json(body)
  }),

  // 초안 목록 / 표본
  http.get('/api/admin/llm/drafts', async ({ request }) => {
    await wait(300)
    if (scenarioOf(request) === 'error') return internalError(request)
    const s = getState(request)
    const params = new URL(request.url).searchParams
    const status = params.get('status')
    const sample = Number(params.get('sample'))
    // 일괄 진행은 "검사 통과 개수" 조회에서만 한 걸음씩 나아간다(두 개수 조회가 겹쳐 두 배로 빨라지지 않게)
    if (status === 'PASSED' && params.get('size') === '1') advanceBulk(s)

    if (sample > 0) {
      const passed = s.drafts.filter((d) => getDraftGrade(d) === 'PASSED')
      const shuffled = [...passed].sort(() => Math.random() - 0.5).slice(0, sample)
      const body: CommonResponseOverviewDraftPage = {
        success: true,
        data: {
          ...toPageMeta(shuffled.length, 0, sample),
          totalPages: 1,
          content: shuffled,
          bulkRun: s.bulkRun,
        },
      }
      return HttpResponse.json(body)
    }

    const { page, size } = readPage(request)
    const filtered =
      status === 'PASSED' || status === 'WARNING' || status === 'FAILED'
        ? s.drafts.filter((d) => getDraftGrade(d) === status)
        : s.drafts
    const body: CommonResponseOverviewDraftPage = {
      success: true,
      data: {
        ...toPageMeta(filtered.length, page, size),
        content: filtered.slice(page * size, (page + 1) * size),
        bulkRun: s.bulkRun,
      },
    }
    return HttpResponse.json(body)
  }),

  // 작품 하나 번역 초안 만들기
  http.post('/api/admin/llm/drafts', async ({ request }) => {
    await wait()
    const denied = denyDemo(request)
    if (denied) return denied
    const s = getState(request)
    const blocked = blockedCreateResponse(request, s.scenario, false)
    if (blocked) return blocked
    const { titleId } = (await request.json()) as CreateOverviewDraftRequest
    const index = titleId - 1
    if (!Number.isInteger(index) || index < 0 || index >= CANDIDATE_TOTAL) {
      return problemResponse(
        request,
        404,
        'Not Found',
        'TITLE_NOT_FOUND',
        '작품을 찾을 수 없습니다.',
      )
    }
    if (s.appliedTitleIds.has(titleId) || s.revertedTitleIds.has(titleId)) {
      return problemResponse(
        request,
        409,
        'Conflict',
        'LLM_NOT_TRANSLATABLE',
        '영어 줄거리가 있는 작품만 번역할 수 있습니다.',
      )
    }
    // 같은 작품의 기존 초안은 교체한다. 작품 번호가 4의 배수이면 실패, 6의 배수이면 주의가 되게 해 등급 안내를 모두 볼 수 있게 한다
    const grade: OverviewDraftStatus =
      titleId % 4 === 0 ? 'FAILED' : titleId % 6 === 0 ? 'WARNING' : 'PASSED'
    const draft = buildDraft(index, grade, s.nextDraftSeq++)
    s.drafts = [...s.drafts.filter((d) => d.titleId !== titleId), draft]
    s.createdTitleIds.add(titleId)
    s.usedToday += 1
    const body: CommonResponseOverviewDraft = { success: true, data: draft }
    return HttpResponse.json(body)
  }),

  // 일괄 번역 시작(202)
  http.post('/api/admin/llm/bulk-drafts', async ({ request }) => {
    await wait(400)
    const denied = denyDemo(request)
    if (denied) return denied
    const s = getState(request)
    const blocked = blockedCreateResponse(request, s.scenario, true)
    if (blocked) return blocked
    if (s.bulkRun?.state === 'RUNNING') {
      return problemResponse(
        request,
        409,
        'Conflict',
        'LLM_BULK_RUNNING',
        '일괄 번역이 이미 실행 중입니다.',
      )
    }
    const { count } = (await request.json()) as CreateBulkOverviewDraftsRequest
    s.bulkRun = {
      bulkRunId: `bulk-${String(s.nextDraftSeq).padStart(4, '0')}`,
      state: 'RUNNING',
      total: count ?? 20,
      doneCount: 0,
      failedCount: 0,
      guardFailedCount: 0,
      warningCount: 0,
      stopReason: null,
      startedAt: new Date().toISOString(),
      finishedAt: null,
    }
    const body: CommonResponseBulkDraftRun = { success: true, data: s.bulkRun }
    return HttpResponse.json(body, { status: 202 })
  }),

  // 초안 반영. 반영 순간의 상태로 건별 결과를 정한다
  http.post('/api/admin/llm/drafts/apply', async ({ request }) => {
    await wait(600)
    const denied = denyDemo(request)
    if (denied) return denied
    const s = getState(request)
    if (s.scenario === 'llm-apply-conflict') {
      return problemResponse(
        request,
        409,
        'Conflict',
        'LLM_APPLY_CONFLICT',
        '수집·재계산·정리가 실행 중이라 줄거리를 바꿀 수 없습니다.',
      )
    }
    const { draftIds, allPassed } = (await request.json()) as ApplyOverviewDraftsRequest
    const targetIds = allPassed
      ? s.drafts.filter((d) => getDraftGrade(d) === 'PASSED').map((d) => d.draftId)
      : [...new Set(draftIds ?? [])]

    const results: ApplyOverviewDraftItem[] = targetIds.map((draftId, i) => {
      const draft = s.drafts.find((d) => d.draftId === draftId)
      if (!draft) return { draftId, titleId: null, outcome: 'SKIPPED', reason: 'DRAFT_NOT_FOUND' }
      if (!draft.isGuardPassed)
        return { draftId, titleId: draft.titleId, outcome: 'SKIPPED', reason: 'GUARD_FAILED' }
      // llm-partial-apply: 세 가지 건너뜀 이유가 섞이도록 앞쪽 몇 건을 강제로 건너뛴다
      if (s.scenario === 'llm-partial-apply' && i < 3) {
        const reasons = ['DRAFT_NOT_FOUND', 'GUARD_FAILED', 'ORIGINAL_CHANGED'] as const
        return { draftId, titleId: draft.titleId, outcome: 'SKIPPED', reason: reasons[i] }
      }
      return { draftId, titleId: draft.titleId, outcome: 'APPLIED', reason: null }
    })
    const appliedIds = new Set(results.filter((r) => r.outcome === 'APPLIED').map((r) => r.draftId))
    for (const r of results) {
      if (r.outcome === 'APPLIED' && r.titleId !== null) {
        s.appliedTitleIds.add(r.titleId)
        s.appliedAt.set(r.titleId, Date.now())
      }
    }
    s.drafts = s.drafts.filter((d) => !appliedIds.has(d.draftId))

    const data: ApplyOverviewDraftsResult = {
      appliedCount: appliedIds.size,
      skippedCount: results.length - appliedIds.size,
      results,
    }
    const body: CommonResponseApplyOverviewDraftsResult = { success: true, data }
    return HttpResponse.json(body)
  }),

  // 초안 번역문 수정. 저장하면 서버가 자동 검사를 다시 돌려 새 등급의 초안을 돌려준다(isEdited=true)
  http.patch('/api/admin/llm/drafts/:draftId', async ({ request, params }) => {
    await wait(500)
    const denied = denyDemo(request)
    if (denied) return denied
    const s = getState(request)
    const draft = s.drafts.find((d) => d.draftId === String(params.draftId))
    if (!draft) {
      return problemResponse(
        request,
        404,
        'Not Found',
        'LLM_DRAFT_NOT_FOUND',
        '번역 초안을 찾을 수 없습니다.',
      )
    }
    const { translatedText } = (await request.json()) as EditOverviewDraftRequest
    if (translatedText.trim() === '' || translatedText.length > 1000) {
      return problemResponse(
        request,
        400,
        'Bad Request',
        'VALIDATION_FAILED',
        '번역문은 1자 이상 1000자 이하여야 합니다.',
      )
    }
    Object.assign(draft, {
      translated: translatedText,
      ...recheckTranslation(translatedText),
      isEdited: true,
    })
    const body: CommonResponseOverviewDraft = { success: true, data: { ...draft } }
    return HttpResponse.json(body)
  }),

  // 초안 삭제(204). llm-draft-gone이면 이미 지워졌다는 404를 주고 다른 초안 몇 건도 목록에서 사라진다
  http.delete('/api/admin/llm/drafts/:draftId', async ({ request, params }) => {
    await wait(300)
    const denied = denyDemo(request)
    if (denied) return denied
    const s = getState(request)
    const draftId = String(params.draftId)
    if (s.scenario === 'llm-draft-gone') {
      s.drafts = s.drafts.filter((d) => d.draftId !== draftId).slice(3)
      return problemResponse(
        request,
        404,
        'Not Found',
        'LLM_DRAFT_NOT_FOUND',
        '번역 초안을 찾을 수 없습니다.',
      )
    }
    if (!s.drafts.some((d) => d.draftId === draftId)) {
      return problemResponse(
        request,
        404,
        'Not Found',
        'LLM_DRAFT_NOT_FOUND',
        '번역 초안을 찾을 수 없습니다.',
      )
    }
    s.drafts = s.drafts.filter((d) => d.draftId !== draftId)
    return new HttpResponse(null, { status: 204 })
  }),

  // 영어 줄거리로 되돌리기. 반영해서 한국어가 된 작품만 되돌릴 수 있다
  http.post('/api/admin/llm/titles/:titleId/overview-revert', async ({ request, params }) => {
    await wait(400)
    const denied = denyDemo(request)
    if (denied) return denied
    const s = getState(request)
    const titleId = Number(params.titleId)
    if (!Number.isInteger(titleId) || titleId < 1 || titleId > CANDIDATE_TOTAL) {
      return problemResponse(
        request,
        404,
        'Not Found',
        'TITLE_NOT_FOUND',
        '작품을 찾을 수 없습니다.',
      )
    }
    if (s.scenario === 'llm-apply-conflict') {
      return problemResponse(
        request,
        409,
        'Conflict',
        'LLM_APPLY_CONFLICT',
        '수집·재계산·정리가 실행 중이라 줄거리를 바꿀 수 없습니다.',
      )
    }
    if (s.scenario === 'llm-not-revertible' || !s.appliedTitleIds.has(titleId)) {
      return problemResponse(
        request,
        409,
        'Conflict',
        'LLM_NOT_REVERTIBLE',
        '관리자가 반영한 번역 줄거리만 영어로 되돌릴 수 있습니다.',
      )
    }
    s.appliedTitleIds.delete(titleId)
    s.appliedAt.delete(titleId)
    s.revertedTitleIds.add(titleId)
    const body: CommonResponseOverviewRevertResult = { success: true, data: { titleId } }
    return HttpResponse.json(body)
  }),

  // 오래된 번역 대량 되돌리기. isPreview=true면 DB를 바꾸지 않고 대상 수만 센다.
  // 기존 번역 312편은 12시간 간격으로 쌓여 있다고 보고, translatedBefore보다 먼저 반영한 편수를 센다(translatedBefore가 없으면 전부)
  http.post('/api/admin/llm/overview-reverts', async ({ request }) => {
    await wait(400)
    const denied = denyDemo(request)
    if (denied) return denied
    const s = getState(request)
    if (s.scenario === 'llm-apply-conflict') {
      return problemResponse(
        request,
        409,
        'Conflict',
        'LLM_APPLY_CONFLICT',
        '수집·재계산·정리가 실행 중이라 줄거리를 바꿀 수 없습니다.',
      )
    }
    const { translatedBefore, isPreview } = (await request.json()) as OverviewBulkRevertRequest
    const before = translatedBefore ? Date.parse(translatedBefore) : Number.POSITIVE_INFINITY
    if (Number.isNaN(before)) {
      return problemResponse(
        request,
        400,
        'Bad Request',
        'INVALID_REQUEST',
        'translatedBefore 날짜 형식이 올바르지 않습니다.',
      )
    }
    // 1. 기존 번역 중 대상 편수(가장 오래된 것부터 시각이 before보다 이른 것)
    const seedLeft = s.seedTotal - s.seedReverted
    const seedTargets =
      before === Number.POSITIVE_INFINITY
        ? seedLeft
        : Math.max(
            0,
            Math.min(
              seedLeft,
              Math.ceil((before - SEED_OLDEST_AT) / SEED_STEP_MS) - s.seedReverted,
            ),
          )
    // 2. 이 세션에 반영한 번역 중 대상
    const appliedTargets = [...s.appliedAt.entries()]
      .filter(([, at]) => at < before)
      .map(([id]) => id)
    const revertedCount = seedTargets + appliedTargets.length
    // 3. 확정이면 실제로 지운다
    if (!isPreview) {
      s.seedReverted += seedTargets
      for (const id of appliedTargets) {
        s.appliedAt.delete(id)
        s.appliedTitleIds.delete(id)
        s.revertedTitleIds.add(id)
      }
    }
    const body: CommonResponseOverviewBulkRevertResult = {
      success: true,
      data: { revertedCount, isPreview: isPreview === true },
    }
    return HttpResponse.json(body)
  }),
]
