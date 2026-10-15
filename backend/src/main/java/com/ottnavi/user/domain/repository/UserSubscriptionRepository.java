package com.ottnavi.user.domain.repository;

import com.ottnavi.user.domain.entity.UserSubscription;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

	/** 사용자의 이용 상태 행을 서비스 ID 오름차순으로 조회한다 */
	@Query("select s from UserSubscription s join fetch s.ottService where s.user.id = :userId order by s.ottService.id asc")
	List<UserSubscription> findAllByUserId(@Param("userId") Long userId);

	/**
	 * 사용자의 이용 상태 행을 한 번에 지운다(설정 전체 교체의 첫 동작).
	 * 같은 트랜잭션에서 delete 뒤 save로 같은 유니크 키(user_id, ott_service_id)를 넣으면 Hibernate가 INSERT를 먼저 실행해
	 * 유니크 위반이 나므로, 벌크 JPQL로 먼저 지우고 flush·clear 한 뒤 넣는다(ERD 408행).
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from UserSubscription s where s.user.id = :userId")
	int deleteAllByUserId(@Param("userId") Long userId);
}
