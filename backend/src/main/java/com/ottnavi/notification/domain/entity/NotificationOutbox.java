package com.ottnavi.notification.domain.entity;

import com.ottnavi.global.common.BaseTimeEntity;
import com.ottnavi.notification.domain.enums.NotificationStatus;
import com.ottnavi.notification.domain.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 보낼 메일 한 통의 기록(ERD notification_outbox). 메일을 보내기 전에 이 표에 먼저 적고, 별도 발송기가 읽어 보낸다.
 * "처리 중" 상태 대신 점유(claim)할 때 next_attempt_at을 임대 시간만큼 미뤄 두므로, 발송 중 앱이 죽어도 임대가 끝나면 다시 집힌다(TECH 5절).
 * 사용자는 ID만 들고, 사용자가 탈퇴하면 DB FK CASCADE가 이 행도 지운다.
 */
@Getter
@Entity
@Table(name = "notification_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox extends BaseTimeEntity {

	private static final int LAST_ERROR_MAX_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId; // 받는 사람

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationType type; // SUBSCRIBE_GUIDE / MONTHLY / PLAN_CHANGE

	@Column(nullable = false, length = 100)
	private String dedupeKey; // 중복 생성 방지 키. 예: MONTHLY:{userId}:2026-11

	// 메일 본문을 만들 재료(JSON 문자열). Java에서는 문자열로 다루고 DB에서는 JSONB로 저장한다
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private NotificationStatus status; // PENDING / SENT / FAILED

	@Column(nullable = false)
	private int attemptCount; // 점유한 횟수(발송을 시도한 횟수)

	@JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
	@Column(nullable = false)
	private Instant nextAttemptAt; // 이 시각 이후에 집는다. 점유 중에는 임대 만료 시각이다

	@JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
	private Instant sentAt; // 발송 성공 시각

	@Column(columnDefinition = "text")
	private String lastError; // 마지막 실패 사유(500자 이내)

	/** 대기 상태(PENDING)의 알림을 만든다. nextAttemptAt이 지금 이전이면 다음 발송 주기에 바로 집힌다 */
	public static NotificationOutbox create(Long userId, NotificationType type, String dedupeKey, String payloadJson,
			Instant nextAttemptAt) {
		NotificationOutbox outbox = new NotificationOutbox();
		outbox.userId = userId;
		outbox.type = type;
		outbox.dedupeKey = dedupeKey;
		outbox.payload = payloadJson;
		outbox.status = NotificationStatus.PENDING;
		outbox.attemptCount = 0;
		outbox.nextAttemptAt = nextAttemptAt;
		return outbox;
	}

	/**
	 * 발송을 시작하려고 점유한다. 시도 횟수를 하나 올리고 임대가 끝나는 시각까지 다시 집히지 않게 미룬다.
	 * 영속 상태의 엔티티라 트랜잭션이 커밋될 때 더티 체킹으로 UPDATE가 나간다.
	 */
	public void claim(Instant now, Duration lease) {
		this.attemptCount = this.attemptCount + 1;
		this.nextAttemptAt = now.plus(lease);
	}

	/** 발송 성공을 기록한다 */
	public void markSent(Instant now) {
		this.status = NotificationStatus.SENT;
		this.sentAt = now;
		this.lastError = null;
	}

	/**
	 * 발송 실패를 기록한다. 시도 횟수가 최대에 닿았으면 FAILED로 끝내고, 아니면 PENDING으로 두고 다음 시도 시각을 정한다.
	 * 예: 최대 5회, 간격 [1분, 5분, 30분, 2시간]이면 1회째 실패 → 1분 뒤, 2회째 → 5분 뒤, …, 5회째 실패 → FAILED.
	 */
	public void recordFailure(String error, int maxAttempts, List<Duration> retryDelays, Instant now) {
		this.lastError = truncate(error);
		if (this.attemptCount >= maxAttempts) {
			this.status = NotificationStatus.FAILED;
			return;
		}
		// 간격 목록이 모자라면 마지막 간격을 쓴다(설정 검증이 막지만 방어)
		int index = this.attemptCount - 1;
		if (index >= retryDelays.size()) {
			index = retryDelays.size() - 1;
		}
		this.nextAttemptAt = now.plus(retryDelays.get(index));
	}

	/** 더 시도하지 않고 FAILED로 끝낸다(임대가 만료됐는데 최대 시도에 닿은 행 등) */
	public void markExhausted(String reason) {
		this.status = NotificationStatus.FAILED;
		this.lastError = truncate(reason);
	}

	private static String truncate(String text) {
		if (text == null) {
			return null;
		}
		if (text.length() > LAST_ERROR_MAX_LENGTH) {
			return text.substring(0, LAST_ERROR_MAX_LENGTH);
		}
		return text;
	}
}
