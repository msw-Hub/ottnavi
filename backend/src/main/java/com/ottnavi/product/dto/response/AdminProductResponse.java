package com.ottnavi.product.dto.response;

import com.ottnavi.product.domain.enums.ProductType;
import com.ottnavi.product.service.model.AdminProductView;
import com.ottnavi.product.service.model.ProductPriceView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * OTT 구독상품(단품) 하나와 월 구독요금 이력(계약 AdminProduct).
 *
 * @param productId    구독상품 ID
 * @param name         이름
 * @param productType  상품 종류(MVP는 SINGLE)
 * @param ottServiceId 속한 서비스 ID
 * @param isActive     false면 판매 종료(삭제하지 않고 남긴다)
 * @param prices       월 구독요금 이력. 적용 시작일이 빠른 순(가장 최근 요금 이력이 가장 최근)
 */
public record AdminProductResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "101") Long productId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "넷플릭스 스탠다드") String name,
		// ProductType에는 BUNDLE(2단계)도 있어, 계약의 SINGLE 한정과 맞추려고 문자열 enum으로 노출한다
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", allowableValues = "SINGLE", example = "SINGLE")
		ProductType productType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") Long ottServiceId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true") boolean isActive,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AdminProductPriceResponse> prices) {

	/** 서비스가 돌려준 값 묶음을 응답으로 바꾼다 */
	public static AdminProductResponse from(AdminProductView view) {
		List<AdminProductPriceResponse> prices = new ArrayList<>();
		for (ProductPriceView price : view.prices()) {
			prices.add(AdminProductPriceResponse.from(price));
		}
		return new AdminProductResponse(
				view.productId(), view.name(), view.productType(), view.ottServiceId(), view.isActive(), prices);
	}
}
