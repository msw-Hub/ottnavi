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
 * GET /api/admin/visitor-stats의 보안과 응답 모양을 실제 보안 체인으로 확인한다(Docker Desktop 필요).
 * 테스트에는 GA 환경 변수가 없어 연동이 꺼진 상태(DISABLED)다. 막으려는 사고: 일반 회원·비로그인이 보는 일,
 * 데모 계정이 못 보는 일, 연동이 없을 때 오류 코드나 숫자 0이 나가는 일.
 */
@AutoConfigureMockMvc
class AdminVisitorStatsApiTest extends IntegrationTestSupport {

	private static final String PATH = "/api/admin/visitor-stats";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Test
	void ADMIN은_200이고_연동이_없으면_DISABLED와_null_숫자다() throws Exception {
		// given
		String token = new TestTokens(jwtTokenProvider).adminBearer();

		// when & then
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.status").value("DISABLED"))
				.andExpect(jsonPath("$.data.todayVisitorCount").isEmpty())
				.andExpect(jsonPath("$.data.yesterdayVisitorCount").isEmpty())
				.andExpect(jsonPath("$.data.last7DaysVisitorCount").isEmpty())
				.andExpect(jsonPath("$.data.last30DaysVisitorCount").isEmpty())
				.andExpect(jsonPath("$.data.fetchedAt").isEmpty());
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
