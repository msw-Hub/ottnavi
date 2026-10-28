package com.ottnavi.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.catalog.domain.entity.Availability;
import com.ottnavi.catalog.domain.entity.Genre;
import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.entity.TitleCast;
import com.ottnavi.catalog.domain.entity.TitleGenre;
import com.ottnavi.catalog.domain.entity.WatchUnit;
import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.UnknownReason;
import com.ottnavi.catalog.domain.repository.AvailabilityRepository;
import com.ottnavi.catalog.domain.repository.GenreRepository;
import com.ottnavi.catalog.domain.repository.TitleCastRepository;
import com.ottnavi.catalog.domain.repository.TitleGenreRepository;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.domain.repository.WatchUnitRepository;
import com.ottnavi.catalog.domain.rule.RuntimeResult;
import com.ottnavi.global.config.RedisConfig;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.domain.repository.OttServiceRepository;
import com.ottnavi.support.IntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 작품 검색·상세 API 통합 테스트(Task 050·051 V-API, Docker 필요).
 * 작품은 repository로 직접 심고, 서비스 7개는 V1 시드를 쓴다.
 */
@AutoConfigureMockMvc
class TitleApiTest extends IntegrationTestSupport {

	private static final String SEARCH_URL = "/api/public/titles";
	// Spring의 MediaType과 이름이 겹쳐 도메인 enum은 전체 이름으로 한 번만 쓴다
	private static final com.ottnavi.catalog.domain.enums.MediaType MOVIE = com.ottnavi.catalog.domain.enums.MediaType.MOVIE;
	private static final com.ottnavi.catalog.domain.enums.MediaType TV = com.ottnavi.catalog.domain.enums.MediaType.TV;
	private static final Instant CHECKED_AT = Instant.parse("2026-10-14T15:30:00Z"); // KST 2026-10-15 00:30

	@Autowired private MockMvc mockMvc;
	@Autowired private TitleRepository titleRepository;
	@Autowired private WatchUnitRepository watchUnitRepository;
	@Autowired private AvailabilityRepository availabilityRepository;
	@Autowired private TitleCastRepository titleCastRepository;
	@Autowired private GenreRepository genreRepository;
	@Autowired private TitleGenreRepository titleGenreRepository;
	@Autowired private OttServiceRepository ottServiceRepository;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private CacheManager cacheManager;

	private List<OttService> services;

	@BeforeEach
	void 초기화() {
		cleanUp();
		services = ottServiceRepository.findAllByOrderByDisplayOrderAsc();
	}

	@AfterEach
	void 정리() {
		cleanUp();
	}

	/** FK 순서대로(자식 → 부모) 비우고 상세 캐시도 비운다 */
	private void cleanUp() {
		for (String table : List.of("availability_change", "availability", "watch_unit", "title_cast", "title_genre",
				"title", "genre")) {
			jdbcTemplate.update("DELETE FROM " + table);
		}
		cacheManager.getCache(RedisConfig.TITLE_DETAIL).clear();
	}

	// 검색

	@Test
	void 검색은_인기도_내림차순으로_작품과_서비스_7개_상태를_돌려준다() throws Exception {
		// given: 인기도가 낮은 작품을 먼저 심는다
		saveMovie(900001, "기생충 외전", "Side Story", "10.0");
		Title high = saveMovie(900002, "기생충", "Parasite", "80.5");
		WatchUnit unit = saveMovieUnit(high, 132, false);
		saveAvailability(unit, services.get(3), AvailabilityStatus.AVAILABLE);   // 서비스 4 있음
		saveAvailability(unit, services.get(0), AvailabilityStatus.NOT_AVAILABLE);

		// when
		var result = mockMvc.perform(get(SEARCH_URL).param("q", " 기생충 "));

		// then
		result.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.content[0].tmdbId").value(900002))
				.andExpect(jsonPath("$.data.content[1].tmdbId").value(900001))
				.andExpect(jsonPath("$.data.content[0].mediaType").value("movie"))
				.andExpect(jsonPath("$.data.content[0].releaseYear").value(2019))
				.andExpect(jsonPath("$.data.content[0].availabilities.length()").value(7))
				.andExpect(jsonPath("$.data.content[0].availabilities[0].status").value("NOT_AVAILABLE"))
				.andExpect(jsonPath("$.data.content[0].availabilities[1].status").value("UNKNOWN"))
				.andExpect(jsonPath("$.data.content[0].availabilities[3].status").value("AVAILABLE"))
				.andExpect(jsonPath("$.data.content[1].availabilities[0].status").value("UNKNOWN"))
				.andExpect(jsonPath("$.data.isExternalCollectFailed").value(false))
				.andExpect(jsonPath("$.data.dataAsOf").value("2026-10-15"));
	}

