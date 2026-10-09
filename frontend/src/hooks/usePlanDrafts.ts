import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { customInstance, isApiError, type ApiError } from '@/api/http'
import { getMockRequestParams } from '@/lib/mockScenario'
import type {
  CommonResponsePlanDraftList,
  CommonResponseSavedPlan,
  PlanDraft,
  PlanType,
  SavedPlan,
  SavePlanRequest,
} from '@/features/plan/mockTypes'

/*
 * 플랜 초안 훅 모음이다. 원래 features/plan/hooks에 있었으나, 요금 화면(pricing)의 "계산하기"가 같은 계산 요청(useCalculatePlan)을
 * 직접 써야 해서 src/hooks로 올렸다. 기능 폴더끼리는 import하지 않으므로(frontend.md) 공유 훅은 이 폴더에 둔다.
 * 계산·조회·저장이 같은 캐시 키와 요청 함수를 나눠 쓰는 한 묶음이라 useCalculatePlan만 떼지 않고 파일째 옮겼다.
 * 응답 타입은 임시 타입 파일(features/plan/mockTypes)을 그대로 쓴다(Task 031에서 생성 타입으로 교체).
 */

// 서버가 "초안의 데이터 기준일이 낡았다"고 알릴 때의 오류 코드(docs/api/error-codes.md, 409)
const PLAN_DRAFT_STALE_ERROR_CODE = 'PLAN_DRAFT_STALE'

// 플랜 초안의 캐시 키. 시나리오·목업 안이 바뀌면 응답이 달라서 키에 넣는다(운영에서는 빈 객체)
function getDraftsQueryKey() {
  return ['/me/plans/drafts', getMockRequestParams()] as const
}

// 플랜 계산 요청. 계산과 "저장 중 자동 재계산"이 같은 요청을 쓰도록 함수로 뺐다
function requestCalculatePlan() {
  return customInstance<CommonResponsePlanDraftList>({
    url: '/me/plans/calculate',
    method: 'POST',
    params: getMockRequestParams(),
  })
}

/**
 * 지금 있는 플랜 초안(DRAFT)들을 불러온다(SCR-10). 계산 전이거나 저장한 뒤에는 빈 배열이다 → 화면은 "계산하기" 안내를 보인다.
 *
 * 응답 모양 주의(PRD 11절 35번): 서버는 항상 유형마다 초안 하나씩을 응답한다. 선택이 같은 유형을 하나로 합쳐 보이는 일은 프론트가 mergeIdenticalDrafts로 한다.
 *
 * 임시 구현: 이 API(GET /me/plans/drafts)는 아직 계약에 없어 생성 훅이 없다. Task 031에서 바뀐다.
 */
export function usePlanDrafts() {
  return useQuery<CommonResponsePlanDraftList, ApiError, PlanDraft[]>({
    queryKey: getDraftsQueryKey(),
    queryFn: ({ signal }) =>
      customInstance<CommonResponsePlanDraftList>({
        url: '/me/plans/drafts',
        method: 'GET',
        params: getMockRequestParams(),
        signal,
      }),
    select: (res) => res.data.drafts,
  })
}

/**
 * 플랜을 계산한다(POST /me/plans/calculate). 유형 3종 초안이 새로 만들어지고 기존 초안은 덮어쓴다.
 * 시간이 걸릴 수 있으므로 화면은 isPending 동안 진행 표시를 보여야 한다. 성공하면 초안 캐시를 새 결과로 바꾼다.
 * 계산할 찜이 없거나 예산·시청 시간이 비었으면 400(VALIDATION_FAILED)이다.
 */
export function useCalculatePlan() {
  const queryClient = useQueryClient()

  return useMutation<CommonResponsePlanDraftList, ApiError, void>({
    mutationFn: () => requestCalculatePlan(),
    onSuccess: (res) => {
      queryClient.setQueryData(getDraftsQueryKey(), res)
    },
  })
}

// 저장 요청 인자
export interface SavePlanVariables extends SavePlanRequest {
  planType: PlanType // 저장할 유형. 합쳐 보인 그룹이어도 첫 유형 하나의 planId·planType을 보낸다(나머지 DRAFT는 서버가 지운다)
}

// 저장 결과. saved=저장 성공 / recalculated=저장 중 409(데이터 갱신)를 받아 자동으로 다시 계산한 결과
export type SavePlanResult =
  { kind: 'saved'; plan: SavedPlan } | { kind: 'recalculated'; drafts: PlanDraft[] }

/**
 * 고른 유형의 초안을 저장한다(POST /me/plans/{planType}/save). 고른 유형만 ACTIVE가 되고 나머지 초안은 삭제된다.
 *
 * 409 PLAN_DRAFT_STALE 자동 재계산(PRD SCR-10): 계산한 뒤 데이터 수집이 새로 끝나 초안이 낡았으면 서버가 저장을 막고 409로 알린다.
 * 단순히 오류를 보이면 사용자는 영문을 모르고 막힌다. 그래서 이 훅이 같은 자리에서 계산을 다시 요청하고 결과를 kind 'recalculated'로 돌려준다.
 * 새 결과로 자동 저장하지 않는 이유: 재계산하면 내용(비용·배정)이 바뀔 수 있어 사용자가 새 결과를 확인하고 다시 저장을 눌러야 한다.
 * 화면은 'recalculated'를 받으면 "데이터가 갱신되어 플랜을 다시 계산했어요. 확인 후 저장해 주세요" 같은 안내를 보인다.
 * 그 밖의 오류(404 PLAN_DRAFT_NOT_FOUND 등)는 그대로 던진다.
 */
export function useSavePlan() {
  const queryClient = useQueryClient()

  return useMutation<SavePlanResult, ApiError, SavePlanVariables>({
    mutationFn: async ({ planType, planId }) => {
      try {
        const res = await customInstance<CommonResponseSavedPlan>({
          url: `/me/plans/${planType}/save`,
          method: 'POST',
          data: { planId },
          params: getMockRequestParams(),
        })
        return { kind: 'saved', plan: res.data }
      } catch (error) {
        if (
          isApiError(error) &&
          error.status === 409 &&
          error.errorCode === PLAN_DRAFT_STALE_ERROR_CODE
        ) {
          const res = await requestCalculatePlan()
          return { kind: 'recalculated', drafts: res.data.drafts }
        }
        throw error
      }
    },
    onSuccess: (result) => {
      if (result.kind === 'recalculated') {
        queryClient.setQueryData<CommonResponsePlanDraftList>(getDraftsQueryKey(), {
          success: true,
          data: { drafts: result.drafts },
        })
        return
      }
      // 저장하면 초안은 사라진다(나머지 DRAFT 삭제). 낡은 초안이 화면에 남지 않도록 다시 불러온다
      void queryClient.invalidateQueries({ queryKey: ['/me/plans/drafts'] })
    },
  })
}
