package com.ottnavi.plan.domain.repository;

import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.enums.PlanStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanRepository extends JpaRepository<Plan, Long> {

	/** 사용자의 해당 상태 플랜 목록(DRAFT 최대 3건 조회 등) */
	List<Plan> findAllByUserIdAndStatus(Long userId, PlanStatus status);

	/** 사용자 본인의 해당 상태 플랜을 ID로 찾는다. 남의 것이나 상태가 다른 것은 비어 있다 */
	Optional<Plan> findByIdAndUserIdAndStatus(Long id, Long userId, PlanStatus status);

	/** 사용자의 ACTIVE 플랜을 찾는다. 부분 유니크 인덱스라 최대 1건이다(status는 ACTIVE로 넘긴다) */
	Optional<Plan> findByUserIdAndStatus(Long userId, PlanStatus status);

	/**
	 * 사용자의 ACTIVE 플랜을 쓰기 잠금으로 조회한다. 같은 사용자의 저장·재계산이 겹치지 않게 직렬화할 때 쓴다.
	 * 트랜잭션 안에서 호출해야 한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Plan p where p.userId = :userId and p.status = com.ottnavi.plan.domain.enums.PlanStatus.ACTIVE")
	Optional<Plan> findActiveByUserIdForUpdate(@Param("userId") Long userId);

	/**
	 * 사용자의 DRAFT를 모두 지운다. 하위 월·상품·항목·배정은 DB FK CASCADE가 지운다(TECH T-1).
	 * flush는 지우기 전 대기 중인 변경을 반영하고, clear는 지워진 엔티티가 영속성 컨텍스트에 남지 않게 한다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from Plan p where p.userId = :userId and p.status = com.ottnavi.plan.domain.enums.PlanStatus.DRAFT")
	int deleteDraftsByUserId(@Param("userId") Long userId);
}
