package com.ottnavi.catalog.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.rule.CastCandidate;
import com.ottnavi.catalog.domain.rule.RuntimeDefaults;
import com.ottnavi.catalog.service.model.FetchedTitle;
import com.ottnavi.catalog.service.model.TitleDetail;
import com.ottnavi.catalog.service.model.TitleKey;
import com.ottnavi.collect.step.detail.SingleTitleFetcher;
import com.ottnavi.infra.tmdb.TmdbClient;
import com.ottnavi.infra.tmdb.exception.TmdbException;
import com.ottnavi.support.IntegrationTestSupport;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 인기 순위 상위 작품 채우기의 부품(SingleTitleFetcher로 받고 TitleFillService로 저장)을 DB(Testcontainers)와 가짜 TMDB(WireMock)로 시험한다(Docker 필요).
 * 막으려는 사고: 작품만 들어가고 제공 상태가 비는 중간 상태, 시즌 하나가 실패했는데 일부만 저장되는 일, 이미 있는 작품을 덮어쓰는 일.
 * 시즌 방영 기준일은 고정 시계(2026-11-01)이고 fixture의 시즌 날짜는 모두 그보다 과거다.
 */
class TitleFillServiceTest extends IntegrationTestSupport {

    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    private static final Instant FETCHED_AT = Instant.parse("2026-11-01T00:00:00Z");
    private static final int MOVIE_ID = 900001; // movie-detail.json, watch-providers.json의 id
    private static final int TV_ID = 800001; // tv-detail.json의 id (시즌 1·2)
    private static final String EMPTY_RESULTS = "{\"id\":1,\"results\":{}}";

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
    private TmdbClient tmdbClient;

    @Autowired
    private RuntimeDefaults runtimeDefaults;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private TitleFillService titleFillService;

    @Autowired
    private TitleCollectService titleCollectService;

    @Autowired
    private TitleDetailQueryService titleDetailQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SingleTitleFetcher fetcher;

    @BeforeEach
    void 초기화() {
        wireMock.resetAll();
        deleteTestTitles();
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        fetcher = new SingleTitleFetcher(tmdbClient, runtimeDefaults, meterRegistry, fixedClock);
    }

    @AfterEach
    void 정리() {
        deleteTestTitles();
    }

    /** 이 테스트가 만든 작품만 FK 순서대로 지운다(다른 테스트의 데이터는 건드리지 않는다) */
    private void deleteTestTitles() {
        String titleIds = "(SELECT id FROM title WHERE tmdb_id IN (" + MOVIE_ID + ", " + TV_ID + "))";
        String unitIds = "(SELECT id FROM watch_unit WHERE title_id IN " + titleIds + ")";
        jdbcTemplate.update("DELETE FROM availability_change WHERE watch_unit_id IN " + unitIds);
        jdbcTemplate.update("DELETE FROM availability WHERE watch_unit_id IN " + unitIds);
        jdbcTemplate.update("DELETE FROM watch_unit WHERE title_id IN " + titleIds);
        jdbcTemplate.update("DELETE FROM title_cast WHERE title_id IN " + titleIds);
        jdbcTemplate.update("DELETE FROM title_genre WHERE title_id IN " + titleIds);
        jdbcTemplate.update("DELETE FROM title WHERE tmdb_id IN (" + MOVIE_ID + ", " + TV_ID + ")");
    }

    @Test
    void 영화_한_편이_title_시청_단위_제공_상태_이력까지_저장되고_상세_조회가_성공한다() throws Exception {
        // given: 영화 상세와 KR 제공처(Netflix 8, Disney+ 337 구독)
        stubMovieFixtures();

        // when
        FetchedTitle fetched = fetcher.fetch(MediaType.MOVIE, MOVIE_ID);
        boolean saved = titleFillService.saveFetched(fetched, "스냅샷 제목", FETCHED_AT);

        // then
        assertThat(saved).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT title FROM title WHERE tmdb_id = ?", String.class, MOVIE_ID))
                .isEqualTo("테스트 영화 1");
        assertThat(jdbcTemplate.queryForObject("SELECT refresh_tier FROM title WHERE tmdb_id = ?", String.class, MOVIE_ID))
                .isEqualTo("WEEKLY");
        assertThat(count(unitCountSql(MOVIE_ID) + " AND u.unit_type = 'MOVIE'")).isEqualTo(1);
        assertThat(count(availabilityCountSql(MOVIE_ID))).isEqualTo(7);
        assertThat(count(availabilityStatusCountSql(MOVIE_ID, "NETFLIX", "AVAILABLE"))).isEqualTo(1);
        assertThat(count(availabilityStatusCountSql(MOVIE_ID, "WATCHA", "NOT_AVAILABLE"))).isEqualTo(1);
        // 이력은 새로 생긴 7건이고 배치 밖 변경이라 job_execution_id가 비어 있다
        assertThat(count("SELECT count(*) FROM availability_change c JOIN watch_unit u ON u.id = c.watch_unit_id "
                + "JOIN title t ON t.id = u.title_id WHERE t.tmdb_id = " + MOVIE_ID
                + " AND c.job_execution_id IS NULL AND c.before_status IS NULL")).isEqualTo(7);
        assertThat(titleDetailQueryService.getDetail(MediaType.MOVIE, MOVIE_ID).tmdbId()).isEqualTo(MOVIE_ID);
    }

