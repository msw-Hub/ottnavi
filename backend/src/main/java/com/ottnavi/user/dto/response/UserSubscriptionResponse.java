package com.ottnavi.user.dto.response;

import com.ottnavi.user.domain.enums.ServiceSubscriptionStatus;
import com.ottnavi.user.service.model.SubscriptionState;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 서비스 하나의 이용 상태 응답(계약 UserSubscription).
 *
 * @param ottServiceId 서비스 내부 ID
 * @param status       이용 상태
 * @param planName     요금제 이름. SUBSCRIBED가 아니면 null
 * @param billingDay   결제일 1~31. SUBSCRIBED가 아니면 null
 */
public record UserSubscriptionResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") Long ottServiceId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SUBSCRIBED") ServiceSubscriptionStatus status,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "스탠다드") String planName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "15") Integer billingDay) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static UserSubscriptionResponse from(SubscriptionState state) {
		return new UserSubscriptionResponse(state.ottServiceId(), state.status(), state.planName(), state.billingDay());
	}
}
