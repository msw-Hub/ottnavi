package com.ottnavi.engine.solver;

import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.SolveResult;

/**
 * 입력에서 3개월 구독 조합 중 최선을 고르는 Solver(TECH 1절). 정확해와 그리디가 이 인터페이스를 공유한다.
 */
public interface PlanSolver {

	/** 월 예산 안에서 목적함수(EvaluationComparator)가 가장 좋은 구독 조합을 고른다. 입력이 같으면 결과도 항상 같다. */
	SolveResult solve(PlanInput input);
}
