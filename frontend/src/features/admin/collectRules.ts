/*
 * 수집 실행·이력(SCR-16)의 순수 규칙 모음이다(Task 029, 단계 3).
 *
 * 화면(버튼을 보일지, 안내 문구)과 목업 서버(adminHandlers, 실행을 받을지 판정)가 같은 규칙을 써야 해서 따로 뒀다.
 * 규칙 근거: TECH 3절. 같은 targetDate + tier가 실행 중이면 409 BATCH_ALREADY_RUNNING, 이미 완료면 409 BATCH_ALREADY_COMPLETED,
 * 실패한 실행은 같은 값으로 restart, 서버가 멈춰 "실행 중"으로 남은 실행은 복구(recover)한 뒤 restart 한다.
 * "중단됨" 판정은 서버가 내려준다(사용자 결정 2026-10-11). 화면은 시간으로 추측하지 않고 status === 'STOPPED'만 본다.
 */
import type { CollectExecution, CollectExecutionStatus, CollectTier } from '@/api/generated/model'

// 범위(tier) 화면 글자
export const COLLECT_TIER_LABELS: Record<CollectTier, string> = {
  ALL: '전체(ALL)',
  DAILY: '매일 갱신(DAILY)',
  WEEKLY: '주간 갱신(WEEKLY)',
}

// MVP에서 실행할 수 있는 범위. DAILY·WEEKLY는 2단계(O3, Task 077)에서 연다
export const RUNNABLE_COLLECT_TIERS: CollectTier[] = ['ALL']

// 시뮬레이션: 화면에서 접수한 실행은 이력을 이 횟수만큼 읽으면 완료로 바뀐다(목업 전용, 결정적 규칙)
export const SIMULATED_COMPLETE_AFTER_READS = 2

/** [복구]를 보일 상태인지. 서버가 중단됨으로 판정한 실행만 대상이다. */
export function canRecover(status: CollectExecutionStatus): boolean {
  return status === 'STOPPED'
}

/** [다시 실행]을 보일 상태인지. 실패한 실행(복구해서 실패가 된 것 포함)만 대상이다. */
export function canRestart(status: CollectExecutionStatus): boolean {
  return status === 'FAILED'
}

// 실행 요청 판정 결과
export type RunJudgement =
  | 'OK' // 새로 실행(또는 실패한 실행의 restart)할 수 있다
  | 'ALREADY_RUNNING' // 같은 날짜·범위가 실행 중 → 409 BATCH_ALREADY_RUNNING
  | 'ALREADY_COMPLETED' // 같은 날짜·범위가 이미 성공 → 409 BATCH_ALREADY_COMPLETED
  | 'RECOVERY_REQUIRED' // 같은 날짜·범위에 중단된 실행이 있다 → 먼저 복구해야 한다(제안 코드 BATCH_RECOVERY_REQUIRED)

/**
 * 같은 대상 날짜·범위의 기존 실행을 보고 새 실행을 받아도 되는지 판정한다.
 * 우선순위: 실행 중 > 성공 > 중단됨. 실패한 실행만 있으면 OK다(TECH 3절: 실패한 실행은 같은 파라미터로 restart).
 */
export function judgeRun(
  executions: CollectExecution[],
  targetDate: string,
  tier: CollectTier,
): RunJudgement {
  const same = executions.filter((e) => e.targetDate === targetDate && e.tier === tier)
  if (same.some((e) => e.status === 'RUNNING')) return 'ALREADY_RUNNING'
  if (same.some((e) => e.status === 'COMPLETED')) return 'ALREADY_COMPLETED'
  if (same.some((e) => e.status === 'STOPPED')) return 'RECOVERY_REQUIRED'
  return 'OK'
}

/**
 * 이력 표시 순서: 대상 날짜 내림차순 → 같은 날짜는 실행 ID 내림차순.
 * 시작 시각이 아니라 대상 날짜로 묶는 이유: 재실행(새 실행 ID)이 원래 실행 바로 위에 붙고, 대상 날짜 열이 위에서 아래로 순서대로 읽힌다.
 */
export function sortExecutionsForHistory(executions: CollectExecution[]): CollectExecution[] {
  return [...executions].sort((a, b) =>
    a.targetDate === b.targetDate
      ? b.executionId - a.executionId
      : a.targetDate < b.targetDate
        ? 1
        : -1,
  )
}

/** 시작 시각이 늦은 순(최신 먼저)으로 정렬한 새 배열. 시각 문자열의 오프셋이 달라도 맞도록 Date로 비교한다. */
export function sortExecutionsNewestFirst(executions: CollectExecution[]): CollectExecution[] {
  return [...executions].sort(
    (a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime(),
  )
}

/** 가장 최근에 성공한 실행. 없으면 null. */
export function findLastSuccess(executions: CollectExecution[]): CollectExecution | null {
  return sortExecutionsNewestFirst(executions).find((e) => e.status === 'COMPLETED') ?? null
}

/** 같은 날짜·범위에서 실행 중인 실행. 없으면 null. */
export function findRunning(
  executions: CollectExecution[],
  targetDate: string,
  tier: CollectTier,
): CollectExecution | null {
  return (
    executions.find(
      (e) => e.targetDate === targetDate && e.tier === tier && e.status === 'RUNNING',
    ) ?? null
  )
}
