package com.ottnavi.plan.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ottnavi.catalog.domain.entity.Availability;
import com.ottnavi.catalog.domain.entity.AvailabilityChange;
import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.entity.WatchUnit;
import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.UnknownReason;
import com.ottnavi.catalog.domain.repository.AvailabilityChangeRepository;
import com.ottnavi.catalog.domain.repository.AvailabilityRepository;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.domain.repository.WatchUnitRepository;
import com.ottnavi.catalog.domain.rule.RuntimeResult;
import com.ottnavi.collect.service.CollectDataVersionService;
import com.ottnavi.engine.InputHasher;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.engine.model.SolverType;
import com.ottnavi.global.config.RedisConfig;
import com.ottnavi.global.security.jwt.JwtTokenProvider;
import com.ottnavi.plan.domain.entity.Plan;
import com.ottnavi.plan.domain.repository.PlanRepository;
import com.ottnavi.plan.service.PlanActivateService;
import com.ottnavi.plan.service.PlanCalculateService;
import com.ottnavi.plan.service.PlanInputService;
import com.ottnavi.plan.service.PlanRecalcService;
import com.ottnavi.plan.service.PlanRecalcTargetService;
import com.ottnavi.plan.service.PlanRecalcWriter;
import com.ottnavi.plan.service.PlanSolveService;
import com.ottnavi.plan.service.model.PlanCalcResults;
import com.ottnavi.plan.service.model.PlanDraftView;
import com.ottnavi.plan.service.model.PlanInputBundle;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 매월 재계산(Task 069)을 실제 DB·Redis·Batch로 확인한다(Docker Desktop 필요).
 * 시나리오: 상품 A(서비스 0, 10,000원)·B(서비스 1, 20,000원), 월 예산 20,000원, 영화 3편(꼭·보고싶음·여유될 때).
 * 재계산을 일으키는 변화는 "여유될 때" 영화가 서비스 0에서 볼 수 있게 된 것이다(제공 상태 변경 이력 + 제공 상태 갱신).
 * 막으려는 사고: 영향 없는 플랜이 재계산되는 일, ACTIVE가 둘이 되거나 이전 플랜 연결이 빠지는 일, DRAFT가 지워지는 일,
 * Tasklet(Resourceless 트랜잭션) 안에서 부른 쓰기 트랜잭션이 커밋되지 않는 일, 읽은 뒤 사용자가 직접 저장했는데 덮어쓰는 일,
 * ARCHIVED가 정리되지 않거나 남겨야 할 것이 지워지는 일.
 */
@AutoConfigureMockMvc
class PlanRecalcIntegrationTest extends IntegrationTestSupport {

	private static final int BASE_TMDB_ID = 950000; // 다른 통합 테스트와 겹치지 않는 작품 ID 대역
	private static final int MAX_TMDB_ID = 959999;
	private static final String NAME_PREFIX = "TASK069 ";
	private static final String EMAIL_PREFIX = "task069-";
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
	@Autowired private AvailabilityChangeRepository availabilityChangeRepository;
	@Autowired private WishlistItemRepository wishlistItemRepository;
	@Autowired private PlanRepository planRepository;
	@Autowired private PlanCalculateService planCalculateService;
	@Autowired private PlanActivateService planActivateService;
	@Autowired private PlanInputService planInputService;
	@Autowired private PlanSolveService planSolveService;
	@Autowired private PlanRecalcService planRecalcService;
	@Autowired private PlanRecalcWriter planRecalcWriter;
	@Autowired private PlanRecalcTargetService planRecalcTargetService;
	@Autowired private PlanRecalcLauncher planRecalcLauncher;
	@Autowired private PlanRecalcExecutionQuery planRecalcExecutionQuery;
	@Autowired private CacheManager cacheManager;
	@Autowired private JdbcTemplate jdbcTemplate;
	@MockitoBean private CollectDataVersionService collectDataVersionService; // 기본: 완료된 수집 없음(계산 시각이 기준 시각)

