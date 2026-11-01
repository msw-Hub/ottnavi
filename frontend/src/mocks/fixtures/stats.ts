/*
 * 관리자 모니터링 "운영 현황"(SCR-17)의 회원 현황·방문자 수 목업 데이터다.
 * 계약의 AdminUserStats·AdminVisitorStats 모양 그대로이고 mocks/statsHandlers.ts가 돌려준다. 실제 집계 값이 아니다.
 * 집계·조회 시각은 화면에서 "기준 ..."으로 보이므로 고정 날짜 대신 요청 시점(지금)으로 만든다.
 */
import type { AdminUserStats, AdminVisitorStats } from '@/api/generated/model'

/** 회원 현황 목업 값. 요청마다 집계 시각(measuredAt)만 지금으로 새로 만든다. */
export function buildMockUserStats(): AdminUserStats {
  return {
    totalUserCount: 42,
    todayNewUserCount: 1,
    monthNewUserCount: 9,
    activePlanUserCount: 17,
    notificationEnabledUserCount: 11,
    measuredAt: new Date().toISOString(),
  }
}

/** 회원이 한 명도 없는 상태(empty 시나리오). 0도 실제로 센 값이라 모든 칸이 0이다. */
export function buildMockEmptyUserStats(): AdminUserStats {
  return {
    ...buildMockUserStats(),
    totalUserCount: 0,
    todayNewUserCount: 0,
    monthNewUserCount: 0,
    activePlanUserCount: 0,
    notificationEnabledUserCount: 0,
  }
}

/** 방문 통계가 정상(OK)일 때의 목업 값. */
export function buildMockVisitorStats(): AdminVisitorStats {
  return {
    status: 'OK',
    todayVisitorCount: 12,
    yesterdayVisitorCount: 31,
    last7DaysVisitorCount: 118,
    last30DaysVisitorCount: 402,
    fetchedAt: new Date().toISOString(),
  }
}

/** 방문 통계 연동 설정이 없는(DISABLED) 또는 조회가 잠깐 실패한(UNAVAILABLE) 상태. 계약대로 숫자와 fetchedAt은 모두 null이다. */
export function buildMockVisitorStatsWithoutNumbers(
  status: 'DISABLED' | 'UNAVAILABLE',
): AdminVisitorStats {
  return {
    status,
    todayVisitorCount: null,
    yesterdayVisitorCount: null,
    last7DaysVisitorCount: null,
    last30DaysVisitorCount: null,
    fetchedAt: null,
  }
}
