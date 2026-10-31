package com.ottnavi.admin.dto.response;

import com.ottnavi.admin.service.model.AdminVisitorStatsView;
import com.ottnavi.admin.service.model.VisitorStatsStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 관리자 방문자 통계 응답(계약 AdminVisitorStats). 방문 통계에 동의한 방문자만 센 값이다.
 * status가 OK가 아니면 숫자와 fetchedAt은 모두 null이다.
 *
 * @param status                 조회 상태(OK, DISABLED, UNAVAILABLE)
 * @param todayVisitorCount      오늘 동의한 방문자 수(GA 처리 지연 때문에 잠정값)
 * @param yesterdayVisitorCount  어제 동의한 방문자 수
 * @param last7DaysVisitorCount  오늘 포함 최근 7일 동의한 방문자 수(기간 전체 중복 제거)
 * @param last30DaysVisitorCount 오늘 포함 최근 30일 동의한 방문자 수(기간 전체 중복 제거)
 * @param fetchedAt              GA에서 값을 받아 온 시각(캐시된 값이면 캐시를 만든 시각)
 */
public record AdminVisitorStatsResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "OK") VisitorStatsStatus status,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "12") Long todayVisitorCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "30") Long yesterdayVisitorCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "150") Long last7DaysVisitorCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "480") Long last30DaysVisitorCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "2026-11-01T10:30:00+09:00") OffsetDateTime fetchedAt) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static AdminVisitorStatsResponse from(AdminVisitorStatsView view) {
		return new AdminVisitorStatsResponse(view.status(), view.todayVisitorCount(), view.yesterdayVisitorCount(),
				view.last7DaysVisitorCount(), view.last30DaysVisitorCount(), view.fetchedAt());
	}
}
