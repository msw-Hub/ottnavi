package com.ottnavi.admin;

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
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /api/admin/user-stats의 보안을 실제 보안 체인으로 확인한다(Docker Desktop 필요).
 * 막으려는 사고: 일반 회원이나 비로그인이 회원 현황을 보는 일, 데모 계정이 조회하지 못하는 일.
 */
@AutoConfigureMockMvc
class AdminStatsApiTest extends IntegrationTestSupport {

	private static final String PATH = "/api/admin/user-stats";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Test
	void ADMIN은_200이고_계약_필드가_모두_있다() throws Exception {
		// given
		String token = new TestTokens(jwtTokenProvider).adminBearer();

		// when & then
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.totalUserCount").isNumber())
				.andExpect(jsonPath("$.data.todayNewUserCount").isNumber())
				.andExpect(jsonPath("$.data.monthNewUserCount").isNumber())
				.andExpect(jsonPath("$.data.activePlanUserCount").isNumber())
				.andExpect(jsonPath("$.data.notificationEnabledUserCount").isNumber())
				.andExpect(jsonPath("$.data.measuredAt").isString());
	}

	@Test
	void DEMO도_200이다() throws Exception {
		// given
		String token = new TestTokens(jwtTokenProvider).demoBearer();

		// when & then
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk());
	}

	@Test
	void USER는_403이다() throws Exception {
		// given
		String token = new TestTokens(jwtTokenProvider).userBearer();

		// when & then
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isForbidden());
	}

	@Test
	void 비로그인은_401이다() throws Exception {
		// when & then
		mockMvc.perform(get(PATH))
				.andExpect(status().isUnauthorized());
	}
}
