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
import type {
  CollectExecution,
  CollectProgress,
  CollectStepCode,
  CollectStepProgress,
  CollectStepStatus,
  CollectTier,
} from '@/api/generated/model'
import { formatIsoDate } from '@/lib/format'

// 범위(tier) 화면 글자
export const COLLECT_TIER_LABELS: Record<CollectTier, string> = {
  ALL: '전체(ALL)',
  DAILY: '매일 갱신(DAILY)',
  WEEKLY: '주간 갱신(WEEKLY)',
}

// 수집 단계(계약 CollectStepCode) 화면 글자. 서버는 코드만 주고 라벨은 화면이 붙인다
export const COLLECT_STEP_LABELS: Record<CollectStepCode, string> = {
  MOVIE_CANDIDATE: '영화 후보 발견',
  TV_CANDIDATE: '드라마 후보 발견',
  MOVIE_DETAIL: '영화 상세',
  TV_DETAIL: '드라마 상세',
  AVAILABILITY: '제공 상태 판정',
}

// 단계 상태(계약 CollectStepStatus) 화면 글자. 화면은 아이콘과 함께 이 글자를 늘 보여 색만으로 상태를 나누지 않는다
export const COLLECT_STEP_STATUS_LABELS: Record<CollectStepStatus, string> = {
  PENDING: '대기',
  RUNNING: '진행 중',
  COMPLETED: '완료',
  FAILED: '실패',
  STOPPED: '중단됨',
}

// 실행 중인 수집이 있을 때 이력을 자동으로 다시 불러오는 간격(15초). 화면 안내 문구("15초마다")와 같은 값이다
export const COLLECT_AUTO_REFRESH_MS = 15_000

// 후보 단계 퍼센트가 근사값이라는 안내(막대의 title, 화면 각주에 같은 글자를 쓴다)
export const CANDIDATE_PERCENT_NOTE = '후보 단계 퍼센트는 근사값이에요'

// 수동 실행에서 고를 수 있는 범위. DAILY·WEEKLY는 Task 077로 구현됐지만 예약 실행(cron) 전용이라 수동 실행 범위에서만 닫아 둔다
export const RUNNABLE_COLLECT_TIERS: CollectTier[] = ['ALL']

// 시뮬레이션: 화면에서 접수한 실행은 이력을 이 횟수만큼 읽으면 완료로 바뀐다(목업 전용, 결정적 규칙).
// 읽은 횟수 1·2·3번째에 단계 진행(후보 45% → 영화 상세 62% → 영화 상세 첫 값 전)을 차례로 보이고 4번째에 완료된다(adminHandlers의 진행 시뮬레이션).
// 이전 값: 2(단계별 진행 표시가 없던 때. 조회 1번째는 실행 중, 2번째에 성공)
export const SIMULATED_COMPLETE_AFTER_READS = 4

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

/* ===== 단계별 진행 표시(화면 전용 규칙) =====
 * 서버가 내려준 progress(계약 CollectProgress)를 화면 글자로 바꾸는 순수 함수다. 화면(진행 띠·요약 카드)과 테스트가 같은 규칙을 쓴다.
 * 숫자는 모두 한국어 자리 구분(1,240)으로 쓴다.
 */

// 건수 숫자를 "1,240"처럼 쓴다
function formatCount(value: number): string {
  return value.toLocaleString('ko-KR')
}

/** 실행 중인 수집이 하나라도 있는지. 있으면 이력을 15초마다 자동으로 다시 불러온다. */
export function hasRunningExecution(executions: CollectExecution[]): boolean {
  return executions.some((execution) => execution.status === 'RUNNING')
}

/** 후보 발견 단계인지. 후보 단계는 전체 수를 미리 알 수 없어 퍼센트가 서비스 수로 낸 근사값이다(계약 percent 설명). */
export function isCandidateStep(step: CollectStepCode): boolean {
  return step === 'MOVIE_CANDIDATE' || step === 'TV_CANDIDATE'
}

// 단계 한 줄 오른쪽의 진행 표시 종류
export type StepProgressDisplay =
  | { kind: 'NONE' } // 대기: "—"
  | { kind: 'TEXT'; text: string } // 막대 없이 글자만(완료 · n건 / 처리 n건 / 처리 n건에서 멈춤)
  | {
      kind: 'BAR' // 막대 + 퍼센트 + 보조 글자
      percent: number // 막대 채움(0~100)
      percentText: string // "62%" 또는 근사값이면 "약 45%"
      detailText: string | null // "1,240 / 2,000건" 또는 "서비스 3/7곳". 보일 값이 없으면 null
      isApproximate: boolean // 후보 단계의 근사값인지(막대 title과 화면 각주로 알린다)
    }

