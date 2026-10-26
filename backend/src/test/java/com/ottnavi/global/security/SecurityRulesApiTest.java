package com.ottnavi.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 경로 3분류 인가 규칙을 실제 보안 체인으로 확인한다(Task 053·054, Docker 필요).
 * USER 토큰으로 /api/me/settings 를 부르면 실제 API(Task 055)가 200을 돌려주고, 토큰 없이는 401이다.
 */
@AutoConfigureMockMvc
class SecurityRulesApiTest extends IntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Value("${app.admin-batch-token}")
	private String batchToken;

	@Test
	void 토큰_없이_로그인_경로를_호출하면_401_ProblemDetail이다() throws Exception {
		mockMvc.perform(get("/api/me/settings"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}

	@Test
	void USER_토큰이면_로그인_경로의_인증을_통과한다() throws Exception {
		mockMvc.perform(get("/api/me/settings").header(HttpHeaders.AUTHORIZATION, tokens().userBearer()))
				.andExpect(status().isOk());
	}

	@Test
	void 만료되었거나_깨진_토큰은_익명으로_보아_401이다() throws Exception {
		mockMvc.perform(get("/api/me/settings").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}

	@Test
	void 공개_경로는_토큰_없이_200이다() throws Exception {
		mockMvc.perform(get("/api/public/ott-services"))
				.andExpect(status().isOk());
	}

	@Test
	void USER_토큰으로_관리자_경로를_호출하면_403이다() throws Exception {
		String user = tokens().userBearer();

		mockMvc.perform(get("/api/admin/providers").header(HttpHeaders.AUTHORIZATION, user))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
		mockMvc.perform(get("/api/admin/collect/executions").header(HttpHeaders.AUTHORIZATION, user))
				.andExpect(status().isForbidden());
	}

	@Test
	void ADMIN_토큰이면_관리자_경로가_200이고_세션_쿠키가_없다() throws Exception {
		mockMvc.perform(get("/api/admin/providers").header(HttpHeaders.AUTHORIZATION, tokens().adminBearer()))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@Test
	void 배치_토큰은_수집_경로만_통과하고_다른_관리자_경로는_거부된다() throws Exception {
		String batch = "Bearer " + batchToken;

		mockMvc.perform(get("/api/admin/collect/executions").header(HttpHeaders.AUTHORIZATION, batch))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/admin/providers").header(HttpHeaders.AUTHORIZATION, batch))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 기본_로그인_페이지는_노출되지_않는다() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isUnauthorized());
	}

	private TestTokens tokens() {
		return new TestTokens(jwtTokenProvider);
	}
}
