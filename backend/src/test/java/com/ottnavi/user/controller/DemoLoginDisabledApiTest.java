package com.ottnavi.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.security.token.RefreshTokenStore;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.repository.UserRepository;
import com.ottnavi.user.service.DemoAccount;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 데모 로그인이 꺼진 상태(기본 설정: 아이디·비밀번호 없음)를 확인한다(Docker Desktop 필요).
 * 다른 통합 테스트와 같은 설정이라 컨텍스트를 새로 띄우지 않는다.
 * 막으려는 사고: 꺼 둔 데모 로그인이 응답으로 존재를 드러내는 일, 끈 뒤에도 남은 데모 세션이 계속 재발급되는 일.
 */
@AutoConfigureMockMvc
class DemoLoginDisabledApiTest extends IntegrationTestSupport {

	private static final String ORIGIN = "http://localhost:5173"; // application-local.yml의 app.frontend-origin 기본값

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RefreshTokenStore store;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void tearDown() {
		jdbc.update("DELETE FROM users WHERE oauth_provider = 'DEMO'");
	}

	@Test
	void 꺼져_있으면_Origin이_달라도_404_RESOURCE_NOT_FOUND다() throws Exception {
		mockMvc.perform(post("/api/auth/demo-login")
						.header(HttpHeaders.ORIGIN, "https://evil.example")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"loginId\": \"any\", \"password\": \"any\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void 꺼진_뒤_남은_데모_세션은_재발급이_401이다() throws Exception {
		// given: 켜져 있을 때 만들어진 데모 행과 Refresh Token
		User demo = userRepository.save(User.createDemo(DemoAccount.EMAIL, DemoAccount.NAME, DemoAccount.SUBJECT));
		String token = store.issue(demo.getId());

		// when & then
		mockMvc.perform(post("/api/auth/refresh")
						.header(HttpHeaders.ORIGIN, ORIGIN)
						.cookie(new Cookie("rt", token)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}
}
