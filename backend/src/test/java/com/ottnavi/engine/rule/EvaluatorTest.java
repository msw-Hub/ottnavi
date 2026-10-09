package com.ottnavi.engine.rule;

import static com.ottnavi.engine.EngineFixtures.assertInvariants;
import static com.ottnavi.engine.EngineFixtures.free;
import static com.ottnavi.engine.EngineFixtures.input;
import static com.ottnavi.engine.EngineFixtures.product;
import static com.ottnavi.engine.EngineFixtures.resultOf;
import static com.ottnavi.engine.EngineFixtures.selection;
import static com.ottnavi.engine.EngineFixtures.subscribed;
import static com.ottnavi.engine.EngineFixtures.unit;
import static com.ottnavi.engine.EngineFixtures.unknownUnit;
import static com.ottnavi.engine.model.Priority.MAYBE;
import static com.ottnavi.engine.model.Priority.MUST;
import static com.ottnavi.engine.model.Priority.WANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.engine.EngineFixtures;
import com.ottnavi.engine.model.Assignment;
import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.UnitReason;
import com.ottnavi.engine.model.UnitResult;
import com.ottnavi.engine.model.WatchUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 평가기(배정 규칙·이유 코드·점수·비용) 정답 테스트. 안내서 5.1절 T1~T13, T15~T17과 사용자 결정(결정 1·2·4, 정렬 기준 ①)을 손으로 계산한 답과 비교한다.
 * 모든 케이스에서 불변식 3가지(달별 합 ≤ M, 배정 합 = 필요 시간, 미배정 행 0개)와 긴 시즌 120분 조각 규칙을 함께 단언한다.
 */
class EvaluatorTest {

	private static final long P1 = 1L;   // 기본 상품 p1(10,000원)
	private static final long P2 = 2L;
	private static final long P3 = 3L;
	private static final long N = 10L;   // 넷플릭스 상품(T15~T17)
	private static final long T = 20L;   // 티빙 상품(T15~T17)
	private static final int N_PRICE = 13_500;
	private static final int T_PRICE = 13_900;

	private static final List<OttProduct> P1_ONLY = List.of(product(P1, 10_000));
	private static final List<OttProduct> NETFLIX_TVING = List.of(product(N, N_PRICE), product(T, T_PRICE));

	/** 평가하고 불변식을 함께 확인한다. */
	private static Evaluation evaluate(PlanInput input, Selection selection) {
		Evaluation evaluation = new Evaluator(input).evaluate(selection);
		assertInvariants(input, evaluation);
		return evaluation;
	}

	private static Set<Long> ids(Long... ids) {
		return Set.of(ids);
	}

	// 안내서 5.1 T1~T13

	@Test
	void T1_모두_한_달에_들어가면_0번째_달에_배정되고_MUST는_시청_완료_수로_나머지는_점수로_센다() {
		PlanInput input = input(600, P1_ONLY, unit(1, MUST, 120, P1), unit(2, WANT, 100, P1), unit(3, MAYBE, 90, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(), ids()));

		assertThat(evaluation.units()).allSatisfy(result -> {
			assertThat(result.scheduled()).isTrue();
			assertThat(result.completedMonthIndex()).isZero();
		});
		assertThat(evaluation.units()).flatExtracting(UnitResult::assignments)
				.extracting(Assignment::minutes).containsExactly(120, 100, 90); // 합 310분
		assertThat(evaluation.mustCompleted()).isEqualTo(1);
		assertThat(evaluation.score()).isEqualTo(3);
		assertThat(evaluation.totalCost()).isEqualTo(10_000);
	}

	@Test
	void T2_남은_시간보다_긴_한_달_이내_단위는_나누지_않고_TIME() {
		PlanInput input = input(200, P1_ONLY, unit(1, MUST, 120, P1), unit(2, WANT, 100, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(), ids()));

		assertThat(resultOf(evaluation, 1).assignments()).containsExactly(new Assignment(0, P1, 120));
		assertThat(resultOf(evaluation, 2).reason()).isEqualTo(UnitReason.TIME);
		assertThat(evaluation.mustCompleted()).isEqualTo(1);
		assertThat(evaluation.score()).isZero();
	}

