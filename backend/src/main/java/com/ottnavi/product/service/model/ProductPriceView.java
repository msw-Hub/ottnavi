package com.ottnavi.product.service.model;

import com.ottnavi.product.domain.entity.ProductPrice;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 월 구독요금 한 줄(서버 안에서 주고받는 값).
 *
 * @param priceId        월 구독요금 줄 ID
 * @param monthlyPrice   월 구독요금(원)
 * @param effectiveFrom  적용 시작일(한국 날짜)
 * @param lastVerifiedAt 마지막 확인 일시
 */
public record ProductPriceView(long priceId, int monthlyPrice, LocalDate effectiveFrom, Instant lastVerifiedAt) {

	/** 엔티티를 값 묶음으로 바꾼다 */
	public static ProductPriceView from(ProductPrice price) {
		return new ProductPriceView(price.getId(), price.getMonthlyPrice(), price.getEffectiveFrom(),
				price.getLastVerifiedAt());
	}
}
