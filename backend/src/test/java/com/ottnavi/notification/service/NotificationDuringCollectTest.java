package com.ottnavi.notification.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.ottnavi.collect.job.CollectJobParameters;
import com.ottnavi.collect.job.CollectTier;
import com.ottnavi.notification.domain.entity.NotificationOutbox;
import com.ottnavi.notification.domain.enums.NotificationType;
import com.ottnavi.notification.domain.repository.NotificationOutboxRepository;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.repository.UserRepository;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 수집 Job이 도는 동안에도 알림 발송이 정상 동작하는지 확인한다. 연결 풀은 운영과 같은 5개(application.yml)다.
 * 수집 Job이 TMDB 응답을 기다리며 연결을 쥐고 있어도, 알림 발송(점유·재확인·기록이 각각 짧은 트랜잭션)이 연결을 못 얻어 멈추거나
 * 서로 교착하지 않아야 한다. 풀이 고갈되면 연결 대기(기본 30초) 끝에 발송이 실패해 아래 단언이 깨진다.
 * 한계: 실제 TMDB 응답 지연·수집 대상 규모를 흉내 낸 것이라 운영 부하와 같지는 않다.
 */
class NotificationDuringCollectTest extends IntegrationTestSupport {

    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    private static final int NOTIFICATION_COUNT = 15;

    static {
        wireMock.start();
    }

    @DynamicPropertySource
    static void tmdbProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tmdb.base-url", () -> wireMock.baseUrl() + "/3");
        registry.add("app.tmdb.api-token", () -> "test-token");
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @RegisterExtension
    GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    private JobOperator jobOperator;
    @Autowired
    private Job collectJob;
    @Autowired
    private NotificationDispatcher dispatcher;
    @Autowired
    private NotificationOutboxRepository outboxRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    private Long userId;

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        jdbc.update("DELETE FROM notification_outbox");
        userId = userRepository.save(User.create("during-collect@example.com", "수집중알림", OAuthProvider.GOOGLE,
                "dispatch-during-collect")).getId();

        // 제공처 목록은 비워 두고, discover는 느리게 응답한다(수집 Job이 TMDB를 기다리는 동안 연결을 쥐는 상황)
        String empty = "{\"results\":[]}";
        wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/movie")).willReturn(json(empty)));
        wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/tv")).willReturn(json(empty)));
        String emptyPage = "{\"page\":1,\"results\":[],\"total_pages\":1,\"total_results\":0}";
        wireMock.stubFor(get(urlPathEqualTo("/3/discover/movie")).atPriority(10)
                .willReturn(json(emptyPage).withFixedDelay(150)));
        wireMock.stubFor(get(urlPathEqualTo("/3/discover/tv")).atPriority(10)
                .willReturn(json(emptyPage).withFixedDelay(150)));
        for (int page = 1; page <= 4; page++) {
            String body = "{\"page\":" + page + ",\"results\":[{\"id\":" + (910000 + page) + ",\"title\":\"알림동시 영화 "
                    + page + "\"}],\"total_pages\":4,\"total_results\":4}";
            wireMock.stubFor(get(urlPathEqualTo("/3/discover/movie"))
                    .withQueryParam("with_watch_providers", equalTo("8|1796"))
                    .withQueryParam("page", equalTo(String.valueOf(page)))
                    .willReturn(json(body).withFixedDelay(500)));
        }
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM notification_outbox");
        jdbc.update("DELETE FROM users WHERE oauth_subject = 'dispatch-during-collect'");
    }

    @Test
    void 수집_Job이_도는_동안에도_알림이_모두_발송되고_풀_고갈이_없다() throws Exception {
        // given
        HikariDataSource hikari = (HikariDataSource) dataSource;
        assertThat(hikari.getMaximumPoolSize()).isEqualTo(5);
        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<JobExecution> collect = executor.submit(() -> jobOperator.start(collectJob,
                CollectJobParameters.of(LocalDate.of(2026, 12, 1), CollectTier.ALL)));

        // when: Job이 도는 동안 알림 행을 하나씩 넣으며 발송기를 계속 돌린다
        int inserted = 0;
        int maxWaiting = 0;
        int sentWhileCollecting = 0;
        while (!collect.isDone()) {
            if (inserted < NOTIFICATION_COUNT) {
                insertDue("k-during-" + inserted);
                inserted++;
            }
            dispatcher.dispatchDue();
            if (pool.getThreadsAwaitingConnection() > maxWaiting) {
                maxWaiting = pool.getThreadsAwaitingConnection();
            }
            sentWhileCollecting = greenMail.getReceivedMessages().length;
            Thread.sleep(200);
        }
        JobExecution execution = collect.get(10, TimeUnit.SECONDS);
        executor.shutdown();
        while (inserted < NOTIFICATION_COUNT) {
            insertDue("k-during-" + inserted);
            inserted++;
        }
        dispatcher.dispatchDue();
        dispatcher.dispatchDue();

        // then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(sentWhileCollecting).as("수집 중에 이미 나간 메일 수").isGreaterThan(0);
        assertThat(greenMail.getReceivedMessages()).hasSize(NOTIFICATION_COUNT);
        Integer notSent = jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE status <> 'SENT'",
                Integer.class);
        assertThat(notSent).isZero();
        System.out.println("수집 중 연결을 기다린 스레드 최대 수: " + maxWaiting + ", 수집 중 발송된 메일: " + sentWhileCollecting);
    }

    private void insertDue(String dedupeKey) {
        NotificationOutbox outbox = NotificationOutbox.create(userId, NotificationType.MONTHLY, dedupeKey,
                "{\"userName\":\"테스터\"}", Instant.now().minusSeconds(60));
        outboxRepository.saveAndFlush(outbox);
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
