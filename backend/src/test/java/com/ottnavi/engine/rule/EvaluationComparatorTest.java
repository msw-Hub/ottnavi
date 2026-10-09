package com.ottnavi.engine.rule;

import static com.ottnavi.engine.EngineFixtures.free;
import static com.ottnavi.engine.EngineFixtures.input;
import static com.ottnavi.engine.EngineFixtures.product;
import static com.ottnavi.engine.EngineFixtures.selection;
import static com.ottnavi.engine.EngineFixtures.subscribed;
import static com.ottnavi.engine.EngineFixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.Selection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 목적함수 비교기 정답 테스트(안내서 5.1 T14, D6). 반환 규칙은 "양수 = 첫 번째가 더 좋다"이다.
 * ④ 조기 시청은 시청 완료 달이 필요해 실제 Evaluator로 평가한 결과로 단언한다(손계산 값은 각 테스트 주석).
 */
class EvaluationComparatorTest {

	private static final List<OttProduct> PRODUCTS = List.of(
			product(1, 10_000), product(2, 10_000), product(3, 10_000), subscribed(4, 10_000), free(5));
	private static final EvaluationComparator COMPARATOR = new EvaluationComparator(input(600, PRODUCTS)); // 시청 단위가 없는 입력: 합성 Evaluation(units 빈 목록)과 짝이 맞는다
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

	/** 평가기로 조합을 평가한다. */
	private static Evaluation evaluate(PlanInput input, Selection selection) {
		return new Evaluator(input).evaluate(selection);
	}

	@Test
	void T14d_비용까지_같으면_조기_시청_점수_합이_먼저_다른_달에서_큰_쪽() {
		// A(WANT 200분)는 N(상품 1)으로만, B(WANT 200분)는 T(상품 2)로만 볼 수 있다. N·T 각 10,000원, 월 600분
		PlanInput input = input(600, List.of(product(1, 10_000), product(2, 10_000)),
				unit(10, Priority.WANT, 200, 1L), unit(11, Priority.WANT, 200, 2L));
		EvaluationComparator comparator = new EvaluationComparator(input);
		Evaluation together = evaluate(input, selection(Set.of(1L, 2L), Set.of(), Set.of())); // [N,T],[],[]
		Evaluation split = evaluate(input, selection(Set.of(1L), Set.of(2L), Set.of()));      // [N],[T],[]

		// 둘 다 must 0·점수 4·비용 20,000. 0번째 달까지 점수 합이 4 대 2라서 앞이 좋다(새 결제 수 2,0,0 대 1,1,0이라 결제 미루기로는 뒤가 좋지만 ④가 먼저다)
		assertThat(together.score()).isEqualTo(4);
		assertThat(split.score()).isEqualTo(4);
		assertThat(together.totalCost()).isEqualTo(20_000);
		assertThat(split.totalCost()).isEqualTo(20_000);
		assertThat(comparator.compare(together, split)).isPositive();
		assertThat(comparator.compare(split, together)).isNegative();
	}

	@Test
	void T14d_조기_시청은_앞_달이_같으면_다음_달의_누적을_비교한다() {
		PlanInput input = input(600, List.of(product(1, 10_000), product(2, 10_000)),
				unit(10, Priority.WANT, 200, 1L), unit(11, Priority.WANT, 200, 2L));
		EvaluationComparator comparator = new EvaluationComparator(input);
		Evaluation earlier = evaluate(input, selection(Set.of(1L), Set.of(2L), Set.of())); // 달별 누적 점수 2, 4, 4
		Evaluation later = evaluate(input, selection(Set.of(1L), Set.of(), Set.of(2L)));    // 달별 누적 점수 2, 2, 4

		assertThat(comparator.compare(earlier, later)).isPositive();
		assertThat(comparator.compare(later, earlier)).isNegative();
	}

