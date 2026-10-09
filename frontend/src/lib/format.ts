/*
 * 화면에 보이는 금액·시간·날짜 문자열을 만드는 함수 모음이다.
 *
 * 화면마다 직접 toLocaleString이나 문자열 조합을 쓰면 "15000원", "15,000 원", "₩15,000"처럼 표기가 제각각이 된다.
 * 그래서 금액·시간 표시는 반드시 이 파일의 함수를 거친다(frontend.md 화면 표시 규칙).
 * 모두 순수 함수(같은 입력이면 항상 같은 출력, 바깥 상태를 바꾸지 않음)라 테스트하기 쉽다.
 */

// 값이 없거나 계산할 수 없는 숫자(NaN 등)일 때 보여 줄 자리표시 문자다.
// 빈 문자열을 돌려주면 화면에서 칸이 통째로 사라져 "값이 0인지, 없는 건지" 구분할 수 없어 눈에 보이는 문자를 둔다.
export const EMPTY_DISPLAY = '-'

// 금액용 숫자 형식. 매번 new Intl.NumberFormat을 만들면 비용이 들어 모듈에서 한 번만 만든다.
// maximumFractionDigits: 0 — 계약상 금액은 원 단위 정수다(api-contract.md 데이터 형식). 소수가 와도 원 단위로 반올림해 보인다.
const WON_FORMATTER = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 })

/**
 * 원 단위 금액을 "15,000원" 형식으로 바꾼다.
 *
 * - 음수는 "-3,000원"으로 그대로 보인다(예: 절감액이 음수인 경우를 숨기지 않기 위함).
 * - NaN·Infinity·null·undefined는 EMPTY_DISPLAY("-")를 돌려준다. 계약상 금액은 항상 정수지만,
 *   계산 실수로 "NaN원"이 화면에 노출되는 일을 막기 위한 방어다.
 */
export function formatWon(amount: number | null | undefined): string {
  if (amount == null || !Number.isFinite(amount)) return EMPTY_DISPLAY
  // -0.4처럼 반올림하면 -0이 되는 값이 "-0원"으로 보이지 않게 0으로 바꾼다
  const rounded = Math.round(amount) || 0
  return `${WON_FORMATTER.format(rounded)}원`
}

/**
 * 분 단위 시간을 "12시간 30분" 형식으로 바꾼다.
 *
 * 경계값 처리(테스트 format.test.ts와 짝을 이룬다):
 * - 0분 → "0분": 시청 시간 0은 "없음"이 아니라 실제 값이므로 숫자로 보인다.
 * - 60분 미만(45) → "45분": "0시간 45분"은 읽기 번거로워 시간 부분을 생략한다.
 * - 정각(120) → "2시간": "2시간 0분"의 "0분"은 정보가 없어 생략한다.
 * - 24시간 이상(1500) → "25시간": 월 시청 시간처럼 큰 값을 다루므로 "일" 단위로 올리지 않는다.
 * - 소수(90.6) → 반올림해 "1시간 31분": 계약상 시간은 분 단위 정수지만 방어적으로 처리한다.
 * - 음수·NaN·Infinity·null·undefined → EMPTY_DISPLAY("-"): 시청 시간·러닝타임은 음수가 될 수 없어
 *   음수가 왔다면 데이터 오류다. "-30분"처럼 그럴듯하게 보이면 오류를 놓치기 쉬워 표시하지 않는다.
 */
export function formatMinutes(minutes: number | null | undefined): string {
  if (minutes == null || !Number.isFinite(minutes) || minutes < 0) return EMPTY_DISPLAY
  const totalMinutes = Math.round(minutes)
  const hours = Math.floor(totalMinutes / 60)
  const restMinutes = totalMinutes % 60

  if (hours === 0) return `${restMinutes}분`
  if (restMinutes === 0) return `${hours}시간`
  return `${hours}시간 ${restMinutes}분`
}

// 날짜만 있는 값('2026-10-09', 계약의 format: date)을 알아보는 정규식이다.
const DATE_ONLY_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/

