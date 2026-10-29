package com.ottnavi.llmdraft.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 번역 초안 한 건 생성 요청(계약 CreateOverviewDraftRequest).
 *
 * @param titleId 번역할 작품 ID(영어 줄거리가 있는 작품)
 */
public record CreateDraftRequest(
		@NotNull(message = "작품 ID는 필수입니다.")
		@Min(value = 1, message = "1 이상이어야 합니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1024") Long titleId) {
}
