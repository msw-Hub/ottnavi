package com.ottnavi.plan.domain.repository;

import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.enums.PlanStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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

	/** ACTIVE 플랜을 가진 사용자 수(제외 목록 없음) */
	@Query("select count(distinct p.userId) from Plan p where p.status = com.ottnavi.plan.domain.enums.PlanStatus.ACTIVE")
	long countActivePlanUsers();

	/** ACTIVE 플랜을 가진 사용자 수에서 제외할 사용자 ID를 뺀다. 목록이 비면 호출하지 않는다(NOT IN 빈 목록 회피) */
	@Query("select count(distinct p.userId) from Plan p where p.status = com.ottnavi.plan.domain.enums.PlanStatus.ACTIVE "
			+ "and p.userId not in :excludedUserIds")
	long countActivePlanUsersExcluding(@Param("excludedUserIds") Collection<Long> excludedUserIds);

	/** 해당 상태의 플랜 전부(매월 재계산이 ACTIVE 전체에서 대상을 고른다) */
	List<Plan> findAllByStatus(PlanStatus status);

	/** 변경 요약이 있는 사용자의 플랜을 최신(ID 큰 순)부터 조회한다. 변경 내역 조회·ARCHIVED 정리가 Pageable로 개수를 자른다 */
	@Query("select p from Plan p where p.userId = :userId and p.changeSummary is not null order by p.id desc")
	List<Plan> findWithChangeSummary(@Param("userId") Long userId, Pageable pageable);

	/**
	 * 보관 대상이 아닌 ARCHIVED를 지운다(Task 069 결정 2). keepIds에 든 플랜은 남긴다. 하위 행은 DB FK CASCADE가 지우고
	 * calc_run.plan_id와 plan.previous_plan_id는 FK ON DELETE SET NULL이 비운다(TECH T-1).
	 * clear는 지워진 엔티티가 영속성 컨텍스트에 남지 않게 한다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from Plan p where p.userId = :userId and p.status = com.ottnavi.plan.domain.enums.PlanStatus.ARCHIVED "
			+ "and p.id not in :keepIds")
	int deleteArchivedExcept(@Param("userId") Long userId, @Param("keepIds") Collection<Long> keepIds);

	/** 해당 시청 단위가 항목에 들어 있는 ACTIVE 플랜 ID(TMDB에서 사라진 작품 정리가 재계산 대상을 정할 때 쓴다) */
	@Query("select distinct i.plan.id from PlanItem i where i.watchUnitId in :watchUnitIds "
			+ "and i.plan.status = com.ottnavi.plan.domain.enums.PlanStatus.ACTIVE")
	List<Long> findActiveIdsByWatchUnitIds(@Param("watchUnitIds") Collection<Long> watchUnitIds);

	/** 해당 시청 단위가 항목에 들어 있는 DRAFT를 모든 사용자에서 지운다. 하위 행은 DB FK CASCADE가 지운다(TECH T-1) */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from Plan p where p.status = com.ottnavi.plan.domain.enums.PlanStatus.DRAFT "
			+ "and p.id in (select i.plan.id from PlanItem i where i.watchUnitId in :watchUnitIds)")
	int deleteDraftsByWatchUnitIds(@Param("watchUnitIds") Collection<Long> watchUnitIds);

	/**
	 * 해당 시청 단위가 항목에 들어 있는 플랜을 상태와 상관없이 지운다(ARCHIVED 통째 삭제, 재계산하지 못한 ACTIVE 포함).
	 * 하위 행은 DB FK CASCADE가 지우고 calc_run.plan_id와 previous_plan_id는 SET NULL이 비운다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from Plan p where p.id in (select i.plan.id from PlanItem i where i.watchUnitId in :watchUnitIds)")
	int deleteByWatchUnitIds(@Param("watchUnitIds") Collection<Long> watchUnitIds);
}
