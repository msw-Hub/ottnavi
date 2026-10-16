package com.ottnavi.plan.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ottnavi.catalog.domain.entity.Availability;
import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.entity.WatchUnit;
import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.enums.UnknownReason;
import com.ottnavi.catalog.domain.repository.AvailabilityRepository;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.domain.repository.WatchUnitRepository;
import com.ottnavi.catalog.domain.rule.RuntimeResult;
import com.ottnavi.collect.service.CollectDataVersionService;
import com.ottnavi.global.config.RedisConfig;
import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.product.domain.entity.ProductComponent;
import com.ottnavi.product.domain.entity.ProductPrice;
import com.ottnavi.product.domain.entity.SubscriptionProduct;
import com.ottnavi.product.domain.enums.ProductType;
import com.ottnavi.product.domain.repository.ProductComponentRepository;
import com.ottnavi.product.domain.repository.ProductPriceRepository;
import com.ottnavi.product.domain.repository.SubscriptionProductRepository;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.domain.enums.ProviderTier;
import com.ottnavi.provider.domain.repository.OttServiceRepository;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.support.TestTokens;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.enums.UserRole;
import com.ottnavi.user.domain.enums.WatchPreset;
import com.ottnavi.user.domain.repository.UserRepository;
import com.ottnavi.user.service.UserSettingService;
import com.ottnavi.wishlist.domain.entity.WishlistItem;
import com.ottnavi.wishlist.domain.enums.WishlistPriority;
import com.ottnavi.wishlist.domain.repository.WishlistItemRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 플랜 계산 API(Task 064)를 실제 보안 체인·DB·Redis로 확인한다(Docker Desktop 필요).
 * 시나리오: 서비스 0 상품 A(10,000원)와 서비스 1 상품 B(20,000원), 월 예산 20,000원, 월 600분.
 * 단위는 영화 3편 — 꼭(120분, A에서만), 보고 싶음(200분, B에서만), 여유될 때(제공처 없음).
 * 한 달에 A+B는 예산을 넘으므로 A·B를 다른 달에 구독해야 하고, 세 유형 모두 꼭 1·점수 2·총비용 30,000원이 된다.
 * 막으려는 사고: 하위 행이 다른 플랜의 달·항목을 가리키는 일, 재계산 뒤 옛 하위 행이 남는 일, 동시 계산에서 유니크 위반이 나는 일,
 * 응답 금액이 DB 저장 값과 다른 일, 보호 경로가 열리는 일.
 */
@AutoConfigureMockMvc
class PlanApiTest extends IntegrationTestSupport {

	private static final String PATH = "/api/me/plan-drafts";
	private static final int BASE_TMDB_ID = 930000; // 다른 통합 테스트와 겹치지 않는 작품 ID 대역
	private static final String NAME_PREFIX = "TASK064 ";
	private static final String EMAIL_PREFIX = "task064-";
	private static final Instant CHECKED_AT = Instant.parse("2026-10-14T15:30:00Z");
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Autowired private MockMvc mockMvc;
	@Autowired private JwtTokenProvider jwtTokenProvider;
	@Autowired private UserRepository userRepository;
	@Autowired private UserSettingService userSettingService;
	@Autowired private OttServiceRepository ottServiceRepository;
	@Autowired private SubscriptionProductRepository subscriptionProductRepository;
	@Autowired private ProductComponentRepository productComponentRepository;
	@Autowired private ProductPriceRepository productPriceRepository;
	@Autowired private TitleRepository titleRepository;
	@Autowired private WatchUnitRepository watchUnitRepository;
	@Autowired private AvailabilityRepository availabilityRepository;
	@Autowired private WishlistItemRepository wishlistItemRepository;
	@Autowired private CollectDataVersionService collectDataVersionService;
	@Autowired private CacheManager cacheManager;
	@Autowired private JdbcTemplate jdbcTemplate;

	private List<OttService> services; // 서비스 ID 오름차순

