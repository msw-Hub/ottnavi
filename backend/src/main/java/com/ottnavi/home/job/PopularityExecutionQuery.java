package com.ottnavi.home.job;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 인기 수집 Job의 실행 기록을 Batch 메타데이터 테이블(BATCH_*)에서 읽기 전용으로 조회한다(RetentionExecutionQuery와 같은 방식) */
@Repository
public class PopularityExecutionQuery {

	private final JdbcTemplate jdbcTemplate;

	public PopularityExecutionQuery(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * 지금 이 서버에서 실행 중인 인기 수집이 있는지. 끝나지 않은 상태(STARTING·STARTED·STOPPING)이면서
	 * 서버 기동 시각 이후에 만들어진 기록만 센다(서버 재기동 전의 기록은 그 서버가 죽은 것이라 중단으로 본다, 수집·재계산·정리와 같은 규칙).
	 */
	public boolean existsRunning(LocalDateTime bootedAt) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM BATCH_JOB_EXECUTION e JOIN BATCH_JOB_INSTANCE i "
						+ "ON i.JOB_INSTANCE_ID = e.JOB_INSTANCE_ID WHERE i.JOB_NAME = ? "
						+ "AND e.STATUS IN ('STARTING', 'STARTED', 'STOPPING') AND e.CREATE_TIME >= ?",
				Long.class, PopularityJobConfig.JOB_NAME, bootedAt);
		return count != null && count > 0;
	}
}
