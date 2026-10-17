package com.ottnavi.engine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 구독 선택지 하나(단품 또는 번들, Task 076).
 * 어떤 시청 단위를 볼 수 있는지는 {@link WatchUnit#watchableProductIds()}가 들고 있고,
 * 엔진은 구성 서비스(ottServiceIds)를 "서비스가 겹치는 상품은 한 달에 1개만"(PRD 11절 33번) 규칙에만 쓴다.
 */
public record OttProduct(
		long id,                        // 상품 ID(product.id)
		int monthlyPrice,               // 한 달 가격(원), 0 이상 MAX_MONTHLY_PRICE 이하
		OttProductCondition condition,  // 사용자의 현재 구독 상태: 없음 / 구독 중 / 무료 이용. 번들은 항상 없음
		List<Long> ottServiceIds        // 구성 서비스 ID 목록(오름차순 정렬됨, 단품은 1개·번들은 2개 이상). 서비스 겹침 판정과 입력 해시에 쓴다
) {

	/**
	 * 월 가격 상한(원). 엔진은 비용을 int로 합산한다.
	 * 상품 최대 32개(비트마스크 한계) × 3개월 × 100만 원 = 약 9,600만 원으로 int 범위(약 21억) 안이라 오버플로가 생길 수 없다.
	 * 월 구독료로 100만 원을 넘는 상품은 없다(사용자 결정 2026-10-09, CodeRabbit PR #25 지적 반영).
	 */
	public static final int MAX_MONTHLY_PRICE = 1_000_000; // 월 가격 상한 100만 원. 비용 합산(int) 오버플로 방지용

	/**
	 * 서비스 정보 없이 상품을 만든다(테스트·벤치마크 호환용). 구성 서비스가 비어 있어 서비스 겹침 검사 대상이 아니다.
	 * 운영 입력(PlanInputService)은 항상 구성 서비스를 채운 생성자를 쓴다.
	 */
	public OttProduct(long id, int monthlyPrice, OttProductCondition condition) {
		this(id, monthlyPrice, condition, List.of());
	}

	/** 가격(0 이상 100만 원 이하)·구독 상태를 검증하고 구성 서비스를 ID 오름차순 불변 목록으로 보관한다. */
	public OttProduct {
		if (monthlyPrice < 0) {
			throw new IllegalArgumentException("상품 가격은 0 이상이어야 한다: productId=" + id + ", monthlyPrice=" + monthlyPrice);
		}
		// 상한이 있어야 Evaluator의 int 비용 합산이 넘치지 않는다(MAX_MONTHLY_PRICE 설명 참고)
		if (monthlyPrice > MAX_MONTHLY_PRICE) {
			throw new IllegalArgumentException("월 가격은 100만 원 이하여야 한다: productId=" + id + ", monthlyPrice=" + monthlyPrice);
		}
		if (condition == null) {
			throw new IllegalArgumentException("상품 구독 상태는 비어 있을 수 없다: productId=" + id);
		}
		if (ottServiceIds == null) {
			throw new IllegalArgumentException("구성 서비스 목록은 null일 수 없다: productId=" + id);
		}
		// 입력 순서와 무관하게 같은 값이 되도록 정렬해 둔다(입력 해시 안정성)
		List<Long> sortedServiceIds = new ArrayList<>(ottServiceIds);
		Collections.sort(sortedServiceIds);
		ottServiceIds = List.copyOf(sortedServiceIds);
	}

	/** 구성 서비스가 둘 이상이면 번들이다. */
	public boolean isBundle() {
		return ottServiceIds.size() > 1;
	}
}
