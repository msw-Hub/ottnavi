package com.ottnavi.notification.service;

import com.ottnavi.notification.domain.entity.NotificationOutbox;
import com.ottnavi.notification.domain.enums.NotificationStatus;
import com.ottnavi.notification.domain.repository.NotificationOutboxRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림 처리 3단계: 발송 결과를 짧은 트랜잭션으로 기록한다. 메일을 보내는 동안 행이 지워졌을 수 있어(탈퇴) 행이 없으면 로그만 남긴다 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationResultRecorder {

	private final NotificationOutboxRepository outboxRepository;
	private final NotificationProperties properties;

	/** 발송 성공을 기록한다. 행이 없으면(탈퇴로 삭제) 아무것도 하지 않고 false를 돌려준다 */
	@Transactional
	public boolean recordSent(Long outboxId, Instant now) {
		Optional<NotificationOutbox> found = outboxRepository.findById(outboxId);
		if (found.isEmpty()) {
			log.info("발송 성공을 기록할 알림 행이 없다(그 사이 삭제됨): outboxId={}", outboxId);
			return false;
		}
		found.get().markSent(now);
		return true;
	}

	/**
	 * 발송 실패를 기록하고 그 결과의 상태를 돌려준다. PENDING이면 재시도 예정, FAILED면 포기다.
	 * 행이 없으면 빈 값이다.
	 */
	@Transactional
	public Optional<NotificationStatus> recordFailure(Long outboxId, String error, Instant now) {
		Optional<NotificationOutbox> found = outboxRepository.findById(outboxId);
		if (found.isEmpty()) {
			log.info("발송 실패를 기록할 알림 행이 없다(그 사이 삭제됨): outboxId={}", outboxId);
			return Optional.empty();
		}
		NotificationOutbox outbox = found.get();
		outbox.recordFailure(error, properties.maxAttempts(), properties.retryDelays(), now);
		return Optional.of(outbox.getStatus());
	}
}
