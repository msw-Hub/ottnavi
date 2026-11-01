package com.ottnavi.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.global.error.AuthStoreUnavailableException;
import com.ottnavi.global.security.token.RefreshTokenStore;
import com.ottnavi.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 데모 로그인 때 이전 방문자의 데이터를 지우고 새로 시작하는지 실제 DB·Redis로 확인한다(Docker Desktop 필요).
 * 아이디·비밀번호는 테스트 전용 더미 값이다.
 * 막으려는 사고: 이전 방문자의 설정·찜·플랜이 다음 방문자에게 보이는 일, 이전 세션의 Refresh Token이 계속 쓰이는 일,
 * Redis 폐기가 실패했는데 DB만 지워져 세션과 데이터가 어긋나는 일.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"app.demo-login.login-id=" + DemoLoginResetApiTest.LOGIN_ID,
		"app.demo-login.password=" + DemoLoginResetApiTest.PASSWORD})
class DemoLoginResetApiTest extends IntegrationTestSupport {

	static final String LOGIN_ID = "test-demo-id";
	static final String PASSWORD = "test-only-dummy-password-16+"; // 테스트 전용 더미 값
	private static final String URL = "/api/auth/demo-login";
	private static final String ORIGIN = "http://localhost:5173"; // application-local.yml의 app.frontend-origin 기본값
	private static final int RESET_TMDB_ID = 985000; // 다른 통합 테스트와 겹치지 않는 작품 ID

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoSpyBean
	private RefreshTokenStore refreshTokenStore; // 진짜 Redis 저장소를 감싸서 일부 호출만 실패시킨다

	@BeforeEach
	void setUp() {
		cleanUp();
	}

	@AfterEach
	void tearDown() {
		cleanUp();
	}

	private void cleanUp() {
		jdbc.update("DELETE FROM users WHERE oauth_provider = 'DEMO'");
		String titleIds = "SELECT id FROM title WHERE tmdb_id = " + RESET_TMDB_ID;
		jdbc.update("DELETE FROM wishlist_item WHERE watch_unit_id IN (SELECT id FROM watch_unit WHERE title_id IN (" + titleIds + "))");
		jdbc.update("DELETE FROM watch_unit WHERE title_id IN (" + titleIds + ")");
		jdbc.update("DELETE FROM title WHERE tmdb_id = " + RESET_TMDB_ID);
	}

	@Test
	void 첫_로그인은_데모_행을_하나_만든다() throws Exception {
		// when
		mockMvc.perform(login()).andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("DEMO"));

