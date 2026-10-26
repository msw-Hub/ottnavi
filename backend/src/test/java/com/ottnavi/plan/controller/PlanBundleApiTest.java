package com.ottnavi.plan.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
 * 번들 상품이 있을 때의 플랜 계산 API(Task 076)를 실제 보안 체인·DB·Redis로 확인한다(Docker Desktop 필요).
 * 시나리오: 서비스 0 단품 A(10,000원), 서비스 1 단품 B(10,000원), 서비스 0+1 번들 C(12,000원), 월 예산 20,000원.
 * 작품은 셋 — 서비스 1에서만, 서비스 0에서만, 서비스 0·1 모두(전부 꼭). 셋을 보려면 A+B(20,000원)보다 C(12,000원)가 싸다.
 * 막으려는 사고: 번들이 후보에 못 들어가는 일, 배정 서비스가 (제공 서비스 ∩ 구성) 최소 ID가 아닌 일,
 * 응답 상품의 ottServiceIds가 구성 목록과 다른 일, 저가형 번들이 모드와 무관하게 후보가 되는 일.
 */
@AutoConfigureMockMvc
class PlanBundleApiTest extends IntegrationTestSupport {

	private static final String PATH = "/api/me/plan-drafts";
	private static final int BASE_TMDB_ID = 960000; // 다른 통합 테스트와 겹치지 않는 작품 ID 대역
	private static final String NAME_PREFIX = "TASK076 ";
	private static final String EMAIL_PREFIX = "task076-";
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

	private List<OttService> services; // 서비스 ID 오름차순
	private SubscriptionProduct bundle;

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
	void 표준_번들이_선택되면_배정_서비스는_제공_서비스와_구성의_교집합_최소_ID이고_응답은_구성_목록이다() throws Exception {
		// given: 표준 번들, 모드 끔
		User user = createScenarioUser(false, false);

		// when
		String body = mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearerOf(user)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		// then: 세 유형 모두 첫 달 번들 하나로 12,000원, 꼭 3편 완료
		long serviceId0 = services.get(0).getId();
		long serviceId1 = services.get(1).getId();
		for (int i = 0; i < 3; i++) {
			String draft = "$.data.drafts[" + i + "]";
			assertThat((Integer) JsonPath.read(body, draft + ".totalCost")).isEqualTo(12_000);
			assertThat((Integer) JsonPath.read(body, draft + ".mustCompleted")).isEqualTo(3);

			// then: 응답 상품은 번들이고 ottServiceIds가 구성 목록이다(오름차순)
			assertThat((List<?>) JsonPath.read(body, draft + ".months[0].products")).hasSize(1);
			assertThat(((Number) JsonPath.read(body, draft + ".months[0].products[0].productId")).longValue())
					.isEqualTo(bundle.getId());
			List<Number> ottServiceIds = JsonPath.read(body, draft + ".months[0].products[0].ottServiceIds");
			assertThat(ottServiceIds).extracting(Number::longValue).containsExactly(serviceId0, serviceId1);

			// then: DB의 배정 서비스 — 서비스 1에서만 제공되는 작품은 서비스 1, 서비스 0에서만·둘 다 제공되는 작품은 서비스 0
			long planId = ((Number) JsonPath.read(body, draft + ".planId")).longValue();
			assertThat(assignedServiceId(planId, BASE_TMDB_ID + 1)).isEqualTo(serviceId1);
			assertThat(assignedServiceId(planId, BASE_TMDB_ID + 2)).isEqualTo(serviceId0);
			assertThat(assignedServiceId(planId, BASE_TMDB_ID + 3)).isEqualTo(serviceId0);
		}
	}

	@Test
	void 저가형_번들은_모드를_끄면_후보가_아니고_켜면_후보가_된다() throws Exception {
		// given: 저가형 번들, 모드 끔
		User user = createScenarioUser(true, false);

		// when: 모드 끔
		String off = mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearerOf(user)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		// then: 번들이 빠져 단품 A+B를 구독한다(20,000원)
		assertThat((Integer) JsonPath.read(off, "$.data.drafts[0].totalCost")).isEqualTo(20_000);

		// when: 모드를 켜고 다시 계산한다
		userSettingService.updateSettings(user.getId(), 20_000, 600, WatchPreset.NORMAL, true, new ArrayList<>());
		String on = mockMvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, bearerOf(user)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		// then: 번들이 후보가 되어 12,000원이 된다
		assertThat((Integer) JsonPath.read(on, "$.data.drafts[0].totalCost")).isEqualTo(12_000);
		assertThat(((Number) JsonPath.read(on, "$.data.drafts[0].months[0].products[0].productId")).longValue())
				.isEqualTo(bundle.getId());
	}

