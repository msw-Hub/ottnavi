package com.ottnavi.engine.solver;

import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.SolveResult;
import java.util.Map;

/**
 * 플랜 유형 3종(추천형·절약형·간편형)을 한 번에 푸는 진입점. plan 도메인(Task 063)이 이 클래스를 호출한다.
 * 추천형을 먼저 풀고 그 점수로 절약형·간편형의 점수 하한을 정한다(TECH 1절).
 * 구현 전 껍데기다(Task 039).
 */
public final class PlanTypeSolver {

	/** 유형별 풀이에 쓸 Solver(보통 SolverSelector)를 받는다. */
	public PlanTypeSolver(PlanSolver solver) {
		throw new UnsupportedOperationException("Task 039 구현 전");
	}

	/** 세 유형을 모두 풀어 PlanType 순서(RECOMMENDED, SAVER, SIMPLE)로 돌려준다. 결과 맵은 항상 세 유형을 모두 가진다. */
	public Map<PlanType, SolveResult> solveAll(PlanInput input) {
		throw new UnsupportedOperationException("Task 039 구현 전");
	}
}
