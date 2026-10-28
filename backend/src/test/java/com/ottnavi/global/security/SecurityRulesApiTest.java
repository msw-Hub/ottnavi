package com.ottnavi.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.support.TestTokens;
import com.ottnavi.user.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
	void DEMO_토큰은_관리자_조회와_회원_조회가_200이다() throws Exception {
		String demo = tokens().demoBearer();

		mockMvc.perform(get("/api/admin/products").header(HttpHeaders.AUTHORIZATION, demo))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/admin/collect/executions").header(HttpHeaders.AUTHORIZATION, demo))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/me/settings").header(HttpHeaders.AUTHORIZATION, demo))
				.andExpect(status().isOk());
	}

	@Test
	void DEMO_토큰의_관리자_쓰기와_실행은_403이다() throws Exception {
		String demo = tokens().demoBearer();

		mockMvc.perform(patch("/api/admin/products/1").header(HttpHeaders.AUTHORIZATION, demo)
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
		mockMvc.perform(post("/api/admin/collect/executions").header(HttpHeaders.AUTHORIZATION, demo)
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/plan-benchmarks").header(HttpHeaders.AUTHORIZATION, demo)
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void DEMO_토큰의_탈퇴와_알림_설정_변경은_403이다() throws Exception {
		String demo = tokens().demoBearer();

		mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, demo))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
		mockMvc.perform(put("/api/me/notification-setting").header(HttpHeaders.AUTHORIZATION, demo)
						.contentType(MediaType.APPLICATION_JSON).content("{\"isNotificationEnabled\": true}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
	}

	@Test
	void USER_토큰의_알림_설정_변경은_보안_규칙을_통과한다() throws Exception {
		// 없는 사용자 id라 서비스가 401(사용자를 찾을 수 없음)을 낸다. 보안 규칙에서 막혔다면 인증된 요청이라 403이었을 것이다
		String user = tokens().bearer(999_999L, UserRole.USER);
		mockMvc.perform(put("/api/me/notification-setting").header(HttpHeaders.AUTHORIZATION, user)
						.contentType(MediaType.APPLICATION_JSON).content("{\"isNotificationEnabled\": true}"))
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
