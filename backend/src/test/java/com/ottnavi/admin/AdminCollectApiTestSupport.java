package com.ottnavi.admin;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.jayway.jsonpath.JsonPath;
import com.ottnavi.collect.job.CollectExecutionQuery;
import com.ottnavi.collect.job.CollectExecutionRecord;
import com.ottnavi.support.CollectAwait;
import com.ottnavi.support.IntegrationTestSupport;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Task 045 관리자 수집 API 통합 시험의 공통 틀(Docker 필요).
 * 가짜 TMDB(WireMock)는 JVM에서 한 대만 띄워 하위 시험 클래스가 함께 쓴다.
 * 비동기 실행이 시험 밖으로 새지 않도록 시험 앞뒤로 실행 중인 수집이 없어질 때까지 기다린다.
 */
@AutoConfigureMockMvc
public abstract class AdminCollectApiTestSupport extends IntegrationTestSupport {

	protected static final String EXECUTIONS_PATH = "/api/admin/collect/executions";
	protected static final String EMPTY_PAGE = "{\"page\":1,\"results\":[],\"total_pages\":1,\"total_results\":0}";

	protected static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

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

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	@Autowired
	protected CollectExecutionQuery collectExecutionQuery;

	@Value("${app.admin-batch-token}")
	protected String batchToken;

	@BeforeEach
	void 초기화() throws Exception {
		// 앞 시험의 비동기 실행이 남아 있지 않게 한 뒤 Batch 메타데이터와 수집 결과를 비운다
		awaitNoLiveExecutions();
		wireMock.resetAll();
		for (String table : List.of("BATCH_STEP_EXECUTION_CONTEXT", "BATCH_STEP_EXECUTION",
				"BATCH_JOB_EXECUTION_CONTEXT", "BATCH_JOB_EXECUTION_PARAMS", "BATCH_JOB_EXECUTION",
				"BATCH_JOB_INSTANCE")) {
			jdbcTemplate.update("DELETE FROM " + table);
		}
		deleteCatalogData();
		jdbcTemplate.update("DELETE FROM tmdb_provider WHERE mapping_state <> 'MAPPED'");
		stubSuccessfulCollection(0);
	}

	@AfterEach
	void 정리() throws Exception {
		awaitNoLiveExecutions();
		deleteCatalogData();
	}

	/** 수집·시험이 만든 카탈로그 데이터를 FK 순서(자식 → 부모)대로 지운다. 부모보다 자식을 먼저 지우지 않으면 FK 위반이 난다 */
	private void deleteCatalogData() {
		for (String table : List.of("availability_change", "availability", "watch_unit", "title_cast", "title_genre",
				"title", "genre")) {
			jdbcTemplate.update("DELETE FROM " + table);
		}
	}

