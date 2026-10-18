/*
 * 홈·랭킹·탐색 화면이 함께 쓰는 표시용 도우미(탭 선택지, 숫자·날짜 글자)다.
 * 컴포넌트 파일과 나눈 이유: 컴포넌트 파일에서 상수·함수를 함께 내보내면 빠른 새로고침(react-refresh) 린트 규칙에 걸린다.
 */
import type { OttService, PopularityPeriod } from '@/api/generated/model'
import type { ServiceTabOption } from '@/features/home/components/ServiceTabs'

// 탭 값 "전체"(서비스를 가리지 않음)
export const ALL_SERVICES_TAB = 'ALL'

// 서비스 목록으로 탭 선택지를 만든다. hasAll이면 맨 앞에 "전체"를 둔다
export function buildServiceTabOptions(
  services: OttService[],
  hasAll: boolean,
): ServiceTabOption[] {
  const serviceOptions = services.map((service) => ({ value: service.code, label: service.name }))
  return hasAll ? [{ value: ALL_SERVICES_TAB, label: '전체' }, ...serviceOptions] : serviceOptions
}

// 탭 값(서비스 코드 또는 ALL)을 서비스로 바꾼다. 전체이거나 모르는 값이면 null
export function findServiceByTab(services: OttService[], tab: string): OttService | null {
  return services.find((service) => service.code === tab) ?? null
}

// 사람 수 같은 개수를 "1,234"처럼 세 자리마다 쉼표를 넣어 쓴다(금액이 아니라 lib/format의 formatWon을 쓰지 않는다)
const COUNT_FORMATTER = new Intl.NumberFormat('ko-KR')
export function formatCount(count: number): string {
  return COUNT_FORMATTER.format(count)
}

/**
 * 'YYYY-MM-DD'를 "10월 15일"처럼 연도 없이 짧게 쓴다(해석할 수 없으면 원래 글자를 그대로 돌려준다).
 * lib/format의 formatDate("2026년 10월 15일")를 쓰지 않은 이유: 폭 128px 카드에서 연도까지 쓰면 두 줄로 넘친다.
 * 최근 2~4주 목록이라 연도가 없어도 헷갈리지 않는다. 전체 기준일은 목록 아래 출처 줄에 연도까지 보인다.
 */
export function formatMonthDay(value: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return value
  return `${Number(match[2])}월 ${Number(match[3])}일`
}

// 인기 순위 기간 선택지. 순서가 화면 순서이고, param은 주소의 ?period= 값(소문자)이다
export const POPULARITY_PERIOD_OPTIONS: {
  value: PopularityPeriod
  param: string
  label: string
}[] = [
  { value: 'TODAY', param: 'today', label: '오늘' },
  { value: 'WEEK', param: 'week', label: '최근 7일' },
]

// 주소의 ?period= 값을 기간으로 바꾼다(없거나 모르는 값이면 오늘)
export function parsePopularityPeriod(param: string | null): PopularityPeriod {
  return POPULARITY_PERIOD_OPTIONS.find((option) => option.param === param)?.value ?? 'TODAY'
}

// 기간을 주소의 ?period= 값으로 바꾼다
export function toPeriodParam(period: PopularityPeriod): string {
  return POPULARITY_PERIOD_OPTIONS.find((option) => option.value === period)?.param ?? 'today'
}

// 응답을 받기 전(불러오는 중·오류)에 쓰는 기준 안내. 응답이 오면 서버가 준 notice를 대신 쓴다
export const DEFAULT_POPULARITY_NOTICE = 'TMDB 인기도 기준, OTT 공식 순위 아님'

// 7일 평균을 모두 채운 날 수. 이보다 적으면 "N일치"를 덧붙여 평균이 짧은 기간임을 밝힌다
const FULL_WEEK_DAYS = 7

/**
 * 인기 순위 기준 문구. "확정"처럼 보이지 않게 무엇을 기준으로 한 순위인지(서버 notice)와 기간, 대상 범위를 함께 쓴다.
 * 예: "TMDB 인기도 기준, OTT 공식 순위 아님 · 오늘 · 넷플릭스에서 볼 수 있다고 확인된 작품만"
 * 서비스 오픈 직후에는 쌓인 날이 7일 미만이라 최근 7일 평균이 며칠치인지(daysCounted)를 밝힌다("최근 7일 평균(3일치)").
 */
export function buildPopularityBasisText(
  period: PopularityPeriod,
  scopeText: string,
  notice: string = DEFAULT_POPULARITY_NOTICE,
  daysCounted?: number,
): string {
  // 1. 7일 평균인데 쌓인 날이 7일보다 적을 때만 "N일치"를 붙인다(0일은 순위 자체가 없으므로 붙이지 않는다)
  const isPartialWeek =
    period === 'WEEK' &&
    daysCounted !== undefined &&
    daysCounted > 0 &&
    daysCounted < FULL_WEEK_DAYS
  // 2. 기간 글자
  const periodText =
    period === 'TODAY' ? '오늘' : `최근 7일 평균${isPartialWeek ? `(${daysCounted}일치)` : ''}`
  return `${notice} · ${periodText} · ${scopeText}`
}
