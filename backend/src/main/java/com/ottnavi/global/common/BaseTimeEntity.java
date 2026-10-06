package com.ottnavi.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * 공통 감사 컬럼(created_at, updated_at) 베이스 엔티티.
 * 값은 Hibernate가 JVM 시각으로 채운다. 벌크 JPQL UPDATE에는 반영되지 않으므로 필요하면 쿼리에서 직접 갱신한다.
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity {

	// Instant의 기본 매핑은 TIMESTAMP(TZ 없음)라 ERD의 TIMESTAMPTZ와 맞추려고 타입을 명시한다
	@CreationTimestamp
	@JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt; // 생성 시각

	@UpdateTimestamp
	@JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt; // 마지막 수정 시각
}
