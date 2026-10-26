package com.ottnavi.plan.service.model;

import com.ottnavi.product.domain.enums.PriceSource;
import java.util.List;

/**
 * 계산 입력에 쓴 상품 하나의 가격 정보. plan_month_product 저장 때 가격 출처와 원래 월 요금을 그대로 쓴다.
 *
 * @param productId       상품 ID
 * @param productName     상품(요금제) 이름
 * @param ottServiceIds   상품이 포함하는 서비스 ID 목록(오름차순, 단품은 1개·번들은 2개 이상)
 * @param appliedPrice    원래 월 요금(사용자 수정 금액이 있으면 그 금액). FREE·ALREADY_PAID여도 0원으로 바꾸지 않는다
 * @param priceSource     구독 상태를 반영한 출처. 무료 이용이면 모든 달이 FREE, 구독 중이면 0번째 달만 ALREADY_PAID이고
 *                        1·2번째 달은 basePriceSource를 쓴다(달별 결정은 저장 매퍼가 한다). FREE·ALREADY_PAID인 달은 비용에 넣지 않는다(엔진이 처리)
 * @param basePriceSource 구독 상태와 무관한 기본 출처(ADMIN 또는 USER_OVERRIDE)
 */
public record ProductPriceInfo(
		long productId,
		String productName,
		List<Long> ottServiceIds,
		int appliedPrice,
		PriceSource priceSource,
		PriceSource basePriceSource) {
}
