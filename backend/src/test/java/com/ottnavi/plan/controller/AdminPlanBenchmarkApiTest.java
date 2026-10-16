package com.ottnavi.plan.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.support.TestTokens;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 완전탐색·그리디 비교 측정 API(Task 065) 통합 시험(Docker 필요).
 * 막으려는 사고: 케이스마다 EXACT·GREEDY 두 행이 같은 input_hash로 남지 않는 일, 응답 id와 DB 행이 어긋나는 일,
 * 재실행에서 유니크 충돌이 나는 일, ADMIN이 아닌 호출이 통과하는 일.
 */
@AutoConfigureMockMvc
class AdminPlanBenchmarkApiTest extends IntegrationTestSupport {

	private static final String URL = "/api/admin/plan-benchmarks";

	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private JwtTokenProvider jwtTokenProvider;

	private long baseCalcRunId; // 시험 시작 시점의 calc_run 최대 id. 이후에 생긴 행만 지운다

	@BeforeEach
	void 기준_id를_기록한다() {
		baseCalcRunId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM calc_run", Long.class);
	}

	@AfterEach
	void 시험이_만든_기록을_지운다() {
		jdbcTemplate.update("DELETE FROM calc_run WHERE id > ?", baseCalcRunId);
	}

	private long count() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM calc_run WHERE id > ?", Long.class, baseCalcRunId);
	}

	@Test
	void ADMIN이_호출하면_케이스마다_두_행이_저장되고_재실행하면_행이_더_쌓인다() throws Exception {
		// given
		String admin = new TestTokens(jwtTokenProvider).adminBearer();

		// when
		String body = mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, admin))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.caseCount").value(21))
				.andExpect(jsonPath("$.data.measuredRuns").value(3))
				.andExpect(jsonPath("$.data.warmupRuns").value(1))
				.andExpect(jsonPath("$.data.skippedCaseNames").isEmpty())
				.andReturn().getResponse().getContentAsString();

		// then
		// 1. 케이스 21건, 42행 증가
		List<Map<String, Object>> cases = JsonPath.read(body, "$.data.cases");
		assertThat(cases).hasSize(21);
		assertThat(count()).isEqualTo(42);

		// 2. 케이스마다 같은 input_hash의 EXACT·GREEDY 행이 있고 응답 id·값이 DB와 같다
		boolean case07Checked = false;
		for (int i = 0; i < cases.size(); i++) {
			String prefix = "$.data.cases[" + i + "]";
			String inputHash = JsonPath.read(body, prefix + ".inputHash");
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(
					"SELECT id, algorithm, plan_id, plan_type, must_completed, score, total_cost, candidate_count, elapsed_ms "
							+ "FROM calc_run WHERE id > ? AND input_hash = ? ORDER BY algorithm", baseCalcRunId, inputHash);
			assertThat(rows).hasSize(2);
			Map<String, Object> exactRow = rows.get(0); // EXACT < GREEDY
			Map<String, Object> greedyRow = rows.get(1);
			assertThat(exactRow.get("algorithm")).isEqualTo("EXACT");
			assertThat(greedyRow.get("algorithm")).isEqualTo("GREEDY");
			for (Map<String, Object> row : rows) {
				assertThat(row.get("plan_id")).isNull();
				assertThat(row.get("plan_type")).isEqualTo("RECOMMENDED");
			}
			assertThat(((Number) exactRow.get("id")).longValue())
					.isEqualTo(((Number) JsonPath.read(body, prefix + ".exact.calcRunId")).longValue());
			assertThat(((Number) greedyRow.get("id")).longValue())
					.isEqualTo(((Number) JsonPath.read(body, prefix + ".greedy.calcRunId")).longValue());
			assertThat(exactRow.get("candidate_count")).isEqualTo(greedyRow.get("candidate_count"));
			assertThat((Boolean) JsonPath.read(body, prefix + ".exact.matchesExpected")).isTrue();
			assertThat((Boolean) JsonPath.read(body, prefix + ".greedy.matchesExpected")).isTrue();
			// 3. 케이스 07은 그리디가 완전탐색보다 점수가 낮다
			String caseName = JsonPath.read(body, prefix + ".caseName");
			if (caseName.startsWith("case-07")) {
				int scoreDiff = JsonPath.read(body, prefix + ".scoreDiff");
				assertThat(scoreDiff).isNegative();
				case07Checked = true;
			}
		}
		assertThat(case07Checked).isTrue();

		// 4. 재실행해도 오류 없이 행이 더 쌓인다
		mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isCreated());
		assertThat(count()).isEqualTo(84);
	}

	@Test
	void USER는_403이고_토큰이_없으면_401이다() throws Exception {
		mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, new TestTokens(jwtTokenProvider).userBearer()))
				.andExpect(status().isForbidden());
		mockMvc.perform(post(URL)).andExpect(status().isUnauthorized());
		assertThat(count()).isZero();
	}
}
