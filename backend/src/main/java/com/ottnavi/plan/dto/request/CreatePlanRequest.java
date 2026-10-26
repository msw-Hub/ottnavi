package com.ottnavi.plan.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 플랜 저장 요청(계약 CreatePlanRequest).
 *
 * @param planId 저장할 초안의 플랜 ID
 */
public record CreatePlanRequest(
		@NotNull(message = "플랜 ID는 필수입니다.")
		@Min(value = 1, message = "1 이상이어야 합니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "301") Long planId) {
}
