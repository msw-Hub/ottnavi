import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { CommonResponsePlanDraftList, PlanDraft, SavedPlan } from '@/api/generated/model'
import {
  createPlan,
  createPlanDrafts,
  getListPlanDraftsQueryKey,
  useListPlanDrafts,
} from '@/api/generated/plan/plan'
import { isApiError, type ApiError } from '@/api/http'
import { getMockRequestParams } from '@/lib/mockScenario'

/*
 * 플랜 초안 훅 모음이다. 원래 features/plan/hooks에 있었으나, 요금 화면(pricing)의 "계산하기"가 같은 계산 요청(useCalculatePlan)을
 * 직접 써야 해서 src/hooks로 올렸다. 기능 폴더끼리는 import하지 않으므로(frontend.md) 공유 훅은 이 폴더에 둔다.
 * 계산·조회·저장이 같은 캐시 키와 요청 함수를 나눠 쓰는 한 묶음이라 useCalculatePlan만 떼지 않고 파일째 옮겼다.
 * 요청·응답 타입과 호출 함수는 확정 계약의 생성물이다(createPlanDrafts, listPlanDrafts, createPlan).
 */

// 서버가 "초안의 데이터 기준일이 낡았다"고 알릴 때의 오류 코드(docs/api/error-codes.md, 409)
const PLAN_DRAFT_STALE_ERROR_CODE = 'PLAN_DRAFT_STALE'

// 플랜 초안의 캐시 키. 생성 훅의 기본 키(['/me/plan-drafts']) 뒤에 목업 시나리오를 붙인다(운영에서는 빈 객체).
// 시나리오가 바뀌면 응답이 달라 새로 불러와야 하기 때문이다. 앞부분이 같아 invalidateQueries({ queryKey: ['/me/plan-drafts'] })가 그대로 맞는다
function getDraftsQueryKey() {
  return [...getListPlanDraftsQueryKey(), getMockRequestParams()] as const
}

/**
 * 지금 있는 플랜 초안(DRAFT)들을 불러온다(SCR-10, 계약 listPlanDrafts). 계산 전이거나 저장한 뒤에는 빈 배열이다 → 화면은 "계산하기" 안내를 보인다.
 *
 * 응답 모양 주의(PRD 11절 35번): 서버는 항상 유형마다 초안 하나씩을 응답한다. 선택이 같은 유형을 하나로 합쳐 보이는 일은 프론트가 mergeIdenticalDrafts로 한다.
 */
export function usePlanDrafts() {
  return useListPlanDrafts({
    query: { queryKey: getDraftsQueryKey(), select: (res) => res.data.drafts },
  })
}

/**
 * 플랜을 계산한다(계약 createPlanDrafts, POST /me/plan-drafts → 201). 유형 3종 초안이 새로 만들어지고 기존 초안은 덮어쓴다.
 * 응답 본문이 새 초안 3개라 그대로 초안 캐시에 넣는다(바로 이어서 조회하지 않아도 결과 화면이 채워진다).
 * 시간이 걸릴 수 있으므로 화면은 isPending 동안 진행 표시를 보여야 한다.
 * 계산할 찜이 없거나 예산·시청 시간이 비었으면 400(VALIDATION_FAILED)이다.
 */
export function useCalculatePlan() {
  const queryClient = useQueryClient()

  return useMutation<CommonResponsePlanDraftList, ApiError, void>({
    mutationFn: () => createPlanDrafts(),
    onSuccess: (res) => {
      queryClient.setQueryData(getDraftsQueryKey(), res)
    },
  })
}

// 저장 요청 인자
export interface SavePlanVariables {
  planId: number // 저장할 초안의 플랜 ID. 유형은 서버가 planId로 안다. 합쳐 보인 그룹이어도 그중 하나의 planId만 보낸다(나머지 DRAFT는 서버가 지운다)
}

// 저장 결과. saved=저장 성공 / recalculated=저장 중 409(데이터 갱신)를 받아 자동으로 다시 계산한 결과
export type SavePlanResult =
  { kind: 'saved'; plan: SavedPlan } | { kind: 'recalculated'; drafts: PlanDraft[] }

/**
 * 고른 초안을 저장한다(계약 createPlan, POST /me/plans { planId } → 201). 고른 유형만 ACTIVE가 되고 나머지 초안은 삭제된다.
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
    mutationFn: async ({ planId }) => {
      try {
        const res = await createPlan({ planId })
        return { kind: 'saved', plan: res.data }
      } catch (error) {
        if (
          isApiError(error) &&
          error.status === 409 &&
          error.errorCode === PLAN_DRAFT_STALE_ERROR_CODE
        ) {
          const res = await createPlanDrafts()
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
      void queryClient.invalidateQueries({ queryKey: ['/me/plan-drafts'] })
    },
  })
}
