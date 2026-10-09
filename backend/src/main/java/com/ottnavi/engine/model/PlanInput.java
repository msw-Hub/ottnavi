package com.ottnavi.engine.model;

import com.ottnavi.engine.rule.Evaluator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 계산 한 번의 입력: 찜한 시청 단위, 후보 상품, 월 예산, 월 시청 가능 시간.
 * 생성될 때 검증과 정렬을 한 번만 한다. 평가기는 이 입력을 수백만 번 다시 쓰므로 평가 중에는 검증·정렬을 하지 않는다(D8).
 */
public record PlanInput(
		List<WatchUnit> units,     // 시청 단위. 생성 시 배정 순서(WatchUnit.ASSIGNMENT_ORDER 정렬 + 같은 시리즈 앞 시즌 끌어올림)로 정렬된 불변 목록이 된다
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
		Set<String> seasonKeys = new HashSet<>(); // (titleId, seasonNumber) 중복 검사용
		for (WatchUnit unit : units) {
			if (unit == null) {
				throw new IllegalArgumentException("시청 단위 목록에 null이 있다");
			}
			// watchUnitId는 정렬의 마지막 기준이라 중복되면 결과가 입력 순서에 따라 달라질 수 있다
			if (!watchUnitIds.add(unit.id())) {
				throw new IllegalArgumentException("시청 단위 ID가 중복됐다: watchUnitId=" + unit.id());
			}
			// 같은 시리즈의 같은 시즌이 두 번 들어오면 시즌 순서를 정할 수 없다
			if (unit.titleId() != null && !seasonKeys.add(unit.titleId() + ":" + unit.seasonNumber())) {
				throw new IllegalArgumentException("같은 시즌이 중복됐다: titleId=" + unit.titleId() + ", seasonNumber=" + unit.seasonNumber());
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
		// PRD 5.4 시즌 순서: 같은 시리즈의 앞 시즌을 뒤 시즌 바로 앞으로 끌어올린다. 입력만으로 정해지므로 여기서 한 번만 한다
		units = List.copyOf(pullUpEarlierSeasons(sortedUnits, products, monthlyBudget, monthlyWatchMinutes));
		products = List.copyOf(products);
	}

	/**
	 * 끌어올림(PRD 5.4 시즌 순서): 기본 정렬된 목록을 앞에서부터 훑으며 단위를 내보낼 때, 같은 시리즈의 아직 안 나온 앞 시즌을
	 * 시즌 번호순으로 그 단위 바로 앞에 끼워 넣는다. 우선순위·필요 시간·id 값은 그대로이고 배정 순서만 바뀐다.
	 * 내보내려는 시즌이나 끌어올릴 앞 시즌 중 하나라도 정적으로 시청 완료 불가능하면 끌어올리지 않는다.
	 * 시청 가능 상품이 없는 단위는 항상 정적 불가능이라 끌어올려지지 않고 앞 시즌을 끌어오지도 않아, "시청 가능 단위 먼저" 불변식이 유지된다.
	 */
	private static List<WatchUnit> pullUpEarlierSeasons(List<WatchUnit> baseOrder, List<OttProduct> products,
			int monthlyBudget, int monthlyWatchMinutes) {
		// 시리즈별 시즌 번호순 목록(시즌 번호는 시리즈 안에서 유일함을 위에서 검증했다)
		Map<Long, TreeMap<Integer, WatchUnit>> seasonsByTitle = new HashMap<>();
		for (WatchUnit unit : baseOrder) {
			if (unit.titleId() != null) {
				seasonsByTitle.computeIfAbsent(unit.titleId(), key -> new TreeMap<>()).put(unit.seasonNumber(), unit);
			}
		}
		if (seasonsByTitle.isEmpty()) {
			return baseOrder; // 시즌이 없는 입력은 기존 정렬 그대로다
		}
		Map<Long, OttProduct> productsById = new HashMap<>();
		for (OttProduct product : products) {
			productsById.put(product.id(), product);
		}

		List<WatchUnit> ordered = new ArrayList<>(baseOrder.size());
		Set<Long> emitted = new HashSet<>(); // 이미 내보낸 watchUnitId
		for (WatchUnit unit : baseOrder) {
			if (emitted.contains(unit.id())) {
				continue; // 앞서 다른 시즌에 끌려가 이미 나왔다
			}
			if (unit.titleId() != null) {
				List<WatchUnit> earlier = new ArrayList<>();
				for (WatchUnit season : seasonsByTitle.get(unit.titleId()).headMap(unit.seasonNumber(), false).values()) {
					if (!emitted.contains(season.id())) {
						earlier.add(season);
					}
				}
				boolean pullable = !earlier.isEmpty() && !isStaticallyImpossible(unit, monthlyBudget, monthlyWatchMinutes, productsById);
				for (int i = 0; pullable && i < earlier.size(); i++) {
					pullable = !isStaticallyImpossible(earlier.get(i), monthlyBudget, monthlyWatchMinutes, productsById);
				}
				if (pullable) {
					for (WatchUnit season : earlier) {
						ordered.add(season);
						emitted.add(season.id());
					}
				}
			}
			ordered.add(unit);
			emitted.add(unit.id());
		}
		return ordered;
	}

	/**
	 * 어떤 구독 조합으로도 3개월 안에 시청 완료할 수 없는 시청 단위인지 입력만 보고 판단한다.
	 * ① FREE·SUBSCRIBED 상품이 시청 가능 상품에 없고 월 예산 이하 가격의 시청 가능 상품도 없다(시청 가능 상품이 아예 없는 경우 포함, 후보 상품 정의 S6과 같은 기준)
	 * ② 필요 시간이 월 시청 시간의 3배를 넘는다 ③ 긴 시즌(필요 시간 &gt; 월 시청 시간)인데 월 시청 시간이 최소 조각 120분 미만이다.
	 */
	private static boolean isStaticallyImpossible(WatchUnit unit, int monthlyBudget, int monthlyWatchMinutes, Map<Long, OttProduct> productsById) {
		boolean hasUsableProduct = false;
		for (long productId : unit.watchableProductIds()) {
			OttProduct product = productsById.get(productId);
			if (product.condition() != OttProductCondition.NONE || product.monthlyPrice() <= monthlyBudget) {
				hasUsableProduct = true;
				break;
			}
		}
		if (!hasUsableProduct) {
			return true; // ①
		}
		if ((long) unit.requiredMinutes() > 3L * monthlyWatchMinutes) {
			return true; // ②
		}
		return unit.requiredMinutes() > monthlyWatchMinutes && monthlyWatchMinutes < Evaluator.MIN_LONG_SEASON_SEGMENT_MINUTES; // ③
	}
}
