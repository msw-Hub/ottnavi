package com.ottnavi.notification.dto.response;

import com.ottnavi.notification.domain.enums.NotificationStatus;
import com.ottnavi.notification.domain.enums.NotificationType;
import com.ottnavi.notification.service.model.NotificationHistoryItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 알림 이력 한 건 응답(계약 NotificationHistoryItem). 본문 재료와 실패 사유는 담지 않는다.
 *
 * @param notificationId 알림 ID
 * @param type           구독 안내 / 월초 안내 / 플랜 변경 알림
 * @param status         대기 / 발송 / 실패(재시도 5회를 넘김)
 * @param createdAt      알림이 만들어진 시각(KST)
 * @param sentAt         발송 성공 시각(KST). 발송 전이거나 실패면 null
 */
public record NotificationHistoryItemResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "31") Long notificationId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "MONTHLY") NotificationType type,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SENT") NotificationStatus status,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11-01T03:50:00+09:00") OffsetDateTime createdAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "2026-11-01T03:51:12+09:00") OffsetDateTime sentAt) {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** 서비스 계층 값을 응답으로 바꾼다. 시각은 한국 시간대 오프셋으로 내보낸다 */
	public static NotificationHistoryItemResponse from(NotificationHistoryItem item) {
		return new NotificationHistoryItemResponse(item.notificationId(), item.type(), item.status(),
				toKst(item.createdAt()), toKst(item.sentAt()));
	}

	private static OffsetDateTime toKst(Instant instant) {
		if (instant == null) {
			return null;
		}
		return instant.atZone(KST).toOffsetDateTime();
	}
}
