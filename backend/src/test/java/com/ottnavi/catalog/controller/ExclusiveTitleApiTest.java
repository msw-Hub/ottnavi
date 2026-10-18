package com.ottnavi.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.collect.job.ExclusiveTitlesRefreshListener;
import com.ottnavi.global.config.RedisConfig;
import com.ottnavi.support.IntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 서비스별 독점 목록 공개 API(Task 078)를 실제 DB·Redis(Testcontainers)와 MockMvc로 확인한다(Docker Desktop 필요).
 * 막으려는 사고: 다른 서비스에도 있는 작품이 독점으로 나오는 일, 드라마의 독점 아닌 시즌이 섞이는 일, 데이터 부족 서비스(쿠팡플레이)가 목록을 보이는 일,
 * 없는 서비스가 200으로 나가는 일, 캐시가 안 걸리거나 수집이 끝나도 옛 목록이 남는 일, 캐시가 비었을 때 빈 목록을 돌려주는 일.
 * 이 클래스의 작품은 TMDB ID 9600000번대를 쓰고, 인기도를 매우 크게 줘 목록 맨 위에 오게 한다(다른 테스트의 남은 데이터와 섞여도 순서를 확인할 수 있다).
 */
@AutoConfigureMockMvc
class ExclusiveTitleApiTest extends IntegrationTestSupport {

	private static final int MOVIE_A = 9600001; // 넷플릭스 독점 영화(웨이브는 모름)
	private static final int TV_B = 9600002; // 시즌 1·2는 넷플릭스 독점, 시즌 3은 디즈니+에도 있음
	private static final int MOVIE_C = 9600003; // 넷플릭스와 티빙 모두 있음(독점 아님)
	private static final int TV_D = 9600004; // 쿠팡플레이에만 있음(데이터 부족 서비스라 어느 목록에도 안 나온다)

	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private CacheManager cacheManager;
	@Autowired private ExclusiveTitlesRefreshListener refreshListener;

	private Map<String, Long> serviceIds;

	@BeforeEach
	void 초기화() {
		cleanUp();
		serviceIds = new java.util.HashMap<>();
		jdbcTemplate.query("SELECT id, code FROM ott_service", rs -> {
			serviceIds.put(rs.getString("code"), rs.getLong("id"));
		});

		// A: 넷플릭스에만 있음. 웨이브는 KR 데이터 없음(모름), 쿠팡플레이는 영화라 구조적 공백
		long unitA = movie(MOVIE_A, "독점 영화 A", "900000");
		availability(unitA, "NETFLIX", "AVAILABLE", null);
		availability(unitA, "WAVVE", "UNKNOWN", "NO_KR_DATA");
		availability(unitA, "COUPANG_PLAY", "UNKNOWN", "STRUCTURAL_GAP");
		fillRest(unitA);

		// B: 시즌 1·2는 넷플릭스만, 시즌 3은 넷플릭스와 디즈니+
		long titleB = title("TV", TV_B, "독점 드라마 B", "800000");
		for (int season = 1; season <= 3; season++) {
			long unit = unit(titleB, "SEASON", season);
			availability(unit, "NETFLIX", "AVAILABLE", null);
			if (season == 3) {
				availability(unit, "DISNEY_PLUS", "AVAILABLE", null);
			}
			fillRest(unit);
		}

		// C: 넷플릭스와 티빙 모두 있음
		long unitC = movie(MOVIE_C, "공동 영화 C", "700000");
		availability(unitC, "NETFLIX", "AVAILABLE", null);
		availability(unitC, "TVING", "AVAILABLE", null);
		availability(unitC, "COUPANG_PLAY", "UNKNOWN", "STRUCTURAL_GAP");
		fillRest(unitC);

		// D: 쿠팡플레이 드라마
		long titleD = title("TV", TV_D, "쿠팡 드라마 D", "600000");
		long unitD = unit(titleD, "SEASON", 1);
		availability(unitD, "COUPANG_PLAY", "AVAILABLE", null);
		fillRest(unitD);

		cacheManager.getCache(RedisConfig.EXCLUSIVE_TITLES).clear();
	}

	@AfterEach
	void 정리() {
		cleanUp();
	}

