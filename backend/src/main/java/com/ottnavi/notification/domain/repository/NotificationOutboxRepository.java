package com.ottnavi.notification.domain.repository;

import com.ottnavi.notification.domain.entity.NotificationOutbox;
import com.ottnavi.notification.domain.enums.NotificationStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

	/**
	 * 지금 발송할 때가 된 대기 행 하나를 잠가 읽는다. 스케줄러가 이미 잡았거나(임대로 next_attempt_at이 미래) 끝난 행, 잠긴 행은 빈 값이다.
	 * 즉시 발송이 스케줄러와 겹칠 때 먼저 잡은 쪽만 발송하게 하는 용도다. 호출하는 쪽의 쓰기 트랜잭션 안에서만 쓴다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
	@Query("select n from NotificationOutbox n "
			+ "where n.id = :id and n.status = com.ottnavi.notification.domain.enums.NotificationStatus.PENDING "
			+ "and n.nextAttemptAt <= :now")
	Optional<NotificationOutbox> findOneDueForUpdate(@Param("id") Long id, @Param("now") Instant now);

	/**
	 * 같은 dedupe_key의 행이 없을 때만 대기 알림을 넣는다. 넣었으면 1, 이미 있어 넣지 않았으면 0을 돌려준다.
	 * exists 확인 뒤 save는 쓰지 않는다: PostgreSQL은 유니크 위반이 나면 그 트랜잭션 전체가 실패 상태가 되어
	 * 같은 트랜잭션의 재계산 저장까지 망가지기 때문에, 충돌을 DB가 조용히 넘기는 ON CONFLICT DO NOTHING을 쓴다.
	 * 대기 중인 변경을 먼저 반영(flush)한 뒤 실행한다.
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = "INSERT INTO notification_outbox "
			+ "(user_id, type, dedupe_key, payload, status, attempt_count, next_attempt_at, created_at, updated_at) "
			+ "VALUES (:userId, :type, :dedupeKey, CAST(:payload AS jsonb), 'PENDING', 0, :now, :now, :now) "
			+ "ON CONFLICT (dedupe_key) DO NOTHING", nativeQuery = true)
	int insertIfAbsent(@Param("userId") Long userId, @Param("type") String type, @Param("dedupeKey") String dedupeKey,
			@Param("payload") String payload, @Param("now") Instant now);

	/** dedupe_key로 행 ID를 찾는다(방금 넣은 행의 ID를 얻는 용도) */
	@Query("select n.id from NotificationOutbox n where n.dedupeKey = :dedupeKey")
	Optional<Long> findIdByDedupeKey(@Param("dedupeKey") String dedupeKey);
}
