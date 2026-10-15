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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Task 044: 수집 Job의 상세·시즌·판정 Step을 DB(Testcontainers)와 가짜 TMDB(WireMock)로 끝까지 시험한다(Docker 필요).
 * 제공처 번호는 V2 시드(Netflix 8 STANDARD·1796 AD, Disney 337, TVING 1883, Watcha 97, wavve 356, 쿠팡 1881, Apple 350)를 쓴다.
 * 판정 기준일은 Job 파라미터 targetDate(2026-11-xx)다. 시즌·회차 날짜는 그보다 앞(방영됨)이거나 2027년(미방영)으로 둔다.
 */
class CollectDetailJobTest extends IntegrationTestSupport {

    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());
    private static final AtomicInteger DAY_SEQUENCE = new AtomicInteger();   // 테스트마다 다른 targetDate
    private static final String DISCOVER_MOVIE = "/3/discover/movie";
    private static final String DISCOVER_TV = "/3/discover/tv";
    private static final String EMPTY_PAGE = "{\"page\":1,\"results\":[],\"total_pages\":1,\"total_results\":0}";

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

    @BeforeEach
    void 초기화() {
        wireMock.resetAll();
        // FK 순서대로 비운다(자식 → 부모). 제공처 시드(MAPPED 8행)는 남긴다
        for (String table : List.of("availability_change", "availability", "watch_unit", "title_cast", "title_genre",
                "title", "genre")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        jdbcTemplate.update("DELETE FROM tmdb_provider WHERE mapping_state <> 'MAPPED'");
        wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/movie")).willReturn(json("{\"results\":[]}")));
        wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/tv")).willReturn(json("{\"results\":[]}")));
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_MOVIE)).atPriority(10).willReturn(json(EMPTY_PAGE)));
        wireMock.stubFor(get(urlPathEqualTo(DISCOVER_TV)).atPriority(10).willReturn(json(EMPTY_PAGE)));
    }

    // 영화

    @Test
    void 영화_한_편의_상세_장르_출연진_시청_단위와_제공_상태가_기대값과_같다() throws Exception {
        stubDiscover(DISCOVER_MOVIE, "8|1796", 1, 1, "title", 900001);
        stubMovie(900001, "가짜 줄거리 1", 118, "[{\"id\":18,\"name\":\"드라마\"},{\"id\":53,\"name\":\"스릴러\"}]", cast(12));
        // KR 구독: Netflix(8), TVING(1883), 7곳 밖(9999). 대여: Disney(337)는 구독이 아니라 없음으로 본다
        stubMovieProviders(900001, region(new int[] {8, 1883, 9999}, new int[] {337}));

        JobExecution execution = start(nextParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        Map<String, Object> title = row("SELECT * FROM title WHERE tmdb_id = 900001 AND media_type = 'MOVIE'");
        assertThat(title.get("title")).isEqualTo("후보 900001");   // 상세 응답의 제목은 sample이 같은 값이다(아래 stubMovie)
        assertThat(title.get("overview")).isEqualTo("가짜 줄거리 1");
        assertThat(title.get("overview_lang")).isEqualTo("ko");
        assertThat(title.get("tv_status")).isNull();
        // 영화는 MOVIE 단위 하나. 회차는 1, 러닝타임은 runtime 그대로, 추정 아님
        Map<String, Object> unit = row("SELECT * FROM watch_unit");
        assertThat(unit.get("unit_type")).isEqualTo("MOVIE");
        assertThat(unit.get("season_number")).isNull();
        assertThat(unit.get("episode_count")).isEqualTo(1);
        assertThat(unit.get("aired_episode_count")).isEqualTo(1);
        assertThat(unit.get("total_runtime_min")).isEqualTo(118);
        assertThat(unit.get("runtime_estimated")).isEqualTo(false);
        // 장르: genre 표에 시드가 없어 응답으로 등록되고 작품에 두 개가 연결된다
        assertThat(jdbcTemplate.queryForList("SELECT name FROM genre ORDER BY id", String.class))
                .containsExactly("드라마", "스릴러");
        assertThat(count("title_genre")).isEqualTo(2);
        // 출연진: 12명 중 order 순 상위 10명, 자리는 0부터
        assertThat(jdbcTemplate.queryForList("SELECT cast_order FROM title_cast ORDER BY cast_order", Integer.class))
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM title_cast WHERE cast_order = 0", String.class))
                .isEqualTo("배우 0");
        // 제공 상태 7행: Netflix·TVING 있음, 대여뿐인 Disney와 나머지는 없음, 쿠팡플레이 영화는 모름(STRUCTURAL_GAP)
        long unitId = ((Number) unit.get("id")).longValue();
        assertThat(count("availability")).isEqualTo(7);
        assertThat(availability(unitId, "NETFLIX")).containsEntry("status", "AVAILABLE").containsEntry("in_standard", true)
                .containsEntry("in_ad_tier", false);
        assertThat(availability(unitId, "TVING")).containsEntry("status", "AVAILABLE");
        assertThat(availability(unitId, "DISNEY_PLUS")).containsEntry("status", "NOT_AVAILABLE");
        assertThat(availability(unitId, "WATCHA")).containsEntry("status", "NOT_AVAILABLE");
        assertThat(availability(unitId, "COUPANG_PLAY")).containsEntry("status", "UNKNOWN")
                .containsEntry("unknown_reason", "STRUCTURAL_GAP");
        // 이력: 신규 7건, 이전 상태 없음, 이 실행의 ID
        assertThat(count("availability_change")).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM availability_change WHERE before_status IS NULL "
                + "AND job_execution_id = ?", Integer.class, execution.getId())).isEqualTo(7);
    }

    @Test
    void 광고형_제공처만_있으면_광고형_플래그만_켜진다() throws Exception {
        stubDiscover(DISCOVER_MOVIE, "8|1796", 1, 1, "title", 900001);
        stubMovie(900001, "줄거리", 100, "[]", cast(0));
        stubMovieProviders(900001, region(new int[] {1796}, new int[] {}));   // Netflix 광고형(AD)만

        start(nextParameters());

        long unitId = jdbcTemplate.queryForObject("SELECT id FROM watch_unit", Long.class);
        assertThat(availability(unitId, "NETFLIX")).containsEntry("status", "AVAILABLE")
                .containsEntry("in_standard", false).containsEntry("in_ad_tier", true);
    }

    @Test
    void 매핑되지_않은_제공처만_있으면_모든_서비스가_없음이고_KR_데이터가_없으면_모름이다() throws Exception {
        stubDiscover(DISCOVER_MOVIE, "8|1796", 1, 1, "title", 900001, 900002);
        stubMovie(900001, "줄거리", 100, "[]", cast(0));
        stubMovie(900002, "줄거리", 100, "[]", cast(0));
        stubMovieProviders(900001, region(new int[] {9999}, new int[] {}));   // 7곳 밖 제공처만 구독
        stubMovieProviders(900002, "{\"id\":900002,\"results\":{}}");          // KR 키가 없다

        start(nextParameters());

        long withProvider = unitIdOf(900001);
        assertThat(availability(withProvider, "NETFLIX")).containsEntry("status", "NOT_AVAILABLE");
        long noKr = unitIdOf(900002);
        assertThat(availability(noKr, "NETFLIX")).containsEntry("status", "UNKNOWN")
                .containsEntry("unknown_reason", "NO_KR_DATA");
        assertThat(availability(noKr, "COUPANG_PLAY")).containsEntry("unknown_reason", "STRUCTURAL_GAP");
    }

    @Test
    void 한국어_줄거리가_비었을_때만_영어로_한_번_더_호출한다() throws Exception {
        stubDiscover(DISCOVER_MOVIE, "8|1796", 1, 1, "title", 900001, 900002);
        stubMovie(900001, "한국어 줄거리", 100, "[]", cast(0));
        stubMovie(900002, "", 100, "[]", cast(0));   // 한국어 줄거리가 빈 문자열
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/900002")).withQueryParam("language", equalTo("en-US"))
                .willReturn(json("{\"id\":900002,\"title\":\"English Title\",\"overview\":\"English overview\"}")));

        start(nextParameters());

        assertThat(row("SELECT overview, overview_lang FROM title WHERE tmdb_id = 900001"))
                .containsEntry("overview", "한국어 줄거리").containsEntry("overview_lang", "ko");
        assertThat(row("SELECT overview, overview_lang FROM title WHERE tmdb_id = 900002"))
                .containsEntry("overview", "English overview").containsEntry("overview_lang", "en");
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/3/movie/900001")).withQueryParam("language", equalTo("en-US")));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/900002")).withQueryParam("language", equalTo("en-US")));
    }

    @Test
    void 한국어와_영어_줄거리가_모두_비면_기존_줄거리와_언어_표시를_유지한다() throws Exception {
        stubDiscover(DISCOVER_MOVIE, "8|1796", 1, 1, "title", 900001);
        stubMovie(900001, "기존 줄거리", 100, "[]", cast(0));
        start(nextParameters());
        assertThat(row("SELECT overview, overview_lang FROM title WHERE tmdb_id = 900001"))
                .containsEntry("overview", "기존 줄거리").containsEntry("overview_lang", "ko");

        // 두 번째 수집: 한국어도 영어도 줄거리가 비었다
        stubMovie(900001, "", 100, "[]", cast(0));
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/900001")).withQueryParam("language", equalTo("en-US"))
                .willReturn(json("{\"id\":900001,\"overview\":\"\"}")));
        start(nextParameters());

        assertThat(row("SELECT overview, overview_lang FROM title WHERE tmdb_id = 900001"))
                .containsEntry("overview", "기존 줄거리").containsEntry("overview_lang", "ko");
    }

    @Test
    void runtime이_비어_있으면_기본값으로_추정하고_표시한다() throws Exception {
        stubDiscover(DISCOVER_MOVIE, "8|1796", 1, 1, "title", 900001);
        stubMovie(900001, "줄거리", null, "[]", cast(0));

        start(nextParameters());

        assertThat(row("SELECT * FROM watch_unit")).containsEntry("total_runtime_min", 100)
                .containsEntry("runtime_missing_count", 1).containsEntry("runtime_estimated", true);
    }

    // 드라마

    @Test
    void 드라마는_방영된_시즌만_시즌_상세의_회차_수로_저장하고_시즌_0과_미방영_시즌은_저장하지_않는다() throws Exception {
        stubDiscover(DISCOVER_TV, "8|1796", 1, 1, "name", 800001);
        stubTv(800001, "[{\"id\":18,\"name\":\"드라마\"}]", """
                [{"id":810000,"season_number":0,"name":"스페셜","air_date":"2022-12-25","episode_count":2},
                 {"id":810001,"season_number":1,"name":"시즌 1","air_date":"2023-03-01","episode_count":12,"poster_path":"/s1.jpg"},
                 {"id":810002,"season_number":2,"name":"시즌 2","air_date":"2027-03-01","episode_count":12}]""");
        // 시즌 1: 4회 중 3회 방영(날짜 없는 4회는 미방영), runtime은 62·58·빈 값 → 62+58+43(기본값)
        stubSeason(800001, 1, "시즌 1", "2023-03-01", """
                [{"episode_number":1,"air_date":"2023-03-01","runtime":62},
                 {"episode_number":2,"air_date":"2023-03-02","runtime":58},
                 {"episode_number":3,"air_date":"2023-03-08","runtime":null},
                 {"episode_number":4,"air_date":"","runtime":null}]""");
        stubSeason(800001, 2, "시즌 2", "2027-03-01", """
                [{"episode_number":1,"air_date":"2027-03-01","runtime":60},
                 {"episode_number":2,"air_date":"2027-03-08","runtime":60}]""");
        stubSeasonProviders(800001, 1, region(new int[] {337}, new int[] {}));

        JobExecution execution = start(nextParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(row("SELECT tv_status, overview FROM title WHERE media_type = 'TV' AND tmdb_id = 800001"))
                .containsEntry("tv_status", "Returning Series").containsEntry("overview", "가짜 줄거리 A");
        Map<String, Object> unit = row("SELECT * FROM watch_unit");   // 단 한 줄: 시즌 1
        assertThat(unit.get("unit_type")).isEqualTo("SEASON");
        assertThat(unit.get("season_number")).isEqualTo(1);
        assertThat(unit.get("tmdb_season_id")).isEqualTo(810001);
        assertThat(unit.get("name")).isEqualTo("시즌 1");
        assertThat(unit.get("episode_count")).isEqualTo(4);   // 시즌 요약의 12가 아니라 시즌 상세의 회차 목록 개수
        assertThat(unit.get("aired_episode_count")).isEqualTo(3);
        assertThat(unit.get("total_runtime_min")).isEqualTo(163);
        assertThat(unit.get("runtime_missing_count")).isEqualTo(1);
        assertThat(unit.get("runtime_estimated")).isEqualTo(true);
        long unitId = ((Number) unit.get("id")).longValue();
        assertThat(availability(unitId, "DISNEY_PLUS")).containsEntry("status", "AVAILABLE");
        assertThat(availability(unitId, "COUPANG_PLAY")).containsEntry("status", "NOT_AVAILABLE");   // 쿠팡 규칙은 영화에만 적용
        // 시즌 0은 시즌 상세·제공처를 아예 부르지 않는다. 시즌 2는 상세는 받았지만 방영 0회라 제공처까지 가지 않는다
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/3/tv/800001/season/0")));
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/tv/800001/season/2")));
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/3/tv/800001/season/2/watch/providers")));
    }

    @Test
    void 애니메이션_드라마의_빈_runtime은_24분으로_추정한다() throws Exception {
        stubDiscover(DISCOVER_TV, "8|1796", 1, 1, "name", 800001);
        stubTv(800001, "[{\"id\":16,\"name\":\"애니메이션\"}]",
                "[{\"id\":810001,\"season_number\":1,\"name\":\"시즌 1\",\"air_date\":\"2023-03-01\",\"episode_count\":1}]");
        stubSeason(800001, 1, "시즌 1", "2023-03-01", "[{\"episode_number\":1,\"air_date\":\"2023-03-01\",\"runtime\":null}]");

        start(nextParameters());

        assertThat(row("SELECT * FROM watch_unit")).containsEntry("total_runtime_min", 24)
                .containsEntry("runtime_estimated", true);
    }

    // 404와 부분 실패

    @Test
    void 상세나_시즌이_404면_그_작품만_건너뛰고_Job은_계속한다() throws Exception {
        stubDiscover(DISCOVER_MOVIE, "8|1796", 1, 1, "title", 900001, 900002);
        // 900001은 상세가 없다(404). 900002는 정상이다
        stubMovie(900002, "정상 줄거리", 100, "[]", cast(0));
        stubDiscover(DISCOVER_TV, "8|1796", 1, 1, "name", 800001);
        stubTv(800001, "[]", "[{\"id\":810001,\"season_number\":1,\"name\":\"시즌 1\",\"air_date\":\"2023-03-01\"}]");
        // 시즌 1 상세는 스텁이 없어 404 → 드라마는 작품 전체를 건너뛴다(부분 저장 없음)

        JobExecution execution = start(nextParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(row("SELECT overview FROM title WHERE tmdb_id = 900001")).containsEntry("overview", null);
        assertThat(row("SELECT overview FROM title WHERE tmdb_id = 900002")).containsEntry("overview", "정상 줄거리");
        assertThat(row("SELECT overview FROM title WHERE media_type = 'TV'")).containsEntry("overview", null);   // 상세 응답은 받았어도 저장하지 않았다
        assertThat(count("watch_unit")).isEqualTo(1);   // 정상 영화 하나뿐
    }

    @Test
    void 제공처_조회가_404면_그_단위의_기존_제공_상태와_이력을_건드리지_않는다() throws Exception {
        stubDiscover(DISCOVER_MOVIE, "8|1796", 1, 1, "title", 900001);
        stubMovie(900001, "줄거리", 100, "[]", cast(0));
        stubMovieProviders(900001, region(new int[] {8}, new int[] {}));
        start(nextParameters());
        long unitId = unitIdOf(900001);
        Object checkedAt = availability(unitId, "NETFLIX").get("checked_at");

        // 두 번째 수집: 제공처 조회가 404가 됐다
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/900001/watch/providers")).willReturn(aResponse().withStatus(404)));
        JobExecution second = start(nextParameters());

        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(availability(unitId, "NETFLIX")).containsEntry("status", "AVAILABLE").containsEntry("checked_at", checkedAt);
        assertThat(count("availability_change")).isEqualTo(7);   // 첫 수집의 신규 7건 그대로
    }

    // 멱등과 이력

    @Test
    void 같은_입력으로_다시_수집해도_행_수와_이력이_늘지_않는다() throws Exception {
        stubDiscover(DISCOVER_MOVIE, "8|1796", 1, 1, "title", 900001);
        stubMovie(900001, "줄거리", 118, "[{\"id\":18,\"name\":\"드라마\"}]", cast(3));
        stubMovieProviders(900001, region(new int[] {8}, new int[] {}));
        stubDiscover(DISCOVER_TV, "8|1796", 1, 1, "name", 800001);
        stubTv(800001, "[{\"id\":18,\"name\":\"드라마\"}]",
                "[{\"id\":810001,\"season_number\":1,\"name\":\"시즌 1\",\"air_date\":\"2023-03-01\"}]");
        stubSeason(800001, 1, "시즌 1", "2023-03-01", "[{\"episode_number\":1,\"air_date\":\"2023-03-01\",\"runtime\":50}]");
        stubSeasonProviders(800001, 1, region(new int[] {337}, new int[] {}));
        start(nextParameters());
        Map<String, Integer> first = counts();
        Object firstFetchedAt = row("SELECT fetched_at FROM title WHERE tmdb_id = 900001 AND media_type = 'MOVIE'")
                .get("fetched_at");

        JobExecution second = start(nextParameters());   // 다른 날짜의 같은 수집

        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(counts()).isEqualTo(first);   // title·watch_unit·genre·title_genre·title_cast·availability·이력 모두 같다
        assertThat(count("availability_change")).isEqualTo(14);   // (영화 7 + 시즌 7) 신규뿐, 재실행이 늘리지 않는다
        // 수집 시각은 새로 기록된다(UPDATE가 나갔다)
        Object secondFetchedAt = row("SELECT fetched_at FROM title WHERE tmdb_id = 900001 AND media_type = 'MOVIE'")
                .get("fetched_at");
        assertThat(secondFetchedAt).isNotEqualTo(firstFetchedAt);
    }

    @Test
    void 제공_상태가_바뀌면_바뀐_서비스만_이력이_한_줄_늘고_이전과_이후_상태가_남는다() throws Exception {
        stubDiscover(DISCOVER_MOVIE, "8|1796", 1, 1, "title", 900001);
        stubMovie(900001, "줄거리", 100, "[]", cast(0));
        stubMovieProviders(900001, region(new int[] {8, 337}, new int[] {}));   // Netflix·Disney 있음
        start(nextParameters());
        long unitId = unitIdOf(900001);

        // 두 번째 수집: Netflix만 빠졌다. 응답은 KR 데이터가 있고 flatrate에 Disney만 있다
        stubMovieProviders(900001, region(new int[] {337}, new int[] {}));
        JobExecution second = start(nextParameters());

        assertThat(availability(unitId, "NETFLIX")).containsEntry("status", "NOT_AVAILABLE");
        assertThat(availability(unitId, "DISNEY_PLUS")).containsEntry("status", "AVAILABLE");
        assertThat(count("availability_change")).isEqualTo(8);   // 첫 수집 7 + 바뀐 Netflix 1
        Map<String, Object> change = row("SELECT before_status, after_status, job_execution_id FROM availability_change "
                + "WHERE job_execution_id = " + second.getId());
        assertThat(change).containsEntry("before_status", "AVAILABLE").containsEntry("after_status", "NOT_AVAILABLE");
    }

    @Test
    void 장르와_출연진이_줄거나_바뀌면_응답에_맞춰_갱신한다() throws Exception {
        stubDiscover(DISCOVER_MOVIE, "8|1796", 1, 1, "title", 900001);
        stubMovie(900001, "줄거리", 100, "[{\"id\":18,\"name\":\"드라마\"},{\"id\":53,\"name\":\"스릴러\"}]", cast(3));
        start(nextParameters());
        assertThat(count("title_genre")).isEqualTo(2);
        assertThat(count("title_cast")).isEqualTo(3);

        // 두 번째 수집: 장르 하나가 빠지고 출연진이 2명으로 줄며 첫 자리 인물이 바뀌었다
        stubMovie(900001, "줄거리", 100, "[{\"id\":18,\"name\":\"드라마\"}]", """
                [{"id":8001,"name":"새 주연","character":"새 역","order":0,"profile_path":null},
                 {"id":7001,"name":"배우 1","character":"역할 1","order":1,"profile_path":"/p1.jpg"}]""");
        start(nextParameters());

        assertThat(jdbcTemplate.queryForList("SELECT genre_id FROM title_genre", Integer.class)).containsExactly(18);
        assertThat(count("genre")).isEqualTo(2);   // 장르 표 자체는 지우지 않는다
        assertThat(jdbcTemplate.queryForList("SELECT name FROM title_cast ORDER BY cast_order", String.class))
                .containsExactly("새 주연", "배우 1");
    }

    // 재시작

    @Test
    void 상세_Step이_중간에_실패하면_재시작이_커밋된_위치부터_이어_읽는다() throws Exception {
        int[] ids = IntStream.rangeClosed(900001, 900025).toArray();   // 25편 = chunk 3개(10, 10, 5)
        stubDiscover(DISCOVER_MOVIE, "8|1796", 1, 2, "title", IntStream.rangeClosed(900001, 900020).boxed().toArray());
        stubDiscover(DISCOVER_MOVIE, "8|1796", 2, 2, "title", IntStream.rangeClosed(900021, 900025).boxed().toArray());
        for (int id : ids) {
            stubMovie(id, "줄거리 " + id, 100, "[]", cast(0));
        }
        // 900015(두 번째 chunk 안)는 TMDB 서버 오류가 재시도 후에도 계속된다
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/900015")).atPriority(1).willReturn(aResponse().withStatus(500)));

        JobExecution failed = start(nextParameters());

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        // 첫 chunk(10건)만 커밋됐다. 두 번째 chunk는 통째로 롤백됐다
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM title WHERE overview IS NOT NULL", Integer.class))
                .isEqualTo(10);

        // TMDB가 살아났다. 같은 실행을 재시작한다
        wireMock.removeStubMapping(wireMock.getStubMappings().stream()
                .filter(m -> m.getPriority() != null && m.getPriority() == 1).findFirst().orElseThrow());
        JobExecution restarted = jobOperator.restart(failed);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM title WHERE overview IS NOT NULL", Integer.class))
                .isEqualTo(25);
        // 첫 chunk의 작품(900001~900010)은 한 번만 호출했다 = 재시작이 이미 커밋한 위치를 건너뛰고 이어 읽었다
        for (int id = 900001; id <= 900010; id++) {
            wireMock.verify(1, getRequestedFor(urlPathEqualTo("/3/movie/" + id)));
        }
        // 롤백된 chunk의 작품(900011~900014)은 다시 처리했다
        for (int id = 900011; id <= 900014; id++) {
            wireMock.verify(2, getRequestedFor(urlPathEqualTo("/3/movie/" + id)));
        }
    }

    // 도우미

    private JobExecution start(JobParameters parameters) throws Exception {
        return jobOperator.start(collectJob, parameters);
    }

    private static JobParameters nextParameters() {
        return CollectJobParameters.of(LocalDate.of(2026, 11, 1).plusDays(DAY_SEQUENCE.incrementAndGet()), CollectTier.ALL);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    /** 행 수를 표별로 모은 값. 재실행 전후 비교용 */
    private Map<String, Integer> counts() {
        return List.of("title", "watch_unit", "genre", "title_genre", "title_cast", "availability", "availability_change")
                .stream().collect(Collectors.toMap(table -> table, this::count));
    }

    private Map<String, Object> row(String sql) {
        return jdbcTemplate.queryForMap(sql);
    }

    private Map<String, Object> availability(long watchUnitId, String serviceCode) {
        return jdbcTemplate.queryForMap("SELECT a.* FROM availability a JOIN ott_service s ON s.id = a.ott_service_id "
                + "WHERE a.watch_unit_id = ? AND s.code = ?", watchUnitId, serviceCode);
    }

    private long unitIdOf(int tmdbId) {
        return jdbcTemplate.queryForObject("SELECT u.id FROM watch_unit u JOIN title t ON t.id = u.title_id "
                + "WHERE t.tmdb_id = ?", Long.class, tmdbId);
    }

    /** discover 응답을 등록한다. 후보 제목은 "후보 {id}"다 */
    private static void stubDiscover(String path, String withProviders, int page, int totalPages, String titleField,
                                     Object... ids) {
        String results = java.util.Arrays.stream(ids)
                .map(id -> "{\"id\":" + id + ",\"" + titleField + "\":\"후보 " + id + "\"}")
                .collect(Collectors.joining(","));
        String body = "{\"page\":" + page + ",\"results\":[" + results + "],\"total_pages\":" + totalPages
                + ",\"total_results\":" + ids.length + "}";
        wireMock.stubFor(get(urlPathEqualTo(path))
                .withQueryParam("with_watch_providers", equalTo(withProviders))
                .withQueryParam("page", equalTo(String.valueOf(page)))
                .willReturn(json(body)));
    }

    /** 영화 상세(한국어)를 등록한다. 제목은 후보와 같게 둔다. runtime이 null이면 JSON null이다 */
    private static void stubMovie(int id, String overview, Integer runtime, String genresJson, String castJson) {
        String body = "{\"id\":" + id + ",\"title\":\"후보 " + id + "\",\"original_title\":\"Original " + id
                + "\",\"overview\":\"" + overview + "\",\"release_date\":\"2024-05-01\",\"runtime\":" + runtime
                + ",\"genres\":" + genresJson + ",\"original_language\":\"ko\",\"popularity\":87.5,\"vote_average\":7.8"
                + ",\"credits\":{\"cast\":" + castJson + "}}";
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/" + id)).withQueryParam("language", equalTo("ko-KR"))
                .willReturn(json(body)));
    }

    private static void stubMovieProviders(int id, String body) {
        wireMock.stubFor(get(urlPathEqualTo("/3/movie/" + id + "/watch/providers")).willReturn(json(body)));
    }

    /** 드라마 상세를 등록한다. 줄거리는 "가짜 줄거리 A"다 */
    private static void stubTv(int id, String genresJson, String seasonsJson) {
        String body = "{\"id\":" + id + ",\"name\":\"후보 " + id + "\",\"original_name\":\"Original " + id
                + "\",\"overview\":\"가짜 줄거리 A\",\"first_air_date\":\"2023-03-01\",\"status\":\"Returning Series\""
                + ",\"genres\":" + genresJson + ",\"seasons\":" + seasonsJson + ",\"credits\":{\"cast\":" + cast(1) + "}}";
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + id)).withQueryParam("language", equalTo("ko-KR"))
                .willReturn(json(body)));
    }

    private static void stubSeason(int tvId, int seasonNumber, String name, String airDate, String episodesJson) {
        String body = "{\"id\":" + (810000 + seasonNumber) + ",\"season_number\":" + seasonNumber + ",\"name\":\"" + name
                + "\",\"air_date\":\"" + airDate + "\",\"poster_path\":\"/season-" + seasonNumber + ".jpg\",\"episodes\":"
                + episodesJson + "}";
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + tvId + "/season/" + seasonNumber))
                .withQueryParam("language", equalTo("ko-KR")).willReturn(json(body)));
    }

    private static void stubSeasonProviders(int tvId, int seasonNumber, String body) {
        wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + tvId + "/season/" + seasonNumber + "/watch/providers"))
                .willReturn(json(body)));
    }

    /** KR 제공처 응답. flatrate(구독)와 rent(대여) 제공처 번호를 받는다 */
    private static String region(int[] flatrate, int[] rent) {
        return "{\"id\":1,\"results\":{\"KR\":{\"link\":\"https://example.test\",\"flatrate\":" + providers(flatrate)
                + ",\"rent\":" + providers(rent) + "}}}";
    }

    private static String providers(int[] ids) {
        return IntStream.of(ids).mapToObj(id -> "{\"provider_id\":" + id + ",\"provider_name\":\"P" + id + "\"}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    /** 출연진 JSON n명. 일부러 order를 거꾸로 주어 정렬이 order 기준임을 확인한다 */
    private static String cast(int n) {
        return IntStream.range(0, n).mapToObj(i -> "{\"id\":" + (7000 + i) + ",\"name\":\"배우 " + i
                        + "\",\"character\":\"역할 " + i + "\",\"order\":" + i + ",\"profile_path\":\"/p" + i + ".jpg\"}")
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                    java.util.Collections.reverse(list);
                    return list.stream().collect(Collectors.joining(",", "[", "]"));
                }));
    }

    private static ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
