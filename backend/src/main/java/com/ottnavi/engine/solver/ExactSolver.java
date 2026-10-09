package com.ottnavi.engine.solver;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.engine.rule.Evaluator;
import com.ottnavi.engine.rule.PlanObjective;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 완전탐색 Solver: 후보 상품에서 쓸모없는 상품을 빼고, 달마다 예산 이하 상품 부분집합을 만들어 3개월을 중첩 순회한다(TECH 1절).
 * 동치 제거는 추천형에서만 쓴다(다른 유형은 전부 훑는다). 후보 상품이 MAX_PRODUCT_COUNT를 넘으면 IllegalArgumentException.
 *
 * <p>비유: 권투 토너먼트에서 링 위에 현재 챔피언 한 명만 둔다. 도전자(조합 하나)가 이기면 챔피언이 바뀌고 지면 바로 집에 간다.
 * 수백만 명을 대기실에 모아 두지 않으므로(모든 Evaluation을 보관하지 않음) 메모리가 늘지 않는다.
 * 비교기의 마지막 기준이 달별 상품 ID 사전순이라 서로 다른 두 조합은 동점이 없고, 순회 순서와 상관없이 챔피언은 하나로 정해진다(결정성).
 */
public final class ExactSolver implements PlanSolver {

	public static final int MAX_PRODUCT_COUNT = 20; // 후보 상품 수 한도. 넘으면 2^P 열거가 불가능하고 후보 수의 곱이 long을 넘는다(MVP는 7개)

	/** 이 목적함수로 완전탐색이 순회할 3개월 조합 수(달별 후보 수의 곱)를 센다. 추천형은 동치 제거 후, 다른 유형은 제거 없이 센다. */
	public long countCandidateCombinations(PlanInput input, PlanObjective objective) {
		requireArguments(input, objective);
		// S5: solve와 같은 코드로 후보를 만들어 "센 수 = 실제로 훑는 수"를 보장한다
		return MonthCandidates.combinationCount(candidates(input, new Evaluator(input), objective));
	}

	@Override
	public SolveResult solve(PlanInput input, PlanObjective objective) {
		requireArguments(input, objective);
		Evaluator evaluator = new Evaluator(input); // 풀이마다 한 번 만들고 evaluate만 반복한다(038 호출 규칙)
		Comparator<Evaluation> comparator = objective.comparator();
		List<List<Set<Long>>> candidates = candidates(input, evaluator, objective);

		// S3: 후보로 만든 모든 3개월 조합을 평가해 비교기로 가장 좋은 것 하나만 남긴다
		Evaluation best = null;
		for (Set<Long> month0 : candidates.get(0)) {
			for (Set<Long> month1 : candidates.get(1)) {
				for (Set<Long> month2 : candidates.get(2)) {
					Evaluation evaluation = evaluator.evaluate(Selection.of(month0, month1, month2));
					if (best == null || comparator.compare(evaluation, best) > 0) {
						best = evaluation;
					}
				}
			}
		}
		// 달마다 빈 집합이 늘 후보라(S2) 조합이 하나도 없는 경우는 없다
		return new SolveResult(best, objective.planType(), SolverType.EXACT);
	}

	/** 목적함수에 맞는 달별 후보를 만든다. 추천형만 동치 제거(S7)를 하고, 절약형·간편형은 예산 이하 부분집합 전부다. */
	private static List<List<Set<Long>>> candidates(PlanInput input, Evaluator evaluator, PlanObjective objective) {
		List<List<Set<Long>>> subsets = MonthCandidates.budgetSubsets(input);
		if (objective.planType() != PlanType.RECOMMENDED) {
			// 절약형·간편형은 비용·볼 수 있는 단위만으로 우열을 정할 수 없어 제거하지 않는다(PRD 5.4 완전탐색과 그리디)
			return subsets;
		}
		return MonthCandidates.representatives(input, evaluator, objective.comparator(), subsets);
	}

	/** 입력과 목적함수가 비어 있지 않은지 확인한다. */
	private static void requireArguments(PlanInput input, PlanObjective objective) {
		if (input == null || objective == null) {
			throw new IllegalArgumentException("입력과 목적함수는 null일 수 없다");
		}
	}
}
