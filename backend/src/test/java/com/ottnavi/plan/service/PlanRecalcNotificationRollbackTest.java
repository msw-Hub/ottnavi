package com.ottnavi.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

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
import com.ottnavi.notification.service.NotificationCreateService;
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
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 알림 생성이 실패하면 그 플랜의 재계산도 롤백되는지(Task 073 사용자 결정)를 실제 DB로 확인한다.
 * 알림 행을 실제로 넣은 뒤 예외를 던지게 해서, 예외 전의 쓰기(이전 ACTIVE 보관, 새 ACTIVE, 알림 행)가 모두 되돌아가는지 본다.
 * 다른 통합 테스트에 스파이가 번지지 않도록 별도 클래스로 둔다.
 * 막으려는 사고: 알림만 실패했는데 새 ACTIVE가 남아 월초 안내 없이 플랜만 바뀌는 일, 알림 행만 남는 일.
 */
class PlanRecalcNotificationRollbackTest extends IntegrationTestSupport {

	private static final int BASE_TMDB_ID = 970000; // 다른 통합 테스트와 겹치지 않는 작품 ID 대역
	private static final int MAX_TMDB_ID = 970999;
	private static final String NAME_PREFIX = "TASK073RB ";
	private static final String EMAIL_PREFIX = "task073-rollback-";
	private static final Instant CHECKED_AT = Instant.parse("2026-10-14T15:30:00Z");
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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
	@Autowired private PlanRecalcService planRecalcService;
	@Autowired private CacheManager cacheManager;
	@Autowired private JdbcTemplate jdbc;
	@MockitoBean private CollectDataVersionService collectDataVersionService; // 기본: 완료된 수집 없음(계산 시각이 기준 시각)
	@MockitoSpyBean private NotificationCreateService notificationCreateService; // 재계산 경로(create)만 실패시킨다

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

	/** 이 테스트가 만든 사용자·알림·플랜·상품·작품만 FK 순서대로 지운다 */
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
	void 월초_안내_생성이_실패하면_재계산이_롤백되어_이전_ACTIVE가_그대로이고_알림_행도_남지_않는다() {
		// given: 지난달에 저장한 플랜(첫 달을 한 달 앞으로 당긴다). 알림을 켜 둔 사용자
		User user = createScenarioUser();
		long oldPlan = activateRecommended(user);
		jdbc.update("UPDATE plan SET start_month = (start_month - interval '1 month')::date WHERE id = ?", oldPlan);

		// given: 알림 행을 실제로 넣은 뒤 예외를 던진다(예외 전의 쓰기가 되돌아가야 한다)
		doAnswer(invocation -> {
			invocation.callRealMethod();
			throw new IllegalStateException("알림 생성 실패(테스트)");
		}).when(notificationCreateService).create(any(), any(), any(), any());

		// when: 월이 바뀐 재계산(MONTHLY 생성 경로)
		// then: 예외가 전파된다
		assertThatThrownBy(() -> planRecalcService.recalcOne(oldPlan))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("알림 생성 실패");

		// then: 이전 ACTIVE가 그대로이고 새 플랜·보관 플랜이 생기지 않았다
		assertThat(jdbc.queryForList("SELECT id FROM plan WHERE user_id = ? AND status = 'ACTIVE'", Long.class,
				user.getId())).containsExactly(oldPlan);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan WHERE user_id = ? AND status = 'ARCHIVED'",
				Integer.class, user.getId())).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan WHERE user_id = ? AND status = 'ACTIVE'",
				Integer.class, user.getId())).isEqualTo(1);

		// then: 방금 넣은 MONTHLY 알림 행도 되돌아가 남지 않는다
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE user_id = ? AND type = 'MONTHLY'",
				Integer.class, user.getId())).isZero();
	}

	private long activateRecommended(User user) {
		List<PlanDraftView> drafts = planCalculateService.calculate(user.getId());
		long planId = 0;
		for (PlanDraftView draft : drafts) {
			if (draft.planType() == PlanType.RECOMMENDED) {
				planId = draft.planId();
			}
		}
		planActivateService.activate(user.getId(), planId);
		return planId;
	}

	private User createScenarioUser() {
		String sub = UUID.randomUUID().toString().replace("-", "");
		User user = userRepository.save(User.create(EMAIL_PREFIX + sub + "@example.com", "테스터", OAuthProvider.GOOGLE, sub));
		userSettingService.updateSettings(user.getId(), 20_000, 600, WatchPreset.NORMAL, false, new ArrayList<>());
		jdbc.update("UPDATE user_setting SET notification_enabled = TRUE WHERE user_id = ?", user.getId());
		WatchUnit must = saveMovie(BASE_TMDB_ID + 1, 120);
		WatchUnit want = saveMovie(BASE_TMDB_ID + 2, 200);
		saveStatuses(must, 0);
		saveStatuses(want, 1);
		wishlistItemRepository.save(WishlistItem.create(user.getId(), must.getId(), WishlistPriority.MUST));
		wishlistItemRepository.save(WishlistItem.create(user.getId(), want.getId(), WishlistPriority.WANT));
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

	/** 서비스 7개 모두에 제공 상태를 저장한다. availableIndex 번째만 있음이다 */
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
