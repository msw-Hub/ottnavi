package com.ottnavi.product.service.model;

import com.ottnavi.product.domain.enums.ProductType;
import java.util.List;

/**
 * 관리자 화면의 구독상품 하나와 월 구독요금 이력(서버 안에서 주고받는 값).
 *
 * @param productId  구독상품 ID
 * @param name       이름
 * @param productType 상품 종류(MVP는 SINGLE)
 * @param ottServiceId 속한 서비스 ID(단품은 구성 1행에서)
 * @param isActive   false면 판매 종료
 * @param prices     월 구독요금 이력(적용 시작일이 빠른 순)
 */
public record AdminProductView(
		long productId,
		String name,
		ProductType productType,
		long ottServiceId,
		boolean isActive,
		List<ProductPriceView> prices) {
}
