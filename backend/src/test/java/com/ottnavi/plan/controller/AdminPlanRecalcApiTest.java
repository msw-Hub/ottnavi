package com.ottnavi.plan.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.plan.batch.PlanRecalcLauncher;
import com.ottnavi.plan.exception.PlanRecalcAlreadyRunningException;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 재계산 수동 실행 API(Task 069)의 응답과 접근 제어를 확인한다. 시작 자체는 목으로 바꿔 응답 규칙만 본다.
 * 막으려는 사고: 일반 사용자가 재계산을 시작하는 일, 실행 중인데 409가 아닌 응답이 나가는 일, 배치 토큰이 거절되는 일.
 */
@AutoConfigureMockMvc
class AdminPlanRecalcApiTest extends IntegrationTestSupport {

	private static final String PATH = "/api/admin/plans/monthly/executions";

	@Autowired private MockMvc mockMvc;
	@Autowired private JwtTokenProvider jwtTokenProvider;
	@Value("${app.admin-batch-token}") private String batchToken;
	@MockitoBean private PlanRecalcLauncher planRecalcLauncher;

	@Test
	void 관리자와_배치_토큰은_202와_실행_ID를_받고_실행_중이면_409이며_일반_사용자는_403이다() throws Exception {
		TestTokens tokens = new TestTokens(jwtTokenProvider);

		// 관리자: 202와 새 실행 ID
		when(planRecalcLauncher.startManual()).thenReturn(14L);
		mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, tokens.adminBearer()))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.data.executionId").value(14));

		// 배치 토큰: 같은 경로를 쓸 수 있다
		mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + batchToken))
				.andExpect(status().isAccepted());

		// 이미 실행 중이면 409 BATCH_ALREADY_RUNNING
		when(planRecalcLauncher.startManual()).thenThrow(new PlanRecalcAlreadyRunningException("재계산이 이미 실행 중입니다."));
		mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, tokens.adminBearer()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("BATCH_ALREADY_RUNNING"));

		// 일반 사용자: 403
		mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, tokens.userBearer()))
				.andExpect(status().isForbidden());
	}
}
