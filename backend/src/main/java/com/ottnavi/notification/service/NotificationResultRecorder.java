package com.ottnavi.notification.service;

import com.ottnavi.notification.domain.entity.NotificationOutbox;
import com.ottnavi.notification.domain.enums.NotificationStatus;
import com.ottnavi.notification.domain.repository.NotificationOutboxRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 알림 처리의 기록 단계: 발송 결과를 짧은 트랜잭션으로 기록한다. 메일을 보내는 동안 행이 지워졌을 수 있어(탈퇴) 행이 없거나 다른 발송기가 다시 점유했으면 기록하지 않고 로그만 남긴다 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationResultRecorder {

	private final NotificationOutboxRepository outboxRepository;
	private final NotificationProperties properties;

	/**
	 * 발송 성공을 기록한다. 기록하면 true, 기록하지 않으면 false를 돌려준다.
	 * 행이 없거나(탈퇴로 삭제) 그 사이 다른 발송기가 다시 점유했으면(시도 횟수가 다름) 아무것도 쓰지 않는다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW) // 즉시 발송이 다른 트랜잭션 안에서 불려도 기록이 따로 커밋되게 한다
	public boolean recordSent(Long outboxId, int claimedAttemptCount, Instant now) {
		// 1. 행이 있고 아직 내 점유인지 확인한다
		Optional<NotificationOutbox> found = findOwned(outboxId, claimedAttemptCount);
		if (found.isEmpty()) {
			return false;
		}
		// 2. 내 점유일 때만 성공을 기록한다
		found.get().markSent(now);
		return true;
	}

	/**
	 * 발송 실패를 기록하고 그 결과의 상태를 돌려준다. PENDING이면 재시도 예정, FAILED면 포기다.
	 * 기록하지 않은 경우(행이 없거나 다른 발송기가 다시 점유함)는 빈 값이다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<NotificationStatus> recordFailure(Long outboxId, int claimedAttemptCount, String error, Instant now) {
		// 1. 행이 있고 아직 내 점유인지 확인한다
		Optional<NotificationOutbox> found = findOwned(outboxId, claimedAttemptCount);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		// 2. 내 점유일 때만 실패를 기록한다
		NotificationOutbox outbox = found.get();
		outbox.recordFailure(error, properties.maxAttempts(), properties.retryDelays(), now);
		return Optional.of(outbox.getStatus());
	}

	/** 행이 있고 점유 세대가 같을 때만 돌려준다. 아니면 이유를 info로 남기고 빈 값이다(수신 주소는 남기지 않는다) */
	private Optional<NotificationOutbox> findOwned(Long outboxId, int claimedAttemptCount) {
		Optional<NotificationOutbox> found = outboxRepository.findById(outboxId);
		if (found.isEmpty()) {
			log.info("결과를 기록할 알림 행이 없다(그 사이 삭제됨): outboxId={}", outboxId);
			return Optional.empty();
		}
		if (!found.get().isClaimedBy(claimedAttemptCount)) {
			log.info("점유가 바뀌었거나 이미 끝난 알림이라 결과를 기록하지 않는다: outboxId={}", outboxId);
			return Optional.empty();
		}
		return found;
	}
}
