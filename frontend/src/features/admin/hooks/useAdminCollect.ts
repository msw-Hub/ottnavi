import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { customInstance, type ApiError } from '@/api/http'
import { getMockScenarioParams } from '@/lib/mockScenario'
import type {
  CollectChangeList,
  CollectExecution,
  CollectExecutionList,
  CommonResponseCollectChangeList,
  CommonResponseCollectExecution,
  CommonResponseCollectExecutionList,
  CommonResponseRunCollectAccepted,
  RunCollectAccepted,
  RunCollectRequest,
} from '@/features/admin/mockTypes'

/*
 * 수집 실행·이력(SCR-16) 데이터 훅 모음이다.
 *
 * 임시 구현: 이 API(/admin/collect 아래)는 아직 계약에 없어 생성 훅이 없다. Task 031에서 생성 훅으로 바뀐다.
 * 실제 권한 검사는 백엔드(/api/admin/** ADMIN, Task 053·057)가 한다.
 */

// 이력 캐시 키의 앞부분. 쓰기가 성공하면 이 키로 시작하는 캐시(이력, 변경된 작품)를 모두 무효화한다
const COLLECT_QUERY_KEY = '/admin/collect'

/**
 * 수집 실행 이력을 불러온다(최신 순).
 * 자동으로 다시 불러오지 않는다: 수집은 몇 분~한 시간 걸리는 일이라, 운영자가 [새로 고침]을 눌러 확인하는 흐름으로 두었다.
 * (창으로 돌아올 때 자동으로 다시 불러오는 기본 동작은 켜져 있다.)
 */
export function useCollectExecutions() {
  return useQuery<CommonResponseCollectExecutionList, ApiError, CollectExecutionList>({
    queryKey: [COLLECT_QUERY_KEY, getMockScenarioParams()],
    queryFn: ({ signal }) =>
      customInstance<CommonResponseCollectExecutionList>({
        url: '/admin/collect',
        method: 'GET',
        params: getMockScenarioParams(),
        signal,
      }),
    select: (res) => res.data,
  })
}

/**
 * 실행 하나에서 제공 상태가 바뀐 작품 목록을 불러온다.
 * isEnabled가 false면 요청하지 않는다(펼치지 않은 행, 아직 실행 중인 행은 불러올 필요가 없다).
 */
export function useCollectChanges(executionId: number, isEnabled: boolean) {
  return useQuery<CommonResponseCollectChangeList, ApiError, CollectChangeList>({
    queryKey: [COLLECT_QUERY_KEY, 'changes', executionId],
    queryFn: ({ signal }) =>
      customInstance<CommonResponseCollectChangeList>({
        url: `/admin/collect/${executionId}/changes`,
        method: 'GET',
        signal,
      }),
    select: (res) => res.data,
    enabled: isEnabled,
  })
}

function useInvalidateCollect() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: [COLLECT_QUERY_KEY] })
}

/** 수집을 수동으로 실행한다(POST /admin/collect). 202로 접수되고 결과는 이력에서 본다. */
export function useRunCollect() {
  const invalidate = useInvalidateCollect()
  return useMutation<RunCollectAccepted, ApiError, RunCollectRequest>({
    mutationFn: (data) =>
      customInstance<CommonResponseRunCollectAccepted>({
        url: '/admin/collect',
        method: 'POST',
        data,
        // run-error 같은 목업 시나리오를 실행 요청에도 실어 보낸다(운영에서는 빈 객체)
        params: getMockScenarioParams(),
      }).then((res) => res.data),
    onSuccess: invalidate,
  })
}

/** 중단됨 실행을 실패로 기록한다(POST /admin/collect/{executionId}/recover, 제안). */
export function useRecoverCollect() {
  const invalidate = useInvalidateCollect()
  return useMutation<CollectExecution, ApiError, { executionId: number }>({
    mutationFn: ({ executionId }) =>
      customInstance<CommonResponseCollectExecution>({
        url: `/admin/collect/${executionId}/recover`,
        method: 'POST',
      }).then((res) => res.data),
    onSuccess: invalidate,
  })
}

/** 실패한 실행을 같은 날짜·범위로 다시 실행한다(POST /admin/collect/{executionId}/restart, 제안). 새 실행 ID가 온다. */
export function useRestartCollect() {
  const invalidate = useInvalidateCollect()
  return useMutation<RunCollectAccepted, ApiError, { executionId: number }>({
    mutationFn: ({ executionId }) =>
      customInstance<CommonResponseRunCollectAccepted>({
        url: `/admin/collect/${executionId}/restart`,
        method: 'POST',
        params: getMockScenarioParams(),
      }).then((res) => res.data),
    onSuccess: invalidate,
  })
}