	@Test
	void 검색은_최근_2년_개봉작을_먼저_과거작을_그_다음_미래와_개봉일_모름을_맨_뒤로_두고_그룹_안은_인기도_순이다() throws Exception {
		// given: 오늘(KST) 기준으로 개봉일이 다른 작품 5개. 인기도는 옛 작품과 미래·모름 쪽이 더 높다
		LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
		insertTitleWithRelease(910001, "정렬검사 옛 고전", "900", LocalDate.of(1993, 1, 1));
		insertTitleWithRelease(910002, "정렬검사 최근 낮은 인기", "5", today.minusYears(1));
		insertTitleWithRelease(910003, "정렬검사 최근 높은 인기", "50", today.minusMonths(6));
		insertTitleWithRelease(910004, "정렬검사 미래 개봉", "999", today.plusDays(30));
		insertTitleWithRelease(910005, "정렬검사 개봉일 모름", "800", null);

		// when: 전체 순서
		var all = mockMvc.perform(get(SEARCH_URL).param("q", "정렬검사"));

		// then: 최근 그룹(인기도 순) → 과거 → 미래(999) → 모름(800)
		all.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(5))
				.andExpect(jsonPath("$.data.content[0].tmdbId").value(910003))
				.andExpect(jsonPath("$.data.content[1].tmdbId").value(910002))
				.andExpect(jsonPath("$.data.content[2].tmdbId").value(910001))
				.andExpect(jsonPath("$.data.content[3].tmdbId").value(910004))
				.andExpect(jsonPath("$.data.content[4].tmdbId").value(910005));

