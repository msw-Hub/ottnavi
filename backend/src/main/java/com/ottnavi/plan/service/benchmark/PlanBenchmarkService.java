package com.ottnavi.plan.service.benchmark;

import com.ottnavi.engine.InputHasher;
import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.rule.PlanObjective;
import com.ottnavi.engine.solver.ExactSolver;
import com.ottnavi.engine.solver.GreedySolver;
import com.ottnavi.engine.solver.SolverSelector;
import com.ottnavi.plan.service.benchmark.BenchmarkCase.ExpectedPlan;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 정답 세트로 완전탐색·그리디의 결과와 소요 시간을 측정하는 서비스.
 * DB·외부 호출이 없고 수 초 걸리는 CPU 작업이라 트랜잭션을 열지 않는다(DB 연결을 붙잡지 않으려는 것이다. 저장은 호출하는 쪽이 짧은 트랜잭션으로 한다).
 * 운영 풀이 경로(PlanSolveService·SolverSelector·캐시)를 거치지 않고 두 풀이기를 직접 부른다.
 */
@Slf4j
@Service
public class PlanBenchmarkService {

	private final BenchmarkCaseLoader loader;
	private final ExactSolver exactSolver;
	private final GreedySolver greedySolver;

	public PlanBenchmarkService(BenchmarkCaseLoader loader, ExactSolver exactSolver, GreedySolver greedySolver) {
		this.loader = loader;
		this.exactSolver = exactSolver;
		this.greedySolver = greedySolver;
	}

	/** 정답 세트 전체를 이름 순으로 측정한다. 케이스 안에서는 완전탐색 → 그리디 순서로 실행한다. */
	public List<BenchmarkCaseResult> measure() {
		List<BenchmarkCaseResult> results = new ArrayList<>();
		for (BenchmarkCase benchmarkCase : loader.loadAll()) {
			results.add(measureCase(benchmarkCase));
		}
		return results;
	}

	private BenchmarkCaseResult measureCase(BenchmarkCase benchmarkCase) {
		// 1. 입력과 입력 해시를 만든다
		PlanInput input = loader.toInput(benchmarkCase);
		LocalDate dataAsOf = loader.dataAsOf(benchmarkCase);
		String inputHash = InputHasher.hash(input, dataAsOf);
		PlanObjective objective = PlanObjective.recommended(input); // 측정은 추천형만 한다
		// 2. 후보 조합 수를 센다
		long candidateCount = exactSolver.countCandidateCombinations(input, objective);
		ExpectedPlan expected = benchmarkCase.expected().get(PlanType.RECOMMENDED);
		// 3. 상한을 넘으면 완전탐색을 건너뛴다(현재 정답 세트에서는 일어나지 않는다)
		SolverMeasurement exact = null;
		if (candidateCount > SolverSelector.DEFAULT_MAX_CANDIDATE_COMBINATIONS) {
			log.warn("후보 조합 수가 상한을 넘어 완전탐색을 건너뛴다: case={}, candidateCount={}", benchmarkCase.name(), candidateCount);
		} else {
			MedianTimer.Timed timed = MedianTimer.measure(benchmarkCase.name() + " EXACT", () -> exactSolver.solve(input, objective));
			exact = toMeasurement("EXACT", timed, expected);
		}
		// 4. 그리디를 측정한다. 기대값은 greedyExpected가 있으면 그것, 없으면 완전탐색 기대값이다
		ExpectedPlan greedyExpected = benchmarkCase.greedyExpected();
		if (greedyExpected == null) {
			greedyExpected = expected;
		}
		MedianTimer.Timed greedyTimed = MedianTimer.measure(benchmarkCase.name() + " GREEDY", () -> greedySolver.solve(input, objective));
		SolverMeasurement greedy = toMeasurement("GREEDY", greedyTimed, greedyExpected);
		return new BenchmarkCaseResult(benchmarkCase.name(), inputHash, candidateCount, exact, greedy);
	}

	private SolverMeasurement toMeasurement(String algorithm, MedianTimer.Timed timed, ExpectedPlan expected) {
		Evaluation evaluation = timed.result().evaluation();
		long micros = timed.medianNanos() / 1_000;
		boolean matches = evaluation.mustCompleted() == expected.mustCompleted()
				&& evaluation.score() == expected.score()
				&& evaluation.totalCost() == expected.totalCost();
		return new SolverMeasurement(algorithm, evaluation.mustCompleted(), evaluation.score(), evaluation.totalCost(),
				micros, micros / 1_000, matches);
	}
}
