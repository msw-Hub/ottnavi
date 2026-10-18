package com.ottnavi.collect;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.ottnavi.collect.job.CollectJobParameters;
import com.ottnavi.collect.job.CollectTier;
import com.ottnavi.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Task 077: tier(DAILY/WEEKLY/ALL)별로 수집 Job이 도는 Step과 상세·판정 대상이 달라지는지 DB(Testcontainers)와 가짜 TMDB(WireMock)로 시험한다(Docker 필요).
 * 작품 A(900001)는 찜이 있고, 작품 B(900002)는 찜이 없다. 시작 때 refresh_tier를 찜 기준으로 다시 맞추므로
 * 일부러 A는 WEEKLY, B는 DAILY(예전에 찜이 있다가 사라진 상태)로 시작해 재맞춤까지 함께 확인한다.
 */
class CollectTierJobTest extends IntegrationTestSupport {

    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    private static final AtomicInteger DAY_SEQUENCE = new AtomicInteger(); // 테스트마다 다른 targetDate
    private static final String DISCOVER_MOVIE = "/3/discover/movie";
    private static final String DISCOVER_TV = "/3/discover/tv";
    private static final String EMPTY_PAGE = "{\"page\":1,\"results\":[],\"total_pages\":1,\"total_results\":0}";
    private static final String TEST_EMAIL = "tier-job@test.com";

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
    private JobOperator jobOperator;

    @Autowired
    private Job collectJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long titleAId;
    private long titleBId;

