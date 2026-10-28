package com.ottnavi.notification.service.model;

import com.ottnavi.notification.domain.enums.NotificationType;

/**
 * 발송기가 점유해 간 알림 한 건. 트랜잭션 밖에서 메일을 만들고 보낼 때 필요한 값만 담는다.
 *
 * @param id           outbox 행 ID
 * @param userId       받는 사람
 * @param type         알림 유형
 * @param payload      메일 본문 재료(JSON 문자열)
 * @param attemptCount 이번이 몇 번째 시도인지(점유 후 값)
 */
public record ClaimedNotification(Long id, Long userId, NotificationType type, String payload, int attemptCount) {
}
