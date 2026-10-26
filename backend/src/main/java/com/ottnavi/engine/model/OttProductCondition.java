package com.ottnavi.engine.model;

/** 상품에 대한 사용자의 현재 구독 상태(ERD C 영역). */
public enum OttProductCondition {

	NONE,       // 구독하지 않음. 선택된 달마다 가격이 든다
	SUBSCRIBED, // 이미 구독 중. 0번째 달은 선택하지 않아도 비용 0으로 볼 수 있고, 1·2번째 달은 선택해야 유지(가격 발생, D2)
	FREE        // 무료 이용. 선택과 무관하게 모든 달에 비용 0으로 볼 수 있다(PRD 5.4 구독 상태 반영, D1)
}
