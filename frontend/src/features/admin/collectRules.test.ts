import { describe, expect, it } from 'vitest'
import {
  COLLECT_STEP_LABELS,
  COLLECT_STEP_STATUS_LABELS,
  computeCanRecover,
  computeCanRestart,
  describeStepProgress,
  findBlockingRun,
  findLastSuccess,
  findLatestOfKey,
  findRunning,
  formatHaltedStepLine,
  formatProgressHeadline,
  formatRunningSummary,
  judgeRun,
  sortExecutionsForHistory,
  sortExecutionsNewestFirst,
  withActionFlags,
} from '@/features/admin/collectRules'
import type {
  CollectExecution,
  CollectExecutionStatus,
  CollectProgress,
  CollectStepProgress,
} from '@/api/generated/model'

function run(
  executionId: number,
  status: CollectExecutionStatus,
  targetDate = '2026-10-10',
  startedAt = '2026-10-10T03:00:00+09:00',
): CollectExecution {
  return {
    executionId,
    targetDate,
    tier: 'ALL',
    status,
    startedAt,
    endedAt: null,
    processedCount: 0,
    changedCount: 0,
    recalcTargetCount: null,
    failureReason: null,
    canRecover: false,
    canRestart: false,
    progress: null, // 판정 규칙은 진행 정보를 보지 않는다
  }
}

// 단계 한 줄(필요한 값만 덮어쓴다). 기본은 진행 중인 영화 상세
function stepOf(overrides: Partial<CollectStepProgress>): CollectStepProgress {
  return {
    step: 'MOVIE_DETAIL',
    status: 'RUNNING',
    processedCount: 0,
    totalCount: null,
    percent: null,
    completedServiceCount: null,
    totalServiceCount: null,
    ...overrides,
  }
}

describe('단계별 진행 표시(화면 규칙)', () => {
  it('단계 코드와 상태에 한국어 라벨이 모두 있다', () => {
    expect(COLLECT_STEP_LABELS).toEqual({
      MOVIE_CANDIDATE: '영화 후보 발견',
      TV_CANDIDATE: '드라마 후보 발견',
      MOVIE_DETAIL: '영화 상세',
      TV_DETAIL: '드라마 상세',
      AVAILABILITY: '제공 상태 판정',
    })
    expect(COLLECT_STEP_STATUS_LABELS).toEqual({
      PENDING: '대기',
      RUNNING: '진행 중',
      COMPLETED: '완료',
      FAILED: '실패',
      STOPPED: '중단됨',
    })
  })

  it('상태와 값에 따라 막대·글자·대시를 고른다', () => {
    // 대기 → "—"
    expect(describeStepProgress(stepOf({ status: 'PENDING', percent: 0 }))).toEqual({
      kind: 'NONE',
    })
    // 완료 → 막대 없이 "완료 · n건"
    expect(
      describeStepProgress(stepOf({ status: 'COMPLETED', processedCount: 1100, percent: 100 })),
    ).toEqual({ kind: 'TEXT', text: '완료 · 1,100건' })
    // 실패·중단됨 → "처리 n건에서 멈춤"
    expect(describeStepProgress(stepOf({ status: 'STOPPED', processedCount: 120 }))).toEqual({
      kind: 'TEXT',
      text: '처리 120건에서 멈춤',
    })
    // 진행 중인데 퍼센트가 아직 없음 → "처리 n건"
    expect(describeStepProgress(stepOf({ processedCount: 1240 }))).toEqual({
      kind: 'TEXT',
      text: '처리 1,240건',
    })
    // 진행 중인 상세 단계 → 막대 + "62%" + "처리 / 전체"
    expect(
      describeStepProgress(stepOf({ processedCount: 1240, totalCount: 2000, percent: 62 })),
    ).toEqual({
      kind: 'BAR',
      percent: 62,
      percentText: '62%',
      detailText: '1,240 / 2,000건',
      isApproximate: false,
    })
    // 진행 중인 후보 단계 → 근사값("약")과 서비스 수
    expect(
      describeStepProgress(
        stepOf({
          step: 'TV_CANDIDATE',
          processedCount: 500,
          percent: 45,
          completedServiceCount: 3,
          totalServiceCount: 7,
        }),
      ),
    ).toEqual({
      kind: 'BAR',
      percent: 45,
      percentText: '약 45%',
      detailText: '서비스 3/7곳',
      isApproximate: true,
    })
  })

  it('첫 줄·멈춘 단계·요약 카드 문구를 만든다', () => {
    const progress: CollectProgress = {
      totalStepCount: 5,
      completedStepCount: 2,
      currentStep: 'MOVIE_DETAIL',
      steps: [],
    }
    expect(formatProgressHeadline(progress)).toBe('2/5 단계 · 지금: 영화 상세')
    expect(formatProgressHeadline({ ...progress, currentStep: null })).toBe(
      '2/5 단계 · 다음 단계 준비 중',
    )
    // 멈춘 단계는 실패·중단된 첫 단계가 기준이고, 없으면 currentStep, 둘 다 없으면 이름을 뺀다
    expect(
      formatHaltedStepLine({
        ...progress,
        currentStep: null,
        steps: [stepOf({ step: 'MOVIE_DETAIL', status: 'FAILED' })],
      }),
    ).toBe('2/5 단계에서 멈춤 · 영화 상세')
    expect(formatHaltedStepLine({ ...progress, currentStep: null })).toBe('2/5 단계에서 멈춤')
    // 요약 카드: progress가 없으면 단계 부분을 뺀다
    const running = { ...run(6, 'RUNNING', '2026-10-30'), progress }
    expect(formatRunningSummary(running)).toBe(
      '실행 6 · 2026-10-30 전체(ALL) 수집 중 — 2/5 단계 · 영화 상세',
    )
    expect(formatRunningSummary({ ...running, progress: null })).toBe(
      '실행 6 · 2026-10-30 전체(ALL) 수집 중',
    )
  })
})

