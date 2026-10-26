package com.ottnavi.engine.solver;

import static com.ottnavi.engine.EngineFixtures.product;
import static com.ottnavi.engine.EngineFixtures.unit;
import static com.ottnavi.engine.solver.SolverCases.ids;
import static com.ottnavi.engine.solver.SolverCases.sel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.engine.rule.PlanObjective;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 플랜 유형 3종을 한 번에 푸는 PlanTypeSolver 테스트(5절 14·15, 6).
 */
class PlanTypeSolverTest {

	private final ExactSolver exact = new ExactSolver();
	private final GreedySolver greedy = new GreedySolver();
	private final SolverSelector selector = new SolverSelector(exact, greedy, 1_000_000);

	private static List<PlanInput> inputs() {
		List<PlanInput> inputs = new ArrayList<>(SolverCases.handCases());
		inputs.addAll(SolverCases.randomInputs(40, 20261011L));
		return inputs;
	}

	@Test
	@DisplayName("solveAll은 항상 세 유형을 PlanType 순서로 담고 각 결과의 유형이 키와 같다")
	void 세_유형을_순서대로_담는다() {
		PlanTypeSolver solver = new PlanTypeSolver(selector);

		for (PlanInput input : inputs()) {
			Map<PlanType, SolveResult> results = solver.solveAll(input);

			assertThat(results.keySet()).as("%s", input)
					.containsExactly(PlanType.RECOMMENDED, PlanType.SAVER, PlanType.SIMPLE);
			results.forEach((type, result) -> assertThat(result.planType()).isEqualTo(type));
		}
	}

	@Test
	@DisplayName("추천형은 solve(input)과 같고 절약형·간편형은 추천형 결과의 점수로 하한을 정하고 그 조합을 baseline으로 쓴다")
	void 추천형_결과로_하한과_baseline을_정한다() {
		PlanTypeSolver solver = new PlanTypeSolver(selector);

		for (PlanInput input : inputs()) {
			Map<PlanType, SolveResult> results = solver.solveAll(input);
			SolveResult recommended = selector.solve(input);

			assertThat(results.get(PlanType.RECOMMENDED).selection()).as("%s", input).isEqualTo(recommended.selection());
			assertThat(results.get(PlanType.SAVER).selection()).as("%s", input)
					.isEqualTo(selector.solve(input, PlanObjective.saver(input, recommended.evaluation())).selection());
			assertThat(results.get(PlanType.SIMPLE).selection()).as("%s", input)
					.isEqualTo(selector.solve(input, PlanObjective.simple(input, recommended.evaluation())).selection());
			// 오라클과도 대조한다: 완전탐색으로 풀리는 상한이므로 세 유형 모두 무차별 대입 최선이다
			int recommendedScore = BruteForceOracle.bestRecommended(input).score();
			for (PlanType type : PlanType.values()) {
				assertThat(results.get(type).selection()).as("%s %s", type, input)
						.isEqualTo(BruteForceOracle.best(input, type, recommendedScore).selection());
			}
		}
	}

	@Test
	@DisplayName("절약형·간편형이 추천형·서로와 다른 입력의 손 계산 정답")
	void 손_계산_정답() {
		PlanTypeSolver solver = new PlanTypeSolver(selector);

		Map<PlanType, SolveResult> dropB = solver.solveAll(SolverCases.saverAndSimpleDropB());
		assertThat(dropB.get(PlanType.RECOMMENDED).selection()).isEqualTo(SolverCases.saverAndSimpleDropBRecommended());
		assertThat(dropB.get(PlanType.SAVER).selection()).isEqualTo(SolverCases.saverAndSimpleDropBSelection());
		assertThat(dropB.get(PlanType.SIMPLE).selection()).isEqualTo(SolverCases.saverAndSimpleDropBSelection());

		Map<PlanType, SolveResult> vs = solver.solveAll(SolverCases.saverVsSimple());
		assertThat(vs.get(PlanType.RECOMMENDED).selection()).isEqualTo(SolverCases.saverVsSimpleRecommended());
		assertThat(vs.get(PlanType.SAVER).selection()).isEqualTo(SolverCases.saverVsSimpleRecommended());
		assertThat(vs.get(PlanType.SIMPLE).selection()).isEqualTo(SolverCases.saverVsSimpleSimple());
		assertThat(vs.get(PlanType.RECOMMENDED).evaluation().score()).isEqualTo(4);
		assertThat(vs.get(PlanType.SIMPLE).evaluation().totalCost()).isEqualTo(9_000);
	}

