package com.ottnavi.product.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.global.error.ErrorCode;
import com.ottnavi.product.domain.entity.ProductPrice;
import com.ottnavi.product.exception.ProductPriceConflictException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 월 구독요금 이력 규칙 단위 시험(Task 059). DB 없이 요금 이력 목록만으로 판단한다 */
class ProductPriceRulesTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
	private static final Instant VERIFIED_AT = Instant.parse("2026-10-01T00:00:00Z");

	private ProductPrice price(int monthlyPrice, LocalDate effectiveFrom) {
		return ProductPrice.create(null, monthlyPrice, effectiveFrom, VERIFIED_AT);
	}

	/** 지난 요금 이력(2026-01-01), 오늘 이전에 시작한 요금 이력(2026-10-02), 미래 요금 이력(2026-11-11) 세 건 */
	private List<ProductPrice> threeRows() {
		List<ProductPrice> prices = new ArrayList<>();
		prices.add(price(12000, LocalDate.of(2026, 1, 1)));
		prices.add(price(13500, LocalDate.of(2026, 10, 2)));
		prices.add(price(14500, LocalDate.of(2026, 11, 11)));
		return prices;
	}

	@Test
	void 기준일_이하_시작일_중_가장_늦은_요금_이력을_고른다() {
		// given
		List<ProductPrice> prices = threeRows();

		// when
		ProductPrice selected = ProductPriceRules.selectOn(prices, TODAY).orElseThrow();

		// then
		assertThat(selected.getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 10, 2));
	}

	@Test
	void 시작일이_기준일과_같은_요금_이력도_적용된다() {
		// given
		List<ProductPrice> prices = threeRows();

		// when
		ProductPrice selected = ProductPriceRules.selectOn(prices, LocalDate.of(2026, 11, 11)).orElseThrow();

		// then
		assertThat(selected.getMonthlyPrice()).isEqualTo(14500);
	}

	@Test
	void 모든_요금_이력이_기준일보다_미래면_빈_값이다() {
		// given
		List<ProductPrice> prices = threeRows();

		// when
		// then
		assertThat(ProductPriceRules.selectOn(prices, LocalDate.of(2025, 12, 31))).isEmpty();
	}

	@Test
	void 첫_요금_이력의_시작일이_오늘이면_허용한다() {
		// given
		// when
		// then
		assertThatCode(() -> ProductPriceRules.validateStartDate(null, TODAY, TODAY)).doesNotThrowAnyException();
	}

	@Test
	void 첫_요금_이력의_시작일이_미래면_PRICE_FIRST_START_DATE_IN_FUTURE다() {
		// given
		// when
		// then
		assertThatThrownBy(() -> ProductPriceRules.validateStartDate(null, TODAY.plusDays(1), TODAY))
				.isInstanceOf(ProductPriceConflictException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PRICE_FIRST_START_DATE_IN_FUTURE);
	}

	@Test
	void 바로_앞_요금_이력보다_늦은_시작일은_미래여도_허용한다() {
		// given
		LocalDate previous = LocalDate.of(2026, 10, 2);

		// when
		// then
		assertThatCode(() -> ProductPriceRules.validateStartDate(previous, LocalDate.of(2026, 12, 1), TODAY))
				.doesNotThrowAnyException();
	}

	@Test
	void 바로_앞_요금_이력과_같은_시작일은_PRICE_START_DATE_DUPLICATE다() {
		// given
		LocalDate previous = LocalDate.of(2026, 10, 2);

		// when
		// then
		assertThatThrownBy(() -> ProductPriceRules.validateStartDate(previous, previous, TODAY))
				.isInstanceOf(ProductPriceConflictException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PRICE_START_DATE_DUPLICATE);
	}

	@Test
	void 바로_앞_요금_이력보다_이른_시작일은_PRICE_START_DATE_NOT_AFTER_LAST다() {
		// given
		LocalDate previous = LocalDate.of(2026, 10, 2);

		// when
		// then
		assertThatThrownBy(() -> ProductPriceRules.validateStartDate(previous, LocalDate.of(2026, 9, 1), TODAY))
				.isInstanceOf(ProductPriceConflictException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PRICE_START_DATE_NOT_AFTER_LAST);
	}

	@Test
	void 가장_최근_요금_이력은_수정할_수_있고_바로_앞_요금_이력은_PRICE_NOT_LATEST다() {
		// given
		List<ProductPrice> prices = threeRows();

		// when
		// then
		assertThatCode(() -> ProductPriceRules.validateEditable(prices, prices.get(2))).doesNotThrowAnyException();
		assertThatThrownBy(() -> ProductPriceRules.validateEditable(prices, prices.get(1)))
				.isInstanceOf(ProductPriceConflictException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PRICE_NOT_LATEST);
	}

	@Test
	void 가장_최근_요금_이력은_삭제할_수_있고_바로_앞_요금_이력은_PRICE_NOT_LATEST다() {
		// given
		List<ProductPrice> prices = threeRows();

		// when
		// then
		assertThatCode(() -> ProductPriceRules.validateDeletable(prices, prices.get(2))).doesNotThrowAnyException();
		assertThatThrownBy(() -> ProductPriceRules.validateDeletable(prices, prices.get(0)))
				.isInstanceOf(ProductPriceConflictException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PRICE_NOT_LATEST);
	}

	@Test
	void 요금_이력이_하나뿐이면_삭제할_수_없다() {
		// given
		List<ProductPrice> prices = new ArrayList<>();
		prices.add(price(12000, LocalDate.of(2026, 1, 1)));

		// when
		// then
		assertThatThrownBy(() -> ProductPriceRules.validateDeletable(prices, prices.get(0)))
				.isInstanceOf(ProductPriceConflictException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PRICE_LAST_ROW_CANNOT_DELETE);
	}

	@Test
	void 수정하는_요금_이력의_바로_앞_시작일을_돌려주고_요금_이력이_하나면_null이다() {
		// given
		List<ProductPrice> prices = threeRows();
		List<ProductPrice> single = new ArrayList<>();
		single.add(prices.get(0));

		// when
		LocalDate previousOfLast = ProductPriceRules.previousFromOf(prices, prices.get(2));
		LocalDate previousOfOnly = ProductPriceRules.previousFromOf(single, single.get(0));

		// then
		assertThat(previousOfLast).isEqualTo(LocalDate.of(2026, 10, 2));
		assertThat(previousOfOnly).isNull();
	}
}
