/*
 * 수집 실행·이력(SCR-16)의 순수 규칙 모음이다(Task 029, 단계 3).
 *
 * 화면(안내 문구)과 목업 서버(adminHandlers, 실행을 받을지 판정·버튼 값 계산)가 같은 규칙을 써야 해서 따로 뒀다.
 * 규칙 근거: TECH 3절. 같은 targetDate + tier가 실행 중이면 409 BATCH_ALREADY_RUNNING, 이미 완료면 409 BATCH_ALREADY_COMPLETED,
 * 실패한 실행은 같은 값으로 restart, 서버가 멈춰 "실행 중"으로 남은 실행은 복구(recover)한 뒤 restart 한다.
 * 수집은 전체에서 동시에 하나만 돈다: 다른 날짜가 실행 중이어도 BATCH_ALREADY_RUNNING이다(Task 045 결정 12).
 * "중단됨" 판정과 버튼 표시 여부(canRecover·canRestart)는 서버가 내려준다(사용자 결정 2026-10-11, Task 045 결정 3).
 * 화면은 시간이나 상태로 추측하지 않고 그 값만 읽는다. 아래 "[목업 서버 전용]" 함수는 목업이 서버와 같은 값을 만들려고 쓴다.
 */
import type { CollectExecution, CollectTier } from '@/api/generated/model'

// 범위(tier) 화면 글자
export const COLLECT_TIER_LABELS: Record<CollectTier, string> = {
  ALL: '전체(ALL)',
  DAILY: '매일 갱신(DAILY)',
  WEEKLY: '주간 갱신(WEEKLY)',
}

// 수동 실행에서 고를 수 있는 범위. DAILY·WEEKLY는 Task 077로 구현됐지만 예약 실행(cron) 전용이라 수동 실행 범위에서만 닫아 둔다
export const RUNNABLE_COLLECT_TIERS: CollectTier[] = ['ALL']

// 시뮬레이션: 화면에서 접수한 실행은 이력을 이 횟수만큼 읽으면 완료로 바뀐다(목업 전용, 결정적 규칙)
export const SIMULATED_COMPLETE_AFTER_READS = 2

// Task 045: 버튼을 보일지는 서버가 내려준 execution.canRecover·execution.canRestart를 읽는다.
// 예전에는 아래처럼 상태만 보고 화면이 정했지만, 재시작은 "같은 날짜·범위의 최신 실행인지, 다른 수집이 도는지, 운영에서 오늘인지"까지
// 봐야 해서 화면이 따라 계산하면 서버 판정과 어긋난다. 그래서 판정은 서버 한 곳에 두고 화면은 값만 읽는다.
// /** [복구]를 보일 상태인지. 서버가 중단됨으로 판정한 실행만 대상이다. */
// export function canRecover(status: CollectExecutionStatus): boolean {
//   return status === 'STOPPED'
// }
//
// /** [다시 실행]을 보일 상태인지. 실패한 실행(복구해서 실패가 된 것 포함)만 대상이다. */
// export function canRestart(status: CollectExecutionStatus): boolean {
//   return status === 'FAILED'
// }

// 실행 요청 판정 결과
export type RunJudgement =
  | 'OK' // 새로 실행(또는 실패한 실행의 restart)할 수 있다
  | 'ALREADY_RUNNING' // 같은 날짜·범위가 실행 중이거나 다른 날짜의 수집이 실행 중 → 409 BATCH_ALREADY_RUNNING
  | 'ALREADY_COMPLETED' // 같은 날짜·범위의 최신 실행이 성공 → 409 BATCH_ALREADY_COMPLETED
  | 'RECOVERY_REQUIRED' // 같은 날짜·범위의 최신 실행이 중단됨 → 먼저 복구해야 한다(409 BATCH_RECOVERY_REQUIRED)

/** 같은 대상 날짜·범위의 가장 최근 실행(실행 ID가 가장 큰 것). 없으면 null. */
export function findLatestOfKey(
  executions: CollectExecution[],
  targetDate: string,
  tier: CollectTier,
): CollectExecution | null {
  return executions
    .filter((e) => e.targetDate === targetDate && e.tier === tier)
    .reduce<CollectExecution | null>(
      (latest, e) => (latest === null || e.executionId > latest.executionId ? e : latest),
      null,
    )
}

