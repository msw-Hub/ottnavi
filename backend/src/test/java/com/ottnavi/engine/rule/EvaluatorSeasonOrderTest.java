package com.ottnavi.engine.rule;

import static com.ottnavi.engine.EngineFixtures.assertInvariants;
import static com.ottnavi.engine.EngineFixtures.input;
import static com.ottnavi.engine.EngineFixtures.product;
import static com.ottnavi.engine.EngineFixtures.resultOf;
import static com.ottnavi.engine.EngineFixtures.selection;
import static com.ottnavi.engine.EngineFixtures.unit;
import static com.ottnavi.engine.model.Priority.MAYBE;
import static com.ottnavi.engine.model.Priority.MUST;
import static com.ottnavi.engine.model.Priority.WANT;
import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.engine.model.Assignment;
import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.UnitReason;
import com.ottnavi.engine.model.UnitResult;
import com.ottnavi.engine.model.WatchUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 평가기의 시즌 순서 규칙(PRD 5.4): 뒤 시즌 시청 완료 달 ≥ 앞 시즌 시청 완료 달, 앞 시즌이 미배정이면 뒤 시즌 TIME.
 * 기대값은 모두 손으로 계산했다(배정 순서 = 기본 정렬 뒤 앞 시즌 끌어올림). 상품 1은 10,000원, 상품 2는 20,000원이다.
 */
class EvaluatorSeasonOrderTest {

	private static final long P1 = 1L;
	private static final long P2 = 2L;
	private static final List<OttProduct> P1_P2 = List.of(product(P1, 10_000), product(P2, 20_000));

	/** 시즌 단위를 만든다(모름 없음). */
	private static WatchUnit season(long id, Priority priority, int minutes, long titleId, int seasonNumber, Long... productIds) {
		return new WatchUnit(id, priority, minutes, Set.of(productIds), false, titleId, seasonNumber);
	}

	private static Set<Long> ids(Long... ids) {
		return Set.of(ids);
	}

	private static Evaluation evaluate(PlanInput input, Selection selection) {
		Evaluation evaluation = new Evaluator(input).evaluate(selection);
		assertInvariants(input, evaluation);
		return evaluation;
	}

	private static List<Long> order(PlanInput input) {
		return input.units().stream().map(WatchUnit::id).toList();
	}

	private static List<Assignment> assignmentsOf(Evaluation evaluation, long unitId) {
		return resultOf(evaluation, unitId).assignments();
	}

	@Test
	void 같은_달에_시즌_1_2가_시청_완료되면_결과_목록에서_앞_시즌이_먼저다() {
		// 월 300분. 2시즌(MUST 60)보다 1시즌(MAYBE 100)이 먼저 배정된다(끌어올림). 상품 1은 0번째 달 선택
		PlanInput input = input(300, P1_P2, season(1, MAYBE, 100, 7, 1, P1), season(2, MUST, 60, 7, 2, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(), ids()));

		assertThat(order(input)).containsExactly(1L, 2L);
		assertThat(resultOf(evaluation, 1).completedMonthIndex()).isZero(); // 1시즌 100분: 0번째 달
		assertThat(resultOf(evaluation, 2).completedMonthIndex()).isZero(); // 2시즌 60분: 같은 달, 합 160 <= 300
		assertThat(evaluation.mustCompleted()).isEqualTo(1);
		assertThat(evaluation.score()).isEqualTo(1); // MAYBE 1점
	}

	@Test
	void 앞_시즌이_미배정이면_뒤_시즌은_자기_서비스가_선택돼도_TIME이다() {
		// 월 300분. 1시즌 400분은 긴 시즌인데 상품 1이 0번째 달만 선택돼 이어 볼 달이 없다 → TIME
		// 2시즌 50분은 혼자라면 0번째 달에 들어가지만 앞 시즌이 미배정이라 TIME
		PlanInput input = input(300, P1_P2, season(1, MAYBE, 400, 7, 1, P1), season(2, MUST, 50, 7, 2, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(), ids()));

		assertThat(resultOf(evaluation, 1).reason()).isEqualTo(UnitReason.TIME);
		assertThat(resultOf(evaluation, 2).reason()).isEqualTo(UnitReason.TIME);
		assertThat(evaluation.mustCompleted()).isZero();
		assertThat(evaluation.score()).isZero();
	}

