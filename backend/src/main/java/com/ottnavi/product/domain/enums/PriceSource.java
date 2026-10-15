package com.ottnavi.product.domain.enums;

/**
 * 계산에 쓰는 금액의 출처(계약 PriceSource, ERD plan_month_product.price_source).
 * 내 서비스 요금 응답(Task 060)은 ADMIN·USER_OVERRIDE만 돌려준다. ALREADY_PAID·FREE는 플랜 계산(Task 063)이 정한다.
 */
public enum PriceSource {
	ADMIN,         // 관리자가 등록한 기본 월 요금
	USER_OVERRIDE, // 사용자가 고친 금액
	ALREADY_PAID,  // 이미 결제해 이번 달 비용에서 제외
	FREE           // 무료 이용이라 비용에서 제외
}
