package com.ottnavi.admin.service.model;

import java.time.OffsetDateTime;

/** 관리자 회원 현황 값 묶음(서버 안). 응답 DTO로 바뀌기 전 단계다 */
public record AdminUserStatsView(long totalUserCount, long todayNewUserCount, long monthNewUserCount,
		long activePlanUserCount, long notificationEnabledUserCount, OffsetDateTime measuredAt) {
}
