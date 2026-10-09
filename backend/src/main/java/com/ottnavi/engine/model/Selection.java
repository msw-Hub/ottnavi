package com.ottnavi.engine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 평가할 구독 조합 하나: 달마다 선택한 상품 ID 집합 3개(0=이번 달, 1·2=다음 두 달).
 * 각 달의 집합은 상품 ID 오름차순으로 순회되는 불변 집합으로 바꿔 보관한다.
 * 비교기 ⑤(달별 상품 ID 사전순)가 비교할 때마다 정렬하지 않도록 생성 때 한 번 정렬해 두는 것이다.
 * 선택 상품 ID가 입력 상품 목록에 있는지는 Selection이 상품 목록을 모르므로 평가기가 상품 위치로 바꿀 때 확인한다.
 */
public record Selection(
		List<Set<Long>> productIdsByMonth // 달별 선택 상품 ID. 길이 3, 인덱스 0=이번 달(확정), 1·2=다음 두 달(예상)
) {

	public static final int MONTH_COUNT = 3; // 플랜은 항상 3개월(PRD 5.4 롤링)

	/** 3개월치인지 검증하고 달별 상품 ID를 오름차순 불변 집합으로 보관한다. */
	public Selection {
		if (productIdsByMonth == null || productIdsByMonth.size() != MONTH_COUNT) {
			throw new IllegalArgumentException("구독 조합은 3개월치(길이 3)여야 한다");
		}
		List<Set<Long>> sortedByMonth = new ArrayList<>(MONTH_COUNT);
		for (int monthIndex = 0; monthIndex < MONTH_COUNT; monthIndex++) {
			Set<Long> productIds = productIdsByMonth.get(monthIndex);
			if (productIds == null) {
				throw new IllegalArgumentException("구독 조합의 달별 상품 집합은 null일 수 없다: monthIndex=" + monthIndex);
			}
			// Set이라 같은 달 안에서 상품 ID는 중복될 수 없다. null만 따로 막는다
			TreeSet<Long> sorted = new TreeSet<>();
			for (Long productId : productIds) {
				if (productId == null) {
					throw new IllegalArgumentException("구독 조합에 null 상품 ID가 있다: monthIndex=" + monthIndex);
				}
				sorted.add(productId);
			}
			sortedByMonth.add(Collections.unmodifiableSortedSet(sorted));
		}
		productIdsByMonth = List.copyOf(sortedByMonth);
	}

	/** 달별 선택 상품 ID 집합 3개로 구독 조합을 만든다. */
	public static Selection of(Set<Long> month0, Set<Long> month1, Set<Long> month2) {
		return new Selection(List.of(month0, month1, month2));
	}

	/** 해당 달에 선택한 상품 ID를 오름차순으로 순회되는 불변 집합으로 돌려준다. */
	public Set<Long> productIdsOf(int monthIndex) {
		return productIdsByMonth.get(monthIndex);
	}
}
