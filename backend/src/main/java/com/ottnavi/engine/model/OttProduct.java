package com.ottnavi.engine.model;

/**
 * 구독 선택지 하나(MVP는 STANDARD 단품, 2단계 번들도 같은 모양).
 * 엔진은 상품이 어느 서비스인지 몰라도 된다. 어떤 시청 단위를 볼 수 있는지는 {@link WatchUnit#watchableProductIds()}가 들고 있다.
 */
public record OttProduct(
		long id,                        // 상품 ID(product.id)
		int monthlyPrice,               // 한 달 가격(원), 0 이상
		OttProductCondition condition   // 사용자의 현재 구독 상태: 없음 / 구독 중 / 무료 이용
) {

	/** 가격·구독 상태를 검증한다. */
	public OttProduct {
		if (monthlyPrice < 0) {
			throw new IllegalArgumentException("상품 가격은 0 이상이어야 한다: productId=" + id + ", monthlyPrice=" + monthlyPrice);
		}
		if (condition == null) {
			throw new IllegalArgumentException("상품 구독 상태는 비어 있을 수 없다: productId=" + id);
		}
	}
}
