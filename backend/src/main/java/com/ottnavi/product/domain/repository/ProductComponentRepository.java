package com.ottnavi.product.domain.repository;

import com.ottnavi.product.domain.entity.ProductComponent;
import com.ottnavi.product.domain.entity.ProductComponentId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductComponentRepository extends JpaRepository<ProductComponent, ProductComponentId>  {

	/** 상품들의 구성 줄을 조회한다. 단품은 상품마다 1행이다 */
	@Query("select c from ProductComponent c where c.id.productId in :productIds")
	List<ProductComponent> findByProductIds(@Param("productIds") Collection<Long> productIds);
}
