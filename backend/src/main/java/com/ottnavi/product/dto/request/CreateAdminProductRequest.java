package com.ottnavi.product.dto.request;

import com.ottnavi.product.domain.enums.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/**
 * OTT 구독상품 등록 요청(계약 CreateAdminProductRequest, 첫 월 구독요금 포함).
 *
 * @param name          이름. 서버가 앞뒤 공백을 제거한 뒤 1~100자인지 검증한다
 * @param productType   상품 종류. MVP는 SINGLE만 받는다
 * @param ottServiceId  속할 서비스 ID
 * @param monthlyPrice  첫 월 구독요금(원, 0~1,000,000)
 * @param effectiveFrom 첫 요금 이력의 적용 시작일(오늘 이하)
 */
public record CreateAdminProductRequest(
		@NotBlank(message = "이름은 필수입니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "디즈니+ 프리미엄") String name,
		@NotNull(message = "상품 종류는 필수입니다.")
		// ProductType에는 BUNDLE(2단계)도 있어, 계약의 SINGLE 한정과 맞추려고 문자열 enum으로 노출한다
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", allowableValues = "SINGLE", example = "SINGLE")
		ProductType productType,
		@NotNull(message = "서비스는 필수입니다.") @Positive(message = "1 이상이어야 합니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2") Long ottServiceId,
		@NotNull(message = "월 구독요금은 필수입니다.")
		@Min(value = 0, message = "0 이상이어야 합니다.") @Max(value = 1000000, message = "1,000,000 이하여야 합니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "13900") Integer monthlyPrice,
		@NotNull(message = "적용 시작일은 필수입니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-01") LocalDate effectiveFrom) {
}
