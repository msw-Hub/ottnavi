package com.ottnavi.notification.service;

import com.ottnavi.global.common.PageResponse;
import com.ottnavi.notification.domain.entity.NotificationOutbox;
import com.ottnavi.notification.domain.enums.NotificationStatus;
import com.ottnavi.notification.domain.repository.NotificationOutboxRepository;
import com.ottnavi.notification.service.model.NotificationHistoryItem;
import com.ottnavi.notification.service.model.NotificationSummaryView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림 이력(본인)과 발송 상태 요약(관리자) 조회. 읽기 전용이다 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationQueryService {

	private static final int SUMMARY_WINDOW_DAYS = 7; // 관리자 요약 집계 기간(일). 계약 windowDays

	private final NotificationOutboxRepository notificationOutboxRepository;
	private final Clock clock;

	/** 본인 알림을 최신순으로 한 페이지 돌려준다. 이력이 없으면 content가 빈 배열이다 */
	public PageResponse<NotificationHistoryItem> listMine(Long userId, int page, int size) {
		// 1. 본인 알림만 최신순으로 읽는다(userId 조건이 타인 알림을 막는다)
		Page<NotificationOutbox> found = notificationOutboxRepository
				.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(page, size));

		// 2. payload·lastError를 뺀 값 묶음으로 바꾼다
		List<NotificationHistoryItem> content = new ArrayList<>();
		for (NotificationOutbox row : found.getContent()) {
			content.add(new NotificationHistoryItem(row.getId(), row.getType(), row.getStatus(),
					row.getCreatedAt(), row.getSentAt()));
		}
		return PageResponse.of(content, page, size, found.getTotalElements());
	}

	/**
	 * 최근 7일 알림 발송 상태 요약을 돌려준다.
	 * 상태별 건수는 7일 안에 "만들어진" 알림을 세고, latestSentAt은 7일 안에 "발송된" 알림의 가장 늦은 sentAt이다
	 * (지난주에 만들어져 오늘 발송된 알림도 latestSentAt에는 잡힌다).
	 */
	public NotificationSummaryView getSummary() {
		// 1. 집계 시작 시각
		Instant since = Instant.now(clock).minus(Duration.ofDays(SUMMARY_WINDOW_DAYS));

		// 2. 상태별 건수와 마지막 발송 시각
		long pending = notificationOutboxRepository.countByStatusAndCreatedAtGreaterThanEqual(NotificationStatus.PENDING, since);
		long sent = notificationOutboxRepository.countByStatusAndCreatedAtGreaterThanEqual(NotificationStatus.SENT, since);
		long failed = notificationOutboxRepository.countByStatusAndCreatedAtGreaterThanEqual(NotificationStatus.FAILED, since);
		Optional<Instant> latestSentAt = notificationOutboxRepository.findLatestSentAtSince(since);

		// 3. 마지막 발송이 없으면 null로 둔다
		Instant latest = null;
		if (latestSentAt.isPresent()) {
			latest = latestSentAt.get();
		}
		return new NotificationSummaryView(SUMMARY_WINDOW_DAYS, pending, sent, failed, latest);
	}
}
