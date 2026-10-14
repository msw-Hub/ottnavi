package com.ottnavi.collect.job;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.batch.core.BatchStatus;

/**
 * Batch 메타데이터 테이블에서 읽은 수집 실행 한 건. 시각은 JVM 기본 시간대의 LocalDateTime 그대로다.
 *
 * @param executionId        BATCH_JOB_EXECUTION의 ID
 * @param instanceId         Job 인스턴스 ID(같은 targetDate+tier는 같은 인스턴스)
 * @param isLatestOfInstance 같은 인스턴스에서 가장 최근에 만들어진 실행인지
 */
public record CollectExecutionRecord(
        long executionId,
        long instanceId,
        LocalDate targetDate,
        CollectTier tier,
        BatchStatus batchStatus,
        String exitCode,
        String exitMessage,
        LocalDateTime createTime,
        LocalDateTime startTime, // 시작 전이면 null
        LocalDateTime endTime, // 끝나기 전이면 null
        boolean isLatestOfInstance) {
}
