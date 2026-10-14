package com.ottnavi.collect.job;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.ottnavi.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Task 045: 수집 실행 이력 조회를 실제 Batch 메타데이터(Testcontainers)와 가짜 TMDB(WireMock)로 시험한다(Docker 필요).
 * 수집은 비동기 launcher로 시작하고, 시험이 끝나기 전에 실행이 끝날 때까지 기다린다.
 */
class CollectExecutionQueryTest extends IntegrationTestSupport {

    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    private static final String EMPTY_PAGE = "{\"page\":1,\"results\":[],\"total_pages\":1,\"total_results\":0}";
    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    static {
        wireMock.start();
    }

    @DynamicPropertySource
    static void tmdbProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tmdb.base-url", () -> wireMock.baseUrl() + "/3");
        registry.add("app.tmdb.api-token", () -> "test-token");
        registry.add("app.tmdb.retry.initial-backoff", () -> "10ms");
        registry.add("app.tmdb.retry.max-backoff", () -> "40ms");
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Autowired
    private CollectJobLauncher launcher;

    @Autowired
    private CollectExecutionQuery query;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 초기화() {
        wireMock.resetAll();
        // Batch 메타데이터를 자식 → 부모 순서로 비운다(이 시험이 날짜별 실행 수를 세기 때문)
        for (String table : List.of("BATCH_STEP_EXECUTION_CONTEXT", "BATCH_STEP_EXECUTION",
                "BATCH_JOB_EXECUTION_CONTEXT", "BATCH_JOB_EXECUTION_PARAMS", "BATCH_JOB_EXECUTION",
                "BATCH_JOB_INSTANCE")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        for (String table : List.of("availability_change", "availability", "watch_unit", "title_cast", "title_genre",
                "title", "genre")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        jdbcTemplate.update("DELETE FROM tmdb_provider WHERE mapping_state <> 'MAPPED'");
        stubSuccessfulCollection();
    }

    @Test
    void 완료된_수집의_findById는_기준일_tier_최신_여부를_채운다() throws Exception {
        // given
        long executionId = launcher.start(DAY, CollectTier.ALL);
        waitUntilFinished(executionId);

        // when
        CollectExecutionRecord record = query.findById(executionId).orElseThrow();

        // then
        assertThat(record.executionId()).isEqualTo(executionId);
        assertThat(record.targetDate()).isEqualTo(DAY);
        assertThat(record.tier()).isEqualTo(CollectTier.ALL);
        assertThat(record.batchStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(record.isLatestOfInstance()).isTrue();
        assertThat(record.createTime()).isNotNull();
        assertThat(record.startTime()).isNotNull();
        assertThat(record.endTime()).isNotNull();
    }

    @Test
    void 재시작하면_findLatest는_가장_최근_실행이고_이전_실행은_최신이_아니다() throws Exception {
        // given: 제공처 목록이 500이라 처음 실행은 실패한다. 그 뒤 TMDB가 살아나 재시작한다
        wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/movie")).atPriority(1)
                .willReturn(aResponse().withStatus(500)));
        long failedId = launcher.start(DAY, CollectTier.ALL);
        waitUntilFinished(failedId);
        wireMock.resetAll();
        stubSuccessfulCollection();
        long restartedId = launcher.restart(failedId);
        waitUntilFinished(restartedId);

        // when
        CollectExecutionRecord latest = query.findLatest(DAY, CollectTier.ALL).orElseThrow();
        CollectExecutionRecord first = query.findById(failedId).orElseThrow();

        // then
        assertThat(restartedId).isGreaterThan(failedId);
        assertThat(latest.executionId()).isEqualTo(restartedId);
        assertThat(latest.batchStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(latest.isLatestOfInstance()).isTrue();
        assertThat(first.batchStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(first.isLatestOfInstance()).isFalse();
        assertThat(first.instanceId()).isEqualTo(latest.instanceId());
    }

    @Test
    void findPage는_기준일_내림차순으로_잘라서_돌려준다() throws Exception {
        // given
        long older = launcher.start(DAY, CollectTier.ALL);
        waitUntilFinished(older);
        long newer = launcher.start(DAY.plusDays(1), CollectTier.ALL);
        waitUntilFinished(newer);

        // when
        List<CollectExecutionRecord> firstPage = query.findPage(0, 1);
        List<CollectExecutionRecord> secondPage = query.findPage(1, 1);

        // then
        assertThat(query.countAll()).isEqualTo(2);
        assertThat(firstPage).hasSize(1);
        assertThat(firstPage.get(0).executionId()).isEqualTo(newer);
        assertThat(secondPage).hasSize(1);
        assertThat(secondPage.get(0).executionId()).isEqualTo(older);
    }

    @Test
    void sumDetailWriteCounts는_영화와_드라마_상세_Step의_WRITE_COUNT_합이다() throws Exception {
        // given: 영화 1편, 드라마 1편이 저장된다
        long executionId = launcher.start(DAY, CollectTier.ALL);
        waitUntilFinished(executionId);

        // when
        Map<Long, Integer> sums = query.sumDetailWriteCounts(List.of(executionId));

        // then
        assertThat(sums).containsEntry(executionId, 2);
    }

    @Test
    void 빈_목록으로_조회하면_쿼리_없이_빈_Map을_돌려준다() {
        // when, then
        assertThat(query.sumDetailWriteCounts(List.of())).isEmpty();
        assertThat(query.findFailedStepExitMessages(List.of())).isEmpty();
    }

    @Test
    void 다른_Job의_실행은_이력과_findById에_섞이지_않는다() {
        // given: 수집이 아닌 Job의 실행 한 건을 직접 넣는다
        jdbcTemplate.update("INSERT INTO BATCH_JOB_INSTANCE (JOB_INSTANCE_ID, VERSION, JOB_NAME, JOB_KEY) "
                + "VALUES (990001, 0, 'otherJob', 'k')");
        jdbcTemplate.update("INSERT INTO BATCH_JOB_EXECUTION (JOB_EXECUTION_ID, VERSION, JOB_INSTANCE_ID, CREATE_TIME, "
                + "STATUS) VALUES (990002, 0, 990001, now(), 'COMPLETED')");
        jdbcTemplate.update("INSERT INTO BATCH_JOB_EXECUTION_PARAMS (JOB_EXECUTION_ID, PARAMETER_NAME, "
                + "PARAMETER_TYPE, PARAMETER_VALUE, IDENTIFYING) VALUES (990002, 'targetDate', 'java.time.LocalDate', "
                + "'2026-03-10', 'Y')");
        jdbcTemplate.update("INSERT INTO BATCH_JOB_EXECUTION_PARAMS (JOB_EXECUTION_ID, PARAMETER_NAME, "
                + "PARAMETER_TYPE, PARAMETER_VALUE, IDENTIFYING) VALUES (990002, 'tier', 'java.lang.String', 'ALL', 'Y')");

        // when, then
        assertThat(query.findById(990002)).isEmpty();
        assertThat(query.countAll()).isZero();
        assertThat(query.findPage(0, 10)).isEmpty();
        assertThat(query.findLatest(DAY, CollectTier.ALL)).isEmpty();
    }

    @Test
    void 실패한_실행의_종료_메시지는_Job에_예외_클래스와_메시지가_들어_있다() throws Exception {
        // given
        wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/movie")).atPriority(1)
                .willReturn(aResponse().withStatus(500)));
        long executionId = launcher.start(DAY, CollectTier.ALL);
        waitUntilFinished(executionId);

        // when
        CollectExecutionRecord record = query.findById(executionId).orElseThrow();
        Map<Long, String> stepMessages = query.findFailedStepExitMessages(List.of(executionId));
        String reason = CollectExecutionRules.failureReasonOf(record, CollectExecutionStatus.FAILED);

        // then
        assertThat(record.batchStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(record.exitMessage()).isNotBlank();
        assertThat(record.exitMessage().split("\\R")[0]).contains("Exception");
        assertThat(stepMessages).containsKey(executionId);
        assertThat(reason).isNotBlank().doesNotContain("org.springframework").contains(":");
    }

    @Test
    void 복구하면_중단된_실행은_FAILED와_RECOVERED_종료_코드로_바뀐다() throws Exception {
        // given: 서버가 죽어 STARTED로 남은 실행을 흉내 낸다(실행 한 건을 끝낸 뒤 상태만 되돌린다)
        long executionId = launcher.start(DAY, CollectTier.ALL);
        waitUntilFinished(executionId);
        jdbcTemplate.update("UPDATE BATCH_JOB_EXECUTION SET STATUS = 'STARTED', EXIT_CODE = 'EXECUTING', "
                + "END_TIME = NULL WHERE JOB_EXECUTION_ID = ?", executionId);

        // when
        launcher.recover(executionId);

        // then
        CollectExecutionRecord record = query.findById(executionId).orElseThrow();
        assertThat(record.batchStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(record.exitCode()).isEqualTo(CollectJobLauncher.RECOVERED_EXIT_CODE);
        assertThat(CollectExecutionRules.failureReasonOf(record, CollectExecutionStatus.FAILED))
                .isEqualTo(CollectJobLauncher.RECOVERED_REASON);
        assertThat(query.findUnfinished()).isEmpty();
    }

    // 도우미

    /** 영화 1편(900001)과 드라마 1편(800001, 시즌 1개)이 수집되는 정상 응답을 등록한다 */
    private static void stubSuccessfulCollection() {
        wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/movie")).willReturn(json("{\"results\":[]}")));
        wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/tv")).willReturn(json("{\"results\":[]}")));
        wireMock.stubFor(get(urlPathEqualTo("/3/discover/movie")).atPriority(10).willReturn(json(EMPTY_PAGE)));
        wireMock.stubFor(get(urlPathEqualTo("/3/discover/tv")).atPriority(10).willReturn(json(EMPTY_PAGE)));
        wireMock.stubFor(get(urlPathEqualTo("/3/discover/movie"))
                .withQueryParam("with_watch_providers", equalTo("8|1796")).withQueryParam("page", equalTo("1"))
                .willReturn(json("{\"page\":1,\"results\":[{\"id\":900001,\"title\":\"영화\"}],"
                        + "\"total_pages\":1,\"total_results\":1}")));
        wireMock.stubFor(get(urlPathEqualTo("/3/discover/tv"))
                .withQueryParam("with_watch_providers", equalTo("8|1796")).withQueryParam("page", equalTo("1"))
                .willReturn(json("{\"page\":1,\"results\":[{\"id\":800001,\"name\":\"드라마\"}],"
                        + "\"total_pages\":1,\"total_results\":1}")));
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/900001")).withQueryParam("language", equalTo("ko-KR"))
                .willReturn(json("{\"id\":900001,\"title\":\"영화\",\"original_title\":\"Movie\",\"overview\":\"줄거리\","
                        + "\"release_date\":\"2024-05-01\",\"runtime\":100,\"genres\":[],\"original_language\":\"ko\","
                        + "\"popularity\":1.5,\"vote_average\":7.0,\"credits\":{\"cast\":[]}}")));
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/800001")).withQueryParam("language", equalTo("ko-KR"))
                .willReturn(json("{\"id\":800001,\"name\":\"드라마\",\"original_name\":\"Show\",\"overview\":\"줄거리\","
                        + "\"first_air_date\":\"2023-03-01\",\"status\":\"Ended\",\"genres\":[],"
                        + "\"seasons\":[{\"id\":810001,\"season_number\":1,\"name\":\"시즌 1\","
                        + "\"air_date\":\"2023-03-01\",\"episode_count\":1}],\"credits\":{\"cast\":[]}}")));
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/800001/season/1")).withQueryParam("language", equalTo("ko-KR"))
                .willReturn(json("{\"id\":810001,\"season_number\":1,\"name\":\"시즌 1\",\"air_date\":\"2023-03-01\","
                        + "\"episodes\":[{\"episode_number\":1,\"air_date\":\"2023-03-01\",\"runtime\":50}]}")));
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/900001/watch/providers"))
                .willReturn(json("{\"id\":1,\"results\":{}}")));
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/800001/season/1/watch/providers"))
                .willReturn(json("{\"id\":1,\"results\":{}}")));
    }

    /** 비동기로 시작한 실행이 끝날 때까지 기다린다(최대 60초) */
    private void waitUntilFinished(long executionId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            JobExecution execution = jobRepository.getJobExecution(executionId);
            if (execution != null && !execution.getStatus().isRunning() && execution.getEndTime() != null) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("수집이 60초 안에 끝나지 않았다: " + executionId);
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
