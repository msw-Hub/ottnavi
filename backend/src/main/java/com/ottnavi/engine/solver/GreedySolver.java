package com.ottnavi.engine.solver;

import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.rule.PlanObjective;

/**
 * 그리디 Solver: 0번째 달부터 앞선 달을 고정하고 뒤의 달은 비운 채로, 달마다 주어진 목적함수가 가장 좋다고 보는 조합 하나를 고른다(PRD 5.4).
 * 구현 전 껍데기다(Task 039).
 */
public final class GreedySolver implements PlanSolver {

	@Override
	public SolveResult solve(PlanInput input, PlanObjective objective) {
		throw new UnsupportedOperationException("Task 039 구현 전");
	}
}
