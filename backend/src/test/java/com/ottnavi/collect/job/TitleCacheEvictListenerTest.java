package com.ottnavi.collect.job;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ottnavi.global.config.RedisConfig;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/** 수집 Job 완료 시 작품 상세 캐시를 비우는 리스너 단위 테스트(Task 051) */
class TitleCacheEvictListenerTest {

    private final CacheManager cacheManager = mock(CacheManager.class);
    private final Cache cache = mock(Cache.class);
    private final TitleCacheEvictListener listener = new TitleCacheEvictListener(cacheManager);

    @Test
    void 정상_완료면_작품_상세_캐시를_비운다() {
        // given
        when(cacheManager.getCache(RedisConfig.TITLE_DETAIL)).thenReturn(cache);
        JobExecution execution = mock(JobExecution.class);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);

        // when
        listener.afterJob(execution);

        // then
        verify(cache).clear();
    }

    @Test
    void 실패한_실행이면_캐시를_비우지_않는다() {
        // given
        JobExecution execution = mock(JobExecution.class);
        when(execution.getStatus()).thenReturn(BatchStatus.FAILED);

        // when
        listener.afterJob(execution);

        // then
        verify(cacheManager, never()).getCache(RedisConfig.TITLE_DETAIL);
    }

    @Test
    void 캐시를_비우다_예외가_나도_Job_결과에_영향을_주지_않는다() {
        // given
        when(cacheManager.getCache(RedisConfig.TITLE_DETAIL)).thenReturn(cache);
        org.mockito.Mockito.doThrow(new IllegalStateException("Redis 연결 실패")).when(cache).clear();
        JobExecution execution = mock(JobExecution.class);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);

        // when: 예외가 밖으로 나오면 이 줄에서 테스트가 실패한다
        listener.afterJob(execution);

        // then
        verify(cache).clear();
    }
}
