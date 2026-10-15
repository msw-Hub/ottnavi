package com.ottnavi.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 월 구독요금 줄 추가·수정 요청(계약 AdminProductPriceRequest, 같은 모양).
 *
 * @param monthlyPrice  월 구독요금(원, 0~1,000,000)
 * @param effectiveFrom 적용 시작일. 앞 줄의 시작일보다 늦어야 한다
 */
public record AdminProductPriceRequest(
		@NotNull(message = "월 구독요금은 필수입니다.")
		@Min(value = 0, message = "0 이상이어야 합니다.") @Max(value = 1000000, message = "1,000,000 이하여야 합니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "14500") Integer monthlyPrice,
		@NotNull(message = "적용 시작일은 필수입니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11-11") LocalDate effectiveFrom) {
}
