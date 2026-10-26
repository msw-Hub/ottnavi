package com.ottnavi.retention.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.service.ExclusiveTitleCacheService;
import com.ottnavi.catalog.service.StaleTitleQueryService;
import com.ottnavi.catalog.service.model.StaleTitle;
import com.ottnavi.home.service.HomeCacheEvictor;
import com.ottnavi.plan.service.PlanPurgeService;
import com.ottnavi.retention.job.RetentionProperties;
import com.ottnavi.retention.service.model.TitleRetentionResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

/** 정리 Job의 작품 점검이 작품을 지운 뒤 홈·탐색 캐시를 비우는지 확인하는 단위 테스트(Task 107) */
class RetentionHomeCacheEvictTest {

	private final StaleTitleQueryService staleTitleQueryService = mock(StaleTitleQueryService.class);
	private final TitleRetentionService titleRetentionService = mock(TitleRetentionService.class);
	private final HomeCacheEvictor homeCacheEvictor = mock(HomeCacheEvictor.class);
	private final ExclusiveTitleCacheService exclusiveTitleCacheService = mock(ExclusiveTitleCacheService.class);
	private final RetentionService service = new RetentionService(new RetentionProperties(5, 180, 500),
			staleTitleQueryService, titleRetentionService, mock(BatchHistoryPurger.class), mock(PlanPurgeService.class),
			mock(CacheManager.class), homeCacheEvictor, exclusiveTitleCacheService, Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneId.of("Asia/Seoul")));

	@Test
	void 작품을_삭제했으면_홈_캐시를_비운다() {
		// given
		StaleTitle target = new StaleTitle(1L, MediaType.MOVIE, 100);
		when(staleTitleQueryService.findStale(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(target));
		when(titleRetentionService.handle(target)).thenReturn(TitleRetentionResult.PURGED);

		// when
		service.retainTitles();

		// then
		verify(homeCacheEvictor).evictAll();
		verify(exclusiveTitleCacheService).evictAll();
	}

	@Test
	void 갱신도_삭제도_없으면_홈_캐시를_건드리지_않는다() {
		// given
		StaleTitle target = new StaleTitle(1L, MediaType.MOVIE, 100);
		when(staleTitleQueryService.findStale(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(target));
		when(titleRetentionService.handle(target)).thenReturn(TitleRetentionResult.DEFERRED);

		// when
		service.retainTitles();

		// then
		verify(homeCacheEvictor, never()).evictAll();
		verify(exclusiveTitleCacheService, never()).evictAll();
	}
}
