package com.ottnavi.engine.rule;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import java.util.Comparator;
import java.util.List;

/**
 * 절약형 목적함수 비교기(PRD 5.4 절약형, 11절 35번, Task 039 안내서 3절).
 *
 * <p>반환 규칙은 EvaluationComparator와 같다: 양수 = 첫 번째가 더 좋다.
 * 사전식 순서: ① mustCompleted 큰 쪽 → ② 점수 하한(minScore) 이상인 쪽 → ③ totalCost 작은 쪽 → ④ score 큰 쪽
 * → ⑤ 결제 미루기 → ⑥ 달별 상품 ID 사전순.
 * ⑤·⑥은 추천형 비교기(EvaluationComparator)에 맡긴다. ①~④가 같으면 must·score·cost가 모두 같으므로
 * 추천형 비교기의 ①②③은 0이 되고 결과가 정확히 그 ④ 결제 미루기 → ⑤ ID 순이 된다(규칙을 한 곳에만 둔다).
 */
final class SaverComparator implements Comparator<Evaluation> {

	private final int minScore;                       // 점수 하한: 추천형 점수의 70% 올림
	private final EvaluationComparator recommended;   // 마지막 동점(결제 미루기·ID 순)을 가르는 추천형 비교기

	/** 상품 목록과 점수 하한으로 비교기를 만든다. */
	SaverComparator(List<OttProduct> products, int minScore) {
		this.minScore = minScore;
		this.recommended = new EvaluationComparator(products);
	}

	/** 양수면 first가, 음수면 second가 더 좋은 결과다. */
	@Override
	public int compare(Evaluation first, Evaluation second) {
		// PRD 5.4 절약형 ①: "꼭" 작품 완주 수가 큰 쪽
		int mustOrder = Integer.compare(first.mustCompleted(), second.mustCompleted());
		if (mustOrder != 0) {
			return mustOrder;
		}
		// ② 점수 하한을 넘었는지만 본다(둘 다 넘었거나 둘 다 못 넘었으면 같다)
		int floorOrder = Boolean.compare(first.score() >= minScore, second.score() >= minScore);
		if (floorOrder != 0) {
			return floorOrder;
		}
		// ③ 총비용이 작은 쪽(작을수록 좋으므로 인자 순서를 뒤집는다)
		int costOrder = Integer.compare(second.totalCost(), first.totalCost());
		if (costOrder != 0) {
			return costOrder;
		}
		// ④ 점수가 큰 쪽
		int scoreOrder = Integer.compare(first.score(), second.score());
		if (scoreOrder != 0) {
			return scoreOrder;
		}
		// ⑤ 결제 미루기 → ⑥ 달별 상품 ID 사전순: 추천형 비교기의 ④·⑤와 같은 정의라 위임한다(클래스 설명 참고)
		return recommended.compare(first, second);
	}
}