	/** 빈 응답으로 끝까지 성공하는 TMDB 응답을 등록한다. providersDelayMillis만큼 제공처 목록 응답을 늦춘다 */
	protected static void stubSuccessfulCollection(int providersDelayMillis) {
		wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/movie"))
				.willReturn(json("{\"results\":[]}").withFixedDelay(providersDelayMillis)));
		wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/tv")).willReturn(json("{\"results\":[]}")));
		wireMock.stubFor(get(urlPathEqualTo("/3/discover/movie")).atPriority(10).willReturn(json(EMPTY_PAGE)));
		wireMock.stubFor(get(urlPathEqualTo("/3/discover/tv")).atPriority(10).willReturn(json(EMPTY_PAGE)));
	}

	/** 제공처 목록이 500이라 수집이 첫 Step에서 실패하게 한다(재시도 뒤에도 실패) */
	protected static void stubFailingCollection() {
		wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/movie")).atPriority(1)
				.willReturn(aResponse().withStatus(500)));
	}

	protected static ResponseDefinitionBuilder json(String body) {
		return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
	}

	protected static String createBody(String targetDate, String tier, String scope) {
		String base = createBody(targetDate, tier);
		if (scope == null) {
			return base;
		}
		return base.substring(0, base.length() - 1) + ",\"scope\":\"" + scope + "\"}";
	}

	protected static String createBody(String targetDate, String tier) {
		StringBuilder body = new StringBuilder("{");
		if (targetDate != null) {
			body.append("\"targetDate\":\"").append(targetDate).append("\"");
		}
		if (tier != null) {
			if (targetDate != null) {
				body.append(",");
			}
			body.append("\"tier\":\"").append(tier).append("\"");
		}
		body.append("}");
		return body.toString();
	}

	/** 관리자 배치 토큰을 붙여 수집 실행 요청을 보낸다 */
	protected ResultActions postCreate(String body) throws Exception {
		return mockMvc.perform(post(EXECUTIONS_PATH)
				.header("Authorization", "Bearer " + batchToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	/** 관리자 배치 토큰을 붙여 본문 없는 하위 동작(recover·restart)을 요청한다 */
	protected ResultActions postAction(long executionId, String action) throws Exception {
		return postAction(String.valueOf(executionId), action);
	}

	protected ResultActions postAction(String executionId, String action) throws Exception {
		return mockMvc.perform(post(EXECUTIONS_PATH + "/" + executionId + "/" + action)
				.header("Authorization", "Bearer " + batchToken));
	}

	/** 202 응답 본문에서 data.executionId를 꺼낸다 */
	protected static long executionIdOf(ResultActions result) throws Exception {
		String content = result.andReturn().getResponse().getContentAsString();
		Number id = JsonPath.read(content, "$.data.executionId");
		return id.longValue();
	}

	/**
	 * 수집이 끝날 때까지 기다린다(100ms 간격, 최대 30초). 종료 시각이 기록되고 Job 스레드가 끝난 뒤에 돌아온다.
	 * END_TIME이 보여도 Job 스레드가 afterJob 뒤 마지막 저장을 한 번 더 하므로 스레드가 끝날 때까지 기다려야 한다
	 * (그 저장은 synchronizeStatus가 버전을 맞춰 덮어쓰므로, 그 전에 DB를 고치면 되돌려진다)
	 */
	protected void awaitFinished(long executionId) throws Exception {
		CollectAwait.until(() -> {
			Optional<CollectExecutionRecord> found = collectExecutionQuery.findById(executionId);
			if (found.isEmpty()) {
				return false;
			}
			String status = found.get().batchStatus().name();
			boolean isRunning = "STARTING".equals(status) || "STARTED".equals(status) || "STOPPING".equals(status);
			return !isRunning && found.get().endTime() != null;
		}, 30_000, "수집이 끝나지 않았다: " + executionId);
	}

	/**
	 * 실행 중인 수집이 없어질 때까지 기다린다. markAsCrashed로 흉내 낸 실행(생성 시각 2000년)은 세지 않는다.
	 * 상태가 끝났어도 Job 스레드가 마지막 저장을 하는 중일 수 있어 스레드 종료까지 함께 기다린다
	 * (시험 사이 DELETE와 그 저장이 겹치지 않게 한다)
	 */
	protected void awaitNoLiveExecutions() throws Exception {
		CollectAwait.until(() -> {
			Integer live = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM BATCH_JOB_EXECUTION "
					+ "WHERE STATUS IN ('STARTING', 'STARTED', 'STOPPING') AND CREATE_TIME > '2001-01-01'",
					Integer.class);
			return live != null && live == 0;
		}, 30_000, "실행 중인 수집이 끝나지 않았다");
	}

	/**
	 * JVM이 죽어 STARTED로 남은 실행을 흉내 낸다: 실행과 마지막 Step을 실행 중 상태로 되돌리고 생성 시각을 과거로 둔다.
	 * 호출하기 전에 awaitFinished로 Job 스레드가 끝나길 기다려야 한다. 스레드가 남아 있으면 마지막 저장이
	 * 버전을 맞춰 이 값을 COMPLETED로 덮어쓴다(VERSION을 올려도 막히지 않는다. VERSION 증가는 해가 없어 그대로 둔다).
	 */
	protected void markAsCrashed(long executionId) {
		jdbcTemplate.update("UPDATE BATCH_JOB_EXECUTION SET STATUS = 'STARTED', END_TIME = NULL, "
				+ "EXIT_CODE = 'UNKNOWN', CREATE_TIME = '2000-01-01', VERSION = VERSION + 1 "
				+ "WHERE JOB_EXECUTION_ID = ?", executionId);
		jdbcTemplate.update("UPDATE BATCH_STEP_EXECUTION SET STATUS = 'STARTED', END_TIME = NULL, "
				+ "EXIT_CODE = 'UNKNOWN' WHERE STEP_EXECUTION_ID = (SELECT MAX(STEP_EXECUTION_ID) "
				+ "FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID = ?)", executionId);
	}
}
