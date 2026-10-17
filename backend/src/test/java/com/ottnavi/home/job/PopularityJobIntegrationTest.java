package com.ottnavi.home.job;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ottnavi.admin.exception.BatchAlreadyRunningException;
import com.ottnavi.admin.service.CollectExecutionService;
import com.ottnavi.collect.job.CollectTier;
import com.ottnavi.home.exception.PopularityAlreadyRunningException;
import com.ottnavi.home.service.PopularityCollectService;
import com.ottnavi.plan.batch.PlanRecalcLauncher;
import com.ottnavi.plan.exception.PlanRecalcAlreadyRunningException;
import com.ottnavi.retention.exception.RetentionAlreadyRunningException;
import com.ottnavi.retention.job.RetentionJobConfig;
import com.ottnavi.retention.job.RetentionLauncher;
import com.ottnavi.support.IntegrationTestSupport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 인기 순위 수집 Job(Task 107)을 실제 DB·Batch와 WireMock(TMDB 대역)으로 확인한다(Docker Desktop 필요).
 * 막으려는 사고: 서비스 7곳 × 영화·드라마 스냅샷이 빠지는 일, 같은 날 재실행이 행을 중복으로 쌓는 일, 한 자리 실패가 전체를 막는 일,
 * 보관 기간 정리가 최근 날짜까지 지우는 일, 수집·재계산·정리·인기 수집이 겹쳐 도는 일(네 방향 모두).
 */
class PopularityJobIntegrationTest extends IntegrationTestSupport {

	private static final long ID_BASE = 9_200_000L; // 실제 Batch 시퀀스와 겹치지 않는 ID 대역
	private static final String COLLECT_JOB = "collectJob";
	private static final String RECALC_JOB = "planRecalcJob";
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	// 이 클래스만 쓰는 TMDB 대역 서버(base-url이 달라 컨텍스트가 따로 만들어진다)
	private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

	static {
		wireMock.start();
	}

	@DynamicPropertySource
	static void tmdbProperties(DynamicPropertyRegistry registry) {
		registry.add("app.tmdb.base-url", () -> wireMock.baseUrl() + "/3");
		registry.add("app.tmdb.retry.initial-backoff", () -> "10ms"); // 실패 시나리오가 느려지지 않게 대기를 줄인다
		registry.add("app.tmdb.retry.max-backoff", () -> "40ms");
	}

	@AfterAll
	static void stopWireMock() {
		wireMock.stop();
	}

	@Autowired private PopularityLauncher popularityLauncher;
	@Autowired private PopularityCollectService popularityCollectService;
	@Autowired private RetentionLauncher retentionLauncher;
	@Autowired private PlanRecalcLauncher planRecalcLauncher;
	@Autowired private CollectExecutionService collectExecutionService;
	@Autowired private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void 초기화() {
		cleanUp();
		wireMock.resetAll();
		wireMock.stubFor(get(urlPathEqualTo("/3/discover/movie")).willReturn(okJson("tmdb/discover-movie.json")));
		wireMock.stubFor(get(urlPathEqualTo("/3/discover/tv")).willReturn(okJson("tmdb/discover-tv.json")));
	}

	@AfterEach
	void 정리() {
		cleanUp();
	}

