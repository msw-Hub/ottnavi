package com.ottnavi.retention.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.ottnavi.catalog.service.TitlePurgeService;
import com.ottnavi.catalog.service.model.StaleTitle;
import com.ottnavi.collect.service.CollectDataVersionService;
import com.ottnavi.engine.model.PlanType;
import com.ottnavi.global.config.RedisConfig;
import com.ottnavi.plan.service.PlanActivateService;
import com.ottnavi.plan.service.PlanCalculateService;
import com.ottnavi.plan.service.PlanRecalcService;
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
import com.ottnavi.retention.service.model.TitleExistence;
import com.ottnavi.retention.service.model.TitleRetentionSummary;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.enums.WatchPreset;
import com.ottnavi.user.domain.repository.UserRepository;
import com.ottnavi.user.service.UserSettingService;
import com.ottnavi.wishlist.domain.entity.WishlistItem;
import com.ottnavi.wishlist.domain.enums.WishlistPriority;
import com.ottnavi.wishlist.domain.repository.WishlistItemRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 6개월 갱신 점검과 사라진 작품 삭제(Task 083)를 실제 DB·Redis로 확인한다(Docker Desktop 필요). TMDB 확인(Probe)과 갱신(Refresher)만 목으로 대신한다.
 * 시나리오: 사용자마다 영화 3편(꼭·보고싶음·여유될 때)을 찜하고 플랜을 저장한다.
 * 막으려는 사고: 기준을 넘기지 않은 작품이 점검되는 일, 일시 장애를 "사라짐"으로 오판해 지우는 일,
 * 찜·DRAFT·ACTIVE·ARCHIVED 중 하나라도 남아 FK 위반이 나는 일, 다른 사용자의 데이터가 지워지는 일,
 * 재계산 실패·경합 때 반쯤 지운 채 멈춰 다음 실행에서 이어지지 않는 일.
 */
class TitleRetentionIntegrationTest extends IntegrationTestSupport {

	private static final int BASE_TMDB_ID = 970000; // 다른 통합 테스트와 겹치지 않는 작품 ID 대역
	private static final int MAX_TMDB_ID = 979999;
	private static final String NAME_PREFIX = "TASK083 ";
	private static final String EMAIL_PREFIX = "task083-";
	private static final Instant CHECKED_AT = Instant.parse("2026-10-14T15:30:00Z");
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final Instant LONG_AGO = Instant.now().minus(Duration.ofDays(400)); // 어느 기준으로 봐도 오래된 작품

	@Autowired private RetentionService retentionService;
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
	@Autowired private CacheManager cacheManager;
	@Autowired private JdbcTemplate jdbcTemplate;
	@MockitoBean private CollectDataVersionService collectDataVersionService; // 기본: 완료된 수집 없음(계산 시각이 기준 시각)
	@MockitoBean private TitleExistenceProbe titleExistenceProbe;
	@MockitoBean private TitleRefresher titleRefresher;
	@MockitoSpyBean private PlanRecalcService planRecalcService;
	@MockitoSpyBean private TitlePurgeService titlePurgeService;

	private List<OttService> services; // 서비스 ID 오름차순
	private final Map<Integer, WatchUnit> unitsByTmdbId = new HashMap<>(); // 이 테스트가 만든 영화(TMDB ID → 시청 단위)

	@BeforeEach
	void 초기화() {
		cleanUp();
		cacheManager.getCache(RedisConfig.PLAN_CALC).clear();
		services = new ArrayList<>(ottServiceRepository.findAllByOrderByDisplayOrderAsc());
		services.sort(Comparator.comparing(OttService::getId));
		when(collectDataVersionService.findDataAsOf()).thenReturn(Optional.empty());
		when(titleExistenceProbe.probe(any())).thenReturn(TitleExistence.UNKNOWN); // 목 기본값이 null이면 삭제로 흘러가므로 안전한 값으로 둔다
		saveProduct("A", services.get(0), 10_000);
		saveProduct("B", services.get(1), 20_000);
	}

