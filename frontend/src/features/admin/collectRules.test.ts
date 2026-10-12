import { describe, expect, it } from 'vitest'
import {
  canRecover,
  canRestart,
  findLastSuccess,
  findRunning,
  judgeRun,
  sortExecutionsForHistory,
  sortExecutionsNewestFirst,
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
  }
}

describe('실행 판정', () => {
  it('실행 중이 있으면 ALREADY_RUNNING, 성공이 있으면 ALREADY_COMPLETED', () => {
    expect(judgeRun([run(1, 'RUNNING')], '2026-10-10', 'ALL')).toBe('ALREADY_RUNNING')
    expect(judgeRun([run(1, 'COMPLETED')], '2026-10-10', 'ALL')).toBe('ALREADY_COMPLETED')
  })

  it('실행 중이 성공보다 먼저다(실패 뒤 재시도가 돌고 있는 경우)', () => {
    expect(judgeRun([run(1, 'COMPLETED'), run(2, 'RUNNING')], '2026-10-10', 'ALL')).toBe(
      'ALREADY_RUNNING',
    )
  })

  it('중단됨이 있으면 먼저 복구해야 하고, 실패만 있으면 다시 실행할 수 있다', () => {
    expect(judgeRun([run(1, 'STOPPED')], '2026-10-10', 'ALL')).toBe('RECOVERY_REQUIRED')
    expect(judgeRun([run(1, 'FAILED')], '2026-10-10', 'ALL')).toBe('OK')
  })

  it('다른 날짜·다른 범위의 실행은 영향을 주지 않는다', () => {
    expect(judgeRun([run(1, 'RUNNING')], '2026-10-09', 'ALL')).toBe('OK')
    expect(judgeRun([run(1, 'RUNNING')], '2026-10-10', 'WEEKLY')).toBe('OK')
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

describe('버튼 대상과 정렬', () => {
  it('복구는 중단됨만, 다시 실행은 실패만', () => {
    expect(canRecover('STOPPED')).toBe(true)
    expect(canRecover('RUNNING')).toBe(false)
    expect(canRestart('FAILED')).toBe(true)
    expect(canRestart('STOPPED')).toBe(false)
  })

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
})
