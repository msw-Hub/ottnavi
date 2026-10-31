package com.ottnavi.infra.analytics.exception;

/** Google 인증 정보를 읽지 못했거나 토큰을 받지 못함: 키 Base64·JSON 오류, 토큰 발급 실패. 운영자가 설정을 고쳐야 하는 오류다 */
public class AnalyticsCredentialsException extends AnalyticsUnavailableException {

	public AnalyticsCredentialsException(String message) {
		super(message);
	}

	public AnalyticsCredentialsException(String message, Throwable cause) {
		super(message, cause);
	}
}
