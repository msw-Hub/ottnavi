package com.ottnavi.collect.job;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ottnavi.support.IntegrationTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Task 045: 수집 전용 비동기 JobOperator가 기본 JobOperator와 섞이지 않고 비동기로 동작하는지 시험한다(Docker 필요) */
class CollectLaunchConfigTest extends IntegrationTestSupport {

    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    private static final LocalDate DAY = LocalDate.of(2026, 4, 20);

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

    @Autowired
    private JobOperator defaultOperator; // 이름이 아니라 타입으로 받는다

    @Autowired
    @Qualifier("collectJobOperator")
    private JobOperator collectOperator;

    @Autowired
    private CollectJobLauncher launcher;

    @Autowired
    private JobRepository jobRepository;

    @BeforeEach
    void 초기화() {
        wireMock.resetAll();
        // 제공처 목록 응답을 1.5초 늦춰서 시작 직후에는 반드시 실행 중이게 한다
        wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/movie")).willReturn(aResponse().withStatus(200)
                .withFixedDelay(1500).withHeader("Content-Type", "application/json").withBody("{\"results\":[]}")));
        wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/tv")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"results\":[]}")));
        wireMock.stubFor(get(urlPathEqualTo("/3/discover/movie")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"page\":1,\"results\":[],\"total_pages\":1,\"total_results\":0}")));
        wireMock.stubFor(get(urlPathEqualTo("/3/discover/tv")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"page\":1,\"results\":[],\"total_pages\":1,\"total_results\":0}")));
    }

    @Test
    void 타입으로_주입한_JobOperator는_수집_전용_비동기_operator가_아니다() {
        // when, then
        assertThat(defaultOperator).isNotNull();
        assertThat(collectOperator).isNotNull();
        assertThat(defaultOperator).isNotSameAs(collectOperator);
    }

    @Test
    void 수집_전용_operator는_시작_직후_끝나지_않은_실행을_돌려준다() throws Exception {
        // given, when
        long executionId = launcher.start(DAY, CollectTier.ALL);
        JobExecution justAfterStart = jobRepository.getJobExecution(executionId);

        // then
        assertThat(justAfterStart.getStatus().isRunning()).isTrue();
        assertThat(justAfterStart.getEndTime()).isNull();

        // 테스트가 끝나기 전에 실행이 끝나도록 기다린다
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            JobExecution execution = jobRepository.getJobExecution(executionId);
            if (!execution.getStatus().isRunning() && execution.getEndTime() != null) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("수집이 60초 안에 끝나지 않았다");
    }
}