	@BeforeEach
	void 초기화() {
		cleanUp();
		cacheManager.getCache(RedisConfig.PLAN_CALC).clear();
		services = new ArrayList<>(ottServiceRepository.findAllByOrderByDisplayOrderAsc());
		services.sort(Comparator.comparing(OttService::getId));
	}

	@AfterEach
	void 정리() {
		cleanUp();
		cacheManager.getCache(RedisConfig.PLAN_CALC).clear();
	}

	/** 이 테스트가 만든 사용자·플랜·상품·작품만 FK 순서대로 지운다 */
	private void cleanUp() {
		String userIds = "SELECT id FROM users WHERE email LIKE '" + EMAIL_PREFIX + "%'";
		jdbcTemplate.update("DELETE FROM calc_run WHERE plan_id IN (SELECT id FROM plan WHERE user_id IN (" + userIds + "))");
		jdbcTemplate.update("DELETE FROM plan WHERE user_id IN (" + userIds + ")"); // 하위 행은 FK CASCADE가 지운다
		jdbcTemplate.update("DELETE FROM calc_run WHERE plan_id IS NULL"); // 재계산으로 플랜이 지워져 남은 기록
		jdbcTemplate.update("DELETE FROM wishlist_item WHERE user_id IN (" + userIds + ")");
		jdbcTemplate.update("DELETE FROM user_subscription WHERE user_id IN (" + userIds + ")");
		jdbcTemplate.update("DELETE FROM user_setting WHERE user_id IN (" + userIds + ")");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE '" + EMAIL_PREFIX + "%'");
		String productIds = "SELECT id FROM subscription_product WHERE name LIKE '" + NAME_PREFIX + "%'";
		jdbcTemplate.update("DELETE FROM product_price WHERE product_id IN (" + productIds + ")");
		jdbcTemplate.update("DELETE FROM product_component WHERE product_id IN (" + productIds + ")");
		jdbcTemplate.update("DELETE FROM subscription_product WHERE name LIKE '" + NAME_PREFIX + "%'");
		String titleIds = "SELECT id FROM title WHERE tmdb_id >= " + BASE_TMDB_ID;
		jdbcTemplate.update("DELETE FROM availability WHERE watch_unit_id IN "
				+ "(SELECT id FROM watch_unit WHERE title_id IN (" + titleIds + "))");
		jdbcTemplate.update("DELETE FROM watch_unit WHERE title_id IN (" + titleIds + ")");
		jdbcTemplate.update("DELETE FROM title WHERE tmdb_id >= " + BASE_TMDB_ID);
	}

	@Test
	void 토큰이_없으면_401이고_설정이_없으면_400이다() throws Exception {
		// given: 온보딩 전 사용자
		String bearer = bearerOf(createUser());

		// when
		// then: 비로그인 401
		mockMvc.perform(post(PATH)).andExpect(status().isUnauthorized());
		// then: 설정 없음 400
		mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
	}