	@AfterEach
	void 정리() {
		cleanUp();
		cacheManager.getCache(RedisConfig.PLAN_CALC).clear();
	}

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
	void 마지막_수집이_5개월을_넘긴_작품만_TMDB에_다시_묻고_갱신한다() {
		// given: 5개월보다 하루 오래된 작품 A와 하루 덜 오래된 작품 B
		Instant now = Instant.now();
		Instant olderThanFiveMonths = ZonedDateTime.ofInstant(now, KST).minusMonths(5).minusDays(1).toInstant();
		Instant newerThanFiveMonths = ZonedDateTime.ofInstant(now, KST).minusMonths(5).plusDays(1).toInstant();
		WatchUnit stale = saveMovie(BASE_TMDB_ID + 1, 100, olderThanFiveMonths);
		WatchUnit fresh = saveMovie(BASE_TMDB_ID + 2, 100, newerThanFiveMonths);
		StaleTitle staleTarget = staleOf(stale, BASE_TMDB_ID + 1);
		StaleTitle freshTarget = staleOf(fresh, BASE_TMDB_ID + 2);
		when(titleExistenceProbe.probe(staleTarget)).thenReturn(TitleExistence.EXISTS);
		when(titleRefresher.refresh(staleTarget)).thenReturn(true);

		// when
		TitleRetentionSummary summary = retentionService.retainTitles();

		// then: 기준을 넘긴 A만 갱신됐고 B는 TMDB에 묻지도 않았다
		assertThat(summary.refreshed()).isGreaterThanOrEqualTo(1);
		verify(titleRefresher).refresh(staleTarget);
		verify(titleExistenceProbe, never()).probe(freshTarget);
		verify(titleRefresher, never()).refresh(freshTarget);
		assertThat(titleCount(BASE_TMDB_ID + 1)).isEqualTo(1);
		assertThat(titleCount(BASE_TMDB_ID + 2)).isEqualTo(1);
	}

	@Test
	void TMDB에서_사라진_작품은_찜_초안_현재_플랜_보관_플랜을_정리하고_삭제되며_다른_사용자는_그대로다() {
		// given: 사용자 0은 플랜을 두 번 저장(첫 플랜은 보관)하고 초안 3개를 다시 만들었다. 사용자 1은 따로 저장했다
		User user0 = createScenarioUser(0, true);
		User user1 = createScenarioUser(1, false);
		activateRecommended(user0);
		activateRecommended(user0);
		planCalculateService.calculate(user0.getId());
		long user1Active = activateRecommended(user1);
		assertThat(countPlans(user0, "ARCHIVED")).isEqualTo(1);
		assertThat(countPlans(user0, "DRAFT")).isEqualTo(3);

		// given: 사용자 0의 "꼭 볼" 영화가 TMDB에서 사라졌다
		WatchUnit gone = unitsByTmdbId.get(BASE_TMDB_ID + 1);
		long goneUnitId = gone.getId();
		when(titleExistenceProbe.probe(staleOf(gone, BASE_TMDB_ID + 1))).thenReturn(TitleExistence.GONE);

		// when
		TitleRetentionSummary summary = retentionService.retainTitles();

		// then: 작품·시청 단위·제공 상태가 지워졌고 FK 위반 없이 끝났다
		assertThat(summary.purged()).isEqualTo(1);
		assertThat(titleCount(BASE_TMDB_ID + 1)).isEqualTo(0);
		assertThat(count("SELECT COUNT(*) FROM watch_unit WHERE id = ?", goneUnitId)).isEqualTo(0);
		assertThat(count("SELECT COUNT(*) FROM availability WHERE watch_unit_id = ?", goneUnitId)).isEqualTo(0);
		assertThat(count("SELECT COUNT(*) FROM availability_change WHERE watch_unit_id = ?", goneUnitId)).isEqualTo(0);
		assertThat(count("SELECT COUNT(*) FROM wishlist_item WHERE watch_unit_id = ?", goneUnitId)).isEqualTo(0);

		// then: 사용자 0은 현재 플랜이 다시 계산돼 하나 남고, 거기에 사라진 작품이 없다. 보관 플랜과 초안은 통째로 지워졌다
		assertThat(countPlans(user0, "ACTIVE")).isEqualTo(1);
		assertThat(countPlans(user0, "ARCHIVED")).isEqualTo(0);
		assertThat(countPlans(user0, "DRAFT")).isEqualTo(0);
		assertThat(count("SELECT COUNT(*) FROM plan_item WHERE watch_unit_id = ?", goneUnitId)).isEqualTo(0);
		Set<Long> remainingItemUnits = new HashSet<>(jdbcTemplate.queryForList(
				"SELECT i.watch_unit_id FROM plan_item i JOIN plan p ON p.id = i.plan_id WHERE p.user_id = ? AND p.status = 'ACTIVE'",
				Long.class, user0.getId()));
		assertThat(remainingItemUnits).containsExactlyInAnyOrder(unitsByTmdbId.get(BASE_TMDB_ID + 2).getId(),
				unitsByTmdbId.get(BASE_TMDB_ID + 3).getId());

		// then: 사용자 0의 나머지 찜 2개와 사용자 1의 데이터는 그대로다
		assertThat(count("SELECT COUNT(*) FROM wishlist_item WHERE user_id = ?", user0.getId())).isEqualTo(2);
		assertThat(count("SELECT COUNT(*) FROM wishlist_item WHERE user_id = ?", user1.getId())).isEqualTo(3);
		assertThat(jdbcTemplate.queryForList("SELECT id FROM plan WHERE user_id = ? AND status = 'ACTIVE'", Long.class,
				user1.getId())).containsExactly(user1Active);
		assertThat(titleCount(BASE_TMDB_ID + 11)).isEqualTo(1);
	}