	@Test
	void T3_시청_가능_상품이_어느_달에도_선택되지_않으면_BUDGET() {
		PlanInput input = input(600, List.of(product(P1, 10_000), product(P2, 9_000)), unit(4, WANT, 60, P2));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1), ids(P1)));

		assertThat(resultOf(evaluation, 4).reason()).isEqualTo(UnitReason.BUDGET);
		assertThat(resultOf(evaluation, 4).scheduled()).isFalse();
	}

	@Test
	void T4_시청_가능_상품이_없으면_조합과_무관하게_NO_PROVIDER_또는_UNKNOWN() {
		PlanInput input = input(600, P1_ONLY, unit(5, WANT, 60), unknownUnit(6, WANT, 60));

		// 어떤 조합이든 BUDGET·TIME보다 먼저 판정된다
		for (Selection selection : List.of(selection(ids(), ids(), ids()), selection(ids(P1), ids(P1), ids(P1)))) {
			Evaluation evaluation = evaluate(input, selection);
			assertThat(resultOf(evaluation, 5).reason()).isEqualTo(UnitReason.NO_PROVIDER);
			assertThat(resultOf(evaluation, 6).reason()).isEqualTo(UnitReason.UNKNOWN);
		}
	}

	@Test
	void T4_보충_결과_목록은_시청_가능_상품이_없는_단위를_우선순위와_무관하게_맨_뒤에_둔다() {
		// 정렬 기준 ①(사용자 결정 2026-10-09): MUST라도 시청 가능 상품이 없으면 WANT·MAYBE 뒤에 온다. 배정 결과는 바뀌지 않는다
		PlanInput input = input(600, P1_ONLY, unknownUnit(1, MUST, 30), unit(2, MUST, 40), unit(3, MAYBE, 500, P1), unit(4, WANT, 60, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(), ids()));

		// 앞: WANT(4) → MAYBE(3). 뒤(시청 가능 상품 없음)끼리는 다시 필요 시간 순: 30분(1) → 40분(2)
		assertThat(evaluation.units()).extracting(UnitResult::watchUnitId).containsExactly(4L, 3L, 1L, 2L);
		assertThat(evaluation.units()).extracting(UnitResult::reason)
				.containsExactly(null, null, UnitReason.UNKNOWN, UnitReason.NO_PROVIDER);
		assertThat(evaluation.mustCompleted()).isZero();
		assertThat(evaluation.score()).isEqualTo(3);
	}

	@Test
	void T4_보충_모름이_있어도_시청_가능_상품이_있으면_UNKNOWN이_아니라_BUDGET() {
		PlanInput input = input(600, List.of(product(P1, 10_000), product(P2, 9_000)), unknownUnit(7, WANT, 60, P2));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(), ids()));

		assertThat(resultOf(evaluation, 7).reason()).isEqualTo(UnitReason.BUDGET);
	}

	@Test
	void T5_긴_시즌은_연속된_달에_나눠_배정하고_다_본_달에_시청_완료한다() {
		PlanInput input = input(600, P1_ONLY, unit(1, MUST, 1200, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1), ids()));

		UnitResult season = resultOf(evaluation, 1);
		assertThat(season.assignments()).containsExactly(new Assignment(0, P1, 600), new Assignment(1, P1, 600));
		assertThat(season.completedMonthIndex()).isEqualTo(1);
		assertThat(evaluation.mustCompleted()).isEqualTo(1);
		assertThat(evaluation.totalCost()).isEqualTo(20_000);
	}

	@Test
	void T6_선택된_달이_연속이_아니면_긴_시즌을_나눌_수_없어_TIME() {
		PlanInput input = input(600, P1_ONLY, unit(1, MUST, 1200, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(), ids(P1)));

		assertThat(resultOf(evaluation, 1).reason()).isEqualTo(UnitReason.TIME);
		assertThat(resultOf(evaluation, 1).assignments()).isEmpty();
	}

	@Test
	void T7_3개월_용량보다_긴_시즌은_TIME() {
		PlanInput input = input(600, P1_ONLY, unit(1, WANT, 2000, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1), ids(P1)));

		assertThat(resultOf(evaluation, 1).reason()).isEqualTo(UnitReason.TIME);
	}

	@Test
	void T8_우선순위와_시간이_같으면_watchUnitId가_작은_단위가_먼저_배정된다() {
		// 일부러 id 5를 먼저 넣어 정렬 누락을 잡는다
		PlanInput input = input(100, P1_ONLY, unit(5, WANT, 90, P1), unit(3, WANT, 90, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(), ids()));

		assertThat(resultOf(evaluation, 3).assignments()).containsExactly(new Assignment(0, P1, 90));
		assertThat(resultOf(evaluation, 5).reason()).isEqualTo(UnitReason.TIME);
		assertThat(evaluation.score()).isEqualTo(2);
	}

	@Test
	void T9_같은_우선순위는_필요_시간이_짧은_단위가_먼저_배정된다() {
		// 일부러 긴 단위(id 1)를 먼저 넣는다. id 순으로 배정하면 U1이 들어가 결과가 달라진다
		PlanInput input = input(250, P1_ONLY, unit(1, WANT, 200, P1), unit(2, WANT, 100, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(), ids()));

		assertThat(evaluation.units()).extracting(UnitResult::watchUnitId).containsExactly(2L, 1L);
		assertThat(resultOf(evaluation, 2).assignments()).containsExactly(new Assignment(0, P1, 100));
		assertThat(resultOf(evaluation, 1).reason()).isEqualTo(UnitReason.TIME);
	}

	@Test
	void T10_시청_완료하지_못한_긴_시즌이_차지했던_시간은_풀려서_다음_단위가_쓴다() {
		PlanInput input = input(600, P1_ONLY, unit(1, WANT, 1200, P1), unit(2, MAYBE, 300, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(), ids()));

		assertThat(resultOf(evaluation, 1).reason()).isEqualTo(UnitReason.TIME);
		assertThat(resultOf(evaluation, 2).assignments()).containsExactly(new Assignment(0, P1, 300));
		assertThat(evaluation.score()).isEqualTo(1);
	}

	@Test
	void T11_구독_중인_상품은_0번째_달_비용이_0이고_다음_달부터_가격이_든다() {
		PlanInput input = input(600, List.of(subscribed(P1, 10_000)), unit(1, WANT, 60, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1), ids()));

		assertThat(evaluation.totalCost()).isEqualTo(10_000);
	}

	@Test
	void T12_무료_이용_상품은_선택하지_않아도_모든_달에_비용_0으로_볼_수_있다() {
		PlanInput input = input(600, List.of(product(P1, 10_000), free(P3)), unit(1, WANT, 60, P3));

		Evaluation evaluation = evaluate(input, selection(ids(), ids(), ids()));

		assertThat(resultOf(evaluation, 1).assignments()).containsExactly(new Assignment(0, P3, 60));
		assertThat(evaluation.totalCost()).isZero();
	}

	@Test
	void T12_보충_무료_이용_상품만으로도_긴_시즌을_3개월에_나눠_볼_수_있다() {
		PlanInput input = input(600, List.of(free(P3)), unit(1, WANT, 1500, P3));

		Evaluation evaluation = evaluate(input, selection(ids(), ids(), ids()));

		assertThat(resultOf(evaluation, 1).assignments()).containsExactly(
				new Assignment(0, P3, 600), new Assignment(1, P3, 600), new Assignment(2, P3, 300));
		assertThat(evaluation.totalCost()).isZero();
	}

	@Test
	void T13_모름은_시청_가능으로_세지_않아_UNKNOWN() {
		PlanInput input = input(600, P1_ONLY, unknownUnit(1, WANT, 60));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(), ids()));

		assertThat(resultOf(evaluation, 1).reason()).isEqualTo(UnitReason.UNKNOWN);
	}

	// 안내서 5.1 T15~T17 (긴 시즌 B안: 서비스와 상관없이 연속된 달)

	@Test
	void T15_서비스가_달라도_연속된_달이면_긴_시즌을_이어_본다() {
		PlanInput input = input(600, NETFLIX_TVING,
				unit(1, WANT, 200, N), unit(2, WANT, 200, T), unit(3, WANT, 800, N, T));

		Evaluation evaluation = evaluate(input, selection(ids(N), ids(T), ids()));

		assertThat(resultOf(evaluation, 1).assignments()).containsExactly(new Assignment(0, N, 200));
		assertThat(resultOf(evaluation, 2).assignments()).containsExactly(new Assignment(1, T, 200));
		UnitResult season = resultOf(evaluation, 3);
		assertThat(season.assignments()).containsExactly(new Assignment(0, N, 400), new Assignment(1, T, 400));
		assertThat(season.completedMonthIndex()).isEqualTo(1);
		assertThat(evaluation.score()).isEqualTo(6);
		assertThat(evaluation.totalCost()).isEqualTo(N_PRICE + T_PRICE);
	}

	@Test
	void T16_같은_달에_두_서비스를_결제하면_시간은_함께_세고_둘_다_볼_수_있으면_ID가_작은_상품으로_기록한다() {
		PlanInput input = input(600, NETFLIX_TVING,
				unit(1, WANT, 200, N), unit(2, WANT, 200, T), unit(3, WANT, 200, T, N));

		Evaluation evaluation = evaluate(input, selection(ids(N, T), ids(), ids()));

		assertThat(evaluation.units()).allSatisfy(result -> assertThat(result.completedMonthIndex()).isZero());
		assertThat(resultOf(evaluation, 3).assignments()).containsExactly(new Assignment(0, N, 200)); // D9: N(10) < T(20)
		assertThat(evaluation.score()).isEqualTo(6);
		assertThat(evaluation.totalCost()).isEqualTo(N_PRICE + T_PRICE);
	}

	@Test
	void T17_서비스와_상관없이도_선택된_달이_연속이_아니면_긴_시즌은_TIME() {
		PlanInput input = input(600, NETFLIX_TVING, unit(1, MUST, 1200, N, T));

		Evaluation evaluation = evaluate(input, selection(ids(N), ids(), ids(T)));

		assertThat(resultOf(evaluation, 1).reason()).isEqualTo(UnitReason.TIME);
		assertThat(resultOf(evaluation, 1).assignments()).isEmpty();
	}

	// 사용자 결정 1(2026-10-09): 긴 시즌은 실제로 시청하는 달도 연속이어야 한다

	@Test
	void 결정1_가운데_달이_이미_꽉_차_0분이_되면_앞뒤_달로_나눠_볼_수_없어_TIME이고_시간은_풀린다() {
		// p1은 세 달 모두, p2는 1번째 달만 선택. MUST A(600분)가 1번째 달을 다 채운다
		PlanInput input = input(600, List.of(product(P1, 10_000), product(P2, 9_000)),
				unit(1, MUST, 600, P2), unit(2, WANT, 1000, P1), unit(3, MAYBE, 300, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1, P2), ids(P1)));

		assertThat(resultOf(evaluation, 1).assignments()).containsExactly(new Assignment(1, P2, 600));
		// 0번째 달 600 + 1번째 달 0 + 2번째 달 400은 실제 시청 달이 끊기므로 허용하지 않는다
		assertThat(resultOf(evaluation, 2).reason()).isEqualTo(UnitReason.TIME);
		// 시즌이 시험 배정했던 0번째 달 시간이 풀려 L이 0번째 달에 들어간다
		assertThat(resultOf(evaluation, 3).assignments()).containsExactly(new Assignment(0, P1, 300));
		assertThat(evaluation.mustCompleted()).isEqualTo(1);
		assertThat(evaluation.score()).isEqualTo(1);
	}

	@Test
	void 결정1_시작_달이_꽉_찼으면_다음_달부터_연속으로_이어_본다() {
		PlanInput input = input(600, P1_ONLY, unit(1, MUST, 600, P1), unit(2, WANT, 1000, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1), ids(P1)));

		assertThat(resultOf(evaluation, 1).assignments()).containsExactly(new Assignment(0, P1, 600));
		assertThat(resultOf(evaluation, 2).assignments()).containsExactly(new Assignment(1, P1, 600), new Assignment(2, P1, 400));
		assertThat(resultOf(evaluation, 2).completedMonthIndex()).isEqualTo(2);
	}

	// 사용자 결정 2(D2): 0번째 달의 SUBSCRIBED는 선택에 없어도 비용 0으로 볼 수 있다

	@Test
	void 결정2_구독_중인_상품은_0번째_달에_선택하지_않아도_비용_0으로_볼_수_있다() {
		PlanInput input = input(600, List.of(subscribed(P1, 10_000)), unit(1, WANT, 60, P1));

		Evaluation evaluation = evaluate(input, selection(ids(), ids(), ids()));

		assertThat(resultOf(evaluation, 1).assignments()).containsExactly(new Assignment(0, P1, 60));
		assertThat(evaluation.totalCost()).isZero();
	}

	@Test
	void 결정2_구독_중인_상품도_1번째_달부터는_선택해야_유지되고_가격이_든다() {
		PlanInput input = input(600, List.of(subscribed(P1, 10_000)), unit(1, WANT, 1200, P1));

		// 1번째 달을 유지하면 0번째 달(자동)과 이어 본다
		Evaluation kept = evaluate(input, selection(ids(), ids(P1), ids()));
		assertThat(resultOf(kept, 1).assignments()).containsExactly(new Assignment(0, P1, 600), new Assignment(1, P1, 600));
		assertThat(kept.totalCost()).isEqualTo(10_000);

		// 1번째 달을 해지하고 2번째 달에 다시 결제하면 연속이 아니라 못 나눈다
		Evaluation cancelled = evaluate(input, selection(ids(), ids(), ids(P1)));
		assertThat(resultOf(cancelled, 1).reason()).isEqualTo(UnitReason.TIME);
		assertThat(cancelled.totalCost()).isEqualTo(10_000);

		// 아무 달도 유지하지 않으면 0번째 달만 볼 수 있어 시간이 모자란다(BUDGET이 아니라 TIME)
		Evaluation none = evaluate(input, selection(ids(), ids(), ids()));
		assertThat(resultOf(none, 1).reason()).isEqualTo(UnitReason.TIME);
		assertThat(none.totalCost()).isZero();
	}

	// 결정 4(사용자 결정 2026-10-09): 긴 시즌은 마지막 조각을 빼고 한 달에 최소 120분씩 나눠 본다

	@Test
	void 결정4_시작_달의_남은_시간이_120분_미만이면_그_달은_건너뛰고_다음_달부터_이어_본다() {
		// MUST 500분이 0번째 달을 쓰고 100분이 남는다. 예전 규칙이면 시즌이 100 + 600 + 300으로 0번째 달부터 시작했다
		PlanInput input = input(600, P1_ONLY, unit(1, MUST, 500, P1), unit(2, WANT, 1000, P1), unit(3, MAYBE, 90, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1), ids(P1)));

		assertThat(resultOf(evaluation, 2).assignments()).containsExactly(new Assignment(1, P1, 600), new Assignment(2, P1, 400));
		assertThat(resultOf(evaluation, 2).completedMonthIndex()).isEqualTo(2);
		// 시즌이 쓰지 않은 0번째 달 100분은 뒤 단위가 쓴다
		assertThat(resultOf(evaluation, 3).assignments()).containsExactly(new Assignment(0, P1, 90));
		assertThat(evaluation.score()).isEqualTo(3);
	}

	@Test
	void 결정4_이어_가는_달의_남은_시간이_120분_미만이고_마지막_조각이_아니면_TIME이고_시간은_풀린다() {
		// MUST 500분(p2)이 1번째 달을 써서 100분이 남는다. 예전 규칙이면 시즌이 600 + 100 + 600 = 1300으로 배정됐다
		PlanInput input = input(600, List.of(product(P1, 10_000), product(P2, 9_000)),
				unit(1, MUST, 500, P2), unit(2, WANT, 1300, P1), unit(3, MAYBE, 300, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1, P2), ids(P1)));

		assertThat(resultOf(evaluation, 1).assignments()).containsExactly(new Assignment(1, P2, 500));
		assertThat(resultOf(evaluation, 2).reason()).isEqualTo(UnitReason.TIME);
		// 시즌이 시험 배정했던 0번째 달 시간이 풀려 뒤 단위가 0번째 달에 들어간다
		assertThat(resultOf(evaluation, 3).assignments()).containsExactly(new Assignment(0, P1, 300));
	}

	@Test
	void 결정4_마지막_조각은_120분보다_작아도_되고_남은_시간이_120분_미만인_달에도_들어간다() {
		// 1번째 달은 100분만 남지만 시즌의 잔여 30분이 마지막 조각이라 허용된다
		PlanInput input = input(600, List.of(product(P1, 10_000), product(P2, 9_000)),
				unit(1, MUST, 500, P2), unit(2, WANT, 630, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1, P2), ids()));

		assertThat(resultOf(evaluation, 2).assignments()).containsExactly(new Assignment(0, P1, 600), new Assignment(1, P1, 30));
		assertThat(resultOf(evaluation, 2).completedMonthIndex()).isEqualTo(1);
	}

	@Test
	void 결정4_남은_시간이_정확히_120분인_달에서는_시작할_수_있다() {
		PlanInput input = input(600, P1_ONLY, unit(1, MUST, 480, P1), unit(2, WANT, 1000, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1), ids(P1)));

		assertThat(resultOf(evaluation, 2).assignments()).containsExactly(
				new Assignment(0, P1, 120), new Assignment(1, P1, 600), new Assignment(2, P1, 280));
	}

	@Test
	void 결정4_월_시청_시간이_120분_미만이면_긴_시즌은_시작할_수_없어_TIME이다() {
		// 한계를 고정한다: M=100이면 어느 달도 120분 조각을 만들 수 없다(3개월 용량 300분 ≥ 150분이어도 미배정)
		PlanInput under = input(100, P1_ONLY, unit(1, WANT, 150, P1));
		Evaluation underEvaluation = evaluate(under, selection(ids(P1), ids(P1), ids(P1)));
		assertThat(resultOf(underEvaluation, 1).reason()).isEqualTo(UnitReason.TIME);

		// 경계: M=120이면 120 + 120 + 60으로 나눠 볼 수 있다
		PlanInput boundary = input(120, P1_ONLY, unit(1, WANT, 300, P1));
		Evaluation boundaryEvaluation = evaluate(boundary, selection(ids(P1), ids(P1), ids(P1)));
		assertThat(resultOf(boundaryEvaluation, 1).assignments()).containsExactly(
				new Assignment(0, P1, 120), new Assignment(1, P1, 120), new Assignment(2, P1, 60));
	}

	// 평가기 + 비교기로 최선 조합 고르기(완전탐색 039의 축소판)

	@Test
	void 여러_조합을_평가하면_비교기가_꼭_볼_작품과_점수가_같을_때_더_싼_조합을_고른다() {
		List<OttProduct> products = List.of(product(P1, 10_000), product(P2, 9_000), free(P3));
		PlanInput input = input(600, products,
				unit(10, WANT, 1000, P1, P2), unit(11, MUST, 400, P2), unit(12, MAYBE, 100, P3), unknownUnit(13, WANT, 300));
		Evaluator evaluator = new Evaluator(input);
		EvaluationComparator comparator = new EvaluationComparator(input);

		// 배정 순서: U11(MUST) → U10(WANT) → U12(MAYBE) → U13(시청 가능 상품 없음, 맨 뒤)
		Evaluation p2p2p2 = evaluator.evaluate(selection(ids(P2), ids(P2), ids(P2)));
		Evaluation p1p2p2 = evaluator.evaluate(selection(ids(P1), ids(P2), ids(P2)));
		Evaluation p1p2p1 = evaluator.evaluate(selection(ids(P1), ids(P2), ids(P1)));
		for (Evaluation evaluation : List.of(p2p2p2, p1p2p2, p1p2p1)) {
			assertInvariants(input, evaluation);
			assertThat(evaluation.units()).extracting(UnitResult::watchUnitId).containsExactly(11L, 10L, 12L, 13L);
			assertThat(evaluation.mustCompleted()).isEqualTo(1);
			assertThat(evaluation.score()).isEqualTo(3); // U10 2점 + U12 1점
			assertThat(resultOf(evaluation, 12).assignments()).containsExactly(new Assignment(2, P3, 100)); // 0·1번째 달이 꽉 차 2번째 달
			assertThat(resultOf(evaluation, 13).reason()).isEqualTo(UnitReason.UNKNOWN);
		}
		assertThat(p2p2p2.totalCost()).isEqualTo(27_000);
		assertThat(p1p2p2.totalCost()).isEqualTo(28_000);
		assertThat(p1p2p1.totalCost()).isEqualTo(29_000);

		// p2/p2/p2: U11이 0번째 달 400분 → U10은 남은 200분(≥ 120) + 600 + 200
		assertThat(resultOf(p2p2p2, 11).assignments()).containsExactly(new Assignment(0, P2, 400));
		assertThat(resultOf(p2p2p2, 10).assignments()).containsExactly(
				new Assignment(0, P2, 200), new Assignment(1, P2, 600), new Assignment(2, P2, 200));
		// p1/p2/p2: U11은 p2가 있는 1번째 달 400분 → U10은 600(p1) + 남은 200(p2, ≥ 120) + 200(p2)
		assertThat(resultOf(p1p2p2, 11).assignments()).containsExactly(new Assignment(1, P2, 400));
		assertThat(resultOf(p1p2p2, 10).assignments()).containsExactly(
				new Assignment(0, P1, 600), new Assignment(1, P2, 200), new Assignment(2, P2, 200));
		// p1/p2/p1: 같은 배정에서 2번째 달 상품만 p1
		assertThat(resultOf(p1p2p1, 10).assignments()).containsExactly(
				new Assignment(0, P1, 600), new Assignment(1, P2, 200), new Assignment(2, P1, 200));

		// ①·② 같음 → ③ 총비용이 작은 쪽이 더 좋다(양수 = 첫 번째가 더 좋다)
		assertThat(comparator.compare(p2p2p2, p1p2p2)).isPositive();
		assertThat(comparator.compare(p1p2p2, p1p2p1)).isPositive();
		assertThat(Collections.max(List.of(p1p2p1, p2p2p2, p1p2p2), comparator)).isEqualTo(p2p2p2);
	}

	// 경계값과 보충 규칙

	@Test
	void 필요_시간이_월_시청_시간과_같으면_긴_시즌이_아니라_나누지_않고_다음_달로_넘긴다() {
		PlanInput input = input(600, P1_ONLY, unit(1, MUST, 100, P1), unit(2, WANT, 600, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1), ids()));

		assertThat(resultOf(evaluation, 2).assignments()).containsExactly(new Assignment(1, P1, 600));
	}

	@Test
	void 한_달_이내_단위는_볼_수_없는_달을_건너뛰고_다음에_볼_수_있는_달에_배정된다() {
		PlanInput input = input(600, P1_ONLY, unit(1, WANT, 60, P1));

		Evaluation evaluation = evaluate(input, selection(ids(), ids(), ids(P1)));

		assertThat(resultOf(evaluation, 1).assignments()).containsExactly(new Assignment(2, P1, 60));
	}

	@Test
	void 긴_시즌이_달_중간에_끝나면_그_달의_남은_시간을_다음_단위가_쓴다() {
		PlanInput input = input(600, P1_ONLY, unit(1, MUST, 1000, P1), unit(2, WANT, 200, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1), ids()));

		assertThat(resultOf(evaluation, 1).assignments()).containsExactly(new Assignment(0, P1, 600), new Assignment(1, P1, 400));
		assertThat(resultOf(evaluation, 2).assignments()).containsExactly(new Assignment(1, P1, 200));
	}

	@Test
	void 선택_상품_ID가_입력_상품_목록에_없으면_예외() {
		Evaluator evaluator = new Evaluator(input(600, P1_ONLY, unit(1, WANT, 60, P1)));

		assertThatThrownBy(() -> evaluator.evaluate(selection(ids(99L), ids(), ids())))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("productId=99");
	}

	@Test
	void 상품이_32개를_넘으면_평가기를_만들_수_없다() {
		List<OttProduct> products = new ArrayList<>();
		for (long id = 1; id <= 33; id++) {
			products.add(product(id, 1_000));
		}
		PlanInput input = input(600, products);

		assertThatThrownBy(() -> new Evaluator(input)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 상한_가격_상품_32개를_3개월_모두_선택해도_총비용이_넘치지_않는다() {
		// CodeRabbit PR #25: 상품 수 상한(32) × 3개월 × 월 가격 상한(100만 원)이 int 범위 안인지 확인한다.
		// FREE·SUBSCRIBED의 0원 처리와 겹치지 않도록 모두 구독하지 않은(NONE) 상품으로 만든다
		List<OttProduct> products = new ArrayList<>();
		Set<Long> allIds = new HashSet<>();
		for (long id = 1; id <= 32; id++) {
			products.add(product(id, OttProduct.MAX_MONTHLY_PRICE));
			allIds.add(id);
		}
		PlanInput input = input(600, products);

		Evaluation evaluation = evaluate(input, selection(allIds, allIds, allIds));

		assertThat(evaluation.totalCost()).isPositive().isEqualTo(32 * 3 * 1_000_000);
	}

	@Test
	void 같은_평가기를_여러_번_불러도_앞선_평가가_다음_평가에_영향을_주지_않는다() {
		PlanInput input = input(600, P1_ONLY, unit(1, MUST, 1200, P1), unit(2, WANT, 300, P1));
		Evaluator evaluator = new Evaluator(input);
		Selection full = selection(ids(P1), ids(P1), ids(P1));

		Evaluation first = evaluator.evaluate(full);
		evaluator.evaluate(selection(ids(P1), ids(), ids()));
		Evaluation again = evaluator.evaluate(full);

		assertThat(again).isEqualTo(first);
	}

	// 입력 순서 무관성과 무작위 불변식

	@Test
	void 같은_단위를_어떤_순서로_넣어도_결과가_같다() {
		List<WatchUnit> units = List.of(
				unit(1, WANT, 200, N), unit(2, WANT, 200, T), unit(3, WANT, 800, N, T), unit(4, MUST, 300, T),
				unit(5, MAYBE, 90, N), unit(6, MAYBE, 90, N), unit(7, WANT, 1300, N, T), unknownUnit(8, MUST, 60),
				unit(9, MUST, 45, N), unit(10, WANT, 45, T));
		Selection selection = selection(ids(N), ids(N, T), ids(T));
		Evaluation expected = new Evaluator(new PlanInput(units, NETFLIX_TVING, 0, 600)).evaluate(selection);

		Random random = new Random(38L);
		for (int round = 0; round < 50; round++) {
			List<WatchUnit> shuffled = new ArrayList<>(units);
			Collections.shuffle(shuffled, random);
			PlanInput input = new PlanInput(shuffled, NETFLIX_TVING, 0, 600);

			Evaluation actual = evaluate(input, selection);

			assertThat(actual).isEqualTo(expected);
		}
	}

	@Test
	void 무작위_입력에서도_불변식이_지켜지고_결과가_결정적이다() {
		Random random = new Random(20261009L);
		List<OttProduct> products = List.of(
				product(1, 13_500), product(2, 13_900), subscribed(3, 9_500), free(4), product(5, 7_900));
		for (int round = 0; round < 2_000; round++) {
			int monthlyWatchMinutes = 120 + random.nextInt(1_200);
			List<WatchUnit> units = new ArrayList<>();
			int unitCount = 1 + random.nextInt(20);
			for (int id = 1; id <= unitCount; id++) {
				Set<Long> watchable = new HashSet<>();
				for (OttProduct product : products) {
					if (random.nextInt(4) == 0) {
						watchable.add(product.id());
					}
				}
				Priority priority = Priority.values()[random.nextInt(3)];
				units.add(new WatchUnit(id, priority, 1 + random.nextInt(2_000), watchable, random.nextBoolean()));
			}
			PlanInput input = new PlanInput(units, products, 0, monthlyWatchMinutes);
			Selection selection = selection(randomSubset(random, products), randomSubset(random, products), randomSubset(random, products));

			Evaluation evaluation = evaluate(input, selection);

			assertThat(new Evaluator(input).evaluate(selection)).isEqualTo(evaluation);
		}
	}

	private static Set<Long> randomSubset(Random random, List<OttProduct> products) {
		Set<Long> subset = new HashSet<>();
		for (OttProduct product : products) {
			if (random.nextBoolean()) {
				subset.add(product.id());
			}
		}
		return subset;
	}

	@Test
	void 픽스처의_불변식_검사가_틀린_결과를_잡아낸다() {
		// 불변식 검사 자체가 동작하는지 확인: 월 시청 시간을 넘는 배정을 넣은 결과는 실패해야 한다
		PlanInput input = input(100, P1_ONLY, unit(1, WANT, 60, P1), unit(2, WANT, 60, P1));
		Evaluation broken = new Evaluation(selection(ids(P1), ids(), ids()), 0, 4, 10_000, List.of(
				UnitResult.scheduled(1, 0, List.of(new Assignment(0, P1, 60))),
				UnitResult.scheduled(2, 0, List.of(new Assignment(0, P1, 60)))));

		assertThatThrownBy(() -> EngineFixtures.assertInvariants(input, broken)).isInstanceOf(AssertionError.class);
	}
}
