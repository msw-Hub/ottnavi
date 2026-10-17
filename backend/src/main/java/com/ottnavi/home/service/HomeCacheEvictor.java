package com.ottnavi.home.service;

import com.ottnavi.global.config.RedisConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * 홈·탐색 응답 캐시(Task 107)를 비운다. 수집·인기 수집·정리 Job이 끝났을 때 호출한다.
 * 비우기에 실패해도 이미 끝난 Job의 결과를 바꾸지 않도록 예외를 올리지 않고 경고만 남긴다(TTL이 지나면 저절로 새 값이 된다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HomeCacheEvictor {

	private final CacheManager cacheManager;

	/** 홈 캐시 5개를 모두 비운다. 작품의 제공 상태나 존재가 바뀐 뒤(수집·정리 Job 종료)에 쓴다 */
	public void evictAll() {
		for (String name : RedisConfig.HOME_CACHES) {
			evict(name);
		}
	}

	/** 인기 순위 캐시만 비운다. 인기 수집 Job이 날짜별 인기 순위 기록을 새로 쌓은 뒤에 쓴다 */
	public void evictPopularity() {
		evict(RedisConfig.HOME_POPULARITY);
	}

	private void evict(String cacheName) {
		try {
			Cache cache = cacheManager.getCache(cacheName);
			if (cache != null) {
				cache.clear();
			}
		} catch (RuntimeException e) {
			log.warn("홈 캐시를 비우지 못했다(TTL이 지나면 만료된다): cache={}, error={}", cacheName, e.toString());
		}
	}
}
