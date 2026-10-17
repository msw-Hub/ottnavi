package com.ottnavi.retention.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 데이터 정리 실행 접수 결과(계약 RetentionExecutionAccepted).
 *
 * @param executionId 새 정리 실행 ID(Spring Batch jobExecutionId)
 */
public record RetentionExecutionAcceptedResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "21") long executionId) {
}
