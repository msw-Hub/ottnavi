package com.ottnavi.plan.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.global.config.RedisConfig;
import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.plan.service.PlanActivateService;
import com.ottnavi.plan.service.PlanCalculateService;
import com.ottnavi.plan.service.model.PlanDraftView;
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
import com.ottnavi.user.domain.enums.ServiceSubscriptionStatus;
import com.ottnavi.user.domain.enums.UserRole;
import com.ottnavi.user.domain.enums.WatchPreset;
import com.ottnavi.user.domain.repository.UserRepository;
import com.ottnavi.user.service.UserSettingService;
import com.ottnavi.user.service.model.SubscriptionState;
import com.ottnavi.wishlist.domain.entity.WishlistItem;
import com.ottnavi.wishlist.domain.enums.WishlistPriority;
import com.ottnavi.wishlist.domain.repository.WishlistItemRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 알림 메일 예시 API(Task 110)를 ACTIVE 플랜이 있는 DEMO 사용자로 확인한다(Docker Desktop 필요).
 * 시나리오: 상품 A(서비스 0, 10,000원)·B(서비스 1, 20,000원), 월 예산 20,000원, 영화 3편(꼭: 서비스 0, 보고싶음: 서비스 1, 여유될 때: 없음).
 * 막으려는 사고: 플랜이 있는데 예시 메일 2통이 안 만들어지는 일, 예시를 만들다가 outbox에 행이 쌓이는 일,
 * 이번 달 가입할 상품이 없는데 구독 안내 예시가 나오는 일.
 */
@AutoConfigureMockMvc
class PlanMailPreviewActivePlanApiTest extends IntegrationTestSupport {

	private static final String PATH = "/api/me/plans/current/mail-preview";
	private static final int BASE_TMDB_ID = 990000; // 다른 통합 테스트와 겹치지 않는 작품 ID 대역
	private static final int MAX_TMDB_ID = 990999;
	private static final String NAME_PREFIX = "TASK110 ";
	private static final String EMAIL_PREFIX = "task110-preview-";
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
	@Autowired private PlanCalculateService planCalculateService;
	@Autowired private PlanActivateService planActivateService;
	@Autowired private CacheManager cacheManager;
	@Autowired private JdbcTemplate jdbc;
	@MockitoBean private CollectDataVersionService collectDataVersionService; // 기본: 완료된 수집 없음(계산 시각이 기준 시각)

	private List<OttService> services; // 서비스 ID 오름차순

	@BeforeEach
	void 초기화() {
		cleanUp();
		cacheManager.getCache(RedisConfig.PLAN_CALC).clear();
		services = new ArrayList<>(ottServiceRepository.findAllByOrderByDisplayOrderAsc());
		services.sort(Comparator.comparing(OttService::getId));
		when(collectDataVersionService.findDataAsOf()).thenReturn(Optional.empty());
		saveProduct("A", services.get(0), 10_000);
		saveProduct("B", services.get(1), 20_000);
	}

	@AfterEach
	void 정리() {
		cleanUp();
		cacheManager.getCache(RedisConfig.PLAN_CALC).clear();
	}

	/** 이 테스트가 만든 사용자·플랜·상품·작품만 FK 순서대로 지운다 */
	private void cleanUp() {
		String userIds = "SELECT id FROM users WHERE email LIKE '" + EMAIL_PREFIX + "%'";
		jdbc.update("DELETE FROM notification_outbox WHERE user_id IN (" + userIds + ")");
		jdbc.update("DELETE FROM calc_run WHERE plan_id IN (SELECT id FROM plan WHERE user_id IN (" + userIds + "))");
		jdbc.update("DELETE FROM plan WHERE user_id IN (" + userIds + ")");
		jdbc.update("DELETE FROM calc_run WHERE plan_id IS NULL");
		jdbc.update("DELETE FROM wishlist_item WHERE user_id IN (" + userIds + ")");
		jdbc.update("DELETE FROM user_subscription WHERE user_id IN (" + userIds + ")");
		jdbc.update("DELETE FROM user_setting WHERE user_id IN (" + userIds + ")");
		jdbc.update("DELETE FROM users WHERE email LIKE '" + EMAIL_PREFIX + "%'");
		String productIds = "SELECT id FROM subscription_product WHERE name LIKE '" + NAME_PREFIX + "%'";
		jdbc.update("DELETE FROM product_price WHERE product_id IN (" + productIds + ")");
		jdbc.update("DELETE FROM product_component WHERE product_id IN (" + productIds + ")");
		jdbc.update("DELETE FROM subscription_product WHERE name LIKE '" + NAME_PREFIX + "%'");
		String unitIds = "SELECT id FROM watch_unit WHERE title_id IN (SELECT id FROM title WHERE tmdb_id BETWEEN "
				+ BASE_TMDB_ID + " AND " + MAX_TMDB_ID + ")";
		jdbc.update("DELETE FROM availability WHERE watch_unit_id IN (" + unitIds + ")");
		jdbc.update("DELETE FROM watch_unit WHERE id IN (" + unitIds + ")");
		jdbc.update("DELETE FROM title WHERE tmdb_id BETWEEN " + BASE_TMDB_ID + " AND " + MAX_TMDB_ID);
	}

