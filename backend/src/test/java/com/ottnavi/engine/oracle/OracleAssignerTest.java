package com.ottnavi.engine.oracle;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 오라클 자체 검증: 규칙 문서(TASK038 안내서 T1~T20, PRD 5.4 시즌 순서)의 손 계산 정답과 OracleAssigner가 일치해야 한다.
 * 평가기(Evaluator)와 무관하게 항상 통과해야 하는 테스트다. 상품 1은 10,000원, 상품 2는 20,000원이다.
 */
class OracleAssignerTest {

	private static final long P1 = 1L;
	private static final long P2 = 2L;
	private static final List<OttProduct> P1_P2 = List.of(product(P1, 10_000), product(P2, 20_000));

	private static WatchUnit season(long id, Priority priority, int minutes, long titleId, int seasonNumber, Long... productIds) {
		return new WatchUnit(id, priority, minutes, Set.of(productIds), false, titleId, seasonNumber);
	}

	private static Set<Long> ids(Long... ids) {
		return Set.of(ids);
	}

	/** 오라클로 평가하고 불변식 위반이 없는지 함께 확인한다. */
	private static Evaluation evaluate(PlanInput input, Selection selection) {
		Evaluation evaluation = OracleAssigner.evaluate(input, selection);
		assertThat(PlanInvariants.violations(input, evaluation)).isEmpty();
		return evaluation;
	}

	@Test
	@DisplayName("T1: 모두 한 달에 들어가면 0번째 달에 배정되고 점수·비용이 맞다")
	void 한_달에_모두_들어간다() {
		PlanInput input = input(600, List.of(product(P1, 10_000)), unit(1, MUST, 120, P1), unit(2, WANT, 100, P1), unit(3, MAYBE, 90, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(), ids()));

		assertThat(evaluation.units()).allMatch(result -> result.scheduled() && result.completedMonthIndex() == 0);
		assertThat(evaluation.mustCompleted()).isEqualTo(1);
		assertThat(evaluation.score()).isEqualTo(3);
		assertThat(evaluation.totalCost()).isEqualTo(10_000);
	}

	@Test
	@DisplayName("T2·T8·T9: 시간이 모자라면 TIME, 동점은 id 순, 같은 우선순위는 짧은 순")
	void 시간_초과와_정렬() {
		PlanInput timeOut = input(200, List.of(product(P1, 10_000)), unit(1, MUST, 120, P1), unit(2, WANT, 100, P1));
		Evaluation first = evaluate(timeOut, selection(ids(P1), ids(), ids()));
		assertThat(resultOf(first, 1).scheduled()).isTrue();
		assertThat(resultOf(first, 2).reason()).isEqualTo(UnitReason.TIME);

		PlanInput tie = input(100, List.of(product(P1, 10_000)), unit(5, WANT, 90, P1), unit(3, WANT, 90, P1));
		Evaluation second = evaluate(tie, selection(ids(P1), ids(), ids()));
		assertThat(resultOf(second, 3).scheduled()).isTrue();
		assertThat(resultOf(second, 5).reason()).isEqualTo(UnitReason.TIME);

		PlanInput shortFirst = input(250, List.of(product(P1, 10_000)), unit(1, WANT, 200, P1), unit(2, WANT, 100, P1));
		Evaluation third = evaluate(shortFirst, selection(ids(P1), ids(), ids()));
		assertThat(resultOf(third, 2).scheduled()).isTrue();
		assertThat(resultOf(third, 1).reason()).isEqualTo(UnitReason.TIME);
	}

