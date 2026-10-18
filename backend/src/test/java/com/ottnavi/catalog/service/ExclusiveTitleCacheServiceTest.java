package com.ottnavi.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ottnavi.catalog.service.model.ExclusiveTitleList;
import com.ottnavi.global.config.RedisConfig;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * 독점 목록 캐시 서비스(Task 078)의 Redis 장애 폴백과 캐시 미스 동시 요청 단일화를 Mockito로 확인한다(Docker 불필요).
 * 막으려는 사고: Redis가 죽었을 때 공개 API가 500이 되는 일, 캐시가 빈 순간 요청이 몰려 요청마다 전체 계산을 하는 일.
 */
class ExclusiveTitleCacheServiceTest {

	private static final long SERVICE_ID = 1L;

	private CacheManager cacheManager;
	private Cache cache;
	private ExclusiveTitleCalculator calculator;
	private ExclusiveTitleCacheService service;
	private ExclusiveTitleList calculated;

	@BeforeEach
	void 초기화() {
		cacheManager = mock(CacheManager.class);
		cache = mock(Cache.class);
		calculator = mock(ExclusiveTitleCalculator.class);
		when(cacheManager.getCache(RedisConfig.EXCLUSIVE_TITLES)).thenReturn(cache);
		calculated = new ExclusiveTitleList(SERVICE_ID, true, LocalDate.of(2026, 10, 18), List.of());
		when(calculator.calculateAll()).thenReturn(Map.of(SERVICE_ID, calculated));
		service = new ExclusiveTitleCacheService(cacheManager, calculator);
	}

	@Test
	void Redis_읽기가_실패해도_DB_계산_결과를_돌려준다() {
		// given: 읽기 예외
		when(cache.get(anyLong(), any(Class.class))).thenThrow(new IllegalStateException("redis down"));

		// when / then
		assertThat(service.get(SERVICE_ID)).isSameAs(calculated);
	}

	@Test
	void Redis_쓰기가_실패해도_DB_계산_결과를_돌려준다() {
		// given: 읽기는 미스, 쓰기는 예외
		when(cache.get(anyLong(), any(Class.class))).thenReturn(null);
		org.mockito.Mockito.doThrow(new IllegalStateException("redis down")).when(cache).put(any(), any());

		// when / then
		assertThat(service.get(SERVICE_ID)).isSameAs(calculated);
		verify(calculator, times(1)).calculateAll();
	}

	@Test
	void Redis_읽기와_쓰기가_모두_실패해도_DB_계산_결과를_돌려준다() {
		when(cache.get(anyLong(), any(Class.class))).thenThrow(new IllegalStateException("redis down"));
		org.mockito.Mockito.doThrow(new IllegalStateException("redis down")).when(cache).put(any(), any());

		assertThat(service.get(SERVICE_ID)).isSameAs(calculated);
	}

	@Test
	void 캐시_미스에_동시에_몰려도_계산은_한_번만_하고_모두_같은_결과를_받는다() throws Exception {
		// 1. 캐시는 맵으로 흉내 낸다: 첫 put 이후부터 get이 값을 돌려준다
		Map<Object, Object> store = new ConcurrentHashMap<>();
		when(cache.get(anyLong(), any(Class.class))).thenAnswer(inv -> store.get(inv.getArgument(0)));
		org.mockito.Mockito.doAnswer(inv -> {
			store.put(inv.getArgument(0), inv.getArgument(1));
			return null;
		}).when(cache).put(any(), any());

		// 2. 계산은 잠깐 걸리게 해 다른 스레드들이 락 앞에서 기다리게 한다
		when(calculator.calculateAll()).thenAnswer(inv -> {
			Thread.sleep(200);
			return Map.of(SERVICE_ID, calculated);
		});

		// 3. 여러 스레드가 동시에 출발한다
		int threads = 8;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<ExclusiveTitleList>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return service.get(SERVICE_ID);
				}));
			}
			ready.await(5, TimeUnit.SECONDS);
			start.countDown();

			// 4. 모두 같은 결과를 받고 계산은 한 번뿐이다
			for (Future<ExclusiveTitleList> future : futures) {
				assertThat(future.get(10, TimeUnit.SECONDS)).isSameAs(calculated);
			}
		} finally {
			executor.shutdownNow();
		}
		verify(calculator, times(1)).calculateAll();
	}
}
