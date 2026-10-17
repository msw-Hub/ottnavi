package com.ottnavi.collect.job;

import com.ottnavi.collect.step.detail.DetailStepConfig;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.batch.core.BatchStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 수집 실행 이력을 Batch 메타데이터 테이블(BATCH_*)에서 읽기 전용으로 조회한다.
 * Batch의 JobExplorer는 날짜·범위로 찾거나 페이지로 자르는 조회를 주지 않아 SQL을 직접 쓴다.
 * 파라미터는 BATCH_JOB_EXECUTION_PARAMS에 이름별 한 행(PARAMETER_VALUE는 문자열, 날짜는 ISO 형식)으로 있다.
 */
@Repository
public class CollectExecutionQuery {

    // targetDate·tier 파라미터를 각각 한 번씩 조인해 실행 한 건을 한 행으로 만든다
    private static final String SELECT_FROM = """
            SELECT e.JOB_EXECUTION_ID AS execution_id, e.JOB_INSTANCE_ID AS instance_id,
                   d.PARAMETER_VALUE AS target_date, t.PARAMETER_VALUE AS tier,
                   e.STATUS AS status, e.EXIT_CODE AS exit_code, e.EXIT_MESSAGE AS exit_message,
                   e.CREATE_TIME AS create_time, e.START_TIME AS start_time, e.END_TIME AS end_time,
                   (e.JOB_EXECUTION_ID = (SELECT MAX(x.JOB_EXECUTION_ID) FROM BATCH_JOB_EXECUTION x
                                          WHERE x.JOB_INSTANCE_ID = e.JOB_INSTANCE_ID)) AS is_latest
            FROM BATCH_JOB_EXECUTION e
            JOIN BATCH_JOB_INSTANCE i ON i.JOB_INSTANCE_ID = e.JOB_INSTANCE_ID
            JOIN BATCH_JOB_EXECUTION_PARAMS d ON d.JOB_EXECUTION_ID = e.JOB_EXECUTION_ID AND d.PARAMETER_NAME = ?
            JOIN BATCH_JOB_EXECUTION_PARAMS t ON t.JOB_EXECUTION_ID = e.JOB_EXECUTION_ID AND t.PARAMETER_NAME = ?
            WHERE i.JOB_NAME = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<CollectExecutionRecord> recordMapper = new RecordMapper();

    public CollectExecutionQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 수집 Job의 전체 실행 수 */
    public long countAll() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM BATCH_JOB_EXECUTION e JOIN BATCH_JOB_INSTANCE i "
                        + "ON i.JOB_INSTANCE_ID = e.JOB_INSTANCE_ID WHERE i.JOB_NAME = ?",
                Long.class, CollectJobConfig.JOB_NAME);
        if (count == null) {
            return 0;
        }
        return count;
    }

    /**
     * 수집 Job의 마지막 COMPLETED 실행의 종료 시각. 완료된 실행이 없으면 비어 있다.
     * Batch 시각은 시간대 없는 값이라 JVM 기본 시간대로 해석해 Instant로 바꾼다(CollectExecutionRules.toKstOffset과 같은 방식).
     */
    public Optional<Instant> findLatestCompletedEndTime() {
        List<LocalDateTime> found = jdbcTemplate.query(
                "SELECT MAX(e.END_TIME) AS end_time FROM BATCH_JOB_EXECUTION e JOIN BATCH_JOB_INSTANCE i "
                        + "ON i.JOB_INSTANCE_ID = e.JOB_INSTANCE_ID WHERE i.JOB_NAME = ? AND e.STATUS = 'COMPLETED'",
                (rs, rowNum) -> rs.getObject("end_time", LocalDateTime.class), CollectJobConfig.JOB_NAME);
        if (found.isEmpty() || found.get(0) == null) {
            return Optional.empty();
        }
        return Optional.of(found.get(0).atZone(ZoneId.systemDefault()).toInstant());
    }

    /** 수집 기준일 내림차순, 같은 날짜는 최근 실행이 먼저. page는 0부터 */
    public List<CollectExecutionRecord> findPage(int page, int size) {
        String sql = SELECT_FROM
                + " ORDER BY CAST(d.PARAMETER_VALUE AS DATE) DESC, e.JOB_EXECUTION_ID DESC LIMIT ? OFFSET ?";
        long offset = (long) page * size;
        return jdbcTemplate.query(sql, recordMapper, CollectJobParameters.TARGET_DATE, CollectJobParameters.TIER,
                CollectJobConfig.JOB_NAME, size, offset);
    }

    /** 실행 ID로 한 건을 찾는다. 수집이 아닌 다른 Job의 실행이면 비어 있다 */
    public Optional<CollectExecutionRecord> findById(long executionId) {
        List<CollectExecutionRecord> found = jdbcTemplate.query(SELECT_FROM + " AND e.JOB_EXECUTION_ID = ?",
                recordMapper, CollectJobParameters.TARGET_DATE, CollectJobParameters.TIER, CollectJobConfig.JOB_NAME,
                executionId);
        return firstOf(found);
    }

    /** 같은 targetDate+tier의 전체(scope 없는) 실행 중 가장 최근에 만들어진 실행 */
    public Optional<CollectExecutionRecord> findLatest(LocalDate targetDate, CollectTier tier) {
        return findLatest(targetDate, tier, null);
    }

    /**
     * 같은 targetDate+tier+scope에서 가장 최근에 만들어진 실행. scope가 null이면 scope 파라미터가 없는 전체 실행만 본다.
     * scope가 다르면 Batch가 다른 인스턴스로 보므로 중복 판정도 scope별로 따로 한다.
     */
    public Optional<CollectExecutionRecord> findLatest(LocalDate targetDate, CollectTier tier, CollectScope scope) {
        String sql = SELECT_FROM + " AND d.PARAMETER_VALUE = ? AND t.PARAMETER_VALUE = ?";
        List<Object> args = new ArrayList<>();
        args.add(CollectJobParameters.TARGET_DATE);
        args.add(CollectJobParameters.TIER);
        args.add(CollectJobConfig.JOB_NAME);
        args.add(targetDate.toString());
        args.add(tier.name());
        if (scope == null) {
            sql += " AND NOT EXISTS (SELECT 1 FROM BATCH_JOB_EXECUTION_PARAMS s"
                    + " WHERE s.JOB_EXECUTION_ID = e.JOB_EXECUTION_ID AND s.PARAMETER_NAME = ?)";
            args.add(CollectJobParameters.SCOPE);
        } else {
            sql += " AND EXISTS (SELECT 1 FROM BATCH_JOB_EXECUTION_PARAMS s"
                    + " WHERE s.JOB_EXECUTION_ID = e.JOB_EXECUTION_ID AND s.PARAMETER_NAME = ? AND s.PARAMETER_VALUE = ?)";
            args.add(CollectJobParameters.SCOPE);
            args.add(scope.name());
        }
        sql += " ORDER BY e.JOB_EXECUTION_ID DESC LIMIT 1";
        // 파라미터 순서: SELECT_FROM의 ?(이름 2개, Job 이름) → 값 2개 → scope 조건
        List<CollectExecutionRecord> found = jdbcTemplate.query(sql, recordMapper, args.toArray());
        return firstOf(found);
    }

    /** 같은 Job 인스턴스(날짜·tier·scope가 같은 실행 묶음)에서 가장 최근에 만들어진 실행. 재시작 판정이 쓴다 */
    public Optional<CollectExecutionRecord> findLatestOfInstance(long instanceId) {
        List<CollectExecutionRecord> found = jdbcTemplate.query(
                SELECT_FROM + " AND e.JOB_INSTANCE_ID = ? ORDER BY e.JOB_EXECUTION_ID DESC LIMIT 1",
                recordMapper, CollectJobParameters.TARGET_DATE, CollectJobParameters.TIER, CollectJobConfig.JOB_NAME,
                instanceId);
        return firstOf(found);
    }

    /** 끝나지 않은 상태(STARTING, STARTED, STOPPING)로 남은 실행. 서버 기동 시각과 비교해 중단 여부를 가른다 */
    public List<CollectExecutionRecord> findUnfinished() {
        return jdbcTemplate.query(SELECT_FROM + " AND e.STATUS IN ('STARTING', 'STARTED', 'STOPPING')"
                        + " ORDER BY e.JOB_EXECUTION_ID DESC",
                recordMapper, CollectJobParameters.TARGET_DATE, CollectJobParameters.TIER, CollectJobConfig.JOB_NAME);
    }

    /** 실행별 처리 건수: 영화·드라마 상세 Step의 WRITE_COUNT 합(404로 건너뛴 작품은 쓰지 않아 빠진다) */
    public Map<Long, Integer> sumDetailWriteCounts(List<Long> executionIds) {
        Map<Long, Integer> sums = new HashMap<>();
        if (executionIds.isEmpty()) {
            return sums;
        }
        List<Object> args = new ArrayList<>();
        args.add(DetailStepConfig.MOVIE_DETAIL_STEP);
        args.add(DetailStepConfig.TV_DETAIL_STEP);
        args.addAll(executionIds);
        String sql = "SELECT JOB_EXECUTION_ID AS execution_id, COALESCE(SUM(WRITE_COUNT), 0) AS write_count "
                + "FROM BATCH_STEP_EXECUTION WHERE STEP_NAME IN (?, ?) AND JOB_EXECUTION_ID IN ("
                + placeholders(executionIds.size()) + ") GROUP BY JOB_EXECUTION_ID";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        for (Map<String, Object> row : rows) {
            long executionId = ((Number) row.get("execution_id")).longValue();
            int writeCount = ((Number) row.get("write_count")).intValue();
            sums.put(executionId, writeCount);
        }
        return sums;
    }

    /**
     * 실패한 Step의 종료 메시지(실행 ID별 첫 번째 실패 Step). Job의 EXIT_MESSAGE가 비어 있을 때 대체로 쓴다.
     */
    public Map<Long, String> findFailedStepExitMessages(List<Long> executionIds) {
        Map<Long, String> messages = new HashMap<>();
        if (executionIds.isEmpty()) {
            return messages;
        }
        String sql = "SELECT JOB_EXECUTION_ID AS execution_id, EXIT_MESSAGE AS exit_message "
                + "FROM BATCH_STEP_EXECUTION WHERE STATUS = 'FAILED' AND JOB_EXECUTION_ID IN ("
                + placeholders(executionIds.size()) + ") ORDER BY STEP_EXECUTION_ID";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, executionIds.toArray());
        for (Map<String, Object> row : rows) {
            long executionId = ((Number) row.get("execution_id")).longValue();
            Object message = row.get("exit_message");
            if (message != null && !messages.containsKey(executionId)) {
                messages.put(executionId, message.toString());
            }
        }
        return messages;
    }

    private static Optional<CollectExecutionRecord> firstOf(List<CollectExecutionRecord> found) {
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(found.get(0));
    }

    private static String placeholders(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append('?');
        }
        return builder.toString();
    }

    private static class RecordMapper implements RowMapper<CollectExecutionRecord> {

        @Override
        public CollectExecutionRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            BatchStatus batchStatus = BatchStatus.UNKNOWN;
            String status = rs.getString("status");
            if (status != null) {
                batchStatus = BatchStatus.valueOf(status);
            }
            return new CollectExecutionRecord(
                    rs.getLong("execution_id"),
                    rs.getLong("instance_id"),
                    LocalDate.parse(rs.getString("target_date")),
                    CollectTier.valueOf(rs.getString("tier")),
                    batchStatus,
                    rs.getString("exit_code"),
                    rs.getString("exit_message"),
                    rs.getObject("create_time", LocalDateTime.class),
                    rs.getObject("start_time", LocalDateTime.class),
                    rs.getObject("end_time", LocalDateTime.class),
                    rs.getBoolean("is_latest"));
        }
    }
}