describe('실행 판정(목업 서버 규칙, 서버 judgeLaunch와 같은 순서)', () => {
  it('같은 날짜·범위의 최신 실행이 실행 중 / 중단됨 / 성공이면 각각의 코드로 막는다', () => {
    expect(judgeRun([run(1, 'RUNNING')], '2026-10-10', 'ALL')).toBe('ALREADY_RUNNING')
    expect(judgeRun([run(1, 'STOPPED')], '2026-10-10', 'ALL')).toBe('RECOVERY_REQUIRED')
    expect(judgeRun([run(1, 'COMPLETED')], '2026-10-10', 'ALL')).toBe('ALREADY_COMPLETED')
  })

  it('같은 날짜·범위는 최신 실행(실행 ID가 가장 큰 것)의 상태로만 본다', () => {
    // 실패 뒤 다시 실행한 것이 돌고 있으면 실행 중
    expect(judgeRun([run(1, 'FAILED'), run(2, 'RUNNING')], '2026-10-10', 'ALL')).toBe(
      'ALREADY_RUNNING',
    )
    // 복구로 실패가 된 뒤에는 다시 실행할 수 있다(앞의 중단됨 기록은 최신이 아니다)
    expect(judgeRun([run(2, 'FAILED')], '2026-10-10', 'ALL')).toBe('OK')
  })

  it('다른 날짜가 실행 중이어도 ALREADY_RUNNING이다(전체 동시 실행 금지)', () => {
    expect(judgeRun([run(1, 'RUNNING')], '2026-10-09', 'ALL')).toBe('ALREADY_RUNNING')
    expect(judgeRun([run(1, 'RUNNING', '2026-10-10')], '2026-10-10', 'WEEKLY')).toBe(
      'ALREADY_RUNNING',
    )
  })

  it('같은 날짜·범위의 상태를 다른 실행 중보다 먼저 본다', () => {
    const others = [run(9, 'RUNNING', '2026-10-01')]
    expect(judgeRun([...others, run(1, 'COMPLETED')], '2026-10-10', 'ALL')).toBe(
      'ALREADY_COMPLETED',
    )
    expect(judgeRun([...others, run(1, 'STOPPED')], '2026-10-10', 'ALL')).toBe('RECOVERY_REQUIRED')
  })

  it('기록이 없거나 최신이 실패이고 다른 실행 중이 없으면 OK다', () => {
    expect(judgeRun([], '2026-10-10', 'ALL')).toBe('OK')
    expect(
      judgeRun([run(1, 'FAILED'), run(2, 'COMPLETED', '2026-10-09')], '2026-10-10', 'ALL'),
    ).toBe('OK')
  })
})

