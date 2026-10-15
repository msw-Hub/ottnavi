package com.ottnavi.user.service.model;

import com.ottnavi.user.domain.enums.ServiceSubscriptionStatus;

/**
 * 서비스 하나의 이용 상태. 설정 저장 입력과 조회 결과에 함께 쓴다.
 *
 * @param ottServiceId 서비스 내부 ID
 * @param status       이용 상태(미구독 포함)
 * @param planName     요금제 이름. SUBSCRIBED가 아니면 null
 * @param billingDay   결제일 1~31. SUBSCRIBED가 아니면 null
 */
public record SubscriptionState(
		Long ottServiceId,
		ServiceSubscriptionStatus status,
		String planName,
		Integer billingDay) {
}
