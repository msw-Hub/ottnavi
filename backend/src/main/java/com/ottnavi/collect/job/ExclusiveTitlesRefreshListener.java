package com.ottnavi.collect.job;

import com.ottnavi.catalog.service.ExclusiveTitleCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * 수집 Job이 끝나면 서비스별 독점 목록 캐시(exclusiveTitles)를 갱신한다(Task 078).
 * 성공(COMPLETED)이면 7개 서비스의 독점 목록을 미리 계산해 채우고, 실패·중단이면 비우기만 한다(다음 조회가 그 자리에서 계산한다).
 * 실패한 실행도 실패 시점까지는 제공 상태가 작품 단위로 반영되어 있으므로 옛 목록을 남기지 않으려고 비운다.
 * 계산이나 Redis 쓰기가 실패해도 이미 끝난 수집 결과를 바꾸지 않도록 예외를 올리지 않고 경고만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExclusiveTitlesRefreshListener implements JobExecutionListener {

    private final ExclusiveTitleCacheService exclusiveTitleCacheService;

    @Override
    public void afterJob(JobExecution jobExecution) {
        // 1. 완료가 아니면 비우기만 한다
        if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
            exclusiveTitleCacheService.evictAll();
            log.info("수집이 완료되지 않아 독점 목록 캐시를 비웠다: executionId={}, status={}", jobExecution.getId(),
                    jobExecution.getStatus());
            return;
        }

        // 2. 완료면 새로 계산해 채운다. 실패하면 옛 값을 남기지 않게 비운다
        try {
            exclusiveTitleCacheService.refreshAll();
            log.info("수집이 끝나 독점 목록을 새로 계산해 캐시에 채웠다: executionId={}", jobExecution.getId());
        } catch (RuntimeException e) {
            log.warn("독점 목록을 계산하지 못해 캐시를 비운다(다음 조회가 즉석에서 계산한다): executionId={}, error={}",
                    jobExecution.getId(), e.toString());
            exclusiveTitleCacheService.evictAll();
        }
    }
}
