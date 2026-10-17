package com.ottnavi.retention.service;

import com.ottnavi.collect.job.CollectJobConfig;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오래된 Spring Batch 실행 이력(BATCH_*)을 지운다(Task 083, 결정 b: 180일 보관).
 * BATCH_* FK에는 ON DELETE가 없어서 하위 테이블부터 차례로 지운다. 지우는 단위는 Job 인스턴스다.
 *
 * <p>지우지 않는 것:
 * <ul>
 *   <li>끝나지 않은 실행(STARTED 등)과 END_TIME이 기준 이후인 실행. 미완료 실행을 지우면 재시작이 깨진다.</li>
 *   <li>마지막 COMPLETED 수집 실행. 플랜의 데이터 기준 시각(data_as_of)이 그 종료 시각을 읽는다(CollectExecutionQuery).</li>
 *   <li>지워도 되는 실행만으로 이뤄지지 않은 인스턴스(재시작으로 실행이 여럿인데 일부만 오래된 경우).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchHistoryPurger {

	private static final String TERMINAL_STATUSES = "('COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED')";
	private static final int CHUNK_SIZE = 500; // IN 목록 하나에 담는 인스턴스 수

	private final JdbcTemplate jdbcTemplate;

	/** cutoff(Batch 시각은 시간대 없는 값이라 JVM 시간대의 LocalDateTime)보다 먼저 끝난 실행 이력을 지우고 지운 인스턴스 수를 돌려준다 */
	@Transactional
	public int purgeEndedBefore(LocalDateTime cutoff) {
		// 1. 보호할 실행(마지막 COMPLETED 수집)을 찾는다. 없으면 어떤 ID와도 겹치지 않는 -1이다
		List<Long> protectedIds = jdbcTemplate.queryForList(
				"SELECT e.JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION e JOIN BATCH_JOB_INSTANCE i "
						+ "ON i.JOB_INSTANCE_ID = e.JOB_INSTANCE_ID WHERE i.JOB_NAME = ? AND e.STATUS = 'COMPLETED' "
						+ "ORDER BY e.END_TIME DESC, e.JOB_EXECUTION_ID DESC LIMIT 1",
				Long.class, CollectJobConfig.JOB_NAME);
		long protectedId = -1L;
		if (!protectedIds.isEmpty()) {
			protectedId = protectedIds.get(0);
		}

		// 2. 실행이 하나 이상 있고 그 전부가 지워도 되는 인스턴스만 고른다
		List<Long> instanceIds = jdbcTemplate.queryForList(
				"SELECT i.JOB_INSTANCE_ID FROM BATCH_JOB_INSTANCE i "
						+ "WHERE EXISTS (SELECT 1 FROM BATCH_JOB_EXECUTION e WHERE e.JOB_INSTANCE_ID = i.JOB_INSTANCE_ID) "
						+ "AND NOT EXISTS (SELECT 1 FROM BATCH_JOB_EXECUTION e WHERE e.JOB_INSTANCE_ID = i.JOB_INSTANCE_ID "
						+ "AND NOT (e.END_TIME < ? AND e.STATUS IN " + TERMINAL_STATUSES + " AND e.JOB_EXECUTION_ID <> ?))",
				Long.class, cutoff, protectedId);

		// 3. 인스턴스를 묶음으로 나눠 하위 테이블부터 지운다
		for (int from = 0; from < instanceIds.size(); from += CHUNK_SIZE) {
			int to = Math.min(from + CHUNK_SIZE, instanceIds.size());
			deleteInstances(instanceIds.subList(from, to));
		}
		log.info("오래된 배치 이력을 삭제했다: 기준={}, 삭제한 Job 인스턴스={}개", cutoff, instanceIds.size());
		return instanceIds.size();
	}

	private void deleteInstances(List<Long> instanceIds) {
		StringBuilder placeholders = new StringBuilder();
		for (int i = 0; i < instanceIds.size(); i++) {
			if (i > 0) {
				placeholders.append(", ");
			}
			placeholders.append('?');
		}
		Object[] args = new ArrayList<Object>(instanceIds).toArray();
		String executionIds = "(SELECT JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION WHERE JOB_INSTANCE_ID IN (" + placeholders + "))";
		String stepIds = "(SELECT STEP_EXECUTION_ID FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID IN " + executionIds + ")";

		jdbcTemplate.update("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT WHERE STEP_EXECUTION_ID IN " + stepIds, args);
		jdbcTemplate.update("DELETE FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID IN " + executionIds, args);
		jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT WHERE JOB_EXECUTION_ID IN " + executionIds, args);
		jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID IN " + executionIds, args);
		jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION WHERE JOB_INSTANCE_ID IN (" + placeholders + ")", args);
		jdbcTemplate.update("DELETE FROM BATCH_JOB_INSTANCE WHERE JOB_INSTANCE_ID IN (" + placeholders + ")", args);
	}
}