	private List<OttService> services; // 서비스 ID 오름차순
	private final Map<Integer, WatchUnit> unitsByTmdbId = new HashMap<>(); // 이 테스트가 만든 영화(TMDB ID → 시청 단위)

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
		String unitIds = "SELECT id FROM watch_unit WHERE title_id IN (SELECT id FROM title WHERE tmdb_id BETWEEN "
				+ BASE_TMDB_ID + " AND " + MAX_TMDB_ID + ")";
		jdbcTemplate.update("DELETE FROM availability_change WHERE watch_unit_id IN (" + unitIds + ")");
		jdbcTemplate.update("DELETE FROM availability WHERE watch_unit_id IN (" + unitIds + ")");
		jdbcTemplate.update("DELETE FROM watch_unit WHERE id IN (" + unitIds + ")");
		jdbcTemplate.update("DELETE FROM title WHERE tmdb_id BETWEEN " + BASE_TMDB_ID + " AND " + MAX_TMDB_ID);
	}

	@Test
	void 영향받은_플랜만_재계산되어_ACTIVE_1개와_이전_플랜_연결_변경_요약이_남고_DRAFT는_그대로이며_변경_내역이_조회된다() throws Exception {
		// given: 사용자 A·B가 각자 플랜을 저장했다. A는 초안 3개를 다시 만들어 두었다
		User userA = createScenarioUser(0);
		User userB = createScenarioUser(1);
		long oldA = activateRecommended(userA);
		long oldB = activateRecommended(userB);
		planCalculateService.calculate(userA.getId());
		Set<Long> draftIdsOfA = idsByStatus(userA, "DRAFT");
		assertThat(draftIdsOfA).hasSize(3);

		// given: A의 "여유될 때" 영화(BASE+3)가 서비스 0에서 볼 수 있게 됐다. B에게는 변화가 없다
		makeAvailable(BASE_TMDB_ID + 3, services.get(0));

		// when: 대상 선정
		List<Long> targets = planRecalcTargetService.findTargetPlanIds();

		// then: 영향받은 A의 플랜만 대상이다
		assertThat(targets).contains(oldA).doesNotContain(oldB);

		// when: 재계산 Job을 수동으로 시작해 끝날 때까지 기다린다
		long executionId = planRecalcLauncher.startManual();
		awaitCompleted(executionId);

		// then: Tasklet 안에서 부른 쓰기 트랜잭션이 커밋됐고(ACTIVE 1개), 이전 플랜은 보관, previous_plan_id가 연결된다
		assertThat(idsByStatus(userA, "ACTIVE")).hasSize(1);
		long newA = idsByStatus(userA, "ACTIVE").iterator().next();
		assertThat(newA).isNotEqualTo(oldA);
		assertThat(statusOf(oldA)).isEqualTo("ARCHIVED");
		assertThat(previousPlanIdOf(newA)).isEqualTo(oldA);
		assertThat(userIdOf(newA)).isEqualTo(userA.getId());
		assertThat(idsByStatus(userA, "DRAFT")).isEqualTo(draftIdsOfA); // DRAFT는 건드리지 않는다
		assertThat(changeSummaryOf(newA)).isNotNull();

		// then: 영향받지 않은 B의 플랜은 그대로다
		assertThat(idsByStatus(userB, "ACTIVE")).containsExactly(oldB);

		// then: 대상 수가 실행 기록(READ_COUNT)에 남고 수집 이력 조회가 읽는 값과 같다
		Integer readCount = jdbcTemplate.queryForObject(
				"SELECT READ_COUNT FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID = ?", Integer.class, executionId);
		assertThat(readCount).isEqualTo(targets.size());
		assertThat(planRecalcExecutionQuery.findTargetCountsByCollectExecutionIds(List.of(0L)))
				.containsEntry(0L, targets.size());

		// then: 변경 내역 API가 A에게는 새 플랜 기준 1건을 주고 B에게는 빈 배열을 준다
		mockMvc.perform(get("/api/me/plan-changes").header(HttpHeaders.AUTHORIZATION, bearerOf(userA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].changeId").value(newA))
				.andExpect(jsonPath("$.data.items[0].planType").value("RECOMMENDED"))
				.andExpect(jsonPath("$.data.items[0].reasons[0].reasonType").value("TITLE_JOINED_SERVICE"))
				.andExpect(jsonPath("$.data.items[0].titleChanges[0].changeType").value("ADDED"))
				.andExpect(jsonPath("$.data.items[0].monthChanges").isArray());
		mockMvc.perform(get("/api/me/plan-changes").header(HttpHeaders.AUTHORIZATION, bearerOf(userB)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	void 읽은_뒤_사용자가_직접_저장해_ACTIVE가_바뀌었으면_재계산은_건너뛴다() {
		// given: A가 플랜을 저장한 뒤 다시 계산해 절약형을 직접 저장했다(첫 플랜은 ARCHIVED)
		User user = createScenarioUser(0);
		long oldPlan = activateRecommended(user);
		List<PlanDraftView> drafts = planCalculateService.calculate(user.getId());
		long saved = planActivateService.activate(user.getId(), planIdOf(drafts, PlanType.SAVER)).planId();
		int planCount = countPlans(user);

		// when: 옛 플랜 ID로 재계산하면(읽기 단계에서 막힌다)
		PlanRecalcOutcome byService = planRecalcService.recalcOne(oldPlan);

		// when: 읽기 단계는 통과했다고 가정하고 쓰기 단계를 직접 부르면(읽은 뒤 바뀐 경우)
		PlanInputBundle bundle = planInputService.assemble(user.getId());
		String hash = InputHasher.hash(bundle.planInput(), LocalDate.now(KST));
		PlanCalcResults results = planSolveService.solve(hash, bundle.planInput());
		PlanRecalcOutcome byWriter = planRecalcWriter.write(user.getId(), oldPlan, hash, bundle, results.results().get(0));

		// then: 둘 다 건너뛰고 직접 저장한 플랜이 ACTIVE로 남으며 플랜 수도 그대로다
		assertThat(byService).isEqualTo(PlanRecalcOutcome.SKIPPED);
		assertThat(byWriter).isEqualTo(PlanRecalcOutcome.SKIPPED);
		assertThat(idsByStatus(user, "ACTIVE")).containsExactly(saved);
		assertThat(countPlans(user)).isEqualTo(planCount);
	}

	@Test
	void 재계산_뒤_변경_기록_최근_6개와_바로_이전_플랜만_남기고_나머지_ARCHIVED는_지운다() {
		// given: 변경 기록이 있는 ARCHIVED 8개(오래된 것부터)와 변경 기록이 없는 ARCHIVED 2개가 있고, 그 뒤에 현재 플랜을 저장했다
		User user = createScenarioUser(0);
		List<Long> withSummary = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			withSummary.add(seedArchived(user, true));
		}
		seedArchived(user, false);
		seedArchived(user, false);
		long oldPlan = activateRecommended(user);
		makeAvailable(BASE_TMDB_ID + 3, services.get(0));

		// when
		PlanRecalcOutcome outcome = planRecalcService.recalcOne(oldPlan);

		// then: 새 플랜에 변경 기록이 생겼고(최근 6개에 들어간다)
		assertThat(outcome).isEqualTo(PlanRecalcOutcome.UPDATED);
		long newPlan = idsByStatus(user, "ACTIVE").iterator().next();
		assertThat(changeSummaryOf(newPlan)).isNotNull();

		// then: 남는 ARCHIVED는 기록 있는 최근 5개(새 플랜이 한 자리를 쓴다) + 바로 이전 플랜(oldPlan)뿐이다
		Set<Long> expected = new HashSet<>(withSummary.subList(3, 8));
		expected.add(oldPlan);
		assertThat(idsByStatus(user, "ARCHIVED")).isEqualTo(expected);
	}

	// 데이터 준비

	private long activateRecommended(User user) {
		List<PlanDraftView> drafts = planCalculateService.calculate(user.getId());
		long planId = planIdOf(drafts, PlanType.RECOMMENDED);
		planActivateService.activate(user.getId(), planId);
		return planId;
	}

	private static long planIdOf(List<PlanDraftView> drafts, PlanType planType) {
		for (PlanDraftView draft : drafts) {
			if (draft.planType() == planType) {
				return draft.planId();
			}
		}
		throw new IllegalStateException("해당 유형의 초안이 없다: " + planType);
	}

	/** 변경 기록(요약 JSON) 유무를 정해 ARCHIVED 플랜을 하나 만든다. 사용자당 ACTIVE·DRAFT 유니크 때문에 하나씩 차례로 만든다 */
	private long seedArchived(User user, boolean withSummary) {
		Plan plan = Plan.createDraft(user.getId(), null, PlanType.RECOMMENDED, LocalDate.of(2026, 1, 1), 30_000, 600,
				SolverType.EXACT, 1, 1, 1, 1_000, 2_000, Instant.now());
		plan = planRepository.saveAndFlush(plan);
		plan.activate(Instant.now(), null);
		plan = planRepository.saveAndFlush(plan);
		if (withSummary) {
			plan.recordChangeSummary("{\"v\":1}");
		}
		plan.archive();
		return planRepository.saveAndFlush(plan).getId();
	}

	/** 영화의 서비스 제공 상태를 있음으로 바꾸고 변경 이력을 남긴다(이력 시각은 계산 시각보다 뒤여야 한다) */
	private void makeAvailable(int tmdbId, OttService service) {
		WatchUnit unit = unitsByTmdbId.get(tmdbId);
		jdbcTemplate.update("UPDATE availability SET status = 'AVAILABLE', in_standard = TRUE WHERE watch_unit_id = ? "
				+ "AND ott_service_id = ?", unit.getId(), service.getId());
		availabilityChangeRepository.save(AvailabilityChange.create(unit, service, AvailabilityStatus.NOT_AVAILABLE,
				AvailabilityStatus.AVAILABLE, null, Instant.now().plusSeconds(3_600)));
		cacheManager.getCache(RedisConfig.PLAN_CALC).clear();
	}

	private User createScenarioUser(int index) {
		String sub = UUID.randomUUID().toString().replace("-", "");
		User user = userRepository.save(User.create(EMAIL_PREFIX + sub + "@example.com", "테스터", OAuthProvider.GOOGLE, sub));
		userSettingService.updateSettings(user.getId(), 20_000, 600, WatchPreset.NORMAL, false, new ArrayList<>());
		int base = BASE_TMDB_ID + index * 10;
		WatchUnit must = saveMovie(base + 1, 120);
		WatchUnit want = saveMovie(base + 2, 200);
		WatchUnit maybe = saveMovie(base + 3, 90);
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
		Title title = titleRepository.save(Title.create(com.ottnavi.catalog.domain.enums.MediaType.MOVIE, tmdbId,
				"작품 " + tmdbId, CHECKED_AT));
		WatchUnit unit = watchUnitRepository.save(WatchUnit.createMovie(title, new RuntimeResult(1, 1, runtimeMin, 0, false), CHECKED_AT));
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

	// 조회 보조

	private String bearerOf(User user) {
		return new TestTokens(jwtTokenProvider).bearer(user.getId(), UserRole.USER);
	}

	private Set<Long> idsByStatus(User user, String status) {
		return new HashSet<>(jdbcTemplate.queryForList("SELECT id FROM plan WHERE user_id = ? AND status = ?", Long.class,
				user.getId(), status));
	}

	private int countPlans(User user) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plan WHERE user_id = ?", Integer.class, user.getId());
	}

	private String statusOf(long planId) {
		return jdbcTemplate.queryForObject("SELECT status FROM plan WHERE id = ?", String.class, planId);
	}

	private Long previousPlanIdOf(long planId) {
		return jdbcTemplate.queryForObject("SELECT previous_plan_id FROM plan WHERE id = ?", Long.class, planId);
	}

	private Long userIdOf(long planId) {
		return jdbcTemplate.queryForObject("SELECT user_id FROM plan WHERE id = ?", Long.class, planId);
	}

	private String changeSummaryOf(long planId) {
		return jdbcTemplate.queryForObject("SELECT change_summary FROM plan WHERE id = ?", String.class, planId);
	}

	/** 재계산 실행이 COMPLETED로 저장될 때까지 기다린다(최대 60초). 실패·중단이면 바로 실패시킨다 */
	private void awaitCompleted(long executionId) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 60_000;
		while (System.currentTimeMillis() < deadline) {
			String batchStatus = jdbcTemplate.queryForObject(
					"SELECT STATUS FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID = ?", String.class, executionId);
			if ("COMPLETED".equals(batchStatus)) {
				return;
			}
			assertThat(batchStatus).isIn("STARTING", "STARTED");
			Thread.sleep(100);
		}
		throw new IllegalStateException("재계산이 60초 안에 끝나지 않았다");
	}
}
