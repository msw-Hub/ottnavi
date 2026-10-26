package com.ottnavi.engine.solver;

import static com.ottnavi.engine.EngineFixtures.product;
import static com.ottnavi.engine.solver.SolverCases.ids;
import static com.ottnavi.engine.solver.SolverCases.in;
import static com.ottnavi.engine.solver.SolverCases.sel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.Selection;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.engine.rule.Evaluator;
import com.ottnavi.engine.rule.PlanObjective;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 그리디 Solver 테스트(명세 S4·S8, 5절 2·3·6·9·10·12·13·17). 추천형은 S4(빈 상태에서 달마다 확정),
 * 절약형·간편형은 S8(baseline에서 출발해 개선)이다. 기대값은 손 계산 또는 두 정의를 독립적으로 구현한 오라클이다.
 */
class GreedySolverTest {

	private final GreedySolver greedy = new GreedySolver();
	private final ExactSolver exact = new ExactSolver();

	private static List<PlanInput> oracleInputs() {
		List<PlanInput> inputs = new ArrayList<>(SolverCases.handCases());
		inputs.addAll(SolverCases.randomInputs(60, 20261010L));
		return inputs;
	}

	@Test
	@DisplayName("그리디가 완전탐색과 다른 입력: 점수 2·비용 8,000원 대 완전탐색 점수 4·비용 28,000원, 차이를 compare로 단언한다")
	void 그리디가_완전탐색보다_나쁜_케이스() {
		PlanInput input = SolverCases.greedyWorse();
		PlanObjective objective = PlanObjective.recommended(input);

		SolveResult greedyResult = greedy.solve(input);
		SolveResult exactResult = exact.solve(input);

		assertThat(greedyResult.evaluation().score()).isEqualTo(2);
		assertThat(greedyResult.evaluation().totalCost()).isEqualTo(8_000);
		assertThat(greedyResult.selection()).isEqualTo(SolverCases.greedyWorseGreedySelection());
		assertThat(greedyResult.solverType()).isEqualTo(SolverType.GREEDY);
		assertThat(greedyResult.planType()).isEqualTo(PlanType.RECOMMENDED);
		assertThat(exactResult.evaluation().score()).isEqualTo(4);
		assertThat(exactResult.evaluation().totalCost()).isEqualTo(28_000);
		assertThat(exactResult.selection()).isEqualTo(sel(ids(2L), ids(1L), ids(1L)));
		assertThat(objective.comparator().compare(exactResult.evaluation(), greedyResult.evaluation())).isPositive();
		assertThat(BruteForceOracle.comparator(PlanType.RECOMMENDED, input, 0)
				.compare(exactResult.evaluation(), greedyResult.evaluation())).isPositive();
	}

	@Test
	@DisplayName("추천형 그리디는 0번째 달부터 확정하고, 절약형·간편형 그리디는 baseline에서 개선한다(손 계산)")
	void 유형별_그리디_손_계산() {
		PlanInput input = SolverCases.saverVsSimple();
		Evaluation baseline = BruteForceOracle.bestRecommended(input);

		SolveResult recommended = greedy.solve(input);
		SolveResult simple = greedy.solve(input, PlanObjective.simple(input, baseline));
		SolveResult saver = greedy.solve(input, PlanObjective.saver(input, baseline));

		assertThat(recommended.selection()).isEqualTo(SolverCases.saverVsSimpleGreedyRecommended());
		assertThat(recommended.selection()).isEqualTo(exact.solve(input).selection()); // 조기 시청(④) 규칙에서는 0번째 달부터 확정하는 그리디가 이 입력의 완전탐색과 같다
		assertThat(saver.selection()).isEqualTo(SolverCases.saverVsSimpleGreedySaver());
		assertThat(simple.selection()).isEqualTo(SolverCases.saverVsSimpleGreedySimple());
		assertThat(simple.planType()).isEqualTo(PlanType.SIMPLE);
		assertThat(saver.planType()).isEqualTo(PlanType.SAVER);
		assertThat(simple.solverType()).isEqualTo(SolverType.GREEDY);
	}

