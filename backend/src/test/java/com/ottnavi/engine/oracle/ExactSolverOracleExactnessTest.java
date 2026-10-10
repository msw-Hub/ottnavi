package com.ottnavi.engine.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.rule.PlanObjective;
import com.ottnavi.engine.solver.BruteForceOracle;
import com.ottnavi.engine.solver.ExactSolver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 완전탐색 정확성: ExactSolver(동치 제거 포함)의 세 유형 결과가, 독립 구현 OracleAssigner로 평가한
 * 전수 탐색(동치 제거 없이 후보 상품 S6으로 만든 예산 이하 모든 3개월 조합)의 최선과 같은지 본다.
 * 비교기는 BruteForceOracle의 독립 구현(명세 3절 표)을 쓰고, 평가만 OracleAssigner로 바꿔 시즌 순서가 섞인 입력에서 배정 규칙까지 함께 검증한다.
 */
class ExactSolverOracleExactnessTest {

	private static final long SEED = 20261011L;
	private static final int INPUT_COUNT = 500; // 상품 3~4개, 시리즈 1~3개가 섞인 무작위 입력 수

	@Test
	@DisplayName("정확성: 추천형·절약형·간편형 완전탐색 결과가 OracleAssigner 전수 탐색의 최선과 같다")
	void 완전탐색_결과가_전수_탐색_최선과_같다() {
		Random random = new Random(SEED);
		ExactSolver exact = new ExactSolver();
		long combinations = 0;
		long representatives = 0;
		List<String> counterexamples = new ArrayList<>();
		for (int i = 0; i < INPUT_COUNT && counterexamples.isEmpty(); i++) {
			PlanInput input = OracleInputs.randomInput(random, 3, 4);
			List<Evaluation> all = BruteForceOracle.allEvaluations(input, selection -> OracleAssigner.evaluate(input, selection));
			combinations += all.size();
			representatives += BruteForceOracle.equivalenceCombinationCount(input);

			Evaluation oracleRecommended = Collections.max(all, BruteForceOracle.comparator(PlanType.RECOMMENDED, input, 0));
			SolveResult exactRecommended = exact.solve(input, PlanObjective.recommended(input));
			compare(input, PlanType.RECOMMENDED, exactRecommended.evaluation(), oracleRecommended, counterexamples);

			for (PlanType type : List.of(PlanType.SAVER, PlanType.SIMPLE)) {
				Evaluation oracleBest = Collections.max(all, BruteForceOracle.comparator(type, input, oracleRecommended.score()));
				PlanObjective objective = type == PlanType.SAVER
						? PlanObjective.saver(input, exactRecommended.evaluation())
						: PlanObjective.simple(input, exactRecommended.evaluation());
				compare(input, type, exact.solve(input, objective).evaluation(), oracleBest, counterexamples);
			}
		}
		assertThat(counterexamples).as("반례(최소 재현 입력과 함께)").isEmpty();
		assertThat(combinations).as("전수 탐색한 조합 수").isGreaterThan(5_000);
		assertThat(representatives).as("추천형 동치 제거 후 조합 수").isLessThanOrEqualTo(combinations);
	}

	private static void compare(PlanInput input, PlanType type, Evaluation actual, Evaluation expected, List<String> counterexamples) {
		// 평가 결과는 OracleAssigner가 만든 것과 ExactSolver 안의 Evaluator가 만든 것이 같아야 하고(배정 규칙 일치), 선택 조합도 같아야 한다(최선 일치)
		Evaluation reevaluated = OracleAssigner.evaluate(input, actual.selection());
		if (!actual.selection().equals(expected.selection()) || !actual.equals(expected) || !actual.equals(reevaluated)) {
			counterexamples.add(type + "\n" + OracleInputs.describe(input) + "\n 완전탐색=" + actual + "\n 전수탐색 최선=" + expected);
		}
	}
}
