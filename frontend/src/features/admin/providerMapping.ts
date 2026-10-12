/*
 * 제공처 매핑(SCR-15)의 순수 규칙 모음이다(Task 029, 단계 2).
 *
 * 화면(행의 select 값 → 저장 요청 → 바뀌었는지 판단)과 목업 서버(adminHandlers)가 같은 규칙을 써야 해서 따로 뒀다.
 *
 * 선택값(choice) 표현: select는 문자열만 다루므로 아래 세 가지를 문자열로 나타낸다.
 * - '' (CHOICE_UNMAPPED): "선택 안 함(미매핑)" → 저장하면 UNMAPPED
 * - 'IGNORED' (CHOICE_IGNORED): "관련 없음(쓰지 않음)" → 저장하면 IGNORED
 * - '1'~'7' : 서비스 ID → 저장하면 MAPPED
 * 등급(tier)은 서비스에 연결(MAPPED)했을 때만 의미가 있다. 아니면 STANDARD로 고정하고 화면에서는 "—"로 보인다.
 */
import type {
  AdminProvider,
  ProviderMappingState,
  ProviderTier,
  UpdateAdminProviderRequest,
} from '@/api/generated/model'

export const CHOICE_UNMAPPED = ''
export const CHOICE_IGNORED = 'IGNORED'

// 보기 필터: 전체(기본, 받은 순서) / 미매핑 먼저(미매핑을 위로 올린 전체) / 미매핑만
export type ProviderFilter = 'all' | 'unmapped-first' | 'unmapped'

export const DEFAULT_PROVIDER_FILTER: ProviderFilter = 'all'

/** 주소의 ?filter= 값을 읽는다. 기본(전체)은 파라미터 없음이고 filter=all도 같은 뜻이다. 모르는 값이면 기본(전체)이다. */
export function parseProviderFilter(value: string | null): ProviderFilter {
  return value === 'unmapped' || value === 'unmapped-first' ? value : DEFAULT_PROVIDER_FILTER
}

/** 제공처의 지금 저장된 값을 select 선택값으로 바꾼다. */
export function toChoiceValue(provider: AdminProvider): string {
  if (provider.mappingState === 'IGNORED') return CHOICE_IGNORED
  if (provider.mappingState === 'MAPPED' && provider.ottServiceId !== null) {
    return String(provider.ottServiceId)
  }
  return CHOICE_UNMAPPED
}

/** 선택값이 서비스 연결(MAPPED)인지. 등급을 고를 수 있는 경우다. */
export function isServiceChoice(choice: string): boolean {
  return choice !== CHOICE_UNMAPPED && choice !== CHOICE_IGNORED
}

/** 선택값 → 저장 후 매핑 상태. 서비스 선택은 MAPPED, 관련 없음은 IGNORED, 선택 안 함은 UNMAPPED다. */
export function toMappingState(choice: string): ProviderMappingState {
  if (choice === CHOICE_IGNORED) return 'IGNORED'
  return choice === CHOICE_UNMAPPED ? 'UNMAPPED' : 'MAPPED'
}

/** 화면 입력(선택값, 등급)을 저장 요청으로 바꾼다. 서비스에 연결하지 않으면 등급은 STANDARD로 고정한다. */
export function buildSaveRequest(choice: string, tier: ProviderTier): UpdateAdminProviderRequest {
  const mappingState = toMappingState(choice)
  return {
    mappingState,
    ottServiceId: mappingState === 'MAPPED' ? Number(choice) : null,
    tier: mappingState === 'MAPPED' ? tier : 'STANDARD',
  }
}

/**
 * 입력이 저장된 값과 다른지 돌려준다. 다른 행만 [저장]을 켠다.
 * 등급은 서비스에 연결한 경우에만 비교한다("선택 안 함"을 그대로 저장하는 일이 없도록, 미매핑 행의 선택 안 함은 바뀐 것이 아니다).
 */
export function isMappingChanged(
  provider: AdminProvider,
  choice: string,
  tier: ProviderTier,
): boolean {
  const request = buildSaveRequest(choice, tier)
  return (
    request.mappingState !== provider.mappingState ||
    request.ottServiceId !== provider.ottServiceId ||
    request.tier !== (provider.mappingState === 'MAPPED' ? provider.tier : 'STANDARD')
  )
}

/** 요청이 규칙에 맞는지 검사한다(서버 쪽 검사). 올바르면 null, 아니면 안내 문장을 돌려준다. */
export function validateSaveRequest(
  request: UpdateAdminProviderRequest,
  knownServiceIds: number[],
): string | null {
  if (request.mappingState === 'MAPPED') {
    if (request.ottServiceId === null || !knownServiceIds.includes(request.ottServiceId)) {
      return '연결할 서비스를 선택해 주세요.'
    }
    return null
  }
  if (request.ottServiceId !== null) return '연결하지 않는 제공처에는 서비스를 지정할 수 없습니다.'
  return null
}

/** 미매핑 제공처 수. */
export function countUnmapped(providers: AdminProvider[]): number {
  return providers.filter((p) => p.mappingState === 'UNMAPPED').length
}

/**
 * 필터에 맞게 제공처를 고르고 정렬한 새 배열을 돌려준다.
 * - all: 전체를 받은 순서 그대로
 * - unmapped-first: 전체. 미매핑을 위로(최근 발견한 순), 나머지는 받은 순서 그대로
 * - unmapped: 미매핑만(최근 발견한 순)
 * - all: 전체를 받은 순서 그대로
 * 최근 발견 순으로 하는 이유: 새로 나타난 제공처를 먼저 확인하게 하려는 것이다.
 */
export function applyProviderFilter(
  providers: AdminProvider[],
  filter: ProviderFilter,
): AdminProvider[] {
  if (filter === 'all') return [...providers]
  const byNewest = (a: AdminProvider, b: AdminProvider) =>
    a.firstSeenAt < b.firstSeenAt ? 1 : a.firstSeenAt > b.firstSeenAt ? -1 : 0
  const unmapped = providers.filter((p) => p.mappingState === 'UNMAPPED').sort(byNewest)
  if (filter === 'unmapped') return unmapped
  return [...unmapped, ...providers.filter((p) => p.mappingState !== 'UNMAPPED')]
}
