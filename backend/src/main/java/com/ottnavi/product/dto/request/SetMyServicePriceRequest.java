package com.ottnavi.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 내 월 요금 설정 요청(계약 SetMyServicePriceRequest). 지우려면 DELETE를 쓴다.
 *
 * @param monthlyPrice 월 요금(원, 0 이상 100만 이하 정수)
 */
public record SetMyServicePriceRequest(
		@NotNull(message = "월 요금은 필수입니다.")
		@Min(value = 0, message = "0 이상이어야 합니다.") @Max(value = 1000000, message = "1,000,000 이하여야 합니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", maximum = "1000000", example = "9900")
		Integer monthlyPrice) {
}
