package com.ottnavi.product.service.model;

import com.ottnavi.product.domain.enums.PriceSource;
import com.ottnavi.user.domain.enums.ServiceSubscriptionStatus;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 내 서비스 요금 한 건(서버 안에서 주고받는 값).
 *
 * @param productId          구독상품 ID
 * @param productName        상품(요금제) 이름
 * @param ottServiceId       상품이 속한 서비스 ID
 * @param adminPrice         오늘(KST) 적용 중인 관리자 월 구독요금
 * @param userPrice          사용자가 고친 금액. 고치지 않았으면 null
 * @param appliedPrice       계산에 쓰는 금액(userPrice가 있으면 그 값, 없으면 adminPrice)
 * @param priceSource        적용 금액의 출처(ADMIN 또는 USER_OVERRIDE)
 * @param subscriptionStatus 사용자의 해당 서비스 이용 상태(행이 없으면 NOT_SUBSCRIBED)
 * @param effectiveFrom      adminPrice 요금 이력의 적용 시작일
 * @param lastVerifiedAt     adminPrice 요금 이력의 마지막 확인 일시
 */
public record MyServicePriceView(
		long productId,
		String productName,
		long ottServiceId,
		int adminPrice,
		Integer userPrice,
		int appliedPrice,
		PriceSource priceSource,
		ServiceSubscriptionStatus subscriptionStatus,
		LocalDate effectiveFrom,
		Instant lastVerifiedAt) {
}
