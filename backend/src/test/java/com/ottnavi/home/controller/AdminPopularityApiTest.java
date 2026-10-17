package com.ottnavi.home.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.home.exception.PopularityAlreadyRunningException;
import com.ottnavi.home.job.PopularityLauncher;
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
 * 인기 순위 수집 실행 API(Task 107)의 응답과 접근 제어를 확인한다. 시작 자체는 목으로 바꿔 응답 규칙만 본다.
 * 막으려는 사고: 일반 사용자나 비로그인이 수집을 시작하는 일, 실행 중인데 409가 아닌 응답이 나가는 일, cron의 배치 토큰이 거절되는 일.
 */
@AutoConfigureMockMvc
class AdminPopularityApiTest extends IntegrationTestSupport {

	private static final String PATH = "/api/admin/popularity/executions";

	@Autowired private MockMvc mockMvc;
	@Autowired private JwtTokenProvider jwtTokenProvider;
	@Value("${app.admin-batch-token}") private String batchToken;
	@MockitoBean private PopularityLauncher popularityLauncher;

	@Test
	void 관리자와_배치_토큰은_202와_실행_ID를_받는다() throws Exception {
		// given
		when(popularityLauncher.start()).thenReturn(31L);
		TestTokens tokens = new TestTokens(jwtTokenProvider);

		// when, then: 관리자
		mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, tokens.adminBearer()))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.data.executionId").value(31));

		// when, then: 배치 토큰(cron)
		mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + batchToken))
				.andExpect(status().isAccepted());
	}

	@Test
	void 이미_실행_중이면_409_BATCH_ALREADY_RUNNING이다() throws Exception {
		// given
		when(popularityLauncher.start()).thenThrow(new PopularityAlreadyRunningException("인기 순위 수집이 이미 실행 중입니다."));

		// when, then
		mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, new TestTokens(jwtTokenProvider).adminBearer()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("BATCH_ALREADY_RUNNING"));
	}

	@Test
	void 일반_사용자는_403이고_토큰이_없거나_틀리면_401이다() throws Exception {
		// given
		TestTokens tokens = new TestTokens(jwtTokenProvider);

		// when, then
		mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, tokens.userBearer()))
				.andExpect(status().isForbidden());
		mockMvc.perform(post(PATH)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token"))
				.andExpect(status().isUnauthorized());
	}
}
