package com.ottnavi.product.domain.repository;

import com.ottnavi.product.domain.entity.ProductPrice;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, Long>  {

	/** 상품 하나의 요금 줄을 적용 시작일이 빠른 순으로 조회한다 */
	@Query("select p from ProductPrice p where p.product.id = :productId order by p.effectiveFrom asc")
	List<ProductPrice> findByProductIdOrderByEffectiveFromAsc(@Param("productId") Long productId);

	/** 여러 상품의 요금 줄을 상품 ID, 적용 시작일 순으로 한 번에 조회한다(목록 화면의 N+1 방지) */
	@Query("select p from ProductPrice p where p.product.id in :productIds order by p.product.id asc, p.effectiveFrom asc")
	List<ProductPrice> findByProductIdsOrderByEffectiveFromAsc(@Param("productIds") Collection<Long> productIds);
}
