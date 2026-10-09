import { describe, expect, it } from 'vitest'
import {
  onboardingFormSchema,
  toOptionalNumber,
  toSaveSettingsRequest,
  type OnboardingFormValues,
} from '@/features/onboarding/schema'

// 모든 조건을 만족하는 기본 폼 값. 테스트마다 필요한 부분만 바꿔 쓴다
function createValidValues(): OnboardingFormValues {
  return {
    services: [
      { ottServiceId: 1, status: 'SUBSCRIBED', planName: '스탠다드', billingDay: 15 },
      { ottServiceId: 2, status: 'NOT_SUBSCRIBED' },
      { ottServiceId: 3, status: 'FREE' },
      { ottServiceId: 4, status: 'UNKNOWN' },
    ],
    watchTime: { mode: 'preset', preset: 'NORMAL' },
    monthlyBudget: 20000,
  }
}

// 검증 실패 시 오류가 난 칸의 경로를 "services.0.planName" 형태로 모아 돌려준다
function getIssuePaths(values: unknown): string[] {
  const result = onboardingFormSchema.safeParse(values)
  return result.success ? [] : result.error.issues.map((issue) => issue.path.join('.'))
}

describe('onboardingFormSchema 서비스 상태', () => {
  it('올바른 값은 통과한다', () => {
    expect(onboardingFormSchema.safeParse(createValidValues()).success).toBe(true)
  })

  it('SUBSCRIBED가 아니면 요금제 이름과 결제일 없이도 통과한다', () => {
    const values = createValidValues()
    values.services = [
      { ottServiceId: 2, status: 'NOT_SUBSCRIBED' },
      { ottServiceId: 3, status: 'FREE' },
      { ottServiceId: 4, status: 'UNKNOWN' },
    ]
    expect(onboardingFormSchema.safeParse(values).success).toBe(true)
  })

  it('SUBSCRIBED이면 요금제 이름이 필수다', () => {
    const values = createValidValues()
    values.services[0] = { ottServiceId: 1, status: 'SUBSCRIBED', billingDay: 1 }
    expect(getIssuePaths(values)).toEqual(['services.0.planName'])
  })

  it('SUBSCRIBED이면 공백뿐인 요금제 이름도 거절한다', () => {
    const values = createValidValues()
    values.services[0] = { ottServiceId: 1, status: 'SUBSCRIBED', planName: '   ', billingDay: 1 }
    expect(getIssuePaths(values)).toEqual(['services.0.planName'])
  })

  it('SUBSCRIBED이면 결제일이 필수다', () => {
    const values = createValidValues()
    values.services[0] = { ottServiceId: 1, status: 'SUBSCRIBED', planName: '스탠다드' }
    expect(getIssuePaths(values)).toEqual(['services.0.billingDay'])
  })

  it.each([0, 32, 1.5, -1])('결제일 %s은 1~31 정수가 아니라서 거절한다', (billingDay) => {
    const values = createValidValues()
    values.services[0] = { ottServiceId: 1, status: 'SUBSCRIBED', planName: '스탠다드', billingDay }
    expect(getIssuePaths(values)).toContain('services.0.billingDay')
  })

  it.each([1, 15, 31])('결제일 %s은 통과한다', (billingDay) => {
    const values = createValidValues()
    values.services[0] = { ottServiceId: 1, status: 'SUBSCRIBED', planName: '스탠다드', billingDay }
    expect(onboardingFormSchema.safeParse(values).success).toBe(true)
  })

  it('알 수 없는 상태 값은 거절한다', () => {
    const values = { ...createValidValues(), services: [{ ottServiceId: 1, status: 'PAUSED' }] }
    expect(getIssuePaths(values)).toEqual(['services.0.status'])
  })

  it('서비스가 하나도 없으면 거절한다', () => {
    const values = { ...createValidValues(), services: [] }
    expect(getIssuePaths(values)).toEqual(['services'])
  })
})