	@Test
	@DisplayName("T3·T4·T13: 이유 코드 BUDGET, NO_PROVIDER, UNKNOWN")
	void 이유_코드() {
		PlanInput input = input(600, P1_P2, unit(1, WANT, 60, P2), unit(2, WANT, 60), unknownUnit(3, WANT, 60));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1), ids(P1)));

		assertThat(resultOf(evaluation, 1).reason()).isEqualTo(UnitReason.BUDGET);
		assertThat(resultOf(evaluation, 2).reason()).isEqualTo(UnitReason.NO_PROVIDER);
		assertThat(resultOf(evaluation, 3).reason()).isEqualTo(UnitReason.UNKNOWN);
	}

	@Test
	@DisplayName("T5·T6·T7·T10: 긴 시즌은 연속한 달에 나눠 보고, 못 끝내면 배정 시간을 풀어 준다")
	void 긴_시즌_분할() {
		PlanInput split = input(600, List.of(product(P1, 10_000)), unit(1, MUST, 1_200, P1));
		Evaluation twoMonths = evaluate(split, selection(ids(P1), ids(P1), ids()));
		assertThat(resultOf(twoMonths, 1).assignments()).containsExactly(new Assignment(0, P1, 600), new Assignment(1, P1, 600));
		assertThat(resultOf(twoMonths, 1).completedMonthIndex()).isEqualTo(1);
		assertThat(twoMonths.totalCost()).isEqualTo(20_000);

		Evaluation notContinuous = evaluate(split, selection(ids(P1), ids(), ids(P1)));
		assertThat(resultOf(notContinuous, 1).reason()).isEqualTo(UnitReason.TIME);

		PlanInput tooLong = input(600, List.of(product(P1, 10_000)), unit(1, WANT, 2_000, P1));
		assertThat(resultOf(evaluate(tooLong, selection(ids(P1), ids(P1), ids(P1))), 1).reason()).isEqualTo(UnitReason.TIME);

		PlanInput release = input(600, List.of(product(P1, 10_000)), unit(1, WANT, 1_200, P1), unit(2, MAYBE, 300, P1));
		Evaluation released = evaluate(release, selection(ids(P1), ids(), ids()));
		assertThat(resultOf(released, 1).scheduled()).isFalse();
		assertThat(resultOf(released, 2).completedMonthIndex()).isZero();
	}

	@Test
	@DisplayName("T11·T12: SUBSCRIBED는 0번째 달 0원, FREE는 선택 없이도 시청 가능")
	void 구독_상태() {
		PlanInput subscribedInput = input(600, List.of(subscribed(P1, 10_000)), unit(1, WANT, 60, P1));
		assertThat(evaluate(subscribedInput, selection(ids(P1), ids(P1), ids())).totalCost()).isEqualTo(10_000);
		assertThat(evaluate(subscribedInput, selection(ids(), ids(), ids())).score()).isEqualTo(2); // 0번째 달은 선택 없이도 본다

		PlanInput freeInput = input(600, List.of(free(3)), unit(1, WANT, 60, 3L));
		Evaluation evaluation = evaluate(freeInput, selection(ids(), ids(), ids()));
		assertThat(resultOf(evaluation, 1).completedMonthIndex()).isZero();
		assertThat(evaluation.totalCost()).isZero();
	}

	@Test
	@DisplayName("T15·T17·T18·T19: 서비스가 달라도 이어 보기, 연속 아님, 실제 시청 달 끊김, 한 달 최소 2시간")
	void 이어_보기_규칙() {
		PlanInput across = input(600, P1_P2, unit(1, WANT, 200, P1), unit(2, WANT, 200, P2), unit(3, WANT, 800, P1, P2));
		Evaluation acrossResult = evaluate(across, selection(ids(P1), ids(P2), ids()));
		assertThat(resultOf(acrossResult, 3).assignments()).containsExactly(new Assignment(0, P1, 400), new Assignment(1, P2, 400));
		assertThat(acrossResult.score()).isEqualTo(6);

		PlanInput gap = input(600, P1_P2, unit(1, WANT, 1_200, P1, P2));
		assertThat(resultOf(evaluate(gap, selection(ids(P1), ids(), ids(P2))), 1).reason()).isEqualTo(UnitReason.TIME);

		PlanInput broken = input(600, P1_P2, unit(1, MUST, 600, P2), unit(2, WANT, 1_200, P1));
		Evaluation brokenResult = evaluate(broken, selection(ids(P1), ids(P1, P2), ids(P1)));
		assertThat(resultOf(brokenResult, 2).reason()).isEqualTo(UnitReason.TIME);

		PlanInput minimum = input(600, List.of(product(P1, 10_000)), unit(1, MUST, 500, P1), unit(2, WANT, 1_000, P1));
		Evaluation minimumResult = evaluate(minimum, selection(ids(P1), ids(P1), ids(P1)));
		assertThat(resultOf(minimumResult, 2).assignments()).containsExactly(new Assignment(1, P1, 600), new Assignment(2, P1, 400));
	}

	@Test
	@DisplayName("시즌 순서: 같은 달에 끝나고 앞 시즌이 끌어올려져 먼저 배정된다")
	void 시즌_순서_끌어올림() {
		// 월 150분: 1시즌(MAYBE 100)이 먼저 시간을 차지해 2시즌(MUST 100)은 1번째 달로 밀린다
		PlanInput input = input(150, P1_P2, season(1, MAYBE, 100, 7, 1, P1), season(2, MUST, 100, 7, 2, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1), ids()));

		assertThat(resultOf(evaluation, 1).completedMonthIndex()).isZero();
		assertThat(resultOf(evaluation, 2).completedMonthIndex()).isEqualTo(1);
		assertThat(evaluation.mustCompleted()).isEqualTo(1);
		assertThat(evaluation.score()).isEqualTo(1);
	}

	@Test
	@DisplayName("시즌 순서: 앞 시즌이 미배정이면 TIME, 자기 서비스가 선택되지 않았으면 BUDGET, 찜에 없는 앞 시즌은 제약 없음")
	void 시즌_순서_이유_코드() {
		PlanInput blocked = input(300, P1_P2, season(1, MAYBE, 400, 7, 1, P1), season(2, MUST, 50, 7, 2, P1));
		Evaluation blockedResult = evaluate(blocked, selection(ids(P1), ids(), ids()));
		assertThat(resultOf(blockedResult, 1).reason()).isEqualTo(UnitReason.TIME);
		assertThat(resultOf(blockedResult, 2).reason()).isEqualTo(UnitReason.TIME);

		PlanInput notSelected = input(300, P1_P2, season(1, WANT, 400, 7, 1, P1), season(2, MUST, 50, 7, 2, P2));
		assertThat(resultOf(evaluate(notSelected, selection(ids(P1), ids(), ids())), 2).reason()).isEqualTo(UnitReason.BUDGET);

		PlanInput missingFirst = input(300, P1_P2, season(2, MUST, 50, 7, 2, P1));
		assertThat(resultOf(evaluate(missingFirst, selection(ids(P1), ids(), ids())), 2).completedMonthIndex()).isZero();
	}

	@Test
	@DisplayName("시즌 순서: 시즌 1·3만 찜하면 3시즌은 1시즌의 시청 완료 달 이후에 끝난다")
	void 시즌_1과_3만_찜() {
		PlanInput input = input(300, P1_P2, season(1, WANT, 100, 5, 1, P2), season(3, MUST, 40, 5, 3, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1, P2), ids()));

		assertThat(resultOf(evaluation, 1).completedMonthIndex()).isEqualTo(1);
		assertThat(resultOf(evaluation, 3).assignments()).containsExactly(new Assignment(1, P1, 40));
	}

	@Test
	@DisplayName("시즌 순서: 긴 시즌은 앞 시즌보다 먼저 끝나는 시작 달을 건너뛰고, 한계 사례는 TIME이다")
	void 긴_시즌_시작_달과_한계() {
		PlanInput skip = input(300, P1_P2, season(1, WANT, 100, 7, 1, P2), season(2, MUST, 400, 7, 2, P1));
		Evaluation skipped = evaluate(skip, selection(ids(P1), ids(P1), ids(P1, P2)));
		assertThat(resultOf(skipped, 2).assignments()).containsExactly(new Assignment(1, P1, 300), new Assignment(2, P1, 100));

		PlanInput limit = input(300, P1_P2, season(1, WANT, 250, 7, 1, P2), season(2, MUST, 400, 7, 2, P1));
		Evaluation limited = evaluate(limit, selection(ids(P1), ids(P1), ids(P1, P2)));
		assertThat(resultOf(limited, 1).completedMonthIndex()).isEqualTo(2);
		assertThat(resultOf(limited, 2).reason()).isEqualTo(UnitReason.TIME);
	}

	@Test
	@DisplayName("시즌 순서: 정적 불가능 시즌이 낀 사슬은 끌어올리지 않고 뒤 시즌이 모두 TIME이다")
	void 끌어올림_제외() {
		// 예산 15,000원, 시즌 1은 상품 2(20,000원)로만 볼 수 있어 정적 불가능
		PlanInput input = new PlanInput(List.of(
				season(1, MAYBE, 60, 9, 1, P2), season(2, WANT, 60, 9, 2, P1), season(3, MUST, 60, 9, 3, P1), unit(4, WANT, 60, P1)),
				P1_P2, 15_000, 600);

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(), ids()));

		assertThat(resultOf(evaluation, 1).reason()).isEqualTo(UnitReason.BUDGET);
		assertThat(resultOf(evaluation, 2).reason()).isEqualTo(UnitReason.TIME);
		assertThat(resultOf(evaluation, 3).reason()).isEqualTo(UnitReason.TIME);
		assertThat(resultOf(evaluation, 4).scheduled()).isTrue();
		assertThat(evaluation.mustCompleted()).isZero();
		assertThat(evaluation.score()).isEqualTo(2);
	}

	@Test
	@DisplayName("오라클: 월 시청 시간이 120분 미만이면 긴 시즌은 시작할 수 없다(TIME)")
	void 월_시청_시간이_짧으면_긴_시즌_불가() {
		PlanInput input = input(100, List.of(product(P1, 10_000)), unit(1, MUST, 150, P1));

		Evaluation evaluation = evaluate(input, selection(ids(P1), ids(P1), ids(P1)));

		assertThat(resultOf(evaluation, 1)).isEqualTo(UnitResult.unscheduled(1, UnitReason.TIME));
	}
}
