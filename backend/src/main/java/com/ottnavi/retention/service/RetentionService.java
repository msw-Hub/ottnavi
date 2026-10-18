package com.ottnavi.retention.service;

import com.ottnavi.catalog.service.StaleTitleQueryService;
import com.ottnavi.catalog.service.model.StaleTitle;
import com.ottnavi.global.config.RedisConfig;
import com.ottnavi.home.service.HomeCacheEvictor;
import com.ottnavi.plan.service.PlanPurgeService;
import com.ottnavi.retention.job.RetentionProperties;
import com.ottnavi.retention.service.model.TitleRetentionResult;
import com.ottnavi.retention.service.model.TitleRetentionSummary;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * 데이터 정리 작업(Task 083)의 세 가지 일을 기준 시각 계산과 함께 묶는다. 2단계의 정리 Job이 Step마다 하나씩 부른다.
 * 1. 오래된 작품 점검(TMDB에 다시 묻고 갱신 또는 삭제) 2. 오래된 배치 이력 삭제 3. 오래된 계산 기록 삭제.
 * 클래스에 @Transactional을 붙이지 않는다(TMDB 호출이 있어 트랜잭션 밖이어야 하고, 삭제는 각 서비스가 자기 트랜잭션을 연다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

	private final RetentionProperties properties;
	private final StaleTitleQueryService staleTitleQueryService;
	private final TitleRetentionService titleRetentionService;
	private final BatchHistoryPurger batchHistoryPurger;
	private final PlanPurgeService planPurgeService;
	private final CacheManager cacheManager;
	private final HomeCacheEvictor homeCacheEvictor; // 삭제된 작품 카드가 홈 목록에 남지 않게 비운다(Task 107)
	private final Clock clock;

	/**
	 * 마지막 수집이 staleMonths보다 오래된 작품을 가장 오래된 순으로 최대 maxTitlesPerRun건 처리한다.
	 * 작품 하나의 실패는 error 로그만 남기고 다음 작품으로 넘어간다(DEFERRED로 센다).
	 */
	public TitleRetentionSummary retainTitles() {
		// 1. 기준 시각을 정하고 기준을 넘긴 작품 수를 센다(점검 쿼리)
		Instant cutoff = ZonedDateTime.ofInstant(clock.instant(), clock.getZone()).minusMonths(properties.staleMonths())
				.toInstant();
		long staleCount = staleTitleQueryService.countStale(cutoff);
		log.info("6개월 갱신 점검: 마지막 수집이 {}개월을 넘은 작품={}개, 이번 처리 상한={}개", properties.staleMonths(), staleCount,
				properties.maxTitlesPerRun());

		// 2. 가장 오래된 순으로 상한까지만 가져온다
		List<StaleTitle> targets = staleTitleQueryService.findStale(cutoff, properties.maxTitlesPerRun());

		// 3. 작품마다 처리하고 결과를 센다
		int refreshed = 0;
		int refreshSkipped = 0;
		int purged = 0;
		int deferred = 0;
		for (StaleTitle target : targets) {
			TitleRetentionResult result;
			try {
				result = titleRetentionService.handle(target);
			} catch (RuntimeException e) {
				log.error("오래된 작품 처리에 실패했다(다음 작품으로 넘어간다): titleId={}", target.titleId(), e);
				result = TitleRetentionResult.DEFERRED;
			}
			if (result == TitleRetentionResult.REFRESHED) {
				refreshed++;
			} else if (result == TitleRetentionResult.REFRESH_SKIPPED) {
				refreshSkipped++;
			} else if (result == TitleRetentionResult.PURGED) {
				purged++;
			} else {
				deferred++;
			}
		}

		// 4. 갱신·삭제가 있었으면 작품 상세 캐시와 홈·탐색 캐시를 비운다(삭제된 작품 카드가 남지 않게)
		if (refreshed > 0 || purged > 0) {
			evictTitleDetailCache();
			homeCacheEvictor.evictAll();
		}

		log.info("6개월 갱신 점검 종료: 점검={}건, 갱신={}건, 갱신 못 함={}건, 삭제={}건, 보류={}건", targets.size(), refreshed,
				refreshSkipped, purged, deferred);
		return new TitleRetentionSummary(staleCount, targets.size(), refreshed, refreshSkipped, purged, deferred);
	}

	/** 보관 기간(historyRetentionDays)이 지난 배치 이력을 지우고 지운 Job 인스턴스 수를 돌려준다 */
	public int purgeBatchHistory() {
		Instant cutoff = clock.instant().minus(Duration.ofDays(properties.historyRetentionDays()));
		// Batch 메타데이터 시각은 시간대 없는 값이라 JVM 기본 시간대로 맞춘다(CollectExecutionQuery와 같은 방식)
		return batchHistoryPurger.purgeEndedBefore(LocalDateTime.ofInstant(cutoff, ZoneId.systemDefault()));
	}

	/** 보관 기간이 지난, 플랜과 연결이 끊긴 계산 기록을 지우고 지운 건수를 돌려준다 */
	public int purgeCalcRuns() {
		Instant cutoff = clock.instant().minus(Duration.ofDays(properties.historyRetentionDays()));
		return planPurgeService.deleteOldDetachedCalcRuns(cutoff);
	}

	/** 작품 상세 캐시를 비운다. 실패해도 이미 끝난 정리를 바꾸지 않도록 경고만 남긴다(TTL 24시간 뒤 만료) */
	private void evictTitleDetailCache() {
		try {
			Cache cache = cacheManager.getCache(RedisConfig.TITLE_DETAIL);
			if (cache != null) {
				cache.clear();
			}
		} catch (RuntimeException e) {
			log.warn("작품 상세 캐시를 비우지 못했다(TTL 24시간 뒤 만료): error={}", e.toString());
		}
	}
}