describe('onboardingFormSchema 시청 시간', () => {
  it('프리셋 방식은 프리셋을 골라야 한다', () => {
    const values = { ...createValidValues(), watchTime: { mode: 'preset' } }
    expect(getIssuePaths(values)).toEqual(['watchTime.preset'])
  })

  it('환산 방식은 횟수와 시간이 필요하고 결과가 120분 이상이어야 한다', () => {
    const missing = { ...createValidValues(), watchTime: { mode: 'weekly' } }
    expect(getIssuePaths(missing)).toEqual(['watchTime.timesPerWeek', 'watchTime.hoursPerTime'])

    const tooShort = {
      ...createValidValues(),
      watchTime: { mode: 'weekly', timesPerWeek: 1, hoursPerTime: 0.25 },
    }
    expect(getIssuePaths(tooShort)).toEqual(['watchTime.hoursPerTime'])

    const ok = {
      ...createValidValues(),
      watchTime: { mode: 'weekly', timesPerWeek: 3, hoursPerTime: 2 },
    }
    expect(onboardingFormSchema.safeParse(ok).success).toBe(true)
  })

  it('환산 방식의 횟수는 1~7 정수여야 한다', () => {
    const values = {
      ...createValidValues(),
      watchTime: { mode: 'weekly', timesPerWeek: 8, hoursPerTime: 2 },
    }
    expect(getIssuePaths(values)).toEqual(['watchTime.timesPerWeek'])
  })

  it('직접 입력은 분 단위 정수이고 120분 이상이어야 한다', () => {
    const base = createValidValues()
    expect(getIssuePaths({ ...base, watchTime: { mode: 'direct' } })).toEqual([
      'watchTime.directMinutes',
    ])
    expect(getIssuePaths({ ...base, watchTime: { mode: 'direct', directMinutes: 119 } })).toEqual([
      'watchTime.directMinutes',
    ])
    expect(getIssuePaths({ ...base, watchTime: { mode: 'direct', directMinutes: 90.5 } })).toEqual([
      'watchTime.directMinutes',
    ])
    expect(
      onboardingFormSchema.safeParse({ ...base, watchTime: { mode: 'direct', directMinutes: 120 } })
        .success,
    ).toBe(true)
  })

  it('직접 입력이 한 달의 최대 분을 넘으면 거절한다', () => {
    const values = { ...createValidValues(), watchTime: { mode: 'direct', directMinutes: 50000 } }
    expect(getIssuePaths(values)).toEqual(['watchTime.directMinutes'])
  })
})

describe('onboardingFormSchema 월 예산', () => {
  it('필수다', () => {
    const { services, watchTime } = createValidValues() // 월 예산만 뺀다
    expect(getIssuePaths({ services, watchTime })).toEqual(['monthlyBudget'])
  })

  it('0원은 통과한다', () => {
    expect(
      onboardingFormSchema.safeParse({ ...createValidValues(), monthlyBudget: 0 }).success,
    ).toBe(true)
  })

  it.each([-1, 1000.5])('예산 %s은 0 이상 정수가 아니라서 거절한다', (monthlyBudget) => {
    expect(getIssuePaths({ ...createValidValues(), monthlyBudget })).toEqual(['monthlyBudget'])
  })
})

