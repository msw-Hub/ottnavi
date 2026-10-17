package com.ottnavi.collect.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 수집 Job이 COMPLETED로 끝났을 때만(scope=AVAILABILITY_TV 실행은 제외) {@link CollectCompletedEvent}를 발행한다(Task 069, R-21).
 * 이벤트를 받는 쪽(재계산 시작)에서 예외가 나도 이미 끝난 수집의 상태를 바꾸지 않도록 여기서 잡아 로그만 남긴다.
 * 주의: afterJob 시점에는 실행 상태가 DB에 아직 COMPLETED로 저장되기 전일 수 있다(저장은 리스너 뒤의 마지막 갱신).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectCompletionListener implements JobExecutionListener {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void afterJob(JobExecution jobExecution) {
        // 1. 완료가 아니면(실패·중단) 후속 작업을 시작하지 않는다
        if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
            return;
        }

        // 2. 일부 범위(scope=AVAILABILITY_TV) 수집은 재계산을 부르지 않는다(사용자 결정 2026-10-17)
        CollectScope scope = CollectJobParameters.scopeOf(jobExecution.getJobParameters());
        if (scope == CollectScope.AVAILABILITY_TV) {
            log.info("일부 범위 수집이라 재계산을 시작하지 않는다: executionId={}, scope={}", jobExecution.getId(), scope);
            return;
        }

        // 3. 완료 이벤트를 발행한다. 받는 쪽 예외가 수집 결과에 영향을 주지 않게 막는다
        try {
            eventPublisher.publishEvent(new CollectCompletedEvent(jobExecution.getId()));
        } catch (RuntimeException e) {
            log.error("수집 완료 이벤트 처리에 실패했다: executionId={}", jobExecution.getId(), e);
        }
    }
}
