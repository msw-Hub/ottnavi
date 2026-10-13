package com.ottnavi.product.domain.entity;

import com.ottnavi.global.common.BaseTimeEntity;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.domain.enums.ProviderTier;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상품에 포함된 서비스와 tier(ERD product_component). 복합 기본키 (product_id, ott_service_id) */
@Getter
@Entity
@Table(name = "product_component")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductComponent extends BaseTimeEntity {

    @EmbeddedId
    private ProductComponentId id;

    @MapsId("productId") // 기본키의 productId를 이 연관에서 가져온다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private SubscriptionProduct product;

    @MapsId("ottServiceId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ott_service_id")
    private OttService ottService;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProviderTier tier; // STANDARD / AD. MVP는 STANDARD만 쓴다

    /** 상품 구성 한 줄을 만든다. 상품과 서비스는 이미 저장돼 id가 있어야 한다 */
    public static ProductComponent create(SubscriptionProduct product, OttService ottService, ProviderTier tier) {
        ProductComponent component = new ProductComponent();
        component.id = new ProductComponentId(product.getId(), ottService.getId());
        component.product = product;
        component.ottService = ottService;
        component.tier = tier;
        return component;
    }
}
