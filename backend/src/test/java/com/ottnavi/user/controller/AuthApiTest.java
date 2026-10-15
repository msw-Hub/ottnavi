package com.ottnavi.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.security.token.RefreshTokenStore;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 재발급·로그아웃 API를 실제 보안 체인과 Redis·DB로 확인한다(Docker Desktop 필요).
 * 막으려는 사고: Origin 검사 전에 토큰이 교체되는 일, 재사용된 토큰 뒤 새 토큰이 살아 있는 일,
 * 응답 본문에 Refresh Token이 노출되는 일.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.jwt.refresh-reuse-grace=1s")
class AuthApiTest extends IntegrationTestSupport {

	private static final String ORIGIN = "http://localhost:5173"; // application-local.yml의 app.frontend-origin 기본값
	private static final long GRACE_PASS_MILLIS = 1300;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RefreshTokenStore store;

	@Autowired
	private UserRepository userRepository;

	@Test
	void 쿠키와_맞는_Origin이면_새_AT와_권한을_주고_RT_쿠키를_회전한다() throws Exception {
		// given
		String token = store.issue(createUserId());

		// when
		MvcResult result = mockMvc.perform(post("/api/auth/refresh")
						.header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("rt", token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.role").value("USER"))
				.andReturn();

		// then: 새 RT는 쿠키로만 오고 본문에는 없다
		String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).startsWith("rt=");
		assertThat(setCookie).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api/auth", "Max-Age=1209600");
		assertThat(setCookie).doesNotContain(token);
		assertThat(result.getResponse().getContentAsString()).doesNotContain(token);
	}

	@Test
	void Origin이_없거나_다르면_403이고_토큰이_회전되지_않는다() throws Exception {
		// given
		String token = store.issue(createUserId());

		// when & then
		mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("rt", token)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
		mockMvc.perform(post("/api/auth/refresh")
						.header(HttpHeaders.ORIGIN, "http://localhost:5173/").cookie(new Cookie("rt", token)))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/auth/refresh")
						.header(HttpHeaders.ORIGIN, "https://evil.example").cookie(new Cookie("rt", token)))
				.andExpect(status().isForbidden());

		// 같은 토큰이 아직 교체되지 않아 정상 요청이 성공한다(재사용 판정이 아님)
		mockMvc.perform(post("/api/auth/refresh")
						.header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("rt", token)))
				.andExpect(status().isOk());
	}

	@Test
	void 쿠키가_없으면_401이고_쿠키를_지운다() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
				.andReturn();

		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0", "Path=/api/auth");
	}

	@Test
	void 유예를_지난_재사용은_401이고_새_RT도_더_못_쓴다() throws Exception {
		// given: 한 번 교체해 새 RT를 받는다
		String token = store.issue(createUserId());
		MvcResult first = mockMvc.perform(post("/api/auth/refresh")
						.header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("rt", token)))
				.andExpect(status().isOk())
				.andReturn();
		String newToken = cookieValue(first.getResponse().getHeader(HttpHeaders.SET_COOKIE));

		// when: 유예가 지난 뒤 옛 RT를 다시 쓴다
		Thread.sleep(GRACE_PASS_MILLIS);
		mockMvc.perform(post("/api/auth/refresh")
						.header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("rt", token)))
				.andExpect(status().isUnauthorized());

		// then: 계열이 무효가 돼 새 RT도 거부된다
		mockMvc.perform(post("/api/auth/refresh")
						.header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("rt", newToken)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 유예_안의_직전_RT는_AT만_주고_쿠키는_갱신하지_않는다() throws Exception {
		// given
		String token = store.issue(createUserId());
		mockMvc.perform(post("/api/auth/refresh")
						.header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("rt", token)))
				.andExpect(status().isOk());

		// when
		MvcResult again = mockMvc.perform(post("/api/auth/refresh")
						.header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("rt", token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andReturn();

		// then
		assertThat(again.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
	}

	@Test
	void 로그아웃은_204이고_쿠키를_지우며_옛_쿠키로_재발급할_수_없다() throws Exception {
		// given
		String token = store.issue(createUserId());

		// when
		MvcResult result = mockMvc.perform(post("/api/auth/logout")
						.header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("rt", token)))
				.andExpect(status().isNoContent())
				.andReturn();

		// then
		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0", "Path=/api/auth");
		mockMvc.perform(post("/api/auth/refresh")
						.header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("rt", token)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 로그아웃은_쿠키가_없어도_204이고_Origin이_다르면_403이다() throws Exception {
		mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.ORIGIN, ORIGIN))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.ORIGIN, "https://evil.example"))
				.andExpect(status().isForbidden());
	}

	/** 재발급 때 권한을 DB에서 읽으므로 실제 사용자 행이 필요하다 */
	private Long createUserId() {
		String sub = "g" + UUID.randomUUID().toString().replace("-", "");
		User user = User.create(sub + "@example.com", "테스터", OAuthProvider.GOOGLE, sub);
		return userRepository.save(user).getId();
	}

	/** Set-Cookie 헤더에서 쿠키 값만 꺼낸다 */
	private String cookieValue(String setCookie) {
		return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
	}
}
