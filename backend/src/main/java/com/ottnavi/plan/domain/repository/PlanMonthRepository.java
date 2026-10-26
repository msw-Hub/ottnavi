package com.ottnavi.plan.domain.repository;

import com.ottnavi.plan.domain.entity.PlanMonth;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanMonthRepository extends JpaRepository<PlanMonth, Long> {

	/** 플랜들의 달을 플랜 ID, 달 번호 순으로 한 번에 조회한다 */
	@Query("select m from PlanMonth m where m.plan.id in :planIds order by m.plan.id asc, m.monthIndex asc")
	List<PlanMonth> findByPlanIds(@Param("planIds") Collection<Long> planIds);
}
