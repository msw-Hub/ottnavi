package com.ottnavi.engine.model;

/**
 * 배정하지 못한 시청 단위의 이유 코드.
 * PRD 5.4 남은 작품의 이유 코드: 선언 순서(NO_PROVIDER → UNKNOWN → BUDGET → TIME)대로 판정해 처음 맞는 것을 쓴다.
 */
public enum UnitReason {

	NO_PROVIDER, // 7개 서비스 모두 "없음"
	UNKNOWN,     // "있음"은 없고 "모름"이 하나 이상
	BUDGET,      // 시청 가능 상품은 있으나 선택된 구독 조합의 어느 달에도 없음(TECH 1절, 도출)
	TIME         // 시청 가능 상품이 있는 달은 있으나 시청 시간이 모자라 3개월 안에 다 보지 못함(TECH 1절, 도출). 긴 시즌 조각 120분 규칙(결정 4)으로 못 나누는 경우도 포함
}
