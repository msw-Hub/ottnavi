package com.ottnavi.product.domain.repository;

import com.ottnavi.product.domain.entity.SubscriptionProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionProductRepository extends JpaRepository<SubscriptionProduct, Long> {
}
