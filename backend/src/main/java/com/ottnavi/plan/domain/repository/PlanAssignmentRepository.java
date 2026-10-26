package com.ottnavi.plan.domain.repository;

import com.ottnavi.plan.domain.entity.PlanAssignment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanAssignmentRepository extends JpaRepository<PlanAssignment, Long> {

	/** 플랜들의 배정을 항목 ID, 달 번호 순으로 한 번에 조회한다 */
	@Query("select a from PlanAssignment a where a.planItem.plan.id in :planIds "
			+ "order by a.planItem.id asc, a.planMonth.monthIndex asc")
	List<PlanAssignment> findByPlanIds(@Param("planIds") Collection<Long> planIds);
}
