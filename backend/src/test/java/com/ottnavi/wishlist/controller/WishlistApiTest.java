package com.ottnavi.wishlist.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ottnavi.catalog.domain.entity.Availability;
import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.entity.WatchUnit;
import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.UnknownReason;
import com.ottnavi.catalog.domain.repository.AvailabilityRepository;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.domain.repository.WatchUnitRepository;
import com.ottnavi.catalog.domain.rule.RuntimeResult;
import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.domain.repository.OttServiceRepository;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.support.TestTokens;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.enums.ServiceSubscriptionStatus;
import com.ottnavi.user.domain.enums.UserRole;
import com.ottnavi.user.domain.enums.WatchPreset;
import com.ottnavi.user.domain.repository.UserRepository;
import com.ottnavi.user.service.UserSettingService;
import com.ottnavi.user.service.model.SubscriptionState;
import com.ottnavi.wishlist.domain.enums.WishlistPriority;
import com.ottnavi.wishlist.domain.repository.WishlistItemRepository;
import com.ottnavi.wishlist.service.WishlistService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 찜 API를 실제 보안 체인과 DB로 확인한다(Docker Desktop 필요).
 * 막으려는 사고: 찜이 21개 이상 저장되는 일(동시 요청 포함), 이미 찜한 항목이 중복 추가·덮어쓰기 되는 일,
 * 타인의 찜이 바뀌거나 지워지는 일, 목록에서 제공 상태·지금 볼 수 있음 판정이 틀리는 일.
 */
@AutoConfigureMockMvc
class WishlistApiTest extends IntegrationTestSupport {

	private static final String PATH = "/api/me/wishlist-items";
	private static final int BASE_TMDB_ID = 910000; // 다른 통합 테스트(900000대)와 겹치지 않는 작품 ID 대역
	private static final Instant CHECKED_AT = Instant.parse("2026-10-14T15:30:00Z"); // KST 2026-10-15 00:30

	@Autowired private MockMvc mockMvc;
	@Autowired private JwtTokenProvider jwtTokenProvider;
	@Autowired private UserRepository userRepository;
	@Autowired private TitleRepository titleRepository;
	@Autowired private WatchUnitRepository watchUnitRepository;
	@Autowired private AvailabilityRepository availabilityRepository;
	@Autowired private OttServiceRepository ottServiceRepository;
	@Autowired private WishlistItemRepository wishlistItemRepository;
	@Autowired private WishlistService wishlistService;
	@Autowired private UserSettingService userSettingService;
	@Autowired private JdbcTemplate jdbcTemplate;

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

	/** 이 테스트가 만든 찜·판정·시청 단위·작품만 FK 순서대로 지운다(다른 테스트의 데이터는 건드리지 않는다) */
	private void cleanUp() {
		jdbcTemplate.update("DELETE FROM wishlist_item");
		String titleIds = "SELECT id FROM title WHERE tmdb_id >= " + BASE_TMDB_ID;
		jdbcTemplate.update("DELETE FROM availability WHERE watch_unit_id IN "
				+ "(SELECT id FROM watch_unit WHERE title_id IN (" + titleIds + "))");
		jdbcTemplate.update("DELETE FROM watch_unit WHERE title_id IN (" + titleIds + ")");
		jdbcTemplate.update("DELETE FROM title WHERE tmdb_id >= " + BASE_TMDB_ID);
	}

