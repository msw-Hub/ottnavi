package com.ottnavi.home.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.config.RedisConfig;
import com.ottnavi.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 홈·탐색 공개 조회 API(Task 107)를 실제 DB(Testcontainers)와 MockMvc로 확인한다(Docker Desktop 필요).
 * 막으려는 사고: 일주일 평균이 하루 반짝 작품을 부풀리는 일, 찜 최소 인원 미만이나 "다 봤음"이 노출되는 일,
 * 입고 14일·공개 30일 경계가 틀리는 일(첫 적재 오분류 포함), 장르·인물 탐색이 다른 작품을 섞는 일, 잘못된 파라미터가 200으로 나가는 일.
 * 다른 테스트가 남긴 데이터와 섞이지 않게 이 클래스의 작품은 TMDB ID 9300000번대를 쓰고, 단언은 "이 작품들이 있다/없다" 위주로 한다.
 */
@AutoConfigureMockMvc
class HomeApiTest extends IntegrationTestSupport {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final int GENRE_ID = 99001;

	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private CacheManager cacheManager;

	private LocalDate today;
	private long netflixId;

	@BeforeEach
	void 초기화() {
		cleanUp();
		today = LocalDate.now(KST);
		netflixId = serviceId("NETFLIX");
	}

	@AfterEach
	void 정리() {
		cleanUp();
	}

	private void cleanUp() {
		String myUnits = "(SELECT u.id FROM watch_unit u JOIN title t ON t.id = u.title_id WHERE t.tmdb_id BETWEEN 9300000 AND 9399999)";
		String myTitles = "(SELECT id FROM title WHERE tmdb_id BETWEEN 9300000 AND 9399999)";
		jdbcTemplate.update("DELETE FROM wishlist_item WHERE watch_unit_id IN " + myUnits);
		jdbcTemplate.update("DELETE FROM availability_change WHERE watch_unit_id IN " + myUnits);
		jdbcTemplate.update("DELETE FROM availability WHERE watch_unit_id IN " + myUnits);
		jdbcTemplate.update("DELETE FROM watch_unit WHERE title_id IN " + myTitles);
		jdbcTemplate.update("DELETE FROM title_cast WHERE title_id IN " + myTitles);
		jdbcTemplate.update("DELETE FROM title_genre WHERE title_id IN " + myTitles);
		jdbcTemplate.update("DELETE FROM title WHERE tmdb_id BETWEEN 9300000 AND 9399999");
		jdbcTemplate.update("DELETE FROM genre WHERE id = " + GENRE_ID);
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'home-test-%'");
		jdbcTemplate.update("DELETE FROM popularity_snapshot");
		// 응답 캐시가 이전 테스트의 값을 돌려주지 않게 홈 캐시를 비운다(Task 107)
		for (String name : RedisConfig.HOME_CACHES) {
			cacheManager.getCache(name).clear();
		}
	}

	// 인기 순위