/**
 * [목업 서버 전용] 새 실행·재시작을 받아도 되는지 판정한다. 화면은 이 함수로 버튼을 정하지 않는다(서버의 canRestart를 읽는다).
 * 백엔드 CollectExecutionRules.judgeLaunch와 같은 순서다.
 * 1. 같은 날짜·범위의 최신 실행 상태를 먼저 본다: 실행 중 → ALREADY_RUNNING, 중단됨 → RECOVERY_REQUIRED, 성공 → ALREADY_COMPLETED
 * 2. 그다음 다른 날짜·범위의 수집이 실행 중인지 본다. 수집은 전체에서 동시에 하나만 돌기 때문이다 → ALREADY_RUNNING
 * 3. 둘 다 아니면(기록 없음, 최신이 실패) OK다(TECH 3절: 실패한 실행은 같은 파라미터로 restart).
 */
export function judgeRun(
  executions: CollectExecution[],
  targetDate: string,
  tier: CollectTier,
): RunJudgement {
  // 1. 같은 날짜·범위의 최신 실행 상태
  const latest = findLatestOfKey(executions, targetDate, tier)
  if (latest?.status === 'RUNNING') return 'ALREADY_RUNNING'
  if (latest?.status === 'STOPPED') return 'RECOVERY_REQUIRED'
  if (latest?.status === 'COMPLETED') return 'ALREADY_COMPLETED'
  // 2. 다른 수집이 실행 중인지(같은 날짜·범위의 최신은 위에서 실행 중이 아님이 확인됐다)
  if (executions.some((e) => e.status === 'RUNNING')) return 'ALREADY_RUNNING'
  // 3. 기록이 없거나 최신이 실패
  return 'OK'
}

/** [목업 서버 전용] 응답의 canRecover 값. 서버와 같이 중단됨(STOPPED)이면 참이다. */
export function computeCanRecover(execution: CollectExecution): boolean {
  return execution.status === 'STOPPED'
}

/**
 * [목업 서버 전용] 응답의 canRestart 값. 서버(CollectExecutionRules.canRestart)와 같은 조건이다.
 * 실패(FAILED) ∧ 같은 날짜·범위의 최신 실행 ∧ 다른 수집이 실행 중이 아님.
 * 운영의 "대상 날짜가 오늘" 조건은 넣지 않는다. 목업은 날짜 지정을 허용하는 로컬 환경처럼 동작하기 때문이다.
 */
export function computeCanRestart(
  execution: CollectExecution,
  executions: CollectExecution[],
): boolean {
  // 1. 실패한 실행만 대상이다
  if (execution.status !== 'FAILED') return false
  // 2. 같은 날짜·범위의 최신 실행이어야 한다(더 나중 실행이 있으면 그 실행의 상태가 기준이다)
  const latest = findLatestOfKey(executions, execution.targetDate, execution.tier)
  if (latest?.executionId !== execution.executionId) return false
  // 3. 시작 판정과 같은 규칙으로 다른 수집이 실행 중이 아닌지 본다
  return judgeRun(executions, execution.targetDate, execution.tier) === 'OK'
}

/** [목업 서버 전용] 목록의 모든 실행에 canRecover·canRestart를 지금 상태로 다시 계산해 붙인 새 배열. */
export function withActionFlags(executions: CollectExecution[]): CollectExecution[] {
  return executions.map((e) => ({
    ...e,
    canRecover: computeCanRecover(e),
    canRestart: computeCanRestart(e, executions),
  }))
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

/**
 * 실행을 막고 있는 실행 중 실행 하나. 같은 날짜·범위를 먼저 찾고, 없으면 다른 날짜·범위의 실행 중을 찾는다. 없으면 null.
 * 409 BATCH_ALREADY_RUNNING 안내에서 "어느 실행 때문에 막혔는지"를 보여 줄 때 쓴다(다른 날짜가 실행 중이어도 같은 코드라서다).
 */
export function findBlockingRun(
  executions: CollectExecution[],
  targetDate: string,
  tier: CollectTier,
): CollectExecution | null {
  return (
    findRunning(executions, targetDate, tier) ??
    executions.find((e) => e.status === 'RUNNING') ??
    null
  )
}
