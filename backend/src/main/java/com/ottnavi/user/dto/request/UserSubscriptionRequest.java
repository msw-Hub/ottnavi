package com.ottnavi.user.dto.request;

import com.ottnavi.user.domain.enums.ServiceSubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 서비스 하나의 이용 상태 요청(계약 UserSubscription).
 *
 * @param ottServiceId 서비스 내부 ID
 * @param status       이용 상태
 * @param planName     요금제 이름. SUBSCRIBED일 때만 필수이고 그 밖에는 null
 * @param billingDay   결제일 1~31. SUBSCRIBED일 때만 값이 있다. SUBSCRIBED인데 null이면 1일로 저장한다
 */
public record UserSubscriptionRequest(
		@NotNull(message = "서비스 ID는 필수입니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") Long ottServiceId,
		@NotNull(message = "이용 상태는 필수입니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SUBSCRIBED") ServiceSubscriptionStatus status,
		@Size(max = 30, message = "요금제 이름은 30자 이하여야 합니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "스탠다드") String planName,
		@Min(value = 1, message = "결제일은 1 이상이어야 합니다.")
		@Max(value = 31, message = "결제일은 31 이하여야 합니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "15") Integer billingDay) {
}
