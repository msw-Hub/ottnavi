import { describe, expect, it } from 'vitest'
import {
  computeCanRecover,
  computeCanRestart,
  findBlockingRun,
  findLastSuccess,
  findLatestOfKey,
  findRunning,
  judgeRun,
  sortExecutionsForHistory,
  sortExecutionsNewestFirst,
  withActionFlags,
} from '@/features/admin/collectRules'
import type { CollectExecution, CollectExecutionStatus } from '@/api/generated/model'

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
    failureReason: null,
    canRecover: false,
    canRestart: false,
  }
}

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