	@Test
	@DisplayName("그리디는 정의를 독립적으로 구현한 오라클과 세 유형 모두 같은 조합을 고른다(추천형 S4, 절약형·간편형 S8)")
	void 독립_구현_그리디와_같다() {
		for (PlanInput input : oracleInputs()) {
			Evaluation recommended = BruteForceOracle.bestRecommended(input);
			for (PlanType type : PlanType.values()) {
				Comparator<Evaluation> comparator = BruteForceOracle.comparator(type, input, recommended.score());
				Evaluation expected = type == PlanType.RECOMMENDED
						? BruteForceOracle.greedy(input, comparator)
						: BruteForceOracle.improve(input, comparator, recommended.selection());

				SolveResult actual = greedy.solve(input, SolverCases.objective(type, input, recommended));

				assertThat(actual.selection()).as("%s %s", type, input).isEqualTo(expected.selection());
				assertThat(actual.evaluation().score()).isEqualTo(expected.score());
				assertThat(actual.evaluation().totalCost()).isEqualTo(expected.totalCost());
				assertThat(actual.evaluation().mustCompleted()).isEqualTo(expected.mustCompleted());
				assertThat(actual.solverType()).isEqualTo(SolverType.GREEDY);
				assertThat(actual.planType()).isEqualTo(type);
			}
		}
	}

	@Test
	@DisplayName("그리디는 달마다 예산을 지키고 후보 상품(S6)만 쓰며 같은 목적함수의 완전탐색보다 좋지 않다")
	void 예산을_지키고_완전탐색보다_좋지_않다() {
		for (PlanInput input : oracleInputs()) {
			Evaluation recommended = BruteForceOracle.bestRecommended(input);
			for (PlanType type : PlanType.values()) {
				PlanObjective objective = SolverCases.objective(type, input, recommended);
				Comparator<Evaluation> comparator = BruteForceOracle.comparator(type, input, recommended.score());

				SolveResult greedyResult = greedy.solve(input, objective);
				Evaluation exactBest = BruteForceOracle.best(input, type, recommended.score());

				assertThat(BruteForceOracle.withinBudget(input, greedyResult.selection())).as("%s %s 예산", type, input).isTrue();
				assertThat(BruteForceOracle.usesOnlyCandidates(input, greedyResult.selection())).as("%s %s 후보", type, input).isTrue();
				assertThat(comparator.compare(exactBest, greedyResult.evaluation())).as("%s %s", type, input).isGreaterThanOrEqualTo(0);
				assertThat(objective.comparator().compare(exactBest, greedyResult.evaluation())).as("%s %s 실제 비교기", type, input)
						.isGreaterThanOrEqualTo(0);
			}
		}
	}

	@Test
	@DisplayName("baseline 개선(성질 17): 절약형·간편형 그리디는 baseline보다 나쁘지 않고 점수 하한을 지키며 빈 플랜이 되지 않는다")
	void baseline보다_나쁘지_않다() {
		for (PlanInput input : oracleInputs()) {
			Evaluation recommended = BruteForceOracle.bestRecommended(input);
			int floor = BruteForceOracle.scoreFloor(recommended.score());
			Evaluator evaluator = new Evaluator(input);
			for (PlanType type : List.of(PlanType.SAVER, PlanType.SIMPLE)) {
				PlanObjective objective = SolverCases.objective(type, input, recommended);

				SolveResult result = greedy.solve(input, objective);

				assertThat(objective.comparator().compare(result.evaluation(), evaluator.evaluate(recommended.selection())))
						.as("%s %s", type, input).isGreaterThanOrEqualTo(0);
				assertThat(result.evaluation().score()).as("%s %s", type, input).isGreaterThanOrEqualTo(floor);
				if (recommended.score() >= 1) {
					assertThat(result.evaluation().score()).as("빈 플랜 방지 %s %s", type, input).isGreaterThanOrEqualTo(1);
				}
			}
		}
	}

	@Test
	@DisplayName("성질 12·13이 그리디에도 성립한다: 절약형은 비용 ≤ 추천형, 간편형은 가입 횟수 ≤ 추천형, 꼭 시청 완료 수는 추천형과 같다")
	void 절약형_간편형_성질() {
		for (PlanInput input : oracleInputs()) {
			Evaluation recommended = BruteForceOracle.bestRecommended(input);
			int floor = BruteForceOracle.scoreFloor(recommended.score());

			SolveResult saver = greedy.solve(input, PlanObjective.saver(input, recommended));
			SolveResult simple = greedy.solve(input, PlanObjective.simple(input, recommended));

			assertThat(saver.evaluation().score()).as("%s", input).isGreaterThanOrEqualTo(floor);
			assertThat(saver.evaluation().totalCost()).as("%s", input).isLessThanOrEqualTo(recommended.totalCost());
			assertThat(saver.evaluation().mustCompleted()).isEqualTo(recommended.mustCompleted());
			assertThat(simple.evaluation().score()).as("%s", input).isGreaterThanOrEqualTo(floor);
			assertThat(BruteForceOracle.joinTotal(simple.selection(), input.products())).as("%s", input)
					.isLessThanOrEqualTo(BruteForceOracle.joinTotal(recommended.selection(), input.products()));
			assertThat(simple.evaluation().mustCompleted()).isEqualTo(recommended.mustCompleted());
		}
	}

