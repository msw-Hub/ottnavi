package com.ottnavi.plan.domain.enums;

/** 플랜 상태(ERD plan.status). DRAFT(계산 결과) → ACTIVE(저장) → ARCHIVED */
public enum PlanStatus {
	DRAFT,    // 계산만 한 결과. 사용자당 플랜 유형마다 최대 1개
	ACTIVE,   // 저장된 플랜. 사용자당 최대 1개
	ARCHIVED  // 새 플랜이 저장돼 보관된 이전 플랜
}
