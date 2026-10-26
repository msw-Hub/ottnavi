package com.ottnavi.plan.domain.repository;

import com.ottnavi.plan.domain.entity.CalcRun;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalcRunRepository extends JpaRepository<CalcRun, Long> {

	/** 플랜과 연결이 끊긴(plan_id가 NULL) 기록 중 cutoff보다 오래된 것을 벌크로 지운다. 지운 건수를 돌려준다 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from CalcRun c where c.planId is null and c.createdAt < :cutoff")
	int deleteDetachedBefore(@Param("cutoff") Instant cutoff);
}
