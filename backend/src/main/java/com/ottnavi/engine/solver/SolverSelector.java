package com.ottnavi.engine.solver;

import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.rule.PlanObjective;

/**
 * 기본은 완전탐색, 후보 조합 수가 상한을 넘으면 그리디로 바꿔 푸는 선택기(TECH 1절 T-4).
 * 후보 조합 수는 푸는 목적함수 기준으로 센다(ExactSolver.countCandidateCombinations).
 */
public final class SolverSelector implements PlanSolver {

	public static final long DEFAULT_MAX_CANDIDATE_COMBINATIONS = 2_097_152L; // 128의 3제곱: MVP는 항상 완전탐색. Task 040 측정 반영(후보 7개일 때 절약형·간편형 조합 수, 단위 30개 기준 이 PC에서 절약형 약 1.6초·간편형 약 1.8초, Cloudtype 실측은 Task 100에서 재확인)

	private final ExactSolver exactSolver;         // 기본 Solver
	private final GreedySolver greedySolver;       // 후보 조합 수가 상한을 넘을 때 쓰는 Solver
	private final long maxCandidateCombinations;   // 완전탐색으로 풀 수 있는 후보 조합 수 상한(이 값까지는 완전탐색)

	/** 완전탐색·그리디와 전환 상한(후보 조합 수)을 받아 선택기를 만든다. 상한은 설정 값이며 기본값의 근거는 DEFAULT_MAX_CANDIDATE_COMBINATIONS 주석(Task 040 측정)이다. */
	public SolverSelector(ExactSolver exactSolver, GreedySolver greedySolver, long maxCandidateCombinations) {
		if (exactSolver == null || greedySolver == null) {
			throw new IllegalArgumentException("완전탐색·그리디 Solver는 null일 수 없다");
		}
		if (maxCandidateCombinations <= 0) {
			throw new IllegalArgumentException("후보 조합 수 상한은 1 이상이어야 한다: maxCandidateCombinations=" + maxCandidateCombinations);
		}
		this.exactSolver = exactSolver;
		this.greedySolver = greedySolver;
		this.maxCandidateCombinations = maxCandidateCombinations;
	}

	@Override
	public SolveResult solve(PlanInput input, PlanObjective objective) {
		if (input == null || objective == null) {
			throw new IllegalArgumentException("입력과 목적함수는 null일 수 없다");
		}
		// S5: 상한보다 크면 그리디, 같거나 작으면 완전탐색. 결과의 solverType은 실제로 푼 쪽이 정한다
		long candidateCombinations = exactSolver.countCandidateCombinations(input, objective);
		if (candidateCombinations > maxCandidateCombinations) {
			return greedySolver.solve(input, objective);
		}
		return exactSolver.solve(input, objective);
	}
}
