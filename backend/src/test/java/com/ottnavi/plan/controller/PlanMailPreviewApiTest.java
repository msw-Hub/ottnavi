package com.ottnavi.plan.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 알림 메일 예시 API(Task 110)의 인가와 부작용을 실제 보안 체인·DB로 확인한다(Docker Desktop 필요).
 * 막으려는 사고: 데모가 아닌 계정(USER·ADMIN)이 부르는 일, 비로그인이 부르는 일, 예시를 만들다가 outbox에 행이 쌓이는 일.
 */
@AutoConfigureMockMvc
class PlanMailPreviewApiTest extends IntegrationTestSupport {

	private static final String PATH = "/api/me/plans/current/mail-preview";

	@Autowired private MockMvc mockMvc;
	@Autowired private JwtTokenProvider jwtTokenProvider;
	@Autowired private JdbcTemplate jdbcTemplate;

	@Test
	void DEMO_토큰은_200이고_현재_플랜이_없으면_빈_메일과_사유를_받으며_outbox가_늘지_않는다() throws Exception {
		// given
		Long before = jdbcTemplate.queryForObject("SELECT count(*) FROM notification_outbox", Long.class);

		// when, then
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, tokens().demoBearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.mails").isEmpty())
				.andExpect(jsonPath("$.data.noMailReasons[0]").value("NO_CURRENT_PLAN"));
		Long after = jdbcTemplate.queryForObject("SELECT count(*) FROM notification_outbox", Long.class);
		assertThat(after).isEqualTo(before);
	}

	@Test
	void USER_토큰은_403이다() throws Exception {
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, tokens().userBearer()))
				.andExpect(status().isForbidden());
	}

	@Test
	void ADMIN_토큰은_403이다() throws Exception {
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, tokens().adminBearer()))
				.andExpect(status().isForbidden());
	}

	@Test
	void 토큰이_없으면_401이다() throws Exception {
		mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
	}

	private TestTokens tokens() {
		return new TestTokens(jwtTokenProvider);
	}
}
