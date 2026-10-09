package com.ottnavi.engine.model;

/** 조합을 고른 알고리즘. plan.algorithm 컬럼에 남는다(ERD, TECH 1절). */
public enum SolverType {
	EXACT,  // 완전탐색: 예산 이하 모든 조합 중 최선을 고른다
	GREEDY  // 그리디: 0번째 달부터 달마다 가장 좋은 조합을 고정한다
}