		// then
		assertThat(demoUserIds()).hasSize(1);
	}

	@Test
	void 다시_로그인하면_이전_데이터가_모두_없고_새_사용자_id가_발급된다() throws Exception {
		// given: 설정·구독·찜·플랜(ACTIVE, DRAFT)이 있는 데모 계정
		mockMvc.perform(login()).andExpect(status().isOk());
		long oldUserId = demoUserIds().get(0);
		insertData(oldUserId);

		// when
		mockMvc.perform(login()).andExpect(status().isOk());

		// then: 행은 하나뿐이고 id가 바뀌었으며, 이전 id의 데이터는 모두 사라졌다
		List<Long> ids = demoUserIds();
		assertThat(ids).hasSize(1);
		assertThat(ids.get(0)).isNotEqualTo(oldUserId);
		assertThat(count("users", "id", oldUserId)).isZero();
		assertThat(count("user_setting", "user_id", oldUserId)).isZero();
		assertThat(count("user_subscription", "user_id", oldUserId)).isZero();
		assertThat(count("wishlist_item", "user_id", oldUserId)).isZero();
		assertThat(count("plan", "user_id", oldUserId)).isZero();
		assertThat(count("notification_outbox", "user_id", oldUserId)).isZero();
		assertThat(count("user_setting", "user_id", ids.get(0))).isZero();
		assertThat(count("plan", "user_id", ids.get(0))).isZero();
	}

	@Test
	void Redis_폐기가_503으로_실패하면_DB_데이터가_그대로_남는다() throws Exception {
		// given
		mockMvc.perform(login()).andExpect(status().isOk());
		long oldUserId = demoUserIds().get(0);
		insertData(oldUserId);
		doThrow(new AuthStoreUnavailableException("저장소 장애", new RuntimeException("원인")))
				.when(refreshTokenStore).revokeAllOf(anyLong());

		// when & then: 503으로 끝난다
		mockMvc.perform(login())
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.errorCode").value("AUTH_STORE_UNAVAILABLE"));

		// then: 행과 데이터가 지워지지 않았다
		assertThat(demoUserIds()).containsExactly(oldUserId);
		assertThat(count("user_setting", "user_id", oldUserId)).isEqualTo(1);
		assertThat(count("wishlist_item", "user_id", oldUserId)).isEqualTo(1);
		assertThat(count("plan", "user_id", oldUserId)).isEqualTo(2);
	}

	@Test
	void 이전_데모_세션의_RT로_재발급하면_401이다() throws Exception {
		// given: 첫 방문자의 RT
		MvcResult first = mockMvc.perform(login()).andExpect(status().isOk()).andReturn();
		String oldRefreshToken = cookieValue(first.getResponse().getHeader(HttpHeaders.SET_COOKIE));

		// when: 다음 방문자가 로그인해 데이터가 초기화된다
		MvcResult second = mockMvc.perform(login()).andExpect(status().isOk()).andReturn();
		String newRefreshToken = cookieValue(second.getResponse().getHeader(HttpHeaders.SET_COOKIE));

		// then: 이전 RT는 거부되고 새 RT는 쓸 수 있다
		mockMvc.perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("rt", oldRefreshToken)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("rt", newRefreshToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.role").value("DEMO"));
	}

	/** 설정·구독·찜·플랜 2개(ACTIVE, DRAFT)·알림 행을 넣는다 */
	private void insertData(long userId) {
		jdbc.update("INSERT INTO user_setting (user_id, monthly_budget, monthly_watch_minutes, watch_preset) VALUES (?, 30000, 1200, 'NORMAL')", userId);
		jdbc.update("INSERT INTO user_subscription (user_id, ott_service_id, status) VALUES (?, (SELECT min(id) FROM ott_service), 'FREE')", userId);
		long titleId = jdbc.queryForObject(
				"INSERT INTO title (media_type, tmdb_id, title, fetched_at) VALUES ('MOVIE', ?, '데모 초기화 테스트 작품', now()) RETURNING id",
				Long.class, RESET_TMDB_ID);
		long unitId = jdbc.queryForObject(
				"INSERT INTO watch_unit (title_id, unit_type, season_number, total_runtime_min, fetched_at) "
						+ "VALUES (?, 'MOVIE', NULL, 120, now()) RETURNING id", Long.class, titleId);
		jdbc.update("INSERT INTO wishlist_item (user_id, watch_unit_id, priority) VALUES (?, ?, 'MUST')", userId, unitId);
		jdbc.update("INSERT INTO notification_outbox (user_id, type, dedupe_key, payload) VALUES (?, 'MONTHLY', ?, '{}')",
				userId, "MONTHLY:demo-reset:" + userId);
		String planColumns = "(user_id, status, plan_type, start_month, monthly_budget, monthly_watch_minutes, algorithm, "
				+ "must_total, must_completed, score, total_cost, all_subscribe_cost, data_as_of, activated_at) ";
		jdbc.update("INSERT INTO plan " + planColumns
				+ "VALUES (?, 'ACTIVE', 'RECOMMENDED', DATE '2026-11-01', 30000, 1200, 'EXACT', 1, 1, 2, 10000, 20000, now(), now())", userId);
		jdbc.update("INSERT INTO plan " + planColumns
				+ "VALUES (?, 'DRAFT', 'SAVER', DATE '2026-11-01', 30000, 1200, 'EXACT', 1, 1, 2, 10000, 20000, now(), NULL)", userId);
	}

	private MockHttpServletRequestBuilder login() {
		return post(URL)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\": \"" + LOGIN_ID + "\", \"password\": \"" + PASSWORD + "\"}");
	}

	private List<Long> demoUserIds() {
		return jdbc.queryForList("SELECT id FROM users WHERE oauth_provider = 'DEMO'", Long.class);
	}

	private int count(String table, String column, long value) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
	}

	/** Set-Cookie 헤더에서 쿠키 값만 꺼낸다 */
	private String cookieValue(String setCookie) {
		return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
	}
}
