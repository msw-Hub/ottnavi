package com.ottnavi.product.dto.response;

import com.ottnavi.product.domain.enums.ProductType;
import com.ottnavi.product.service.model.AdminProductView;
import com.ottnavi.product.service.model.ProductPriceView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * OTT 구독상품(단품 또는 번들) 하나와 월 구독요금 이력(계약 AdminProduct).
 *
 * @param productId     구독상품 ID
 * @param name          이름
 * @param productType   상품 종류(SINGLE 또는 BUNDLE)
 * @param ottServiceIds 상품이 포함하는 서비스 ID 목록(오름차순). 단품은 1개, 번들은 2개 이상
 * @param isLowCost     저가형(광고형·베이직 등) 요금 분류. false면 표준
 * @param isActive      false면 판매 종료(삭제하지 않고 남긴다)
 * @param prices        월 구독요금 이력. 적용 시작일이 빠른 순(가장 최근 요금 이력이 가장 최근)
 */
public record AdminProductResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "101") Long productId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "넷플릭스 스탠다드") String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SINGLE") ProductType productType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> ottServiceIds,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false") boolean isLowCost,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true") boolean isActive,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AdminProductPriceResponse> prices) {

	/** 서비스가 돌려준 값 묶음을 응답으로 바꾼다 */
	public static AdminProductResponse from(AdminProductView view) {
		List<AdminProductPriceResponse> prices = new ArrayList<>();
		for (ProductPriceView price : view.prices()) {
			prices.add(AdminProductPriceResponse.from(price));
		}
		return new AdminProductResponse(view.productId(), view.name(), view.productType(), view.ottServiceIds(),
				view.isLowCost(), view.isActive(), prices);
	}
}
