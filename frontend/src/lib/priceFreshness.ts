import { diffInDays } from '@/lib/dateUtils'
import { formatIsoDate } from '@/lib/format'

/*
 * 월 구독요금의 "마지막 확인이 오래됐는지" 판정이다. 관리자 화면(요금 확인일 오래됨 태그)과 회원의 요금 확인 화면(SCR-09,
 * PRD 11절 41번)이 같은 기준을 써야 해서 기능 폴더가 아니라 lib에 뒀다(기능 폴더끼리는 import하지 않는다).
 */

// 마지막 확인이 오늘로부터 이 일수를 넘으면 "오래됨"으로 본다(PRD 11절 41번)
export const STALE_VERIFIED_DAYS = 90

/**
 * 마지막 확인 일시가 오늘로부터 90일을 넘었는지 돌려준다. 날짜를 해석할 수 없으면 오래된 것으로 본다(확인을 유도).
 *
 * 계약의 lastVerifiedAt은 일시(date-time)다. 화면과 같은 기준으로 한국 날짜로 바꾼 뒤(formatIsoDate, Asia/Seoul) 날짜끼리 비교한다.
 * 일시를 그대로 시각 차이로 비교하면 하루 중 몇 시냐에 따라 "90일째"가 흔들리고, 계약도 "한국 날짜 기준"으로 판정하라고 정했다.
 * 'YYYY-MM-DD' 날짜만 들어와도 같은 날짜로 읽힌다. today는 'YYYY-MM-DD'(한국 날짜)다.
 */
export function isVerifiedStale(lastVerifiedAt: string, today: string): boolean {
  const verifiedDate = formatIsoDate(lastVerifiedAt)
  if (verifiedDate === null) return true
  return diffInDays(verifiedDate, today) > STALE_VERIFIED_DAYS
}
