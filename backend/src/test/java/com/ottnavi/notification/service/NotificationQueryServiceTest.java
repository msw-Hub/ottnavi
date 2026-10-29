package com.ottnavi.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ottnavi.notification.domain.enums.NotificationStatus;
import com.ottnavi.notification.domain.repository.NotificationOutboxRepository;
import com.ottnavi.notification.service.model.NotificationSummaryView;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 관리자 요약의 7일 기준을 DB 없이 확인한다.
 * 막으려는 사고: 집계 시작 시각이 7일이 아닌 일, 발송이 없는데 latestSentAt이 null이 아닌 일.
 */
class NotificationQueryServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
	private static final Instant SINCE = Instant.parse("2026-11-01T00:00:00Z"); // NOW - 7일

	@Test
	void 요약은_지금_기준_7일_전부터_센다() {
		// given: 집계 시작 시각이 SINCE일 때만 값이 나오도록 목을 정한다
		NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
		when(repository.countByStatusAndCreatedAtGreaterThanEqual(NotificationStatus.PENDING, SINCE)).thenReturn(2L);
		when(repository.countByStatusAndCreatedAtGreaterThanEqual(NotificationStatus.SENT, SINCE)).thenReturn(148L);
		when(repository.countByStatusAndCreatedAtGreaterThanEqual(NotificationStatus.FAILED, SINCE)).thenReturn(1L);
		Instant latest = Instant.parse("2026-11-07T03:51:12Z");
		when(repository.findLatestSentAtSince(SINCE)).thenReturn(Optional.of(latest));
		NotificationQueryService service = new NotificationQueryService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

		// when
		NotificationSummaryView view = service.getSummary();

		// then
		assertThat(view.windowDays()).isEqualTo(7);
		assertThat(view.pendingCount()).isEqualTo(2L);
		assertThat(view.sentCount()).isEqualTo(148L);
		assertThat(view.failedCount()).isEqualTo(1L);
		assertThat(view.latestSentAt()).isEqualTo(latest);
	}

	@Test
	void 기간_안에_발송이_없으면_latestSentAt은_null이다() {
		// given
		NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
		when(repository.findLatestSentAtSince(SINCE)).thenReturn(Optional.empty());
		NotificationQueryService service = new NotificationQueryService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

		// when
		NotificationSummaryView view = service.getSummary();

		// then
		assertThat(view.latestSentAt()).isNull();
		assertThat(view.sentCount()).isZero();
	}
}
