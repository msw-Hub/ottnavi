import type { QueryClient } from '@tanstack/react-query'
import {
  getCurrentPlanMailPreview,
  getGetCurrentPlanMailPreviewQueryKey,
  useGetCurrentPlanMailPreview,
} from '@/api/generated/plan/plan'
import { getMockRequestParams } from '@/lib/mockScenario'

/*
 * 알림 메일 예시 조회 훅 모음이다(Task 110, 계약 getCurrentPlanMailPreview, 데모 계정 전용).
 *
 * 쿼리 키 뒤에 목업 시나리오를 붙이는 이유: usePlanDrafts와 같다. 시나리오가 바뀌면 응답이 달라 새로 불러와야 한다(운영에서는 빈 객체).
 * staleTime 0: 이 응답은 "지금 저장된 플랜"으로 서버가 그때그때 만든 메일이라 캐시에 오래 두면 낡은 플랜의 메일이 보인다.
 * retry false: 403(데모 계정이 아님)은 다시 해도 같아서, 재시도로 오류 안내가 늦게 뜨는 것을 막는다.
 */

function getPreviewQueryKey() {
  return [...getGetCurrentPlanMailPreviewQueryKey(), getMockRequestParams()] as const
}

/**
 * 저장 직후 자동 열기에서 쓰는 fetchQuery 옵션이다. 응답을 받은 뒤 메일이 있을 때만 팝업을 열기 위해 훅이 아니라 직접 불러온다.
 * 같은 키로 캐시에 들어가므로, 팝업이 열린 뒤 조회 훅이 그 데이터를 바로 보여 준다(같은 요청을 한 번 더 보내지 않는다).
 * staleTime 0이라 fetchQuery는 항상 새로 요청한다(저장할 때마다 최신 플랜으로 만든 메일을 받는다).
 */
export function getPlanMailPreviewFetchOptions() {
  return {
    queryKey: getPreviewQueryKey(),
    queryFn: ({ signal }: { signal: AbortSignal }) => getCurrentPlanMailPreview(undefined, signal),
    staleTime: 0,
    retry: false,
  } as const
}

/**
 * "알림 메일 예시 보기" 버튼으로 팝업을 열기 직전에 캐시를 비운다.
 * 이유: 팝업 안의 조회는 마운트 때 캐시가 있으면 다시 요청하지 않는다(자동 열기에서 받은 데이터를 재사용하려는 설계).
 * 그대로 두면 버튼으로 다시 열 때 옛 플랜의 메일이 보일 수 있고, 이전에 실패한 오류 상태도 남는다.
 * 비워 두고 열면 팝업이 새로 요청하면서 로딩 → 결과 상태를 정상적으로 거친다.
 */
export function resetPlanMailPreview(queryClient: QueryClient): void {
  queryClient.removeQueries({ queryKey: getPreviewQueryKey() })
}

/**
 * 팝업 본문 안에서 메일 예시를 조회한다. 팝업 본문은 열릴 때마다 새로 마운트되므로(Radix는 닫히면 내용을 지운다)
 * 별도의 enabled 조건 없이 마운트 시점에 한 번 요청한다.
 * refetchOnMount를 끄는 이유: 자동 열기에서 fetchQuery가 방금 받아 둔 캐시를 바로 쓰고, staleTime 0 때문에 같은 요청이 한 번 더 나가는 것을 막기 위함이다.
 */
export function usePlanMailPreview() {
  return useGetCurrentPlanMailPreview({
    query: {
      queryKey: getPreviewQueryKey(),
      staleTime: 0,
      retry: false,
      refetchOnMount: false,
      select: (res) => res.data,
    },
  })
}
