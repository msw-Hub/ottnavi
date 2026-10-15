package com.ottnavi.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * 캐시 오류 처리: Redis가 응답하지 않거나 저장된 값을 읽지 못해도 요청을 실패시키지 않는다.
 * 캐시는 속도를 위한 보조 수단이라, 오류가 나면 경고만 남기고 원본(DB)으로 응답한다.
 */
@Slf4j
@Configuration
public class CacheErrorHandlingConfig implements CachingConfigurer {

	@Override
	public CacheErrorHandler errorHandler() {
		return new LoggingCacheErrorHandler();
	}

	/** get·put·evict·clear 실패를 예외로 올리지 않고 warn 로그만 남기는 핸들러 */
	static class LoggingCacheErrorHandler implements CacheErrorHandler {

		@Override
		public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
			log.warn("캐시 조회 실패, DB로 응답한다: cache={}, key={}, error={}",
					cache.getName(), key, exception.toString());
		}

		@Override
		public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
			log.warn("캐시 저장 실패, 무시한다: cache={}, key={}, error={}",
					cache.getName(), key, exception.toString());
		}

		@Override
		public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
			log.warn("캐시 삭제 실패, 무시한다: cache={}, key={}, error={}",
					cache.getName(), key, exception.toString());
		}

		@Override
		public void handleCacheClearError(RuntimeException exception, Cache cache) {
			log.warn("캐시 비우기 실패, 무시한다: cache={}, error={}", cache.getName(), exception.toString());
		}
	}
}
