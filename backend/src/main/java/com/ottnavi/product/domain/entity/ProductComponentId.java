package com.ottnavi.product.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** product_component의 복합 기본키. equals/hashCode가 필수이고, 값은 ProductComponent의 @MapsId가 연관 객체와 맞춘다 */
@Getter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProductComponentId implements Serializable {

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "ott_service_id")
    private Long ottServiceId;
}
