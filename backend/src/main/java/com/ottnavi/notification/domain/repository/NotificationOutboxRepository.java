package com.ottnavi.notification.domain.repository;

import com.ottnavi.notification.domain.entity.NotificationOutbox;
import com.ottnavi.notification.domain.enums.NotificationStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

	/** 그 ID의 행이 그 상태로 있는지 확인한다(발송 직전 재확인) */
	boolean existsByIdAndStatus(Long id, NotificationStatus status);

	/**
	 * 발송할 때가 된 대기 알림을 id 순으로 최대 limit건 읽으며 잠근다.
	 * 락 타임아웃 -2는 "잠긴 행은 기다리지 않고 건너뛴다"(SKIP LOCKED)라서, 발송기 둘이 동시에 불러도 서로 다른 행을 받는다.
	 * 호출하는 쪽의 쓰기 트랜잭션 안에서만 쓴다(잠금은 트랜잭션이 끝날 때 풀린다).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
	@Query("select n from NotificationOutbox n "
			+ "where n.status = com.ottnavi.notification.domain.enums.NotificationStatus.PENDING "
			+ "and n.nextAttemptAt <= :now order by n.id")
	List<NotificationOutbox> findDueForUpdate(@Param("now") Instant now, Limit limit);
}
