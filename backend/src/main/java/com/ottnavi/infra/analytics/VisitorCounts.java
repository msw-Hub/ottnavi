package com.ottnavi.infra.analytics;

/**
 * GA에서 읽은 동의한 방문자 수(activeUsers) 네 값.
 *
 * @param today      오늘
 * @param yesterday  어제
 * @param last7Days  오늘 포함 최근 7일(기간 전체에서 중복을 제거한 값이라 일별 합과 다르다)
 * @param last30Days 오늘 포함 최근 30일
 */
public record VisitorCounts(long today, long yesterday, long last7Days, long last30Days) {
}
