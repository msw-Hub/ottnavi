package com.ottnavi.collect;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.enums.RefreshTier;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.provider.domain.entity.TmdbProvider;
import com.ottnavi.provider.domain.enums.MappingState;
import com.ottnavi.provider.domain.repository.TmdbProviderRepository;
import com.ottnavi.support.IntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Task 043: 수집 Job의 후보 모으기 Step을 DB(Testcontainers)와 가짜 TMDB(WireMock)로 끝까지 시험한다(Docker 필요).
 * 서비스 7곳의 TMDB 제공처 번호는 V2 시드(Netflix 8|1796, Disney 337, TVING 1883, Watcha 97, wavve 356, 쿠팡 1881, Apple 350)를 쓴다.
 */
class CollectJobTest extends IntegrationTestSupport {

    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    private static final AtomicInteger DAY_SEQUENCE = new AtomicInteger(); // 테스트마다 다른 targetDate로 Job 실행이 겹치지 않게 한다
    private static final String DISCOVER_MOVIE = "/3/discover/movie";
    private static final String DISCOVER_TV = "/3/discover/tv";
    private static final String EMPTY_PAGE = "{\"page\":1,\"results\":[],\"total_pages\":1,\"total_results\":0}";

    static {
        wireMock.start();
    }

    @DynamicPropertySource
    static void tmdbProperties(DynamicPropertyRegistry registry) {
        registry.add("app.tmdb.base-url", () -> wireMock.baseUrl() + "/3");   // 실제 TMDB 대신 WireMock
        registry.add("app.tmdb.api-token", () -> "test-token");
        registry.add("app.tmdb.retry.initial-backoff", () -> "10ms");         // 실패 시험이 느려지지 않게 대기를 줄인다
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
    private TitleRepository titleRepository;

    @Autowired
    private TmdbProviderRepository tmdbProviderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 초기화() {
        wireMock.resetAll();
        titleRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM tmdb_provider WHERE mapping_state <> 'MAPPED'");   // 시드(MAPPED 8행)만 남긴다
        // 기본 응답: 제공처 목록은 샘플(8, 337, 9999), discover는 모든 서비스가 빈 결과. 시험이 필요한 서비스만 덮어쓴다
        stubProviderLists("""
                {"results":[
                  {"provider_id":8,"provider_name":"테스트 제공처 8","logo_path":"/l8.jpg"},
                  {"provider_id":337,"provider_name":"테스트 제공처 337","logo_path":"/l337.jpg"},
                  {"provider_id":9999,"provider_name":"테스트 제공처 9999(7곳 밖)","logo_path":"/l9999.jpg"}]}
                """, """
                {"results":[{"provider_id":1881,"provider_name":"Coupang Play","logo_path":"/l1881.jpg"}]}
                """);
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE)).atPriority(10).willReturn(json(EMPTY_PAGE)));
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_TV)).atPriority(10).willReturn(json(EMPTY_PAGE)));
    }

    @Test
    void 작은_discover_응답을_수집하면_title과_tmdb_provider_행이_기대값과_같다() throws Exception {
        stubDiscover(DISCOVER_MOVIE, "8|1796", 1, 1, "title", 900001, "테스트 영화 1", 900002, "테스트 영화 2");

        JobExecution execution = start(nextParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        // 영화 후보: 제목, 종류, 기본 갱신 등급, 수집 시각
        assertThat(titleRepository.findAll()).hasSize(2);
        Title movie = titleRepository.findByMediaTypeAndTmdbId(MediaType.MOVIE, 900001).orElseThrow();
        assertThat(movie.getTitle()).isEqualTo("테스트 영화 1");
        assertThat(movie.getRefreshTier()).isEqualTo(RefreshTier.WEEKLY);
        assertThat(movie.getFetchedAt()).isNotNull();
        // 제공처: 시드 8행 + 7곳 밖 9999 한 곳. 9999는 UNMAPPED, 처음 본 시각이 있다
        assertThat(tmdbProviderRepository.count()).isEqualTo(9);
        TmdbProvider outsider = tmdbProviderRepository.findById(9999).orElseThrow();
        assertThat(outsider.getMappingState()).isEqualTo(MappingState.UNMAPPED);
        assertThat(outsider.getTmdbName()).isEqualTo("테스트 제공처 9999(7곳 밖)");
        assertThat(outsider.getFirstSeenAt()).isNotNull();
        // 서비스 7곳을 영화·드라마 각각 한 번씩 요청했고, Netflix는 두 제공처 번호를 |(OR)로 묶었다
        wireMock.verify(7, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE)));
        wireMock.verify(7, getRequestedFor(urlPathEqualTo(DISCOVER_TV)));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE))
                .withQueryParam("with_watch_providers", equalTo("8|1796"))
                .withQueryParam("watch_region", equalTo("KR"))
                .withQueryParam("with_watch_monetization_types", equalTo("flatrate")));
    }

    @Test
    void 같은_작품이_두_서비스_목록에_나와도_title은_한_줄이다() throws Exception {
        stubDiscover(DISCOVER_MOVIE, "8|1796", 1, 1, "title", 900001, "겹치는 영화", 900002, "넷플릭스만");
        stubDiscover(DISCOVER_MOVIE, "337", 1, 1, "title", 900001, "겹치는 영화");

        JobExecution execution = start(nextParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(titleRepository.findAll()).hasSize(2);
    }

    @Test
    void 이미_있는_MAPPED_제공처는_덮어쓰지_않고_새_제공처만_UNMAPPED로_추가한다() throws Exception {
        jdbcTemplate.update("INSERT INTO tmdb_provider (provider_id, tmdb_name, mapping_state, tier, first_seen_at) "
                + "VALUES (7777, '관리자가 정리한 곳', 'IGNORED', 'STANDARD', now())");

        start(nextParameters());

        // 시드 8번은 목록에 "테스트 제공처 8"로 와도 이름·상태·서비스 연결이 그대로다
        assertThat(tmdbProviderRepository.findById(8).orElseThrow().getTmdbName()).isEqualTo("Netflix");
        assertThat(tmdbProviderRepository.findById(8).orElseThrow().getMappingState()).isEqualTo(MappingState.MAPPED);
        assertThat(jdbcTemplate.queryForObject("SELECT ott_service_id IS NOT NULL FROM tmdb_provider WHERE provider_id = 8",
                Boolean.class)).isTrue();
        assertThat(tmdbProviderRepository.findById(337).orElseThrow().getTmdbName()).isEqualTo("Disney Plus");
        // 관리자가 IGNORED로 정리한 행도 그대로다
        assertThat(tmdbProviderRepository.findById(7777).orElseThrow().getMappingState()).isEqualTo(MappingState.IGNORED);
        // 새 제공처 9999만 UNMAPPED로 늘었다
        assertThat(tmdbProviderRepository.findById(9999).orElseThrow().getMappingState()).isEqualTo(MappingState.UNMAPPED);
        assertThat(tmdbProviderRepository.count()).isEqualTo(10);   // 시드 8 + IGNORED 1 + 새 1
    }

    @Test
    void 영화와_드라마_후보를_모두_저장한다() throws Exception {
        stubDiscover(DISCOVER_MOVIE, "8|1796", 1, 1, "title", 900001, "테스트 영화");
        stubDiscover(DISCOVER_TV, "1881", 1, 1, "name", 900001, "테스트 드라마");   // 쿠팡플레이는 드라마 목록에 있다. TMDB ID는 영화와 겹쳐도 된다

        JobExecution execution = start(nextParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(titleRepository.findByMediaTypeAndTmdbId(MediaType.MOVIE, 900001).orElseThrow().getTitle())
                .isEqualTo("테스트 영화");
        assertThat(titleRepository.findByMediaTypeAndTmdbId(MediaType.TV, 900001).orElseThrow().getTitle())
                .isEqualTo("테스트 드라마");
        assertThat(titleRepository.findAll()).hasSize(2);
    }

    @Test
    void 결과가_여러_쪽이면_total_pages의_마지막_쪽까지_처리한다() throws Exception {
        stubDiscover(DISCOVER_MOVIE, "8|1796", 1, 3, "title", 900001, "1쪽 영화");
        stubDiscover(DISCOVER_MOVIE, "8|1796", 2, 3, "title", 900002, "2쪽 영화");
        stubDiscover(DISCOVER_MOVIE, "8|1796", 3, 3, "title", 900003, "3쪽 영화");

        JobExecution execution = start(nextParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(titleRepository.findAll()).extracting(Title::getTmdbId).containsExactlyInAnyOrder(900001, 900002, 900003);
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE))
                .withQueryParam("with_watch_providers", equalTo("8|1796")).withQueryParam("page", equalTo("3")));
        wireMock.verify(0, getRequestedFor(urlPathEqualTo(DISCOVER_MOVIE))
                .withQueryParam("with_watch_providers", equalTo("8|1796")).withQueryParam("page", equalTo("4")));
    }

    @Test
    void 같은_파라미터로_이미_완료된_Job은_다시_실행할_수_없다() throws Exception {
        JobParameters parameters = nextParameters();
        assertThat(start(parameters).getStatus()).isEqualTo(BatchStatus.COMPLETED);

        assertThatThrownBy(() -> start(parameters)).isInstanceOf(JobInstanceAlreadyCompleteException.class);
    }

    @Test
    void 중간에_실패한_Job은_같은_파라미터로_재시작하면_처음부터_다시_해_결과가_같다() throws Exception {
        stubDiscover(DISCOVER_MOVIE, "8|1796", 1, 2, "title", fullPage(900001)); // 꽉 찬 한 쪽(20건) = chunk 하나
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE))   // 2쪽은 TMDB 서버 오류(재시도 후에도 실패)
                .withQueryParam("with_watch_providers", equalTo("8|1796")).withQueryParam("page", equalTo("2"))
                .willReturn(aResponse().withStatus(500)));

        JobExecution failed = start(nextParameters());

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(titleRepository.findAll()).hasSize(20);   // 1쪽(chunk 하나)은 이미 커밋됐다

        // TMDB가 살아난 상황: 2쪽이 정상 응답한다. 같은 실행을 재시작한다
        stubDiscover(DISCOVER_MOVIE, "8|1796", 2, 2, "title", 900021, "2쪽 영화");
        JobExecution restarted = jobOperator.restart(failed);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getJobInstance().getInstanceId()).isEqualTo(failed.getJobInstance().getInstanceId());
        // 1쪽을 다시 읽었지만 이미 있는 작품은 건너뛰어 중복이 없고, 결과는 한 번에 성공했을 때와 같다
        assertThat(titleRepository.findAll()).extracting(Title::getTmdbId).hasSize(21).contains(900001, 900020, 900021);
        assertThat(tmdbProviderRepository.count()).isEqualTo(9);
    }

    @Test
    void ALL이_아닌_tier나_없는_targetDate는_시작하지_않는다() {
        LocalDate day = LocalDate.of(2026, 1, 1);
        JobParameters noDate = new JobParametersBuilder().addString(CollectJobParameters.TIER, "ALL").toJobParameters();

        // 이 Task는 ALL만 실행한다. DAILY·WEEKLY는 enum에 있어도 2단계(Task 077)까지 거부한다
        assertThatThrownBy(() -> start(CollectJobParameters.of(day, CollectTier.DAILY)))
                .isInstanceOf(InvalidJobParametersException.class);
        assertThatThrownBy(() -> start(CollectJobParameters.of(day, CollectTier.WEEKLY)))
                .isInstanceOf(InvalidJobParametersException.class);
        assertThatThrownBy(() -> start(new JobParametersBuilder().addLocalDate(CollectJobParameters.TARGET_DATE, day)
                .addString(CollectJobParameters.TIER, "MONTHLY").toJobParameters()))
                .isInstanceOf(InvalidJobParametersException.class);
        assertThatThrownBy(() -> start(noDate)).isInstanceOf(InvalidJobParametersException.class);
    }

    @Test
    void 새_작품은_요약_필드를_자릿수에_맞춰_저장하고_줄거리와_드라마_상태는_비워_둔다() throws Exception {
        stubDiscoverItems(DISCOVER_MOVIE, "8|1796", 1, 1, """
                {"id":900001,"title":"요약 영화","original_title":"Summary Movie","release_date":"2024-05-01",
                 "poster_path":"/p.jpg","backdrop_path":"/b.jpg","original_language":"en",
                 "popularity":12.3456,"vote_average":7.846,"overview":"목록 줄거리"}""");
        stubDiscoverItems(DISCOVER_TV, "1881", 1, 1, """
                {"id":900001,"name":"요약 드라마","original_name":"Summary Show","first_air_date":"2023-03-01",
                 "poster_path":"/tp.jpg","backdrop_path":null,"original_language":"ko","popularity":3,"vote_average":0}""");

        start(nextParameters());

        Title movie = titleRepository.findByMediaTypeAndTmdbId(MediaType.MOVIE, 900001).orElseThrow();
        assertThat(movie.getOriginalTitle()).isEqualTo("Summary Movie");
        assertThat(movie.getReleaseDate()).isEqualTo(LocalDate.of(2024, 5, 1));
        assertThat(movie.getPosterPath()).isEqualTo("/p.jpg");
        assertThat(movie.getBackdropPath()).isEqualTo("/b.jpg");
        assertThat(movie.getOriginalLanguage()).isEqualTo("en");
        assertThat(movie.getPopularity()).isEqualByComparingTo("12.346");   // NUMERIC(10,3) 반올림
        assertThat(movie.getVoteAverage()).isEqualByComparingTo("7.85");    // NUMERIC(4,2) 반올림
        assertThat(movie.getOverview()).isNull();   // 줄거리는 044가 한국어→영어 대체 규칙으로 채운다
        assertThat(movie.getOverviewLang()).isNull();
        assertThat(movie.getTvStatus()).isNull();
        Title tv = titleRepository.findByMediaTypeAndTmdbId(MediaType.TV, 900001).orElseThrow();
        assertThat(tv.getReleaseDate()).isEqualTo(LocalDate.of(2023, 3, 1));   // 드라마는 first_air_date
        assertThat(tv.getBackdropPath()).isNull();
        assertThat(tv.getVoteAverage()).isEqualByComparingTo("0");
    }

    @Test
    void 이미_있는_작품은_포스터_배경_인기도_평점만_갱신하고_나머지는_그대로_둔다() throws Exception {
        Instant old = Instant.parse("2026-01-01T00:00:00Z");
        Title existing = Title.create(MediaType.MOVIE, 900001, "원래 제목", old);
        existing.updateDetail("원래 제목", "Original", "상세 줄거리", "ko", LocalDate.of(2020, 1, 1), "/old-p.jpg",
                "/old-b.jpg", "ko", null, new BigDecimal("1.000"), new BigDecimal("1.00"), old);
        titleRepository.saveAndFlush(existing);
        stubDiscoverItems(DISCOVER_MOVIE, "8|1796", 1, 1, """
                {"id":900001,"title":"바뀐 제목","original_title":"Changed","release_date":"2024-05-01",
                 "poster_path":"/new-p.jpg","backdrop_path":"/new-b.jpg","original_language":"en",
                 "popularity":50.5,"vote_average":8.1}""");

        start(nextParameters());

        Title after = titleRepository.findByMediaTypeAndTmdbId(MediaType.MOVIE, 900001).orElseThrow();
        assertThat(after.getPosterPath()).isEqualTo("/new-p.jpg");
        assertThat(after.getBackdropPath()).isEqualTo("/new-b.jpg");
        assertThat(after.getPopularity()).isEqualByComparingTo("50.5");
        assertThat(after.getVoteAverage()).isEqualByComparingTo("8.1");
        // 044가 상세로 채우는 값은 건드리지 않는다
        assertThat(after.getTitle()).isEqualTo("원래 제목");
        assertThat(after.getOriginalTitle()).isEqualTo("Original");
        assertThat(after.getOverview()).isEqualTo("상세 줄거리");
        assertThat(after.getOverviewLang()).isEqualTo("ko");
        assertThat(after.getReleaseDate()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(after.getOriginalLanguage()).isEqualTo("ko");
        assertThat(after.getFetchedAt()).isEqualTo(old);
    }

    @Test
    void 요약_값이_그대로면_UPDATE가_나가지_않는다() throws Exception {
        stubDiscoverItems(DISCOVER_MOVIE, "8|1796", 1, 1, """
                {"id":900001,"title":"같은 영화","poster_path":"/p.jpg","backdrop_path":"/b.jpg",
                 "popularity":10.5,"vote_average":7.5}""");
        start(nextParameters());
        String sql = "SELECT updated_at FROM title WHERE tmdb_id = 900001";
        Object firstUpdatedAt = jdbcTemplate.queryForObject(sql, java.sql.Timestamp.class);

        start(nextParameters());   // 다른 날짜의 두 번째 수집, 응답은 같다

        assertThat(jdbcTemplate.queryForObject(sql, java.sql.Timestamp.class)).isEqualTo(firstUpdatedAt);   // updated_at이 그대로 = UPDATE 없음
    }

    // 도우미

    private JobExecution start(JobParameters parameters) throws Exception {
        return jobOperator.start(collectJob, parameters);
    }

    private static JobParameters nextParameters() {
        return CollectJobParameters.of(LocalDate.of(2026, 10, 1).plusDays(DAY_SEQUENCE.incrementAndGet()), CollectTier.ALL);
    }

    private static void stubProviderLists(String movieBody, String tvBody) {
        wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/movie")).willReturn(json(movieBody)));
        wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/tv")).willReturn(json(tvBody)));
    }

    /** 제공처 번호 묶음과 쪽 번호에 맞는 discover 응답을 등록한다. idAndTitles는 (TMDB ID, 제목) 쌍을 이어 붙인 것 */
    private static void stubDiscover(String path, String withProviders, int page, int totalPages, String titleField,
                                     Object... idAndTitles) {
        StringBuilder results = new StringBuilder();
        for (int i = 0; i < idAndTitles.length; i += 2) {
            if (i > 0) {
                results.append(',');
            }
            results.append("{\"id\":").append(idAndTitles[i]).append(",\"").append(titleField).append("\":\"")
                    .append(idAndTitles[i + 1]).append("\"}");
        }
        String body = "{\"page\":" + page + ",\"results\":[" + results + "],\"total_pages\":" + totalPages
                + ",\"total_results\":" + idAndTitles.length / 2 + "}";
        wireMock.stubFor(get(urlPathEqualTo(path))
                .withQueryParam("with_watch_providers", equalTo(withProviders))
                .withQueryParam("page", equalTo(String.valueOf(page)))
                .willReturn(json(body)));
    }

    /** 항목 JSON을 그대로 넣어 discover 응답을 등록한다(요약 필드 시험용) */
    private static void stubDiscoverItems(String path, String withProviders, int page, int totalPages, String... items) {
        String body = "{\"page\":" + page + ",\"results\":[" + String.join(",", items) + "],\"total_pages\":" + totalPages
                + ",\"total_results\":" + items.length + "}";
        wireMock.stubFor(get(urlPathEqualTo(path))
                .withQueryParam("with_watch_providers", equalTo(withProviders))
                .withQueryParam("page", equalTo(String.valueOf(page)))
                .willReturn(json(body)));
    }

    /** (ID, 제목) 쌍 20개: discover 한 쪽이 꽉 찬 경우 */
    private static Object[] fullPage(int firstId) {
        Object[] pairs = new Object[40];
        for (int i = 0; i < 20; i++) {
            pairs[i * 2] = firstId + i;
            pairs[i * 2 + 1] = "영화 " + (firstId + i);
        }
        return pairs;
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
