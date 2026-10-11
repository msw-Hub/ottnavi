import { describe, expect, it } from 'vitest'
import type { AdminProvider } from '@/features/admin/mockTypes'
import {
  applyProviderFilter,
  buildSaveRequest,
  countUnmapped,
  isMappingChanged,
  parseProviderFilter,
  toChoiceValue,
  validateSaveRequest,
} from '@/features/admin/providerMapping'

function provider(fields: Partial<AdminProvider> & { providerId: number }): AdminProvider {
  return {
    tmdbName: `P${fields.providerId}`,
    ottServiceId: null,
    tier: 'STANDARD',
    firstSeenAt: '2026-10-01T03:00:00+09:00',
    mappingState: 'UNMAPPED',
    ...fields,
  }
}

const MAPPED = provider({
  providerId: 1,
  mappingState: 'MAPPED',
  ottServiceId: 1,
  tier: 'STANDARD',
})
const IGNORED = provider({ providerId: 2, mappingState: 'IGNORED' })
const UNMAPPED_OLD = provider({ providerId: 3, firstSeenAt: '2026-10-02T03:00:00+09:00' })
const UNMAPPED_NEW = provider({ providerId: 4, firstSeenAt: '2026-10-09T03:00:00+09:00' })

describe('선택값 ↔ 저장 요청', () => {
  it('저장된 값을 select 선택값으로 바꾼다', () => {
    expect(toChoiceValue(MAPPED)).toBe('1')
    expect(toChoiceValue(IGNORED)).toBe('IGNORED')
    expect(toChoiceValue(UNMAPPED_OLD)).toBe('')
  })

  it('서비스 선택은 MAPPED, 관련 없음은 IGNORED, 선택 안 함은 UNMAPPED로 저장한다', () => {
    expect(buildSaveRequest('3', 'AD')).toEqual({
      mappingState: 'MAPPED',
      ottServiceId: 3,
      tier: 'AD',
    })
    expect(buildSaveRequest('IGNORED', 'AD')).toEqual({
      mappingState: 'IGNORED',
      ottServiceId: null,
      tier: 'STANDARD', // 연결하지 않으면 등급은 STANDARD로 고정
    })
    expect(buildSaveRequest('', 'STANDARD').mappingState).toBe('UNMAPPED')
  })
})

describe('바뀐 행 판단', () => {
  it('저장된 값과 같으면 바뀐 것이 아니다', () => {
    expect(isMappingChanged(MAPPED, '1', 'STANDARD')).toBe(false)
    expect(isMappingChanged(IGNORED, 'IGNORED', 'STANDARD')).toBe(false)
  })

  it('이미 미매핑인 행의 "선택 안 함"은 바뀐 것이 아니다(등급을 만져도 마찬가지)', () => {
    expect(isMappingChanged(UNMAPPED_OLD, '', 'STANDARD')).toBe(false)
    expect(isMappingChanged(UNMAPPED_OLD, '', 'AD')).toBe(false)
  })

  it('서비스·등급·상태가 달라지면 바뀐 것이다', () => {
    expect(isMappingChanged(UNMAPPED_OLD, '2', 'STANDARD')).toBe(true)
    expect(isMappingChanged(MAPPED, '1', 'AD')).toBe(true)
    expect(isMappingChanged(MAPPED, 'IGNORED', 'STANDARD')).toBe(true)
    expect(isMappingChanged(IGNORED, '', 'STANDARD')).toBe(true)
  })
})

describe('서버 쪽 검사', () => {
  it('MAPPED는 아는 서비스가 있어야 하고, 아니면 서비스를 지정할 수 없다', () => {
    expect(validateSaveRequest(buildSaveRequest('3', 'STANDARD'), [1, 2, 3])).toBeNull()
    expect(validateSaveRequest(buildSaveRequest('9', 'STANDARD'), [1, 2, 3])).not.toBeNull()
    expect(
      validateSaveRequest({ mappingState: 'IGNORED', ottServiceId: 1, tier: 'STANDARD' }, [1]),
    ).not.toBeNull()
  })
})

describe('필터', () => {
  const all = [MAPPED, UNMAPPED_OLD, IGNORED, UNMAPPED_NEW]

  it('미매핑 먼저: 미매핑을 최근 발견 순으로 위에 두고 나머지는 받은 순서 그대로', () => {
    expect(applyProviderFilter(all, 'unmapped-first').map((p) => p.providerId)).toEqual([
      4, 3, 1, 2,
    ])
  })

  it('미매핑만 / 전체', () => {
    expect(applyProviderFilter(all, 'unmapped').map((p) => p.providerId)).toEqual([4, 3])
    expect(applyProviderFilter(all, 'all').map((p) => p.providerId)).toEqual([1, 3, 2, 4])
  })

  it('미매핑 수를 센다', () => {
    expect(countUnmapped(all)).toBe(2)
  })

  it('주소의 filter 값을 읽고 모르는 값은 기본으로 본다', () => {
    expect(parseProviderFilter('unmapped')).toBe('unmapped')
    expect(parseProviderFilter('unmapped-first')).toBe('unmapped-first')
    expect(parseProviderFilter('all')).toBe('all')
    expect(parseProviderFilter(null)).toBe('all')
    expect(parseProviderFilter('x')).toBe('all')
  })
})