	@Test
	void 초안_3종이_계약_형태로_만들어지고_응답이_DB_저장값과_같다() throws Exception {
		// given
		User user = createScenarioUser();

		// when
		String body = mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearerOf(user)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.drafts.length()").value(3))
				.andExpect(jsonPath("$.data.drafts[0].planType").value("RECOMMENDED"))
				.andExpect(jsonPath("$.data.drafts[1].planType").value("SAVER"))
				.andExpect(jsonPath("$.data.drafts[2].planType").value("SIMPLE"))
				.andReturn().getResponse().getContentAsString();

		// then: 세 유형 모두 꼭 1·점수 2·총비용 30,000원, 전체 구독 비용 90,000원, 절감액 60,000원
		String currentMonth = YearMonth.now(KST).toString();
		for (int i = 0; i < 3; i++) {
			String draft = "$.data.drafts[" + i + "]";
			assertThat((Integer) JsonPath.read(body, draft + ".mustTotal")).isEqualTo(1);
			assertThat((Integer) JsonPath.read(body, draft + ".mustCompleted")).isEqualTo(1);
			assertThat((Integer) JsonPath.read(body, draft + ".score")).isEqualTo(2);
			assertThat((Integer) JsonPath.read(body, draft + ".totalCost")).isEqualTo(30_000);
			assertThat((Integer) JsonPath.read(body, draft + ".allSubscribeCost")).isEqualTo(90_000);
			assertThat((Integer) JsonPath.read(body, draft + ".savedAmount")).isEqualTo(60_000);
			assertThat((Integer) JsonPath.read(body, draft + ".scheduledCount")).isEqualTo(2);
			assertThat((Integer) JsonPath.read(body, draft + ".totalCount")).isEqualTo(3);
			assertThat((String) JsonPath.read(body, draft + ".algorithm")).isEqualTo("EXACT");
			assertThat((String) JsonPath.read(body, draft + ".startMonth")).isEqualTo(currentMonth);
			assertThat((Integer) JsonPath.read(body, draft + ".monthlyBudget")).isEqualTo(20_000);
			assertThat((List<?>) JsonPath.read(body, draft + ".months")).hasSize(3);
			assertThat((Boolean) JsonPath.read(body, draft + ".months[0].isConfirmed")).isTrue();
			assertThat((Boolean) JsonPath.read(body, draft + ".months[1].isConfirmed")).isFalse();
			assertThat((String) JsonPath.read(body, draft + ".months[1].month"))
					.isEqualTo(YearMonth.now(KST).plusMonths(1).toString());
			// 제공처 없는 작품 하나가 남는다
			assertThat((List<?>) JsonPath.read(body, draft + ".unscheduled")).hasSize(1);
			assertThat((String) JsonPath.read(body, draft + ".unscheduled[0].reason")).isEqualTo("NO_PROVIDER");
			assertThat((String) JsonPath.read(body, draft + ".unscheduled[0].priority")).isEqualTo("MAYBE");
			// 세 달 비용 합 = 총비용, 조각은 나뉘지 않아 1/1
			int monthCostSum = (Integer) JsonPath.read(body, draft + ".months[0].cost")
					+ (Integer) JsonPath.read(body, draft + ".months[1].cost")
					+ (Integer) JsonPath.read(body, draft + ".months[2].cost");
			assertThat(monthCostSum).isEqualTo(30_000);

			// then: 응답 금액이 DB에 저장된 값과 같다
			long planId = ((Number) JsonPath.read(body, draft + ".planId")).longValue();
			String planType = JsonPath.read(body, draft + ".planType");
			Integer dbTotalCost = jdbcTemplate.queryForObject(
					"SELECT total_cost FROM plan WHERE id = ? AND status = 'DRAFT' AND plan_type = ?",
					Integer.class, planId, planType);
			assertThat(dbTotalCost).isEqualTo(30_000);
			Integer dbMonthCostSum = jdbcTemplate.queryForObject(
					"SELECT SUM(cost) FROM plan_month WHERE plan_id = ?", Integer.class, planId);
			assertThat(dbMonthCostSum).isEqualTo(30_000);
			// then: calc_run이 유형마다 1행, 알고리즘 EXACT
			Integer runCount = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM calc_run WHERE plan_id = ? AND plan_type = ? AND algorithm = 'EXACT' "
							+ "AND total_cost = 30000 AND must_completed = 1 AND score = 2",
					Integer.class, planId, planType);
			assertThat(runCount).isEqualTo(1);
		}

