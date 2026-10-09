package com.ottnavi.engine.model;

/** 플랜 유형. 같은 입력과 예산에서 목적함수만 다르다(PRD 5.4, 11절 35번). plan.plan_type 컬럼에 남는다(ERD). */
public enum PlanType {
	RECOMMENDED, // 추천형: 꼭 완주 → 점수 → 총비용 → 결제 미루기
	SAVER,       // 절약형: 꼭 완주 → 점수 하한 → 총비용 최소
	SIMPLE       // 간편형: 꼭 완주 → 점수 하한 → 새 결제 횟수 최소
}