	@Test
	void 비로그인은_401이다() throws Exception {
		mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(movieBody(BASE_TMDB_ID, "MUST")))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(patch(PATH + "/1").contentType(MediaType.APPLICATION_JSON).content("{\"priority\":\"WANT\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete(PATH + "/1")).andExpect(status().isUnauthorized());
	}

	@Test
	void 시즌_찜_추가부터_목록_우선순위_다_봤음_삭제까지_이어진다() throws Exception {
		// given: 드라마 시즌 1·2, 시즌 1은 첫 서비스에서 볼 수 있다
		Title tv = saveTitle(BASE_TMDB_ID + 1, com.ottnavi.catalog.domain.enums.MediaType.TV, "오징어 게임");
		WatchUnit season1 = saveSeasonUnit(tv, 1, 500, "시즌 1");
		saveSeasonUnit(tv, 2, 400, "");
		saveAvailability(season1, services.get(0), AvailabilityStatus.AVAILABLE);
		String bearer = bearerOfNewUser();

		// when: 시즌 1 찜 추가
		long itemId = addAndGetId(bearer, tvBody(BASE_TMDB_ID + 1, 1, "MUST"), 201);

		// then: 목록에 한 줄, 시즌 이름·시간·서비스 7개 상태가 채워진다
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].wishlistItemId").value(itemId))
				.andExpect(jsonPath("$.data.items[0].mediaType").value("tv"))
				.andExpect(jsonPath("$.data.items[0].seasonNumber").value(1))
				.andExpect(jsonPath("$.data.items[0].seasonName").value("시즌 1"))
				.andExpect(jsonPath("$.data.items[0].priority").value("MUST"))
				.andExpect(jsonPath("$.data.items[0].requiredMin").value(500))
				.andExpect(jsonPath("$.data.items[0].isRuntimeEstimated").value(false))
				.andExpect(jsonPath("$.data.items[0].availabilities.length()").value(7))
				.andExpect(jsonPath("$.data.items[0].availabilities[0].status").value("AVAILABLE"))
				.andExpect(jsonPath("$.data.items[0].availabilities[1].status").value("UNKNOWN"))
				.andExpect(jsonPath("$.data.items[0].watchedAt").doesNotExist())
				.andExpect(jsonPath("$.data.activeItemCount").value(1))
				.andExpect(jsonPath("$.data.maxActiveItemCount").value(20))
				.andExpect(jsonPath("$.data.dataAsOf").value("2026-10-15"));

		// when & then: 우선순위 변경
		mockMvc.perform(patch(PATH + "/" + itemId).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content("{\"priority\":\"MAYBE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.priority").value("MAYBE"))
				.andExpect(jsonPath("$.data.watchedAt").doesNotExist());

		// when & then: 다 봤음 → 개수에서 빠진다
		mockMvc.perform(patch(PATH + "/" + itemId).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content("{\"isWatched\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.priority").value("MAYBE"))
				.andExpect(jsonPath("$.data.watchedAt").isNotEmpty());
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.activeItemCount").value(0));

		// when & then: 삭제 후 목록이 비고 날짜는 오늘로 채워진다
		mockMvc.perform(delete(PATH + "/" + itemId).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isNoContent());
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(jsonPath("$.data.items.length()").value(0))
				.andExpect(jsonPath("$.data.dataAsOf").isNotEmpty());
	}

