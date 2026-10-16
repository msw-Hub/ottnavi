package com.ottnavi.plan.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 플랜 저장 API(Task 066)를 실제 보안 체인·DB·Redis로 확인한다(Docker Desktop 필요).
 * 시나리오는 플랜 계산 API 테스트와 같다: 상품 A(10,000원)·B(20,000원), 월 예산 20,000원, 영화 3편.
 * 수집 기준 시각은 CollectDataVersionService를 목으로 바꿔 제어한다(기본은 완료된 수집 없음).
 * 막으려는 사고: 저장 때 부분 유니크 위반(500), 이전 플랜 연결 누락, 남의 초안·이미 저장한 초안 저장,
 * 오래된 초안이 저장되는 일, 같은 사용자의 동시 저장이 둘 다 성공하거나 500이 되는 일.
 */
@AutoConfigureMockMvc
class PlanActivateApiTest extends IntegrationTestSupport {

	private static final String DRAFTS_PATH = "/api/me/plan-drafts";
	private static final String PLANS_PATH = "/api/me/plans";
	private static final int BASE_TMDB_ID = 940000; // 다른 통합 테스트와 겹치지 않는 작품 ID 대역
	private static final int MAX_TMDB_ID = 949999;
	private static final String NAME_PREFIX = "TASK066 ";
	private static final String EMAIL_PREFIX = "task066-";
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
	@Autowired private CacheManager cacheManager;
	@Autowired private JdbcTemplate jdbcTemplate;
	@MockitoBean private CollectDataVersionService collectDataVersionService; // 기본: 완료된 수집 없음(빈 Optional)

	private List<OttService> services; // 서비스 ID 오름차순

	@BeforeEach
	void 초기화() {
		cleanUp();
		cacheManager.getCache(RedisConfig.PLAN_CALC).clear();
		services = new ArrayList<>(ottServiceRepository.findAllByOrderByDisplayOrderAsc());
		services.sort(Comparator.comparing(OttService::getId));
		when(collectDataVersionService.findDataAsOf()).thenReturn(Optional.empty());
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
		jdbcTemplate.update("DELETE FROM calc_run WHERE plan_id IS NULL");
		jdbcTemplate.update("DELETE FROM wishlist_item WHERE user_id IN (" + userIds + ")");
		jdbcTemplate.update("DELETE FROM user_subscription WHERE user_id IN (" + userIds + ")");
		jdbcTemplate.update("DELETE FROM user_setting WHERE user_id IN (" + userIds + ")");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE '" + EMAIL_PREFIX + "%'");
		String productIds = "SELECT id FROM subscription_product WHERE name LIKE '" + NAME_PREFIX + "%'";
		jdbcTemplate.update("DELETE FROM product_price WHERE product_id IN (" + productIds + ")");
		jdbcTemplate.update("DELETE FROM product_component WHERE product_id IN (" + productIds + ")");
		jdbcTemplate.update("DELETE FROM subscription_product WHERE name LIKE '" + NAME_PREFIX + "%'");
		String titleIds = "SELECT id FROM title WHERE tmdb_id BETWEEN " + BASE_TMDB_ID + " AND " + MAX_TMDB_ID;
		jdbcTemplate.update("DELETE FROM availability WHERE watch_unit_id IN "
				+ "(SELECT id FROM watch_unit WHERE title_id IN (" + titleIds + "))");
		jdbcTemplate.update("DELETE FROM watch_unit WHERE title_id IN (" + titleIds + ")");
		jdbcTemplate.update("DELETE FROM title WHERE tmdb_id BETWEEN " + BASE_TMDB_ID + " AND " + MAX_TMDB_ID);
	}