		// when, then: 두 번째 쪽(크기 2)도 같은 순서로 이어지고 개수 쿼리가 깨지지 않는다
		mockMvc.perform(get(SEARCH_URL).param("q", "정렬검사").param("page", "1").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(5))
				.andExpect(jsonPath("$.data.totalPages").value(3))
				.andExpect(jsonPath("$.data.content[0].tmdbId").value(910001))
				.andExpect(jsonPath("$.data.content[1].tmdbId").value(910004));
	}

	@Test
	void 제목이_아닌_원제로도_찾고_페이지네이션이_동작한다() throws Exception {
		// given: 원제에만 Parasite가 있는 작품 3개
		saveMovie(900001, "작품 A", "Parasite A", "30");
		saveMovie(900002, "작품 B", "Parasite B", "20");
		saveMovie(900003, "작품 C", "Parasite C", "10");

		// when: 크기 2의 두 번째 쪽
		var result = mockMvc.perform(get(SEARCH_URL).param("q", "parasite").param("page", "1").param("size", "2"));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(1))
				.andExpect(jsonPath("$.data.size").value(2))
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.totalPages").value(2))
				.andExpect(jsonPath("$.data.content.length()").value(1))
				.andExpect(jsonPath("$.data.content[0].tmdbId").value(900003));
	}

	@Test
	void 동명의_영화와_드라마는_각자의_상태로_구분해_돌려준다() throws Exception {
		// given: 같은 제목·같은 TMDB ID의 영화와 드라마. 영화만 서비스 1에서 제공
		Title movie = saveMovie(900001, "오징어", "Squid", "50");
		Title tv = saveTv(900001, "오징어", "Squid", "40");
		saveAvailability(saveMovieUnit(movie, 100, false), services.get(0), AvailabilityStatus.AVAILABLE);
		saveAvailability(saveSeasonUnit(tv, 1, 400, "시즌 1"), services.get(0), AvailabilityStatus.NOT_AVAILABLE);

		// when
		var result = mockMvc.perform(get(SEARCH_URL).param("q", "오징어"));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[0].mediaType").value("movie"))
				.andExpect(jsonPath("$.data.content[0].availabilities[0].status").value("AVAILABLE"))
				.andExpect(jsonPath("$.data.content[1].mediaType").value("tv"))
				.andExpect(jsonPath("$.data.content[1].availabilities[0].status").value("NOT_AVAILABLE"));
	}

	@Test
	void 결과가_없으면_빈_페이지와_전체_최신_확인일을_돌려준다() throws Exception {
		// given: 다른 작품에만 판정이 있다
		Title other = saveMovie(900001, "다른 작품", "Other", "10");
		saveAvailability(saveMovieUnit(other, 100, false), services.get(0), AvailabilityStatus.AVAILABLE);

		// when
		var result = mockMvc.perform(get(SEARCH_URL).param("q", "없는제목"));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(0))
				.andExpect(jsonPath("$.data.totalElements").value(0))
				.andExpect(jsonPath("$.data.totalPages").value(0))
				.andExpect(jsonPath("$.data.dataAsOf").value("2026-10-15"));
	}

	@Test
	void 퍼센트는_와일드카드가_아닌_문자로_검색한다() throws Exception {
		// given
		saveMovie(900001, "100% 실화", "Real", "20");
		saveMovie(900002, "100 실화", "Real 2", "10");

		// when
		var result = mockMvc.perform(get(SEARCH_URL).param("q", "%"));

		// then: %를 와일드카드로 풀면 두 작품이 모두 나온다
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].tmdbId").value(900001));
	}

	@Test
	void 검색어가_비어_있으면_400_VALIDATION_FAILED이다() throws Exception {
		mockMvc.perform(get(SEARCH_URL).param("q", "   "))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("q"));
		mockMvc.perform(get(SEARCH_URL))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
	}

	@Test
	void 페이지_크기가_범위를_벗어나면_400_VALIDATION_FAILED이다() throws Exception {
		mockMvc.perform(get(SEARCH_URL).param("q", "a").param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
	}

	// 상세

	@Test
	void 영화_상세는_러닝타임_장르_출연진_서비스_상태를_돌려준다() throws Exception {
		// given
		Title movie = saveMovie(900001, "기생충", "Parasite", "80.5");
		saveAvailability(saveMovieUnit(movie, 132, true), services.get(2), AvailabilityStatus.AVAILABLE);
		Genre genre = genreRepository.save(Genre.create(18, "드라마"));
		titleGenreRepository.save(TitleGenre.create(movie, genre));
		for (int i = 0; i < 12; i++) {
			titleCastRepository.save(TitleCast.create(movie, i, 1000 + i, "배우" + i, "배역" + i, null));
		}

		// when
		var result = mockMvc.perform(get(SEARCH_URL + "/movie/900001"));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.mediaType").value("movie"))
				.andExpect(jsonPath("$.data.tmdbId").value(900001))
				.andExpect(jsonPath("$.data.title").value("기생충"))
				.andExpect(jsonPath("$.data.originalTitle").value("Parasite"))
				.andExpect(jsonPath("$.data.overviewLang").value("ko"))
				.andExpect(jsonPath("$.data.genres").value(contains("드라마")))
				.andExpect(jsonPath("$.data.runtimeMin").value(132))
				.andExpect(jsonPath("$.data.isRuntimeEstimated").value(true))
				.andExpect(jsonPath("$.data.cast.length()").value(10))
				.andExpect(jsonPath("$.data.cast[0].name").value("배우0"))
				.andExpect(jsonPath("$.data.availabilities.length()").value(7))
				.andExpect(jsonPath("$.data.availabilities[2].status").value("AVAILABLE"))
				.andExpect(jsonPath("$.data.seasons.length()").value(0))
				.andExpect(jsonPath("$.data.dataAsOf").value("2026-10-15"));
	}

	@Test
	void 드라마_상세는_시즌별_상태와_작품_전체_상태를_합쳐_돌려준다() throws Exception {
		// given: 시즌1은 서비스1 있음, 시즌2는 서비스1 없음·서비스2 모름(행 없음)
		Title tv = saveTv(900010, "오징어 게임", "Squid Game", "70");
		WatchUnit season1 = saveSeasonUnit(tv, 1, 390, "시즌 1");
		WatchUnit season2 = saveSeasonUnit(tv, 2, 410, "시즌 2");
		saveAvailability(season1, services.get(0), AvailabilityStatus.AVAILABLE);
		saveAvailability(season2, services.get(0), AvailabilityStatus.NOT_AVAILABLE);
		saveAvailability(season1, services.get(1), AvailabilityStatus.NOT_AVAILABLE);

		// when
		var result = mockMvc.perform(get(SEARCH_URL + "/tv/900010"));

		// then
		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.mediaType").value("tv"))
				.andExpect(jsonPath("$.data.runtimeMin").doesNotExist())
				.andExpect(jsonPath("$.data.isRuntimeEstimated").value(false))
				.andExpect(jsonPath("$.data.availabilities[0].status").value("AVAILABLE"))   // 시즌 중 하나라도 있음
				.andExpect(jsonPath("$.data.availabilities[1].status").value("UNKNOWN"))     // 시즌2 행 없음
				.andExpect(jsonPath("$.data.seasons.length()").value(2))
				.andExpect(jsonPath("$.data.seasons[0].seasonNumber").value(1))
				.andExpect(jsonPath("$.data.seasons[0].totalRuntimeMin").value(390))
				.andExpect(jsonPath("$.data.seasons[0].availabilities[0].status").value("AVAILABLE"))
				.andExpect(jsonPath("$.data.seasons[1].seasonNumber").value(2))
				.andExpect(jsonPath("$.data.seasons[1].availabilities[0].status").value("NOT_AVAILABLE"))
				.andExpect(jsonPath("$.data.seasons[1].availabilities[1].status").value("UNKNOWN"));
	}

	@Test
	void 없는_작품이면_404_TITLE_NOT_FOUND이다() throws Exception {
		mockMvc.perform(get(SEARCH_URL + "/movie/999999"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errorCode").value("TITLE_NOT_FOUND"));
	}

	@Test
	void 작품_구분이나_TMDB_ID_형식이_틀리면_400_VALIDATION_FAILED이다() throws Exception {
		mockMvc.perform(get(SEARCH_URL + "/anime/1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
		mockMvc.perform(get(SEARCH_URL + "/MOVIE/1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
		mockMvc.perform(get(SEARCH_URL + "/movie/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
	}

	@Test
	void 두_번째_상세_조회는_캐시에서_나와_DB_변경이_보이지_않는다() throws Exception {
		// given
		saveMovie(900001, "기생충", "Parasite", "80.5");
		mockMvc.perform(get(SEARCH_URL + "/movie/900001"))
				.andExpect(jsonPath("$.data.title").value("기생충"));
		assertThat(cacheManager.getCache(RedisConfig.TITLE_DETAIL).get("movie:900001")).isNotNull();

		// when: DB 값을 바꾼 뒤 다시 조회
		jdbcTemplate.update("UPDATE title SET title = '바뀐 제목' WHERE tmdb_id = 900001");
		var cached = mockMvc.perform(get(SEARCH_URL + "/movie/900001"));

		// then: 캐시가 살아 있어 옛 값이 나온다. 캐시를 비우면 DB 값이 나온다
		cached.andExpect(jsonPath("$.data.title").value("기생충"));
		cacheManager.getCache(RedisConfig.TITLE_DETAIL).clear();
		mockMvc.perform(get(SEARCH_URL + "/movie/900001"))
				.andExpect(jsonPath("$.data.title").value("바뀐 제목"));
	}

	// 데이터 준비

	private Title saveMovie(int tmdbId, String title, String originalTitle, String popularity) {
		Title entity = Title.createFromSummary(MOVIE, tmdbId, title, originalTitle,
				LocalDate.of(2019, 5, 30), "/poster.jpg", "/backdrop.jpg", "ko",
				new BigDecimal(popularity), new BigDecimal("8.5"), CHECKED_AT);
		entity.updateDetail(title, originalTitle, "줄거리", "ko", LocalDate.of(2019, 5, 30), "/poster.jpg",
				"/backdrop.jpg", "ko", null, new BigDecimal(popularity), new BigDecimal("8.5"), CHECKED_AT);
		return titleRepository.save(entity);
	}

	/** 개봉일(null 가능)과 인기도만 정해 영화 한 행을 직접 넣는다. 정렬 그룹 테스트용이다 */
	private void insertTitleWithRelease(int tmdbId, String name, String popularity, LocalDate releaseDate) {
		jdbcTemplate.update("INSERT INTO title (media_type, tmdb_id, title, popularity, release_date, fetched_at) "
				+ "VALUES ('MOVIE', ?, ?, ?::numeric, ?, now())", tmdbId, name, popularity, releaseDate);
	}

	private Title saveTv(int tmdbId, String title, String originalTitle, String popularity) {
		Title entity = Title.createFromSummary(TV, tmdbId, title,
				originalTitle, LocalDate.of(2021, 9, 17), "/poster.jpg", null, "ko",
				new BigDecimal(popularity), new BigDecimal("8.0"), CHECKED_AT);
		return titleRepository.save(entity);
	}

	private WatchUnit saveMovieUnit(Title title, int runtimeMin, boolean estimated) {
		RuntimeResult runtime = new RuntimeResult(1, 1, runtimeMin, 0, estimated);
		return watchUnitRepository.save(WatchUnit.createMovie(title, runtime, CHECKED_AT));
	}

	private WatchUnit saveSeasonUnit(Title title, int seasonNumber, int runtimeMin, String name) {
		RuntimeResult runtime = new RuntimeResult(8, 8, runtimeMin, 0, false);
		return watchUnitRepository.save(WatchUnit.createSeason(title, seasonNumber, 5000 + seasonNumber, name,
				"/season.jpg", LocalDate.of(2021, 9, 17), runtime, CHECKED_AT));
	}

	private void saveAvailability(WatchUnit unit, OttService service, AvailabilityStatus status) {
		UnknownReason reason = null;
		boolean inStandard = status == AvailabilityStatus.AVAILABLE;
		if (status == AvailabilityStatus.UNKNOWN) {
			reason = UnknownReason.NO_KR_DATA;
		}
		availabilityRepository.save(Availability.create(unit, service, status, reason, inStandard, false, CHECKED_AT));
	}
}
