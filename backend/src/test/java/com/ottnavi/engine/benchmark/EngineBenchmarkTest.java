package com.ottnavi.engine.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.engine.InputHasher;
import com.ottnavi.engine.benchmark.BenchmarkCase.Basis;
import com.ottnavi.engine.benchmark.BenchmarkCase.ExpectedPlan;
import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.rule.PlanObjective;
import com.ottnavi.engine.solver.BruteForceOracle;
import com.ottnavi.engine.solver.ExactSolver;
import com.ottnavi.engine.solver.GreedySolver;
import com.ottnavi.engine.solver.PlanTypeSolver;
import com.ottnavi.engine.solver.SolverSelector;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 정답 세트(engine-benchmark/*.json) 통과 테스트. 일반 CI에서 돈다.
 * 완전탐색 결과가 기대와 같은지, 소규모(ORACLE)는 무차별 대입 오라클과도 같은지, 그리디가 완전탐색보다 좋지 않은지를 본다.
 */
class EngineBenchmarkTest {

	private final PlanTypeSolver planTypeSolver = new PlanTypeSolver(
			new SolverSelector(new ExactSolver(), new GreedySolver(), SolverSelector.DEFAULT_MAX_CANDIDATE_COMBINATIONS)); // 운영과 같은 구성

	static Stream<BenchmarkCase> cases() {
		return BenchmarkLoader.loadAll().stream();
	}

	@Test
	@DisplayName("정답 세트는 12개 이상이고 이름이 겹치지 않으며 입력이 모두 다르다")
	void 정답_세트의_구성() {
		List<BenchmarkCase> cases = BenchmarkLoader.loadAll();

		assertThat(cases).hasSizeGreaterThanOrEqualTo(12);
		assertThat(cases).extracting(BenchmarkCase::name).doesNotHaveDuplicates();
		Set<String> hashes = new HashSet<>();
		for (BenchmarkCase benchmarkCase : cases) {
			hashes.add(InputHasher.hash(BenchmarkLoader.toInput(benchmarkCase), BenchmarkLoader.dataAsOf(benchmarkCase)));
		}
		assertThat(hashes).as("케이스마다 input_hash가 달라야 한다").hasSize(cases.size());
		assertThat(cases).extracting(BenchmarkCase::note).allSatisfy(note -> assertThat(note).isNotBlank());
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	@DisplayName("세 유형의 풀이 결과가 기대와 같고 달마다 예산을 지킨다")
	void 풀이_결과가_기대와_같다(BenchmarkCase benchmarkCase) {
		PlanInput input = BenchmarkLoader.toInput(benchmarkCase);
		Map<PlanType, SolveResult> results = planTypeSolver.solveAll(input);

		assertThat(benchmarkCase.expected()).as("기대 결과가 세 유형 모두 있어야 한다")
				.containsOnlyKeys(PlanType.RECOMMENDED, PlanType.SAVER, PlanType.SIMPLE);
		for (PlanType type : PlanType.values()) {
			ExpectedPlan expected = benchmarkCase.expected().get(type);
			Evaluation actual = results.get(type).evaluation();

			assertThat(BenchmarkLoader.toExpected(actual)).as("%s %s", benchmarkCase.name(), type).isEqualTo(expected);
			assertThat(BruteForceOracle.withinBudget(input, actual.selection())).as("%s %s 예산", benchmarkCase.name(), type).isTrue();
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	@DisplayName("소규모(ORACLE) 케이스는 무차별 대입 오라클과 세 유형 모두 같다")
	void 소규모는_오라클과_같다(BenchmarkCase benchmarkCase) {
		if (benchmarkCase.expectationBasis() != Basis.ORACLE) {
			return; // 회귀 기준 케이스는 오라클로 풀기엔 조합이 너무 많다
		}
		PlanInput input = BenchmarkLoader.toInput(benchmarkCase);
		Evaluation recommended = BruteForceOracle.bestRecommended(input);

		assertThat(BenchmarkLoader.toExpected(recommended)).isEqualTo(benchmarkCase.expected().get(PlanType.RECOMMENDED));
		assertThat(BenchmarkLoader.toExpected(BruteForceOracle.best(input, PlanType.SAVER, recommended.score())))
				.isEqualTo(benchmarkCase.expected().get(PlanType.SAVER));
		assertThat(BenchmarkLoader.toExpected(BruteForceOracle.best(input, PlanType.SIMPLE, recommended.score())))
				.isEqualTo(benchmarkCase.expected().get(PlanType.SIMPLE));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	@DisplayName("그리디(추천형)는 완전탐색보다 좋지 않고, 다른 결과를 내는 케이스는 기대한 그리디 결과와 같다")
	void 그리디는_완전탐색_이하다(BenchmarkCase benchmarkCase) {
		PlanInput input = BenchmarkLoader.toInput(benchmarkCase);
		PlanObjective objective = PlanObjective.recommended(input);
		Evaluation exact = new ExactSolver().solve(input, objective).evaluation();
		Evaluation greedy = new GreedySolver().solve(input, objective).evaluation();

		assertThat(objective.comparator().compare(exact, greedy)).as("그리디가 완전탐색보다 좋으면 안 된다").isGreaterThanOrEqualTo(0);
		assertThat(BruteForceOracle.withinBudget(input, greedy.selection())).isTrue();
		if (benchmarkCase.greedyExpected() == null) {
			// 파일에 그리디 기대가 없으면 완전탐색과 같은 결과여야 한다
			assertThat(BenchmarkLoader.toExpected(greedy)).isEqualTo(benchmarkCase.expected().get(PlanType.RECOMMENDED));
		} else {
			assertThat(BenchmarkLoader.toExpected(greedy)).isEqualTo(benchmarkCase.greedyExpected());
			assertThat(objective.comparator().compare(exact, greedy)).as("다른 결과면 비교기상 엄격히 나빠야 한다").isPositive();
		}
	}

	@Test
	@DisplayName("그리디가 완전탐색보다 점수가 낮은 케이스가 1개 이상 있다")
	void 그리디가_나쁜_케이스가_있다() {
		assertThat(BenchmarkLoader.loadAll())
				.anyMatch(benchmarkCase -> benchmarkCase.greedyExpected() != null
						&& benchmarkCase.greedyExpected().score() < benchmarkCase.expected().get(PlanType.RECOMMENDED).score());
	}
}
