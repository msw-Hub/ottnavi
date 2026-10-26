package com.ottnavi.product.service.model;

import com.ottnavi.product.domain.enums.PriceSource;
import com.ottnavi.product.domain.enums.ProductType;
import com.ottnavi.user.domain.enums.ServiceSubscriptionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 내 서비스 요금 한 건(서버 안에서 주고받는 값).
 *
 * @param productId          구독상품 ID
 * @param productName        상품(요금제) 이름
 * @param productType        상품 종류(SINGLE 또는 BUNDLE)
 * @param ottServiceId       단품이 속한 서비스 ID. 번들이면 null
 * @param ottServiceIds      상품이 포함하는 서비스 ID 목록(오름차순, 단품은 1개)
 * @param isLowCost          저가형(광고형·베이직 등) 요금 분류
 * @param isCalcCandidate    사용자의 현재 모드 기준으로 이 상품이 플랜 계산 후보인지
 * @param adminPrice         오늘(KST) 적용 중인 관리자 월 구독요금
 * @param userPrice          사용자가 고친 금액. 고치지 않았으면 null
 * @param appliedPrice       계산에 쓰는 금액(userPrice가 있으면 그 값, 없으면 adminPrice)
 * @param priceSource        적용 금액의 출처(ADMIN 또는 USER_OVERRIDE)
 * @param subscriptionStatus 사용자의 해당 서비스 이용 상태(행이 없으면 NOT_SUBSCRIBED). 번들은 항상 NOT_SUBSCRIBED
 * @param effectiveFrom      adminPrice 요금 이력의 적용 시작일
 * @param lastVerifiedAt     adminPrice 요금 이력의 마지막 확인 일시
 */
public record MyServicePriceView(
		long productId,
		String productName,
		ProductType productType,
		Long ottServiceId,
		List<Long> ottServiceIds,
		boolean isLowCost,
		boolean isCalcCandidate,
		int adminPrice,
		Integer userPrice,
		int appliedPrice,
		PriceSource priceSource,
		ServiceSubscriptionStatus subscriptionStatus,
		LocalDate effectiveFrom,
		Instant lastVerifiedAt) {
}
