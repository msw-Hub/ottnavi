package com.ottnavi.notification.service.model;

import com.ottnavi.notification.domain.enums.NotificationStatus;
import com.ottnavi.notification.domain.enums.NotificationType;
import java.time.Instant;

/**
 * 알림 이력 한 건. 메일 본문 재료(payload)와 실패 사유(lastError)는 일부러 담지 않는다.
 *
 * @param notificationId 알림 ID
 * @param type           유형
 * @param status         상태
 * @param createdAt      만든 시각
 * @param sentAt         발송 성공 시각(발송 전이거나 실패면 null)
 */
public record NotificationHistoryItem(Long notificationId, NotificationType type, NotificationStatus status,
		Instant createdAt, Instant sentAt) {
}
