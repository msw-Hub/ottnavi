package com.ottnavi.llmdraft.service.model;

/**
 * 호출 상한과 사용량.
 *
 * @param usedToday      오늘(태평양 시간 날짜 기준) 호출한 수
 * @param dailyLimit     하루 상한
 * @param perMinuteLimit 분당 상한
 */
public record QuotaUsage(long usedToday, int dailyLimit, int perMinuteLimit) {
}
