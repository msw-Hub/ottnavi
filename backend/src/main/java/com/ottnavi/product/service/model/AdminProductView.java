package com.ottnavi.product.service.model;

import com.ottnavi.product.domain.enums.ProductType;
import java.util.List;

/**
 * 관리자 화면의 구독상품 하나와 월 구독요금 이력(서버 안에서 주고받는 값).
 *
 * @param productId     구독상품 ID
 * @param name          이름
 * @param productType   상품 종류(SINGLE 또는 BUNDLE)
 * @param ottServiceIds 포함 서비스 ID 목록(오름차순, 단품은 1개)
 * @param isLowCost     저가형 요금 분류
 * @param isActive      false면 판매 종료
 * @param prices        월 구독요금 이력(적용 시작일이 빠른 순)
 */
public record AdminProductView(
		long productId,
		String name,
		ProductType productType,
		List<Long> ottServiceIds,
		boolean isLowCost,
		boolean isActive,
		List<ProductPriceView> prices) {
}
