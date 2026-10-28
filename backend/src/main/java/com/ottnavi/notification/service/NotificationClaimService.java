package com.ottnavi.notification.service;

import com.ottnavi.notification.domain.entity.NotificationOutbox;
import com.ottnavi.notification.domain.enums.NotificationStatus;
import com.ottnavi.notification.domain.repository.NotificationOutboxRepository;
import com.ottnavi.notification.service.model.ClaimedNotification;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 처리의 점유 단계: 발송할 행을 짧은 트랜잭션 안에서 점유한다(TECH 5절).
 * 행을 잠가 읽고 시도 횟수와 임대 시각만 기록한 뒤 바로 커밋하므로, 메일을 보내는 동안 DB 연결과 잠금을 붙잡지 않는다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationClaimService {

	private final NotificationOutboxRepository outboxRepository;
	private final NotificationProperties properties;
	private final NotificationMetrics metrics;

	/**
	 * 발송 시각이 된 대기 행을 최대 batch-size건 점유해 돌려준다.
	 * 이미 최대 시도에 닿은 행(임대가 만료됐지만 결과가 기록되지 않은 행)은 보내지 않고 FAILED로 끝낸다.
	 */
	@Transactional
	public List<ClaimedNotification> claimDue(Instant now) {
		// 1. 발송 시각이 된 행을 SKIP LOCKED로 잠가 읽는다(다른 발송기가 잡은 행은 건너뜀)
		List<NotificationOutbox> due = outboxRepository.findDueForUpdate(now, Limit.of(properties.batchSize()));

		List<ClaimedNotification> claimed = new ArrayList<>();
		for (NotificationOutbox outbox : due) {
			// 2. 행마다 점유한다(최대 시도에 닿은 행은 포기 처리되어 빈 값)
			Optional<ClaimedNotification> one = claim(outbox, now);
			if (one.isPresent()) {
				claimed.add(one.get());
			}
		}
		return claimed;
	}

	/**
	 * 플랜 저장 직후 즉시 발송을 위해 그 행 하나만 점유한다. 별도 트랜잭션(REQUIRES_NEW)이라 호출한 쪽의 트랜잭션과 무관하게 바로 커밋된다.
	 * 스케줄러가 이미 잡았거나 아직 때가 아니거나 끝난 행, 다른 곳이 잠근 행은 빈 값이다(그 경우 발송은 스케줄러에 맡긴다).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<ClaimedNotification> claimOne(Long outboxId, Instant now) {
		// 1. 때가 된 대기 행인지 잠가 읽는다
		Optional<NotificationOutbox> found = outboxRepository.findOneDueForUpdate(outboxId, now);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		// 2. claimDue와 같은 방식으로 점유한다
		return claim(found.get(), now);
	}

	/** 행 하나를 점유한다. 최대 시도에 닿은 행(임대가 만료됐지만 결과가 기록되지 않은 행)은 보내지 않고 FAILED로 끝내 빈 값을 돌려준다 */
	private Optional<ClaimedNotification> claim(NotificationOutbox outbox, Instant now) {
		// 1. 최대 시도에 닿은 행은 발송하지 않고 포기 처리한다(앞선 시도의 결과 기록이 유실된 경우)
		if (outbox.getAttemptCount() >= properties.maxAttempts()) {
			outbox.markExhausted("최대 시도 도달: 마지막 시도 결과 미기록");
			metrics.count(NotificationMetrics.FAILED);
			log.warn("알림을 최대 시도 횟수에 도달해 포기한다: outboxId={}", outbox.getId());
			return Optional.empty();
		}

		// 2. 시도 횟수를 올리고 임대 시각까지 미룬다(더티 체킹으로 커밋 때 UPDATE)
		outbox.claim(now, properties.lease());
		return Optional.of(new ClaimedNotification(outbox.getId(), outbox.getUserId(), outbox.getType(),
				outbox.getPayload(), outbox.getAttemptCount()));
	}

	/** 점유한 행이 아직 있고 대기 상태인지 확인한다(탈퇴로 지워졌거나 다른 경로로 끝났으면 false). 발송 직전 재확인용 */
	public boolean isStillPending(Long outboxId) {
		return outboxRepository.existsByIdAndStatus(outboxId, NotificationStatus.PENDING);
	}
}
