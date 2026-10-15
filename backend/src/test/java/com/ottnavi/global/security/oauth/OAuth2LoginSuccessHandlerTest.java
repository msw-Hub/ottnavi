package com.ottnavi.global.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

/**
 * 로그인 성공 핸들러를 직접 호출해 사용자 생성·RT 쿠키·이동 주소를 확인한다(Docker Desktop 필요).
 * 전체 구글 흐름(토큰 교환·ID 토큰 검증)은 자동 테스트하지 않고 브라우저로 확인한다.
 * 막으려는 사고: 이동 주소에 토큰이 실리는 일, 필수 프로필 값이 없는데 로그인되는 일.
 */
class OAuth2LoginSuccessHandlerTest extends IntegrationTestSupport {

	@Autowired
	private OAuth2LoginSuccessHandler successHandler;

	@Autowired
	private UserRepository userRepository;

	@Test
	void 성공하면_사용자를_만들고_RT_쿠키를_심고_프론트_콜백으로_보낸다() throws Exception {
		// given
		String sub = "g" + UUID.randomUUID().toString().replace("-", "");
		MockHttpServletResponse response = new MockHttpServletResponse();

		// when
		successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
				authentication(Map.of("sub", sub, "email", sub + "@example.com", "email_verified", true,
							"name", "홍길동")));

		// then: 쿼리 없는 고정 주소, rt 쿠키 존재, 사용자 생성
		assertThat(response.getStatus()).isEqualTo(302);
		assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/auth/callback");
		String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).startsWith("rt=");
		assertThat(setCookie).contains("HttpOnly", "SameSite=Lax", "Path=/api/auth");
		assertThat(userRepository.findByOauthProviderAndOauthSubject(OAuthProvider.GOOGLE, sub)).isPresent();
	}

	@Test
	void 필수_프로필_값이_없으면_실패_주소로_보내고_쿠키를_심지_않는다() throws Exception {
		// given: email이 없다
		MockHttpServletResponse response = new MockHttpServletResponse();

		// when
		successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
				authentication(Map.of("sub", "only-sub", "name", "이름")));

		// then
		assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/auth/callback?error=login_failed");
		assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
	}

	@Test
	void 이메일이_검증되지_않은_계정은_사용자를_만들지_않고_실패_주소로_보낸다() throws Exception {
		// given: email_verified=false
		String sub = "g" + UUID.randomUUID().toString().replace("-", "");
		MockHttpServletResponse response = new MockHttpServletResponse();

		// when
		successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
				authentication(Map.of("sub", sub, "email", sub + "@example.com", "email_verified", false,
						"name", "이름")));

		// then
		assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/auth/callback?error=login_failed");
		assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
		assertThat(userRepository.findByOauthProviderAndOauthSubject(OAuthProvider.GOOGLE, sub)).isEmpty();
	}

	private OAuth2AuthenticationToken authentication(Map<String, Object> attributes) {
		DefaultOAuth2User principal = new DefaultOAuth2User(
				List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "sub");
		return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
	}
}
