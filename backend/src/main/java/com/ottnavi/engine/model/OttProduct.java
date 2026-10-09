package com.ottnavi.engine.model;

/**
 * 구독 선택지 하나(MVP는 STANDARD 단품, 2단계 번들도 같은 모양).
 * 엔진은 상품이 어느 서비스인지 몰라도 된다. 어떤 시청 단위를 볼 수 있는지는 {@link WatchUnit#watchableProductIds()}가 들고 있다.
 */
public record OttProduct(
		long id,                        // 상품 ID(product.id)
		int monthlyPrice,               // 한 달 가격(원), 0 이상 MAX_MONTHLY_PRICE 이하
		OttProductCondition condition   // 사용자의 현재 구독 상태: 없음 / 구독 중 / 무료 이용
) {

	/**
	 * 월 가격 상한(원). 엔진은 비용을 int로 합산한다.
	 * 상품 최대 32개(비트마스크 한계) × 3개월 × 100만 원 = 약 9,600만 원으로 int 범위(약 21억) 안이라 오버플로가 생길 수 없다.
	 * 월 구독료로 100만 원을 넘는 상품은 없다(사용자 결정 2026-10-09, CodeRabbit PR #25 지적 반영).
	 */
	public static final int MAX_MONTHLY_PRICE = 1_000_000; // 월 가격 상한 100만 원. 비용 합산(int) 오버플로 방지용

	/** 가격(0 이상 100만 원 이하)·구독 상태를 검증한다. */
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
	}
}
