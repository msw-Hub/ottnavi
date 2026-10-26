package com.ottnavi.plan.service.model;

import com.ottnavi.engine.model.Assignment;
import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.engine.model.UnitReason;
import com.ottnavi.engine.model.UnitResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 플랜 유형 하나의 계산 결과. 엔진 record를 그대로 캐시하지 않으려고 값만 옮겨 담은 것이며 planCalc 캐시에 JSON으로 저장된다.
 *
 * @param planType          플랜 유형
 * @param solverType        실제로 푼 알고리즘(완전탐색·그리디)
 * @param mustCompleted     시청 완료한 "꼭" 수
 * @param score             점수
 * @param totalCost         3개월 총비용(원)
 * @param productIdsByMonth 달별 선택 상품 ID(길이 3, 각 달은 오름차순)
 * @param units             시청 단위별 결과(엔진 입력의 단위 순서)
 * @param candidateCount    완전탐색 기준 후보 조합 수(그리디로 풀었어도 센 값, int 상한)
 * @param elapsedMs         이 유형을 푸는 데 걸린 시간(ms)
 */
public record PlanCalcResult(
		PlanType planType,
		SolverType solverType,
		int mustCompleted,
		int score,
		int totalCost,
		List<List<Long>> productIdsByMonth,
		List<Unit> units,
		int candidateCount,
		long elapsedMs) {

	/** 시청 단위 하나의 결과. */
	public record Unit(
			long unitId,
			boolean scheduled,
			Integer completedMonthIndex, // 미배정이면 null
			UnitReason reason,           // 배정됐으면 null
			List<Slot> assignments) {
	}

	/** 배정 행 하나(어느 달에 어느 상품으로 몇 분). */
	public record Slot(int monthIndex, long productId, int minutes) {
	}

	/** 엔진 결과와 측정값(후보 조합 수, 소요 시간)으로 만든다. */
	public static PlanCalcResult from(SolveResult result, int candidateCount, long elapsedMs) {
		// 1. 달별 선택 상품을 오름차순 목록으로 옮긴다
		Evaluation evaluation = result.evaluation();
		List<List<Long>> productIdsByMonth = new ArrayList<>();
		for (int monthIndex = 0; monthIndex < Selection.MONTH_COUNT; monthIndex++) {
			Set<Long> productIds = evaluation.selection().productIdsOf(monthIndex);
			productIdsByMonth.add(new ArrayList<>(productIds));
		}

		// 2. 단위별 결과와 배정 행을 옮긴다
		List<Unit> units = new ArrayList<>();
		for (UnitResult unit : evaluation.units()) {
			List<Slot> slots = new ArrayList<>();
			for (Assignment assignment : unit.assignments()) {
				slots.add(new Slot(assignment.monthIndex(), assignment.productId(), assignment.minutes()));
			}
			units.add(new Unit(unit.watchUnitId(), unit.scheduled(), unit.completedMonthIndex(), unit.reason(), slots));
		}

		return new PlanCalcResult(result.planType(), result.solverType(), evaluation.mustCompleted(),
				evaluation.score(), evaluation.totalCost(), productIdsByMonth, units, candidateCount, elapsedMs);
	}
}