	@Test
	void T14d3_조기_시청은_점수_합보다_꼭_작품_수를_먼저_비교한다() {
		// A(MUST 200분)는 N으로만, B(WANT 200분)는 T로만 볼 수 있다
		PlanInput input = input(600, List.of(product(1, 10_000), product(2, 10_000)),
				unit(10, Priority.MUST, 200, 1L), unit(11, Priority.WANT, 200, 2L));
		EvaluationComparator comparator = new EvaluationComparator(input);
		Evaluation wantFirst = evaluate(input, selection(Set.of(2L), Set.of(1L), Set.of())); // [T],[N],[]
		Evaluation mustFirst = evaluate(input, selection(Set.of(1L), Set.of(2L), Set.of())); // [N],[T],[]

		// 둘 다 must 1·점수 2·비용 20,000. 0번째 달까지 (꼭 0, 점수 2) 대 (꼭 1, 점수 0)이라 꼭 작품 수가 먼저라서 뒤가 좋다
		assertThat(comparator.compare(wantFirst, mustFirst)).isNegative();
		assertThat(comparator.compare(mustFirst, wantFirst)).isPositive();
	}

	@Test
	void T14d2_조기_시청까지_같으면_결제를_미루는_쪽() {
		// A(WANT 200분)만 있고 N(상품 1)으로만 볼 수 있다. T(상품 2)로 볼 작품은 없다
		PlanInput input = input(600, List.of(product(1, 10_000), product(2, 10_000)), unit(10, Priority.WANT, 200, 1L));
		EvaluationComparator comparator = new EvaluationComparator(input);
		Evaluation payLater = evaluate(input, selection(Set.of(1L), Set.of(), Set.of(2L))); // 새 결제 수 1,0,1
		Evaluation payNow = evaluate(input, selection(Set.of(1L), Set.of(2L), Set.of()));   // 새 결제 수 1,1,0

		// 시청 완료 달(0번째)·점수·비용 20,000이 같아 ④까지 동점이고, 결제 수가 먼저 다른 1번째 달에서 0 < 1이라 앞이 좋다
		assertThat(comparator.compare(payLater, payNow)).isPositive();
		assertThat(comparator.compare(payNow, payLater)).isNegative();
	}

	@Test
	void 평가_결과의_단위_수가_입력과_다르면_예외() {
		PlanInput input = input(600, List.of(product(1, 10_000)), unit(10, Priority.WANT, 200, 1L));
		EvaluationComparator comparator = new EvaluationComparator(input);
		Evaluation mismatched = evaluation(EMPTY, 0, 0, 0); // 단위 결과 0개

		assertThatThrownBy(() -> comparator.compare(mismatched, mismatched)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 결제_미루기_달별_새_결제_수가_2_0_0_대_1_1_0이면_뒤가_좋다() {
		// 시청 단위가 없어 ④가 늘 동점이고 ⑤만 본다
		Evaluation payNow = evaluation(selection(Set.of(1L, 2L), Set.of(), Set.of()), 1, 5, 20_000);
		Evaluation payLater = evaluation(selection(Set.of(1L), Set.of(2L), Set.of()), 1, 5, 20_000);

		assertThat(COMPARATOR.compare(payNow, payLater)).isNegative();
		assertThat(COMPARATOR.compare(payLater, payNow)).isPositive();
	}

	@Test
	void T14e_결제_수까지_같으면_달별_상품_ID_사전순으로_작은_쪽() {
		// T14d2와 같은 입력에서 상품 2·3은 같은 가격의 쓸모없는 상품이다. 조기 시청·결제 수가 모두 같다
		PlanInput input = input(600, List.of(product(1, 10_000), product(2, 10_000), product(3, 10_000)), unit(10, Priority.WANT, 200, 1L));
		EvaluationComparator comparator = new EvaluationComparator(input);
		Evaluation smaller = evaluate(input, selection(Set.of(1L), Set.of(2L), Set.of()));
		Evaluation larger = evaluate(input, selection(Set.of(1L), Set.of(3L), Set.of()));

		assertThat(comparator.compare(smaller, larger)).isPositive();
		assertThat(comparator.compare(larger, smaller)).isNegative();
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
