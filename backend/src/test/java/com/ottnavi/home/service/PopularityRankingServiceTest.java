package com.ottnavi.home.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ottnavi.home.domain.enums.PopularityPeriod;
import com.ottnavi.home.domain.rule.PopularFillTarget;
import com.ottnavi.home.service.model.PopularityRankingView;
import com.ottnavi.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 인기 순위 상위 작품 채우기 후보(findFillCandidates)를 실제 DB(Testcontainers)와 Redis 캐시로 확인한다(Docker Desktop 필요).
 * 막으려는 사고: 후보 순서가 뒤섞여 눈에 띄는 순위부터 채우지 못하는 일, 캐시에 남은 옛 순위로 후보를 만드는 일.
 */
class PopularityRankingServiceTest extends IntegrationTestSupport {

	private static final int BASE_TMDB_ID = 1_000_000; // 서비스 k의 작품은 BASE + k

	@Autowired private PopularityRankingService popularityRankingService;
	@Autowired private HomeCacheEvictor homeCacheEvictor;
	@Autowired private JdbcTemplate jdbcTemplate;

	private List<Long> serviceIds;

	@BeforeEach
	void 초기화() {
		jdbcTemplate.update("DELETE FROM popularity_snapshot");
		homeCacheEvictor.evictAll();
		serviceIds = jdbcTemplate.queryForList("SELECT id FROM ott_service ORDER BY display_order", Long.class);
	}

	@AfterEach
	void 정리() {
		jdbcTemplate.update("DELETE FROM popularity_snapshot");
		homeCacheEvictor.evictAll();
	}

	@Test
	void 후보는_전체_오늘_전체_7일_서비스1_오늘_서비스1_7일_순서의_16개_순위다() {
		// given: 서비스마다 작품 하나씩(서비스 k는 BASE + k, 인기도는 서비스 순서가 앞일수록 낮다)
		for (int k = 0; k < serviceIds.size(); k++) {
			insertSnapshot(serviceIds.get(k), BASE_TMDB_ID + k, 10 + k);
		}

		// when
		List<List<PopularFillTarget>> rankings = popularityRankingService.findFillCandidates(20);

		// then: 16개. 앞 둘은 전체(7개 작품, 인기도 높은 순), 그 뒤는 서비스 순서대로 TODAY, WEEK 쌍이다
		assertThat(rankings).hasSize(2 + 2 * serviceIds.size());
		assertThat(rankings.get(0)).hasSize(serviceIds.size());
		assertThat(rankings.get(0).get(0).tmdbId()).isEqualTo(BASE_TMDB_ID + serviceIds.size() - 1);
		assertThat(rankings.get(1)).isEqualTo(rankings.get(0));
		for (int k = 0; k < serviceIds.size(); k++) {
			assertThat(rankings.get(2 + 2 * k)).extracting(PopularFillTarget::tmdbId).containsExactly(BASE_TMDB_ID + k);
			assertThat(rankings.get(3 + 2 * k)).extracting(PopularFillTarget::tmdbId).containsExactly(BASE_TMDB_ID + k);
		}
	}

	@Test
	void 캐시에_옛_순위가_있어도_후보는_방금_쌓은_스냅샷으로_계산한다() {
		// given: 순위를 한 번 조회해 캐시에 옛 순위(작품 1개)를 채운 뒤, 더 인기 있는 작품 스냅샷이 새로 쌓인다
		insertSnapshot(serviceIds.get(0), BASE_TMDB_ID, 10);
		PopularityRankingView cachedBefore = popularityRankingService.rank(PopularityPeriod.TODAY, null, 20);
		assertThat(cachedBefore.items()).hasSize(1);
		insertSnapshot(serviceIds.get(1), BASE_TMDB_ID + 1, 99);

		// when
		PopularityRankingView cachedAfter = popularityRankingService.rank(PopularityPeriod.TODAY, null, 20);
		List<List<PopularFillTarget>> rankings = popularityRankingService.findFillCandidates(20);

		// then: 화면용 순위는 캐시된 옛 값 그대로이고, 후보는 새 작품을 맨 앞에 가진다
		assertThat(cachedAfter.items()).hasSize(1);
		assertThat(rankings.get(0)).extracting(PopularFillTarget::tmdbId).containsExactly(BASE_TMDB_ID + 1, BASE_TMDB_ID);
	}

	private void insertSnapshot(long ottServiceId, int tmdbId, int popularity) {
		jdbcTemplate.update("INSERT INTO popularity_snapshot (snapshot_date, ott_service_id, media_type, tmdb_id, title, popularity) "
				+ "VALUES (current_date, ?, 'MOVIE', ?, '테스트', ?)", ottServiceId, tmdbId, popularity);
	}
}
