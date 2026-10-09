package com.ottnavi.engine.model;

/**
 * Solver가 고른 최선의 구독 조합과 그 평가 결과, 그리고 플랜 유형과 사용한 알고리즘.
 */
public record SolveResult(
		Evaluation evaluation, // 고른 조합의 평가 결과(조합 자체는 evaluation.selection())
		PlanType planType,     // 이 결과의 플랜 유형
		SolverType solverType  // 이 결과를 고른 알고리즘
) {

	/** 값이 비어 있지 않은지 검증한다. */
	public SolveResult {
		if (evaluation == null || planType == null || solverType == null) {
			throw new IllegalArgumentException("평가 결과, 플랜 유형, 알고리즘은 null일 수 없다");
		}
	}

	/** 고른 구독 조합. */
	public Selection selection() {
		return evaluation.selection();
	}
}
