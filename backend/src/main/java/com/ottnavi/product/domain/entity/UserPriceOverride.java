package com.ottnavi.product.domain.entity;

import com.ottnavi.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 상품의 월 요금을 직접 고친 금액(ERD user_price_override, FR-11). 관리자 요금표는 바꾸지 않는다.
 * 사용자는 다른 도메인의 엔티티라 연관관계 대신 ID만 들고, 상품도 같은 방식으로 ID만 보관한다.
 */
@Getter
@Entity
@Table(name = "user_price_override")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPriceOverride extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId; // 금액을 고친 사용자

	@Column(nullable = false)
	private Long productId; // 구독상품 ID. 사용자 금액은 상품 단위로 저장된다

	@Column(nullable = false)
	private int monthlyPrice; // 사용자가 정한 월 요금(원)

	/** 사용자가 고친 금액 한 건을 만든다. 요금은 0 이상이다(DB CHECK와 같은 규칙) */
	public static UserPriceOverride create(Long userId, Long productId, int monthlyPrice) {
		if (monthlyPrice < 0) {
			throw new IllegalArgumentException("월 요금은 0 이상이어야 한다: " + monthlyPrice);
		}
		UserPriceOverride override = new UserPriceOverride();
		override.userId = userId;
		override.productId = productId;
		override.monthlyPrice = monthlyPrice;
		return override;
	}

	/** 금액만 바꾼다 */
	public void changePrice(int monthlyPrice) {
		if (monthlyPrice < 0) {
			throw new IllegalArgumentException("월 요금은 0 이상이어야 한다: " + monthlyPrice);
		}
		this.monthlyPrice = monthlyPrice;
	}
}
