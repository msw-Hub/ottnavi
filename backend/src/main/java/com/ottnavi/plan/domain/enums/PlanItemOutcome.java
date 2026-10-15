package com.ottnavi.plan.domain.enums;

/** 찜 단위의 계산 결과(ERD plan_item.outcome). UNSCHEDULED이면 이유 코드(UnitReason)가 반드시 있다 */
public enum PlanItemOutcome {
	SCHEDULED,   // 3개월 안에 배정됨
	UNSCHEDULED  // 배정하지 못함("남은 작품")
}