	private void cleanUp() {
		String myUnits = "(SELECT u.id FROM watch_unit u JOIN title t ON t.id = u.title_id WHERE t.tmdb_id BETWEEN 9600000 AND 9699999)";
		jdbcTemplate.update("DELETE FROM availability WHERE watch_unit_id IN " + myUnits);
		jdbcTemplate.update("DELETE FROM watch_unit WHERE title_id IN (SELECT id FROM title WHERE tmdb_id BETWEEN 9600000 AND 9699999)");
		jdbcTemplate.update("DELETE FROM title WHERE tmdb_id BETWEEN 9600000 AND 9699999");
		cacheManager.getCache(RedisConfig.EXCLUSIVE_TITLES).clear();
	}

	@Test
	void 넷플릭스_독점_목록은_인기도_순이고_다른_서비스에도_있는_작품과_시즌은_빠진다() throws Exception {
		mockMvc.perform(get("/api/public/ott-services/" + serviceIds.get("NETFLIX") + "/exclusive-titles"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.ottServiceId").value(serviceIds.get("NETFLIX")))
				.andExpect(jsonPath("$.data.isJudged").value(true))
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(20))
				.andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(2)))
				// A: 영화, 시즌 번호 없음, 비교 5곳(구조적 공백 제외) 중 모름 1곳
				.andExpect(jsonPath("$.data.content[0].mediaType").value("movie"))
				.andExpect(jsonPath("$.data.content[0].tmdbId").value(MOVIE_A))
				.andExpect(jsonPath("$.data.content[0].exclusiveSeasonNumbers").value((Object) null))
				.andExpect(jsonPath("$.data.content[0].comparedServiceCount").value(5))
				.andExpect(jsonPath("$.data.content[0].unknownElsewhereCount").value(1))
				// B: 드라마, 독점 시즌은 1·2만, 비교 6곳
				.andExpect(jsonPath("$.data.content[1].mediaType").value("tv"))
				.andExpect(jsonPath("$.data.content[1].tmdbId").value(TV_B))
				.andExpect(jsonPath("$.data.content[1].exclusiveSeasonNumbers[0]").value(1))
				.andExpect(jsonPath("$.data.content[1].exclusiveSeasonNumbers[1]").value(2))
				.andExpect(jsonPath("$.data.content[1].exclusiveSeasonNumbers", hasSize(2)))
				.andExpect(jsonPath("$.data.content[1].comparedServiceCount").value(6))
				// C(티빙에도 있음)와 D(쿠팡플레이 전용)는 없다
				.andExpect(jsonPath("$.data.content[?(@.tmdbId==" + MOVIE_C + ")]", hasSize(0)))
				.andExpect(jsonPath("$.data.content[?(@.tmdbId==" + TV_D + ")]", hasSize(0)));

		// 페이지 자르기: size=1, page=1이면 두 번째 작품(B)
		mockMvc.perform(get("/api/public/ott-services/" + serviceIds.get("NETFLIX") + "/exclusive-titles")
						.param("page", "1").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content", hasSize(1)))
				.andExpect(jsonPath("$.data.content[0].tmdbId").value(TV_B))
				.andExpect(jsonPath("$.data.page").value(1))
				.andExpect(jsonPath("$.data.size").value(1));
	}

