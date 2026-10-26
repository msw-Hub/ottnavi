package com.ottnavi.collect.job;

import com.ottnavi.global.config.RedisConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * 수집 Job이 끝나면(완료·실패·중단 모두) 작품 상세 캐시(titleDetail)를 비운다.
 * 수집이 제공 상태·러닝타임을 바꾸므로, 캐시에 남은 옛 값을 24시간 동안 보여 주지 않기 위해서다.
 * 실패한 실행도 실패 시점까지는 작품 단위로 반영되어 있으므로 함께 비운다(다음 조회가 DB를 한 번 읽을 뿐이다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TitleCacheEvictListener implements JobExecutionListener {

    private final CacheManager cacheManager;

    @Override
    public void afterJob(JobExecution jobExecution) {
        // 1. 캐시를 비운다. 실패해도 이미 끝난 수집 결과를 바꾸지 않도록 경고만 남긴다
        try {
            Cache cache = cacheManager.getCache(RedisConfig.TITLE_DETAIL);
            if (cache != null) {
                cache.clear();
                log.info("수집이 끝나 작품 상세 캐시를 비웠다: executionId={}", jobExecution.getId());
            }
        } catch (RuntimeException e) {
            log.warn("작품 상세 캐시를 비우지 못했다(TTL 24시간 뒤 만료): executionId={}, error={}",
                    jobExecution.getId(), e.toString());
        }
    }
}