    @Test
    void 드라마_한_편이_시즌마다_시청_단위와_제공_상태까지_저장되고_상세_조회가_성공한다() throws Exception {
        // given: 드라마 상세(시즌 1·2). 시즌 1은 시즌 단위 KR 제공처가 있고, 시즌 2는 비어 작품 단위 제공처로 대신 판정한다
        stubTvFixtures();
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + TV_ID + "/season/1/watch/providers"))
                .willReturn(json(krFlatrate(337))));
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + TV_ID + "/season/2/watch/providers"))
                .willReturn(json(EMPTY_RESULTS)));
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + TV_ID + "/watch/providers")).willReturn(json(krFlatrate(8))));

        // when
        FetchedTitle fetched = fetcher.fetch(MediaType.TV, TV_ID);
        boolean saved = titleFillService.saveFetched(fetched, "스냅샷 제목", FETCHED_AT);

        // then
        assertThat(saved).isTrue();
        assertThat(count(unitCountSql(TV_ID) + " AND u.unit_type = 'SEASON'")).isEqualTo(2);
        assertThat(count(availabilityCountSql(TV_ID))).isEqualTo(14); // 시즌 2개 x 서비스 7곳
        assertThat(count(availabilityStatusCountSql(TV_ID, "DISNEY_PLUS", "AVAILABLE"))).isEqualTo(1); // 시즌 1(시즌 단위)
        assertThat(count(availabilityStatusCountSql(TV_ID, "NETFLIX", "AVAILABLE"))).isEqualTo(1); // 시즌 2(작품 단위로 대신)
        assertThat(count("SELECT count(*) FROM title_cast c JOIN title t ON t.id = c.title_id WHERE t.tmdb_id = " + TV_ID))
                .isEqualTo(1);
        assertThat(titleDetailQueryService.getDetail(MediaType.TV, TV_ID).tmdbId()).isEqualTo(TV_ID);
    }

    @Test
    void 이미_있는_작품이면_저장하지_않고_false이며_TMDB를_부르지_않는다() throws Exception {
        // given: 영화가 먼저 저장돼 있다
        stubMovieFixtures();
        FetchedTitle fetched = fetcher.fetch(MediaType.MOVIE, MOVIE_ID);
        assertThat(titleFillService.saveFetched(fetched, "스냅샷 제목", FETCHED_AT)).isTrue();
        wireMock.resetRequests();

        // when: 이미 있는 키인지 확인하고, 같은 값을 다시 저장해 본다
        Set<TitleKey> existing = titleCollectService.findExistingKeys(
                List.of(new TitleKey(MediaType.MOVIE, MOVIE_ID), new TitleKey(MediaType.TV, MOVIE_ID)));
        boolean savedAgain = titleFillService.saveFetched(fetched, "스냅샷 제목", FETCHED_AT);

        // then: 영화 키만 있고(같은 번호의 드라마는 없음), 두 번째 저장은 false이며 TMDB 요청은 0회, 행도 늘지 않는다
        assertThat(existing).containsExactly(new TitleKey(MediaType.MOVIE, MOVIE_ID));
        assertThat(savedAgain).isFalse();
        assertThat(wireMock.getAllServeEvents()).isEmpty();
        assertThat(count("SELECT count(*) FROM title WHERE tmdb_id = " + MOVIE_ID)).isEqualTo(1);
        assertThat(count(availabilityCountSql(MOVIE_ID))).isEqualTo(7);
    }

    @Test
    void TMDB에_상세가_없으면_404라_null이고_아무것도_저장하지_않는다() {
        // given: 스텁이 없으면 WireMock이 404를 준다

        // when
        FetchedTitle fetched = fetcher.fetch(MediaType.MOVIE, MOVIE_ID);

        // then
        assertThat(fetched).isNull();
        assertThat(count("SELECT count(*) FROM title WHERE tmdb_id = " + MOVIE_ID)).isZero();
    }

    @Test
    void 시즌_하나가_실패하면_예외가_오르고_그_작품은_아무것도_저장되지_않는다() throws Exception {
        // given: 시즌 1은 정상, 시즌 2 상세는 서버 오류(500)
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + TV_ID)).withQueryParam("language", equalTo("ko-KR"))
                .willReturn(json(fixture("tv-detail.json"))));
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + TV_ID + "/season/1")).willReturn(json(fixture("tv-season.json"))));
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + TV_ID + "/season/2")).willReturn(aResponse().withStatus(500)));

        // when & then: 받는 단계에서 실패하므로 저장 단계에 가지 않는다
        assertThatThrownBy(() -> fetcher.fetch(MediaType.TV, TV_ID)).isInstanceOf(TmdbException.class);
        assertThat(count("SELECT count(*) FROM title WHERE tmdb_id = " + TV_ID)).isZero();
        assertThat(count(unitCountSql(TV_ID))).isZero();
    }

    @Test
    void 저장_도중_실패하면_이미_넣은_title까지_함께_되돌아가_중간_상태가_남지_않는다() throws Exception {
        // given: 출연자 이름이 컬럼 길이(200자)를 넘어, title INSERT 뒤 출연진 INSERT에서 DB 오류가 나는 묶음
        stubMovieFixtures();
        FetchedTitle fetched = fetcher.fetch(MediaType.MOVIE, MOVIE_ID);
        TitleDetail d = fetched.detail();
        List<CastCandidate> brokenCast = List.of(new CastCandidate(1L, "가".repeat(500), null, null, 0));
        TitleDetail brokenDetail = new TitleDetail(d.mediaType(), d.tmdbId(), d.title(), d.originalTitle(), d.overview(),
                d.overviewLang(), d.releaseDate(), d.posterPath(), d.backdropPath(), d.originalLanguage(), d.tvStatus(),
                d.popularity(), d.voteAverage(), d.genres(), brokenCast, d.units());
        FetchedTitle broken = new FetchedTitle(brokenDetail, fetched.providersBySeason());

        // when & then: 예외가 오르고 title 행이 남지 않는다
        assertThatThrownBy(() -> titleFillService.saveFetched(broken, "스냅샷 제목", FETCHED_AT))
                .isInstanceOf(RuntimeException.class);
        assertThat(count("SELECT count(*) FROM title WHERE tmdb_id = " + MOVIE_ID)).isZero();
    }

    // 보조

    private void stubMovieFixtures() throws IOException {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/" + MOVIE_ID)).withQueryParam("language", equalTo("ko-KR"))
                .willReturn(json(fixture("movie-detail.json"))));
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/" + MOVIE_ID + "/watch/providers"))
                .willReturn(json(fixture("watch-providers.json"))));
    }

    private void stubTvFixtures() throws IOException {
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + TV_ID)).withQueryParam("language", equalTo("ko-KR"))
                .willReturn(json(fixture("tv-detail.json"))));
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + TV_ID + "/season/1")).willReturn(json(fixture("tv-season.json"))));
        String season2 = "{\"id\":810002,\"season_number\":2,\"name\":\"시즌 2\",\"air_date\":\"2024-03-01\","
                + "\"episodes\":[{\"episode_number\":1,\"air_date\":\"2024-03-01\",\"runtime\":60}]}";
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + TV_ID + "/season/2")).willReturn(json(season2)));
    }

    private static String krFlatrate(int providerId) {
        return "{\"id\":1,\"results\":{\"KR\":{\"flatrate\":[{\"provider_id\":" + providerId + ",\"provider_name\":\"P\"}]}}}";
    }

    private static String unitCountSql(int tmdbId) {
        return "SELECT count(*) FROM watch_unit u JOIN title t ON t.id = u.title_id WHERE t.tmdb_id = " + tmdbId;
    }

    private static String availabilityCountSql(int tmdbId) {
        return "SELECT count(*) FROM availability a JOIN watch_unit u ON u.id = a.watch_unit_id JOIN title t ON t.id = u.title_id "
                + "WHERE t.tmdb_id = " + tmdbId;
    }

    private static String availabilityStatusCountSql(int tmdbId, String serviceCode, String status) {
        return availabilityCountSql(tmdbId) + " AND a.status = '" + status + "' AND a.ott_service_id = "
                + "(SELECT id FROM ott_service WHERE code = '" + serviceCode + "')";
    }

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = TitleFillServiceTest.class.getResourceAsStream("/tmdb/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