	@Test
	void 찜이_그_작품_하나뿐이라_재계산할_수_없는_현재_플랜도_삭제되고_FK_위반이_없다() {
		// given: 사용자가 세 작품으로 플랜을 저장한 뒤 다른 찜 둘을 지워 찜이 사라질 작품 하나뿐이다
		User user = createScenarioUser(0, true);
		activateRecommended(user);
		WatchUnit gone = unitsByTmdbId.get(BASE_TMDB_ID + 1);
		jdbcTemplate.update("DELETE FROM wishlist_item WHERE user_id = ? AND watch_unit_id <> ?", user.getId(), gone.getId());
		when(titleExistenceProbe.probe(staleOf(gone, BASE_TMDB_ID + 1))).thenReturn(TitleExistence.GONE);

		// when
		TitleRetentionSummary summary = retentionService.retainTitles();

		// then: 작품은 삭제됐고 계산할 수 없는 현재 플랜도 함께 삭제돼 플랜이 남지 않는다
		assertThat(summary.purged()).isEqualTo(1);
		assertThat(titleCount(BASE_TMDB_ID + 1)).isEqualTo(0);
		assertThat(countPlans(user, "ACTIVE")).isEqualTo(0);
		assertThat(count("SELECT COUNT(*) FROM plan WHERE user_id = ?", user.getId())).isEqualTo(0);
	}

	@Test
	void TMDB가_일시_오류면_삭제하지_않고_다음_실행으로_미룬다() {
		// given: 사라진 것처럼 보이는 작품이지만 TMDB 확인이 UNKNOWN이다(기본 목 값)
		User user = createScenarioUser(0, true);
		long active = activateRecommended(user);
		WatchUnit target = unitsByTmdbId.get(BASE_TMDB_ID + 1);

		// when
		TitleRetentionSummary summary = retentionService.retainTitles();

		// then: 작품·찜·현재 플랜 모두 그대로다
		assertThat(summary.deferred()).isGreaterThanOrEqualTo(1);
		assertThat(titleCount(BASE_TMDB_ID + 1)).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM wishlist_item WHERE watch_unit_id = ?", target.getId())).isEqualTo(1);
		assertThat(jdbcTemplate.queryForList("SELECT id FROM plan WHERE user_id = ? AND status = 'ACTIVE'", Long.class,
				user.getId())).containsExactly(active);
	}

