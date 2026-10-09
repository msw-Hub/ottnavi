package com.ottnavi.engine.benchmark;

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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 일회성 기대값 생성기. 환경 변수 {@code ENGINE_BENCHMARK_GENERATE=1}일 때만 돌고 일반 빌드에서는 건너뛴다.
 *
 * <p>정답 세트 파일(src/main/resources/engine-benchmark)을 <b>절대 덮어쓰지 않는다</b>. 입력 파일에서 기대값을 계산해
 * {@code build/engine-benchmark-generated/} 아래에 새 파일로만 쓴다. 결과를 눈으로 확인한 뒤 사람이 직접 복사한다.
 * 기대값 근거는 케이스의 {@code expectationBasis}를 따른다: ORACLE이면 무차별 대입 오라클, REGRESSION이면 SolverSelector(완전탐색).
 * 그리디(추천형)가 완전탐색과 다른 결과를 내는 케이스에는 그리디의 기대 결과를 {@code greedyExpected}로 함께 쓴다.
 */
@EnabledIfEnvironmentVariable(named = "ENGINE_BENCHMARK_GENERATE", matches = "1")
class EngineBenchmarkGenerator {

	private static final Path OUTPUT_DIR = Path.of("build", "engine-benchmark-generated"); // Gradle test의 작업 폴더는 backend/

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	@Test
	void 기대값을_생성해_build_폴더에_쓴다() throws IOException {
		Files.createDirectories(OUTPUT_DIR);
		for (BenchmarkCase benchmarkCase : BenchmarkLoader.loadAll()) {
			PlanInput input = BenchmarkLoader.toInput(benchmarkCase);
			Map<PlanType, ExpectedPlan> expected = benchmarkCase.expectationBasis() == Basis.ORACLE
					? fromOracle(input)
					: fromSolver(input);
			ExpectedPlan greedyExpected = greedyIfDifferent(input, expected.get(PlanType.RECOMMENDED));

			BenchmarkCase generated = new BenchmarkCase(benchmarkCase.name(), benchmarkCase.note(), benchmarkCase.expectationBasis(),
					benchmarkCase.dataAsOf(), benchmarkCase.monthlyBudget(), benchmarkCase.monthlyWatchMinutes(),
					benchmarkCase.products(), benchmarkCase.units(), expected, greedyExpected);
			String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(generated);
			Files.writeString(OUTPUT_DIR.resolve(benchmarkCase.name() + ".json"), json + System.lineSeparator(), StandardCharsets.UTF_8);
			System.out.println("[engine-benchmark] 생성: " + benchmarkCase.name() + " (" + benchmarkCase.expectationBasis() + ") "
					+ summary(expected) + " greedy=" + greedyExpected);
		}
	}

	/** 오라클(무차별 대입)로 세 유형의 기대값을 만든다. 절약형·간편형의 점수 하한은 추천형 점수로 정한다. */
	private static Map<PlanType, ExpectedPlan> fromOracle(PlanInput input) {
		Evaluation recommended = BruteForceOracle.bestRecommended(input);
		Map<PlanType, ExpectedPlan> expected = new EnumMap<>(PlanType.class);
		expected.put(PlanType.RECOMMENDED, BenchmarkLoader.toExpected(recommended));
		expected.put(PlanType.SAVER, BenchmarkLoader.toExpected(BruteForceOracle.best(input, PlanType.SAVER, recommended.score())));
		expected.put(PlanType.SIMPLE, BenchmarkLoader.toExpected(BruteForceOracle.best(input, PlanType.SIMPLE, recommended.score())));
		return expected;
	}

	/** 운영과 같은 구성(SolverSelector)으로 세 유형을 풀어 회귀 기준을 만든다. */
	private static Map<PlanType, ExpectedPlan> fromSolver(PlanInput input) {
		PlanTypeSolver solver = new PlanTypeSolver(new SolverSelector(new ExactSolver(), new GreedySolver(),
				SolverSelector.DEFAULT_MAX_CANDIDATE_COMBINATIONS));
		Map<PlanType, ExpectedPlan> expected = new EnumMap<>(PlanType.class);
		for (Map.Entry<PlanType, SolveResult> entry : solver.solveAll(input).entrySet()) {
			expected.put(entry.getKey(), BenchmarkLoader.toExpected(entry.getValue().evaluation()));
		}
		return expected;
	}

	/** 그리디(추천형) 결과가 완전탐색 결과와 다르면 그리디 결과를, 같으면 null을 돌려준다. */
	private static ExpectedPlan greedyIfDifferent(PlanInput input, ExpectedPlan exactRecommended) {
		PlanObjective objective = PlanObjective.recommended(input);
		Evaluation greedy = new GreedySolver().solve(input, objective).evaluation();
		ExpectedPlan greedyPlan = BenchmarkLoader.toExpected(greedy);
		return greedyPlan.equals(exactRecommended) ? null : greedyPlan;
	}

	private static String summary(Map<PlanType, ExpectedPlan> expected) {
		StringBuilder builder = new StringBuilder();
		expected.forEach((type, plan) -> builder.append(type).append("=").append(plan.months())
				.append(" must=").append(plan.mustCompleted()).append(" score=").append(plan.score())
				.append(" cost=").append(plan.totalCost()).append(" | "));
		return builder.toString();
	}
}