	@Test
	void ACTIVE_플랜이_있는_DEMO는_구독_안내와_월초_안내_예시_2통을_순서대로_받는다() throws Exception {
		// given
		User demo = createDemoWithActivePlan(new ArrayList<>());

		// when, then
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, demoBearer(demo)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.mails.length()").value(2))
				.andExpect(jsonPath("$.data.mails[0].type").value("SUBSCRIBE_GUIDE"))
				.andExpect(jsonPath("$.data.mails[0].subject").value("[OTT내비] 저장한 구독 플랜 안내"))
				.andExpect(jsonPath("$.data.mails[1].type").value("MONTHLY"))
				.andExpect(jsonPath("$.data.mails[1].subject").value("[OTT내비] 이번 달 구독 플랜을 확인해 주세요"))
				.andExpect(jsonPath("$.data.noMailReasons").isEmpty());
	}

	@Test
	void 예시_메일_본문_HTML에_이번_달_구독할_서비스가_들어_있다() throws Exception {
		// given
		User demo = createDemoWithActivePlan(new ArrayList<>());

		// when, then
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, demoBearer(demo)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.mails[0].html").value(containsString("이번 달 구독할 서비스")))
				.andExpect(jsonPath("$.data.mails[1].html").value(containsString("이번 달 구독할 서비스")));
	}

	@Test
	void 예시를_조회해도_notification_outbox_행_수가_변하지_않는다() throws Exception {
		// given: 플랜 저장 시 생길 수 있는 행은 호출 전에 끝났으므로 기준 개수는 호출 직전에 센다
		User demo = createDemoWithActivePlan(new ArrayList<>());
		Long before = jdbc.queryForObject("SELECT count(*) FROM notification_outbox", Long.class);

		// when
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, demoBearer(demo))).andExpect(status().isOk());

		// then
		Long after = jdbc.queryForObject("SELECT count(*) FROM notification_outbox", Long.class);
		assertThat(after).isEqualTo(before);
	}

	@Test
	void 이번_달_가입할_상품이_없으면_월초_안내_1통만_받고_사유는_NO_SUBSCRIBE_GUIDE다() throws Exception {
		// given: 두 서비스를 모두 구독 중으로 두어 0번 달 상품이 모두 ALREADY_PAID가 된다
		List<SubscriptionState> subscribed = new ArrayList<>();
		subscribed.add(new SubscriptionState(services.get(0).getId(), ServiceSubscriptionStatus.SUBSCRIBED, "기본", 1));
		subscribed.add(new SubscriptionState(services.get(1).getId(), ServiceSubscriptionStatus.SUBSCRIBED, "기본", 1));
		User demo = createDemoWithActivePlan(subscribed);

		// when, then
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, demoBearer(demo)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.mails.length()").value(1))
				.andExpect(jsonPath("$.data.mails[0].type").value("MONTHLY"))
				.andExpect(jsonPath("$.data.noMailReasons.length()").value(1))
				.andExpect(jsonPath("$.data.noMailReasons[0]").value("NO_SUBSCRIBE_GUIDE"));
	}

	// 데이터 준비

	private String demoBearer(User demo) {
		return new TestTokens(jwtTokenProvider).bearer(demo.getId(), UserRole.DEMO);
	}

	/** DEMO 사용자를 만들고 추천형 플랜을 계산해 ACTIVE로 저장한다 */
	private User createDemoWithActivePlan(List<SubscriptionState> subscriptions) {
		String sub = UUID.randomUUID().toString().replace("-", "");
		User user = userRepository.save(User.createDemo(EMAIL_PREFIX + sub + "@example.com", "데모", sub));
		userSettingService.updateSettings(user.getId(), 20_000, 600, WatchPreset.NORMAL, false, subscriptions);
		WatchUnit must = saveMovie(BASE_TMDB_ID + 1, 120);
		WatchUnit want = saveMovie(BASE_TMDB_ID + 2, 200);
		WatchUnit maybe = saveMovie(BASE_TMDB_ID + 3, 90);
		saveStatuses(must, 0);
		saveStatuses(want, 1);
		saveStatuses(maybe, -1); // 7개 서비스 모두 없음
		wishlistItemRepository.save(WishlistItem.create(user.getId(), must.getId(), WishlistPriority.MUST));
		wishlistItemRepository.save(WishlistItem.create(user.getId(), want.getId(), WishlistPriority.WANT));
		wishlistItemRepository.save(WishlistItem.create(user.getId(), maybe.getId(), WishlistPriority.MAYBE));

		List<PlanDraftView> drafts = planCalculateService.calculate(user.getId());
		long planId = 0;
		for (PlanDraftView draft : drafts) {
			if (draft.planType() == PlanType.RECOMMENDED) {
				planId = draft.planId();
			}
		}
		planActivateService.activate(user.getId(), planId);
		return user;
	}

	private void saveProduct(String name, OttService service, int price) {
		SubscriptionProduct product = subscriptionProductRepository
				.save(SubscriptionProduct.create(NAME_PREFIX + name, ProductType.SINGLE, false));
		productComponentRepository.save(ProductComponent.create(product, service, ProviderTier.STANDARD));
		productPriceRepository.save(ProductPrice.create(product, price, LocalDate.now(KST).minusDays(30), Instant.now()));
	}

	private WatchUnit saveMovie(int tmdbId, int runtimeMin) {
		Title title = titleRepository.save(Title.create(MediaType.MOVIE, tmdbId, "작품 " + tmdbId, CHECKED_AT));
		return watchUnitRepository
				.save(WatchUnit.createMovie(title, new RuntimeResult(1, 1, runtimeMin, 0, false), CHECKED_AT));
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
