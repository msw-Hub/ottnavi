/*
 * 수동 실행·다시 실행의 결과 안내(info 띠)에 쓰는 값과 변환 함수다(SCR-16).
 *
 * 202(접수)와 409(이미 실행 중 / 이미 성공 / 먼저 복구)는 오류(빨강)가 아니라 "지금 상태 안내"로 보인다.
 * 이미 있는 실행과의 충돌은 운영자가 잘못한 것이 아니라 이력을 확인하라는 뜻이기 때문이다. 그 밖의 오류만 빨간 문장이다.
 * 대상 날짜가 서버 규칙에 맞지 않는 400(fieldErrors.targetDate)은 날짜 입력 아래에 서버 문구를 그대로 보인다(Task 045).
 */
import { isApiError } from '@/api/http'
import { findBlockingRun } from '@/features/admin/collectRules'
import type { CollectExecution, CollectTier } from '@/api/generated/model'
import { getErrorMessage } from '@/lib/errorMessages'

export type RunNotice =
  | { kind: 'ACCEPTED'; executionId: number; targetDate: string; tier: CollectTier }
  | {
      kind: 'ALREADY_RUNNING'
      targetDate: string // 실행하려던 대상 날짜
      tier: CollectTier // 실행하려던 범위
      running: CollectExecution | null // 막고 있는 실행 중 실행(같은 날짜·범위가 먼저). 지금 이력에서 못 찾으면 null
    }
  | { kind: 'ALREADY_COMPLETED'; targetDate: string; tier: CollectTier }
  | { kind: 'RECOVERY_REQUIRED'; targetDate: string; tier: CollectTier }
  | { kind: 'DATE_INVALID'; message: string } // 서버가 대상 날짜를 거절함(예: 운영은 오늘만). message는 서버 fieldErrors 문구
  | { kind: 'ERROR'; message: string }

/**
 * 실행 요청이 실패했을 때 어떤 안내로 보일지 정한다.
 * 이력(executions)에서 막고 있는 실행 중 행을 찾아 [실행 중인 이력 보기]에 쓴다. BATCH_ALREADY_RUNNING은
 * 같은 날짜·범위뿐 아니라 다른 날짜가 실행 중일 때도 오므로(전체 동시 실행 금지) 같은 날짜 → 다른 날짜 순으로 찾는다.
 */
export function noticeFromError(
  error: unknown,
  targetDate: string,
  tier: CollectTier,
  executions: CollectExecution[],
): RunNotice {
  // 1. 서버 오류 코드를 꺼낸다(ApiError가 아니면 코드 없음)
  const code = isApiError(error) ? error.errorCode : null
  // 2. 이미 있는 실행과의 충돌(409)은 상태 안내로 바꾼다
  if (code === 'BATCH_ALREADY_RUNNING') {
    return {
      kind: 'ALREADY_RUNNING',
      targetDate,
      tier,
      running: findBlockingRun(executions, targetDate, tier),
    }
  }
  if (code === 'BATCH_ALREADY_COMPLETED') return { kind: 'ALREADY_COMPLETED', targetDate, tier }
  if (code === 'BATCH_RECOVERY_REQUIRED') return { kind: 'RECOVERY_REQUIRED', targetDate, tier }
  // 3. 대상 날짜 필드 오류는 날짜 입력 아래에 서버 문구로 보인다
  const dateError = isApiError(error)
    ? error.fieldErrors.find((fieldError) => fieldError.field === 'targetDate')
    : undefined
  if (dateError) return { kind: 'DATE_INVALID', message: dateError.message }
  // 4. 그 밖은 빨간 오류 문장
  return { kind: 'ERROR', message: isApiError(error) ? error.userMessage : getErrorMessage(null) }
}