	@Test
	void 인기_순위는_오늘은_가장_최근_하루_일주일은_스냅샷_날_수로_나눈_평균이다() throws Exception {
		// given: 어제와 오늘 스냅샷. A는 두 날 모두 10, B는 오늘만 40(우리 DB에 없음), C는 어제만 30
		long titleAId = insertTitle("MOVIE", 9300001, "작품 A", null);
		insertUnit(titleAId, "MOVIE", null, null);
		insertSnapshot(today.minusDays(1), "MOVIE", 9300001, "작품 A", "10.0");
		insertSnapshot(today, "MOVIE", 9300001, "작품 A", "10.0");
		insertSnapshot(today, "TV", 9300002, "작품 B", "40.0");
		insertSnapshot(today.minusDays(1), "MOVIE", 9300003, "작품 C", "30.0");

		// when, then: TODAY는 오늘 스냅샷만 보고 B(40) → A(10)이다
		mockMvc.perform(get("/api/public/popularity-rankings").param("period", "TODAY").param("ottServiceId", String.valueOf(netflixId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.period").value("TODAY"))
				.andExpect(jsonPath("$.data.daysCounted").value(1))
				.andExpect(jsonPath("$.data.snapshotDate").value(today.toString()))
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.items[0].rank").value(1))
				.andExpect(jsonPath("$.data.items[0].title.tmdbId").value(9300002))
				.andExpect(jsonPath("$.data.items[0].title.availabilities.length()").value(0))
				.andExpect(jsonPath("$.data.items[1].title.tmdbId").value(9300001))
				.andExpect(jsonPath("$.data.items[1].title.availabilities.length()").value(7));

		// when, then: WEEK는 2일로 나눈다. A=(10+10)/2=10, B=40/2=20, C=30/2=15 → B, C, A
		mockMvc.perform(get("/api/public/popularity-rankings").param("period", "WEEK").param("ottServiceId", String.valueOf(netflixId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.daysCounted").value(2))
				.andExpect(jsonPath("$.data.items[0].title.tmdbId").value(9300002))
				.andExpect(jsonPath("$.data.items[1].title.tmdbId").value(9300003))
				.andExpect(jsonPath("$.data.items[2].title.tmdbId").value(9300001));
	}

	@Test
	void 스냅샷이_없으면_200과_빈_순위를_돌려준다() throws Exception {
		mockMvc.perform(get("/api/public/popularity-rankings"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.period").value("TODAY"))
				.andExpect(jsonPath("$.data.snapshotDate").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.daysCounted").value(0))
				.andExpect(jsonPath("$.data.notice").value("TMDB 인기도 기준, OTT 공식 순위 아님"))
				.andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	void 잘못된_파라미터는_400_VALIDATION_FAILED다() throws Exception {
		mockMvc.perform(get("/api/public/popularity-rankings").param("period", "YEAR"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
		mockMvc.perform(get("/api/public/popularity-rankings").param("limit", "21"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/public/wishlisted-titles").param("limit", "0"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/public/newly-available-titles").param("limit", "51"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/public/people/titles").param("name", "   "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
	}

	// 찜 많은 작품

	@Test
	void 찜_많은_작품은_최소_인원_미만과_다_봤음을_빼고_드라마는_사용자를_한_번만_센다() throws Exception {
		// given: 사용자 3명
		long user1 = insertUser(1);
		long user2 = insertUser(2);
		long user3 = insertUser(3);
		// W1: 3명이 찜 → 포함(3)
		long w1 = insertMovieUnit(9300011, "찜 영화 3명");
		wish(user1, w1, false);
		wish(user2, w1, false);
		wish(user3, w1, false);
		// W2: 2명만 → 최소 인원 미만이라 제외
		long w2 = insertMovieUnit(9300012, "찜 영화 2명");
		wish(user1, w2, false);
		wish(user2, w2, false);
		// W3: 3명이지만 1명이 "다 봤음" → 유효 2명이라 제외
		long w3 = insertMovieUnit(9300013, "찜 영화 다봤음");
		wish(user1, w3, false);
		wish(user2, w3, false);
		wish(user3, w3, true);
		// W4: 드라마 시즌 2개. user1이 둘 다 찜해도 1명으로 세어 모두 3명 → 포함(3)
		long w4Title = insertTitle("TV", 9300014, "찜 드라마", null);
		long season1 = insertUnit(w4Title, "SEASON", 1, null);
		long season2 = insertUnit(w4Title, "SEASON", 2, null);
		wish(user1, season1, false);
		wish(user1, season2, false);
		wish(user2, season1, false);
		wish(user3, season2, false);

		// when, then
		mockMvc.perform(get("/api/public/wishlisted-titles").param("limit", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.minUserCount").value(3))
				.andExpect(jsonPath("$.data.items[?(@.title.tmdbId==9300011)].wishlistUserCount").value(3))
				.andExpect(jsonPath("$.data.items[?(@.title.tmdbId==9300014)].wishlistUserCount").value(3))
				.andExpect(jsonPath("$.data.items[?(@.title.tmdbId==9300012)]").isEmpty())
				.andExpect(jsonPath("$.data.items[?(@.title.tmdbId==9300013)]").isEmpty());
	}

	// 새로 볼 수 있게 된 작품

	@Test
	void 새로_볼_수_있게_된_작품은_입고_14일과_공개_30일_경계를_지키고_첫_적재_입고를_걸러낸다() throws Exception {
		// given: 입고(없음 → 있음). 14일 전은 포함, 15일 전은 제외
		arrival(9300021, "입고 14일 전", today.minusDays(14), "NOT_AVAILABLE", "AVAILABLE", "AVAILABLE");
		arrival(9300022, "입고 15일 전", today.minusDays(15), "UNKNOWN", "AVAILABLE", "AVAILABLE");
		// 입고 뒤 지금은 빠진 작품은 제외
		arrival(9300023, "입고 뒤 빠짐", today.minusDays(2), "NOT_AVAILABLE", "AVAILABLE", "NOT_AVAILABLE");
		// 첫 적재(이전 상태 없음)는 입고가 아니다. 공개일 30일 전은 포함, 31일 전·공개일 모름·오래된 작품은 제외
		firstLoad(9300031, "공개 30일 전", today.minusDays(30), today.minusDays(300));
		firstLoad(9300032, "공개 31일 전", today.minusDays(31), today.minusDays(300));
		firstLoad(9300033, "공개일 모름", null, today.minusDays(300));
		firstLoad(9300034, "오래된 작품", today.minusDays(900), today.minusDays(300));
		// 드라마는 시즌 방영일로 본다(작품 첫 방영일이 오래돼도 최근 시즌이면 포함)
		long tvTitle = insertTitle("TV", 9300035, "새 시즌 드라마", today.minusDays(900));
		long seasonUnit = insertUnit(tvTitle, "SEASON", 3, today.minusDays(5));
		insertAvailability(seasonUnit, "AVAILABLE");
		insertChange(seasonUnit, null, "AVAILABLE", today.minusDays(300));

		// when, then
		mockMvc.perform(get("/api/public/newly-available-titles").param("ottServiceId", String.valueOf(netflixId)).param("limit", "50"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[?(@.title.tmdbId==9300021)].arrivalType").value("ARRIVAL"))
				.andExpect(jsonPath("$.data.items[?(@.title.tmdbId==9300021)].availableSince").value(today.minusDays(14).toString()))
				.andExpect(jsonPath("$.data.items[?(@.title.tmdbId==9300022)]").isEmpty())
				.andExpect(jsonPath("$.data.items[?(@.title.tmdbId==9300023)]").isEmpty())
				.andExpect(jsonPath("$.data.items[?(@.title.tmdbId==9300031)].arrivalType").value("RELEASE"))
				.andExpect(jsonPath("$.data.items[?(@.title.tmdbId==9300031)].availableSince").value(today.minusDays(30).toString()))
				.andExpect(jsonPath("$.data.items[?(@.title.tmdbId==9300032)]").isEmpty())
				.andExpect(jsonPath("$.data.items[?(@.title.tmdbId==9300033)]").isEmpty())
				.andExpect(jsonPath("$.data.items[?(@.title.tmdbId==9300034)]").isEmpty())
				.andExpect(jsonPath("$.data.items[?(@.title.tmdbId==9300035)].availableSince").value(today.minusDays(5).toString()));
	}

	// 장르·인물 탐색

	@Test
	void 장르_목록과_장르별_작품은_연결된_작품만_인기도_순으로_돌려준다() throws Exception {
		// given: 장르에 작품 2개(인기도 90, 10)와 장르 없는 작품 1개
		jdbcTemplate.update("INSERT INTO genre (id, name) VALUES (?, '테스트장르')", GENRE_ID);
		long popular = insertTitle("MOVIE", 9300041, "장르 인기작", null);
		long quiet = insertTitle("MOVIE", 9300042, "장르 조용한작", null);
		insertTitle("MOVIE", 9300043, "장르 없는 작품", null);
		jdbcTemplate.update("UPDATE title SET popularity = 90 WHERE id = ?", popular);
		jdbcTemplate.update("UPDATE title SET popularity = 10 WHERE id = ?", quiet);
		jdbcTemplate.update("INSERT INTO title_genre (title_id, genre_id) VALUES (?, ?)", quiet, GENRE_ID);
		jdbcTemplate.update("INSERT INTO title_genre (title_id, genre_id) VALUES (?, ?)", popular, GENRE_ID);

		// when, then
		mockMvc.perform(get("/api/public/genres"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.genreId==" + GENRE_ID + ")].name").value("테스트장르"));
		mockMvc.perform(get("/api/public/genres/" + GENRE_ID + "/titles"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.content[0].tmdbId").value(9300041))
				.andExpect(jsonPath("$.data.content[1].tmdbId").value(9300042));
		mockMvc.perform(get("/api/public/genres/98765/titles"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(0))
				.andExpect(jsonPath("$.data.content.length()").value(0));
	}

	@Test
	void 인물_탐색은_이름이_같은_출연자의_작품만_돌려준다() throws Exception {
		// given
		long titleA = insertTitle("MOVIE", 9300051, "출연작 A", null);
		long titleB = insertTitle("TV", 9300052, "출연작 B", null);
		long titleC = insertTitle("MOVIE", 9300053, "다른 사람 작품", null);
		insertCast(titleA, 0, "Kim Test");
		insertCast(titleB, 1, "kim test"); // 대소문자가 달라도 같은 이름이다
		insertCast(titleC, 0, "Kim Testing"); // 부분만 같은 이름은 다른 사람이다

		// when, then: 앞뒤 공백은 무시한다
		mockMvc.perform(get("/api/public/people/titles").param("name", "  Kim Test "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.content[?(@.tmdbId==9300053)]").isEmpty());
	}

	// 데이터 준비 보조

	private long serviceId(String code) {
		Long id = jdbcTemplate.queryForObject("SELECT id FROM ott_service WHERE code = ?", Long.class, code);
		return id;
	}

	private long insertTitle(String mediaType, int tmdbId, String name, LocalDate releaseDate) {
		return jdbcTemplate.queryForObject("INSERT INTO title (media_type, tmdb_id, title, release_date, fetched_at) "
				+ "VALUES (?, ?, ?, ?, now()) RETURNING id", Long.class, mediaType, tmdbId, name, releaseDate);
	}

	private long insertUnit(long titleId, String unitType, Integer seasonNumber, LocalDate airDate) {
		return jdbcTemplate.queryForObject("INSERT INTO watch_unit (title_id, unit_type, season_number, air_date, "
				+ "total_runtime_min, fetched_at) VALUES (?, ?, ?, ?, 100, now()) RETURNING id", Long.class, titleId,
				unitType, seasonNumber, airDate);
	}

	/** 넷플릭스 기준 제공 상태 한 행. UNKNOWN이면 이유 제약 때문에 NO_KR_DATA를 함께 넣는다 */
	private void insertAvailability(long unitId, String status) {
		String reason = null;
		if ("UNKNOWN".equals(status)) {
			reason = "NO_KR_DATA";
		}
		jdbcTemplate.update("INSERT INTO availability (watch_unit_id, ott_service_id, status, unknown_reason, in_standard, "
				+ "checked_at) VALUES (?, ?, ?, ?, ?, now())", unitId, netflixId, status, reason, "AVAILABLE".equals(status));
	}

	private void insertChange(long unitId, String before, String after, LocalDate changedDate) {
		OffsetDateTime changedAt = changedDate.atTime(12, 0).atZone(KST).toOffsetDateTime();
		jdbcTemplate.update("INSERT INTO availability_change (watch_unit_id, ott_service_id, before_status, after_status, "
				+ "changed_at) VALUES (?, ?, ?, ?, ?)", unitId, netflixId, before, after, changedAt);
	}

	/** 영화 작품 + 시청 단위 하나를 만들고 시청 단위 ID를 돌려준다 */
	private long insertMovieUnit(int tmdbId, String name) {
		long titleId = insertTitle("MOVIE", tmdbId, name, null);
		return insertUnit(titleId, "MOVIE", null, null);
	}

	/** 입고 후보 영화: 이전 상태 → 이후 상태 변경 기록과 현재 제공 상태를 만든다 */
	private void arrival(int tmdbId, String name, LocalDate changedDate, String before, String after, String currentStatus) {
		long unitId = insertMovieUnit(tmdbId, name);
		insertAvailability(unitId, currentStatus);
		insertChange(unitId, before, after, changedDate);
	}

	/** 첫 적재 영화: 이전 상태 없이 바로 있음. releaseDate는 영화 개봉일, loadedDate는 적재(변경 기록) 날짜 */
	private void firstLoad(int tmdbId, String name, LocalDate releaseDate, LocalDate loadedDate) {
		long titleId = insertTitle("MOVIE", tmdbId, name, releaseDate);
		long unitId = insertUnit(titleId, "MOVIE", null, null);
		insertAvailability(unitId, "AVAILABLE");
		insertChange(unitId, null, "AVAILABLE", loadedDate);
	}

	private long insertUser(int number) {
		return jdbcTemplate.queryForObject("INSERT INTO users (email, name, oauth_provider, oauth_subject) "
				+ "VALUES (?, '홈 테스트', 'GOOGLE', ?) RETURNING id", Long.class, "home-test-" + number + "@example.com",
				"home-test-" + number);
	}

	private void wish(long userId, long unitId, boolean watched) {
		OffsetDateTime watchedAt = null;
		if (watched) {
			watchedAt = OffsetDateTime.now();
		}
		jdbcTemplate.update("INSERT INTO wishlist_item (user_id, watch_unit_id, priority, watched_at) VALUES (?, ?, 'WANT', ?)",
				userId, unitId, watchedAt);
	}

	private void insertSnapshot(LocalDate snapshotDate, String mediaType, int tmdbId, String name, String popularity) {
		jdbcTemplate.update("INSERT INTO popularity_snapshot (snapshot_date, ott_service_id, media_type, tmdb_id, title, popularity) "
				+ "VALUES (?, ?, ?, ?, ?, ?::numeric)", snapshotDate, netflixId, mediaType, tmdbId, name, popularity);
	}

	private void insertCast(long titleId, int castOrder, String name) {
		jdbcTemplate.update("INSERT INTO title_cast (title_id, cast_order, person_tmdb_id, name) VALUES (?, ?, ?, ?)",
				titleId, castOrder, 7000 + castOrder, name);
	}
}
