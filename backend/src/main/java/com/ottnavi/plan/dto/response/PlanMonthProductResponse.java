package com.ottnavi.plan.dto.response;

import com.ottnavi.plan.service.model.PlanDraftView;
import com.ottnavi.product.domain.enums.PriceSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 그 달에 구독할(또는 이미 이용 중인) 상품 하나(계약 PlanMonthProduct).
 *
 * @param productId     OTT 구독상품 ID
 * @param productName   상품(요금제) 이름
 * @param ottServiceIds 상품이 포함하는 서비스 ID들(단품은 1개)
 * @param appliedPrice  적용 월 요금(원). priceSource가 ALREADY_PAID·FREE면 그 달 비용에 넣지 않는다
 * @param priceSource   그 달의 가격 출처
 */
public record PlanMonthProductResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "101") long productId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "넷플릭스 스탠다드") String productName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> ottServiceIds,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "13500") int appliedPrice,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "ADMIN") PriceSource priceSource) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static PlanMonthProductResponse from(PlanDraftView.Product product) {
		return new PlanMonthProductResponse(product.productId(), product.productName(), product.ottServiceIds(),
				product.appliedPrice(), product.priceSource());
	}
}
