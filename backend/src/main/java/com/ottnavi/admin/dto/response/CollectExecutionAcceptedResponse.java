package com.ottnavi.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 실행 접수 결과(계약 CollectExecutionAccepted). 결과는 실행 이력에서 본다.
 *
 * @param executionId 새 실행 ID(Spring Batch jobExecutionId)
 */
public record CollectExecutionAcceptedResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "6") long executionId) {
}
