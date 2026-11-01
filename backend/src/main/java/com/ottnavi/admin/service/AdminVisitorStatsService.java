package com.ottnavi.admin.service;

import com.ottnavi.admin.service.model.AdminVisitorStatsView;
import com.ottnavi.admin.service.model.VisitorStatsCacheValue;
import com.ottnavi.admin.service.model.VisitorStatsStatus;
import com.ottnavi.global.config.RedisConfig;
import com.ottnavi.infra.analytics.AnalyticsProperties;
import com.ottnavi.infra.analytics.GaDataClient;
import com.ottnavi.infra.analytics.VisitorCounts;
import com.ottnavi.infra.analytics.exception.AnalyticsCredentialsException;
import com.ottnavi.infra.analytics.exception.AnalyticsUnavailableException;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * 관리자 방문자 통계: Google Analytics의 동의한 방문자 수(activeUsers)를 읽어 15분 캐시한다.
 * 이 기능은 DB를 쓰지 않고 외부 호출이 있어 트랜잭션을 걸지 않는다.
 * 캐시는 @Cacheable이 아니라 CacheManager로 직접 읽고 쓴다. 실패(UNAVAILABLE)를 캐시에 저장하지 않기 위해서다.
 * 로그에는 원인 종류만 남기고 속성 ID·키·토큰·응답 본문은 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminVisitorStatsService {

	private static final String CACHE_KEY = "current"; // 값이 하나뿐이라 고정 키

	private final AnalyticsProperties analyticsProperties;
	private final GaDataClient gaDataClient;
	private final CacheManager cacheManager;
	private final Clock clock; // KST 기준 시계(ClockConfig). 테스트에서 고정할 수 있다

	/** 방문자 통계를 돌려준다. 연동이 없으면 DISABLED, 조회에 실패하면 UNAVAILABLE이며 예외는 던지지 않는다 */
	public AdminVisitorStatsView getVisitorStats() {
		// 1. 연동 설정이 없으면 호출 없이 DISABLED
		if (!analyticsProperties.isEnabled()) {
			return AdminVisitorStatsView.disabled();
		}

		// 2. 캐시에 있으면 그대로 돌려준다(fetchedAt은 캐시를 만든 시각)
		VisitorStatsCacheValue cached = readCache();
		if (cached != null) {
			return toView(cached);
		}

		// 3. 없으면 GA에서 읽는다. 실패는 UNAVAILABLE로 알리고 캐시하지 않는다
		VisitorCounts counts;
		try {
			counts = gaDataClient.fetchVisitorCounts();
		} catch (AnalyticsCredentialsException e) {
			log.error("방문자 통계 조회 실패(인증 정보 오류): {}", e.getMessage());
			return AdminVisitorStatsView.unavailable();
		} catch (AnalyticsUnavailableException e) {
			log.warn("방문자 통계 조회 실패: {}", e.getMessage());
			return AdminVisitorStatsView.unavailable();
		} catch (RuntimeException e) {
			log.error("방문자 통계 조회 중 예상하지 못한 오류: 원인 종류={}", e.getClass().getSimpleName());
			return AdminVisitorStatsView.unavailable();
		}

		// 4. 성공하면 호출 성공 시각을 붙여 캐시에 저장하고 돌려준다
		VisitorStatsCacheValue value = new VisitorStatsCacheValue(counts.today(), counts.yesterday(),
				counts.last7Days(), counts.last30Days(), clock.instant());
		writeCache(value);
		return toView(value);
	}

	/** 캐시를 읽는다. Redis 오류면 경고만 남기고 없는 것으로 본다(통계를 못 보여 주는 것보다 GA를 직접 부르는 편이 낫다) */
	private VisitorStatsCacheValue readCache() {
		try {
			Cache cache = cacheManager.getCache(RedisConfig.VISITOR_STATS);
			if (cache == null) {
				return null;
			}
			return cache.get(CACHE_KEY, VisitorStatsCacheValue.class);
		} catch (RuntimeException e) {
			log.warn("방문자 통계 캐시를 읽지 못했다: 원인 종류={}", e.getClass().getSimpleName());
			return null;
		}
	}

	/** 캐시에 저장한다. 저장에 실패해도 이번 응답에는 영향이 없다 */
	private void writeCache(VisitorStatsCacheValue value) {
		try {
			Cache cache = cacheManager.getCache(RedisConfig.VISITOR_STATS);
			if (cache != null) {
				cache.put(CACHE_KEY, value);
			}
		} catch (RuntimeException e) {
			log.warn("방문자 통계 캐시에 저장하지 못했다: 원인 종류={}", e.getClass().getSimpleName());
		}
	}

	private AdminVisitorStatsView toView(VisitorStatsCacheValue value) {
		OffsetDateTime fetchedAt = value.fetchedAt().atZone(clock.getZone()).toOffsetDateTime();
		return new AdminVisitorStatsView(VisitorStatsStatus.OK, value.todayVisitorCount(), value.yesterdayVisitorCount(),
				value.last7DaysVisitorCount(), value.last30DaysVisitorCount(), fetchedAt);
	}
}
