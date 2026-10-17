package com.ottnavi.plan.service.model;

/** 플랜 한 건 재계산의 결과 */
public enum PlanRecalcOutcome {
	UPDATED, // 새 ACTIVE로 갱신했다(변경 요약은 기록 조건을 채웠을 때만 남는다)
	SKIPPED  // 건너뛰었다(다 본 찜뿐이라 계산 불가, 그 사이 사용자가 직접 저장함 등)
}
