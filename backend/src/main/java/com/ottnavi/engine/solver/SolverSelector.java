package com.ottnavi.engine.solver;

import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.rule.PlanObjective;

/**
 * 기본은 정확해, 후보 조합 수가 상한을 넘으면 그리디로 바꿔 푸는 선택기(TECH 1절 T-4).
 * 후보 조합 수는 푸는 목적함수 기준으로 센다(ExactSolver.countCandidateCombinations).
 * 구현 전 껍데기다(Task 039).
 */
public final class SolverSelector implements PlanSolver {

	public static final long DEFAULT_MAX_CANDIDATE_COMBINATIONS = 2_097_152L; // 128의 3제곱: MVP는 항상 정확해. R11-30 측정 전 잠정값

	/** 정확해·그리디와 전환 상한(후보 조합 수)을 받아 선택기를 만든다. 상한은 설정 값이며 R11-30 전까지 잠정값이다. */
	public SolverSelector(ExactSolver exactSolver, GreedySolver greedySolver, long maxCandidateCombinations) {
		throw new UnsupportedOperationException("Task 039 구현 전");
	}

	@Override
	public SolveResult solve(PlanInput input, PlanObjective objective) {
		throw new UnsupportedOperationException("Task 039 구현 전");
	}
}
