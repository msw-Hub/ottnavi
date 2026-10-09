package com.ottnavi.engine.solver;

import static com.ottnavi.engine.EngineFixtures.product;
import static com.ottnavi.engine.EngineFixtures.unit;
import static com.ottnavi.engine.solver.SolverCases.in;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.engine.rule.PlanObjective;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 완전탐색 → 그리디 전환 테스트(명세 S5·S9, 5절 4·10). 후보 조합 수가 상한 이하면 EXACT, 초과면 GREEDY, 같으면 EXACT다.
 */
class SolverSelectorTest {

	private final ExactSolver exact = new ExactSolver();
	private final GreedySolver greedy = new GreedySolver();

	/** 상품 2개가 모두 3,000원, 예산 10,000원이고 둘 다 같은 작품을 봐서 절약형·간편형 후보 수는 4^3 = 64, 추천형은 동치 제거로 2^3 = 8이다. */
	private static PlanInput sixtyFourCandidates() {
		return in(10_000, 600, List.of(product(1, 3_000), product(2, 3_000)), unit(1, Priority.WANT, 200, 1L, 2L));
	}

	@Test
	@DisplayName("상한이 후보 조합 수와 같으면 완전탐색(경계값)")
	void 경계값은_완전탐색() {
		PlanInput input = sixtyFourCandidates();
		PlanObjective saver = PlanObjective.saver(input.products(), BruteForceOracle.bestRecommended(input));

		SolveResult result = new SolverSelector(exact, greedy, 64).solve(input, saver);

		assertThat(result.solverType()).isEqualTo(SolverType.EXACT);
		assertThat(result.selection()).isEqualTo(exact.solve(input, saver).selection());
	}

	@Test
	@DisplayName("상한이 후보 조합 수보다 1 작으면 그리디")
	void 상한_초과는_그리디() {
		PlanInput input = sixtyFourCandidates();
		PlanObjective saver = PlanObjective.saver(input.products(), BruteForceOracle.bestRecommended(input));

		SolveResult result = new SolverSelector(exact, greedy, 63).solve(input, saver);

		assertThat(result.solverType()).isEqualTo(SolverType.GREEDY);
		assertThat(result.selection()).isEqualTo(greedy.solve(input, saver).selection());
	}

	@Test
	@DisplayName("상한이 후보 조합 수보다 크면 완전탐색, 상한이 1이어도 후보가 1개면 완전탐색")
	void 상한이_넉넉하면_완전탐색() {
		PlanInput input = sixtyFourCandidates();
		PlanObjective saver = PlanObjective.saver(input.products(), BruteForceOracle.bestRecommended(input));

		assertThat(new SolverSelector(exact, greedy, 65).solve(input, saver).solverType()).isEqualTo(SolverType.EXACT);
		assertThat(new SolverSelector(exact, greedy, Long.MAX_VALUE).solve(input, saver).solverType()).isEqualTo(SolverType.EXACT);

		PlanInput empty = in(10_000, 600, List.of());
		assertThat(new SolverSelector(exact, greedy, 1).solve(empty).solverType()).isEqualTo(SolverType.EXACT);
	}

	@Test
	@DisplayName("후보 수는 실제 완전탐색이 세는 값과 같은 기준이다: 그 값이 상한이면 완전탐색, 하나 작으면 그리디")
	void 완전탐색이_세는_값이_기준이다() {
		PlanInput input = SolverCases.greedyWorse();
		PlanObjective objective = PlanObjective.recommended(input.products());
		long count = exact.countCandidateCombinations(input, objective);
		assertThat(count).isGreaterThan(1);

		assertThat(new SolverSelector(exact, greedy, count).solve(input, objective).solverType()).isEqualTo(SolverType.EXACT);
		SolveResult overLimit = new SolverSelector(exact, greedy, count - 1).solve(input, objective);
		assertThat(overLimit.solverType()).isEqualTo(SolverType.GREEDY);
		// 그리디로 풀린 결과는 그리디 정의와 같다(점수 2·비용 8,000원)
		assertThat(overLimit.evaluation().score()).isEqualTo(2);
		assertThat(overLimit.evaluation().totalCost()).isEqualTo(8_000);
	}

	@Test
	@DisplayName("전환은 푸는 유형마다 따로 정해진다: 추천형은 후보 8개라 완전탐색, 절약형·간편형은 64개라 그리디")
	void 유형마다_전환이_다르다() {
		PlanInput input = sixtyFourCandidates();
		Evaluation recommended = BruteForceOracle.bestRecommended(input);
		SolverSelector selector = new SolverSelector(exact, greedy, 8);

		assertThat(selector.solve(input, PlanObjective.recommended(input.products())).solverType()).isEqualTo(SolverType.EXACT);
		SolveResult saver = selector.solve(input, PlanObjective.saver(input.products(), recommended));
		assertThat(saver.solverType()).isEqualTo(SolverType.GREEDY);
		assertThat(saver.planType()).isEqualTo(PlanType.SAVER);
		assertThat(selector.solve(input, PlanObjective.simple(input.products(), recommended)).solverType()).isEqualTo(SolverType.GREEDY);
	}

	@Test
	@DisplayName("solve(input)은 추천형으로 푼다")
	void 기본_solve는_추천형() {
		PlanInput input = SolverCases.singleProduct();

		SolveResult result = new SolverSelector(exact, greedy, 1_000).solve(input);

		assertThat(result.planType()).isEqualTo(PlanType.RECOMMENDED);
		assertThat(result.selection()).isEqualTo(SolverCases.singleProductSelection());
	}

	@Test
	@DisplayName("기본 상한은 128의 3제곱 = 2,097,152다")
	void 기본_상한() {
		assertThat(SolverSelector.DEFAULT_MAX_CANDIDATE_COMBINATIONS).isEqualTo(2_097_152L);
		assertThat(SolverSelector.DEFAULT_MAX_CANDIDATE_COMBINATIONS).isEqualTo(128L * 128 * 128);
		// 기본 상한이면 후보 수가 작은 입력은 완전탐색으로 푼다
		assertThat(new SolverSelector(exact, greedy, SolverSelector.DEFAULT_MAX_CANDIDATE_COMBINATIONS)
				.solve(SolverCases.greedyWorse()).solverType()).isEqualTo(SolverType.EXACT);
	}

	@Test
	@DisplayName("상한이 0 이하이면 IllegalArgumentException")
	void 상한이_0_이하면_예외() {
		assertThatThrownBy(() -> new SolverSelector(exact, greedy, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new SolverSelector(exact, greedy, -1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new SolverSelector(exact, greedy, Long.MIN_VALUE)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("생성자에 null Solver를 넘기면 IllegalArgumentException")
	void null_Solver는_예외() {
		assertThatThrownBy(() -> new SolverSelector(null, greedy, 100)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new SolverSelector(exact, null, 100)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("null 입력이나 null 목적함수는 IllegalArgumentException")
	void null_입력() {
		SolverSelector selector = new SolverSelector(exact, greedy, 100);
		PlanInput input = SolverCases.singleProduct();

		assertThatThrownBy(() -> selector.solve(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> selector.solve(null, PlanObjective.recommended(input.products()))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> selector.solve(input, null)).isInstanceOf(IllegalArgumentException.class);
	}
}
