package com.ottnavi.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.ottnavi.notification.domain.entity.NotificationOutbox;
import com.ottnavi.notification.domain.enums.NotificationType;
import com.ottnavi.notification.domain.repository.NotificationOutboxRepository;
import com.ottnavi.notification.service.model.ClaimedNotification;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 알림 발송 3단계를 실제 PostgreSQL과 가짜 SMTP 서버(GreenMail)로 확인한다.
 * 막으려는 사고: 성공했는데 SENT가 안 되는 일, 실패한 알림이 영원히 재시도되거나 한 번에 포기되는 일,
 * 탈퇴한 사용자에게 메일이 나가는 일, 최대 시도에 닿은 행이 계속 발송되는 일.
 */
@ExtendWith(OutputCaptureExtension.class)
class NotificationDispatcherTest extends IntegrationTestSupport {

    @RegisterExtension
    GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    private NotificationDispatcher dispatcher;
    @Autowired
    private NotificationClaimService claimService;
    @Autowired
    private NotificationOutboxRepository outboxRepository;
    @Autowired
    private NotificationProperties properties;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Long userId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM notification_outbox");
        userId = saveUser("dispatch-test@example.com", "dispatch-sub");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM notification_outbox");
        jdbc.update("DELETE FROM users WHERE oauth_subject LIKE 'dispatch-%'");
    }

    private Long saveUser(String email, String subject) {
        return userRepository.save(User.create(email, "발송테스트", OAuthProvider.GOOGLE, subject)).getId();
    }

    private Long insertDue(Long ownerId, String dedupeKey) {
        NotificationOutbox outbox = NotificationOutbox.create(ownerId, NotificationType.MONTHLY, dedupeKey,
                "{\"userName\":\"테스터\",\"summary\":\"11월에는 넷플릭스를 구독하세요\"}", Instant.now().minusSeconds(60));
        return outboxRepository.saveAndFlush(outbox).getId();
    }

    /** 다음 시도 시각을 과거로 당겨, 임대·백오프를 기다리지 않고 바로 다시 집히게 한다 */
    private void makeDueNow(Long id) {
        jdbc.update("UPDATE notification_outbox SET next_attempt_at = now() - interval '1 minute' WHERE id = ?", id);
    }

    private Map<String, Object> row(Long id) {
        return jdbc.queryForMap("SELECT status, attempt_count, last_error, sent_at, next_attempt_at "
                + "FROM notification_outbox WHERE id = ?", id);
    }

    @Test
    void 정상_발송하면_메일_1통이_가고_SENT로_기록된다() throws Exception {
        // given
        Long id = insertDue(userId, "k-ok");

        // when
        dispatcher.dispatchDue();

        // then
        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getSubject()).startsWith("[OTT내비] 이번 달 구독 플랜");
        assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("dispatch-test@example.com");
        String body = (String) received[0].getContent();
        assertThat(body).contains("확인된 범위").contains("11월에는 넷플릭스를 구독하세요");
        Map<String, Object> saved = row(id);
        assertThat(saved.get("status")).isEqualTo("SENT");
        assertThat(saved.get("attempt_count")).isEqualTo(1);
        assertThat(saved.get("sent_at")).isNotNull();
    }

    @Test
    void 발송이_계속_실패하면_5회째에_FAILED가_되고_메일은_가지_않는다() throws Exception {
        // given: 형식이 잘못된 수신 주소라 매번 발송에 실패한다
        Long badUserId = saveUser("not-an-email@@", "dispatch-bad");
        Long id = insertDue(badUserId, "k-fail");

        for (int attempt = 1; attempt <= 5; attempt++) {
            // when
            makeDueNow(id);
            dispatcher.dispatchDue();

            // then
            Map<String, Object> saved = row(id);
            assertThat(saved.get("attempt_count")).isEqualTo(attempt);
            assertThat(String.valueOf(saved.get("last_error"))).contains("MailSendException");
            if (attempt < 5) {
                assertThat(saved.get("status")).isEqualTo("PENDING");
            } else {
                assertThat(saved.get("status")).isEqualTo("FAILED");
            }
        }
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void 발송_실패_경고_로그에_근본_원인_클래스가_있고_수신자_주소는_없다(CapturedOutput output) {
        // given: 형식이 잘못된 수신 주소라 발송에 실패한다
        Long badUserId = saveUser("not-an-email@@", "dispatch-bad-log");
        insertDue(badUserId, "k-log");

        // when
        dispatcher.dispatchDue();

        // then
        assertThat(output.getOut()).contains("알림 발송에 실패했다").contains("근본원인=");
        assertThat(output.getOut()).doesNotContain("not-an-email@@");
    }

    @Test
    void 실패하면_정해진_간격만큼_뒤로_미루고_그_전에는_다시_집지_않는다() {
        // given
        Long badUserId = saveUser("not-an-email@@", "dispatch-bad2");
        Long id = insertDue(badUserId, "k-backoff");
        dispatcher.dispatchDue();

        // when: 1회 실패 직후 다시 훑는다
        Instant nextAttemptAt = jdbc.queryForObject("SELECT next_attempt_at FROM notification_outbox WHERE id = ?",
                java.sql.Timestamp.class, id).toInstant();
        dispatcher.dispatchDue();

        // then: 1분(첫 간격) 뒤로 미뤄졌고 시도 횟수는 그대로다
        long secondsFromNow = nextAttemptAt.getEpochSecond() - Instant.now().getEpochSecond();
        assertThat(secondsFromNow).isBetween(50L, 61L);
        assertThat(row(id).get("attempt_count")).isEqualTo(1);
    }

    @Test
    void SMTP_서버에_연결할_수_없으면_실패로_기록하고_재시도_대기가_된다() {
        // given
        Long id = insertDue(userId, "k-smtp-down");
        greenMail.stop();

        // when
        dispatcher.dispatchDue();

        // then
        Map<String, Object> saved = row(id);
        assertThat(saved.get("status")).isEqualTo("PENDING");
        assertThat(saved.get("attempt_count")).isEqualTo(1);
        assertThat(String.valueOf(saved.get("last_error"))).contains("MailSendException");
    }

    @Test
    void 임대_중인_행은_발송하지_않는다() {
        // given: 다른 발송기가 방금 점유한 행
        insertDue(userId, "k-leased");
        claimService.claimDue(Instant.now());

        // when
        int claimed = dispatcher.dispatchDue();

        // then
        assertThat(claimed).isZero();
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void 최대_시도에_닿은_대기_행은_메일을_보내지_않고_FAILED가_된다() {
        // given
        Long id = insertDue(userId, "k-max");
        jdbc.update("UPDATE notification_outbox SET attempt_count = ? WHERE id = ?", properties.maxAttempts(), id);

        // when
        dispatcher.dispatchDue();

        // then
        assertThat(greenMail.getReceivedMessages()).isEmpty();
        assertThat(row(id).get("status")).isEqualTo("FAILED");
    }

    @Test
    void 점유한_뒤_사용자가_탈퇴하면_메일을_보내지_않고_오류도_없다() {
        // given: 점유(1단계)까지 끝난 상태에서 탈퇴(users 삭제 → outbox 행도 CASCADE로 삭제)가 끼어든다
        Long id = insertDue(userId, "k-withdraw");
        List<ClaimedNotification> claimed = claimService.claimDue(Instant.now());
        assertThat(claimed).hasSize(1);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);

        // when
        dispatcher.dispatchAll(claimed);

        // then
        assertThat(greenMail.getReceivedMessages()).isEmpty();
        assertThat(outboxRepository.findById(id)).isEmpty();
    }
}
