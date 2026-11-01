package com.ottnavi.admin.dto.response;

import com.ottnavi.admin.service.model.AdminUserStatsView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 관리자 회원 현황 응답(계약 AdminUserStats). USER 권한 회원만 센 값이며 개인정보는 담지 않는다.
 *
 * @param totalUserCount               현재 남아 있는 회원 수
 * @param todayNewUserCount            오늘 00:00(KST) 이후 가입했고 남아 있는 회원 수
 * @param monthNewUserCount            이번 달 1일 00:00(KST) 이후 가입했고 남아 있는 회원 수
 * @param activePlanUserCount          ACTIVE 플랜을 가진 회원 수
 * @param notificationEnabledUserCount 알림을 켠 회원 수(설정 행이 없으면 꺼짐)
 * @param measuredAt                   집계 시각(KST 오프셋)
 */
public record AdminUserStatsResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "42") long totalUserCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3") long todayNewUserCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "17") long monthNewUserCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "12") long activePlanUserCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "20") long notificationEnabledUserCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11-01T10:30:00+09:00") OffsetDateTime measuredAt) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static AdminUserStatsResponse from(AdminUserStatsView view) {
		return new AdminUserStatsResponse(view.totalUserCount(), view.todayNewUserCount(), view.monthNewUserCount(),
				view.activePlanUserCount(), view.notificationEnabledUserCount(), view.measuredAt());
	}
}
