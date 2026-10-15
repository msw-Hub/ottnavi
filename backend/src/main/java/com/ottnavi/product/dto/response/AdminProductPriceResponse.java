package com.ottnavi.product.dto.response;

import com.ottnavi.product.service.model.ProductPriceView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 월 구독요금 한 줄(계약 AdminProductPrice). 종료일은 저장하지 않는다(다음 줄 시작일의 전날까지, 마지막 줄은 계속).
 *
 * @param priceId        월 구독요금 줄 ID
 * @param monthlyPrice   월 구독요금(원)
 * @param effectiveFrom  적용 시작일
 * @param lastVerifiedAt 마지막 확인 일시
 */
public record AdminProductPriceResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1011") Long priceId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "12000") Integer monthlyPrice,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-02") LocalDate effectiveFrom,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant lastVerifiedAt) {

	/** 서비스가 돌려준 값 묶음을 응답으로 바꾼다 */
	public static AdminProductPriceResponse from(ProductPriceView view) {
		return new AdminProductPriceResponse(
				view.priceId(), view.monthlyPrice(), view.effectiveFrom(), view.lastVerifiedAt());
	}
}
