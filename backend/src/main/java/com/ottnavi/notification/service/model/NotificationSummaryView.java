package com.ottnavi.notification.service.model;

import java.time.Instant;

/**
 * 최근 N일 알림 발송 상태 요약.
 *
 * @param windowDays   집계 기간(일)
 * @param pendingCount 기간 안에 만들어진 알림 중 대기 건수
 * @param sentCount    기간 안에 만들어진 알림 중 발송 건수
 * @param failedCount  기간 안에 만들어진 알림 중 실패 건수
 * @param latestSentAt 기간 안에 발송에 성공한 알림 중 가장 늦은 발송 시각(없으면 null)
 */
public record NotificationSummaryView(int windowDays, long pendingCount, long sentCount, long failedCount,
		Instant latestSentAt) {
}
