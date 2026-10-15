package com.ottnavi.plan.domain.repository;

import com.ottnavi.plan.domain.entity.PlanItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanItemRepository extends JpaRepository<PlanItem, Long> {
}
