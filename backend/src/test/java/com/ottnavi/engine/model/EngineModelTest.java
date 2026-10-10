package com.ottnavi.engine.model;

import static com.ottnavi.engine.EngineFixtures.product;
import static com.ottnavi.engine.EngineFixtures.unit;
import static com.ottnavi.engine.EngineFixtures.unknownUnit;
import static com.ottnavi.engine.model.Priority.MAYBE;
import static com.ottnavi.engine.model.Priority.MUST;
import static com.ottnavi.engine.model.Priority.WANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 엔진 입력·출력 record의 생성 시 검증, 방어적 복사, 정렬 보관(D8)을 확인한다. */
class EngineModelTest {

	private static final List<OttProduct> P1_ONLY = List.of(product(1, 10_000));

	// PlanInput: 정렬과 상품 집합 검증(결정 3·4)

	@Test
	void PlanInput은_생성할_때_우선순위_필요시간_ID_순으로_정렬해_보관한다() {
		PlanInput input = new PlanInput(List.of(
				unit(5, MAYBE, 10, 1L), unit(4, WANT, 90, 1L), unit(3, WANT, 90, 1L),
				unit(2, WANT, 30, 1L), unit(1, MUST, 500, 1L)), P1_ONLY, 0, 600);

		assertThat(input.units()).extracting(WatchUnit::id).containsExactly(1L, 2L, 3L, 4L, 5L);
	}

	@Test
	void 시청_가능_상품이_없는_단위는_우선순위와_무관하게_맨_뒤에_정렬된다() {
		// 사용자 결정 2026-10-09: 정렬 기준 ① "시청 가능 상품이 있는가". 없는 단위끼리는 다시 우선순위 → 필요 시간 → ID 순
		PlanInput input = new PlanInput(List.of(
				unknownUnit(9, MUST, 10), unit(8, MUST, 5), unit(7, MAYBE, 999, 1L), unit(6, MUST, 999, 1L)), P1_ONLY, 0, 600);

		assertThat(input.units()).extracting(WatchUnit::id).containsExactly(6L, 7L, 8L, 9L);
	}

	@Test
	void 시청_가능_상품_ID가_상품_목록에_없으면_PlanInput을_만들_수_없다() {
		List<WatchUnit> units = List.of(unit(1, WANT, 60, 1L, 2L));

		assertThatThrownBy(() -> new PlanInput(units, P1_ONLY, 0, 600))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("productId=2");
	}

