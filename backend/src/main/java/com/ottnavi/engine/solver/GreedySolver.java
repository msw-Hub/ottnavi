package com.ottnavi.engine.solver;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.engine.rule.Evaluator;
import com.ottnavi.engine.rule.PlanObjective;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 그리디 Solver(PRD 5.4 정확해와 그리디, Task 039 안내서 S4·S8). 달마다 후보는 예산 이하 부분집합 전부다(동치 제거 없음, S6은 적용).
 * <ul>
 *   <li>추천형(시작 조합 없음, S4): 0번째 달부터 앞선 달은 고정하고 뒤의 달은 비운 채로, 그 달에 가장 좋은 후보를 확정한다.
 *       비유: 체스를 한 수 앞만 보고 두는 사람이다. 뒤의 달을 보지 못해 여러 달에 걸친 긴 시즌에서 정확해보다 나쁠 수 있다.</li>
 *   <li>절약형·간편형(시작 조합 있음, S8): 추천형 결과 조합에서 출발해 달 0 → 1 → 2 순서로 그 달 선택만 바꿔 보며 3개월 전체를 평가하고,
 *       더 좋으면 바꾼다. 한 바퀴 동안 바뀐 달이 없으면 멈춘다. 비유: 완성된 답안지를 한 칸씩 고쳐 보며 점수가 오르면 고친다.
 *       점수 하한은 3개월 기준이라 빈 상태에서 달마다 고르면 매달 빈 집합이 뽑히므로 이렇게 한다.</li>
 * </ul>
 */
public final class GreedySolver implements PlanSolver {

	@Override
	public SolveResult solve(PlanInput input, PlanObjective objective) {
		if (input == null || objective == null) {
			throw new IllegalArgumentException("입력과 목적함수는 null일 수 없다");
		}
		Evaluator evaluator = new Evaluator(input); // 풀이마다 한 번 만들고 evaluate만 반복한다(038 호출 규칙)
		List<List<Set<Long>>> candidates = MonthCandidates.budgetSubsets(input);
		Evaluation best = objective.baseline() == null
				? fixMonthByMonth(evaluator, objective.comparator(), candidates)
				: improveFromBaseline(evaluator, objective.comparator(), candidates, objective.baseline());
		return new SolveResult(best, objective.planType(), SolverType.GREEDY);
	}

	/** S4: 달 0 → 1 → 2 순서로, 앞선 달은 확정값·뒤의 달은 빈 집합으로 두고 그 달의 최선을 확정한다. */
	private static Evaluation fixMonthByMonth(Evaluator evaluator, Comparator<Evaluation> comparator, List<List<Set<Long>>> candidates) {
		Selection fixed = Selection.of(Set.of(), Set.of(), Set.of()); // 아직 확정하지 않은 달은 빈 집합
		Evaluation best = null;
		for (int month = 0; month < Selection.MONTH_COUNT; month++) {
			Evaluation monthBest = null;
			for (Set<Long> subset : candidates.get(month)) {
				Evaluation evaluation = evaluator.evaluate(MonthCandidates.replaceMonth(fixed, month, subset));
				if (monthBest == null || comparator.compare(evaluation, monthBest) > 0) {
					monthBest = evaluation;
				}
			}
			fixed = monthBest.selection();
			best = monthBest;
		}
		// 마지막 달까지 확정한 조합의 평가가 곧 최종 Evaluation이다(뒤에 빈 달이 남지 않는다)
		return best;
	}

	/**
	 * S8: 시작 조합을 평가하고, 달마다 그 달 선택만 후보로 바꿔 3개월 전체를 평가해 더 좋으면 바꾼다.
	 * 바꿀 때마다 비교기상 엄격히 좋아지고 조합 수는 유한하므로 반드시 끝난다. 결과는 시작 조합보다 나쁘지 않다.
	 */
	private static Evaluation improveFromBaseline(Evaluator evaluator, Comparator<Evaluation> comparator,
			List<List<Set<Long>>> candidates, Selection baseline) {
		Evaluation current = evaluator.evaluate(baseline);
		boolean isChanged = true;
		while (isChanged) {
			isChanged = false;
			for (int month = 0; month < Selection.MONTH_COUNT; month++) {
				for (Set<Long> subset : candidates.get(month)) {
					Evaluation evaluation = evaluator.evaluate(MonthCandidates.replaceMonth(current.selection(), month, subset));
					if (comparator.compare(evaluation, current) > 0) {
						current = evaluation;
						isChanged = true;
					}
				}
			}
		}
		return current;
	}
}
