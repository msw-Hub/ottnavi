package com.ottnavi.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 캐시 오류 시 DB 폴백 검증(Task 051). Docker 없이 Spring 캐시 프록시만 띄워,
 * 캐시의 get·put이 예외를 던져도 원본 메서드 결과가 그대로 반환되는지 확인한다.
 */
class CacheErrorHandlingConfigTest {

    static final AtomicInteger CALLS = new AtomicInteger(); // 원본(DB) 메서드 호출 횟수

    /** get·put·clear가 항상 예외를 던지는 캐시(Redis 장애 흉내) */
    static class BrokenCache extends ConcurrentMapCache {
        BrokenCache(String name) {
            super(name);
        }

        @Override
        public ValueWrapper get(Object key) {
            throw new IllegalStateException("Redis 연결 실패");
        }

        @Override
        public void put(Object key, Object value) {
            throw new IllegalStateException("Redis 연결 실패");
        }

        @Override
        public void clear() {
            throw new IllegalStateException("Redis 연결 실패");
        }
    }

    static class DbBackedBean {
        @Cacheable(cacheNames = "broken", key = "#id")
        public String load(long id) {
            CALLS.incrementAndGet();
            return "DB값-" + id;
        }
    }

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            SimpleCacheManager manager = new SimpleCacheManager();
            manager.setCaches(List.of(new BrokenCache("broken")));
            return manager;
        }

        @Bean
        CacheErrorHandlingConfig cacheErrorHandlingConfig() {
            return new CacheErrorHandlingConfig();
        }

        @Bean
        DbBackedBean dbBackedBean() {
            return new DbBackedBean();
        }
    }

    @Test
    void 캐시_조회와_저장이_실패해도_원본_결과를_반환한다() {
        // given
        CALLS.set(0);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            DbBackedBean bean = context.getBean(DbBackedBean.class);

            // when
            String first = bean.load(7);
            String second = bean.load(7);

            // then: 캐시가 동작하지 않아 매번 원본을 호출하지만 예외 없이 값이 나온다
            assertThat(first).isEqualTo("DB값-7");
            assertThat(second).isEqualTo("DB값-7");
            assertThat(CALLS.get()).isEqualTo(2);
        }
    }

    @Test
    void 캐시_비우기가_실패해도_예외를_올리지_않는다() {
        // given
        Cache broken = new BrokenCache("broken");
        CacheErrorHandlingConfig.LoggingCacheErrorHandler handler = new CacheErrorHandlingConfig.LoggingCacheErrorHandler();

        // when
        handler.handleCacheClearError(new IllegalStateException("Redis 연결 실패"), broken);
        handler.handleCacheEvictError(new IllegalStateException("Redis 연결 실패"), broken, "k");

        // then: 예외 없이 여기까지 도달한다
        assertThat(broken.getName()).isEqualTo("broken");
    }
}
