package com.ottnavi.home.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.config.RedisConfig;
import com.ottnavi.home.job.HomeCacheEvictListener;
import com.ottnavi.home.job.PopularityCacheEvictListener;
import com.ottnavi.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 홈·탐색 응답 캐시(Redis)와 브라우저 캐시 헤더(Task 107)를 실제 DB·Redis(Testcontainers)와 MockMvc로 확인한다(Docker Desktop 필요).
 * 막으려는 사고: 캐시가 안 걸려 매 요청이 DB를 읽는 일, 수집·인기 수집이 끝났는데 옛 목록이 남는 일,
 * 출연자 이름마다 캐시 키가 쌓이는 일, 공개 목록 5개에만 붙어야 할 캐시 헤더가 다른 응답에 새거나 Spring Security가 지우는 일.
 * 이 클래스의 작품은 TMDB ID 9400000번대, 장르는 99101·99102를 쓴다.
 */
@AutoConfigureMockMvc
class HomeCacheApiTest extends IntegrationTestSupport {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final int GENRE_A = 99101;
	private static final int GENRE_B = 99102;

	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private CacheManager cacheManager;
	@Autowired private StringRedisTemplate redis;
	@Autowired private HomeCacheEvictListener homeCacheEvictListener;
	@Autowired private PopularityCacheEvictListener popularityCacheEvictListener;

	private LocalDate today;
	private long netflixId;

	@BeforeEach
	void 초기화() {
		cleanUp();
		today = LocalDate.now(KST);
		netflixId = jdbcTemplate.queryForObject("SELECT id FROM ott_service WHERE code = 'NETFLIX'", Long.class);
	}

	@AfterEach
	void 정리() {
		cleanUp();
	}

	private void cleanUp() {
		String myTitles = "(SELECT id FROM title WHERE tmdb_id BETWEEN 9400000 AND 9499999)";
		jdbcTemplate.update("DELETE FROM title_cast WHERE title_id IN " + myTitles);
		jdbcTemplate.update("DELETE FROM title_genre WHERE title_id IN " + myTitles);
		jdbcTemplate.update("DELETE FROM title WHERE tmdb_id BETWEEN 9400000 AND 9499999");
		jdbcTemplate.update("DELETE FROM genre WHERE id IN (" + GENRE_A + ", " + GENRE_B + ")");
		jdbcTemplate.update("DELETE FROM popularity_snapshot");
		// 다른 테스트가 남긴 캐시와 이 테스트가 만든 캐시가 섞이지 않게 홈 캐시를 모두 비운다
		for (String name : RedisConfig.HOME_CACHES) {
			Cache cache = cacheManager.getCache(name);
			cache.clear();
		}
	}

	// 캐시 적중과 비우기

	@Test
	void 인기_순위는_두_번째_요청부터_캐시를_준다_수집_종료_뒤에는_새_값이다() throws Exception {
		// given: 스냅샷 하나를 두고 한 번 조회해 캐시를 채운다
		insertSnapshot(9400001, "인기 A");
		requestPopularityItemCount(1);

		// when: DB에 스냅샷을 더 넣어도
		insertSnapshot(9400002, "인기 B");

		// then: 두 번째 요청은 캐시된 값(1건)이다
		requestPopularityItemCount(1);

		// when: 인기 수집 Job이 끝난 뒤에는
		popularityCacheEvictListener.afterJob(mock(JobExecution.class));

		// then: 새 값(2건)이 나온다
		requestPopularityItemCount(2);
	}

	@Test
	void 장르_목록은_두_번째_요청부터_캐시를_준다_수집_종료_뒤에는_새_값이다() throws Exception {
		// given
		insertGenreWithTitle(GENRE_A, "캐시장르A", 9400011);
		mockMvc.perform(get("/api/public/genres"))
				.andExpect(jsonPath("$.data[?(@.genreId==" + GENRE_A + ")]").isNotEmpty());

		// when: 새 장르가 생겨도
		insertGenreWithTitle(GENRE_B, "캐시장르B", 9400012);

		// then: 캐시된 목록에는 아직 없다
		mockMvc.perform(get("/api/public/genres"))
				.andExpect(jsonPath("$.data[?(@.genreId==" + GENRE_B + ")]").isEmpty());

		// when: 수집 Job이 끝난 뒤에는
		homeCacheEvictListener.afterJob(mock(JobExecution.class));

		// then: 새 장르가 보인다
		mockMvc.perform(get("/api/public/genres"))
				.andExpect(jsonPath("$.data[?(@.genreId==" + GENRE_B + ")]").isNotEmpty());
	}

	@Test
	void 수집_종료_리스너는_홈_캐시_5개를_모두_비운다() throws Exception {
		// given: 5개 공개 목록을 한 번씩 조회해 캐시를 채운다
		insertGenreWithTitle(GENRE_A, "캐시장르A", 9400021);
		requestAllFiveLists();
		for (String name : RedisConfig.HOME_CACHES) {
			assertThat(redis.keys(name + "::*")).as("%s 채워짐", name).isNotEmpty();
		}

		// when
		homeCacheEvictListener.afterJob(mock(JobExecution.class));

		// then
		for (String name : RedisConfig.HOME_CACHES) {
			assertThat(redis.keys(name + "::*")).as("%s 비워짐", name).isEmpty();
		}
	}