		// then: 배정 행의 항목·달이 같은 플랜 소속이다(어긋난 행 0건), 배정 행은 있다
		Integer mismatched = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM plan_assignment a JOIN plan_item i ON i.id = a.plan_item_id "
						+ "JOIN plan_month m ON m.id = a.plan_month_id WHERE i.plan_id <> m.plan_id", Integer.class);
		assertThat(mismatched).isZero();
		Integer assignmentCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM plan_assignment a JOIN plan_item i ON i.id = a.plan_item_id "
						+ "JOIN plan p ON p.id = i.plan_id WHERE p.user_id = ?", Integer.class, user.getId());
		assertThat(assignmentCount).isEqualTo(6); // 유형 3개 × 배정된 단위 2개
	}

	@Test
	void 다시_계산하면_옛_플랜과_하위_행이_모두_사라지고_초안은_3개다() throws Exception {
		// given: 한 번 계산한다
		User user = createScenarioUser();
		String bearer = bearerOf(user);
		List<Long> firstIds = planIds(post(PATH).header(HttpHeaders.AUTHORIZATION, bearer));

		// when: 같은 입력으로 다시 계산한다(캐시에서 온 결과라도 DRAFT는 새로 저장된다)
		List<Long> secondIds = planIds(post(PATH).header(HttpHeaders.AUTHORIZATION, bearer));

		// then: 새 ID이고 옛 ID의 plan·하위 4개 테이블 행이 없다
		assertThat(secondIds).doesNotContainAnyElementsOf(firstIds);
		String ids = firstIds.toString().replace("[", "(").replace("]", ")");
		assertThat(count("SELECT COUNT(*) FROM plan WHERE id IN " + ids)).isZero();
		assertThat(count("SELECT COUNT(*) FROM plan_month WHERE plan_id IN " + ids)).isZero();
		assertThat(count("SELECT COUNT(*) FROM plan_item WHERE plan_id IN " + ids)).isZero();
		assertThat(count("SELECT COUNT(*) FROM plan_month_product WHERE plan_month_id IN "
				+ "(SELECT id FROM plan_month WHERE plan_id IN " + ids + ")")).isZero();
		assertThat(count("SELECT COUNT(*) FROM plan_assignment WHERE plan_item_id IN "
				+ "(SELECT id FROM plan_item WHERE plan_id IN " + ids + ")")).isZero();
		assertThat(count("SELECT COUNT(*) FROM plan WHERE user_id = " + user.getId() + " AND status = 'DRAFT'"))
				.isEqualTo(3);

		// then: planCalc 캐시에 입력 해시 키가 남아 있다(두 번째 계산은 이 값을 쓴다)
		String inputHash = jdbcTemplate.queryForObject(
				"SELECT input_hash FROM calc_run WHERE plan_id = ? AND plan_type = 'RECOMMENDED'", String.class,
				secondIds.get(0));
		assertThat(cacheManager.getCache(RedisConfig.PLAN_CALC).get("e1:" + inputHash)).isNotNull();
	}

	@Test
	void 같은_사용자의_동시_계산은_모두_성공하고_초안은_3개만_남는다() throws Exception {
		// given
		User user = createScenarioUser();
		String bearer = bearerOf(user);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);

		// when: 두 요청이 동시에 출발한다
		List<Future<Integer>> futures = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			futures.add(pool.submit(() -> {
				ready.countDown();
				go.await();
				return mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearer))
						.andReturn().getResponse().getStatus();
			}));
		}
		ready.await();
		go.countDown();

		// then: 둘 다 201이고 초안은 3개다(나중 요청이 덮어쓴다)
		for (Future<Integer> future : futures) {
			assertThat(future.get()).isEqualTo(201);
		}
		pool.shutdown();
		assertThat(count("SELECT COUNT(*) FROM plan WHERE user_id = " + user.getId() + " AND status = 'DRAFT'"))
				.isEqualTo(3);
	}

	@Test
	void 데이터_기준_시각은_완료된_수집이_있으면_그_시각이고_없으면_계산_시각이다() throws Exception {
		// given
		User user = createScenarioUser();
		Instant before = Instant.now();
		Optional<Instant> collected = collectDataVersionService.findDataAsOf();

		// when
		long planId = planIds(post(PATH).header(HttpHeaders.AUTHORIZATION, bearerOf(user))).get(0);

		// then
		OffsetDateTime stored = jdbcTemplate.queryForObject("SELECT data_as_of FROM plan WHERE id = ?",
				OffsetDateTime.class, planId);
		if (collected.isPresent()) {
			assertThat(stored.toInstant().truncatedTo(ChronoUnit.MILLIS))
					.isEqualTo(collected.get().truncatedTo(ChronoUnit.MILLIS));
		} else {
			assertThat(stored.toInstant()).isBetween(before.minusSeconds(5), Instant.now().plusSeconds(5));
		}
	}

	// 데이터 준비

	private List<Long> planIds(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
			throws Exception {
		String body = mockMvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			ids.add(((Number) JsonPath.read(body, "$.data.drafts[" + i + "].planId")).longValue());
		}
		return ids;
	}

	private int count(String sql) {
		return jdbcTemplate.queryForObject(sql, Integer.class);
	}

	private String bearerOf(User user) {
		return new TestTokens(jwtTokenProvider).bearer(user.getId(), UserRole.USER);
	}

	private User createUser() {
		String sub = UUID.randomUUID().toString().replace("-", "");
		return userRepository.save(User.create(EMAIL_PREFIX + sub + "@example.com", "테스터", OAuthProvider.GOOGLE, sub));
	}

	/** 시나리오 데이터(상품 2개, 영화 3편, 찜 3개, 설정)를 가진 사용자를 만든다 */
	private User createScenarioUser() {
		User user = createUser();
		saveProduct("A", services.get(0), 10_000);
		saveProduct("B", services.get(1), 20_000);
		userSettingService.updateSettings(user.getId(), 20_000, 600, WatchPreset.NORMAL, new ArrayList<>());

		WatchUnit must = saveMovie(BASE_TMDB_ID + 1, 120);
		WatchUnit want = saveMovie(BASE_TMDB_ID + 2, 200);
		WatchUnit maybe = saveMovie(BASE_TMDB_ID + 3, 90);
		saveStatuses(must, 0);
		saveStatuses(want, 1);
		saveStatuses(maybe, -1); // 7개 서비스 모두 없음
		wishlistItemRepository.save(WishlistItem.create(user.getId(), must.getId(), WishlistPriority.MUST));
		wishlistItemRepository.save(WishlistItem.create(user.getId(), want.getId(), WishlistPriority.WANT));
		wishlistItemRepository.save(WishlistItem.create(user.getId(), maybe.getId(), WishlistPriority.MAYBE));
		return user;
	}

	private void saveProduct(String name, OttService service, int price) {
		SubscriptionProduct product = subscriptionProductRepository
				.save(SubscriptionProduct.create(NAME_PREFIX + name, ProductType.SINGLE));
		productComponentRepository.save(ProductComponent.create(product, service, ProviderTier.STANDARD));
		productPriceRepository.save(ProductPrice.create(product, price, LocalDate.now(KST).minusDays(30), Instant.now()));
	}

	private WatchUnit saveMovie(int tmdbId, int runtimeMin) {
		Title title = titleRepository.save(Title.create(MediaType.MOVIE, tmdbId, "작품 " + tmdbId, CHECKED_AT));
		return watchUnitRepository.save(WatchUnit.createMovie(title, new RuntimeResult(1, 1, runtimeMin, 0, false), CHECKED_AT));
	}

	/** 서비스 7개 모두에 제공 상태를 저장한다. availableIndex 번째만 있음이고 -1이면 모두 없음이다 */
	private void saveStatuses(WatchUnit unit, int availableIndex) {
		for (int i = 0; i < services.size(); i++) {
			AvailabilityStatus status = AvailabilityStatus.NOT_AVAILABLE;
			if (i == availableIndex) {
				status = AvailabilityStatus.AVAILABLE;
			}
			availabilityRepository.save(Availability.create(unit, services.get(i), status, (UnknownReason) null,
					status == AvailabilityStatus.AVAILABLE, false, CHECKED_AT));
		}
	}
}