// "2026년 10월 9일" 형식. 날짜만 있는 값은 UTC 기준으로 만든 Date를 UTC로 읽어 날짜가 밀리지 않게 한다(아래 함수 주석).
const DATE_ONLY_FORMATTER = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  timeZone: 'UTC',
})

// 시각까지 있는 값(계약의 format: date-time, 오프셋 포함)은 한국 시간 기준 날짜로 보인다.
// 국내 OTT 서비스이고 수집 배치도 한국 날짜 기준이라, 사용자 컴퓨터 시간대가 달라도 같은 날짜가 보이게 한다.
const DATE_TIME_FORMATTER = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  timeZone: 'Asia/Seoul',
})

/**
 * 일반 날짜(개봉일·첫 방영일 등)를 "2026년 10월 9일" 형식으로 바꾼다. 해석할 수 없으면 null을 돌려준다.
 *
 * 받을 수 있는 값:
 * - 'YYYY-MM-DD' (계약의 날짜 형식)
 * - ISO-8601 일시 문자열(오프셋 포함, 예: '2026-10-09T14:00:00+09:00') → 한국 시간 기준 날짜
 * - Date 객체 → 한국 시간 기준 날짜
 *
 * 'YYYY-MM-DD'를 new Date()에 바로 넣지 않는 이유:
 * new Date('2026-10-09')는 UTC 자정으로 해석된다. 사용자의 시간대가 UTC보다 늦으면(예: 미국) 현지 시각으로 읽을 때
 * 전날(10월 8일)로 보인다. 그래서 연·월·일을 직접 나눠 UTC 기준 Date를 만들고 UTC로 읽는다.
 *
 * 잘못된 값(빈 문자열, '2026-02-30' 같은 없는 날짜, 형식이 다른 문자열)에 null을 돌려주는 이유:
 * 기준일은 "확인된 범위"를 알려 주는 중요한 정보라 엉뚱한 날짜를 보이느니 표시하는 쪽(DataAsOf 등)이
 * "기준일 정보 없음"처럼 따로 안내하게 하기 위함이다. 금액·시간처럼 "-"로 바꾸지 않고 null을 돌려준다.
 */
export function formatDate(value: string | Date | null | undefined): string | null {
  if (value == null) return null

  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : DATE_TIME_FORMATTER.format(value)
  }

  const trimmed = value.trim()
  if (trimmed === '') return null

  const dateOnlyMatch = DATE_ONLY_PATTERN.exec(trimmed)
  if (dateOnlyMatch) {
    const [, yearText, monthText, dayText] = dateOnlyMatch
    const year = Number(yearText)
    const month = Number(monthText)
    const day = Number(dayText)
    const date = new Date(Date.UTC(year, month - 1, day))
    // Date.UTC는 2월 30일을 3월 2일로 넘겨 버린다. 만든 날짜를 다시 읽어 입력과 같은지 확인해 없는 날짜를 걸러 낸다
    const isRealDate =
      date.getUTCFullYear() === year &&
      date.getUTCMonth() === month - 1 &&
      date.getUTCDate() === day
    return isRealDate ? DATE_ONLY_FORMATTER.format(date) : null
  }

  // 날짜만 있는 형식이 아니면 일시(ISO-8601)로 본다. 'abc'처럼 해석할 수 없으면 Invalid Date가 된다
  const dateTime = new Date(trimmed)
  return Number.isNaN(dateTime.getTime()) ? null : DATE_TIME_FORMATTER.format(dateTime)
}

/**
 * 데이터 기준일을 "2026년 10월 9일" 형식으로 바꾼다(해석할 수 없으면 null).
 *
 * 표기는 formatDate와 같지만 이름을 나눈 이유: "기준일"은 확인된 범위를 알리는 정보라 개봉일 같은 일반 날짜와 의미가 다르고,
 * 나중에 기준일 표기만 바꾸고 싶을 때(예: 시각 추가) 개봉일 표기까지 흔들리지 않게 하기 위함이다.
 */
export function formatDataAsOf(value: string | Date | null | undefined): string | null {
  return formatDate(value)
}
