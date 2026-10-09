package com.ottnavi.engine.rule;

import static com.ottnavi.engine.EngineFixtures.free;
import static com.ottnavi.engine.EngineFixtures.product;
import static com.ottnavi.engine.EngineFixtures.selection;
import static com.ottnavi.engine.EngineFixtures.subscribed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.Selection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 목적함수 비교기 정답 테스트(안내서 5.1 T14, D6). 반환 규칙은 "양수 = 첫 번째가 더 좋다"이다.
 */
class EvaluationComparatorTest {

	private static final List<OttProduct> PRODUCTS = List.of(
			product(1, 10_000), product(2, 10_000), product(3, 10_000), subscribed(4, 10_000), free(5));
	private static final EvaluationComparator COMPARATOR = new EvaluationComparator(PRODUCTS);
	private static final Selection EMPTY = selection(Set.of(), Set.of(), Set.of());

	private static Evaluation evaluation(Selection selection, int mustCompleted, int score, int totalCost) {
		return new Evaluation(selection, mustCompleted, score, totalCost, List.of());
	}

	@Test
	void T14a_MUST_시청_완료_수가_많으면_점수와_비용보다_우선한다() {
		Evaluation more = evaluation(EMPTY, 2, 0, 20_000);
		Evaluation less = evaluation(EMPTY, 1, 10, 0);

		assertThat(COMPARATOR.compare(more, less)).isPositive();
		assertThat(COMPARATOR.compare(less, more)).isNegative();
	}

	@Test
	void T14b_MUST가_같으면_점수가_큰_쪽() {
		assertThat(COMPARATOR.compare(evaluation(EMPTY, 1, 5, 30_000), evaluation(EMPTY, 1, 4, 0))).isPositive();
	}

	@Test
	void T14c_MUST와_점수가_같으면_비용이_작은_쪽() {
		assertThat(COMPARATOR.compare(evaluation(EMPTY, 1, 5, 10_000), evaluation(EMPTY, 1, 5, 15_000))).isPositive();
	}

	@Test
	void T14d_비용까지_같으면_이른_달에_새로_결제하는_상품이_적은_쪽() {
		// 달별 새 결제 수 2,0,0 vs 1,1,0 → 뒤가 결제를 미룬다
		Evaluation payNow = evaluation(selection(Set.of(1L, 2L), Set.of(), Set.of()), 1, 5, 20_000);
		Evaluation payLater = evaluation(selection(Set.of(1L), Set.of(2L), Set.of()), 1, 5, 20_000);

		assertThat(COMPARATOR.compare(payNow, payLater)).isNegative();
		assertThat(COMPARATOR.compare(payLater, payNow)).isPositive();
	}

	@Test
	void T14e_결제_수까지_같으면_달별_상품_ID_사전순으로_작은_쪽() {
		Evaluation smaller = evaluation(selection(Set.of(1L), Set.of(2L), Set.of()), 1, 5, 20_000);
		Evaluation larger = evaluation(selection(Set.of(1L), Set.of(3L), Set.of()), 1, 5, 20_000);

		assertThat(COMPARATOR.compare(smaller, larger)).isPositive();
		assertThat(COMPARATOR.compare(larger, smaller)).isNegative();
	}

	@Test
	void 결제_미루기는_0번째_달의_구독_중_상품과_무료_상품을_새_결제로_세지_않는다() {
		// [4(구독 중), 5(무료)] 는 0번째 달 새 결제 0개, [1]은 1개
		Evaluation subscribedAndFree = evaluation(selection(Set.of(4L, 5L), Set.of(), Set.of()), 0, 0, 0);
		Evaluation newPayment = evaluation(selection(Set.of(1L), Set.of(), Set.of()), 0, 0, 0);

		assertThat(COMPARATOR.compare(subscribedAndFree, newPayment)).isPositive();
	}

	@Test
	void 결제_미루기는_1번째_달부터_구독_중_상품_유지를_새_결제로_센다() {
		Evaluation keepSubscribed = evaluation(selection(Set.of(), Set.of(4L), Set.of()), 0, 0, 10_000);
		Evaluation nothing = evaluation(selection(Set.of(), Set.of(), Set.of(1L)), 0, 0, 10_000);

		assertThat(COMPARATOR.compare(keepSubscribed, nothing)).isNegative();
	}

	@Test
	void 상품_ID_사전순에서는_앞부분이_같으면_짧은_쪽이_작다() {
		Evaluation shorter = evaluation(selection(Set.of(4L), Set.of(), Set.of()), 0, 0, 0);
		Evaluation longer = evaluation(selection(Set.of(4L, 5L), Set.of(), Set.of()), 0, 0, 0);

		assertThat(COMPARATOR.compare(shorter, longer)).isPositive();
	}

	@Test
	void 모든_기준이_같으면_0이고_가장_좋은_결과는_Collections_max로_고른다() {
		Evaluation best = evaluation(selection(Set.of(1L), Set.of(), Set.of()), 2, 3, 10_000);
		Evaluation same = evaluation(selection(Set.of(1L), Set.of(), Set.of()), 2, 3, 10_000);
		List<Evaluation> candidates = List.of(
				evaluation(EMPTY, 1, 9, 0), best, evaluation(selection(Set.of(2L), Set.of(), Set.of()), 2, 3, 10_000));

		assertThat(COMPARATOR.compare(best, same)).isZero();
		assertThat(Collections.max(candidates, COMPARATOR)).isSameAs(best);
	}

	@Test
	void 비교할_조합의_상품_ID가_상품_목록에_없으면_예외() {
		Evaluation unknown = evaluation(selection(Set.of(99L), Set.of(), Set.of()), 0, 0, 0);

		assertThatThrownBy(() -> COMPARATOR.compare(unknown, evaluation(EMPTY, 0, 0, 0)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
