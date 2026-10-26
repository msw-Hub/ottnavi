package com.ottnavi.plan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.WatchUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 전체 구독 비용 계산을 손계산 값과 비교한다.
 * 막으려는 사고: 무료 이용·구독 중 상품이 3개월치 전액으로 계산되는 일, 같은 상품이 여러 단위에 있어 중복으로 더해지는 일.
 */
class AllSubscribeCostCalculatorTest {

	@Test
	void 구독_상태가_섞이면_NONE은_3배_SUBSCRIBED는_2배_FREE는_0이다() {
		// given: NONE 10,000 / SUBSCRIBED 8,000 / FREE 5,000. 상품 1은 두 단위가 함께 가지므로 한 번만 센다
		List<OttProduct> products = new ArrayList<>();
		products.add(new OttProduct(1L, 10_000, OttProductCondition.NONE));
		products.add(new OttProduct(2L, 8_000, OttProductCondition.SUBSCRIBED));
		products.add(new OttProduct(3L, 5_000, OttProductCondition.FREE));
		List<WatchUnit> units = new ArrayList<>();
		units.add(new WatchUnit(1L, Priority.MUST, 100, Set.of(1L, 2L), false));
		units.add(new WatchUnit(2L, Priority.WANT, 100, Set.of(1L, 3L), false));

		// when
		int cost = AllSubscribeCostCalculator.calculate(units, products);

		// then: 30,000 + 16,000 + 0
		assertThat(cost).isEqualTo(46_000);
	}

	@Test
	void 번들은_전체_구독_비용에_넣지_않고_단품만_3개월치로_센다() {
		// given: 단품 두 개(서비스 1·2)와 그 둘을 묶은 번들. 두 단위가 번들도 시청 가능 상품으로 가진다
		List<OttProduct> products = new ArrayList<>();
		products.add(new OttProduct(1L, 13_500, OttProductCondition.NONE, List.of(1L)));
		products.add(new OttProduct(2L, 10_900, OttProductCondition.NONE, List.of(2L)));
		products.add(new OttProduct(3L, 16_000, OttProductCondition.NONE, List.of(1L, 2L)));
		List<WatchUnit> units = new ArrayList<>();
		units.add(new WatchUnit(1L, Priority.MUST, 100, Set.of(1L, 3L), false));
		units.add(new WatchUnit(2L, Priority.WANT, 100, Set.of(2L, 3L), false));

		// when
		int cost = AllSubscribeCostCalculator.calculate(units, products);

		// then: (13,500 + 10,900) × 3 = 73,200, 번들 16,000은 더하지 않는다
		assertThat(cost).isEqualTo(73_200);
	}

	@Test
	void 시청_가능_상품이_하나도_없으면_0이다() {
		// given: 후보 상품은 있지만 어느 단위도 볼 수 있는 상품이 없다
		List<OttProduct> products = new ArrayList<>();
		products.add(new OttProduct(1L, 10_000, OttProductCondition.NONE));
		List<WatchUnit> units = new ArrayList<>();
		units.add(new WatchUnit(1L, Priority.MUST, 100, Set.of(), true));

		// when
		int cost = AllSubscribeCostCalculator.calculate(units, products);

		// then
		assertThat(cost).isZero();
	}
}
