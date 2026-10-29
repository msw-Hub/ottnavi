package com.ottnavi.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.ottnavi.catalog.domain.entity.Availability;
import com.ottnavi.catalog.domain.entity.AvailabilityChange;
import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.entity.WatchUnit;
import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.enums.UnknownReason;
import com.ottnavi.catalog.domain.repository.AvailabilityChangeRepository;
import com.ottnavi.catalog.domain.repository.AvailabilityRepository;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.domain.repository.WatchUnitRepository;
import com.ottnavi.catalog.domain.rule.RuntimeResult;
import com.ottnavi.collect.service.CollectDataVersionService;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.global.config.RedisConfig;
import com.ottnavi.plan.service.model.PlanDraftView;
import com.ottnavi.plan.service.model.PlanRecalcOutcome;
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
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 플랜 저장·재계산에서 알림 행이 만들어지는 흐름(Task 073)을 실제 DB·Redis·가짜 SMTP(GreenMail)로 확인한다.
 * 구독 안내는 저장 커밋 뒤 비동기로 만들어지고 즉시 발송까지 켜 둔다(app.notification.immediate-send-enabled=true).
 * 시나리오: 상품 A(서비스 0, 10,000원)·B(서비스 1, 20,000원), 월 예산 20,000원, 영화 3편(꼭: 서비스 0, 보고싶음: 서비스 1, 여유될 때: 없음).
 * 막으려는 사고: 저장했는데 구독 안내가 없거나 두 번 만들어지는 일, 알림을 끈 사용자에게 행이 생기는 일,
 * 월 중간 재계산에서 월초 안내가 나가는 일, 같은 달을 두 번 재계산해 월초 안내가 두 통 나가는 일,
 * 다음 달 작품이 빠졌는데 변경 알림이 나가는 일(또는 이번 달 작품이 빠졌는데 안 나가는 일).
 */
@TestPropertySource(properties = "app.notification.immediate-send-enabled=true")
class PlanNotificationIntegrationTest extends IntegrationTestSupport {

	private static final int BASE_TMDB_ID = 960000; // 다른 통합 테스트와 겹치지 않는 작품 ID 대역
	private static final int MAX_TMDB_ID = 969999;
	private static final String NAME_PREFIX = "TASK073 ";
	private static final String EMAIL_PREFIX = "task073-plan-";
	private static final Instant CHECKED_AT = Instant.parse("2026-10-14T15:30:00Z");
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final long AWAIT_MILLIS = 15_000;

	@RegisterExtension
	GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

	@Autowired private UserRepository userRepository;
	@Autowired private UserSettingService userSettingService;
	@Autowired private OttServiceRepository ottServiceRepository;
	@Autowired private SubscriptionProductRepository subscriptionProductRepository;
	@Autowired private ProductComponentRepository productComponentRepository;
	@Autowired private ProductPriceRepository productPriceRepository;
	@Autowired private TitleRepository titleRepository;
	@Autowired private WatchUnitRepository watchUnitRepository;
	@Autowired private AvailabilityRepository availabilityRepository;
	@Autowired private AvailabilityChangeRepository availabilityChangeRepository;
	@Autowired private WishlistItemRepository wishlistItemRepository;
	@Autowired private PlanCalculateService planCalculateService;
	@Autowired private PlanActivateService planActivateService;
	@Autowired private PlanRecalcService planRecalcService;
	@Autowired private CacheManager cacheManager;
	@Autowired private JdbcTemplate jdbc;
	@MockitoBean private CollectDataVersionService collectDataVersionService; // 기본: 완료된 수집 없음(계산 시각이 기준 시각)