	@Test
	@DisplayName("추천형이 그리디로 풀린 경우에도 그 결과의 점수와 조합을 하한·baseline으로 쓴다")
	void 그리디_추천형_결과로_하한과_baseline을_정한다() {
		PlanTypeSolver solver = new PlanTypeSolver(greedy);

		for (PlanInput input : inputs()) {
			Map<PlanType, SolveResult> results = solver.solveAll(input);
			SolveResult recommended = greedy.solve(input);
			Evaluation greedyEvaluation = recommended.evaluation();

			assertThat(results.get(PlanType.RECOMMENDED).selection()).as("%s", input).isEqualTo(recommended.selection());
			assertThat(results.get(PlanType.SAVER).selection()).as("%s", input)
					.isEqualTo(greedy.solve(input, PlanObjective.saver(input, greedyEvaluation)).selection());
			assertThat(results.get(PlanType.SIMPLE).selection()).as("%s", input)
					.isEqualTo(greedy.solve(input, PlanObjective.simple(input, greedyEvaluation)).selection());
			results.values().forEach(result -> assertThat(result.solverType()).isEqualTo(SolverType.GREEDY));
		}
	}

	@Test
	@DisplayName("그리디 추천형의 점수가 완전탐색보다 낮으면 하한도 낮아진다(그리디 열세 케이스)")
	void 그리디_열세_케이스의_하한() {
		PlanTypeSolver solver = new PlanTypeSolver(greedy);

		Map<PlanType, SolveResult> results = solver.solveAll(SolverCases.greedyWorse());

		// 그리디 추천형 점수 2 → 하한 ceil(1.4) = 2. 절약형·간편형 그리디도 baseline(점수 2)보다 나쁘지 않아야 한다
		assertThat(results.get(PlanType.RECOMMENDED).evaluation().score()).isEqualTo(2);
		assertThat(results.get(PlanType.SAVER).evaluation().score()).isGreaterThanOrEqualTo(2);
		assertThat(results.get(PlanType.SIMPLE).evaluation().score()).isGreaterThanOrEqualTo(2);
		assertThat(results.get(PlanType.SAVER).evaluation().totalCost()).isLessThanOrEqualTo(8_000);
	}

	@Test
	@DisplayName("전환 상한이 유형마다 다르게 작동한다: 상한 8이면 추천형은 완전탐색, 절약형·간편형은 그리디")
	void 유형별로_다른_알고리즘() {
		PlanInput input = SolverCases.in(10_000, 600, List.of(product(1, 3_000), product(2, 3_000)),
				unit(1, Priority.WANT, 200, 1L, 2L));
		PlanTypeSolver solver = new PlanTypeSolver(new SolverSelector(exact, greedy, 8));

		Map<PlanType, SolveResult> results = solver.solveAll(input);

		assertThat(results.get(PlanType.RECOMMENDED).solverType()).isEqualTo(SolverType.EXACT);
		assertThat(results.get(PlanType.SAVER).solverType()).isEqualTo(SolverType.GREEDY);
		assertThat(results.get(PlanType.SIMPLE).solverType()).isEqualTo(SolverType.GREEDY);
	}

	@Test
	@DisplayName("세 유형의 조합이 같은 입력에서도 세 항목을 모두 돌려준다")
	void 같아도_세_항목을_돌려준다() {
		PlanTypeSolver solver = new PlanTypeSolver(selector);

		Map<PlanType, SolveResult> results = solver.solveAll(SolverCases.singleProduct());

		assertThat(results).hasSize(3);
		for (PlanType type : PlanType.values()) {
			assertThat(results.get(type).selection()).as(type.name()).isEqualTo(sel(ids(1L), ids(), ids()));
		}
	}

	@Test
	@DisplayName("빈 입력도 세 유형을 모두 담는다")
	void 빈_입력() {
		PlanTypeSolver solver = new PlanTypeSolver(selector);

		Map<PlanType, SolveResult> results = solver.solveAll(SolverCases.in(10_000, 600, List.of()));

		assertThat(results.keySet()).containsExactly(PlanType.RECOMMENDED, PlanType.SAVER, PlanType.SIMPLE);
		results.values().forEach(result -> {
			assertThat(result.selection()).isEqualTo(sel(ids(), ids(), ids()));
			assertThat(result.evaluation().totalCost()).isZero();
		});
	}

	@Test
	@DisplayName("결정성: 같은 입력을 두 번 풀어도, 단위 순서를 섞어도 세 유형의 조합이 같다")
	void 결정성() {
		PlanTypeSolver solver = new PlanTypeSolver(selector);
		long seed = 1;

		for (PlanInput input : inputs()) {
			Map<PlanType, SolveResult> first = solver.solveAll(input);
			Map<PlanType, SolveResult> second = solver.solveAll(input);
			Map<PlanType, SolveResult> shuffled = solver.solveAll(SolverCases.shuffledUnits(input, seed++));

			for (PlanType type : PlanType.values()) {
				assertThat(second.get(type).selection()).isEqualTo(first.get(type).selection());
				assertThat(shuffled.get(type).selection()).as("%s %s", type, input).isEqualTo(first.get(type).selection());
			}
		}
	}

	@Test
	@DisplayName("null 입력은 IllegalArgumentException")
	void null_입력() {
		PlanTypeSolver solver = new PlanTypeSolver(selector);

		assertThatThrownBy(() -> solver.solveAll(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("생성자에 null Solver를 넘기면 IllegalArgumentException")
	void null_Solver는_예외() {
		assertThatThrownBy(() -> new PlanTypeSolver(null)).isInstanceOf(IllegalArgumentException.class);
	}
}
