package com.ottnavi.plan.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 재계산 실행 접수 결과(계약 PlanRecalcExecutionAccepted).
 *
 * @param executionId 새 재계산 실행 ID(Spring Batch jobExecutionId)
 */
public record PlanRecalcExecutionAcceptedResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "14") long executionId) {
}
