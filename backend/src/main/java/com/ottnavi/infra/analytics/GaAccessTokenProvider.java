package com.ottnavi.infra.analytics;

/** GA Data API 호출에 쓸 Google OAuth2 액세스 토큰 제공자. 테스트에서 실제 구글 호출 없이 가짜로 바꿀 수 있게 인터페이스로 둔다 */
public interface GaAccessTokenProvider {

	/**
	 * 유효한 액세스 토큰 값을 돌려준다.
	 *
	 * @throws com.ottnavi.infra.analytics.exception.AnalyticsCredentialsException 키를 읽지 못했거나 토큰을 받지 못했을 때
	 */
	String getAccessToken();
}
