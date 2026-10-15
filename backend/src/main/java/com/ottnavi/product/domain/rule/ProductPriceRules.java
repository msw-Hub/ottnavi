package com.ottnavi.product.domain.rule;

import com.ottnavi.global.error.ErrorCode;
import com.ottnavi.product.domain.entity.ProductPrice;
import com.ottnavi.product.exception.ProductPriceConflictException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 월 구독요금 이력의 규칙(PRD FR-19, ERD product_price). DB를 읽지 않고 넘겨받은 요금 이력만 보고 판단한다.
 *
 * <p>요금 이력은 종료일 없이 "다음 요금 이력 시작일의 전날까지" 적용되고, 맨 뒤에 시작일이 더 늦은 요금 이력으로만 추가한다.
 * 수정·삭제는 가장 최근 요금 이력만 허용하므로 기간이 겹치거나 비는 일이 구조상 없다.
 *
 * <p>예(오늘 2026-10-16, 요금 이력 = 2026-01-01, 2026-10-02, 2026-11-11):
 * <ul>
 *   <li>선택: 오늘 기준 → 2026-10-02 요금 이력(시작일이 오늘 이하인 것 중 가장 늦은 것)</li>
 *   <li>추가: 2026-11-11(같음) → PRICE_START_DATE_DUPLICATE, 2026-11-01(더 이른 날) → PRICE_START_DATE_NOT_AFTER_LAST</li>
 *   <li>수정·삭제: 2026-11-11 요금 이력만 가능, 나머지는 PRICE_NOT_LATEST</li>
 * </ul>
 */
public final class ProductPriceRules {

	private ProductPriceRules() {
	}

	/** 기준 날짜 이하의 시작일 중 가장 늦은 요금 이력을 고른다. 없으면(모든 요금 이력이 미래) 빈 값이다 */
	public static Optional<ProductPrice> selectOn(List<ProductPrice> prices, LocalDate date) {
		ProductPrice selected = null;
		for (ProductPrice price : prices) {
			if (price.getEffectiveFrom().isAfter(date)) {
				continue;
			}
			if (selected == null || price.getEffectiveFrom().isAfter(selected.getEffectiveFrom())) {
				selected = price;
			}
		}
		return Optional.ofNullable(selected);
	}

	/**
	 * 새 시작일이 바로 앞 요금 이력보다 늦은지 확인한다. 바로 앞 요금 이력이 없으면 첫 요금 이력이므로 시작일이 오늘 이하여야 한다.
	 *
	 * @param previousFrom 바로 앞 요금 이력의 시작일. 첫 요금 이력이면 null
	 * @param newFrom      새로 두려는 시작일
	 * @param today        오늘(KST)
	 */
	public static void validateStartDate(LocalDate previousFrom, LocalDate newFrom, LocalDate today) {
		// 1. 첫 요금 이력은 오늘 이하여야 한다(첫 요금 이력이 미래면 오늘 적용할 요금이 없다)
		if (previousFrom == null) {
			if (newFrom.isAfter(today)) {
				throw new ProductPriceConflictException(ErrorCode.PRICE_FIRST_START_DATE_IN_FUTURE);
			}
			return;
		}

		// 2. 바로 앞 요금 이력과 같은 날이면 중복, 바로 앞 요금 이력보다 이르면 순서 위반
		if (newFrom.isEqual(previousFrom)) {
			throw new ProductPriceConflictException(ErrorCode.PRICE_START_DATE_DUPLICATE);
		}
		if (newFrom.isBefore(previousFrom)) {
			throw new ProductPriceConflictException(ErrorCode.PRICE_START_DATE_NOT_AFTER_LAST);
		}
	}

	/** 요금 이력 목록에서 시작일이 가장 늦은(가장 최근) 요금 이력을 돌려준다. 요금 이력이 없으면 null이다 */
	public static ProductPrice latestOf(List<ProductPrice> prices) {
		ProductPrice latest = null;
		for (ProductPrice price : prices) {
			if (latest == null || price.getEffectiveFrom().isAfter(latest.getEffectiveFrom())) {
				latest = price;
			}
		}
		return latest;
	}

	/** target을 뺀 나머지 요금 이력 중 가장 늦은 시작일을 돌려준다. 다른 요금 이력이 없으면 null이다(target이 첫 요금 이력) */
	public static LocalDate previousFromOf(List<ProductPrice> prices, ProductPrice target) {
		LocalDate previous = null;
		for (ProductPrice price : prices) {
			if (price == target) {
				continue;
			}
			if (previous == null || price.getEffectiveFrom().isAfter(previous)) {
				previous = price.getEffectiveFrom();
			}
		}
		return previous;
	}

	/** 수정은 가장 최근 요금 이력만 허용한다 */
	public static void validateEditable(List<ProductPrice> prices, ProductPrice target) {
		if (latestOf(prices) != target) {
			throw new ProductPriceConflictException(ErrorCode.PRICE_NOT_LATEST);
		}
	}

	/** 삭제는 가장 최근 요금 이력만 허용하고, 요금 이력이 하나뿐이면 지울 수 없다 */
	public static void validateDeletable(List<ProductPrice> prices, ProductPrice target) {
		if (prices.size() <= 1) {
			throw new ProductPriceConflictException(ErrorCode.PRICE_LAST_ROW_CANNOT_DELETE);
		}
		if (latestOf(prices) != target) {
			throw new ProductPriceConflictException(ErrorCode.PRICE_NOT_LATEST);
		}
	}
}