	// 데이터 준비

	private long assignedServiceId(long planId, int tmdbId) {
		return jdbcTemplate.queryForObject(
				"SELECT a.ott_service_id FROM plan_assignment a JOIN plan_item i ON i.id = a.plan_item_id "
						+ "JOIN watch_unit w ON w.id = i.watch_unit_id JOIN title t ON t.id = w.title_id "
						+ "WHERE i.plan_id = ? AND t.tmdb_id = ?", Long.class, planId, tmdbId);
	}

	private String bearerOf(User user) {
		return new TestTokens(jwtTokenProvider).bearer(user.getId(), UserRole.USER);
	}

	/** 단품 A·B, 번들 C(저가형 여부는 인자), 영화 3편, 찜 3개, 설정(모드는 인자)을 가진 사용자를 만든다 */
	private User createScenarioUser(boolean bundleLowCost, boolean lowestPriceMode) {
		String sub = UUID.randomUUID().toString().replace("-", "");
		User user = userRepository.save(User.create(EMAIL_PREFIX + sub + "@example.com", "테스터", OAuthProvider.GOOGLE, sub));

		saveProduct("A", ProductType.SINGLE, false, 10_000, services.get(0));
		saveProduct("B", ProductType.SINGLE, false, 10_000, services.get(1));
		bundle = saveProduct("C", ProductType.BUNDLE, bundleLowCost, 12_000, services.get(0), services.get(1));
		userSettingService.updateSettings(user.getId(), 20_000, 600, WatchPreset.NORMAL, lowestPriceMode, new ArrayList<>());

		WatchUnit onlyService1 = saveMovie(BASE_TMDB_ID + 1, 120);
		WatchUnit onlyService0 = saveMovie(BASE_TMDB_ID + 2, 120);
		WatchUnit service0And1 = saveMovie(BASE_TMDB_ID + 3, 120);
		saveStatuses(onlyService1, Set.of(1));
		saveStatuses(onlyService0, Set.of(0));
		saveStatuses(service0And1, Set.of(0, 1));
		wishlistItemRepository.save(WishlistItem.create(user.getId(), onlyService1.getId(), WishlistPriority.MUST));
		wishlistItemRepository.save(WishlistItem.create(user.getId(), onlyService0.getId(), WishlistPriority.MUST));
		wishlistItemRepository.save(WishlistItem.create(user.getId(), service0And1.getId(), WishlistPriority.MUST));
		return user;
	}

	private SubscriptionProduct saveProduct(String name, ProductType type, boolean lowCost, int price, OttService... components) {
		SubscriptionProduct product = subscriptionProductRepository
				.save(SubscriptionProduct.create(NAME_PREFIX + name, type, lowCost));
		for (OttService service : components) {
			productComponentRepository.save(ProductComponent.create(product, service, ProviderTier.STANDARD));
		}
		productPriceRepository.save(ProductPrice.create(product, price, LocalDate.now(KST).minusDays(30), Instant.now()));
		return product;
	}

	private WatchUnit saveMovie(int tmdbId, int runtimeMin) {
		Title title = titleRepository.save(Title.create(MediaType.MOVIE, tmdbId, "작품 " + tmdbId, CHECKED_AT));
		return watchUnitRepository.save(WatchUnit.createMovie(title, new RuntimeResult(1, 1, runtimeMin, 0, false), CHECKED_AT));
	}

	/** 서비스 7개 모두에 제공 상태를 저장한다. availableIndexes에 든 순번만 있음이고 나머지는 없음이다 */
	private void saveStatuses(WatchUnit unit, Set<Integer> availableIndexes) {
		for (int i = 0; i < services.size(); i++) {
			AvailabilityStatus status = AvailabilityStatus.NOT_AVAILABLE;
			if (availableIndexes.contains(i)) {
				status = AvailabilityStatus.AVAILABLE;
			}
			availabilityRepository.save(Availability.create(unit, services.get(i), status, (UnknownReason) null,
					status == AvailabilityStatus.AVAILABLE, false, CHECKED_AT));
		}
	}
}
