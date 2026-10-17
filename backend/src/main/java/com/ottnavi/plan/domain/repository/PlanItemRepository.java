package com.ottnavi.plan.domain.repository;

import com.ottnavi.plan.domain.entity.PlanItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanItemRepository extends JpaRepository<PlanItem, Long> {

	/** 플랜들의 항목을 ID 순으로 한 번에 조회한다. ID 순이 계산 입력(찜) 순서다 */
	@Query("select i from PlanItem i where i.plan.id in :planIds order by i.id asc")
	List<PlanItem> findByPlanIds(@Param("planIds") Collection<Long> planIds);

	/** 플랜들의 항목에서 [플랜 ID, 시청 단위 ID] 쌍만 조회한다(영향 플랜 선정용) */
	@Query("select i.plan.id, i.watchUnitId from PlanItem i where i.plan.id in :planIds")
	List<Object[]> findUnitIdsByPlanIds(@Param("planIds") Collection<Long> planIds);
}
