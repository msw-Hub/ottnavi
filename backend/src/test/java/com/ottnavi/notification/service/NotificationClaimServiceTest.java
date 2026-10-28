package com.ottnavi.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.notification.domain.entity.NotificationOutbox;
import com.ottnavi.notification.domain.enums.NotificationType;
import com.ottnavi.notification.domain.repository.NotificationOutboxRepository;
import com.ottnavi.notification.service.model.ClaimedNotification;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 점유(1단계)를 실제 PostgreSQL로 확인한다.
 * 막으려는 사고: 발송기 둘이 같은 행을 점유해 메일이 두 번 나가는 일, 락을 기다리며 발송이 멈추는 일(SKIP LOCKED가 아닌 경우),
 * 최대 시도에 닿은 행이 계속 발송되는 일, JSON 재료가 JSONB로 저장되지 않는 일.
 */
@ExtendWith(OutputCaptureExtension.class)
class NotificationClaimServiceTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

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
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private LoggingSystem loggingSystem;

    private Long userId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM notification_outbox");
        User user = userRepository.save(User.create("claim-test@example.com", "점유테스트", OAuthProvider.GOOGLE, "claim-sub"));
        userId = user.getId();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM notification_outbox");
        userRepository.deleteById(userId);
    }

    private Long insert(String dedupeKey, Instant nextAttemptAt, int attemptCount) {
        NotificationOutbox outbox = NotificationOutbox.create(userId, NotificationType.MONTHLY, dedupeKey,
                "{\"planName\":\"테스트 플랜\"}", nextAttemptAt);
        Long id = outboxRepository.saveAndFlush(outbox).getId();
        if (attemptCount > 0) {
            jdbc.update("UPDATE notification_outbox SET attempt_count = ? WHERE id = ?", attemptCount, id);
        }
        return id;
    }

    @Test
    void 발송_시각이_된_행만_점유하고_시도_횟수와_임대_시각을_기록한다() {
        // given
        Long dueId = insert("k-due", NOW.minusSeconds(10), 0);
        insert("k-future", NOW.plusSeconds(3600), 0);

        // when
        List<ClaimedNotification> claimed = claimService.claimDue(NOW);

        // then
        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).id()).isEqualTo(dueId);
        assertThat(claimed.get(0).attemptCount()).isEqualTo(1);
        NotificationOutbox saved = outboxRepository.findById(dueId).orElseThrow();
        assertThat(saved.getAttemptCount()).isEqualTo(1);
        assertThat(saved.getNextAttemptAt()).isEqualTo(NOW.plus(properties.lease()));
    }

    @Test
    void 임대_중인_행은_다시_점유되지_않는다() {
        // given
        insert("k-lease", NOW.minusSeconds(10), 0);
        claimService.claimDue(NOW);

        // when
        List<ClaimedNotification> second = claimService.claimDue(NOW.plusSeconds(60));

        // then
        assertThat(second).isEmpty();
    }

    @Test
    void 최대_시도에_닿은_대기_행은_발송하지_않고_FAILED로_끝낸다() {
        // given
        Long id = insert("k-exhausted", NOW.minusSeconds(10), properties.maxAttempts());

        // when
        List<ClaimedNotification> claimed = claimService.claimDue(NOW);

        // then
        assertThat(claimed).isEmpty();
        String status = jdbc.queryForObject("SELECT status FROM notification_outbox WHERE id = ?", String.class, id);
        String lastError = jdbc.queryForObject("SELECT last_error FROM notification_outbox WHERE id = ?", String.class, id);
        assertThat(status).isEqualTo("FAILED");
        assertThat(lastError).contains("최대 시도 도달");
    }

    @Test
    void payload는_JSONB로_저장되어_키로_조회된다() {
        // given
        Long id = insert("k-json", NOW.minusSeconds(10), 0);

        // when
        String planName = jdbc.queryForObject(
                "SELECT payload->>'planName' FROM notification_outbox WHERE id = ?", String.class, id);
        String jsonType = jdbc.queryForObject(
                "SELECT jsonb_typeof(payload) FROM notification_outbox WHERE id = ?", String.class, id);

        // then
        assertThat(planName).isEqualTo("테스트 플랜");
        assertThat(jsonType).isEqualTo("object");
    }

    @Test
    void 발송기_둘이_동시에_점유해도_같은_행을_받지_않는다() throws Exception {
        // given: 행 6개. A가 3개를 잠근 채 트랜잭션을 열어 둔다
        for (int i = 0; i < 6; i++) {
            insert("k-concurrent-" + i, NOW.minusSeconds(10), 0);
        }
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        Future<List<Long>> threadA = executor.submit(() -> {
            return template.execute(status -> {
                List<NotificationOutbox> rows = outboxRepository.findDueForUpdate(NOW, Limit.of(3));
                List<Long> ids = new ArrayList<>();
                for (NotificationOutbox row : rows) {
                    ids.add(row.getId());
                }
                locked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ids;
            });
        });
        assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();

        // when: B(이 스레드)가 A의 잠금을 기다리지 않고 나머지를 점유한다
        List<ClaimedNotification> claimedByB = claimService.claimDue(NOW);
        release.countDown();
        List<Long> idsA = threadA.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        Set<Long> idsB = new HashSet<>();
        for (ClaimedNotification claimed : claimedByB) {
            idsB.add(claimed.id());
        }
        assertThat(idsA).hasSize(3);
        assertThat(idsB).hasSize(3);
        assertThat(idsB).doesNotContainAnyElementsOf(idsA);
    }

    @Test
    void 점유_조회_SQL은_SKIP_LOCKED로_나간다(CapturedOutput output) {
        // given: 실제로 나가는 SQL을 보려고 Hibernate SQL 로그를 잠시 DEBUG로 올린다(컨텍스트는 새로 띄우지 않는다)
        insert("k-sql", NOW.minusSeconds(10), 0);
        loggingSystem.setLogLevel("org.hibernate.SQL", LogLevel.DEBUG);
        try {
            // when
            claimService.claimDue(NOW);
        } finally {
            loggingSystem.setLogLevel("org.hibernate.SQL", null);
        }

        // then
        assertThat(output.getAll().toLowerCase()).contains("skip locked");
    }
}
