package com.ottnavi.home.job;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.service.TitleCollectService;
import com.ottnavi.catalog.service.TitleDetailQueryService;
import com.ottnavi.catalog.service.TitleFillService;
import com.ottnavi.collect.step.detail.SingleTitleFetcher;
import com.ottnavi.home.service.PopularityRankingService;
import com.ottnavi.home.step.PopularityDetailFillTasklet;
import com.ottnavi.support.IntegrationTestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 인기 수집 Job의 "인기 상위 작품 상세 채우기" Step을 실제 DB·Batch와 WireMock(TMDB 대역)으로 확인한다(Docker Desktop 필요).
 * 막으려는 사고: 순위에는 보이는데 상세가 404인 작품이 남는 일, 이미 있는 작품을 TMDB에서 다시 받는 일,
 * 작품 하나의 TMDB 오류가 Job 전체나 삭제 Step을 막는 일, 상한을 넘겨 TMDB를 과하게 부르는 일, 상한이 있을 때 눈에 덜 띄는 순위부터 채우는 일.
 * 인기 목록은 기존 fixture(tmdb/discover-*.json)를 쓴다: 영화 목록은 비우고, 드라마 목록은 800001·800002다.
 * 800001은 tv-detail.json 등으로 상세가 있고, 800002는 상세 스텁이 없어 TMDB 404다. 시즌 날짜는 모두 과거다.
 */
class PopularityDetailFillIntegrationTest extends IntegrationTestSupport {

	private static final int TV_ID = 800001;
	private static final int TV_MISSING_ID = 800002;
	private static final int MOVIE_ID = 900001;
	private static final String EMPTY_PAGE = "{\"page\":1,\"results\":[],\"total_pages\":0,\"total_results\":0}";

	private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

	static {
		wireMock.start();
	}

	@DynamicPropertySource
	static void tmdbProperties(DynamicPropertyRegistry registry) {
		registry.add("app.tmdb.base-url", () -> wireMock.baseUrl() + "/3");
		registry.add("app.tmdb.api-token", () -> "test-token");
		registry.add("app.tmdb.retry.initial-backoff", () -> "10ms"); // 5xx 재시도가 오래 걸리지 않게 대기를 줄인다
		registry.add("app.tmdb.retry.max-backoff", () -> "40ms");
		registry.add("app.tmdb.retry.max-attempts", () -> "2");
	}

	@AfterAll
	static void stopWireMock() {
		wireMock.stop();
	}

	@Autowired private PopularityLauncher popularityLauncher;
	@Autowired private TitleDetailQueryService titleDetailQueryService;
	@Autowired private PopularityRankingService popularityRankingService;
	@Autowired private TitleCollectService titleCollectService;
	@Autowired private TitleFillService titleFillService;
	@Autowired private SingleTitleFetcher singleTitleFetcher;
	@Autowired private Clock clock;
	@Autowired private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void 초기화() throws IOException {
		cleanUp();
		wireMock.resetAll();
		wireMock.stubFor(get(urlPathEqualTo("/3/discover/movie")).willReturn(json(EMPTY_PAGE)));
		wireMock.stubFor(get(urlPathEqualTo("/3/discover/tv")).willReturn(json(fixture("tmdb/discover-tv.json"))));
	}

	@AfterEach
	void 정리() {
		cleanUp();
	}

	private void cleanUp() {
		String titleIds = "(SELECT id FROM title WHERE tmdb_id IN (" + TV_ID + ", " + TV_MISSING_ID + ", " + MOVIE_ID + ", 900002))";
		String unitIds = "(SELECT id FROM watch_unit WHERE title_id IN " + titleIds + ")";
		jdbcTemplate.update("DELETE FROM availability_change WHERE watch_unit_id IN " + unitIds);
		jdbcTemplate.update("DELETE FROM availability WHERE watch_unit_id IN " + unitIds);
		jdbcTemplate.update("DELETE FROM watch_unit WHERE title_id IN " + titleIds);
		jdbcTemplate.update("DELETE FROM title_cast WHERE title_id IN " + titleIds);
		jdbcTemplate.update("DELETE FROM title_genre WHERE title_id IN " + titleIds);
		jdbcTemplate.update("DELETE FROM title WHERE tmdb_id IN (" + TV_ID + ", " + TV_MISSING_ID + ", " + MOVIE_ID + ", 900002)");
		jdbcTemplate.update("DELETE FROM popularity_snapshot");
	}