	@Test
	void 저장하면_ACTIVE_1개만_남고_재계산_뒤_다른_유형을_저장하면_이전_플랜이_보관된다() throws Exception {
		// given: 계산한 뒤 추천형을 저장한다
		User user = createScenarioUser();
		String bearer = bearerOf(user);
		List<Long> firstIds = calculate(bearer);

		// when
		String body = mockMvc.perform(savePlan(bearer, firstIds.get(0)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.planId").value(firstIds.get(0)))
				.andExpect(jsonPath("$.data.planType").value("RECOMMENDED"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andReturn().getResponse().getContentAsString();

		// then: 응답 시각은 KST 오프셋이고, ACTIVE 1·DRAFT 0, 이전 플랜은 없다
		assertThat((String) JsonPath.read(body, "$.data.activatedAt")).endsWith("+09:00");
		assertThat(countByStatus(user, "ACTIVE")).isEqualTo(1);
		assertThat(countByStatus(user, "DRAFT")).isZero();
		assertThat(previousPlanIdOf(firstIds.get(0))).isNull();

		// when: 재계산하고 다른 유형(절약형)을 저장한다
		List<Long> secondIds = calculate(bearer);
		mockMvc.perform(savePlan(bearer, secondIds.get(1)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.planType").value("SAVER"));

		// then: 이전 ACTIVE는 ARCHIVED, 새 ACTIVE의 previous_plan_id는 옛 ACTIVE ID, 유니크 위반 없음
		assertThat(statusOf(firstIds.get(0))).isEqualTo("ARCHIVED");
		assertThat(statusOf(secondIds.get(1))).isEqualTo("ACTIVE");
		assertThat(previousPlanIdOf(secondIds.get(1))).isEqualTo(firstIds.get(0));
		assertThat(countByStatus(user, "ACTIVE")).isEqualTo(1);
		assertThat(countByStatus(user, "DRAFT")).isZero();
		assertThat(countByStatus(user, "ARCHIVED")).isEqualTo(1);
	}

	@Test
	void 남의_초안과_이미_저장한_초안은_404이고_본문이_잘못되면_400이다() throws Exception {
		// given: 사용자 A가 계산하고 저장한다. 사용자 B도 계산한다
		User owner = createScenarioUser();
		String ownerBearer = bearerOf(owner);
		List<Long> ownerIds = calculate(ownerBearer);
		User other = createUser();
		userSettingService.updateSettings(other.getId(), 20_000, 600, WatchPreset.NORMAL, new ArrayList<>());
		String otherBearer = bearerOf(other);

		// when, then: B가 A의 초안을 저장하려 하면 404이고 A의 초안은 그대로다
		mockMvc.perform(savePlan(otherBearer, ownerIds.get(0)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("PLAN_DRAFT_NOT_FOUND"));
		assertThat(countByStatus(owner, "DRAFT")).isEqualTo(3);

		// when, then: A가 저장하면 201, 같은 ID를 다시 저장하면 404(이미 ACTIVE)
		mockMvc.perform(savePlan(ownerBearer, ownerIds.get(0))).andExpect(status().isCreated());
		mockMvc.perform(savePlan(ownerBearer, ownerIds.get(0)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("PLAN_DRAFT_NOT_FOUND"));

		// then: 삭제된 다른 유형 초안도 404, planId가 0이면 400
		mockMvc.perform(savePlan(ownerBearer, ownerIds.get(1))).andExpect(status().isNotFound());
		mockMvc.perform(savePlan(ownerBearer, 0L)).andExpect(status().isBadRequest());
	}

	@Test
	void 계산_뒤_수집이_더_늦게_끝났으면_409이고_초안은_그대로다() throws Exception {
		// given: 수집 이력이 없을 때 계산한다(초안 기준 시각은 계산 시각)
		User user = createScenarioUser();
		String bearer = bearerOf(user);
		List<Long> ids = calculate(bearer);

		// when: 그 뒤 수집이 끝났다
		when(collectDataVersionService.findDataAsOf()).thenReturn(Optional.of(Instant.now().plusSeconds(3600)));

		// then: 409이고 DRAFT 3개가 남는다
		mockMvc.perform(savePlan(bearer, ids.get(0)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("PLAN_DRAFT_STALE"));
		assertThat(countByStatus(user, "DRAFT")).isEqualTo(3);
		assertThat(countByStatus(user, "ACTIVE")).isZero();
	}

	@Test
	void 같은_사용자가_두_유형을_동시에_저장하면_하나만_성공한다() throws Exception {
		// given
		User user = createScenarioUser();
		String bearer = bearerOf(user);
		List<Long> ids = calculate(bearer);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);

		// when: 서로 다른 유형을 동시에 저장한다
		List<Future<Integer>> futures = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			Long planId = ids.get(i);
			futures.add(pool.submit(() -> {
				ready.countDown();
				go.await();
				return mockMvc.perform(savePlan(bearer, planId)).andReturn().getResponse().getStatus();
			}));
		}
		ready.await();
		go.countDown();
		List<Integer> statuses = new ArrayList<>();
		for (Future<Integer> future : futures) {
			statuses.add(future.get());
		}
		pool.shutdown();

		// then: 201 하나와 404 하나(500 없음), ACTIVE 1·DRAFT 0
		Collections.sort(statuses);
		assertThat(statuses).containsExactly(201, 404);
		assertThat(countByStatus(user, "ACTIVE")).isEqualTo(1);
		assertThat(countByStatus(user, "DRAFT")).isZero();
	}

	// 초안·현재 플랜 조회(Task 066 2단계)

	@Test
	void 계산_직후_초안_조회_응답은_계산_응답과_같다() throws Exception {
		// given: 계산한 응답의 data
		User user = createScenarioUser();
		String bearer = bearerOf(user);
		String calculated = mockMvc.perform(post(DRAFTS_PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

		// when
		String listed = mockMvc.perform(get(DRAFTS_PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		// then: 달·상품·배정·남은 작품의 값과 순서가 모두 같다
		Object calculatedData = JsonPath.read(calculated, "$.data");
		Object listedData = JsonPath.read(listed, "$.data");
		assertThat(listedData).isEqualTo(calculatedData);
		assertThat((List<?>) JsonPath.read(listed, "$.data.drafts")).hasSize(3);
	}

	@Test
	void 저장하면_초안_조회는_빈_배열이고_현재_플랜은_저장한_초안_내용이다() throws Exception {
		// given: 계산한 뒤 간편형이 아닌 절약형을 저장한다
		User user = createScenarioUser();
		String bearer = bearerOf(user);
		String calculated = mockMvc.perform(post(DRAFTS_PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		Map<String, Object> savedDraft = JsonPath.read(calculated, "$.data.drafts[1]");
		Long planId = ((Number) savedDraft.get("planId")).longValue();

		// when
		mockMvc.perform(savePlan(bearer, planId)).andExpect(status().isCreated());

		// then: ACTIVE 1·DRAFT 0이고 초안 조회는 빈 배열이다
		assertThat(countByStatus(user, "ACTIVE")).isEqualTo(1);
		assertThat(countByStatus(user, "DRAFT")).isZero();
		mockMvc.perform(get(DRAFTS_PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.drafts").isEmpty());

		// then: 현재 플랜은 저장한 초안의 모든 필드와 같고 저장 시각(KST)이 더해진다
		String current = mockMvc.perform(get(PLANS_PATH + "/current").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Map<String, Object> plan = new HashMap<>(JsonPath.<Map<String, Object>>read(current, "$.data.plan"));
		assertThat((String) plan.remove("activatedAt")).endsWith("+09:00");
		assertThat(plan).isEqualTo(savedDraft);
	}

	@Test
	void 저장한_플랜이_없으면_현재_플랜_조회는_200이고_plan이_null이다() throws Exception {
		// given: 계산만 하고 저장하지 않은 사용자
		User user = createScenarioUser();
		String bearer = bearerOf(user);
		calculate(bearer);

		// when, then
		mockMvc.perform(get(PLANS_PATH + "/current").header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.plan").value(nullValue()));
	}

	// 요청·조회 보조

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder savePlan(String bearer,
			Long planId) {
		return post(PLANS_PATH).header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
				.content("{\"planId\":" + planId + "}");
	}

	/** 계산 API를 호출해 초안 ID를 추천형·절약형·간편형 순으로 돌려준다 */
	private List<Long> calculate(String bearer) throws Exception {
		String body = mockMvc.perform(post(DRAFTS_PATH).header(HttpHeaders.AUTHORIZATION, bearer))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			ids.add(((Number) JsonPath.read(body, "$.data.drafts[" + i + "].planId")).longValue());
		}
		return ids;
	}

	private int countByStatus(User user, String status) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plan WHERE user_id = ? AND status = ?", Integer.class,
				user.getId(), status);
	}

	private String statusOf(Long planId) {
		return jdbcTemplate.queryForObject("SELECT status FROM plan WHERE id = ?", String.class, planId);
	}

	private Long previousPlanIdOf(Long planId) {
		return jdbcTemplate.queryForObject("SELECT previous_plan_id FROM plan WHERE id = ?", Long.class, planId);
	}

	// 데이터 준비

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
		Title title = titleRepository.save(Title.create(com.ottnavi.catalog.domain.enums.MediaType.MOVIE, tmdbId,
				"작품 " + tmdbId, CHECKED_AT));
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
