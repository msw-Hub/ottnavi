import {
  keepPreviousData,
  useMutation,
  useQueryClient,
  type QueryClient,
} from '@tanstack/react-query'
import type {
  ApplyOverviewDraftsRequest,
  ApplyOverviewDraftsResult,
  CommonResponseOverviewDraftPage,
  CreateBulkOverviewDraftsRequest,
  OverviewBulkRevertRequest,
  OverviewBulkRevertResult,
  OverviewDraft,
  BulkDraftRun,
  OverviewDraftStatus,
  OverviewRevertResult,
} from '@/api/generated/model'
import {
  applyOverviewDrafts,
  createOverviewDraft,
  createBulkOverviewDrafts,
  deleteOverviewDraft,
  editOverviewDraft,
  getListOverviewCandidatesQueryKey,
  getListOverviewDraftsQueryKey,
  revertTitleOverview,
  revertTranslatedOverviews,
  useListOverviewCandidates,
  useListOverviewDrafts,
} from '@/api/generated/admin/admin'
import type { ApiError } from '@/api/http'
import { getMockScenarioParams } from '@/lib/mockScenario'
import { BULK_POLL_INTERVAL_MS, LIST_PAGE_SIZE, SAMPLE_SIZE } from '@/features/admin/overviewRules'

/*
 * 관리자 "LLM 줄거리 번역"(/admin/llm) 데이터 훅 모음이다. 확정 계약의 생성 훅·함수를 감싼다.
 * 실제 권한 검사는 백엔드(/api/admin/** ADMIN, 데모 계정은 쓰기 403)가 한다.
 *
 * 쿼리 키 규칙: 모든 키가 '/admin/llm'으로 시작한다(생성 키 /admin/llm/overview-candidates, /admin/llm/drafts).
 * 쓰기가 성공하면 이 접두어의 캐시를 무효화해 현황·후보·초안 목록·개수를 한꺼번에 새로 불러온다.
 * 단, 표본 조회는 제외한다. 표본은 호출마다 무작위로 다른 초안을 주므로 자동으로 다시 불러오면
 * 운영자가 읽던 카드가 갑자기 바뀐다. 표본은 [다른 표본 뽑기]를 눌렀을 때만 새로 받는다.
 */

const LLM_QUERY_KEY_PREFIX = '/admin/llm'
const SAMPLE_KEY_TAG = 'sample' // 표본 조회 키에 넣는 표시. 무효화에서 걸러내는 데 쓴다

// 키가 표본 조회의 것인지 확인한다(생성 키 ['/admin/llm/drafts', 'sample', ...] 모양)
function isSampleKey(queryKey: readonly unknown[]): boolean {
  return queryKey[1] === SAMPLE_KEY_TAG
}

/** 번역 후보 한 쪽(+ 번역 현황 summary)을 불러온다. 쪽을 넘길 때 이전 쪽을 보여 주며 기다려 화면이 깜빡이지 않는다. */
export function useOverviewCandidates(page: number) {
  const params = { page, size: LIST_PAGE_SIZE }
  return useListOverviewCandidates(params, {
    query: {
      queryKey: [...getListOverviewCandidatesQueryKey(params), getMockScenarioParams()],
      select: (res) => res.data,
      placeholderData: keepPreviousData,
    },
  })
}

// 일괄 번역이 실행 중이면 5초마다 다시 불러오고, 아니면 멈춘다. 새로고침해도 서버의 bulk를 읽어 폴링이 이어진다
function pollWhileBulkRunning(query: {
  state: { data?: CommonResponseOverviewDraftPage }
}): number | false {
  return query.state.data?.data.bulkRun?.state === 'RUNNING' ? BULK_POLL_INTERVAL_MS : false
}

// 등급별 개수 조회 하나(size=1로 한 건만 받고 PageMeta.totalElements만 쓴다). 일괄 실행 중에는 5초마다 다시 불러온다
function useDraftCountQuery(status: OverviewDraftStatus) {
  const params = { status, page: 0, size: 1 }
  return useListOverviewDrafts(params, {
    query: {
      queryKey: [...getListOverviewDraftsQueryKey(params), getMockScenarioParams()],
      select: (res) => res.data,
      refetchInterval: pollWhileBulkRunning,
    },
  })
}

/**
 * 초안 개수(성공·주의·실패)와 일괄 번역 진행 상황을 불러온다.
 * 개수는 size=1로 한 건만 받고 PageMeta.totalElements만 쓴다(목록 본문은 탭이 따로 불러온다).
 * 일괄 진행 상황(bulkRun)은 모든 응답에 실려 있어 성공 쪽 응답을 쓴다.
 * 실행 중에는 세 조회 모두 5초마다 다시 불러와 개수도 함께 오른다.
 */
