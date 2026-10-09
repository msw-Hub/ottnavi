package com.ottnavi.engine.solver;

import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.SolveResult;

/**
 * 기본은 정확해, 후보 조합 수가 상한을 넘으면 그리디로 바꿔 푸는 선택기(TECH 1절 T-4).
 * 구현 전 껍데기다(Task 039).
 */
public final class SolverSelector implements PlanSolver {

	/** 정확해·그리디와 전환 상한(후보 조합 수)을 받아 선택기를 만든다. 상한은 설정 값이며 R11-30 전까지 잠정값이다. */
	public SolverSelector(ExactSolver exactSolver, GreedySolver greedySolver, long maxCandidateCombinations) {
		throw new UnsupportedOperationException("Task 039 구현 전");
	}

	@Override
	public SolveResult solve(PlanInput input) {
		throw new UnsupportedOperationException("Task 039 구현 전");
	}
}