describe('버튼 값 계산(목업 서버 전용)', () => {
  it('canRecover는 중단됨만 참이다', () => {
    expect(computeCanRecover(run(1, 'STOPPED'))).toBe(true)
    expect(computeCanRecover(run(1, 'RUNNING'))).toBe(false)
    expect(computeCanRecover(run(1, 'FAILED'))).toBe(false)
  })

  it('canRestart는 같은 날짜·범위의 최신 실패이고 다른 실행 중이 없을 때만 참이다', () => {
    const failed = run(1, 'FAILED')
    expect(computeCanRestart(failed, [failed])).toBe(true)
    // 같은 날짜·범위에 더 나중 실행이 있으면 거짓
    expect(computeCanRestart(failed, [failed, run(2, 'FAILED')])).toBe(false)
    // 다른 날짜가 실행 중이면 거짓
    expect(computeCanRestart(failed, [failed, run(3, 'RUNNING', '2026-10-11')])).toBe(false)
    // 실패가 아니면 거짓
    const stopped = run(4, 'STOPPED')
    expect(computeCanRestart(stopped, [stopped])).toBe(false)
  })

  it('withActionFlags는 목록 전체를 지금 상태로 다시 계산한다(예전 값을 믿지 않는다)', () => {
    const list = [
      { ...run(1, 'FAILED', '2026-10-08'), canRestart: false },
      { ...run(2, 'FAILED', '2026-10-09'), canRestart: true },
      run(3, 'FAILED', '2026-10-09'),
      run(4, 'STOPPED', '2026-10-07'),
    ]
    const flagged = withActionFlags(list)
    expect(flagged.map((e) => [e.executionId, e.canRecover, e.canRestart])).toEqual([
      [1, false, true],
      [2, false, false], // 같은 날짜의 3번이 더 나중이다
      [3, false, true],
      [4, true, false],
    ])
  })
})

describe('이력 정렬', () => {
  it('대상 날짜 내림차순 → 같은 날짜는 실행 ID 내림차순(재실행이 원 실행 위에 붙는다)', () => {
    const list = [
      run(1, 'COMPLETED', '2026-10-07'),
      run(3, 'FAILED', '2026-10-09'),
      run(2, 'COMPLETED', '2026-10-10'),
      run(6, 'RUNNING', '2026-10-09'),
    ]
    expect(sortExecutionsForHistory(list).map((e) => e.executionId)).toEqual([2, 6, 3, 1])
  })
})

describe('찾기와 정렬', () => {
  it('최신 시작 순으로 정렬하고, 마지막 성공·실행 중을 찾는다', () => {
    const list = [
      run(1, 'COMPLETED', '2026-10-08', '2026-10-08T03:00:00+09:00'),
      run(2, 'RUNNING', '2026-10-10', '2026-10-10T18:00:00Z'),
      run(3, 'COMPLETED', '2026-10-09', '2026-10-09T03:00:00+09:00'),
    ]
    expect(sortExecutionsNewestFirst(list).map((e) => e.executionId)).toEqual([2, 3, 1])
    expect(findLastSuccess(list)?.executionId).toBe(3)
    expect(findRunning(list, '2026-10-10', 'ALL')?.executionId).toBe(2)
    expect(findLastSuccess([run(1, 'FAILED')])).toBeNull()
  })

  it('같은 날짜·범위의 최신 실행과, 막고 있는 실행 중(같은 날짜 우선)을 찾는다', () => {
    const list = [run(1, 'FAILED'), run(4, 'FAILED'), run(2, 'RUNNING', '2026-10-09')]
    expect(findLatestOfKey(list, '2026-10-10', 'ALL')?.executionId).toBe(4)
    expect(findLatestOfKey(list, '2026-10-01', 'ALL')).toBeNull()
    expect(findBlockingRun(list, '2026-10-10', 'ALL')?.executionId).toBe(2) // 다른 날짜의 실행 중
    expect(findBlockingRun([...list, run(5, 'RUNNING')], '2026-10-10', 'ALL')?.executionId).toBe(5) // 같은 날짜가 먼저
    expect(findBlockingRun([run(1, 'FAILED')], '2026-10-10', 'ALL')).toBeNull()
  })
})