	@Test
	void 뒤_시즌_서비스가_선택되지_않았으면_앞_시즌이_미배정이어도_기존_코드_BUDGET이다() {
		// 1시즌은 상품 1, 2시즌은 상품 2로만 본다. 상품 2는 어느 달에도 선택되지 않았다
		PlanInput scheduledPrereq = input(300, P1_P2, season(1, WANT, 50, 7, 1, P1), season(2, MUST, 50, 7, 2, P2));
		// 앞 시즌이 400분 긴 시즌이라 미배정이어도 뒤 시즌은 TIME이 아니라 BUDGET이다
		PlanInput unscheduledPrereq = input(300, P1_P2, season(1, WANT, 400, 7, 1, P1), season(2, MUST, 50, 7, 2, P2));
		Selection onlyP1Month0 = selection(ids(P1), ids(), ids());

		Evaluation first = evaluate(scheduledPrereq, onlyP1Month0);
		Evaluation second = evaluate(unscheduledPrereq, onlyP1Month0);

		assertThat(resultOf(first, 1).scheduled()).isTrue();
		assertThat(resultOf(first, 2).reason()).isEqualTo(UnitReason.BUDGET);
		assertThat(resultOf(second, 1).reason()).isEqualTo(UnitReason.TIME);
		assertThat(resultOf(second, 2).reason()).isEqualTo(UnitReason.BUDGET);
	}

	@Test
	void 앞_시즌이_찜에_없으면_뒤_시즌에_제약이_없다() {
		// 같은 시리즈 7의 2시즌만 찜. 다른 시리즈 8의 1시즌(400분 긴 시즌, 미배정)은 영향이 없다
		PlanInput input = input(300, P1_P2, season(1, MAYBE, 400, 8, 1, P1), season(2, MUST, 50, 7, 2, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(), ids()));

		assertThat(resultOf(evaluation, 2).completedMonthIndex()).isZero();
		assertThat(resultOf(evaluation, 1).reason()).isEqualTo(UnitReason.TIME);
	}

	@Test
	void 시즌_1과_3만_찜하면_3시즌은_1시즌의_시청_완료_달_이후에_끝난다() {
		// 시즌 1(상품 2, 100분)은 1번째 달에만 볼 수 있다. 시즌 3(상품 1, 40분)은 0·1번째 달에 볼 수 있다
		// 제약이 없다면 시즌 3은 0번째 달이지만, 앞 시즌(찜에 있는 직전 시즌 = 시즌 1)이 1번째 달에 끝나므로 1번째 달이다
		PlanInput input = input(300, P1_P2, season(1, WANT, 100, 5, 1, P2), season(3, MUST, 40, 5, 3, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1, P2), ids()));