	@Test
	void 인기_상위에_있고_title에_없는_드라마가_시즌과_제공_상태까지_저장되고_Job은_삭제_Step까지_완료한다() throws Exception {
		// given
		stubTvDetail();

		// when
		long executionId = popularityLauncher.start();
		awaitFinished(executionId);

		// then
		assertThat(jobStatus(executionId)).isEqualTo("COMPLETED");
		assertThat(stepStatuses(executionId)).containsExactly("popularityCollectStep=COMPLETED",
				"popularityDetailFillStep=COMPLETED", "popularityPurgeStep=COMPLETED");
		assertThat(count("SELECT count(*) FROM title WHERE media_type = 'TV' AND tmdb_id = " + TV_ID)).isEqualTo(1);
		assertThat(count("SELECT count(*) FROM watch_unit u JOIN title t ON t.id = u.title_id WHERE t.tmdb_id = " + TV_ID))
				.isEqualTo(2);
		assertThat(count("SELECT count(*) FROM availability a JOIN watch_unit u ON u.id = a.watch_unit_id "
				+ "JOIN title t ON t.id = u.title_id WHERE t.tmdb_id = " + TV_ID)).isEqualTo(14);
	}

	@Test
	void 채운_작품은_상세_조회가_예외_없이_응답한다() throws Exception {
		// given
		stubTvDetail();

		// when
		awaitFinished(popularityLauncher.start());

		// then
		assertThat(titleDetailQueryService.getDetail(MediaType.TV, TV_ID).tmdbId()).isEqualTo(TV_ID);
	}

	@Test
	void 이미_title에_있는_작품은_TMDB_상세와_제공처를_부르지_않는다() throws Exception {
		// given: 800001이 이미 title에 있다
		stubTvDetail();
		jdbcTemplate.update("INSERT INTO title (media_type, tmdb_id, title, fetched_at) VALUES ('TV', ?, '이미 있음', now())", TV_ID);

		// when
		awaitFinished(popularityLauncher.start());

		// then: 800001 관련 요청이 0회, 제목도 그대로다
		wireMock.verify(0, getRequestedFor(urlPathMatching("/3/tv/" + TV_ID + ".*")));
		assertThat(jdbcTemplate.queryForObject("SELECT title FROM title WHERE tmdb_id = ?", String.class, TV_ID))
				.isEqualTo("이미 있음");
	}

	@Test
	void 한_작품의_상세가_404이면_그_작품만_건너뛰고_다른_작품은_채운다() throws Exception {
		// given: 800002는 상세 스텁이 없어 404
		stubTvDetail();

		// when
		long executionId = popularityLauncher.start();
		awaitFinished(executionId);

		// then
		assertThat(jobStatus(executionId)).isEqualTo("COMPLETED");
		assertThat(count("SELECT count(*) FROM title WHERE tmdb_id = " + TV_MISSING_ID)).isZero();
		assertThat(count("SELECT count(*) FROM title WHERE tmdb_id = " + TV_ID)).isEqualTo(1);
	}

	@Test
	void TMDB_상세가_계속_5xx여도_인기_수집_Job은_완료되고_삭제_Step까지_돈다() throws Exception {
		// given
		wireMock.stubFor(get(urlPathMatching("/3/tv/.*")).willReturn(aResponse().withStatus(500)));

		// when
		long executionId = popularityLauncher.start();
		awaitFinished(executionId);

		// then
		assertThat(jobStatus(executionId)).isEqualTo("COMPLETED");
		assertThat(stepStatuses(executionId)).containsExactly("popularityCollectStep=COMPLETED",
				"popularityDetailFillStep=COMPLETED", "popularityPurgeStep=COMPLETED");
		assertThat(count("SELECT count(*) FROM title WHERE tmdb_id IN (" + TV_ID + ", " + TV_MISSING_ID + ")")).isZero();
	}