export function useOverviewDraftCounts() {
  const passed = useDraftCountQuery('PASSED')
  const warning = useDraftCountQuery('WARNING')
  const failed = useDraftCountQuery('FAILED')
  const bulkRun: BulkDraftRun | null = passed.data?.bulkRun ?? null
  return {
    passedTotal: passed.data?.totalElements ?? null,
    warningTotal: warning.data?.totalElements ?? null,
    failedTotal: failed.data?.totalElements ?? null,
    bulkRun,
    lastCheckedAt: passed.dataUpdatedAt, // 마지막으로 서버에서 받은 시각(밀리초). 진행 줄의 "마지막 확인"에 쓴다
    isPending: passed.isPending || warning.isPending || failed.isPending,
    isError: passed.isError || warning.isError || failed.isError,
    error: passed.error ?? warning.error ?? failed.error,
    refetch: () => Promise.all([passed.refetch(), warning.refetch(), failed.refetch()]),
  }
}

/** 등급별 초안 목록 한 쪽을 불러온다(성공·승인 대기·실패 탭). */
export function useOverviewDrafts(status: OverviewDraftStatus, page: number) {
  const params = { status, page, size: LIST_PAGE_SIZE }
  return useListOverviewDrafts(params, {
    query: {
      queryKey: [...getListOverviewDraftsQueryKey(params), getMockScenarioParams()],
      select: (res) => res.data,
      placeholderData: keepPreviousData,
    },
  })
}

/**
 * 성공 초안 중 무작위 표본을 받는다(sample=10, page·size는 서버가 무시한다).
 * 자동으로 다시 받지 않는 이유: 호출마다 다른 초안이 오므로 창을 다시 보거나 네트워크가 되살아날 때 다시 받으면
 * 읽던 카드가 바뀐다. staleTime Infinity + 포커스·재연결 재조회 끄기로 막고, [다른 표본 뽑기]의 refetch로만 새로 받는다.
 */
export function useOverviewDraftSample() {
  const params = { sample: SAMPLE_SIZE }
  return useListOverviewDrafts(params, {
    query: {
      queryKey: [
        ...getListOverviewDraftsQueryKey(),
        SAMPLE_KEY_TAG,
        SAMPLE_SIZE,
        getMockScenarioParams(),
      ],
      select: (res) => res.data,
      staleTime: Infinity,
      refetchOnWindowFocus: false,
      refetchOnReconnect: false,
    },
  })
}

// 표본 캐시에서 지정한 초안을 뺀다. 반영·삭제한 카드가 표본에 계속 남지 않게 하되, 표본을 다시 뽑지는 않는다
function removeFromSampleCache(queryClient: QueryClient, draftIds: readonly string[]) {
  const removed = new Set(draftIds)
  queryClient.setQueriesData<CommonResponseOverviewDraftPage>(
    { queryKey: [LLM_QUERY_KEY_PREFIX + '/drafts', SAMPLE_KEY_TAG] },
    (old) =>
      old
        ? {
            ...old,
            data: { ...old.data, content: old.data.content.filter((d) => !removed.has(d.draftId)) },
          }
        : old,
  )
}

/**
 * 지금 캐시에 있는 모든 초안 목록(표본·통과·실패 쪽)에서 "초안 ID → 작품 제목" 표를 만드는 함수를 돌려준다.
 * 반영 응답에는 제목이 없어서, 반영 결과 화면이 제목을 보이려면 반영하기 전에 이 표를 떠 두어야 한다
 * (반영 직후에는 반영된 초안이 목록에서 사라지기 때문이다). 캐시에 없는 초안은 표에 없다.
 */
export function useDraftTitleSnapshot() {
  const queryClient = useQueryClient()
  return (): Map<string, string> => {
    const titles = new Map<string, string>()
    const cached = queryClient.getQueriesData<CommonResponseOverviewDraftPage>({
      queryKey: [LLM_QUERY_KEY_PREFIX + '/drafts'],
    })
    for (const [, page] of cached) {
      for (const draft of page?.data.content ?? []) titles.set(draft.draftId, draft.title)
    }
    return titles
  }
}

/** 표본을 뺀 모든 LLM 조회(현황·후보·초안 목록·개수)를 무효화하는 함수를 돌려준다. */
export function useInvalidateOverviewQueries() {
  const queryClient = useQueryClient()
  return () =>
    queryClient.invalidateQueries({
      predicate: (query) =>
        typeof query.queryKey[0] === 'string' &&
        query.queryKey[0].startsWith(LLM_QUERY_KEY_PREFIX) &&
        !isSampleKey(query.queryKey),
    })
}

// 429 전역 토스트("요청이 많습니다")를 끄는 요청 옵션. 번역 생성은 429가 LLM_RATE_LIMITED·LLM_TITLE_COOLDOWN·LLM_DAILY_LIMIT_EXCEEDED로
// 오는데, 이 화면이 코드별 정확한 문장을 따로 보이므로 일반 안내가 겹쳐 뜨지 않게 한다(api/http.ts의 _skipRateLimitToast)
const SKIP_RATE_LIMIT_TOAST = { _skipRateLimitToast: true } as const

