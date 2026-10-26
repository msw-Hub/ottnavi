package com.ottnavi.product.domain.entity;

import com.ottnavi.global.common.BaseTimeEntity;
import com.ottnavi.product.domain.enums.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 구독 상품(ERD subscription_product). 요금제와 번들을 하나로 통일한다. 판매가 끝나면 삭제하지 않고 비활성화한다 */
@Getter
@Entity
@Table(name = "subscription_product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionProduct extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name; // 예: 넷플릭스 스탠다드

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductType productType; // SINGLE / BUNDLE

    @Column(nullable = false)
    private boolean active; // false면 판매 종료. API에서는 isActive

    @Column(nullable = false)
    private boolean lowCost; // 저가형(광고형·베이직 등) 요금 분류. false면 표준. 가격 선택에만 쓴다. API에서는 isLowCost

    /** 판매 중인 상품을 만든다 */
    public static SubscriptionProduct create(String name, ProductType productType, boolean lowCost) {
        SubscriptionProduct product = new SubscriptionProduct();
        product.name = name;
        product.productType = productType;
        product.lowCost = lowCost;
        product.active = true; // DB 기본값과 같다. Hibernate는 DB 기본값을 쓰지 않으므로 직접 채운다
        return product;
    }

    /** 판매를 종료한다. 가격 이력과 사용자 구독이 참조하므로 행은 지우지 않는다 */
    public void deactivate() {
        this.active = false;
    }

    /** 판매를 다시 시작한다 */
    public void activate() {
        this.active = true;
    }

    /** 이름을 바꾼다. 공백 제거와 길이 검증은 호출하는 서비스가 한다 */
    public void rename(String name) {
        this.name = name;
    }

    /** 저가형 분류를 바꾼다 */
    public void changeLowCost(boolean lowCost) {
        this.lowCost = lowCost;
    }
}