	@Test
	void 쿠팡플레이는_데이터_부족이라_판정하지_않고_빈_목록을_200으로_돌려준다() throws Exception {
		mockMvc.perform(get("/api/public/ott-services/" + serviceIds.get("COUPANG_PLAY") + "/exclusive-titles"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isJudged").value(false))
				.andExpect(jsonPath("$.data.content", hasSize(0)))
				.andExpect(jsonPath("$.data.totalElements").value(0))
				.andExpect(jsonPath("$.data.totalPages").value(0));
	}

	@Test
	void 없는_서비스는_404_OTT_SERVICE_NOT_FOUND이고_범위_밖_값은_400이다() throws Exception {
		mockMvc.perform(get("/api/public/ott-services/999999/exclusive-titles"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("OTT_SERVICE_NOT_FOUND"));
		mockMvc.perform(get("/api/public/ott-services/0/exclusive-titles"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
		mockMvc.perform(get("/api/public/ott-services/" + serviceIds.get("NETFLIX") + "/exclusive-titles")
						.param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
	}

	@Test
	void 캐시가_있으면_DB가_바뀌어도_그대로이고_수집_성공_뒤에_새로_채우며_실패하면_비운다() throws Exception {
		String url = "/api/public/ott-services/" + serviceIds.get("NETFLIX") + "/exclusive-titles";
		Cache cache = cacheManager.getCache(RedisConfig.EXCLUSIVE_TITLES);

		// given: 첫 조회로 캐시가 채워진다(캐시 미스 폴백: 7개 서비스를 모두 채운다)
		mockMvc.perform(get(url)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[?(@.tmdbId==" + MOVIE_A + ")]", hasSize(1)));
		assertThat(cache.get(serviceIds.get("NETFLIX"))).isNotNull();
		assertThat(cache.get(serviceIds.get("DISNEY_PLUS"))).isNotNull();

		// when: A가 티빙에도 생겼지만(독점 아님) 캐시는 그대로다
		availability(unitIdOf(MOVIE_A), "TVING", "AVAILABLE", null, true);
		mockMvc.perform(get(url)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[?(@.tmdbId==" + MOVIE_A + ")]", hasSize(1)));

		// when: 수집이 성공으로 끝나면 새로 계산해 채운다
		JobExecution completed = mock(JobExecution.class);
		when(completed.getStatus()).thenReturn(BatchStatus.COMPLETED);
		refreshListener.afterJob(completed);

		// then: 캐시가 미리 채워져 있고(조회 전에 이미 값이 있다) A는 빠졌다
		assertThat(cache.get(serviceIds.get("NETFLIX"))).isNotNull();
		mockMvc.perform(get(url)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[?(@.tmdbId==" + MOVIE_A + ")]", hasSize(0)))
				.andExpect(jsonPath("$.data.content[?(@.tmdbId==" + TV_B + ")]", hasSize(1)));

		// when: 수집이 실패로 끝나면 비운다
		JobExecution failed = mock(JobExecution.class);
		when(failed.getStatus()).thenReturn(BatchStatus.FAILED);
		refreshListener.afterJob(failed);

		// then: 캐시가 비었지만 다음 조회가 즉석에서 계산해 목록을 준다
		assertThat(cache.get(serviceIds.get("NETFLIX"))).isNull();
		mockMvc.perform(get(url)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content", not(hasSize(0))));
	}

	private long title(String mediaType, int tmdbId, String name, String popularity) {
		return jdbcTemplate.queryForObject("INSERT INTO title (media_type, tmdb_id, title, popularity, fetched_at) "
				+ "VALUES (?, ?, ?, ?::numeric, now()) RETURNING id", Long.class, mediaType, tmdbId, name, popularity);
	}

	private long unit(long titleId, String unitType, Integer seasonNumber) {
		return jdbcTemplate.queryForObject("INSERT INTO watch_unit (title_id, unit_type, season_number, "
				+ "total_runtime_min, fetched_at) VALUES (?, ?, ?, 100, now()) RETURNING id", Long.class, titleId,
				unitType, seasonNumber);
	}

	private long movie(int tmdbId, String name, String popularity) {
		return unit(title("MOVIE", tmdbId, name, popularity), "MOVIE", null);
	}

	private long unitIdOf(int tmdbId) {
		return jdbcTemplate.queryForObject("SELECT u.id FROM watch_unit u JOIN title t ON t.id = u.title_id "
				+ "WHERE t.tmdb_id = ? LIMIT 1", Long.class, tmdbId);
	}

	private void availability(long unitId, String serviceCode, String status, String reason) {
		availability(unitId, serviceCode, status, reason, false);
	}

	/** 제공 상태 한 행을 넣는다. replace가 true면 이미 있는 행을 지우고 다시 넣는다 */
	private void availability(long unitId, String serviceCode, String status, String reason, boolean replace) {
		if (replace) {
			jdbcTemplate.update("DELETE FROM availability WHERE watch_unit_id = ? AND ott_service_id = ?", unitId,
					serviceIds.get(serviceCode));
		}
		jdbcTemplate.update("INSERT INTO availability (watch_unit_id, ott_service_id, status, unknown_reason, in_standard, "
				+ "checked_at) VALUES (?, ?, ?, ?, ?, now())", unitId, serviceIds.get(serviceCode), status, reason,
				"AVAILABLE".equals(status));
	}

	/** 아직 행이 없는 서비스를 없음(NOT_AVAILABLE)으로 채워 7곳 모두 판정이 있는 상태로 만든다 */
	private void fillRest(long unitId) {
		for (Map.Entry<String, Long> entry : serviceIds.entrySet()) {
			Integer count = jdbcTemplate.queryForObject(
					"SELECT count(*) FROM availability WHERE watch_unit_id = ? AND ott_service_id = ?", Integer.class,
					unitId, entry.getValue());
			if (count == 0) {
				availability(unitId, entry.getKey(), "NOT_AVAILABLE", null);
			}
		}
	}
}