	@Test
	void 인기_수집_종료_리스너는_인기_순위_캐시만_비운다() throws Exception {
		// given
		insertGenreWithTitle(GENRE_A, "캐시장르A", 9400031);
		requestAllFiveLists();

		// when
		popularityCacheEvictListener.afterJob(mock(JobExecution.class));

		// then
		assertThat(redis.keys(RedisConfig.HOME_POPULARITY + "::*")).isEmpty();
		assertThat(redis.keys(RedisConfig.HOME_GENRES + "::*")).isNotEmpty();
		assertThat(redis.keys(RedisConfig.HOME_NEW_ARRIVALS + "::*")).isNotEmpty();
	}

	@Test
	void 캐시_키에는_사용자_정보가_없고_전체_서비스는_all로_쓴다() throws Exception {
		// given, when
		mockMvc.perform(get("/api/public/popularity-rankings").param("period", "WEEK").param("limit", "5"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/public/newly-available-titles").param("limit", "7")).andExpect(status().isOk());

		// then
		assertThat(redis.keys(RedisConfig.HOME_POPULARITY + "::*")).containsExactly("homePopularity::WEEK:all:5");
		assertThat(redis.keys(RedisConfig.HOME_NEW_ARRIVALS + "::*")).containsExactly("homeNewArrivals::all:7");
	}

	@Test
	void 출연자별_작품은_캐시하지_않는다() throws Exception {
		// given: 한 번 조회한다
		long titleA = insertTitle("MOVIE", 9400041, "출연작 A");
		insertCast(titleA, 0, "Cache Person");
		mockMvc.perform(get("/api/public/people/titles").param("name", "Cache Person"))
				.andExpect(jsonPath("$.data.totalElements").value(1));

		// when: 같은 출연자의 작품이 늘어나면
		long titleB = insertTitle("MOVIE", 9400042, "출연작 B");
		insertCast(titleB, 0, "Cache Person");

		// then: 바로 반영되고 어떤 홈 캐시 키도 생기지 않는다
		mockMvc.perform(get("/api/public/people/titles").param("name", "Cache Person"))
				.andExpect(jsonPath("$.data.totalElements").value(2));
		for (String name : RedisConfig.HOME_CACHES) {
			assertThat(redis.keys(name + "::*")).as(name).isEmpty();
		}
	}

	// 브라우저 캐시 헤더

	@Test
	void 공개_목록_5개에는_브라우저_캐시_헤더가_붙고_Security가_덮어쓰지_않는다() throws Exception {
		insertGenreWithTitle(GENRE_A, "캐시장르A", 9400051);
		String[] urls = {"/api/public/popularity-rankings", "/api/public/wishlisted-titles",
				"/api/public/newly-available-titles", "/api/public/genres", "/api/public/genres/" + GENRE_A + "/titles"};
		for (String url : urls) {
			mockMvc.perform(get(url))
					.andExpect(status().isOk())
					.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=60"));
		}
	}

	@Test
	void 출연자별_작품과_다른_API의_Cache_Control은_그대로_no_store다() throws Exception {
		mockMvc.perform(get("/api/public/people/titles").param("name", "누구"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
		mockMvc.perform(get("/api/public/ott-services"))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
		mockMvc.perform(get("/api/me/settings"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
	}

	@Test
	void 잘못된_요청_오류_응답에는_공개_캐시_헤더가_붙지_않는다() throws Exception {
		mockMvc.perform(get("/api/public/popularity-rankings").param("limit", "21"))
				.andExpect(status().isBadRequest())
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, not(containsString("public"))));
	}

	// 데이터 준비 보조

	private void requestPopularityItemCount(int expected) throws Exception {
		mockMvc.perform(get("/api/public/popularity-rankings").param("ottServiceId", String.valueOf(netflixId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(expected));
	}

	private void requestAllFiveLists() throws Exception {
		mockMvc.perform(get("/api/public/popularity-rankings")).andExpect(status().isOk());
		mockMvc.perform(get("/api/public/wishlisted-titles")).andExpect(status().isOk());
		mockMvc.perform(get("/api/public/newly-available-titles")).andExpect(status().isOk());
		mockMvc.perform(get("/api/public/genres")).andExpect(status().isOk());
		mockMvc.perform(get("/api/public/genres/" + GENRE_A + "/titles")).andExpect(status().isOk());
	}

	private long insertTitle(String mediaType, int tmdbId, String name) {
		return jdbcTemplate.queryForObject("INSERT INTO title (media_type, tmdb_id, title, fetched_at) "
				+ "VALUES (?, ?, ?, now()) RETURNING id", Long.class, mediaType, tmdbId, name);
	}

	private void insertGenreWithTitle(int genreId, String genreName, int tmdbId) {
		jdbcTemplate.update("INSERT INTO genre (id, name) VALUES (?, ?)", genreId, genreName);
		long titleId = insertTitle("MOVIE", tmdbId, genreName + " 작품");
		jdbcTemplate.update("INSERT INTO title_genre (title_id, genre_id) VALUES (?, ?)", titleId, genreId);
	}

	private void insertSnapshot(int tmdbId, String name) {
		jdbcTemplate.update("INSERT INTO popularity_snapshot (snapshot_date, ott_service_id, media_type, tmdb_id, title, popularity) "
				+ "VALUES (?, ?, 'MOVIE', ?, ?, 10)", today, netflixId, tmdbId, name);
	}

	private void insertCast(long titleId, int castOrder, String name) {
		jdbcTemplate.update("INSERT INTO title_cast (title_id, cast_order, person_tmdb_id, name) VALUES (?, ?, ?, ?)",
				titleId, castOrder, 7100 + castOrder, name);
	}
}