    @BeforeEach
    void 초기화() {
        wireMock.resetAll();
        clearData();
        wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/movie")).willReturn(json("{\"results\":[]}")));
        wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/tv")).willReturn(json("{\"results\":[]}")));
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE)).atPriority(10).willReturn(json(EMPTY_PAGE)));
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_TV)).atPriority(10).willReturn(json(EMPTY_PAGE)));

        // A: 찜이 있지만 WEEKLY로 시작 / B: 찜이 없지만 DAILY로 시작(찜이 사라진 뒤)
        titleAId = insertMovie(900001, "WEEKLY");
        titleBId = insertMovie(900002, "DAILY");
        long userId = jdbcTemplate.queryForObject("INSERT INTO users (email, name, oauth_provider, oauth_subject) "
                + "VALUES (?, '티어시험', 'GOOGLE', 'sub-tier-job') RETURNING id", Long.class, TEST_EMAIL);
        jdbcTemplate.update("INSERT INTO wishlist_item (user_id, watch_unit_id, priority) VALUES (?, ?, 'MUST')",
                userId, unitIdOf(titleAId));
        stubMovie(900001);
        stubMovie(900002);
    }

    @AfterEach
    void 정리() {
        clearData();
    }

    @Test
    void DAILY는_찜한_작품만_상세를_받고_제공처_목록과_후보_발견은_건너뛴다() throws Exception {
        // given: 후보 발견이 불렸다면 새 작품 900003이 생기도록 응답을 준비한다
        stubDiscover(900003);
        stubMovie(900003);

        // when
        JobExecution execution = start(CollectTier.DAILY);

        // then: 도는 Step은 재맞춤 + 상세·판정뿐이다
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(stepNames(execution)).containsExactlyInAnyOrder("refreshTierStep", "movieDetailStep", "tvDetailStep",
                "availabilityStep");
        // then: 찜한 A만 TMDB 상세를 받았고 B와 새 작품은 부르지 않았다
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/900001")));
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/3/movie/900002")));
        wireMock.verify(0, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE)));
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/3/watch/providers/movie")));
        assertThat(count("SELECT count(*) FROM title WHERE tmdb_id = 900003")).isEqualTo(0);
        // then: 제공 상태도 A의 단위에만 생겼다
        assertThat(count("SELECT count(*) FROM availability WHERE watch_unit_id = " + unitIdOf(titleAId))).isEqualTo(7);
        assertThat(count("SELECT count(*) FROM availability WHERE watch_unit_id = " + unitIdOf(titleBId))).isEqualTo(0);
        // then: 재맞춤으로 A는 DAILY, B는 WEEKLY가 됐다
        assertThat(refreshTierOf(titleAId)).isEqualTo("DAILY");
        assertThat(refreshTierOf(titleBId)).isEqualTo("WEEKLY");
    }

    @Test
    void WEEKLY는_후보를_발견하고_찜하지_않은_작품만_상세를_받는다() throws Exception {
        // given: 후보 발견이 새 작품 900003을 돌려준다
        stubDiscover(900003);
        stubMovie(900003);

        // when
        JobExecution execution = start(CollectTier.WEEKLY);

        // then: 전체 Step 흐름(재맞춤 + 제공처 목록 + 후보 2 + 상세 2 + 판정)을 돌았다
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(stepNames(execution)).containsExactlyInAnyOrder("refreshTierStep", "providerListStep",
                "movieCandidateStep", "tvCandidateStep", "movieDetailStep", "tvDetailStep", "availabilityStep");
        // then: 찜한 A는 건너뛰고, 찜 없는 B와 새로 들어온 작품(WEEKLY로 시작)만 상세를 받았다
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/3/movie/900001")));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/900002")));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/900003")));
        wireMock.verify(7, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE)));
        assertThat(refreshTierOf(titleAId)).isEqualTo("DAILY");
        assertThat(refreshTierOf(titleBId)).isEqualTo("WEEKLY");
    }

    @Test
    void ALL은_찜_여부와_상관없이_모든_작품의_상세를_받는다() throws Exception {
        // given
        stubDiscover(900003);
        stubMovie(900003);

        // when
        JobExecution execution = start(CollectTier.ALL);

        // then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/900001")));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/900002")));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/900003")));
        assertThat(stepNames(execution)).contains("refreshTierStep", "providerListStep");
    }

    @Test
    void 같은_날짜의_DAILY와_ALL은_서로_다른_실행이라_둘_다_끝까지_돈다() throws Exception {
        // given
        LocalDate day = nextDay();

        // when
        JobExecution daily = jobOperator.start(collectJob, CollectJobParameters.of(day, CollectTier.DAILY));
        JobExecution all = jobOperator.start(collectJob, CollectJobParameters.of(day, CollectTier.ALL));

        // then
        assertThat(daily.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(all.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(all.getJobInstance().getInstanceId()).isNotEqualTo(daily.getJobInstance().getInstanceId());
    }

    // 도우미

    private JobExecution start(CollectTier tier) throws Exception {
        return jobOperator.start(collectJob, CollectJobParameters.of(nextDay(), tier));
    }

    private static LocalDate nextDay() {
        return LocalDate.of(2026, 12, 1).plusDays(DAY_SEQUENCE.incrementAndGet());
    }

    private List<String> stepNames(JobExecution execution) {
        return jdbcTemplate.queryForList("SELECT STEP_NAME FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID = ?",
                String.class, execution.getId());
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private String refreshTierOf(long titleId) {
        return jdbcTemplate.queryForObject("SELECT refresh_tier FROM title WHERE id = ?", String.class, titleId);
    }

    private long unitIdOf(long titleId) {
        return jdbcTemplate.queryForObject("SELECT id FROM watch_unit WHERE title_id = ?", Long.class, titleId);
    }

    /** 영화 작품 한 줄과 시청 단위 한 줄을 직접 넣는다. refresh_tier는 시험이 정한 시작 값이다 */
    private long insertMovie(int tmdbId, String refreshTier) {
        long titleId = jdbcTemplate.queryForObject("INSERT INTO title (media_type, tmdb_id, title, refresh_tier, fetched_at) "
                + "VALUES ('MOVIE', ?, ?, ?, now()) RETURNING id", Long.class, tmdbId, "후보 " + tmdbId, refreshTier);
        jdbcTemplate.update("INSERT INTO watch_unit (title_id, unit_type, total_runtime_min, fetched_at) "
                + "VALUES (?, 'MOVIE', 100, now())", titleId);
        return titleId;
    }

    /** FK 순서(자식 → 부모)대로 이 시험이 쓰는 데이터를 비운다 */
    private void clearData() {
        jdbcTemplate.update("DELETE FROM wishlist_item");
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", TEST_EMAIL);
        for (String table : List.of("availability_change", "availability", "watch_unit", "title_cast", "title_genre",
                "title", "genre")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    /** Netflix(8|1796) discover 첫 쪽이 새 영화 한 편을 돌려준다 */
    private static void stubDiscover(int tmdbId) {
        String body = "{\"page\":1,\"results\":[{\"id\":" + tmdbId + ",\"title\":\"후보 " + tmdbId
                + "\"}],\"total_pages\":1,\"total_results\":1}";
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE))
                .withQueryParam("with_watch_providers", equalTo("8|1796"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(json(body)));
    }

    /** 영화 상세(한국어)와 제공처(Netflix 구독)를 등록한다 */
    private static void stubMovie(int id) {
        String detail = "{\"id\":" + id + ",\"title\":\"후보 " + id + "\",\"original_title\":\"Original " + id
                + "\",\"overview\":\"줄거리\",\"release_date\":\"2024-05-01\",\"runtime\":100,\"genres\":[]"
                + ",\"original_language\":\"ko\",\"popularity\":1.5,\"vote_average\":7.0,\"credits\":{\"cast\":[]}}";
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/" + id)).withQueryParam("language", equalTo("ko-KR"))
                .willReturn(json(detail)));
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/" + id + "/watch/providers")).willReturn(json(
                "{\"id\":1,\"results\":{\"KR\":{\"link\":\"https://example.test\",\"flatrate\":"
                        + "[{\"provider_id\":8,\"provider_name\":\"Netflix\"}],\"rent\":[]}}}")));
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
