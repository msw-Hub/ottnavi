package com.ottnavi.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ottnavi.admin.service.model.AdminVisitorStatsView;
import com.ottnavi.admin.service.model.VisitorStatsStatus;
import com.ottnavi.global.config.RedisConfig;
import com.ottnavi.infra.analytics.AnalyticsProperties;
import com.ottnavi.infra.analytics.GaDataClient;
import com.ottnavi.infra.analytics.VisitorCounts;
import com.ottnavi.infra.analytics.exception.AnalyticsUnavailableException;
import com.ottnavi.support.IntegrationTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

/**
 * 관리자 방문자 통계의 캐시·상태 판정을 실제 Redis(Testcontainers)로 확인한다(Docker Desktop 필요).
 * GA 호출(GaDataClient)은 목으로 바꿔 실제 구글을 부르지 않는다. 시각은 고정 Clock만 쓰고 실제 현재 시각에 기대지 않는다.
 * 막으려는 사고: 연동이 없는데 GA를 부르는 일, 캐시 안에서 GA를 또 부르는 일, 실패가 캐시에 남아 15분 동안 계속 실패로 보이는 일,
 * 캐시된 값의 fetchedAt이 매번 바뀌는 일.
 */
class AdminVisitorStatsServiceTest extends IntegrationTestSupport {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final Instant FIRST_CALL = Instant.parse("2026-11-01T01:30:00Z");   // 10:30 KST

	@Autowired
	private CacheManager cacheManager;

	private final GaDataClient gaDataClient = mock(GaDataClient.class);

	private final AnalyticsProperties enabledProps = new AnalyticsProperties("123456789", "dGVzdA==", "http://x", Duration.ofMinutes(15));
	private final AnalyticsProperties disabledProps = new AnalyticsProperties("", "", "http://x", Duration.ofMinutes(15));

	@BeforeEach
	void 캐시를_비운다() {
		cacheManager.getCache(RedisConfig.VISITOR_STATS).clear();
	}

	private AdminVisitorStatsService service(AnalyticsProperties props, Instant now) {
		return new AdminVisitorStatsService(props, gaDataClient, cacheManager, Clock.fixed(now, KST));
	}

	@Test
	void 연동이_없으면_GA를_부르지_않고_DISABLED이다() {
		// given
		AdminVisitorStatsService service = service(disabledProps, FIRST_CALL);

		// when
		AdminVisitorStatsView view = service.getVisitorStats();

		// then
		assertThat(view.status()).isEqualTo(VisitorStatsStatus.DISABLED);
		assertThat(view.todayVisitorCount()).isNull();
		assertThat(view.fetchedAt()).isNull();
		verifyNoInteractions(gaDataClient);
	}

	@Test
	void 성공하면_OK이고_다시_부르면_GA_재호출_없이_같은_fetchedAt의_캐시를_돌려준다() {
		// given
		when(gaDataClient.fetchVisitorCounts()).thenReturn(new VisitorCounts(12, 30, 150, 480));

		// when: 첫 호출, 그리고 10분 뒤 다른 서비스 인스턴스(같은 Redis 캐시)로 두 번째 호출
		AdminVisitorStatsView first = service(enabledProps, FIRST_CALL).getVisitorStats();
		AdminVisitorStatsView second = service(enabledProps, FIRST_CALL.plusSeconds(600)).getVisitorStats();

		// then
		OffsetDateTime expectedFetchedAt = FIRST_CALL.atZone(KST).toOffsetDateTime();
		assertThat(first.status()).isEqualTo(VisitorStatsStatus.OK);
		assertThat(first.todayVisitorCount()).isEqualTo(12L);
		assertThat(first.yesterdayVisitorCount()).isEqualTo(30L);
		assertThat(first.last7DaysVisitorCount()).isEqualTo(150L);
		assertThat(first.last30DaysVisitorCount()).isEqualTo(480L);
		assertThat(first.fetchedAt()).isEqualTo(expectedFetchedAt);
		assertThat(second).isEqualTo(first);   // 숫자와 fetchedAt 모두 캐시를 만든 시각 그대로
		verify(gaDataClient, times(1)).fetchVisitorCounts();
	}

	@Test
	void 실패하면_UNAVAILABLE이고_숫자와_fetchedAt이_null이며_다음_호출에서_GA를_다시_부른다() {
		// given: 첫 호출은 실패, 두 번째는 성공
		when(gaDataClient.fetchVisitorCounts())
				.thenThrow(new AnalyticsUnavailableException("GA 장애"))
				.thenReturn(new VisitorCounts(1, 2, 3, 4));

		// when
		AdminVisitorStatsView failed = service(enabledProps, FIRST_CALL).getVisitorStats();
		AdminVisitorStatsView recovered = service(enabledProps, FIRST_CALL.plusSeconds(60)).getVisitorStats();

		// then
		assertThat(failed.status()).isEqualTo(VisitorStatsStatus.UNAVAILABLE);
		assertThat(failed.todayVisitorCount()).isNull();
		assertThat(failed.yesterdayVisitorCount()).isNull();
		assertThat(failed.last7DaysVisitorCount()).isNull();
		assertThat(failed.last30DaysVisitorCount()).isNull();
		assertThat(failed.fetchedAt()).isNull();
		assertThat(recovered.status()).isEqualTo(VisitorStatsStatus.OK);   // 실패가 캐시에 남지 않았다
		verify(gaDataClient, times(2)).fetchVisitorCounts();
	}
}
