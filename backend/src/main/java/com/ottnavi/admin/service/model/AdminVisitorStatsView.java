package com.ottnavi.admin.service.model;

import java.time.OffsetDateTime;

/** 관리자 방문자 통계 값 묶음(서버 안). status가 OK가 아니면 숫자와 fetchedAt은 모두 null이다 */
public record AdminVisitorStatsView(VisitorStatsStatus status, Long todayVisitorCount, Long yesterdayVisitorCount,
		Long last7DaysVisitorCount, Long last30DaysVisitorCount, OffsetDateTime fetchedAt) {

	/** 연동 설정이 없을 때의 값 */
	public static AdminVisitorStatsView disabled() {
		return new AdminVisitorStatsView(VisitorStatsStatus.DISABLED, null, null, null, null, null);
	}

	/** 조회가 실패했을 때의 값 */
	public static AdminVisitorStatsView unavailable() {
		return new AdminVisitorStatsView(VisitorStatsStatus.UNAVAILABLE, null, null, null, null, null);
	}
}
