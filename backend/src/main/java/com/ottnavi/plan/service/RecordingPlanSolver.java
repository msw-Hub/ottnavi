package com.ottnavi.plan.service;

import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.rule.PlanObjective;
import com.ottnavi.engine.solver.ExactSolver;
import com.ottnavi.engine.solver.PlanSolver;
import java.util.EnumMap;
import java.util.Map;

/**
 * 기록용 Solver 감싸개: 실제 Solver(SolverSelector)가 유형 하나를 푸는 데 걸린 시간과 후보 조합 수를 유형별로 남긴다.
 * 엔진 코드를 고치지 않고 측정하려는 것이며, 상태를 가지므로 계산 요청마다 새로 만든다(스레드 간 공유 금지).
 */
public final class RecordingPlanSolver implements PlanSolver {

	/** 유형 하나의 측정값. */
	public record Measurement(int candidateCount, long elapsedMs) {
	}

	private final PlanSolver delegate;          // 실제로 푸는 Solver
	private final ExactSolver candidateCounter; // 후보 조합 수를 세는 데 쓰는 완전탐색
	private final Map<PlanType, Measurement> measurements = new EnumMap<>(PlanType.class); // 유형별 측정값

	public RecordingPlanSolver(PlanSolver delegate, ExactSolver candidateCounter) {
		this.delegate = delegate;
		this.candidateCounter = candidateCounter;
	}

	@Override
	public SolveResult solve(PlanInput input, PlanObjective objective) {
		// 1. 푸는 시간만 잰다(후보 수 세기는 시간에 넣지 않는다)
		long startNanos = System.nanoTime();
		SolveResult result = delegate.solve(input, objective);
		long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;

		// 2. 같은 목적함수 기준 후보 조합 수를 세어 int 범위로 줄인다
		long candidates = candidateCounter.countCandidateCombinations(input, objective);
		int candidateCount = (int) Math.min(candidates, Integer.MAX_VALUE);

		// 3. 유형별로 남긴다
		measurements.put(result.planType(), new Measurement(candidateCount, elapsedMs));
		return result;
	}

	/** 유형의 측정값. 풀지 않은 유형이면 null이다. */
	public Measurement measurementOf(PlanType planType) {
		return measurements.get(planType);
	}
}
