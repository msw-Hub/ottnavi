package com.ottnavi.home.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 인기 순위 수집 실행 접수 결과(계약 PopularityExecutionAccepted).
 *
 * @param executionId 새 수집 실행 ID(Spring Batch jobExecutionId)
 */
public record PopularityExecutionAcceptedResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "31") long executionId) {
}
