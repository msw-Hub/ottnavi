package com.ottnavi.support;

import static org.mockito.Mockito.mock;

import com.ottnavi.global.security.jwt.JwtProperties;
import com.ottnavi.global.security.oauth.CookieOAuth2AuthorizationRequestRepository;
import com.ottnavi.global.security.oauth.OAuth2LoginFailureHandler;
import com.ottnavi.global.security.oauth.OAuth2LoginSuccessHandler;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * SecurityConfig를 가져오는 슬라이스 테스트(@WebMvcTest)용 구글 로그인 협력 빈.
 * 슬라이스에는 클라이언트 등록 저장소와 서비스·저장소 빈이 없어, 체인이 만들어지도록 최소한의 값만 채운다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class OAuth2LoginTestConfig {

	@Bean
	ClientRegistrationRepository clientRegistrationRepository() {
		ClientRegistration registration = ClientRegistration.withRegistrationId("google")
				.clientId("test-client")
				.clientSecret("test-secret")
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("http://localhost/api/login/oauth2/code/google")
				.authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
				.tokenUri("https://oauth2.googleapis.com/token")
				.userNameAttributeName("sub")
				.build();
		return new InMemoryClientRegistrationRepository(registration);
	}

	@Bean
	CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository(JwtProperties properties) {
		return new CookieOAuth2AuthorizationRequestRepository(properties, true);
	}

	@Bean
	OAuth2LoginSuccessHandler oauth2LoginSuccessHandler() {
		return mock(OAuth2LoginSuccessHandler.class);
	}

	@Bean
	OAuth2LoginFailureHandler oauth2LoginFailureHandler() {
		return new OAuth2LoginFailureHandler("http://localhost");
	}
}
