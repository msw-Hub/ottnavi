package com.ottnavi.plan.service.model;

import com.ottnavi.product.domain.enums.PriceSource;

/**
 * 계산 입력에 쓴 상품 하나의 가격 정보. plan_month_product 저장 때 가격 출처와 원래 월 요금을 그대로 쓴다.
 *
 * @param productId    상품 ID
 * @param ottServiceId 상품이 속한 서비스 ID
 * @param appliedPrice 원래 월 요금(사용자 수정 금액이 있으면 그 금액). FREE·ALREADY_PAID여도 0원으로 바꾸지 않는다
 * @param priceSource  가격 출처. FREE·ALREADY_PAID면 그 달 비용에 넣지 않는다(비용 제외는 엔진이 처리)
 */
public record ProductPriceInfo(
		long productId,
		long ottServiceId,
		int appliedPrice,
		PriceSource priceSource) {
}
