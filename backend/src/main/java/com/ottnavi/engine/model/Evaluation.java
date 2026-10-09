package com.ottnavi.engine.model;

import java.util.List;

/**
 * 구독 조합 하나를 평가한 결과. 비교기(EvaluationComparator)는 이 값만 보고 두 조합의 우열을 가린다.
 */
public record Evaluation(
		Selection selection,    // 평가한 구독 조합. 비교기 ④ 결제 미루기·⑤ 상품 ID 순(D6)이 쓴다
		int mustCompleted,      // 시청 완료한 MUST 수(목적함수 ①)
		int score,              // 시청 완료한 WANT 2점·MAYBE 1점의 합(목적함수 ②, MUST는 넣지 않음, D5)
		int totalCost,          // 3개월 비용 합(원, 목적함수 ③). FREE는 0, SUBSCRIBED는 0번째 달 0
		List<UnitResult> units  // 시청 단위별 결과. PlanInput.units()와 같은 순서(배정 순서)
) {

	/** 값을 검증하고 단위 결과 목록을 불변으로 복사한다. */
	public Evaluation {
		if (selection == null || units == null) {
			throw new IllegalArgumentException("평가 결과의 구독 조합과 단위 결과는 null일 수 없다");
		}
		units = List.copyOf(units);
	}
}
