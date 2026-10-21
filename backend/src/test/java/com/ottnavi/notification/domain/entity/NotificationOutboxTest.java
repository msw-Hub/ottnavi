package com.ottnavi.notification.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.notification.domain.enums.NotificationStatus;
import com.ottnavi.notification.domain.enums.NotificationType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 알림 상태 전이를 DB 없이 확인한다.
 * 막으려는 사고: 실패했는데 영원히 재시도하는 일, 간격이 한 칸씩 어긋나는 일, 최대 횟수 뒤에도 PENDING으로 남는 일.
 */
class NotificationOutboxTest {

    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final List<Duration> DELAYS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2));

    private NotificationOutbox newOutbox() {
        return NotificationOutbox.create(1L, NotificationType.MONTHLY, "MONTHLY:1:2026-11", "{\"a\":1}", NOW);
    }

    @Test
    void 새_알림은_대기_상태이고_시도_횟수가_0이다() {
        // given & when
        NotificationOutbox outbox = newOutbox();

        // then
        assertThat(outbox.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(outbox.getAttemptCount()).isZero();
        assertThat(outbox.getNextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void 점유하면_시도_횟수가_늘고_다음_시도가_임대_시간만큼_미뤄진다() {
        // given
        NotificationOutbox outbox = newOutbox();

        // when
        outbox.claim(NOW, LEASE);

        // then
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(NOW.plus(LEASE));
        assertThat(outbox.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void 실패_1회부터_4회까지는_대기_상태로_두고_정해진_간격만큼_미룬다() {
        // given
        NotificationOutbox outbox = newOutbox();

        for (int attempt = 1; attempt <= 4; attempt++) {
            // when
            outbox.claim(NOW, LEASE);
            outbox.recordFailure("연결 실패", 5, DELAYS, NOW);

            // then
            assertThat(outbox.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(outbox.getNextAttemptAt()).isEqualTo(NOW.plus(DELAYS.get(attempt - 1)));
        }
    }

    @Test
    void 실패_5회째는_FAILED로_끝나고_사유가_남는다() {
        // given
        NotificationOutbox outbox = newOutbox();
        for (int attempt = 1; attempt <= 4; attempt++) {
            outbox.claim(NOW, LEASE);
            outbox.recordFailure("연결 실패", 5, DELAYS, NOW);
        }

        // when
        outbox.claim(NOW, LEASE);
        outbox.recordFailure("연결 실패", 5, DELAYS, NOW);

        // then
        assertThat(outbox.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(outbox.getAttemptCount()).isEqualTo(5);
        assertThat(outbox.getLastError()).isEqualTo("연결 실패");
    }

    @Test
    void 발송_성공을_기록하면_SENT와_발송_시각이_남고_이전_오류는_지워진다() {
        // given
        NotificationOutbox outbox = newOutbox();
        outbox.claim(NOW, LEASE);
        outbox.recordFailure("일시 오류", 5, DELAYS, NOW);
        outbox.claim(NOW, LEASE);

        // when
        outbox.markSent(NOW);

        // then
        assertThat(outbox.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(outbox.getSentAt()).isEqualTo(NOW);
        assertThat(outbox.getLastError()).isNull();
    }

    @Test
    void 포기_처리하면_FAILED와_사유가_남는다() {
        // given
        NotificationOutbox outbox = newOutbox();

        // when
        outbox.markExhausted("최대 시도 도달");

        // then
        assertThat(outbox.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(outbox.getLastError()).isEqualTo("최대 시도 도달");
    }

    @Test
    void 오류_사유는_500자까지만_저장한다() {
        // given
        NotificationOutbox outbox = newOutbox();
        outbox.claim(NOW, LEASE);

        // when
        outbox.recordFailure("가".repeat(800), 5, DELAYS, NOW);

        // then
        assertThat(outbox.getLastError()).hasSize(500);
    }
}
