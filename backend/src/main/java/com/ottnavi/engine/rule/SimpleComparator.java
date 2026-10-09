package com.ottnavi.engine.rule;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Selection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 간편형 목적함수 비교기(PRD 5.4 간편형, 11절 35번, Task 039 안내서 3절).
 *
 * <p>반환 규칙은 EvaluationComparator와 같다: 양수 = 첫 번째가 더 좋다.
 * 사전식 순서: ① mustCompleted 큰 쪽 → ② 점수 하한(minScore) 이상인 쪽 → ③ 가입 횟수 적은 쪽 → ④ totalCost 작은 쪽
 * → ⑤ score 큰 쪽 → ⑥ 조기 시청 → ⑦ 결제 미루기 → ⑧ 달별 상품 ID 사전순.
 * ⑥~⑧은 추천형 비교기(EvaluationComparator)에 맡긴다. ①~⑤가 같으면 must·score·cost가 모두 같으므로
 * 추천형 비교기의 ①②③은 0이 되고 결과가 정확히 그 ④ 조기 시청 → ⑤ 결제 미루기 → ⑥ ID 순이 된다.
 */
final class SimpleComparator implements Comparator<Evaluation> {

	private final int minScore;                       // 점수 하한: 추천형 점수의 70% 올림
	private final EvaluationComparator recommended;   // 마지막 동점(조기 시청·결제 미루기·ID 순)을 가르는 추천형 비교기
	private final long[] productIds;                  // 상품 ID 오름차순(구독 상태를 이진 탐색으로 찾는 용도)
	private final OttProductCondition[] conditions;   // productIds와 같은 위치의 구독 상태
	private final Set<Long> subscribedProductIds;     // 이미 구독 중(SUBSCRIBED)인 상품 ID. 0번째 달에는 선택하지 않아도 구독 중이다

	/** 입력과 점수 하한으로 비교기를 만든다. */
	SimpleComparator(PlanInput input, int minScore) {
		this.minScore = minScore;
		this.recommended = new EvaluationComparator(input); // input이 null이면 여기서 IllegalArgumentException
		List<OttProduct> sorted = new ArrayList<>(input.products());
		sorted.sort(Comparator.comparingLong(OttProduct::id));
		this.productIds = new long[sorted.size()];
		this.conditions = new OttProductCondition[sorted.size()];
		List<Long> subscribed = new ArrayList<>();
		for (int i = 0; i < sorted.size(); i++) {
			productIds[i] = sorted.get(i).id();
			conditions[i] = sorted.get(i).condition();
			if (conditions[i] == OttProductCondition.SUBSCRIBED) {
				subscribed.add(productIds[i]);
			}
		}
		this.subscribedProductIds = Set.copyOf(subscribed);
	}

	/** 양수면 first가, 음수면 second가 더 좋은 결과다. */
	@Override
	public int compare(Evaluation first, Evaluation second) {
		// PRD 5.4 간편형 ①: "꼭" 작품 시청 완료 수가 큰 쪽
		int mustOrder = Integer.compare(first.mustCompleted(), second.mustCompleted());
		if (mustOrder != 0) {
			return mustOrder;
		}
		// ② 점수 하한을 넘었는지만 본다(둘 다 넘었거나 둘 다 못 넘었으면 같다)
		int floorOrder = Boolean.compare(first.score() >= minScore, second.score() >= minScore);
		if (floorOrder != 0) {
			return floorOrder;
		}
		// ③ 새로 가입하는 횟수가 적은 쪽(적을수록 좋으므로 인자 순서를 뒤집는다)
		int signUpOrder = Integer.compare(signUpCount(second.selection()), signUpCount(first.selection()));
		if (signUpOrder != 0) {
			return signUpOrder;
		}
		// ④ 총비용이 작은 쪽
		int costOrder = Integer.compare(second.totalCost(), first.totalCost());
		if (costOrder != 0) {
			return costOrder;
		}
		// ⑤ 점수가 큰 쪽
		int scoreOrder = Integer.compare(first.score(), second.score());
		if (scoreOrder != 0) {
			return scoreOrder;
		}
		// ⑥ 조기 시청 → ⑦ 결제 미루기 → ⑧ 달별 상품 ID 사전순: 추천형 비교기의 ④·⑤·⑥과 같은 정의라 위임한다(클래스 설명 참고)
		return recommended.compare(first, second);
	}

	/**
	 * 3개월 동안 새로 가입하는 횟수를 센다(간편형 ③, 2026-10-09 사용자 결정).
	 * m번째 달에 선택했고 바로 앞 달에는 구독 중이 아니던 상품마다 1회다. 연속으로 유지하면 다시 세지 않고, 빠졌다가 다시 선택하면 다시 센다.
	 * 0번째 달 앞(계산 시작 전)에 구독 중인 상품은 SUBSCRIBED 상품이고, 0번째 달에 구독 중인 상품은 "0번째 달 선택 + SUBSCRIBED"다.
	 * FREE는 가입이 아니므로 세지 않는다(Solver 후보에서도 빠진다, 안내서 S6).
	 * 예: 넷플릭스 0·1·2번째 달 연속 = 1회, 넷플릭스 0 → 티빙 1 → 웨이브 2 = 3회, 이미 구독 중인 상품을 1번째 달에 선택 = 0회.
	 */
	int signUpCount(Selection selection) {
		int count = 0;
		for (int month = 0; month < Selection.MONTH_COUNT; month++) {
			Set<Long> selected = selection.productIdsOf(month);
			for (long productId : selected) {
				if (conditionOf(productId) == OttProductCondition.FREE) {
					continue;
				}
				if (!wasActiveBefore(selection, month, productId)) {
					count++;
				}
			}
		}
		return count;
	}

	/** month번째 달 바로 앞 달에 그 상품을 구독 중이었는지 확인한다. 0번째 달의 앞 달은 SUBSCRIBED 상품만 구독 중이다. */
	private boolean wasActiveBefore(Selection selection, int month, long productId) {
		if (month == 0) {
			return subscribedProductIds.contains(productId);
		}
		int previous = month - 1;
		// 0번째 달은 선택하지 않아도 SUBSCRIBED 상품을 구독 중인 것으로 본다(D2와 같은 기준)
		return selection.productIdsOf(previous).contains(productId)
				|| (previous == 0 && subscribedProductIds.contains(productId));
	}

	/** 상품 ID의 구독 상태를 찾는다. 상품 목록에 없으면 IllegalArgumentException. */
	private OttProductCondition conditionOf(long productId) {
		int index = Arrays.binarySearch(productIds, productId);
		if (index < 0) {
			throw new IllegalArgumentException("비교할 조합의 상품 ID가 상품 목록에 없다: productId=" + productId);
		}
		return conditions[index];
	}
}
