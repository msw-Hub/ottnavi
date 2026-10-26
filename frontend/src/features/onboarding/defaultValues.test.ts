import { describe, expect, it } from 'vitest'
import type { OttService } from '@/api/generated/model'
import { buildDefaultValues } from '@/features/onboarding/defaultValues'
import { DEFAULT_USER_SETTINGS, EMPTY_USER_SETTINGS } from '@/mocks/fixtures/settings'

const services = [{ ottServiceId: 1 }, { ottServiceId: 2 }] as OttService[]

describe('buildDefaultValues 시청 시간 처음 값', () => {
  it('저장된 설정이 없으면 프리셋 방식의 보통(NORMAL)을 미리 고른다', () => {
    const values = buildDefaultValues(EMPTY_USER_SETTINGS, services)
    expect(values.watchTime).toEqual({ mode: 'preset', preset: 'NORMAL' })
    expect(values.services).toHaveLength(2)
  })

  it('저장된 프리셋이 있으면 보통이 아니라 그 값이 우선한다', () => {
    const settings = { ...EMPTY_USER_SETTINGS, watchPreset: 'HEAVY' as const }
    expect(buildDefaultValues(settings, services).watchTime).toEqual({
      mode: 'preset',
      preset: 'HEAVY',
    })
  })

  it('직접 입력으로 저장했다면 직접 입력 방식으로 되살린다', () => {
    const settings = {
      ...EMPTY_USER_SETTINGS,
      watchPreset: 'CUSTOM' as const,
      monthlyWatchMinutes: 1200,
    }
    expect(buildDefaultValues(settings, services).watchTime).toEqual({
      mode: 'direct',
      directMinutes: 1200,
    })
  })

  it('회원 기본 설정(목업)은 프리셋 보통으로 열린다', () => {
    expect(buildDefaultValues(DEFAULT_USER_SETTINGS, services).watchTime).toEqual({
      mode: 'preset',
      preset: 'NORMAL',
    })
  })

  it('CUSTOM으로 저장했어도 분 값이 프리셋과 정확히 같으면 프리셋으로 복원한다', () => {
    const settings = {
      ...EMPTY_USER_SETTINGS,
      watchPreset: 'CUSTOM' as const,
      monthlyWatchMinutes: 480,
    }
    expect(buildDefaultValues(settings, services).watchTime).toEqual({
      mode: 'preset',
      preset: 'LIGHT',
    })
  })
})
