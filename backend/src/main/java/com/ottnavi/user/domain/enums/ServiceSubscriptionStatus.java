package com.ottnavi.user.domain.enums;

/**
 * API 계약의 서비스 이용 상태(ServiceSubscriptionStatus). DB 값({@link SubscriptionStatus})에 미구독이 더해진 4가지다.
 * 요청과 응답에서만 쓰고 DB에는 저장하지 않는다.
 */
public enum ServiceSubscriptionStatus {
	SUBSCRIBED,
	FREE,
	NOT_SUBSCRIBED, // 저장 행이 없는 서비스
	UNKNOWN
}
