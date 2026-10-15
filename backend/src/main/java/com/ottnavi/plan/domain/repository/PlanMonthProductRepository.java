package com.ottnavi.plan.domain.repository;

import com.ottnavi.plan.domain.entity.PlanMonthProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanMonthProductRepository extends JpaRepository<PlanMonthProduct, Long> {
}
