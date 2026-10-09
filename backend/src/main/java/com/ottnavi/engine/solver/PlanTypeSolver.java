package com.ottnavi.engine.solver;

import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.SolveResult;
import com.ottnavi.engine.rule.PlanObjective;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 플랜 유형 3종(추천형·절약형·간편형)을 한 번에 푸는 진입점. plan 도메인(Task 063)이 이 클래스를 호출한다.
 * 추천형을 먼저 풀고 그 결과로 절약형·간편형의 점수 하한과 시작 조합을 정한다(TECH 1절).
 */
public final class PlanTypeSolver {

	private final PlanSolver solver; // 유형마다 쓸 Solver(보통 SolverSelector)

	/** 유형별 풀이에 쓸 Solver(보통 SolverSelector)를 받는다. */
	public PlanTypeSolver(PlanSolver solver) {
		if (solver == null) {
			throw new IllegalArgumentException("Solver는 null일 수 없다");
		}
		this.solver = solver;
	}

	/** 세 유형을 모두 풀어 PlanType 순서(RECOMMENDED, SAVER, SIMPLE)로 돌려준다. 결과 맵은 항상 세 유형을 모두 가진다. */
	public Map<PlanType, SolveResult> solveAll(PlanInput input) {
		if (input == null) {
			throw new IllegalArgumentException("입력은 null일 수 없다");
		}
		// PRD 11절 35번: 추천형을 먼저 푼다. 그리디로 풀렸어도 그 결과의 점수·조합을 그대로 기준으로 쓴다
		SolveResult recommended = solver.solve(input, PlanObjective.recommended(input));
		SolveResult saver = solver.solve(input, PlanObjective.saver(input, recommended.evaluation()));
		SolveResult simple = solver.solve(input, PlanObjective.simple(input, recommended.evaluation()));

		// EnumMap은 enum 선언 순서(RECOMMENDED, SAVER, SIMPLE)로 순회된다. 세 유형의 조합이 같아도 합치지 않는다(호출하는 쪽의 몫)
		Map<PlanType, SolveResult> results = new EnumMap<>(PlanType.class);
		results.put(PlanType.RECOMMENDED, recommended);
		results.put(PlanType.SAVER, saver);
		results.put(PlanType.SIMPLE, simple);
		return Collections.unmodifiableMap(results);
	}
}
