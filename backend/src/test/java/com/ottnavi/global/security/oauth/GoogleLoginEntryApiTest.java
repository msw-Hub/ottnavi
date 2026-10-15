package com.ottnavi.global.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 구글 로그인 진입점의 보안 설정 배선을 확인한다(구글에는 접속하지 않는다. Docker Desktop 필요).
 * 막으려는 사고: 기본 로그인 페이지 노출, 세션 생성, 인가 요청 쿠키 누락, 콜백 실패가 화면 없는 오류로 끝나는 일.
 */
@AutoConfigureMockMvc
class GoogleLoginEntryApiTest extends IntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 인가_시작은_구글로_302하고_서명_쿠키만_내리며_세션을_만들지_않는다() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/oauth2/authorization/google"))
				.andExpect(status().isFound())
				.andReturn();

		String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
		assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
		assertThat(location).contains("redirect_uri=http://localhost:5173/api/login/oauth2/code/google");
		assertThat(location).contains("scope=openid%20email%20profile");
		List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
		assertThat(cookies).hasSize(1);
		assertThat(cookies.get(0)).startsWith("oauth2_auth_request=").contains("HttpOnly", "Path=/api");
		assertThat(result.getRequest().getSession(false)).isNull();
	}

	@Test
	void 인가_요청_쿠키_없이_콜백이_오면_실패_주소로_302한다() throws Exception {
		mockMvc.perform(get("/api/login/oauth2/code/google").param("code", "x").param("state", "y"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("http://localhost:5173/auth/callback?error=login_failed"));
	}

	@Test
	void 기본_로그인_페이지는_만들어지지_않는다() throws Exception {
		MvcResult result = mockMvc.perform(get("/login")).andReturn();

		assertThat(result.getResponse().getContentAsString()).doesNotContain("Please sign in");
		assertThat(result.getResponse().getStatus()).isNotEqualTo(200);
	}
}
