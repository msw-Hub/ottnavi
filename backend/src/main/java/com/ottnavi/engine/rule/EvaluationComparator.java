package com.ottnavi.engine.rule;

import com.ottnavi.engine.model.Evaluation;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.Selection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 두 평가 결과 중 어느 쪽이 더 좋은지 가르는 목적함수 비교기(PRD 5.4 목적함수, TECH 1절, D6).
 *
 * <p><b>반환 규칙: 양수 = 첫 번째가 더 좋다, 음수 = 두 번째가 더 좋다, 0 = 완전히 같다.</b>
 * 그래서 가장 좋은 결과는 {@code Collections.max(evaluations, comparator)}로 고른다(정렬하면 가장 좋은 것이 맨 뒤).
 *
 * <p>사전식 순서: ① mustCompleted 큰 쪽 → ② score 큰 쪽 → ③ totalCost 작은 쪽 → ④ 결제를 미루는 쪽 → ⑤ 달별 상품 ID 사전순으로 작은 쪽.
 * ④·⑤는 Solver가 수많은 후보를 비교할 때 자주 불리므로 스트림·정렬 없이 계산한다(Selection이 생성 때 ID를 정렬해 둔다).
 */
public final class EvaluationComparator implements Comparator<Evaluation> {

	private final long[] productIds;                 // 상품 ID 오름차순(구독 상태를 이진 탐색으로 찾는 용도)
	private final OttProductCondition[] conditions;  // productIds와 같은 위치의 구독 상태

	/** 평가에 쓴 상품 목록으로 비교기를 만든다(④에서 상품의 구독 상태를 본다). */
	public EvaluationComparator(List<OttProduct> products) {
		if (products == null) {
			throw new IllegalArgumentException("상품 목록은 null일 수 없다");
		}
		List<OttProduct> sorted = new ArrayList<>(products);
		sorted.sort(Comparator.comparingLong(OttProduct::id));
		this.productIds = new long[sorted.size()];
		this.conditions = new OttProductCondition[sorted.size()];
		for (int i = 0; i < sorted.size(); i++) {
			productIds[i] = sorted.get(i).id();
			conditions[i] = sorted.get(i).condition();
		}
	}

	/** 양수면 first가, 음수면 second가 더 좋은 결과다. */
	@Override
	public int compare(Evaluation first, Evaluation second) {
		// PRD 5.4 목적함수 ①: "꼭" 작품 완주 수가 큰 쪽
		int mustOrder = Integer.compare(first.mustCompleted(), second.mustCompleted());
		if (mustOrder != 0) {
			return mustOrder;
		}
		// PRD 5.4 목적함수 ②: 점수(WANT 2, MAYBE 1) 합이 큰 쪽
		int scoreOrder = Integer.compare(first.score(), second.score());
		if (scoreOrder != 0) {
			return scoreOrder;
		}
		// PRD 5.4 목적함수 ③: 총비용이 작은 쪽(작을수록 좋으므로 인자 순서를 뒤집는다)
		int costOrder = Integer.compare(second.totalCost(), first.totalCost());
		if (costOrder != 0) {
			return costOrder;
		}
		// PRD 5.4 목적함수 ④ 결제 미루기(D6): 달 0→1→2 순서로 새로 돈이 드는 상품 수가 먼저 다른 달에서 적은 쪽
		for (int month = 0; month < Selection.MONTH_COUNT; month++) {
			int paymentOrder = Integer.compare(
					newPaymentCount(second.selection(), month),
					newPaymentCount(first.selection(), month));
			if (paymentOrder != 0) {
				return paymentOrder;
			}
		}
		// ⑤ 결과를 항상 같게 하려는 마지막 기준(도출, D6): 달별로 정렬된 상품 ID 목록을 차례로 비교해 작은 쪽
		for (int month = 0; month < Selection.MONTH_COUNT; month++) {
			int idOrder = compareAscendingIds(first.selection().productIdsOf(month), second.selection().productIdsOf(month));
			if (idOrder != 0) {
				return -idOrder; // 작은 쪽이 좋으므로 부호를 뒤집는다
			}
		}
		return 0;
	}

	/**
	 * 그 달에 새로 돈이 드는 상품 수를 센다(④).
	 * FREE는 돈이 들지 않고, 0번째 달의 SUBSCRIBED는 이미 낸 돈이라 세지 않는다. 1·2번째 달의 SUBSCRIBED 유지는 센다.
	 */
	private int newPaymentCount(Selection selection, int month) {
		int count = 0;
		for (long productId : selection.productIdsOf(month)) {
			OttProductCondition condition = conditionOf(productId);
			if (condition == OttProductCondition.FREE) {
				continue;
			}
			if (month == 0 && condition == OttProductCondition.SUBSCRIBED) {
				continue;
			}
			count++;
		}
		return count;
	}

	/** 상품 ID의 구독 상태를 찾는다. 상품 목록에 없으면 IllegalArgumentException. */
	private OttProductCondition conditionOf(long productId) {
		int index = Arrays.binarySearch(productIds, productId);
		if (index < 0) {
			throw new IllegalArgumentException("비교할 조합의 상품 ID가 상품 목록에 없다: productId=" + productId);
		}
		return conditions[index];
	}

	/**
	 * 오름차순으로 순회되는 두 ID 집합을 목록처럼 사전순 비교한다. 앞이 작으면 음수.
	 * 앞부분이 같으면 짧은 쪽이 작다(예: [] &lt; [1], [1] &lt; [1, 2], [1, 3] &lt; [2]).
	 * Selection이 생성 때 정렬해 둔 집합이라 여기서는 정렬 없이 앞에서부터 한 번씩만 읽는다.
	 */
	private static int compareAscendingIds(Set<Long> left, Set<Long> right) {
		Iterator<Long> leftIds = left.iterator();
		Iterator<Long> rightIds = right.iterator();
		while (leftIds.hasNext() && rightIds.hasNext()) {
			int order = Long.compare(leftIds.next(), rightIds.next());
			if (order != 0) {
				return order;
			}
		}
		return Boolean.compare(leftIds.hasNext(), rightIds.hasNext());
	}
}
