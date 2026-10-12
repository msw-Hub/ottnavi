import { useMutation, useQueryClient } from '@tanstack/react-query'
import type {
  CollectExecution,
  CollectExecutionAccepted,
  CreateCollectExecutionRequest,
} from '@/api/generated/model'
import {
  createCollectExecution,
  recoverCollectExecution,
  restartCollectExecution,
  useListCollectExecutionChanges,
  useListCollectExecutions,
} from '@/api/generated/admin/admin'
import type { ApiError } from '@/api/http'
import { getMockScenarioParams } from '@/lib/mockScenario'

/*
 * 수집 실행·이력(SCR-16) 데이터 훅 모음이다. 확정 계약의 생성 훅·함수를 감싼다(경로는 /admin/collect/executions 아래).
 * 실제 권한 검사는 백엔드(/api/admin/** ADMIN, Task 053·057)가 한다.
 */

// 이력 캐시 키의 앞부분. 쓰기가 성공하면 이 키로 시작하는 캐시(이력, 변경된 작품)를 모두 무효화한다
const COLLECT_QUERY_KEY = '/admin/collect/executions'

// 한 번에 가져오는 개수. 계약 최대값(100)이다. 이 화면에는 쪽 넘김 조작이 없어(새 화면 문구를 만들지 않으려고) 첫 쪽에 최대로 담아 받는다.
// 이력·변경 작품이 100건을 넘는 규모는 이 목업 단계에 없고, 운영에서 필요해지면 쪽 넘김 UI를 따로 정한다
const FIRST_PAGE_PARAMS = { page: 0, size: 100 } as const

/**
 * 수집 실행 이력(페이지 형태: content/page/size/totalElements/totalPages, 대상 날짜 내림차순)을 불러온다(계약 listCollectExecutions).
 * 자동으로 다시 불러오지 않는다: 수집은 몇 분~한 시간 걸리는 일이라, 운영자가 [새로 고침]을 눌러 확인하는 흐름으로 두었다.
 * (창으로 돌아올 때 자동으로 다시 불러오는 기본 동작은 켜져 있다.)
 */
export function useCollectExecutions() {
  return useListCollectExecutions(FIRST_PAGE_PARAMS, {
    query: {
      queryKey: [COLLECT_QUERY_KEY, FIRST_PAGE_PARAMS, getMockScenarioParams()],
      select: (res) => res.data,
    },
  })
}

/**
 * 실행 하나에서 제공 상태가 바뀐 작품 목록(페이지 형태)을 불러온다(계약 listCollectExecutionChanges).
 * isEnabled가 false면 요청하지 않는다(펼치지 않은 행, 아직 실행 중인 행은 불러올 필요가 없다).
 */
export function useCollectChanges(executionId: number, isEnabled: boolean) {
  return useListCollectExecutionChanges(executionId, FIRST_PAGE_PARAMS, {
    query: {
      queryKey: [COLLECT_QUERY_KEY, 'changes', executionId, FIRST_PAGE_PARAMS],
      select: (res) => res.data,
      enabled: isEnabled,
    },
  })
}

function useInvalidateCollect() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: [COLLECT_QUERY_KEY] })
}

/** 수집을 수동으로 실행한다(POST /admin/collect/executions). 202로 접수되고 결과는 이력에서 본다. 대상 날짜를 비우면 서버가 오늘로 본다. */
export function useRunCollect() {
  const invalidate = useInvalidateCollect()
  return useMutation<CollectExecutionAccepted, ApiError, CreateCollectExecutionRequest>({
    mutationFn: (data) => createCollectExecution(data).then((res) => res.data),
    onSuccess: invalidate,
  })
}

/** 중단됨 실행을 실패로 기록한다(POST /admin/collect/executions/{executionId}/recover, 200). */
export function useRecoverCollect() {
  const invalidate = useInvalidateCollect()
  return useMutation<CollectExecution, ApiError, { executionId: number }>({
    mutationFn: ({ executionId }) => recoverCollectExecution(executionId).then((res) => res.data),
    onSuccess: invalidate,
  })
}

/** 실패한 실행을 같은 날짜·범위로 다시 실행한다(POST /admin/collect/executions/{executionId}/restart, 202). 새 실행 ID가 온다. */
export function useRestartCollect() {
  const invalidate = useInvalidateCollect()
  return useMutation<CollectExecutionAccepted, ApiError, { executionId: number }>({
    mutationFn: ({ executionId }) => restartCollectExecution(executionId).then((res) => res.data),
    onSuccess: invalidate,
  })
}