describe('onboardingFormSchema 아무것도 입력하지 않고 저장', () => {
  // 시청 시간이 프리셋 '보통'으로 미리 골라진 온보딩 첫 화면 그대로(defaultValues.ts). 예산 칸만 비어 undefined다
  const firstScreen = {
    services: [{ ottServiceId: 1, status: 'NOT_SUBSCRIBED', planName: '', billingDay: 1 }],
    watchTime: { mode: 'preset', preset: 'NORMAL' },
    monthlyBudget: undefined,
  }

  it('첫 화면 그대로 저장하면 월 예산 칸에만 오류가 난다(시청 시간은 미리 골라져 있다)', () => {
    const result = onboardingFormSchema.safeParse(firstScreen)
    expect(result.success).toBe(false)
    if (result.success) return
    expect(result.error.issues.map((issue) => issue.path.join('.'))).toEqual(['monthlyBudget'])
  })

  // 방어: 프리셋을 고르지 않은 라디오 묶음은 RHF가 null로 넘긴다(저장값이 CUSTOM인데 값이 없는 경우 등). 이때도 한국어로 막는다
  const untouched = {
    services: [{ ottServiceId: 1, status: 'NOT_SUBSCRIBED', planName: '', billingDay: 1 }],
    watchTime: { mode: 'preset', preset: null },
    monthlyBudget: undefined,
  }

  it('시청 시간과 월 예산 칸에 오류가 나고 메시지는 모두 한국어다', () => {
    const result = onboardingFormSchema.safeParse(untouched)
    expect(result.success).toBe(false)
    if (result.success) return
    expect(result.error.issues.map((issue) => issue.path.join('.'))).toEqual([
      'watchTime.preset',
      'monthlyBudget',
    ])
    result.error.issues.forEach((issue) => {
      expect(issue.message).toMatch(/[가-힣]/)
      expect(issue.message).not.toMatch(/Invalid|expected/i)
    })
  })

  it('프리셋이 undefined여도 같은 한국어 메시지다', () => {
    const result = onboardingFormSchema.safeParse({ ...untouched, watchTime: { mode: 'preset' } })
    expect(result.success).toBe(false)
    if (result.success) return
    expect(result.error.issues[0].message).toBe('시청 시간을 골라 주세요.')
  })

  it('환산 방식으로 바꿨는데 프리셋이 null로 남아 있어도 그 때문에 막히지 않는다', () => {
    const values = {
      ...createValidValues(),
      watchTime: { mode: 'weekly', preset: null, timesPerWeek: 3, hoursPerTime: 2 },
    }
    expect(onboardingFormSchema.safeParse(values).success).toBe(true)
  })

  it('숫자 칸이 숫자가 아니어도 한국어 메시지다', () => {
    const result = onboardingFormSchema.safeParse({ ...createValidValues(), monthlyBudget: 'abc' })
    expect(result.success).toBe(false)
    if (result.success) return
    expect(result.error.issues[0].message).toBe('월 예산을 입력해 주세요.')
  })
})

describe('toOptionalNumber', () => {
  it('빈 문자열과 숫자가 아닌 값은 undefined다', () => {
    expect(toOptionalNumber('')).toBeUndefined()
    expect(toOptionalNumber('  ')).toBeUndefined()
    expect(toOptionalNumber('abc')).toBeUndefined()
    expect(toOptionalNumber(Number.NaN)).toBeUndefined()
  })

  it('숫자 문자열은 숫자로 바꾼다', () => {
    expect(toOptionalNumber('15')).toBe(15)
    expect(toOptionalNumber(2.5)).toBe(2.5)
  })
})

describe('toSaveSettingsRequest', () => {
  it('구독 중이 아닌 서비스의 요금제·결제일은 비운다', () => {
    const values = createValidValues()
    values.services[1] = {
      ottServiceId: 2,
      status: 'NOT_SUBSCRIBED',
      planName: '남은 값',
      billingDay: 9,
    }
    const request = toSaveSettingsRequest(values)

    expect(request.subscriptions[0]).toEqual({
      ottServiceId: 1,
      status: 'SUBSCRIBED',
      planName: '스탠다드',
      billingDay: 15,
    })
    expect(request.subscriptions[1]).toEqual({
      ottServiceId: 2,
      status: 'NOT_SUBSCRIBED',
      planName: null,
      billingDay: null,
    })
  })

  it('프리셋은 그 값과 분으로 저장하고 환산 입력값은 비운다', () => {
    const request = toSaveSettingsRequest(createValidValues())
    expect(request).toMatchObject({
      monthlyBudget: 20000,
      monthlyWatchMinutes: 900,
      watchPreset: 'NORMAL',
      weeklyInput: null,
    })
  })

  it('환산 방식은 CUSTOM과 환산한 분, 환산 입력값을 함께 담는다', () => {
    const values = createValidValues()
    values.watchTime = { mode: 'weekly', timesPerWeek: 3, hoursPerTime: 2 }
    expect(toSaveSettingsRequest(values)).toMatchObject({
      monthlyWatchMinutes: 1559,
      watchPreset: 'CUSTOM',
      weeklyInput: { timesPerWeek: 3, hoursPerTime: 2 },
    })
  })
})