	private List<OttService> services; // 서비스 ID 오름차순
	private final Map<Integer, WatchUnit> unitsByTmdbId = new HashMap<>();

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
		jdbc.update("DELETE FROM availability_change WHERE watch_unit_id IN (" + unitIds + ")");
		jdbc.update("DELETE FROM availability WHERE watch_unit_id IN (" + unitIds + ")");
		jdbc.update("DELETE FROM watch_unit WHERE id IN (" + unitIds + ")");
		jdbc.update("DELETE FROM title WHERE tmdb_id BETWEEN " + BASE_TMDB_ID + " AND " + MAX_TMDB_ID);
	}

	// 구독 안내

	@Test
	void 저장하면_가입_안내_행이_1개_만들어지고_즉시_발송으로_메일_1통이_가며_같은_달에_다시_저장해도_늘지_않는다() throws Exception {
		// given: 알림을 켠 사용자
		User user = createScenarioUser(true);

		// when: 추천형을 저장한다
		long planId = activateRecommended(user);

		// then: 비동기로 SUBSCRIBE_GUIDE 행이 1개 만들어지고 메일 1통이 간다
		String dedupeKey = "SUBSCRIBE_GUIDE:" + user.getId() + ":" + YearMonth.now(KST);
		awaitRows("SELECT COUNT(*) FROM notification_outbox WHERE dedupe_key = ?", dedupeKey, 1);
		assertThat(greenMail.waitForIncomingEmail(AWAIT_MILLIS, 1)).isTrue();
		awaitRows("SELECT COUNT(*) FROM notification_outbox WHERE dedupe_key = ? AND status = 'SENT'", dedupeKey, 1);
		String body = (String) greenMail.getReceivedMessages()[0].getContent();
		assertThat(body).contains("이번 달 구독할 서비스").contains("작품");
		assertThat(planId).isPositive();

		// when: 같은 달에 다시 계산해서 저장한다
		activateRecommended(user);
		Thread.sleep(1_500);

		// then: 사용자·달마다 1회라 행도 메일도 그대로다
		assertThat(countRows("SELECT COUNT(*) FROM notification_outbox WHERE user_id = ? AND type = 'SUBSCRIBE_GUIDE'",
				user.getId())).isEqualTo(1);
		assertThat(greenMail.getReceivedMessages()).hasSize(1);
	}

	@Test
	void 알림을_끈_사용자가_저장하면_알림_행이_만들어지지_않는다() throws Exception {
		// given
		User user = createScenarioUser(false);

		// when
		activateRecommended(user);
		Thread.sleep(1_500); // 비동기 리스너가 돌 시간을 준다

		// then
		assertThat(countRows("SELECT COUNT(*) FROM notification_outbox WHERE user_id = ?", user.getId())).isZero();
		assertThat(greenMail.getReceivedMessages()).isEmpty();
	}

	// 월초 안내

	@Test
	void 월이_바뀐_재계산은_월초_안내_1행을_만들고_같은_달을_다시_재계산해도_늘지_않는다() throws Exception {
		// given: 지난달에 저장한 플랜(첫 달을 한 달 앞으로 당긴다)
		User user = createScenarioUser(true);
		long oldPlan = activateRecommended(user);
		awaitGuide(user);
		moveStartMonthBack(oldPlan);

		// when
		PlanRecalcOutcome first = planRecalcService.recalcOne(oldPlan);

		// then: MONTHLY가 이번 달 키로 1행 만들어진다
		assertThat(first).isEqualTo(PlanRecalcOutcome.UPDATED);
		String dedupeKey = "MONTHLY:" + user.getId() + ":" + YearMonth.now(KST);
		assertThat(countRows("SELECT COUNT(*) FROM notification_outbox WHERE dedupe_key = ?", dedupeKey)).isEqualTo(1);
		Map<String, Object> row = jdbc.queryForMap("SELECT status, payload ->> 'month' AS month, "
				+ "payload ->> 'userName' AS name FROM notification_outbox WHERE dedupe_key = ?", dedupeKey);
		assertThat(row.get("status")).isEqualTo("PENDING"); // 월초 안내는 즉시 발송이 아니라 스케줄러가 보낸다
		assertThat(row.get("month")).isEqualTo(YearMonth.now(KST).toString());
		assertThat(row.get("name")).isEqualTo("테스터");

		// when: 새 ACTIVE도 지난달 플랜인 것처럼 당겨 같은 달에 한 번 더 재계산한다
		long secondPlan = activePlanId(user);
		moveStartMonthBack(secondPlan);
		planRecalcService.recalcOne(secondPlan);

		// then: 사용자·달마다 1행이다
		assertThat(countRows("SELECT COUNT(*) FROM notification_outbox WHERE user_id = ? AND type = 'MONTHLY'",
				user.getId())).isEqualTo(1);
	}

	@Test
	void 월_중간_재계산은_월초_안내를_만들지_않는다() throws Exception {
		// given: 이번 달에 저장한 플랜
		User user = createScenarioUser(true);
		long oldPlan = activateRecommended(user);
		awaitGuide(user);

		// when: 첫 달이 그대로인 재계산
		PlanRecalcOutcome outcome = planRecalcService.recalcOne(oldPlan);

		// then
		assertThat(outcome).isEqualTo(PlanRecalcOutcome.UPDATED);
		assertThat(countRows("SELECT COUNT(*) FROM notification_outbox WHERE user_id = ? AND type <> 'SUBSCRIBE_GUIDE'",
				user.getId())).isZero();
	}

	@Test
	void 알림을_끈_사용자는_월이_바뀐_재계산에도_월초_안내가_없고_재계산은_정상_완료된다() throws Exception {
		// given
		User user = createScenarioUser(false);
		long oldPlan = activateRecommended(user);
		moveStartMonthBack(oldPlan);

		// when
		PlanRecalcOutcome outcome = planRecalcService.recalcOne(oldPlan);

		// then
		assertThat(outcome).isEqualTo(PlanRecalcOutcome.UPDATED);
		assertThat(countRows("SELECT COUNT(*) FROM notification_outbox WHERE user_id = ?", user.getId())).isZero();
	}

	// 변경 알림

	@Test
	void 이번_달_확정_달에_배정된_작품이_그_서비스에서_빠지면_변경_알림_1행이_만들어진다() throws Exception {
		// given: 꼭 볼 영화(BASE+1, 서비스 0)가 첫 달에 배정된 플랜
		User user = createScenarioUser(true);
		long oldPlan = activateRecommended(user);
		awaitGuide(user);
		assertThat(completedMonthIndexOf(oldPlan, BASE_TMDB_ID + 1)).isEqualTo(0);

		// given: 그 영화가 서비스 0에서 빠졌다
		leaveService(BASE_TMDB_ID + 1, services.get(0));

		// when
		planRecalcService.recalcOne(oldPlan);

		// then: 새 플랜 ID를 키로 PLAN_CHANGE 1행이 만들어지고 빠진 작품이 담긴다
		long newPlan = activePlanId(user);
		String dedupeKey = "PLAN_CHANGE:" + user.getId() + ":" + newPlan;
		assertThat(countRows("SELECT COUNT(*) FROM notification_outbox WHERE dedupe_key = ?", dedupeKey)).isEqualTo(1);
		Map<String, Object> row = jdbc.queryForMap("SELECT payload -> 'leftTitles' -> 0 ->> 'title' AS title, "
				+ "payload -> 'leftTitles' -> 0 ->> 'serviceName' AS service FROM notification_outbox "
				+ "WHERE dedupe_key = ?", dedupeKey);
		assertThat(row.get("title")).isEqualTo("작품 " + (BASE_TMDB_ID + 1));
		assertThat(row.get("service")).isEqualTo(services.get(0).getName());
	}

	@Test
	void 다음_달_작품만_서비스에서_빠지면_변경_알림이_만들어지지_않는다() throws Exception {
		// given: 보고 싶은 영화(BASE+2, 서비스 1)는 첫 달이 아닌 달에 끝나는 플랜
		User user = createScenarioUser(true);
		long oldPlan = activateRecommended(user);
		awaitGuide(user);
		assertThat(completedMonthIndexOf(oldPlan, BASE_TMDB_ID + 2)).isGreaterThan(0);

		// given: 그 영화가 서비스 1에서 빠졌다
		leaveService(BASE_TMDB_ID + 2, services.get(1));

		// when
		planRecalcService.recalcOne(oldPlan);

		// then
		assertThat(countRows("SELECT COUNT(*) FROM notification_outbox WHERE user_id = ? AND type = 'PLAN_CHANGE'",
				user.getId())).isZero();
	}

	// 데이터 준비

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

	/** 지난달에 저장한 플랜인 것처럼 첫 달을 한 달 앞으로 당긴다(월이 바뀐 재계산을 만들기 위해) */
	private void moveStartMonthBack(long planId) {
		jdbc.update("UPDATE plan SET start_month = (start_month - interval '1 month')::date WHERE id = ?", planId);
	}

	/** 영화가 서비스에서 빠진 것으로 제공 상태와 변경 이력을 바꾼다(이력 시각은 계산 시각보다 뒤여야 한다) */
	private void leaveService(int tmdbId, OttService service) {
		WatchUnit unit = unitsByTmdbId.get(tmdbId);
		jdbc.update("UPDATE availability SET status = 'NOT_AVAILABLE', in_standard = FALSE WHERE watch_unit_id = ? "
				+ "AND ott_service_id = ?", unit.getId(), service.getId());
		availabilityChangeRepository.save(AvailabilityChange.create(unit, service, AvailabilityStatus.AVAILABLE,
				AvailabilityStatus.NOT_AVAILABLE, null, Instant.now().plusSeconds(3_600)));
		cacheManager.getCache(RedisConfig.PLAN_CALC).clear();
	}

	private User createScenarioUser(boolean notificationEnabled) {
		String sub = UUID.randomUUID().toString().replace("-", "");
		User user = userRepository.save(User.create(EMAIL_PREFIX + sub + "@example.com", "테스터", OAuthProvider.GOOGLE, sub));
		userSettingService.updateSettings(user.getId(), 20_000, 600, WatchPreset.NORMAL, false, new ArrayList<>());
		jdbc.update("UPDATE user_setting SET notification_enabled = ? WHERE user_id = ?", notificationEnabled,
				user.getId());
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
				.save(SubscriptionProduct.create(NAME_PREFIX + name, ProductType.SINGLE, false));
		productComponentRepository.save(ProductComponent.create(product, service, ProviderTier.STANDARD));
		productPriceRepository.save(ProductPrice.create(product, price, LocalDate.now(KST).minusDays(30), Instant.now()));
	}

	private WatchUnit saveMovie(int tmdbId, int runtimeMin) {
		Title title = titleRepository.save(Title.create(MediaType.MOVIE, tmdbId, "작품 " + tmdbId, CHECKED_AT));
		WatchUnit unit = watchUnitRepository
				.save(WatchUnit.createMovie(title, new RuntimeResult(1, 1, runtimeMin, 0, false), CHECKED_AT));
		unitsByTmdbId.put(tmdbId, unit);
		return unit;
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

	// 조회·대기 보조

	private int countRows(String sql, Object arg) {
		return jdbc.queryForObject(sql, Integer.class, arg);
	}

	private long activePlanId(User user) {
		return jdbc.queryForObject("SELECT id FROM plan WHERE user_id = ? AND status = 'ACTIVE'", Long.class, user.getId());
	}

	private int completedMonthIndexOf(long planId, int tmdbId) {
		return jdbc.queryForObject("SELECT completed_month_index FROM plan_item WHERE plan_id = ? AND watch_unit_id = ?",
				Integer.class, planId, unitsByTmdbId.get(tmdbId).getId());
	}

	/** 비동기로 만들어지는 구독 안내 행이 생기고 즉시 발송이 끝날 때까지 기다린다(뒤 단계가 같은 사용자의 플랜을 바꾸기 전에 비동기 처리를 끝내려는 것) */
	private void awaitGuide(User user) throws InterruptedException {
		awaitRows("SELECT COUNT(*) FROM notification_outbox WHERE user_id = ? AND type = 'SUBSCRIBE_GUIDE' "
				+ "AND status = 'SENT'", user.getId(), 1);
	}

	/** 쿼리 결과 건수가 기대한 값이 될 때까지 기다린다. 시간이 지나면 마지막 값을 담아 실패시킨다 */
	private void awaitRows(String sql, Object arg, int expected) throws InterruptedException {
		long deadline = System.currentTimeMillis() + AWAIT_MILLIS;
		int count = -1;
		while (System.currentTimeMillis() < deadline) {
			count = countRows(sql, arg);
			if (count == expected) {
				return;
			}
			Thread.sleep(100);
		}
		throw new IllegalStateException("기다린 건수에 도달하지 못했다: 기대=" + expected + ", 마지막=" + count + ", sql=" + sql);
	}
}