	@Test
	void 상품_ID나_시청_단위_ID가_중복되면_PlanInput을_만들_수_없다() {
		List<OttProduct> duplicatedProducts = List.of(product(1, 10_000), product(1, 9_000));
		List<WatchUnit> duplicatedUnits = List.of(unit(1, WANT, 60, 1L), unit(1, MUST, 30, 1L));

		assertThatThrownBy(() -> new PlanInput(List.of(), duplicatedProducts, 0, 600)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PlanInput(duplicatedUnits, P1_ONLY, 0, 600)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 월_시청_시간이_0_이하이거나_예산이_음수이면_PlanInput을_만들_수_없다() {
		assertThatThrownBy(() -> new PlanInput(List.of(), P1_ONLY, 0, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PlanInput(List.of(), P1_ONLY, -1, 600)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PlanInput(null, P1_ONLY, 0, 600)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PlanInput(Arrays.asList((WatchUnit) null), P1_ONLY, 0, 600))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void PlanInput은_원본_목록이_바뀌어도_영향을_받지_않는다() {
		List<WatchUnit> units = new ArrayList<>(List.of(unit(1, WANT, 60, 1L)));
		List<OttProduct> products = new ArrayList<>(P1_ONLY);
		PlanInput input = new PlanInput(units, products, 0, 600);

		units.add(unit(2, WANT, 60, 1L));
		products.clear();

		assertThat(input.units()).hasSize(1);
		assertThat(input.products()).hasSize(1);
		assertThatThrownBy(() -> input.units().add(unit(3, WANT, 60, 1L))).isInstanceOf(UnsupportedOperationException.class);
	}

	// WatchUnit·OttProduct

	@Test
	void 시청_단위는_필요_시간_1분_이상_우선순위와_상품_집합이_있어야_한다() {
		assertThatThrownBy(() -> new WatchUnit(1, WANT, 0, Set.of(), false)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new WatchUnit(1, null, 60, Set.of(), false)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new WatchUnit(1, WANT, 60, null, false)).isInstanceOf(IllegalArgumentException.class);
		Set<Long> withNull = new HashSet<>(Arrays.asList(1L, null));
		assertThatThrownBy(() -> new WatchUnit(1, WANT, 60, withNull, false)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 시청_단위의_시청_가능_상품_집합은_원본이_바뀌어도_영향을_받지_않는다() {
		Set<Long> watchable = new HashSet<>(Set.of(1L));
		WatchUnit unit = new WatchUnit(1, WANT, 60, watchable, false);

		watchable.add(2L);

		assertThat(unit.watchableProductIds()).containsExactly(1L);
	}

	@Test
	void 상품은_가격이_0_이상이고_구독_상태가_있어야_한다() {
		assertThat(new OttProduct(1, 0, OttProductCondition.NONE).monthlyPrice()).isZero();
		assertThatThrownBy(() -> new OttProduct(1, -1, OttProductCondition.NONE)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OttProduct(1, 1_000, null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 상품_월_가격은_100만_원까지_허용하고_넘으면_예외() {
		// CodeRabbit PR #25: 엔진의 int 비용 합산 오버플로를 입력 단계에서 막는다
		assertThat(new OttProduct(1, OttProduct.MAX_MONTHLY_PRICE, OttProductCondition.NONE).monthlyPrice()).isEqualTo(1_000_000);
		assertThatThrownBy(() -> new OttProduct(1, 1_000_001, OttProductCondition.NONE))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("100만 원 이하")
				.hasMessageContaining("monthlyPrice=1000001");
	}

	// Selection

	@Test
	void 구독_조합은_3개월치여야_하고_null을_담을_수_없다() {
		assertThatThrownBy(() -> new Selection(List.of(Set.of(), Set.of()))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Selection(List.of(Set.of(), Set.of(), Set.of(), Set.of()))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Selection(Arrays.asList(Set.of(), null, Set.of()))).isInstanceOf(IllegalArgumentException.class);
		Set<Long> withNull = new HashSet<>(Arrays.asList(1L, null));
		assertThatThrownBy(() -> Selection.of(withNull, Set.of(), Set.of())).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 구독_조합은_달별_상품_ID를_오름차순으로_보관하고_원본_변경에_영향을_받지_않는다() {
		Set<Long> month0 = new HashSet<>(Set.of(30L, 10L, 20L));
		Selection selection = Selection.of(month0, Set.of(), Set.of());

		month0.add(5L);

		assertThat(selection.productIdsOf(0)).containsExactly(10L, 20L, 30L);
		assertThat(selection).isEqualTo(Selection.of(Set.of(10L, 20L, 30L), Set.of(), Set.of()));
		assertThatThrownBy(() -> selection.productIdsOf(0).add(1L)).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void 이미_정렬된_SortedIdSet은_구독_조합이_복사하지_않고_그대로_쓴다() {
		SortedIdSet month1 = SortedIdSet.of(Set.of(30L, 10L, 20L));

		Selection selection = Selection.of(Set.of(), month1, new HashSet<>(Set.of(2L, 1L)));

		assertThat(selection.productIdsOf(1)).isSameAs(month1); // 같은 인스턴스를 공유한다(조합마다 정렬 복사하지 않음)
		assertThat(selection.productIdsOf(1)).containsExactly(10L, 20L, 30L);
		assertThat(selection.productIdsOf(2)).containsExactly(1L, 2L); // 정렬 안 된 집합은 기존대로 정렬해 보관한다
		assertThat(selection).isEqualTo(Selection.of(Set.of(), Set.of(10L, 20L, 30L), Set.of(1L, 2L))); // 다른 Set 구현과도 같다
	}

	@Test
	void SortedIdSet은_오름차순_불변_집합이고_다른_Set과_같다() {
		SortedIdSet ids = SortedIdSet.of(List.of(5L, 3L, 5L, 9L)); // 중복은 한 번만 남는다

		assertThat(ids).containsExactly(3L, 5L, 9L);
		assertThat(ids).hasSize(3);
		assertThat(ids.contains(5L)).isTrue();
		assertThat(ids.contains(4L)).isFalse();
		assertThat(ids.contains("5")).isFalse();
		assertThat(ids).isEqualTo(Set.of(3L, 5L, 9L));
		assertThat(Set.of(3L, 5L, 9L)).isEqualTo(ids);
		assertThat(ids.hashCode()).isEqualTo(Set.of(3L, 5L, 9L).hashCode());
		assertThat(SortedIdSet.of(Set.of())).isSameAs(SortedIdSet.EMPTY);
		assertThatThrownBy(() -> ids.add(1L)).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> ids.iterator().remove()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> SortedIdSet.of(Arrays.asList(1L, null))).isInstanceOf(IllegalArgumentException.class);
	}

	// Assignment·UnitResult

	@Test
	void 배정_행은_달이_0에서_2이고_시간이_1분_이상이어야_한다() {
		assertThatThrownBy(() -> new Assignment(3, 1, 60)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Assignment(-1, 1, 60)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Assignment(0, 1, 0)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 단위_결과는_배정_여부와_이유_코드가_맞아야_한다() {
		List<Assignment> rows = List.of(new Assignment(0, 1, 60));

		assertThatThrownBy(() -> new UnitResult(1, true, 0, UnitReason.TIME, rows)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new UnitResult(1, false, null, null, List.of())).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 배정된_단위_결과는_다_본_달이_0에서_2이고_배정_행이_있어야_한다() {
		List<Assignment> rows = List.of(new Assignment(0, 1, 60));

		assertThatThrownBy(() -> new UnitResult(1, true, 3, null, rows)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new UnitResult(1, true, null, null, rows)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new UnitResult(1, true, 0, null, List.of())).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 미배정_단위_결과는_배정_행과_다_본_달이_없어야_한다() {
		List<Assignment> rows = List.of(new Assignment(0, 1, 60));

		assertThatThrownBy(() -> new UnitResult(1, false, null, UnitReason.TIME, rows)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new UnitResult(1, false, 0, UnitReason.TIME, List.of())).isInstanceOf(IllegalArgumentException.class);
		assertThat(new UnitResult(1, false, null, UnitReason.TIME, null).assignments()).isEmpty();
	}

	@Test
	void 우선순위_점수는_WANT_2_MAYBE_1이고_MUST는_점수에_넣지_않는다() {
		assertThat(Priority.MUST.score()).isZero();
		assertThat(Priority.WANT.score()).isEqualTo(2);
		assertThat(Priority.MAYBE.score()).isEqualTo(1);
	}
}
