package com.ottnavi.plan.batch;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ottnavi.collect.job.CollectJobLauncher;
import com.ottnavi.collect.job.CollectTier;
import com.ottnavi.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * 수집 Job이 COMPLETED로 끝나면 재계산 Job이 이어서 실행 기록을 정상으로 남기는지 실제 수집 Job으로 확인한다(Docker 필요).
 * 수집의 afterJob 안에서 이벤트로 비동기 재계산을 시작하므로, 수집 실행이 DB에 COMPLETED로 저장되기 전에 재계산이 시작된다.
 * 막으려는 사고: 수집 완료 뒤 재계산이 시작되지 않는 일, afterJob 안에서 시작한 재계산 실행 기록이 깨지는 일,
 * 수집 이력의 재계산 대상 플랜 수가 비는 일.
 */
@TestPropertySource(properties = "app.plan-recalc.auto-start=true")
class PlanRecalcAfterCollectTest extends IntegrationTestSupport {

	private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

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

	@Autowired private CollectJobLauncher collectJobLauncher;
	@Autowired private PlanRecalcExecutionQuery planRecalcExecutionQuery;
	@Autowired private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void 초기화() {
		wireMock.resetAll();
		wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/movie")).willReturn(json("{\"results\":[]}")));
		wireMock.stubFor(get(urlPathEqualTo("/3/watch/providers/tv")).willReturn(json("{\"results\":[]}")));
		String emptyPage = "{\"page\":1,\"results\":[],\"total_pages\":1,\"total_results\":0}";
		wireMock.stubFor(get(urlPathEqualTo("/3/discover/movie")).willReturn(json(emptyPage)));
		wireMock.stubFor(get(urlPathEqualTo("/3/discover/tv")).willReturn(json(emptyPage)));
	}

	@Test
	void 수집이_완료되면_재계산이_이어서_COMPLETED로_기록되고_수집_이력에_대상_플랜_수가_남는다() throws Exception {
		// when: 수집을 시작해 끝날 때까지, 이어서 재계산 Job이 끝날 때까지 기다린다(날짜는 겹치지 않게 임의로 고른다)
		LocalDate day = LocalDate.of(2026, 1, 1).plusDays(System.nanoTime() % 200);
		long collectId = collectJobLauncher.start(day, CollectTier.ALL);
		Map<Long, Integer> counts = awaitRecalcCounts(collectId);

		// then: 수집은 COMPLETED이고, 그 수집이 부른 재계산이 COMPLETED로 기록되어 대상 수(READ_COUNT)를 남긴다
		String collectStatus = jdbcTemplate.queryForObject(
				"SELECT STATUS FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID = ?", String.class, collectId);
		assertThat(collectStatus).isEqualTo("COMPLETED");
		assertThat(counts).containsKey(collectId);
		String trigger = jdbcTemplate.queryForObject(
				"SELECT p.PARAMETER_VALUE FROM BATCH_JOB_EXECUTION_PARAMS p JOIN BATCH_JOB_EXECUTION e "
						+ "ON e.JOB_EXECUTION_ID = p.JOB_EXECUTION_ID JOIN BATCH_JOB_EXECUTION_PARAMS c "
						+ "ON c.JOB_EXECUTION_ID = e.JOB_EXECUTION_ID AND c.PARAMETER_NAME = 'collectExecutionId' "
						+ "WHERE p.PARAMETER_NAME = 'trigger' AND c.PARAMETER_VALUE = ?", String.class,
				String.valueOf(collectId));
		assertThat(trigger).isEqualTo("COLLECT");
	}

	/** 수집이 부른 재계산이 COMPLETED로 기록될 때까지 기다린다(최대 90초) */
	private Map<Long, Integer> awaitRecalcCounts(long collectId) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 90_000;
		while (System.currentTimeMillis() < deadline) {
			Map<Long, Integer> counts = planRecalcExecutionQuery.findTargetCountsByCollectExecutionIds(List.of(collectId));
			if (counts.containsKey(collectId)) {
				return counts;
			}
			Thread.sleep(200);
		}
		throw new IllegalStateException("재계산이 90초 안에 COMPLETED로 기록되지 않았다: collectId=" + collectId);
	}

	private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
		return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
	}
}
