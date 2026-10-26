package com.ottnavi.plan.domain.enums;

/** 플랜이 바뀐 이유의 종류(계약 PlanChangeReasonType, Task 068 결정). 찜·예산 변경은 이유로 쓰지 않는다 */
public enum PlanChangeReasonType {
	PRICE_CHANGED,        // 구독 상품의 요금이 바뀜
	TITLE_LEFT_SERVICE,   // 작품이 어느 서비스에서 빠짐
	TITLE_JOINED_SERVICE, // 작품이 어느 서비스에 새로 들어옴
	MARKED_WATCHED        // 사용자가 "다 봤음"으로 체크함
}