	@Test
	@DisplayName("그리디가 돌려주는 평가 결과는 그 조합을 Evaluator로 평가한 값과 같다")
	void 평가_결과가_조합과_일치한다() {
		for (PlanInput input : oracleInputs()) {
			Evaluation recommended = BruteForceOracle.bestRecommended(input);
			for (PlanType type : PlanType.values()) {
				SolveResult result = greedy.solve(input, SolverCases.objective(type, input, recommended));

				Evaluation recomputed = new Evaluator(input).evaluate(result.selection());

				assertThat(result.evaluation().mustCompleted()).isEqualTo(recomputed.mustCompleted());
				assertThat(result.evaluation().score()).isEqualTo(recomputed.score());
				assertThat(result.evaluation().totalCost()).isEqualTo(recomputed.totalCost());
				assertThat(result.evaluation().units()).isEqualTo(recomputed.units());
			}
		}
	}

	@Test
	@DisplayName("결정성: 같은 입력을 두 번 풀어도, 단위 순서를 섞어도 같은 결과다")
	void 결정성() {
		long seed = 1;
		for (PlanInput input : oracleInputs()) {
			Evaluation recommended = BruteForceOracle.bestRecommended(input);
			for (PlanType type : PlanType.values()) {
				SolveResult first = greedy.solve(input, SolverCases.objective(type, input, recommended));

				assertThat(greedy.solve(input, SolverCases.objective(type, input, recommended)).selection()).isEqualTo(first.selection());
				PlanInput shuffled = SolverCases.shuffledUnits(input, seed++);
				assertThat(greedy.solve(shuffled, SolverCases.objective(type, shuffled, recommended)).selection())
						.as("%s %s", type, input).isEqualTo(first.selection());
			}
		}
	}

	@Test
	@DisplayName("빈 입력: 세 달 모두 빈 집합이고 비용 0이다")
	void 빈_입력() {
		PlanInput input = in(10_000, 600, List.of(product(1, 5_000)));
		Evaluation recommended = BruteForceOracle.bestRecommended(input);

		for (PlanType type : PlanType.values()) {
			SolveResult result = greedy.solve(input, SolverCases.objective(type, input, recommended));

			assertThat(result.selection()).isEqualTo(sel(ids(), ids(), ids()));
			assertThat(result.evaluation().totalCost()).isZero();
			assertThat(result.solverType()).isEqualTo(SolverType.GREEDY);
		}
	}

	@Test
	@DisplayName("예산 0원: 비용이 드는 상품은 고르지 않는다")
	void 예산_0원() {
		SolveResult result = greedy.solve(SolverCases.zeroBudget());

		assertThat(result.evaluation().totalCost()).isZero();
		assertThat(result.evaluation().score()).isEqualTo(2);
		assertThat(result.selection()).isEqualTo(sel(ids(), ids(), ids()));
	}

	@Test
	@DisplayName("동치 제거 동점 입력도 그리디는 0번째 달부터 확정한다: 같은 가격이면 ID가 작은 p1")
	void 동점_입력() {
		SolveResult result = greedy.solve(SolverCases.tiePair());

		assertThat(result.selection()).isEqualTo(sel(ids(1L), ids(), ids()));
	}

	@Test
	@DisplayName("FREE 상품은 그리디 결과에도 나타나지 않는다(S6)")
	void 무료_상품은_나타나지_않는다() {
		for (PlanInput input : List.of(SolverCases.freeProduct(), SolverCases.freeProductLowId())) {
			Selection selection = greedy.solve(input).selection();

			for (int month = 0; month < Selection.MONTH_COUNT; month++) {
				assertThat(selection.productIdsOf(month)).as("%d번째 달", month).doesNotContain(1L, 3L);
			}
		}
	}

	@Test
	@DisplayName("null 입력이나 null 목적함수는 IllegalArgumentException이다")
	void null_입력() {
		PlanInput input = SolverCases.singleProduct();

		assertThatThrownBy(() -> greedy.solve(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> greedy.solve(null, PlanObjective.recommended(input))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> greedy.solve(input, null)).isInstanceOf(IllegalArgumentException.class);
	}
}
