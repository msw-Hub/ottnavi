package com.ottnavi.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.notification.domain.entity.NotificationOutbox;
import com.ottnavi.notification.domain.enums.NotificationStatus;
import com.ottnavi.notification.domain.enums.NotificationType;
import com.ottnavi.notification.domain.repository.NotificationOutboxRepository;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 결과 기록이 점유 세대를 확인하는지 실제 PostgreSQL로 확인한다.
 * 막으려는 사고: 임대가 끝나 다른 발송기가 다시 점유한 뒤 앞선 발송기가 늦게 기록해 SENT가 FAILED로 덮이거나 그 반대가 되는 일.
 */
class NotificationResultRecorderTest extends IntegrationTestSupport {

    @Autowired
    private NotificationResultRecorder recorder;
    @Autowired
    private NotificationOutboxRepository outboxRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Long userId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM notification_outbox");
        userId = userRepository.save(User.create("recorder-test@example.com", "기록테스트", OAuthProvider.GOOGLE,
                "dispatch-recorder")).getId();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM notification_outbox");
        jdbc.update("DELETE FROM users WHERE oauth_subject = 'dispatch-recorder'");
    }

    /** 시도 횟수만큼 점유된 대기 행을 만든다 */
    private Long insertClaimed(int attemptCount) {
        NotificationOutbox outbox = NotificationOutbox.create(userId, NotificationType.MONTHLY, "k-recorder",
                "{\"userName\":\"테스터\"}", Instant.now().minusSeconds(60));
        Long id = outboxRepository.saveAndFlush(outbox).getId();
        jdbc.update("UPDATE notification_outbox SET attempt_count = ? WHERE id = ?", attemptCount, id);
        return id;
    }

    private Map<String, Object> row(Long id) {
        return jdbc.queryForMap("SELECT status, attempt_count, last_error FROM notification_outbox WHERE id = ?", id);
    }

    @Test
    void 내_점유가_맞으면_성공이_기록된다() {
        // given
        Long id = insertClaimed(1);

        // when
        boolean recorded = recorder.recordSent(id, 1, Instant.now());

        // then
        assertThat(recorded).isTrue();
        assertThat(row(id).get("status")).isEqualTo("SENT");
    }

    @Test
    void 다른_발송기가_다시_점유했으면_늦은_성공_기록은_상태를_바꾸지_않는다() {
        // given: 1회째 점유한 발송기가 느린 사이 임대가 끝나 다른 발송기가 2회째로 다시 점유했다
        Long id = insertClaimed(2);

        // when
        boolean recorded = recorder.recordSent(id, 1, Instant.now());

        // then
        assertThat(recorded).isFalse();
        assertThat(row(id).get("status")).isEqualTo("PENDING");
    }

    @Test
    void 이미_SENT인_행에_늦은_실패_기록이_와도_덮어쓰지_않는다() {
        // given: 최대 시도(5회)째 점유한 발송기가 SENT로 끝냈는데 4회째 발송기가 늦게 실패를 기록하려 한다
        Long id = insertClaimed(5);
        recorder.recordSent(id, 5, Instant.now());

        // when
        Optional<NotificationStatus> status = recorder.recordFailure(id, 4, "늦은 실패", Instant.now());
        Optional<NotificationStatus> sameGenerationLate = recorder.recordFailure(id, 5, "중복 실패", Instant.now());

        // then
        assertThat(status).isEmpty();
        assertThat(sameGenerationLate).isEmpty();
        assertThat(row(id).get("status")).isEqualTo("SENT");
        assertThat(row(id).get("last_error")).isNull();
    }

    @Test
    void 내_점유가_맞으면_실패가_기록되고_재시도_대기가_된다() {
        // given
        Long id = insertClaimed(1);

        // when
        Optional<NotificationStatus> status = recorder.recordFailure(id, 1, "연결 실패", Instant.now());

        // then
        assertThat(status).contains(NotificationStatus.PENDING);
        assertThat(String.valueOf(row(id).get("last_error"))).contains("연결 실패");
    }
}