	@Test
	void 이미_찜한_작품을_다시_추가하면_200이고_기존_값이_그대로다() throws Exception {
		// given
		saveMovieWithUnit(BASE_TMDB_ID + 2);
		String bearer = bearerOfNewUser();
		long itemId = addAndGetId(bearer, movieBody(BASE_TMDB_ID + 2, "MUST"), 201);

		// when: 다른 우선순위로 다시 추가
		long again = addAndGetId(bearer, movieBody(BASE_TMDB_ID + 2, "MAYBE"), 200);

		// then: 같은 항목이고 우선순위는 바뀌지 않았으며 한 줄뿐이다
		assertThat(again).isEqualTo(itemId);
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].priority").value("MUST"));
	}

	@Test
	void 활성_찜이_20개면_21번째는_409이고_다_봤음은_개수에서_빠지며_되돌리기도_막힌다() throws Exception {
		// given: 작품 21개를 심고 20개를 찜한다
		String bearer = bearerOfNewUser();
		List<Long> itemIds = new ArrayList<>();
		for (int i = 0; i < 21; i++) {
			saveMovieWithUnit(BASE_TMDB_ID + 100 + i);
		}
		for (int i = 0; i < 20; i++) {
			itemIds.add(addAndGetId(bearer, movieBody(BASE_TMDB_ID + 100 + i, "WANT"), 201));
		}

		// when & then: 21번째는 409
		mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content(movieBody(BASE_TMDB_ID + 120, "WANT")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("WISHLIST_LIMIT_EXCEEDED"));

		// when & then: 이미 찜한 작품 중복 추가는 상한이어도 200
		addAndGetId(bearer, movieBody(BASE_TMDB_ID + 100, "WANT"), 200);

		// when & then: 하나를 다 봤음으로 바꾸면 자리가 나서 21번째가 들어간다
		mockMvc.perform(patch(PATH + "/" + itemIds.get(0)).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content("{\"isWatched\":true}"))
				.andExpect(status().isOk());
		addAndGetId(bearer, movieBody(BASE_TMDB_ID + 120, "WANT"), 201);

		// when & then: 다시 활성 20개라 되돌리기는 409이고 다 봤음 상태가 유지된다
		mockMvc.perform(patch(PATH + "/" + itemIds.get(0)).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content("{\"isWatched\":false}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("WISHLIST_LIMIT_EXCEEDED"));
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(jsonPath("$.data.items.length()").value(21))
				.andExpect(jsonPath("$.data.activeItemCount").value(20));
	}

	@Test
	void 타인의_찜은_변경하면_404이고_삭제는_204지만_지워지지_않는다() throws Exception {
		// given
		saveMovieWithUnit(BASE_TMDB_ID + 3);
		String owner = bearerOfNewUser();
		String other = bearerOfNewUser();
		long itemId = addAndGetId(owner, movieBody(BASE_TMDB_ID + 3, "MUST"), 201);

		// when & then: 다른 사용자의 변경은 404
		mockMvc.perform(patch(PATH + "/" + itemId).header(HttpHeaders.AUTHORIZATION, other)
						.contentType(MediaType.APPLICATION_JSON).content("{\"priority\":\"MAYBE\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("WISHLIST_ITEM_NOT_FOUND"));

		// when & then: 다른 사용자의 삭제는 204지만 찜은 남는다
		mockMvc.perform(delete(PATH + "/" + itemId).header(HttpHeaders.AUTHORIZATION, other))
				.andExpect(status().isNoContent());
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, owner))
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].priority").value("MUST"));
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, other))
				.andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	void 없는_작품이나_시즌을_찜하면_404다() throws Exception {
		// given: 시즌 1만 있는 드라마
		Title tv = saveTitle(BASE_TMDB_ID + 4, com.ottnavi.catalog.domain.enums.MediaType.TV, "시즌 하나");
		saveSeasonUnit(tv, 1, 300, "시즌 1");
		String bearer = bearerOfNewUser();

		// when & then
		mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content(movieBody(BASE_TMDB_ID + 999, "MUST")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("TITLE_NOT_FOUND"));
		mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content(tvBody(BASE_TMDB_ID + 4, 2, "MUST")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("TITLE_NOT_FOUND"));
	}

	@Test
	void 영화에_시즌_번호를_보내거나_드라마에_빼면_400이다() throws Exception {
		// given
		String bearer = bearerOfNewUser();
		String movieWithSeason = """
				{"mediaType":"movie","tmdbId":%d,"seasonNumber":1,"priority":"MUST"}
				""".formatted(BASE_TMDB_ID + 5);
		String tvWithoutSeason = """
				{"mediaType":"tv","tmdbId":%d,"seasonNumber":null,"priority":"MUST"}
				""".formatted(BASE_TMDB_ID + 5);

		// when & then
		mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content(movieWithSeason))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("seasonNumber"));
		mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content(tvWithoutSeason))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
	}

	@Test
	void 바꿀_값이_없는_변경_요청은_400이다() throws Exception {
		// given
		saveMovieWithUnit(BASE_TMDB_ID + 6);
		String bearer = bearerOfNewUser();
		long itemId = addAndGetId(bearer, movieBody(BASE_TMDB_ID + 6, "MUST"), 201);

		// when & then
		mockMvc.perform(patch(PATH + "/" + itemId).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
	}

	@Test
	void 지금_볼_수_있음은_구독_또는_무료_서비스의_있음만_센다() throws Exception {
		// given: 작품 3개. A는 구독 중 서비스, B는 구독 안 한 서비스, C는 "잘 모르겠음" 서비스에서만 볼 수 있다
		Title a = saveTitle(BASE_TMDB_ID + 7, com.ottnavi.catalog.domain.enums.MediaType.MOVIE, "작품 A");
		Title b = saveTitle(BASE_TMDB_ID + 8, com.ottnavi.catalog.domain.enums.MediaType.MOVIE, "작품 B");
		Title c = saveTitle(BASE_TMDB_ID + 9, com.ottnavi.catalog.domain.enums.MediaType.MOVIE, "작품 C");
		saveAvailability(saveMovieUnit(a), services.get(0), AvailabilityStatus.AVAILABLE);
		saveAvailability(saveMovieUnit(b), services.get(1), AvailabilityStatus.AVAILABLE);
		saveAvailability(saveMovieUnit(c), services.get(2), AvailabilityStatus.AVAILABLE);
		User user = createUser();
		List<SubscriptionState> states = new ArrayList<>();
		states.add(new SubscriptionState(services.get(0).getId(), ServiceSubscriptionStatus.SUBSCRIBED, "스탠다드", 1));
		states.add(new SubscriptionState(services.get(2).getId(), ServiceSubscriptionStatus.UNKNOWN, null, null));
		userSettingService.updateSettings(user.getId(), 20000, 900, WatchPreset.NORMAL, false, states);
		String bearer = new TestTokens(jwtTokenProvider).bearer(user.getId(), UserRole.USER);
		addAndGetId(bearer, movieBody(BASE_TMDB_ID + 7, "MUST"), 201);
		addAndGetId(bearer, movieBody(BASE_TMDB_ID + 8, "MUST"), 201);
		addAndGetId(bearer, movieBody(BASE_TMDB_ID + 9, "MUST"), 201);

		// when & then: 최근에 찜한 순(C, B, A)으로 A만 true
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].title").value("작품 C"))
				.andExpect(jsonPath("$.data.items[0].isWatchableNow").value(false))
				.andExpect(jsonPath("$.data.items[1].title").value("작품 B"))
				.andExpect(jsonPath("$.data.items[1].isWatchableNow").value(false))
				.andExpect(jsonPath("$.data.items[2].title").value("작품 A"))
				.andExpect(jsonPath("$.data.items[2].isWatchableNow").value(true));
	}

	@Test
	void 같은_사용자의_동시_추가도_상한_20개를_넘지_않는다() throws Exception {
		// given: 활성 찜 19개를 채우고 서로 다른 작품 2개를 동시에 추가한다
		User user = createUser();
		for (int i = 0; i < 19; i++) {
			saveMovieWithUnit(BASE_TMDB_ID + 200 + i);
			wishlistService.add(user.getId(), com.ottnavi.catalog.domain.enums.MediaType.MOVIE,
					BASE_TMDB_ID + 200 + i, null, WishlistPriority.WANT);
		}
		saveMovieWithUnit(BASE_TMDB_ID + 300);
		saveMovieWithUnit(BASE_TMDB_ID + 301);
		int[] tmdbIds = {BASE_TMDB_ID + 300, BASE_TMDB_ID + 301};
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		List<Future<Boolean>> futures = new ArrayList<>();
		try {
			for (int t = 0; t < 2; t++) {
				int tmdbId = tmdbIds[t];
				futures.add(pool.submit(() -> {
					start.await();
					try {
						wishlistService.add(user.getId(), com.ottnavi.catalog.domain.enums.MediaType.MOVIE,
								tmdbId, null, WishlistPriority.WANT);
						return true;
					} catch (com.ottnavi.wishlist.exception.WishlistLimitExceededException e) {
						return false;
					}
				}));
			}

			// when
			start.countDown();
			int succeeded = 0;
			for (Future<Boolean> future : futures) {
				if (future.get()) {
					succeeded++;
				}
			}

			// then: 하나만 성공하고 활성 찜은 정확히 20개다
			assertThat(succeeded).isEqualTo(1);
			assertThat(wishlistItemRepository.countByUserIdAndWatchedAtIsNull(user.getId())).isEqualTo(20);
		} finally {
			pool.shutdownNow();
		}
	}

	// 데이터 준비

	private long addAndGetId(String bearer, String body, int expectedStatus) throws Exception {
		String content = mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().is(expectedStatus))
				.andReturn().getResponse().getContentAsString();
		Number id = JsonPath.read(content, "$.data.wishlistItemId");
		return id.longValue();
	}

	private String movieBody(int tmdbId, String priority) {
		return """
				{"mediaType":"movie","tmdbId":%d,"seasonNumber":null,"priority":"%s"}
				""".formatted(tmdbId, priority);
	}

	private String tvBody(int tmdbId, int seasonNumber, String priority) {
		return """
				{"mediaType":"tv","tmdbId":%d,"seasonNumber":%d,"priority":"%s"}
				""".formatted(tmdbId, seasonNumber, priority);
	}

	private Title saveTitle(int tmdbId, com.ottnavi.catalog.domain.enums.MediaType mediaType, String name) {
		return titleRepository.save(Title.create(mediaType, tmdbId, name, CHECKED_AT));
	}

	private WatchUnit saveMovieUnit(Title title) {
		RuntimeResult runtime = new RuntimeResult(1, 1, 120, 0, false);
		return watchUnitRepository.save(WatchUnit.createMovie(title, runtime, CHECKED_AT));
	}

	private void saveMovieWithUnit(int tmdbId) {
		saveMovieUnit(saveTitle(tmdbId, com.ottnavi.catalog.domain.enums.MediaType.MOVIE, "영화 " + tmdbId));
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

	private String bearerOfNewUser() {
		return new TestTokens(jwtTokenProvider).bearer(createUser().getId(), UserRole.USER);
	}

	private User createUser() {
		String sub = "g" + UUID.randomUUID().toString().replace("-", "");
		return userRepository.save(User.create(sub + "@example.com", "테스터", OAuthProvider.GOOGLE, sub));
	}
}
