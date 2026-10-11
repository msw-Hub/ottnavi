/*
 * 수동 실행·다시 실행의 결과 안내(info 띠)에 쓰는 값과 변환 함수다(SCR-16).
 *
 * 202(접수)와 409(이미 실행 중 / 이미 성공 / 먼저 복구)는 오류(빨강)가 아니라 "지금 상태 안내"로 보인다.
 * 이미 있는 실행과의 충돌은 운영자가 잘못한 것이 아니라 이력을 확인하라는 뜻이기 때문이다. 그 밖의 오류만 빨간 문장이다.
 */
import { isApiError } from '@/api/http'
import { findRunning } from '@/features/admin/collectRules'
import type { CollectExecution, CollectTier } from '@/features/admin/mockTypes'
import { getErrorMessage } from '@/lib/errorMessages'

export type RunNotice =
  | { kind: 'ACCEPTED'; executionId: number; targetDate: string; tier: CollectTier }
  | {
      kind: 'ALREADY_RUNNING'
      targetDate: string
      tier: CollectTier
      runningExecutionId: number | null
    }
  | { kind: 'ALREADY_COMPLETED'; targetDate: string; tier: CollectTier }
  | { kind: 'RECOVERY_REQUIRED'; targetDate: string; tier: CollectTier }
  | { kind: 'ERROR'; message: string }

/** 실행 요청이 실패했을 때 어떤 안내로 보일지 정한다. 이력(executions)에서 실행 중인 행을 찾아 [실행 중인 이력 보기]에 쓴다. */
export function noticeFromError(
  error: unknown,
  targetDate: string,
  tier: CollectTier,
  executions: CollectExecution[],
): RunNotice {
  const code = isApiError(error) ? error.errorCode : null
  if (code === 'BATCH_ALREADY_RUNNING') {
    return {
      kind: 'ALREADY_RUNNING',
      targetDate,
      tier,
      runningExecutionId: findRunning(executions, targetDate, tier)?.executionId ?? null,
    }
  }
  if (code === 'BATCH_ALREADY_COMPLETED') return { kind: 'ALREADY_COMPLETED', targetDate, tier }
  if (code === 'BATCH_RECOVERY_REQUIRED') return { kind: 'RECOVERY_REQUIRED', targetDate, tier }
  return { kind: 'ERROR', message: isApiError(error) ? error.userMessage : getErrorMessage(null) }
}
