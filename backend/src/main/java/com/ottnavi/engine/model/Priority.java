package com.ottnavi.engine.model;

/**
 * 찜한 시청 단위의 우선순위.
 * 선언 순서(MUST → WANT → MAYBE)가 배정 순서의 두 번째 기준이다. {@link WatchUnit#ASSIGNMENT_ORDER}가 enum 자연 순서({@code compareTo})로 이 순서를 쓴다.
 */
public enum Priority {

	MUST(0),  // 꼭 볼 작품. 점수 대신 mustCompleted(목적함수 ①)로 따로 센다(D5)
	WANT(2),  // 보고 싶은 작품. 완주하면 2점
	MAYBE(1); // 여유될 때 볼 작품. 완주하면 1점

	private final int score; // 완주했을 때 score(목적함수 ②)에 더하는 점수

	Priority(int score) {
		this.score = score;
	}

	/** 완주했을 때 score에 더하는 점수를 돌려준다(PRD 5.4 목적함수 ②, MUST는 0). */
	public int score() {
		return score;
	}
}
