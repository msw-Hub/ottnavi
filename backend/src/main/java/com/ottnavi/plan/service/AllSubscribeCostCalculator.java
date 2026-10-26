package com.ottnavi.plan.service;

import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.WatchUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 전체 구독 비용(PRD 5.4, 11절 38번) 계산. 찜한 안 다 본 단위의 시청 가능 상품 전체를 3개월 내내 구독했을 때의 비용이다.
 * 번들은 제외한다(Task 076): 서비스마다 후보로 고른 단품 1개 × 3개월만 더한다. 단품은 서비스마다 사용자 모드(표준/가장 저렴)에 맞게 이미 1개로 줄어 들어온다.
 * 엔진 밖(plan 서비스)에서 세 플랜 유형이 같은 값을 쓰도록 한 번만 계산한다. 프레임워크에 의존하지 않는 순수 계산이다.
 */
public final class AllSubscribeCostCalculator {

	private static final int MONTH_COUNT = 3; // 3개월 롤링 플랜

	private AllSubscribeCostCalculator() {
	}

	/**
	 * 시청 가능 단품 합집합을 3개월 내내 구독한 비용(원)을 구한다(번들 제외).
	 * 예: NONE 10,000원 + SUBSCRIBED 8,000원 + FREE 5,000원 → 30,000 + 16,000 + 0 = 46,000원.
	 *
	 * @param units    시청 단위(입력 단계에서 제외된 상품은 시청 가능 상품 ID에서도 이미 빠져 있어야 한다)
	 * @param products 후보 상품(가격·구독 상태)
	 */
	public static int calculate(List<WatchUnit> units, List<OttProduct> products) {
		// 1. 상품 ID로 가격·구독 상태를 찾을 수 있게 한다
		Map<Long, OttProduct> productsById = new HashMap<>();
		for (OttProduct product : products) {
			productsById.put(product.id(), product);
		}

		// 2. 모든 시청 단위의 시청 가능 상품 ID 합집합을 만든다
		Set<Long> union = new HashSet<>();
		for (WatchUnit unit : units) {
			union.addAll(unit.watchableProductIds());
		}

		// 3. 단품마다 3개월치 비용을 더한다(FREE는 0, SUBSCRIBED는 첫 달만 0, 번들은 더하지 않는다)
		long total = 0;
		for (Long productId : union) {
			OttProduct product = productsById.get(productId);
			if (product == null) {
				throw new IllegalArgumentException("시청 가능 상품 ID가 상품 목록에 없다: productId=" + productId);
			}
			if (product.isBundle()) {
				continue; // Task 076: 번들은 절감액 기준(전체 구독 비용)에 넣지 않는다
			}
			if (product.condition() == OttProductCondition.FREE) {
				continue; // PRD 5.4 구독 상태 반영: 무료 이용은 모든 달 0원
			}
			if (product.condition() == OttProductCondition.SUBSCRIBED) {
				total += (long) product.monthlyPrice() * (MONTH_COUNT - 1); // 0번째 달은 이미 결제해 0원, 1·2번째 달만 가격
				continue;
			}
			total += (long) product.monthlyPrice() * MONTH_COUNT;
		}
		return Math.toIntExact(total);
	}
}
