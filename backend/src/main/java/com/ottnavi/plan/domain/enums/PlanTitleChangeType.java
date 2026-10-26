package com.ottnavi.plan.domain.enums;

/** 작품(시즌) 하나의 변화 종류(계약 PlanTitleChangeType). 자리는 (시청 완료 달, 그 달의 서비스)다 */
public enum PlanTitleChangeType {
	ADDED,           // 배정 안 됨 → 배정됨
	REMOVED,         // 배정됨 → 배정 안 됨 또는 새 플랜에서 빠짐
	MOVED_MONTH,     // 같은 서비스에서 보는 달만 바뀜
	CHANGED_SERVICE  // 보는 서비스가 바뀜(달이 같이 바뀌어도 이 값)
}
