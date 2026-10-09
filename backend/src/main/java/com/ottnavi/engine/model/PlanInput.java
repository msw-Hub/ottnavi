package com.ottnavi.engine.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 계산 한 번의 입력: 찜한 시청 단위, 후보 상품, 월 예산, 월 시청 가능 시간.
 * 생성될 때 검증과 정렬을 한 번만 한다. 평가기는 이 입력을 수백만 번 다시 쓰므로 평가 중에는 검증·정렬을 하지 않는다(D8).
 */
public record PlanInput(
		List<WatchUnit> units,     // 시청 단위. 생성 시 배정 순서(WatchUnit.ASSIGNMENT_ORDER)로 정렬된 불변 목록이 된다
		List<OttProduct> products, // 후보 상품. 입력 순서 그대로의 불변 목록
		int monthlyBudget,         // 월 예산(원). 예산 검사는 Solver(039)가 하고 평가기는 쓰지 않는다(D7)
		int monthlyWatchMinutes    // 월 시청 가능 시간(분). 서비스와 무관하게 그 달 전체에서 센다
) {

	/** 입력을 검증하고 시청 단위를 배정 순서로 정렬해 불변으로 보관한다. */
	public PlanInput {
		if (units == null || products == null) {
			throw new IllegalArgumentException("시청 단위 목록과 상품 목록은 null일 수 없다");
		}
		if (monthlyBudget < 0) {
			throw new IllegalArgumentException("월 예산은 0 이상이어야 한다: monthlyBudget=" + monthlyBudget);
		}
		if (monthlyWatchMinutes <= 0) {
			throw new IllegalArgumentException("월 시청 가능 시간은 1분 이상이어야 한다: monthlyWatchMinutes=" + monthlyWatchMinutes);
		}

		Set<Long> productIds = new HashSet<>();
		for (OttProduct product : products) {
			if (product == null) {
				throw new IllegalArgumentException("상품 목록에 null이 있다");
			}
			if (!productIds.add(product.id())) {
				throw new IllegalArgumentException("상품 ID가 중복됐다: productId=" + product.id());
			}
		}

		Set<Long> watchUnitIds = new HashSet<>();
		for (WatchUnit unit : units) {
			if (unit == null) {
				throw new IllegalArgumentException("시청 단위 목록에 null이 있다");
			}
			// watchUnitId는 정렬의 마지막 기준이라 중복되면 결과가 입력 순서에 따라 달라질 수 있다
			if (!watchUnitIds.add(unit.id())) {
				throw new IllegalArgumentException("시청 단위 ID가 중복됐다: watchUnitId=" + unit.id());
			}
			// plan 변환이 입력 필터(예: 쿠팡플레이 제외, R11-13)로 상품을 빼면 시청 가능 상품 집합에서도 함께 걸러야 한다.
			// 걸러지지 않은 ID가 남으면 그 상품의 가격·구독 상태를 알 수 없어 여기서 막는다
			for (Long productId : unit.watchableProductIds()) {
				if (!productIds.contains(productId)) {
					throw new IllegalArgumentException("시청 가능 상품 ID가 상품 목록에 없다: watchUnitId=" + unit.id() + ", productId=" + productId);
				}
			}
		}

		// PRD 5.4 배정 규칙 1: 정렬은 여기서 한 번만 한다(D8). 입력 순서와 무관하게 같은 결과가 나오게 하는 전제다.
		// 평가기는 시청 가능 상품이 없는 단위가 맨 뒤에 모여 있다는 이 정렬 결과(ASSIGNMENT_ORDER ①)에 기대어 그 단위들을 건너뛴다
		List<WatchUnit> sortedUnits = new ArrayList<>(units);
		sortedUnits.sort(WatchUnit.ASSIGNMENT_ORDER);
		units = List.copyOf(sortedUnits);
		products = List.copyOf(products);
	}
}
