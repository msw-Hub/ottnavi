package com.ottnavi.plan.domain.enums;

/** 예시 메일을 만들 수 없는 사유(계약 PlanMailPreviewNoMailReason) */
public enum PlanMailPreviewNoMailReason {
	NO_CURRENT_PLAN, // 저장한 현재 플랜이 없다
	NO_SUBSCRIBE_GUIDE // 이번 달 새로 가입할 상품이 없거나 플랜의 첫 달이 이번 달이 아니다
}
