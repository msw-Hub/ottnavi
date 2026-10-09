package com.ottnavi.engine.solver;

import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.SolveResult;

/**
 * 정확해 Solver: 달마다 예산 이하 상품 부분집합을 만들고 지배 조합을 제거한 뒤 3개월을 중첩 순회한다(TECH 1절).
 * 구현 전 껍데기다(Task 039).
 */
public final class ExactSolver implements PlanSolver {

	/** 달별 후보 수의 곱, 즉 정확해가 순회할 3개월 조합 수를 센다(지배 조합 제거 후). SolverSelector가 그리디 전환 판단에 쓴다. */
	public long countCandidateCombinations(PlanInput input) {
		throw new UnsupportedOperationException("Task 039 구현 전");
	}

	@Override
	public SolveResult solve(PlanInput input) {
		throw new UnsupportedOperationException("Task 039 구현 전");
	}
}
