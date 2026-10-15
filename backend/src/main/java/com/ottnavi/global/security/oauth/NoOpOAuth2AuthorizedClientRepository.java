package com.ottnavi.global.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * 구글 Access Token을 저장하지 않는 저장소. 로그인에만 쓰고 구글 API를 다시 호출하지 않으므로 저장할 필요가 없다.
 * 기본 구현은 메모리 서비스나 세션에 저장해 STATELESS 방침과 맞지 않는다.
 */
public class NoOpOAuth2AuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

	@Override
	@SuppressWarnings("unchecked")
	public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId,
			Authentication principal, HttpServletRequest request) {
		return null;
	}

	@Override
	public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
			HttpServletRequest request, HttpServletResponse response) {
		// 저장하지 않는다
	}

	@Override
	public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
			HttpServletRequest request, HttpServletResponse response) {
		// 저장하지 않으므로 지울 것도 없다
	}
}
