package com.ottnavi.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.global.security.token.RefreshTokenStore;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.support.TestTokens;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.enums.UserRole;
import com.ottnavi.user.domain.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 탈퇴 API(DELETE /api/me)를 실제 보안 체인과 DB·Redis로 확인한다(Docker Desktop 필요).
 * 막으려는 사고: 탈퇴 뒤 사용자 행이 남는 일, 계산 기록(calc_run)이 같이 지워지거나 플랜 연결이 안 끊기는 일,
 * 탈퇴 뒤에도 Refresh Token으로 재발급되는 일, 탈퇴 중 들어온 찜 추가가 주인 없는 행으로 남는 일.
 */
@AutoConfigureMockMvc
class UserWithdrawApiTest extends IntegrationTestSupport {

	private static final String PATH = "/api/me";
	private static final String ORIGIN = "http://localhost:5173"; // application-local.yml의 app.frontend-origin 기본값
	private static final int BASE_TMDB_ID = 980000; // 다른 통합 테스트(900000~970000대)와 겹치지 않는 작품 ID 대역
	private static final String CALC_HASH = "w".repeat(64); // 이 테스트가 만든 calc_run을 알아보는 해시

	@Autowired private MockMvc mockMvc;
	@Autowired private JwtTokenProvider jwtTokenProvider;
	@Autowired private UserRepository userRepository;
	@Autowired private RefreshTokenStore refreshTokenStore;
	@Autowired private StringRedisTemplate redis;
	@Autowired private JdbcTemplate jdbc;

	@AfterEach
	void 정리() {
		// 이 테스트가 만든 calc_run·상품·작품만 FK 순서대로 지운다(사용자 행은 탈퇴로 이미 사라졌고, 남았으면 아래에서 지운다)
		jdbc.update("DELETE FROM calc_run WHERE input_hash = ?", CALC_HASH);
		jdbc.update("DELETE FROM user_price_override WHERE product_id IN (SELECT id FROM subscription_product WHERE name = '탈퇴테스트 상품')");
		jdbc.update("DELETE FROM subscription_product WHERE name = '탈퇴테스트 상품'");
		String titleIds = "SELECT id FROM title WHERE tmdb_id >= " + BASE_TMDB_ID;
		jdbc.update("DELETE FROM wishlist_item WHERE watch_unit_id IN (SELECT id FROM watch_unit WHERE title_id IN (" + titleIds + "))");
		jdbc.update("DELETE FROM watch_unit WHERE title_id IN (" + titleIds + ")");
		jdbc.update("DELETE FROM title WHERE tmdb_id >= " + BASE_TMDB_ID);
		jdbc.update("DELETE FROM users WHERE email LIKE 'withdraw-%@example.com'");
	}

	@Test
	void 비로그인은_401이다() throws Exception {
		mockMvc.perform(delete(PATH)).andExpect(status().isUnauthorized());
	}