		assertThat(order(input)).containsExactly(1L, 3L);
		assertThat(resultOf(evaluation, 1).completedMonthIndex()).isEqualTo(1);
		assertThat(resultOf(evaluation, 3).completedMonthIndex()).isEqualTo(1);
		assertThat(assignmentsOf(evaluation, 3)).containsExactly(new Assignment(1, P1, 40));
		assertThat(evaluation.mustCompleted()).isEqualTo(1);
		assertThat(evaluation.score()).isEqualTo(2);
	}

	@Test
	void 시즌_1과_3만_찜했을_때_시즌_1이_미배정이면_시즌_3은_TIME이다() {
		// 시즌 1의 상품 2가 선택되지 않아 BUDGET. 시즌 3의 상품 1은 선택됐으므로 TIME
		PlanInput input = input(300, P1_P2, season(1, WANT, 100, 5, 1, P2), season(3, MUST, 40, 5, 3, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1), ids()));

		assertThat(resultOf(evaluation, 1).reason()).isEqualTo(UnitReason.BUDGET);
		assertThat(resultOf(evaluation, 3).reason()).isEqualTo(UnitReason.TIME);
	}

	@Test
	void 앞_시즌이_MAYBE이고_뒤_시즌이_MUST이면_끌어올림으로_둘_다_시청_완료된다() {
		// 월 300분, 둘 다 100분, 상품 1을 0번째 달 선택. 배정 순서 1시즌 → 2시즌(끌어올림)
		PlanInput input = input(300, P1_P2, season(1, MAYBE, 100, 7, 1, P1), season(2, MUST, 100, 7, 2, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(), ids()));

		assertThat(order(input)).containsExactly(1L, 2L);
		assertThat(resultOf(evaluation, 1).completedMonthIndex()).isZero();
		assertThat(resultOf(evaluation, 2).completedMonthIndex()).isZero(); // 100 + 100 <= 300
		assertThat(evaluation.mustCompleted()).isEqualTo(1);
		assertThat(evaluation.score()).isEqualTo(1);
	}

	@Test
	void 앞_시즌이_먼저_시간을_차지해_뒤_시즌이_다음_달로_밀린다() {
		// 월 150분: 1시즌 100분이 0번째 달 남은 시간을 50분으로 만들어 2시즌 100분은 1번째 달로 넘어간다
		PlanInput input = input(150, P1_P2, season(1, MAYBE, 100, 7, 1, P1), season(2, MUST, 100, 7, 2, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1), ids()));

		assertThat(resultOf(evaluation, 1).completedMonthIndex()).isZero();
		assertThat(resultOf(evaluation, 2).completedMonthIndex()).isEqualTo(1);
	}

	@Test
	void 정적_불가능_시즌이_낀_사슬은_뒤_시즌이_모두_TIME이고_시즌_3의_MUST가_시즌_1을_끌어올리지_않는다() {
		// 예산 15,000원. 시즌 1(MAYBE)은 상품 2(20,000원)로만 볼 수 있어 정적 불가능
		// 기본 정렬: 3시즌(MUST), 2시즌(WANT 60 id 2), 영화 4(WANT 60 id 4), 1시즌(MAYBE). 3시즌을 내보낼 때 1시즌이 불가능이라 끌어올리지 않는다
		PlanInput input = new PlanInput(List.of(
				season(1, MAYBE, 60, 9, 1, P2), season(2, WANT, 60, 9, 2, P1), season(3, MUST, 60, 9, 3, P1), unit(4, WANT, 60, P1)),
				P1_P2, 15_000, 600);

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(), ids()));

		assertThat(order(input)).containsExactly(3L, 2L, 4L, 1L); // 앞 시즌들이 3시즌보다 뒤에 평가된다
		assertThat(resultOf(evaluation, 1).reason()).isEqualTo(UnitReason.BUDGET); // 상품 2가 선택되지 않음
		assertThat(resultOf(evaluation, 2).reason()).isEqualTo(UnitReason.TIME);   // 앞 시즌(1시즌)이 항상 막힘
		assertThat(resultOf(evaluation, 3).reason()).isEqualTo(UnitReason.TIME);   // 사슬 전이: 2시즌도 막힘
		assertThat(resultOf(evaluation, 4).completedMonthIndex()).isZero();        // 영화는 제약 없음
		assertThat(evaluation.mustCompleted()).isZero();
		assertThat(evaluation.score()).isEqualTo(2);
	}

	@Test
	void 긴_시즌은_시작_달을_자유롭게_두되_앞_시즌보다_먼저_끝나는_시작_달은_건너뛴다() {
		// 월 300분. 1시즌(상품 2, 100분)은 2번째 달에만 볼 수 있어 2번째 달에 끝난다
		// 2시즌(상품 1, 400분, 긴 시즌, 0·1·2번째 달 선택): 시작 0 → 0번째 300 + 1번째 100으로 1번째 달에 끝나 < 2라 건너뜀
		// 시작 1 → 1번째 300 + 2번째 100으로 2번째 달에 끝나 통과(시작 달은 앞 시즌 완료 달보다 이르다)
		PlanInput input = input(300, P1_P2, season(1, WANT, 100, 7, 1, P2), season(2, MUST, 400, 7, 2, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1), ids(P1, P2)));

		assertThat(resultOf(evaluation, 1).completedMonthIndex()).isEqualTo(2);
		assertThat(resultOf(evaluation, 2).completedMonthIndex()).isEqualTo(2);
		assertThat(assignmentsOf(evaluation, 2)).containsExactly(new Assignment(1, P1, 300), new Assignment(2, P1, 100));
	}

	@Test
	void 한계_앞_시즌이_마지막_달에_250분으로_끝나고_뒤_시즌이_400분이면_나눠_보면_볼_수_있어도_TIME이다() {
		// 월 300분. 1시즌(상품 2, 250분)은 2번째 달에만 볼 수 있어 2번째 달에 끝난다(남은 50분)
		// 2시즌 400분(상품 1, 0·1·2번째 달): 시작 0은 1번째 달에 끝나 2번째 달 미만이라 건너뜀
		// 시작 1은 1번째 300분 뒤 2번째 달 남은 50분이 잔여 100분보다 적고 120분 미만(결정 4)이라 실패, 시작 2는 남은 50분 < 400분이고 120분 미만이라 실패
		// 제약 없이 평가하면 시작 0(300 + 100)으로 시청 완료할 수 있는 경우다
		PlanInput input = input(300, P1_P2, season(1, WANT, 250, 7, 1, P2), season(2, MUST, 400, 7, 2, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1), ids(P1, P2)));

		assertThat(resultOf(evaluation, 1).completedMonthIndex()).isEqualTo(2);
		assertThat(resultOf(evaluation, 2).reason()).isEqualTo(UnitReason.TIME);
		assertThat(evaluation.mustCompleted()).isZero();
	}

	@Test
	void 서로_다른_시리즈와_영화는_서로_제약이_없다() {
		// 월 300분, 상품 1: 0·1번째 달 선택, 상품 2: 1번째 달 선택
		// 시리즈 A: A1(id 1, MAYBE 100, 상품 2) → A2(id 2, MUST 100, 상품 1). 시리즈 B: B1(id 3, WANT 100) → B2(id 4, WANT 150). 영화 5(MUST 50)
		// 배정 순서: 영화 5, A1, A2(끌어올림), B1, B2
		// 영화 5: 0번째 달(남은 250). A1: 상품 2는 1번째 달만 → 1번째 달(남은 200). A2: 앞 시즌 완료 달 1 이상 → 1번째 달(남은 100)
		// B1: 0번째 달(남은 150). B2: B1과 같은 0번째 달 150분(남은 0) → A 시리즈의 제약이 B·영화에 번지지 않는다
		PlanInput input = input(300, P1_P2,
				season(1, MAYBE, 100, 1, 1, P2), season(2, MUST, 100, 1, 2, P1),
				season(3, WANT, 100, 2, 1, P1), season(4, WANT, 150, 2, 2, P1),
				unit(5, MUST, 50, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1, P2), ids()));

		assertThat(order(input)).containsExactly(5L, 1L, 2L, 3L, 4L);
		assertThat(resultOf(evaluation, 5).completedMonthIndex()).isZero();
		assertThat(resultOf(evaluation, 1).completedMonthIndex()).isEqualTo(1);
		assertThat(resultOf(evaluation, 2).completedMonthIndex()).isEqualTo(1);
		assertThat(resultOf(evaluation, 3).completedMonthIndex()).isZero();
		assertThat(resultOf(evaluation, 4).completedMonthIndex()).isZero();
		assertThat(evaluation.mustCompleted()).isEqualTo(2);
		assertThat(evaluation.score()).isEqualTo(5); // MAYBE 1 + WANT 2 + WANT 2
		assertThat(evaluation.totalCost()).isEqualTo(40_000); // 0번째 달 10,000 + 1번째 달 30,000
	}

	@Test
	void 시즌이_없는_입력과_앞_시즌이_없는_시즌_입력은_같은_조합에서_같은_배정이다() {
		// 시즌 번호만 붙이고 앞 시즌이 없는(서로 다른 시리즈) 입력은 영화 입력과 결과가 같아야 한다
		PlanInput movies = input(300, P1_P2, unit(1, MUST, 100, P1), unit(2, WANT, 250, P1), unit(3, MAYBE, 50, P1, P2));
		PlanInput seasons = input(300, P1_P2, season(1, MUST, 100, 1, 1, P1), season(2, WANT, 250, 2, 1, P1), season(3, MAYBE, 50, 3, 1, P1, P2));
		Selection selection = selection(ids(P1), ids(P2), ids(P1));

		Evaluation movieResult = evaluate(movies, selection);
		Evaluation seasonResult = evaluate(seasons, selection);

		assertThat(seasonResult.units()).isEqualTo(movieResult.units());
		assertThat(seasonResult.score()).isEqualTo(movieResult.score());
	}

	@Test
	void 끌어올린_배정_순서가_결과_목록_순서와_같다() {
		// 끌어올림으로 바뀐 배정 순서가 결과 목록 순서와 일치하는지(앞 시즌이 먼저 평가됐음을 보장하는 전제)
		PlanInput input = input(300, P1_P2, season(1, MAYBE, 100, 7, 1, P1), season(2, MUST, 100, 7, 2, P1), unit(3, MUST, 10, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(), ids()));

		assertThat(evaluation.units()).extracting(UnitResult::watchUnitId).containsExactly(3L, 1L, 2L);
	}
}
