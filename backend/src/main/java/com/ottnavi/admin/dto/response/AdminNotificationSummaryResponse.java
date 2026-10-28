package com.ottnavi.admin.dto.response;

import com.ottnavi.notification.service.model.NotificationSummaryView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 최근 7일 알림 발송 상태 요약 응답(계약 AdminNotificationSummary). 사용자 이메일이나 본문은 담지 않는다.
 *
 * @param windowDays   집계 기간(일). 지금은 7
 * @param pendingCount 대기 중(발송 전이거나 재시도 대기) 건수
 * @param sentCount    발송한 건수
 * @param failedCount  재시도 5회를 넘겨 포기한 건수
 * @param latestSentAt 최근 7일 안에 발송에 성공한 알림 중 가장 늦은 발송 시각(KST). 없으면 null
 */
public record AdminNotificationSummaryResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "7") int windowDays,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2") long pendingCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "148") long sentCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") long failedCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "2026-11-01T03:51:12+09:00") OffsetDateTime latestSentAt) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static AdminNotificationSummaryResponse from(NotificationSummaryView view) {
		OffsetDateTime latestSentAt = null;
		if (view.latestSentAt() != null) {
			latestSentAt = view.latestSentAt().atZone(KST).toOffsetDateTime();
		}
		return new AdminNotificationSummaryResponse(view.windowDays(), view.pendingCount(), view.sentCount(),
				view.failedCount(), latestSentAt);
	}
}