	@Test
	void 탈퇴하면_사용자_데이터가_모두_사라지고_계산_기록은_플랜_연결만_끊긴다() throws Exception {
		// given: 설정·구독·요금·찜·플랜·알림·계산 기록이 있는 사용자
		User user = createUser();
		long planId = insertFullData(user.getId());

		// when
		MvcResult result = mockMvc.perform(delete(PATH).header(HttpHeaders.AUTHORIZATION, bearerOf(user)))
				.andExpect(status().isNoContent())
				.andReturn();

		// then: 사용자와 소유 행은 0행, 계산 기록은 남고 plan_id만 NULL
		assertThat(count("users", "id", user.getId())).isZero();
		assertThat(count("user_setting", "user_id", user.getId())).isZero();
		assertThat(count("user_subscription", "user_id", user.getId())).isZero();
		assertThat(count("user_price_override", "user_id", user.getId())).isZero();
		assertThat(count("wishlist_item", "user_id", user.getId())).isZero();
		assertThat(count("plan", "user_id", user.getId())).isZero();
		assertThat(count("notification_outbox", "user_id", user.getId())).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM calc_run WHERE input_hash = ?", Integer.class, CALC_HASH))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT plan_id FROM calc_run WHERE input_hash = ?", Long.class, CALC_HASH))
				.isNull();
		assertThat(planId).isPositive();

		// then: rt 쿠키를 만료시킨다(Path는 발급 때와 같아야 지워진다)
		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
				.startsWith("rt=").contains("Max-Age=0", "Path=/api/auth", "HttpOnly");
	}

	@Test
	void 탈퇴하면_같은_RT로_재발급할_수_없고_Redis_계열이_지워진다() throws Exception {
		// given: 기기 두 대에서 로그인한 사용자(계열 2개)
		User user = createUser();
		String tokenA = refreshTokenStore.issue(user.getId());
		String tokenB = refreshTokenStore.issue(user.getId());
		String familyA = tokenA.substring(0, tokenA.indexOf('.'));
		assertThat(redis.hasKey("rt:user:" + user.getId())).isTrue();
		assertThat(redis.hasKey("rt:fam:" + familyA)).isTrue();

		// when
		mockMvc.perform(delete(PATH).header(HttpHeaders.AUTHORIZATION, bearerOf(user))).andExpect(status().isNoContent());

		// then: 계열 키와 사용자 목록 키가 모두 사라지고 두 RT 모두 401이다
		assertThat(redis.hasKey("rt:user:" + user.getId())).isFalse();
		assertThat(redis.hasKey("rt:fam:" + familyA)).isFalse();
		mockMvc.perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("rt", tokenA)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("rt", tokenB)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 탈퇴_직후_남은_AT로는_쓰기가_401이고_설정_조회는_빈_200이다() throws Exception {
		// given
		User user = createUser();
		long unitTmdbId = BASE_TMDB_ID + 1;
		insertMovieUnit(unitTmdbId);
		String bearer = bearerOf(user);
		mockMvc.perform(delete(PATH).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isNoContent());

		// when & then: 사용자 행 잠금에서 401
		mockMvc.perform(put("/api/me/settings").header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content(settingsBody(jdbc.queryForObject("SELECT min(id) FROM ott_service", Long.class))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
		mockMvc.perform(post("/api/me/wishlist-items").header(HttpHeaders.AUTHORIZATION, bearer)
						.contentType(MediaType.APPLICATION_JSON).content(movieBody(unitTmdbId)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete(PATH).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isUnauthorized());

		// then: 읽기는 온보딩 전 상태의 200이다(요청마다 DB·Redis를 보지 않기로 한 결정, TECH 4절)
		mockMvc.perform(get("/api/me/settings").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isOnboardingDone").value(false))
				.andExpect(jsonPath("$.data.subscriptions").isEmpty());
		assertThat(count("wishlist_item", "user_id", user.getId())).isZero();
	}

	@Test
	void 탈퇴와_찜_추가를_동시에_해도_주인_없는_찜이_남지_않는다() throws Exception {
		// given: 작품 하나와, 라운드마다 새 사용자
		long tmdbId = BASE_TMDB_ID + 2;
		insertMovieUnit(tmdbId);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			for (int round = 0; round < 8; round++) {
				User user = createUser();
				String bearer = bearerOf(user);
				CountDownLatch start = new CountDownLatch(1);
				List<Future<Integer>> futures = new ArrayList<>();
				futures.add(pool.submit(() -> {
					start.await();
					return mockMvc.perform(delete(PATH).header(HttpHeaders.AUTHORIZATION, bearer))
							.andReturn().getResponse().getStatus();
				}));
				futures.add(pool.submit(() -> {
					start.await();
					return mockMvc.perform(post("/api/me/wishlist-items").header(HttpHeaders.AUTHORIZATION, bearer)
									.contentType(MediaType.APPLICATION_JSON).content(movieBody(tmdbId)))
							.andReturn().getResponse().getStatus();
				}));

				// when
				start.countDown();
				int withdrawStatus = futures.get(0).get();
				int addStatus = futures.get(1).get();

				// then: 탈퇴는 항상 성공하고, 찜 추가는 먼저 끝났거나(2xx) 사용자가 없어 401이며(500이 아니다), 어느 쪽이든 찜 행은 0이다
				assertThat(withdrawStatus).isEqualTo(204);
				assertThat(addStatus).isIn(200, 201, 401);
				assertThat(count("users", "id", user.getId())).isZero();
				assertThat(count("wishlist_item", "user_id", user.getId())).isZero();
			}
		} finally {
			pool.shutdownNow();
		}
	}

	/** 설정·구독·요금·찜·플랜·알림·계산 기록 행을 한 사용자에게 넣고 플랜 ID를 돌려준다 */
	private long insertFullData(long userId) {
		jdbc.update("INSERT INTO user_setting (user_id, monthly_budget, monthly_watch_minutes, watch_preset) VALUES (?, 30000, 1200, 'NORMAL')", userId);
		jdbc.update("INSERT INTO user_subscription (user_id, ott_service_id, status) VALUES (?, 1, 'FREE')", userId);
		long productId = jdbc.queryForObject(
				"INSERT INTO subscription_product (name, product_type) VALUES ('탈퇴테스트 상품', 'SINGLE') RETURNING id", Long.class);
		jdbc.update("INSERT INTO user_price_override (user_id, product_id, monthly_price) VALUES (?, ?, 9900)", userId, productId);
		long unitId = insertMovieUnit(BASE_TMDB_ID);
		jdbc.update("INSERT INTO wishlist_item (user_id, watch_unit_id, priority) VALUES (?, ?, 'MUST')", userId, unitId);
		jdbc.update("INSERT INTO notification_outbox (user_id, type, dedupe_key, payload) VALUES (?, 'MONTHLY', ?, '{}')",
				userId, "MONTHLY:withdraw:" + userId);
		long planId = jdbc.queryForObject(
				"INSERT INTO plan (user_id, status, plan_type, start_month, monthly_budget, monthly_watch_minutes, algorithm, "
						+ "must_total, must_completed, score, total_cost, all_subscribe_cost, data_as_of, activated_at) "
						+ "VALUES (?, 'ACTIVE', 'RECOMMENDED', DATE '2026-11-01', 30000, 1200, 'EXACT', 1, 1, 2, 10000, 20000, now(), now()) RETURNING id",
				Long.class, userId);
		jdbc.update("INSERT INTO calc_run (plan_id, input_hash, plan_type, algorithm, must_completed, score, total_cost, candidate_count, elapsed_ms) "
				+ "VALUES (?, ?, 'RECOMMENDED', 'EXACT', 1, 2, 10000, 5, 12)", planId, CALC_HASH);
		return planId;
	}

	/** 영화 작품과 시청 단위를 넣고 시청 단위 ID를 돌려준다(이미 있으면 그 ID) */
	private long insertMovieUnit(long tmdbId) {
		List<Long> existing = jdbc.queryForList(
				"SELECT w.id FROM watch_unit w JOIN title t ON t.id = w.title_id WHERE t.tmdb_id = ?", Long.class, tmdbId);
		if (!existing.isEmpty()) {
			return existing.get(0);
		}
		long titleId = jdbc.queryForObject(
				"INSERT INTO title (media_type, tmdb_id, title, fetched_at) VALUES ('MOVIE', ?, '탈퇴 테스트 작품', now()) RETURNING id",
				Long.class, tmdbId);
		return jdbc.queryForObject(
				"INSERT INTO watch_unit (title_id, unit_type, season_number, total_runtime_min, fetched_at) "
						+ "VALUES (?, 'MOVIE', NULL, 120, now()) RETURNING id",
				Long.class, titleId);
	}

	private int count(String table, String column, long value) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
	}

	private String movieBody(long tmdbId) {
		return """
				{"mediaType":"movie","tmdbId":%d,"seasonNumber":null,"priority":"MUST"}
				""".formatted(tmdbId);
	}

	private String settingsBody(long ottServiceId) {
		return """
				{"subscriptions":[{"ottServiceId":%d,"status":"FREE","planName":null,"billingDay":null}],"monthlyBudget":20000,"monthlyWatchMinutes":900,"watchPreset":"NORMAL","isLowestPriceMode":false}
				""".formatted(ottServiceId);
	}

	private String bearerOf(User user) {
		return new TestTokens(jwtTokenProvider).bearer(user.getId(), UserRole.USER);
	}

	private User createUser() {
		String sub = "w" + UUID.randomUUID().toString().replace("-", "");
		return userRepository.save(User.create("withdraw-" + sub + "@example.com", "탈퇴자", OAuthProvider.GOOGLE, sub));
	}
}
