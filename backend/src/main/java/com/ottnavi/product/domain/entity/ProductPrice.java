package com.ottnavi.product.domain.entity;

import com.ottnavi.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 월 구독요금 이력(ERD product_price). 종료일은 없고 다음 요금 이력 시작일의 전날까지 적용된다 */
@Getter
@Entity
@Table(name = "product_price")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductPrice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private SubscriptionProduct product;

    @Column(nullable = false)
    private int monthlyPrice; // 원 단위 정수

    @Column(nullable = false)
    private LocalDate effectiveFrom; // 적용 시작일(한국 날짜)

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(nullable = false)
    private Instant lastVerifiedAt; // 관리자가 마지막으로 확인한 일시

    /** 요금 이력 한 건을 만든다. 요금은 0 이상이다(DB CHECK와 같은 규칙) */
    public static ProductPrice create(SubscriptionProduct product, int monthlyPrice, LocalDate effectiveFrom,
                                      Instant lastVerifiedAt) {
        if (monthlyPrice < 0) {
            throw new IllegalArgumentException("월 요금은 0 이상이어야 한다: " + monthlyPrice);
        }
        ProductPrice price = new ProductPrice();
        price.product = product;
        price.monthlyPrice = monthlyPrice;
        price.effectiveFrom = effectiveFrom;
        price.lastVerifiedAt = lastVerifiedAt;
        return price;
    }

    /** 금액과 적용 시작일을 고친다. 금액을 고친 것은 확인한 것으로 보아 마지막 확인 일시도 함께 바꾼다 */
    public void change(int monthlyPrice, LocalDate effectiveFrom, Instant verifiedAt) {
        if (monthlyPrice < 0) {
            throw new IllegalArgumentException("월 요금은 0 이상이어야 한다: " + monthlyPrice);
        }
        this.monthlyPrice = monthlyPrice;
        this.effectiveFrom = effectiveFrom;
        this.lastVerifiedAt = verifiedAt;
    }

    /** 금액은 그대로 두고 마지막 확인 일시만 바꾼다([요금 확인함]) */
    public void verify(Instant verifiedAt) {
        this.lastVerifiedAt = verifiedAt;
    }
}
