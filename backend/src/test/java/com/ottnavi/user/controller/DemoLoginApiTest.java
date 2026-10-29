package com.ottnavi.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 데모 계정 로그인 API를 실제 보안 체인·Redis·DB로 확인한다(Docker Desktop 필요).
 * 아이디·비밀번호는 테스트 전용 더미 값이다. 요청 제한을 켜고(데모 버킷 검증), 테스트마다 다른 방문자 IP(X-Forwarded-For)를 써서
 * 한 테스트의 시도가 다른 테스트의 한도를 쓰지 않게 한다.
 * 막으려는 사고: 틀린 비밀번호로 로그인되는 일, 로그인할 때마다 데모 행이 늘어나는 일, 다른 사이트에서 로그인시키는 일(Origin),
 * 비밀번호를 무한히 맞혀 보는 일, 실패 로그에 입력값이 남는 일.
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
		"app.demo-login.login-id=" + DemoLoginApiTest.LOGIN_ID,
		"app.demo-login.password=" + DemoLoginApiTest.PASSWORD,
		"app.rate-limit.enabled=true"})
class DemoLoginApiTest extends IntegrationTestSupport {

	static final String LOGIN_ID = "test-demo-id";
	static final String PASSWORD = "test-only-dummy-password-16+"; // 테스트 전용 더미 값
	private static final String URL = "/api/auth/demo-login";
	private static final String ORIGIN = "http://localhost:5173"; // application-local.yml의 app.frontend-origin 기본값

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		cleanUp();
	}

	@AfterEach
	void tearDown() {
		cleanUp();
	}

	private void cleanUp() {
		jdbc.update("DELETE FROM users WHERE oauth_provider = 'DEMO'");
	}

	@Test
	void 맞는_아이디와_비밀번호면_AT와_DEMO_권한을_주고_RT_쿠키를_심는다() throws Exception {
		// when
		MvcResult result = mockMvc.perform(login("10.9.0.1", LOGIN_ID, PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.role").value("DEMO"))
				.andReturn();

		// then: RT는 재발급과 같은 속성의 쿠키로만 오고, 데모 행이 하나 생긴다
		String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).startsWith("rt=");
		assertThat(setCookie).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api/auth");
		assertThat(countDemoRows()).isEqualTo(1);
	}

	@Test
	void 두_번_로그인해도_데모_행은_하나다() throws Exception {
		// given
		mockMvc.perform(login("10.9.0.2", LOGIN_ID, PASSWORD)).andExpect(status().isOk());

		// when
		mockMvc.perform(login("10.9.0.2", LOGIN_ID, PASSWORD)).andExpect(status().isOk());

		// then
		assertThat(countDemoRows()).isEqualTo(1);
	}

	@Test
	void 비밀번호가_틀리면_401_DEMO_LOGIN_FAILED이고_로그에_입력값이_없다(CapturedOutput output) throws Exception {
		// given
		String wrongPassword = "wrong-dummy-password-typed";

		// when & then
		mockMvc.perform(login("10.9.0.3", LOGIN_ID, wrongPassword))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("DEMO_LOGIN_FAILED"))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
		assertThat(countDemoRows()).isZero();
		assertThat(output.getAll()).doesNotContain(wrongPassword);
	}

	@Test
	void 아이디가_틀려도_같은_401이다() throws Exception {
		mockMvc.perform(login("10.9.0.4", "wrong-id", PASSWORD))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("DEMO_LOGIN_FAILED"));
	}

	@Test
	void Origin이_다르면_403이고_로그인되지_않는다() throws Exception {
		// given
		MockHttpServletRequestBuilder request = post(URL)
				.header("X-Forwarded-For", "10.9.0.5")
				.header(HttpHeaders.ORIGIN, "https://evil.example")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(LOGIN_ID, PASSWORD));

		// when & then
		mockMvc.perform(request)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
		assertThat(countDemoRows()).isZero();
	}

	@Test
	void 같은_IP의_여섯_번째_시도는_429다() throws Exception {
		// given: 5번까지는 틀린 비밀번호라도 401이다(성공·실패 모두 한도를 쓴다)
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(login("10.9.0.6", LOGIN_ID, "wrong-dummy-password-typed"))
					.andExpect(status().isUnauthorized());
		}

		// when & then: 여섯 번째는 맞는 비밀번호여도 429다
		mockMvc.perform(login("10.9.0.6", LOGIN_ID, PASSWORD))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
				.andExpect(header().exists("Retry-After"));
	}

	@Test
	void 로그인_뒤_재발급해도_DEMO_권한이_유지된다() throws Exception {
		// given
		MvcResult loggedIn = mockMvc.perform(login("10.9.0.7", LOGIN_ID, PASSWORD))
				.andExpect(status().isOk())
				.andReturn();
		String refreshToken = cookieValue(loggedIn.getResponse().getHeader(HttpHeaders.SET_COOKIE));

		// when & then
		mockMvc.perform(post("/api/auth/refresh")
						.header("X-Forwarded-For", "10.9.0.7")
						.header(HttpHeaders.ORIGIN, ORIGIN)
						.cookie(new Cookie("rt", refreshToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.role").value("DEMO"));
	}

	private MockHttpServletRequestBuilder login(String clientIp, String loginId, String password) {
		return post(URL)
				.header("X-Forwarded-For", clientIp)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(loginId, password));
	}

	private String body(String loginId, String password) {
		return "{\"loginId\": \"" + loginId + "\", \"password\": \"" + password + "\"}";
	}

	private int countDemoRows() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE oauth_provider = 'DEMO'", Integer.class);
	}

	/** Set-Cookie 헤더에서 쿠키 값만 꺼낸다 */
	private String cookieValue(String setCookie) {
		return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
	}
}