	private void cleanUp() {
		jdbcTemplate.update("DELETE FROM popularity_snapshot");
		jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID >= " + ID_BASE
				+ " AND JOB_EXECUTION_ID < " + (ID_BASE + 1000));
		jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID >= " + ID_BASE
				+ " AND JOB_EXECUTION_ID < " + (ID_BASE + 1000));
		jdbcTemplate.update("DELETE FROM BATCH_JOB_INSTANCE WHERE JOB_INSTANCE_ID >= " + ID_BASE
				+ " AND JOB_INSTANCE_ID < " + (ID_BASE + 1000));
	}

	// 수집·저장

	@Test
	void 인기_수집_Job은_서비스_7곳_영화_드라마를_오늘_날짜로_저장한다() throws Exception {
		// when
		long executionId = popularityLauncher.start();
		awaitFinished(executionId);

		// then: 서비스 7곳 × (영화 2건 + 드라마 2건) = 28행, 두 Step 모두 완료
		assertThat(stepStatuses(executionId)).containsExactly("popularityCollectStep=COMPLETED", "popularityPurgeStep=COMPLETED");
		assertThat(countRows()).isEqualTo(28);
		Integer todayRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM popularity_snapshot WHERE snapshot_date = ?",
				Integer.class, LocalDate.now(KST));
		assertThat(todayRows).isEqualTo(28);
		String title = jdbcTemplate.queryForObject("SELECT title FROM popularity_snapshot WHERE tmdb_id = 900001 "
				+ "AND media_type = 'MOVIE' LIMIT 1", String.class);
		assertThat(title).isEqualTo("테스트 영화 1");
	}

	@Test
	void 같은_날_다시_실행해도_행이_중복되지_않는다() throws Exception {
		// given
		awaitFinished(popularityLauncher.start());

		// when
		long secondExecutionId = popularityLauncher.start();
		awaitFinished(secondExecutionId);

		// then
		assertThat(stepStatuses(secondExecutionId)).containsExactly("popularityCollectStep=COMPLETED", "popularityPurgeStep=COMPLETED");
		assertThat(countRows()).isEqualTo(28);
	}

	@Test
	void 한_자리의_TMDB_호출이_실패해도_나머지는_저장되고_Job은_완료된다() throws Exception {
		// given: 디즈니+(제공처 337)의 드라마 호출만 서버 오류
		wireMock.stubFor(get(urlPathEqualTo("/3/discover/tv")).withQueryParam("with_watch_providers", equalTo("337"))
				.willReturn(aResponse().withStatus(500)));

		// when
		long executionId = popularityLauncher.start();
		awaitFinished(executionId);

		// then: 28행에서 디즈니+ 드라마 2행만 빠진다
		assertThat(stepStatuses(executionId)).containsExactly("popularityCollectStep=COMPLETED", "popularityPurgeStep=COMPLETED");
		assertThat(countRows()).isEqualTo(26);
	}

	// 보관 기간 정리

	@Test
	void 보관_기간이_지난_날짜만_지우고_가장_오래된_보관_날짜는_남긴다() {
		// given: 보관 14일이면 오늘 포함 14개 날짜(오늘-13일까지)가 남는다
		LocalDate today = LocalDate.now(KST);
		insertSnapshot(today.minusDays(14), 1);
		insertSnapshot(today.minusDays(13), 2);

		// when
		int deleted = popularityCollectService.purgeOld();

		// then
		assertThat(deleted).isEqualTo(1);
		List<Integer> remaining = jdbcTemplate.queryForList("SELECT tmdb_id FROM popularity_snapshot", Integer.class);
		assertThat(remaining).containsExactly(2);
	}

	// 동시 실행 제한(수집·재계산·정리·인기 수집이 서로 막는다)

	@Test
	void 수집이_실행_중이면_인기_수집_시작은_409다() {
		// given
		insertRunning(1, COLLECT_JOB);

		// when, then
		assertThatThrownBy(() -> popularityLauncher.start()).isInstanceOf(PopularityAlreadyRunningException.class);
	}

	@Test
	void 재계산이_실행_중이면_인기_수집_시작은_409다() {
		// given
		insertRunning(1, RECALC_JOB);

		// when, then
		assertThatThrownBy(() -> popularityLauncher.start()).isInstanceOf(PopularityAlreadyRunningException.class);
	}

	@Test
	void 정리가_실행_중이면_인기_수집_시작은_409다() {
		// given
		insertRunning(1, RetentionJobConfig.JOB_NAME);

		// when, then
		assertThatThrownBy(() -> popularityLauncher.start()).isInstanceOf(PopularityAlreadyRunningException.class);
	}

	@Test
	void 인기_수집이_이미_실행_중이면_인기_수집_시작은_409다() {
		// given
		insertRunning(1, PopularityJobConfig.JOB_NAME);

		// when, then
		assertThatThrownBy(() -> popularityLauncher.start()).isInstanceOf(PopularityAlreadyRunningException.class);
	}

	@Test
	void 인기_수집이_실행_중이면_수집_재계산_정리_시작은_409다() {
		// given
		insertRunning(1, PopularityJobConfig.JOB_NAME);

		// when, then
		assertThatThrownBy(() -> collectExecutionService.start(null, CollectTier.ALL, null))
				.isInstanceOf(BatchAlreadyRunningException.class);
		assertThatThrownBy(() -> planRecalcLauncher.startManual()).isInstanceOf(PlanRecalcAlreadyRunningException.class);
		assertThatThrownBy(() -> retentionLauncher.start()).isInstanceOf(RetentionAlreadyRunningException.class);
	}

	// 데이터 준비·조회 보조

	private int countRows() {
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM popularity_snapshot", Integer.class);
		if (count == null) {
			return 0;
		}
		return count;
	}

	/** 넷플릭스(서비스 코드로 찾음)의 영화 스냅샷 한 행을 직접 넣는다 */
	private void insertSnapshot(LocalDate snapshotDate, int tmdbId) {
		jdbcTemplate.update("INSERT INTO popularity_snapshot (snapshot_date, ott_service_id, media_type, tmdb_id, title, popularity) "
				+ "SELECT ?, id, 'MOVIE', ?, '테스트', 10.0 FROM ott_service WHERE code = 'NETFLIX'", snapshotDate, tmdbId);
	}

	/** 실행 중(STARTED, 서버 기동 이후에 만들어짐)인 Job을 직접 넣는다. 수집은 조회가 요구하는 파라미터도 넣는다 */
	private void insertRunning(int offset, String jobName) {
		jdbcTemplate.update("INSERT INTO BATCH_JOB_INSTANCE (JOB_INSTANCE_ID, VERSION, JOB_NAME, JOB_KEY) VALUES (?, 0, ?, ?)",
				ID_BASE + offset, jobName, "popularity-test-" + offset);
		jdbcTemplate.update("INSERT INTO BATCH_JOB_EXECUTION (JOB_EXECUTION_ID, VERSION, JOB_INSTANCE_ID, CREATE_TIME, STATUS) "
				+ "VALUES (?, 0, ?, ?, 'STARTED')", ID_BASE + offset, ID_BASE + offset, LocalDateTime.now().plusSeconds(1));
		if (COLLECT_JOB.equals(jobName)) {
			insertParam(offset, "targetDate", "java.time.LocalDate", "2099-01-01");
			insertParam(offset, "tier", "java.lang.String", "ALL");
		}
	}

	private void insertParam(int offset, String name, String type, String value) {
		jdbcTemplate.update("INSERT INTO BATCH_JOB_EXECUTION_PARAMS (JOB_EXECUTION_ID, PARAMETER_NAME, PARAMETER_TYPE, "
				+ "PARAMETER_VALUE, IDENTIFYING) VALUES (?, ?, ?, ?, 'Y')", ID_BASE + offset, name, type, value);
	}

	/** 인기 수집 실행이 끝날 때까지 기다린다(최대 60초) */
	private void awaitFinished(long executionId) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 60_000;
		while (System.currentTimeMillis() < deadline) {
			String status = jdbcTemplate.queryForObject("SELECT STATUS FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID = ?",
					String.class, executionId);
			if (!"STARTING".equals(status) && !"STARTED".equals(status)) {
				return;
			}
			Thread.sleep(100);
		}
		throw new IllegalStateException("인기 수집이 60초 안에 끝나지 않았다");
	}

	/** 실행한 Step을 시작 순서대로 "이름=상태"로 돌려준다 */
	private List<String> stepStatuses(long executionId) {
		return jdbcTemplate.queryForList("SELECT STEP_NAME || '=' || STATUS FROM BATCH_STEP_EXECUTION "
				+ "WHERE JOB_EXECUTION_ID = ? ORDER BY STEP_EXECUTION_ID", String.class, executionId);
	}

	/** src/test/resources 의 샘플 JSON을 읽어 200 응답으로 만든다 */
	private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder okJson(String path) {
		try {
			String body = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
			return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
		} catch (IOException e) {
			throw new UncheckedIOException("샘플 JSON을 읽지 못했다: " + path, e);
		}
	}
}
