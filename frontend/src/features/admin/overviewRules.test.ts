import { describe, expect, it } from 'vitest'
import type { OverviewDraft } from '@/api/generated/model'
import {
  APPLY_SKIP_REASON_LABELS,
  GUARD_FAILURE_LABELS,
  WARNING_LABELS,
  getBulkProgress,
  getBulkStopMessage,
  getBulkSuccessCount,
  getDraftGrade,
  getDraftGradeToast,
  getGuardFailureLabel,
  getRemainingCalls,
  getRevertCutoff,
  getUsageLevel,
  getWarningLabel,
  parseRevertMonths,
} from '@/features/admin/overviewRules'

/*
 * LLM 줄거리 번역 화면의 순수 규칙 테스트. 확인하는 것: 검사 실패 코드가 사람이 읽는 라벨로 바뀌는지,
 * 멈춘 이유 6가지가 모두 안내 문장을 갖는지, 사용량 단계 경계(79%·80%·100%)가 맞는지.
 * 막으려는 사고: 서버가 코드를 더하거나 문장이 빠져 운영자가 빈 칸이나 코드 문자열을 보는 것.
 */

describe('검사 실패 이유 라벨', () => {
  it('계약의 실패 코드 9개가 모두 라벨과 설명으로 바뀐다', () => {
    const codes = [
      'EMPTY',
      'TOO_LONG',
      'LENGTH_RATIO_LOW',
      'LENGTH_RATIO_HIGH',
      'HANGUL_RATIO_LOW',
      'CONTAINS_URL',
      'CONTAINS_MARKDOWN',
      'CONTAINS_PREAMBLE',
      'NUMBER_MISSING',
    ]
    expect(Object.keys(GUARD_FAILURE_LABELS).sort()).toEqual([...codes].sort())
    for (const code of codes) {
      const { label, description } = getGuardFailureLabel(code)
      expect(label).not.toBe('')
      expect(description).not.toBe('')
      expect(label).not.toContain(code)
    }
    expect(getGuardFailureLabel('LENGTH_RATIO_LOW').label).toBe('원문보다 너무 짧음')
  })

  it('모르는 코드는 "기타 검사 실패(코드)"로 보인다', () => {
    expect(getGuardFailureLabel('SOMETHING_NEW').label).toBe('기타 검사 실패(SOMETHING_NEW)')
    expect(getGuardFailureLabel('toString').label).toBe('기타 검사 실패(toString)')
  })
})

describe('일괄 번역 중단 사유', () => {
  it('6가지 사유가 모두 안내 문장을 갖고, 하루 상한 문장에는 상한 횟수가 들어간다', () => {
    const reasons = [
      'DISABLED',
      'RATE_LIMITED',
      'DAILY_LIMIT',
      'DRAFT_LIMIT',
      'CALL_FAILED_REPEATED',
      'SERVER_RESTARTED',
    ] as const
    for (const reason of reasons) {
      expect(getBulkStopMessage(reason, 400).length).toBeGreaterThan(10)
    }
    expect(getBulkStopMessage('DAILY_LIMIT', 400)).toContain('오늘 호출 상한(400회)')
  })
})

describe('반영 건너뜀 이유 라벨', () => {
  it('3가지 이유가 모두 라벨을 갖는다', () => {
    expect(Object.keys(APPLY_SKIP_REASON_LABELS).sort()).toEqual([
      'DRAFT_NOT_FOUND',
      'GUARD_FAILED',
      'ORIGINAL_CHANGED',
    ])
  })
})

describe('사용량 단계', () => {
  it('79%는 정상, 80%는 곧 상한, 100%는 도달이다', () => {
    expect(getUsageLevel(79, 100)).toBe('normal')
    expect(getUsageLevel(80, 100)).toBe('nearLimit')
    expect(getUsageLevel(99, 100)).toBe('nearLimit')
    expect(getUsageLevel(100, 100)).toBe('reached')
    expect(getUsageLevel(320, 400)).toBe('nearLimit')
    expect(getUsageLevel(319, 400)).toBe('normal')
  })

  it('상한이 0이면 정상으로 본다', () => {
    expect(getUsageLevel(0, 0)).toBe('normal')
  })

  it('남은 호출 수는 0 아래로 내려가지 않는다', () => {
    expect(getRemainingCalls(37, 400)).toBe(363)
    expect(getRemainingCalls(450, 400)).toBe(0)
  })
})

describe('일괄 진행률', () => {
  it('처리 수는 초안을 만든 수와 만들지 못한 수의 합이고, 검사 실패 수는 더하지 않는다', () => {
    const progress = getBulkProgress({ total: 20, doneCount: 9, failedCount: 3 })
    expect(progress.processed).toBe(12)
    expect(progress.ratio).toBeCloseTo(0.6)
  })

  it('전체가 0이면 비율은 0이다', () => {
    expect(getBulkProgress({ total: 0, doneCount: 0, failedCount: 0 }).ratio).toBe(0)
  })
})

// 등급·토스트 테스트에 쓰는 초안 하나(필요한 필드만 바꿔 쓴다)
function draftOf(overrides: Partial<OverviewDraft>): OverviewDraft {
  return {
    draftId: 'd-1',
    titleId: 7,
    mediaType: 'movie',
    title: '기생충',
    original: 'original',
    translated: '번역',
    isGuardPassed: true,
    guardFailures: [],
    warnings: [],
    model: 'm',
    createdAt: '2026-10-28T01:00:00Z',
    attempts: 1,
    isEdited: false,
    ...overrides,
  }
}

