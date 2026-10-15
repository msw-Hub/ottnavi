package com.ottnavi.product.domain.repository;

import com.ottnavi.product.domain.entity.SubscriptionProduct;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionProductRepository extends JpaRepository<SubscriptionProduct, Long> {

	/** 판매 종료 상품을 포함해 전체를 ID 순으로 조회한다 */
	List<SubscriptionProduct> findAllByOrderByIdAsc();
}
