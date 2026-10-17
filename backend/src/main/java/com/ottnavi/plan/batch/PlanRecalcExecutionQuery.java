package com.ottnavi.plan.batch;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 재계산 Job의 실행 기록을 Batch 메타데이터 테이블(BATCH_*)에서 읽기 전용으로 조회한다(CollectExecutionQuery와 같은 방식).
 */
@Repository
public class PlanRecalcExecutionQuery {

	private final JdbcTemplate jdbcTemplate;

	public PlanRecalcExecutionQuery(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * 지금 이 서버에서 실행 중인 재계산이 있는지. 끝나지 않은 상태(STARTING·STARTED·STOPPING)이면서
	 * 서버 기동 시각 이후에 만들어진 기록만 센다(서버 재기동 전의 기록은 그 서버가 죽은 것이라 중단으로 본다, 수집과 같은 규칙).
	 */
	public boolean existsRunning(LocalDateTime bootedAt) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM BATCH_JOB_EXECUTION e JOIN BATCH_JOB_INSTANCE i "
						+ "ON i.JOB_INSTANCE_ID = e.JOB_INSTANCE_ID WHERE i.JOB_NAME = ? "
						+ "AND e.STATUS IN ('STARTING', 'STARTED', 'STOPPING') AND e.CREATE_TIME >= ?",
				Long.class, PlanRecalcJobConfig.JOB_NAME, bootedAt);
		return count != null && count > 0;
	}

	/** 수집 실행이 DB에 COMPLETED로 저장됐는지. 수집의 afterJob 시점에는 아직 저장 전일 수 있어 재계산이 이를 기다린다 */
	public boolean isCollectCompleted(long collectExecutionId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID = ? AND STATUS = 'COMPLETED'",
				Long.class, collectExecutionId);
		return count != null && count > 0;
	}

	/**
	 * 수집 실행 ID별로 그 수집 뒤 이어 돈 재계산 중 가장 최근 COMPLETED 실행의 대상 플랜 수(Step의 READ_COUNT)를 한 번에 조회한다.
	 * 재계산이 없었거나 아직 끝나지 않았으면 결과에 없다(호출하는 쪽이 null로 본다).
	 * 예: [5, 6] → {5=12} (6번 수집은 재계산 없음)
	 */
	public Map<Long, Integer> findTargetCountsByCollectExecutionIds(List<Long> collectExecutionIds) {
		Map<Long, Integer> counts = new HashMap<>();
		if (collectExecutionIds.isEmpty()) {
			return counts;
		}
		StringBuilder placeholders = new StringBuilder();
		List<Object> args = new ArrayList<>();
		args.add(PlanRecalcJobConfig.JOB_NAME);
		args.add(PlanRecalcJobConfig.STEP_NAME);
		args.add(PlanRecalcJobParameters.COLLECT_EXECUTION_ID);
		for (int i = 0; i < collectExecutionIds.size(); i++) {
			if (i > 0) {
				placeholders.append(", ");
			}
			placeholders.append('?');
			args.add(String.valueOf(collectExecutionIds.get(i)));
		}
		// 실행 ID 오름차순으로 읽어 같은 수집에 재계산이 여러 번이면 나중 것이 덮어쓴다
		String sql = "SELECT p.PARAMETER_VALUE AS collect_id, s.READ_COUNT AS read_count "
				+ "FROM BATCH_JOB_EXECUTION e "
				+ "JOIN BATCH_JOB_INSTANCE i ON i.JOB_INSTANCE_ID = e.JOB_INSTANCE_ID "
				+ "JOIN BATCH_STEP_EXECUTION s ON s.JOB_EXECUTION_ID = e.JOB_EXECUTION_ID AND s.STEP_NAME = ? "
				+ "JOIN BATCH_JOB_EXECUTION_PARAMS p ON p.JOB_EXECUTION_ID = e.JOB_EXECUTION_ID AND p.PARAMETER_NAME = ? "
				+ "WHERE i.JOB_NAME = ? AND e.STATUS = 'COMPLETED' AND p.PARAMETER_VALUE IN (" + placeholders + ") "
				+ "ORDER BY e.JOB_EXECUTION_ID ASC";
		// 파라미터 순서: Step 이름, 파라미터 이름, Job 이름, 수집 ID들
		List<Object> ordered = new ArrayList<>();
		ordered.add(args.get(1));
		ordered.add(args.get(2));
		ordered.add(args.get(0));
		ordered.addAll(args.subList(3, args.size()));
		for (Map<String, Object> row : jdbcTemplate.queryForList(sql, ordered.toArray())) {
			long collectId = Long.parseLong(row.get("collect_id").toString());
			counts.put(collectId, ((Number) row.get("read_count")).intValue());
		}
		return counts;
	}
}