describe('검사 등급 계산', () => {
  it('실패도 경고도 없으면 성공, 경고만 있으면 주의, 실패가 있으면 실패다', () => {
    expect(getDraftGrade(draftOf({}))).toBe('PASSED')
    expect(getDraftGrade(draftOf({ warnings: ['STYLE_NOT_PLAIN'] }))).toBe('WARNING')
    expect(getDraftGrade(draftOf({ isGuardPassed: false, guardFailures: ['EMPTY'] }))).toBe(
      'FAILED',
    )
  })

  it('실패가 있으면 경고가 함께 있어도 실패다(반영 가능으로 보이면 안 된다)', () => {
    expect(
      getDraftGrade(
        draftOf({
          isGuardPassed: false,
          guardFailures: ['TOO_LONG'],
          warnings: ['STYLE_NOT_PLAIN'],
        }),
      ),
    ).toBe('FAILED')
  })

  it('isGuardPassed가 true여도 실패 이유가 남아 있으면 실패로 본다', () => {
    expect(getDraftGrade(draftOf({ guardFailures: ['CONTAINS_URL'] }))).toBe('FAILED')
  })
})

describe('주의 이유 라벨', () => {
  it('계약의 주의 코드 4개가 모두 라벨과 설명을 갖는다', () => {
    expect(Object.keys(WARNING_LABELS).sort()).toEqual([
      'LENGTH_RATIO_NEAR_LIMIT',
      'SENTENCE_COUNT_MISMATCH',
      'STYLE_NOT_PLAIN',
      'SUSPICIOUS_SPACING',
    ])
    expect(getWarningLabel('STYLE_NOT_PLAIN').label).toBe('말투 섞임')
  })

  it('모르는 코드는 "기타 주의(코드)"로 보인다', () => {
    expect(getWarningLabel('SOMETHING_NEW').label).toBe('기타 주의(SOMETHING_NEW)')
    expect(getWarningLabel('toString').label).toBe('기타 주의(toString)')
  })
})

describe('다시 번역 토스트', () => {
  it('성공 / 주의 / 실패 문장이 등급과 가야 할 탭을 알려 준다', () => {
    const passed = getDraftGradeToast(draftOf({}), '다시 번역했습니다')
    expect(passed).toEqual({
      level: 'success',
      text: '「기생충」을 다시 번역했습니다. 검사 결과: 성공',
    })
    const warning = getDraftGradeToast(
      draftOf({ warnings: ['STYLE_NOT_PLAIN', 'SUSPICIOUS_SPACING'] }),
      '다시 번역했습니다',
    )
    expect(warning.level).toBe('warning')
    expect(warning.text).toContain('검사 결과: 주의(말투 섞임, 띄어쓰기 의심)')
    expect(warning.text).toContain('[승인 대기] 탭')
    const failed = getDraftGradeToast(
      draftOf({ isGuardPassed: false, guardFailures: ['EMPTY'] }),
      '다시 번역했습니다',
    )
    expect(failed.text).toContain('검사 결과: 실패(번역문 없음)')
    expect(failed.text).toContain('[실패] 탭')
  })
})

describe('일괄 번역 성공 수', () => {
  it('만든 수에서 주의와 실패를 뺀 값이고 0 아래로 내려가지 않는다', () => {
    expect(getBulkSuccessCount({ doneCount: 18, warningCount: 2, guardFailedCount: 3 })).toBe(13)
    expect(getBulkSuccessCount({ doneCount: 1, warningCount: 2, guardFailedCount: 3 })).toBe(0)
  })
})

describe('대량 되돌리기 개월 수와 기준 시각', () => {
  it('1~24의 정수만 받는다', () => {
    expect(parseRevertMonths('5')).toBe(5)
    expect(parseRevertMonths('1')).toBe(1)
    expect(parseRevertMonths('24')).toBe(24)
    for (const bad of ['', '  ', '0', '25', '1.5', 'abc', '-3']) {
      expect(parseRevertMonths(bad)).toBeNull()
    }
  })

  it('기준 시각은 N개월 전이고, 달이 짧으면 그 달의 마지막 날로 맞춘다', () => {
    const fiveMonthsBefore = new Date(getRevertCutoff(5, new Date(2026, 9, 29, 12, 0, 0)))
    expect(fiveMonthsBefore.getFullYear()).toBe(2026)
    expect(fiveMonthsBefore.getMonth()).toBe(4) // 5월
    expect(fiveMonthsBefore.getDate()).toBe(29)
    // 3월 31일의 1개월 전은 2월 28일이다(3월 3일로 넘어가면 안 된다)
    const clamped = new Date(getRevertCutoff(1, new Date(2026, 2, 31, 12, 0, 0)))
    expect(clamped.getMonth()).toBe(1)
    expect(clamped.getDate()).toBe(28)
    // 해를 넘어가는 경우
    const crossYear = new Date(getRevertCutoff(24, new Date(2026, 0, 15, 12, 0, 0)))
    expect(crossYear.getFullYear()).toBe(2024)
    expect(crossYear.getMonth()).toBe(0)
  })
})
