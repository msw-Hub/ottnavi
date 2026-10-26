package com.ottnavi.product.domain.rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 플랜 계산에 후보로 쓸 상품을 고르는 규칙(Task 076). DB를 읽지 않고 넘겨받은 상품 목록만 보고 판단한다.
 *
 * <p>서비스마다 단품은 정확히 1개만 후보가 되고, 번들은 모드에 따라 걸러진 것 전부가 후보다.
 * 저가형·광고형·베이직은 볼 수 있는 작품에 제한이 없다고 보므로 분류(isLowCost)는 가격 선택에만 쓴다.
 * <ul>
 *   <li>모드 끔: 서비스마다 표준 단품 중 가장 싼 것. 표준이 없으면 저가형 중 가장 싼 것(서비스가 후보에서 사라지는 것을 막는다). 번들은 표준 번들만</li>
 *   <li>모드 켬: 서비스마다 모든 단품 중 적용 금액이 가장 싼 것(분류와 무관). 번들은 전부</li>
 *   <li>금액이 같으면 상품 ID가 작은 것</li>
 * </ul>
 *
 * <p>예(모드 끔): 넷플릭스 스탠다드 13,500(표준), 광고형 7,000(저가형) → 스탠다드. 모드 켬 → 광고형.
 * 왓챠 광고형 5,000(저가형)만 있으면 모드 끔에서도 광고형이 후보다.
 */
public final class CalcCandidateRules {

	private CalcCandidateRules() {
	}

	/**
	 * 후보 선택에 필요한 상품 정보.
	 *
	 * @param productId     상품 ID
	 * @param isBundle      번들이면 true
	 * @param isLowCost     저가형이면 true(표준이면 false)
	 * @param ottServiceId  단품이 속한 서비스 ID. 번들은 null
	 * @param appliedPrice  적용 금액(사용자가 고친 금액이 있으면 그 값)
	 */
	public record Candidate(long productId, boolean isBundle, boolean isLowCost, Long ottServiceId, int appliedPrice) {
	}

	/** 후보로 쓸 상품 ID 집합을 고른다. 입력 순서와 무관하다 */
	public static Set<Long> select(List<Candidate> products, boolean lowestPriceMode) {
		Set<Long> selected = new HashSet<>();

		// 1. 번들은 모드에 따라 거른다(끔: 표준 번들만, 켬: 전부). 서비스마다 하나로 줄이지 않는다
		// 2. 단품은 서비스별로 모은다(서비스 ID 순)
		Map<Long, List<Candidate>> singlesByService = new TreeMap<>();
		for (Candidate product : products) {
			if (product.isBundle()) {
				if (lowestPriceMode || !product.isLowCost()) {
					selected.add(product.productId());
				}
				continue;
			}
			List<Candidate> group = singlesByService.get(product.ottServiceId());
			if (group == null) {
				group = new ArrayList<>();
				singlesByService.put(product.ottServiceId(), group);
			}
			group.add(product);
		}

		// 3. 서비스마다 단품 1개를 고른다
		for (List<Candidate> group : singlesByService.values()) {
			Candidate picked = pickSingle(group, lowestPriceMode);
			selected.add(picked.productId());
		}
		return selected;
	}

	/** 한 서비스의 단품 중 후보 1개를 고른다. group은 비어 있지 않다 */
	private static Candidate pickSingle(List<Candidate> group, boolean lowestPriceMode) {
		// 1. 모드 켬: 분류와 무관하게 가장 싼 것
		if (lowestPriceMode) {
			return cheapest(group, null);
		}

		// 2. 모드 끔: 표준 중 가장 싼 것, 표준이 없으면 저가형 중 가장 싼 것
		Candidate standard = cheapest(group, false);
		if (standard != null) {
			return standard;
		}
		return cheapest(group, true);
	}

	/**
	 * 조건에 맞는 단품 중 가장 싼 것을 돌려준다. 금액이 같으면 상품 ID가 작은 것이다.
	 *
	 * @param lowCostFilter true면 저가형만, false면 표준만, null이면 전부 대상
	 * @return 조건에 맞는 상품이 없으면 null
	 */
	private static Candidate cheapest(List<Candidate> group, Boolean lowCostFilter) {
		Candidate best = null;
		for (Candidate product : group) {
			if (lowCostFilter != null && product.isLowCost() != lowCostFilter) {
				continue;
			}
			if (best == null || product.appliedPrice() < best.appliedPrice()
					|| (product.appliedPrice() == best.appliedPrice() && product.productId() < best.productId())) {
				best = product;
			}
		}
		return best;
	}
}