	@Test
	void 상한이_1이면_전체_오늘_순위의_1위부터_채우고_서비스_순위의_작품은_받지_않는다() throws Exception {
		// given: 첫 서비스에는 영화 900001(인기도 10), 마지막 서비스에는 드라마 800001(인기도 50)이 있다.
		//        전체 순위의 1위는 드라마 800001이다. 서비스 순서로 훑으면 영화 900001이 먼저다
		List<Long> serviceIds = jdbcTemplate.queryForList("SELECT id FROM ott_service ORDER BY display_order", Long.class);
		insertSnapshot(serviceIds.get(0), "MOVIE", MOVIE_ID, "10");
		insertSnapshot(serviceIds.get(serviceIds.size() - 1), "TV", TV_ID, "50");
		stubTvDetail();
		PopularityFillProperties limited = new PopularityFillProperties(true, 20, 1);
		PopularityDetailFillTasklet tasklet = new PopularityDetailFillTasklet(limited, popularityRankingService,
				titleCollectService, titleFillService, singleTitleFetcher, clock);

		// when
		tasklet.execute(mock(StepContribution.class), null);

		// then: 드라마만 채워지고 영화 상세는 한 번도 부르지 않는다
		assertThat(count("SELECT count(*) FROM title WHERE tmdb_id = " + TV_ID)).isEqualTo(1);
		assertThat(count("SELECT count(*) FROM title WHERE tmdb_id = " + MOVIE_ID)).isZero();
		wireMock.verify(0, getRequestedFor(urlPathMatching("/3/movie/.*")));
	}

	// 보조

	private void insertSnapshot(long ottServiceId, String mediaType, int tmdbId, String popularity) {
		jdbcTemplate.update("INSERT INTO popularity_snapshot (snapshot_date, ott_service_id, media_type, tmdb_id, title, popularity) "
				+ "VALUES (current_date, ?, ?, ?, '테스트', ?::numeric)", ottServiceId, mediaType, tmdbId, popularity);
	}

	/** 드라마 800001의 상세·시즌 1·2·시즌 제공처를 등록한다(시즌 2는 시즌 단위 KR이 비어 작품 단위로 대신 판정) */
	private void stubTvDetail() throws IOException {
		wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + TV_ID)).withQueryParam("language", equalTo("ko-KR"))
				.willReturn(json(fixture("tmdb/tv-detail.json"))));
		wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + TV_ID + "/season/1")).willReturn(json(fixture("tmdb/tv-season.json"))));
		String season2 = "{\"id\":810002,\"season_number\":2,\"name\":\"시즌 2\",\"air_date\":\"2024-03-01\","
				+ "\"episodes\":[{\"episode_number\":1,\"air_date\":\"2024-03-01\",\"runtime\":60}]}";
		wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + TV_ID + "/season/2")).willReturn(json(season2)));
		String kr337 = "{\"id\":1,\"results\":{\"KR\":{\"flatrate\":[{\"provider_id\":337,\"provider_name\":\"P\"}]}}}";
		wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + TV_ID + "/season/1/watch/providers")).willReturn(json(kr337)));
		wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + TV_ID + "/season/2/watch/providers")).willReturn(json("{\"id\":1,\"results\":{}}")));
		wireMock.stubFor(get(urlPathEqualTo("/3/tv/" + TV_ID + "/watch/providers")).willReturn(json(kr337)));
	}

	private long count(String sql) {
		return jdbcTemplate.queryForObject(sql, Long.class);
	}

	private String jobStatus(long executionId) {
		return jdbcTemplate.queryForObject("SELECT STATUS FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID = ?", String.class, executionId);
	}

	/** 인기 수집 실행이 끝날 때까지 기다린다(최대 60초) */
	private void awaitFinished(long executionId) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 60_000;
		while (System.currentTimeMillis() < deadline) {
			String status = jobStatus(executionId);
			if (!"STARTING".equals(status) && !"STARTED".equals(status)) {
				return;
			}
			Thread.sleep(100);
		}
		throw new IllegalStateException("인기 수집이 60초 안에 끝나지 않았다");
	}

	private List<String> stepStatuses(long executionId) {
		return jdbcTemplate.queryForList("SELECT STEP_NAME || '=' || STATUS FROM BATCH_STEP_EXECUTION "
				+ "WHERE JOB_EXECUTION_ID = ? ORDER BY STEP_EXECUTION_ID", String.class, executionId);
	}

	private static String fixture(String path) throws IOException {
		return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
	}

	private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
		return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
	}
}
