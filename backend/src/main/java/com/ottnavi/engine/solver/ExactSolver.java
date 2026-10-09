package com.ottnavi.engine.solver;

import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.rule.PlanObjective;

/**
 * 정확해 Solver: 달마다 예산 이하 상품 부분집합을 만들고 3개월을 중첩 순회한다(TECH 1절).
 * 지배 조합 제거는 추천형에서만 쓴다(다른 유형은 전부 훑는다). 구현 전 껍데기다(Task 039).
 */
public final class ExactSolver implements PlanSolver {

	/** 이 목적함수로 정확해가 순회할 3개월 조합 수(달별 후보 수의 곱)를 센다. 추천형은 지배 조합 제거 후, 다른 유형은 제거 없이 센다. */
	public long countCandidateCombinations(PlanInput input, PlanObjective objective) {
		throw new UnsupportedOperationException("Task 039 구현 전");
	}

	@Override
	public SolveResult solve(PlanInput input, PlanObjective objective) {
		throw new UnsupportedOperationException("Task 039 구현 전");
	}
}
