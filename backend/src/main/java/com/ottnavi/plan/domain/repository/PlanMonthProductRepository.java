package com.ottnavi.plan.domain.repository;

import com.ottnavi.plan.domain.entity.PlanMonthProduct;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanMonthProductRepository extends JpaRepository<PlanMonthProduct, Long> {

	/** 플랜들의 달별 상품을 상품 ID 순으로 한 번에 조회한다 */
	@Query("select p from PlanMonthProduct p where p.planMonth.plan.id in :planIds order by p.productId asc")
	List<PlanMonthProduct> findByPlanIds(@Param("planIds") Collection<Long> planIds);
}