/** 작품 하나의 번역 초안을 만든다(POST /admin/llm/drafts). Gemini를 부르므로 최대 1분쯤 걸린다. 같은 작품의 기존 초안은 교체된다. */
export function useCreateOverviewDraft() {
  const invalidate = useInvalidateOverviewQueries()
  return useMutation<OverviewDraft, ApiError, { titleId: number }>({
    mutationFn: ({ titleId }) =>
      createOverviewDraft({ titleId }, SKIP_RATE_LIMIT_TOAST).then((res) => res.data),
    onSuccess: invalidate,
  })
}

/** 일괄 번역을 시작한다(POST /admin/llm/bulk-drafts). 접수 뒤 서버에서 계속 진행되고 진행 상황은 초안 목록 응답의 bulk로 본다. */
export function useStartBulkOverviewDrafts() {
  const invalidate = useInvalidateOverviewQueries()
  return useMutation<BulkDraftRun, ApiError, CreateBulkOverviewDraftsRequest>({
    mutationFn: (data) =>
      createBulkOverviewDrafts(data, SKIP_RATE_LIMIT_TOAST).then((res) => res.data),
    // 성공해도 실패(이미 실행 중)해도 진행 상황을 다시 불러온다. 실패 쪽은 화면이 onError에서 부른다
    onSuccess: invalidate,
  })
}

/** 초안을 반영한다(POST /admin/llm/drafts/apply). 선택 반영은 draftIds, 전부 반영은 allPassed:true. 건별 결과가 온다. */
export function useApplyOverviewDrafts() {
  const queryClient = useQueryClient()
  const invalidate = useInvalidateOverviewQueries()
  return useMutation<ApplyOverviewDraftsResult, ApiError, ApplyOverviewDraftsRequest>({
    mutationFn: (data) => applyOverviewDrafts(data).then((res) => res.data),
    onSuccess: (result) => {
      // 반영된 초안은 표본 카드에서 뺀다(표본 자체는 다시 뽑지 않는다)
      removeFromSampleCache(
        queryClient,
        result.results.filter((r) => r.outcome === 'APPLIED').map((r) => r.draftId),
      )
      return invalidate()
    },
  })
}

/** 초안 하나를 삭제한다(DELETE /admin/llm/drafts/{draftId}, 204). 이미 없으면 404 LLM_DRAFT_NOT_FOUND다. */
export function useDeleteOverviewDraft() {
  const queryClient = useQueryClient()
  const invalidate = useInvalidateOverviewQueries()
  return useMutation<void, ApiError, { draftId: string }>({
    mutationFn: ({ draftId }) => deleteOverviewDraft(draftId),
    onSuccess: (_data, { draftId }) => {
      removeFromSampleCache(queryClient, [draftId])
      return invalidate()
    },
    // 이미 지워진 초안(404)도 목록은 새로 맞춘다
    onError: (_error, { draftId }) => {
      removeFromSampleCache(queryClient, [draftId])
      return invalidate()
    },
  })
}

/**
 * 초안의 번역문을 직접 고친다(PATCH /admin/llm/drafts/{draftId}). 서버가 저장하고 자동 검사를 다시 돌려 새 등급의 초안을 돌려준다.
 * 성공해도 여기서 목록을 무효화하지 않는다: 저장하자마자 항목이 다른 탭으로 옮겨 가면 운영자가 새 검사 결과를 읽지 못한다.
 * 수정 칸을 닫을 때 화면이 useInvalidateOverviewQueries로 목록·개수를 새로 불러온다(낙관적 갱신 없이 서버 값을 다시 받는다).
 */
export function useEditOverviewDraft() {
  return useMutation<OverviewDraft, ApiError, { draftId: string; translatedText: string }>({
    mutationFn: ({ draftId, translatedText }) =>
      editOverviewDraft(draftId, { translatedText }).then((res) => res.data),
  })
}

/**
 * 반영한 번역 줄거리를 한꺼번에 영어로 되돌린다(POST /admin/llm/overview-reverts).
 * isPreview=true면 DB를 바꾸지 않고 대상 수만 센다. 그때는 아무것도 바뀌지 않았으므로 캐시를 무효화하지 않는다.
 * 실제로 되돌렸으면 번역 현황(번역 수·가장 오래된 반영)과 후보 목록이 달라지므로 LLM 조회를 다시 불러온다.
 */
export function useRevertTranslatedOverviews() {
  const invalidate = useInvalidateOverviewQueries()
  return useMutation<OverviewBulkRevertResult, ApiError, OverviewBulkRevertRequest>({
    mutationFn: (data) => revertTranslatedOverviews(data).then((res) => res.data),
    onSuccess: (result) => (result.isPreview ? undefined : invalidate()),
  })
}

/** 작품의 한국어 줄거리를 영어로 되돌린다(POST /admin/llm/titles/{titleId}/overview-revert). 줄거리가 비워지고 다음 수집이 영어를 채운다. */
export function useRevertTitleOverview() {
  const invalidate = useInvalidateOverviewQueries()
  return useMutation<OverviewRevertResult, ApiError, { titleId: number }>({
    mutationFn: ({ titleId }) => revertTitleOverview(titleId).then((res) => res.data),
    onSuccess: invalidate,
  })
}
