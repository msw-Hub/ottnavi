package com.ottnavi.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.ottnavi.notification.domain.entity.NotificationOutbox;
import com.ottnavi.notification.domain.enums.NotificationType;
import com.ottnavi.notification.domain.repository.NotificationOutboxRepository;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * 스케줄러가 실제로 알림을 집어 보내고 ShedLock 락 표에 기록을 남기는지 확인한다.
 * 스케줄러를 켠 컨텍스트가 필요해 이 클래스만 별도 컨텍스트로 뜬다(공통 베이스는 스케줄러를 끈다).
 * 막으려는 사고: @EnableSchedulerLock·락 표 설정 오류로 스케줄러가 한 번도 안 돌거나 기동이 실패하는 일.
 */
@TestPropertySource(properties = {"app.notification.scheduler-enabled=true", "app.notification.poll-interval-ms=300"})
class NotificationSendSchedulerTest extends IntegrationTestSupport {

    @RegisterExtension
    GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    private NotificationOutboxRepository outboxRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired(required = false)
    private NotificationSendScheduler scheduler;

    private Long userId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM notification_outbox");
        userId = userRepository.save(User.create("scheduler-test@example.com", "스케줄러", OAuthProvider.GOOGLE,
                "dispatch-scheduler")).getId();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM notification_outbox");
        jdbc.update("DELETE FROM users WHERE oauth_subject = 'dispatch-scheduler'");
    }

    @Test
    void 스케줄러가_켜져_있으면_대기_알림을_스스로_발송하고_락_기록을_남긴다() throws Exception {
        // given: 행을 먼저 저장한 뒤 락을 푼다. 순서가 반대면 그 사이 300ms 주기 실행이 빈 표를 보고 끝나며 30초 락을 다시 건다
        assertThat(scheduler).isNotNull();
        NotificationOutbox outbox = NotificationOutbox.create(userId, NotificationType.MONTHLY, "k-scheduler",
                "{\"userName\":\"테스터\"}", Instant.now().minusSeconds(60));
        Long id = outboxRepository.saveAndFlush(outbox).getId();
        jdbc.update("UPDATE shedlock SET lock_until = lock_until - interval '1 hour'");

        // when: 스케줄러가 깨어나 보낼 때까지 기다린다
        String status = "PENDING";
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            status = jdbc.queryForObject("SELECT status FROM notification_outbox WHERE id = ?", String.class, id);
            if (status.equals("SENT")) {
                break;
            }
            Thread.sleep(200);
        }

        // then
        assertThat(status).isEqualTo("SENT");
        assertThat(greenMail.getReceivedMessages()).hasSize(1);
        Integer lockRows = jdbc.queryForObject("SELECT count(*) FROM shedlock WHERE name = 'notificationSend'",
                Integer.class);
        assertThat(lockRows).isEqualTo(1);
    }
}
