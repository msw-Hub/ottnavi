package com.ottnavi.plan.domain.entity;

import com.ottnavi.product.domain.enums.PriceSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** 그 달에 구독할 상품과 적용 가격 스냅샷(ERD plan_month_product). 상품은 다른 도메인이라 ID만 보관한다 */
@Getter
@Entity
@Table(name = "plan_month_product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanMonthProduct {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "plan_month_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE) // 달 삭제 시 DB가 함께 지운다
	private PlanMonth planMonth;

	@Column(nullable = false)
	private Long productId; // 구독상품 ID

	@Column(nullable = false)
	private int appliedPrice; // 계산에 적용한 월 요금(원)

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PriceSource priceSource; // ADMIN / USER_OVERRIDE / ALREADY_PAID / FREE

	/** 달별 상품 한 건을 만든다. 가격은 0 이상이다(DB CHECK와 같은 규칙) */
	public static PlanMonthProduct create(PlanMonth planMonth, Long productId, int appliedPrice,
			PriceSource priceSource) {
		if (appliedPrice < 0) {
			throw new IllegalArgumentException("적용 가격은 0 이상이어야 한다: " + appliedPrice);
		}
		PlanMonthProduct product = new PlanMonthProduct();
		product.planMonth = planMonth;
		product.productId = productId;
		product.appliedPrice = appliedPrice;
		product.priceSource = priceSource;
		return product;
	}
}