/**
 * 단계 하나의 진행 표시를 정한다.
 * - 대기 → "—"
 * - 완료 → "완료 · 1,100건"(막대 없음. 끝난 단계에 꽉 찬 막대를 늘어놓으면 진행 중 단계가 눈에 덜 띈다)
 * - 실패·중단됨 → "처리 120건에서 멈춤"
 * - 진행 중인데 percent가 아직 없음(첫 값을 받기 전) → "처리 1,240건"
 * - 진행 중인 후보 단계 → 막대 + "약 45%" + "서비스 3/7곳"(근사값이라 "약"을 붙인다)
 * - 진행 중인 상세·판정 단계 → 막대 + "62%" + 전체 수가 있으면 "1,240 / 2,000건"
 */
export function describeStepProgress(stepProgress: CollectStepProgress): StepProgressDisplay {
  const { status, step, processedCount, totalCount, percent } = stepProgress
  // 1. 진행 중이 아닌 상태는 글자만 보인다
  if (status === 'PENDING') return { kind: 'NONE' }
  if (status === 'COMPLETED')
    return { kind: 'TEXT', text: `완료 · ${formatCount(processedCount)}건` }
  if (status === 'FAILED' || status === 'STOPPED') {
    return { kind: 'TEXT', text: `처리 ${formatCount(processedCount)}건에서 멈춤` }
  }
  // 2. 진행 중이지만 퍼센트를 아직 모르면 처리한 수만 보인다
  if (percent === null) return { kind: 'TEXT', text: `처리 ${formatCount(processedCount)}건` }
  // 3. 후보 단계: 서비스 몇 곳을 끝냈는지 + 근사 퍼센트
  if (isCandidateStep(step)) {
    const { completedServiceCount, totalServiceCount } = stepProgress
    return {
      kind: 'BAR',
      percent,
      percentText: `약 ${percent}%`,
      detailText:
        completedServiceCount !== null && totalServiceCount !== null
          ? `서비스 ${completedServiceCount}/${totalServiceCount}곳`
          : null,
      isApproximate: true,
    }
  }
  // 4. 상세·판정 단계: 처리 수 / 전체 수 + 퍼센트
  return {
    kind: 'BAR',
    percent,
    percentText: `${percent}%`,
    detailText:
      totalCount !== null ? `${formatCount(processedCount)} / ${formatCount(totalCount)}건` : null,
    isApproximate: false,
  }
}

/** 진행 띠 첫 줄: "2/5 단계 · 지금: 영화 상세". 진행 중인 단계가 없으면(단계 사이 준비 중) "2/5 단계 · 다음 단계 준비 중". */
export function formatProgressHeadline(progress: CollectProgress): string {
  const stepCount = `${progress.completedStepCount}/${progress.totalStepCount} 단계`
  return progress.currentStep
    ? `${stepCount} · 지금: ${COLLECT_STEP_LABELS[progress.currentStep]}`
    : `${stepCount} · 다음 단계 준비 중`
}

/**
 * 실패·중단된 실행이 어느 단계에서 멈췄는지 한 줄: "2/5 단계에서 멈춤 · 영화 상세".
 * 단계는 상태가 실패·중단됨인 첫 단계, 없으면 currentStep으로 정한다. 둘 다 없으면 단계 이름 없이 "2/5 단계에서 멈춤"이다.
 */
export function formatHaltedStepLine(progress: CollectProgress): string {
  const haltedStep =
    progress.steps.find((s) => s.status === 'FAILED' || s.status === 'STOPPED')?.step ??
    progress.currentStep
  const stepCount = `${progress.completedStepCount}/${progress.totalStepCount} 단계에서 멈춤`
  return haltedStep ? `${stepCount} · ${COLLECT_STEP_LABELS[haltedStep]}` : stepCount
}

/**
 * 화면 위 요약 카드 한 줄: "실행 6 · 2026-10-30 전체(ALL) 수집 중 — 2/5 단계 · 영화 상세".
 * 진행 중인 단계가 없으면 "· 다음 단계 준비 중", progress가 없으면 단계 부분을 빼고 "… 수집 중"까지만 쓴다.
 */
export function formatRunningSummary(execution: CollectExecution): string {
  const head = `실행 ${execution.executionId} · ${formatIsoDate(execution.targetDate) ?? execution.targetDate} ${COLLECT_TIER_LABELS[execution.tier]} 수집 중`
  const { progress } = execution
  if (!progress) return head
  const stepCount = `${progress.completedStepCount}/${progress.totalStepCount} 단계`
  const current = progress.currentStep
    ? COLLECT_STEP_LABELS[progress.currentStep]
    : '다음 단계 준비 중'
  return `${head} — ${stepCount} · ${current}`
}