	@Test
	void 재계산이_실패하면_작품_삭제를_미루고_다음_실행에서_이어서_삭제한다() {
		// given: 사라진 작품이고 첫 실행에서 재계산이 일시 오류로 실패한다
		User user = createScenarioUser(0, true);
		activateRecommended(user);
		WatchUnit gone = unitsByTmdbId.get(BASE_TMDB_ID + 1);
		when(titleExistenceProbe.probe(staleOf(gone, BASE_TMDB_ID + 1))).thenReturn(TitleExistence.GONE);
		doThrow(new IllegalStateException("일시 오류")).when(planRecalcService).recalcOne(anyLong());

		// when: 첫 실행
		TitleRetentionSummary first = retentionService.retainTitles();

		// then: 삭제하지 않고 보류했다(작품은 남고 현재 플랜도 그대로다)
		assertThat(first.purged()).isEqualTo(0);
		assertThat(titleCount(BASE_TMDB_ID + 1)).isEqualTo(1);
		assertThat(countPlans(user, "ACTIVE")).isEqualTo(1);

		// when: 재계산이 회복된 다음 실행
		reset(planRecalcService);
		TitleRetentionSummary second = retentionService.retainTitles();

		// then: 이어서 정리가 끝났다
		assertThat(second.purged()).isEqualTo(1);
		assertThat(titleCount(BASE_TMDB_ID + 1)).isEqualTo(0);
		assertThat(countPlans(user, "ACTIVE")).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM plan_item WHERE watch_unit_id = ?", gone.getId())).isEqualTo(0);
	}

	@Test
	void 삭제하는_중_FK_위반이_나면_그_작품만_건너뛴다() {
		// given: 사라진 작품인데 삭제 단계에서 다른 요청이 참조해 FK 위반이 난다
		createScenarioUser(0, true);
		WatchUnit gone = unitsByTmdbId.get(BASE_TMDB_ID + 1);
		when(titleExistenceProbe.probe(staleOf(gone, BASE_TMDB_ID + 1))).thenReturn(TitleExistence.GONE);
		doThrow(new DataIntegrityViolationException("FK 위반")).when(titlePurgeService).purge(anyLong());

		// when
		TitleRetentionSummary summary = retentionService.retainTitles();

		// then: 예외가 퍼지지 않고 보류로 끝나며 작품은 남는다
		assertThat(summary.purged()).isEqualTo(0);
		assertThat(summary.deferred()).isGreaterThanOrEqualTo(1);
		assertThat(titleCount(BASE_TMDB_ID + 1)).isEqualTo(1);
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

	/**
	 * 사용자와 영화 3편(꼭·보고싶음·여유될 때)을 만든다. TMDB ID는 BASE + index*10 + 1~3이다.
	 * staleMovies가 true면 세 작품을 오래된 작품으로(마지막 수집 400일 전), false면 최근 작품으로 만든다.
	 */
	private User createScenarioUser(int index, boolean staleMovies) {
		String sub = UUID.randomUUID().toString().replace("-", "");
		User user = userRepository.save(User.create(EMAIL_PREFIX + sub + "@example.com", "테스터", OAuthProvider.GOOGLE, sub));
		userSettingService.updateSettings(user.getId(), 20_000, 600, WatchPreset.NORMAL, false, new ArrayList<>());
		Instant fetchedAt = Instant.now();
		if (staleMovies) {
			fetchedAt = LONG_AGO;
		}
		int base = BASE_TMDB_ID + index * 10;
		WatchUnit must = saveMovie(base + 1, 120, fetchedAt);
		WatchUnit want = saveMovie(base + 2, 200, fetchedAt);
		WatchUnit maybe = saveMovie(base + 3, 90, fetchedAt);
		saveStatuses(must, 0);
		saveStatuses(want, 1);
		saveStatuses(maybe, -1); // 7개 서비스 모두 없음
		availabilityChangeRepository.save(AvailabilityChange.create(must, services.get(0), AvailabilityStatus.NOT_AVAILABLE,
				AvailabilityStatus.AVAILABLE, null, CHECKED_AT.minusSeconds(3_600)));
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

	private WatchUnit saveMovie(int tmdbId, int runtimeMin, Instant fetchedAt) {
		Title title = titleRepository.save(Title.create(MediaType.MOVIE, tmdbId, "작품 " + tmdbId, fetchedAt));
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

	private StaleTitle staleOf(WatchUnit unit, int tmdbId) {
		return new StaleTitle(unit.getTitle().getId(), MediaType.MOVIE, tmdbId);
	}

	// 조회 보조

	private int titleCount(int tmdbId) {
		return count("SELECT COUNT(*) FROM title WHERE media_type = 'MOVIE' AND tmdb_id = ?", tmdbId);
	}

	private int countPlans(User user, String status) {
		return count("SELECT COUNT(*) FROM plan WHERE user_id = ? AND status = ?", user.getId(), status);
	}

	private int count(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}
}
