package com.ottnavi.infra.analytics.exception;

/**
 * Google Analytics 조회 실패: 오류 응답·시간 초과·연결 실패·응답 해석 실패.
 * 메시지는 고정 문구만 쓰고 속성 ID·토큰·응답 본문을 넣지 않는다. 사용자에게는 오류 코드가 아니라 status=UNAVAILABLE로 알린다.
 */
public class AnalyticsUnavailableException extends RuntimeException {

	public AnalyticsUnavailableException(String message) {
		super(message);
	}

	public AnalyticsUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
