package com.ottnavi.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.engine.model.Assignment;
import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.UnitResult;
import com.ottnavi.engine.model.WatchUnit;
import com.ottnavi.engine.rule.Evaluator;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 엔진 테스트에서 함께 쓰는 입력 생성 함수와 불변식 단언. */
public final class EngineFixtures {

	public static final int NO_BUDGET_LIMIT = 1_000_000; // 평가기는 예산을 보지 않으므로(D7) 테스트에서는 넉넉한 값을 쓴다

	private EngineFixtures() {
	}

	/** 구독하지 않은 상품을 만든다. */
	public static OttProduct product(long id, int monthlyPrice) {
		return new OttProduct(id, monthlyPrice, OttProductCondition.NONE);
	}

	/** 이미 구독 중인 상품을 만든다. */
	public static OttProduct subscribed(long id, int monthlyPrice) {
		return new OttProduct(id, monthlyPrice, OttProductCondition.SUBSCRIBED);
	}

	/** 무료 이용 상품을 만든다(가격은 의미 없음). */
	public static OttProduct free(long id) {
		return new OttProduct(id, 0, OttProductCondition.FREE);
	}

	/** "모름" 서비스가 없는 시청 단위를 만든다. */
	public static WatchUnit unit(long id, Priority priority, int requiredMinutes, Long... watchableProductIds) {
		return new WatchUnit(id, priority, requiredMinutes, Set.of(watchableProductIds), false);
	}

	/** "모름" 서비스가 있는 시청 단위를 만든다. */
	public static WatchUnit unknownUnit(long id, Priority priority, int requiredMinutes, Long... watchableProductIds) {
		return new WatchUnit(id, priority, requiredMinutes, Set.of(watchableProductIds), true);
	}

	/** 월 시청 가능 시간과 상품·단위로 입력을 만든다. */
	public static PlanInput input(int monthlyWatchMinutes, List<OttProduct> products, WatchUnit... units) {
		return new PlanInput(Arrays.asList(units), products, NO_BUDGET_LIMIT, monthlyWatchMinutes);
	}

	/** 달별 선택 상품 ID로 구독 조합을 만든다. */
	public static Selection selection(Set<Long> month0, Set<Long> month1, Set<Long> month2) {
		return Selection.of(month0, month1, month2);
	}

	/** 평가 결과에서 단위 ID로 결과를 찾는다. */
	public static UnitResult resultOf(Evaluation evaluation, long watchUnitId) {
		return evaluation.units().stream()
				.filter(result -> result.watchUnitId() == watchUnitId)
				.findFirst()
				.orElseThrow(() -> new AssertionError("결과에 단위가 없다: watchUnitId=" + watchUnitId));
	}

	/**
	 * 모든 평가 결과가 지켜야 할 불변식(안내서 5단계 ⑦)을 단언한다.
	 * ① 달마다 배정 분 합 ≤ 월 시청 가능 시간 ② 배정된 단위의 배정 분 합 = 필요 시간 ③ 미배정 단위는 배정 행 0개.
	 * 덧붙여 결과 수·순서가 입력(정렬된 단위, WatchUnit.ASSIGNMENT_ORDER)과 같고, 다 본 달이 마지막 배정 행의 달이며, 배정 행의 달이 연속이고,
	 * 긴 시즌의 마지막이 아닌 조각이 120분 이상인지(결정 4)도 본다.
	 */
	public static void assertInvariants(PlanInput input, Evaluation evaluation) {
		assertThat(evaluation.units()).extracting(UnitResult::watchUnitId)
				.containsExactlyElementsOf(input.units().stream().map(WatchUnit::id).toList());

		Map<Long, WatchUnit> unitsById = input.units().stream()
				.collect(Collectors.toMap(WatchUnit::id, unit -> unit));
		Map<Integer, Integer> minutesByMonth = new HashMap<>();
		for (UnitResult result : evaluation.units()) {
			WatchUnit unit = unitsById.get(result.watchUnitId());
			if (!result.scheduled()) {
				assertThat(result.assignments()).as("미배정 단위 %d의 배정 행", unit.id()).isEmpty();
				continue;
			}
			int assignedMinutes = result.assignments().stream().mapToInt(Assignment::minutes).sum();
			assertThat(assignedMinutes).as("배정 단위 %d의 배정 분 합", unit.id()).isEqualTo(unit.requiredMinutes());
			List<Assignment> assignments = result.assignments();
			assertThat(result.completedMonthIndex()).isEqualTo(assignments.get(assignments.size() - 1).monthIndex());
			for (int i = 0; i < assignments.size(); i++) {
				Assignment assignment = assignments.get(i);
				assertThat(assignment.monthIndex()).as("배정 행 달 연속").isEqualTo(assignments.get(0).monthIndex() + i);
				// 결정 4: 긴 시즌의 마지막 조각을 뺀 조각은 120분 이상
				if (i < assignments.size() - 1) {
					assertThat(assignment.minutes()).as("긴 시즌 %d의 마지막이 아닌 조각", unit.id())
							.isGreaterThanOrEqualTo(Evaluator.MIN_LONG_SEASON_SEGMENT_MINUTES);
				}
				assertThat(unit.watchableProductIds()).as("배정 상품은 시청 가능 상품").contains(assignment.productId());
				minutesByMonth.merge(assignment.monthIndex(), assignment.minutes(), Integer::sum);
			}
		}
		minutesByMonth.forEach((month, minutes) ->
				assertThat(minutes).as("%d번째 달 배정 분 합", month).isLessThanOrEqualTo(input.monthlyWatchMinutes()));
	}
}
