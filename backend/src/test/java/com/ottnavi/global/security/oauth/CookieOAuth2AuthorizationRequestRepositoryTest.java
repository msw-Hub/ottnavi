package com.ottnavi.global.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.global.security.jwt.JwtProperties;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * 서명 쿠키 인가 요청 저장소를 단위로 확인한다(Docker 불필요).
 * 막으려는 사고: 쿠키 변조로 남의 인가 요청 주입, 쿠키가 커져 브라우저가 버리는 일, 콜백 재사용.
 */
class CookieOAuth2AuthorizationRequestRepositoryTest {

	// 로컬 개발 키와 같은 형식(Base64, 48바이트)
	private static final String SECRET = "b3R0bmF2aS1sb2NhbC1kZXYtb25seS1qd3Qtc2VjcmV0LWtleS0wMTIzNDU2Nzg5";

	private final CookieOAuth2AuthorizationRequestRepository repository = new CookieOAuth2AuthorizationRequestRepository(
			new JwtProperties(SECRET, Duration.ofMinutes(30), "ottnavi"), true);

	@Test
	void 저장한_인가_요청을_쿠키에서_그대로_읽는다() {
		// given
		OAuth2AuthorizationRequest original = sampleRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		// when: 저장하고, 응답 쿠키를 다음 요청에 실어 읽는다
		repository.saveAuthorizationRequest(original, new MockHttpServletRequest(), response);
		OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(requestWith(cookieValue(response)));

		// then
		assertThat(loaded).isNotNull();
		assertThat(loaded.getState()).isEqualTo("state-value");
		assertThat(loaded.getClientId()).isEqualTo("client-id");
		assertThat(loaded.getRedirectUri()).isEqualTo(original.getRedirectUri());
		assertThat(loaded.getScopes()).containsExactlyInAnyOrder("openid", "email", "profile");
		assertThat(loaded.getAdditionalParameters()).containsEntry("nonce", "nonce-value");
		assertThat((String) loaded.getAttribute("registration_id")).isEqualTo("google");
		assertThat(loaded.getAuthorizationRequestUri()).isEqualTo(original.getAuthorizationRequestUri());
	}

	@Test
	void 쿠키_속성이_맞고_크기가_4KB보다_작다() {
		// given & when
		MockHttpServletResponse response = new MockHttpServletResponse();
		repository.saveAuthorizationRequest(sampleRequest(), new MockHttpServletRequest(), response);
		String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);

		// then
		assertThat(setCookie).startsWith("oauth2_auth_request=");
		assertThat(setCookie).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api", "Max-Age=180");
		assertThat(setCookie.length()).isLessThan(4096);
	}

	@Test
	void 서명이_바뀌면_없는_것으로_본다() {
		// given
		MockHttpServletResponse response = new MockHttpServletResponse();
		repository.saveAuthorizationRequest(sampleRequest(), new MockHttpServletRequest(), response);
		String value = cookieValue(response);

		// when: payload 한 글자를 바꾼다 / 서명 한 글자를 바꾼다
		String tamperedPayload = flipFirstChar(value);
		int dot = value.indexOf('.');
		String tamperedSignature = value.substring(0, dot + 1) + flipFirstChar(value.substring(dot + 1));

		// then
		assertThat(repository.loadAuthorizationRequest(requestWith(tamperedPayload))).isNull();
		assertThat(repository.loadAuthorizationRequest(requestWith(tamperedSignature))).isNull();
		assertThat(repository.loadAuthorizationRequest(requestWith("not-a-signed-cookie"))).isNull();
	}

	@Test
	void 읽으면서_삭제하면_Max_Age_0_쿠키를_내린다() {
		// given
		MockHttpServletResponse saved = new MockHttpServletResponse();
		repository.saveAuthorizationRequest(sampleRequest(), new MockHttpServletRequest(), saved);
		MockHttpServletResponse removed = new MockHttpServletResponse();

		// when
		OAuth2AuthorizationRequest loaded = repository.removeAuthorizationRequest(requestWith(cookieValue(saved)), removed);

		// then
		assertThat(loaded).isNotNull();
		assertThat(removed.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0", "Path=/api");
	}

	private OAuth2AuthorizationRequest sampleRequest() {
		return OAuth2AuthorizationRequest.authorizationCode()
				.authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
				.clientId("client-id")
				.redirectUri("https://ottnavi.vercel.app/api/login/oauth2/code/google")
				.scopes(Set.of("openid", "email", "profile"))
				.state("state-value")
				.additionalParameters(Map.of("nonce", "nonce-value"))
				.attributes(attrs -> attrs.put("registration_id", "google"))
				.authorizationRequestUri("https://accounts.google.com/o/oauth2/v2/auth?client_id=client-id&state=state-value")
				.build();
	}

	private MockHttpServletRequest requestWith(String cookieValue) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, cookieValue));
		return request;
	}

	/** Set-Cookie 헤더에서 쿠키 값만 꺼낸다 */
	private String cookieValue(MockHttpServletResponse response) {
		String header = response.getHeader(HttpHeaders.SET_COOKIE);
		int start = header.indexOf('=') + 1;
		return header.substring(start, header.indexOf(';'));
	}

	private String flipFirstChar(String value) {
		char first = value.charAt(0);
		char replaced = 'A';
		if (first == 'A') {
			replaced = 'B';
		}
		return replaced + value.substring(1);
	}
}
