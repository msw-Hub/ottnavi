package com.ottnavi.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.error.AuthStoreUnavailableException;
import com.ottnavi.global.security.token.RefreshTokenStore;
import com.ottnavi.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Refresh Token 저장소(Redis)가 장애일 때 재발급·로그아웃·데모 로그인이 503으로 끝나는지 확인한다.
 * 저장소를 목으로 바꿔 예외를 던지게 한다(Docker Desktop 필요, 컨텍스트가 DB·Redis를 띄운다).
 * 막으려는 사고: Redis 장애가 500이 되는 일, 재발급이 실패했는데 쿠키가 지워져 사용자가 로그아웃되는 일.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"app.demo-login.login-id=" + AuthStoreUnavailableApiTest.LOGIN_ID,
		"app.demo-login.password=" + AuthStoreUnavailableApiTest.PASSWORD})
class AuthStoreUnavailableApiTest extends IntegrationTestSupport {

	static final String LOGIN_ID = "test-demo-id";
	static final String PASSWORD = "test-only-dummy-password-16+"; // 테스트 전용 더미 값
	private static final String ORIGIN = "http://localhost:5173"; // application-local.yml의 app.frontend-origin 기본값
	private static final String TOKEN = "A".repeat(22) + "." + "B".repeat(43); // 형식만 맞는 더미 토큰

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private RefreshTokenStore store;

	@BeforeEach
	void setUp() {
		AuthStoreUnavailableException failure = new AuthStoreUnavailableException("저장소 장애", new RuntimeException("원인"));
		when(store.rotate(anyString())).thenThrow(failure);
		when(store.issue(anyLong())).thenThrow(failure);
		doThrow(failure).when(store).revoke(anyString());
	}

	@AfterEach
	void tearDown() {
		jdbc.update("DELETE FROM users WHERE oauth_provider = 'DEMO'");
	}

	@Test
	void 재발급은_503이고_쿠키를_지우지_않는다() throws Exception {
		// when
		MvcResult result = mockMvc.perform(post("/api/auth/refresh")
						.header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("rt", TOKEN)))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.errorCode").value("AUTH_STORE_UNAVAILABLE"))
				.andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
				.andReturn();

		// then: 쿠키 삭제(Set-Cookie)가 없다
		assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
	}

	@Test
	void 로그아웃은_503이고_쿠키를_지우지_않는다() throws Exception {
		// when
		MvcResult result = mockMvc.perform(post("/api/auth/logout")
						.header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("rt", TOKEN)))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.errorCode").value("AUTH_STORE_UNAVAILABLE"))
				.andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
				.andReturn();

		// then
		assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
	}

	@Test
	void 데모_로그인은_503이다() throws Exception {
		// when & then
		mockMvc.perform(post("/api/auth/demo-login")
						.header(HttpHeaders.ORIGIN, ORIGIN)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"loginId\": \"" + LOGIN_ID + "\", \"password\": \"" + PASSWORD + "\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.errorCode").value("AUTH_STORE_UNAVAILABLE"))
				.andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}
}
