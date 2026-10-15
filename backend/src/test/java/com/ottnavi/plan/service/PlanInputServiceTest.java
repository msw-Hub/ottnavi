package com.ottnavi.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ottnavi.catalog.domain.entity.Availability;
import com.ottnavi.catalog.domain.entity.Title;
import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.domain.enums.UnknownReason;
import com.ottnavi.catalog.domain.repository.AvailabilityRepository;
import com.ottnavi.catalog.domain.repository.TitleRepository;
import com.ottnavi.catalog.domain.repository.WatchUnitRepository;
import com.ottnavi.catalog.domain.rule.RuntimeResult;
import com.ottnavi.collect.service.CollectDataVersionService;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.global.error.FieldValidationException;
import com.ottnavi.plan.service.model.PlanInputBundle;
import com.ottnavi.product.domain.entity.ProductComponent;
import com.ottnavi.product.domain.entity.ProductPrice;
import com.ottnavi.product.domain.entity.SubscriptionProduct;
import com.ottnavi.product.domain.entity.UserPriceOverride;
import com.ottnavi.product.domain.enums.PriceSource;
import com.ottnavi.product.domain.enums.ProductType;
import com.ottnavi.product.domain.repository.ProductComponentRepository;
import com.ottnavi.product.domain.repository.ProductPriceRepository;
import com.ottnavi.product.domain.repository.SubscriptionProductRepository;
import com.ottnavi.product.domain.repository.UserPriceOverrideRepository;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.domain.enums.ProviderTier;
import com.ottnavi.provider.domain.repository.OttServiceRepository;
import com.ottnavi.support.IntegrationTestSupport;
import com.ottnavi.user.domain.entity.User;
import com.ottnavi.user.domain.enums.OAuthProvider;
import com.ottnavi.user.domain.enums.ServiceSubscriptionStatus;
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
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 계산 입력 수집(Task 063)을 실제 DB로 확인한다(Docker Desktop 필요). 시험이 만든 상품·작품·사용자만 지운다.
 * 막으려는 사고: 제외한 상품이 시청 가능 상품 ID에 남아 엔진 입력 생성이 실패하는 일, 가격 출처가 구독 상태·수정 금액과 어긋나는 일,
 * 다 봤음 찜이 입력에 들어가는 일, 시즌 번호·시리즈 식별자가 빠져 시즌 순서 규칙이 꺼지는 일.
 */
class PlanInputServiceTest extends IntegrationTestSupport {

	private static final int BASE_TMDB_ID = 920000; // 다른 통합 테스트와 겹치지 않는 작품 ID 대역
	private static final String NAME_PREFIX = "TASK063 ";
	private static final String EMAIL_PREFIX = "task063-";
	private static final Instant CHECKED_AT = Instant.parse("2026-10-14T15:30:00Z");
	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

	@Autowired private PlanInputService planInputService;
	@Autowired private CollectDataVersionService collectDataVersionService;
	@Autowired private UserRepository userRepository;
	@Autowired private UserSettingService userSettingService;
	@Autowired private OttServiceRepository ottServiceRepository;
	@Autowired private SubscriptionProductRepository subscriptionProductRepository;
	@Autowired private ProductComponentRepository productComponentRepository;
	@Autowired private ProductPriceRepository productPriceRepository;
	@Autowired private UserPriceOverrideRepository userPriceOverrideRepository;
	@Autowired private TitleRepository titleRepository;
	@Autowired private WatchUnitRepository watchUnitRepository;
	@Autowired private AvailabilityRepository availabilityRepository;
	@Autowired private WishlistItemRepository wishlistItemRepository;
	@Autowired private JdbcTemplate jdbcTemplate;

	private List<OttService> services; // 서비스 ID 오름차순

	@BeforeEach
	void 초기화() {
		cleanUp();
		services = new ArrayList<>(ottServiceRepository.findAllByOrderByDisplayOrderAsc());
		services.sort(Comparator.comparing(OttService::getId));
	}

	@AfterEach
	void 정리() {
		cleanUp();
	}

	private void cleanUp() {
		String userIds = "SELECT id FROM users WHERE email LIKE '" + EMAIL_PREFIX + "%'";
		jdbcTemplate.update("DELETE FROM wishlist_item WHERE user_id IN (" + userIds + ")");
		jdbcTemplate.update("DELETE FROM user_price_override WHERE user_id IN (" + userIds + ")");
		jdbcTemplate.update("DELETE FROM user_subscription WHERE user_id IN (" + userIds + ")");
		jdbcTemplate.update("DELETE FROM user_setting WHERE user_id IN (" + userIds + ")");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE '" + EMAIL_PREFIX + "%'");
		String productIds = "SELECT id FROM subscription_product WHERE name LIKE 'TASK063%'";
		jdbcTemplate.update("DELETE FROM product_price WHERE product_id IN (" + productIds + ")");
		jdbcTemplate.update("DELETE FROM product_component WHERE product_id IN (" + productIds + ")");
		jdbcTemplate.update("DELETE FROM subscription_product WHERE name LIKE 'TASK063%'");
		String titleIds = "SELECT id FROM title WHERE tmdb_id >= " + BASE_TMDB_ID;
		jdbcTemplate.update("DELETE FROM availability WHERE watch_unit_id IN "
				+ "(SELECT id FROM watch_unit WHERE title_id IN (" + titleIds + "))");
		jdbcTemplate.update("DELETE FROM watch_unit WHERE title_id IN (" + titleIds + ")");
		jdbcTemplate.update("DELETE FROM title WHERE tmdb_id >= " + BASE_TMDB_ID);
	}

	@Test
	void 조립_결과가_기대_입력과_같고_가격_출처_4가지와_제외_규칙이_적용된다() {
		// given: 상품 6개(서비스 0~5). 0 기본, 1 수정 금액, 2 구독 중, 3 무료 이용(수정 금액 있음), 4 요금 없음, 5 상한 초과
		User user = createUser();
		long p0 = saveProduct("p0", services.get(0), 10_000, TODAY.minusDays(30));
		long p1 = saveProduct("p1", services.get(1), 8_000, TODAY.minusDays(30));
		long p2 = saveProduct("p2", services.get(2), 12_000, TODAY.minusDays(30));
		long p3 = saveProduct("p3", services.get(3), 5_000, TODAY.minusDays(30));
		long p4 = saveProduct("p4", services.get(4), 9_000, TODAY.plusDays(1));
		long p5 = saveProduct("p5", services.get(5), OttProduct.MAX_MONTHLY_PRICE + 1, TODAY.minusDays(30));
		userPriceOverrideRepository.save(UserPriceOverride.create(user.getId(), p1, 7_000));
		userPriceOverrideRepository.save(UserPriceOverride.create(user.getId(), p3, 4_500));
		List<SubscriptionState> subscriptions = new ArrayList<>();
		subscriptions.add(new SubscriptionState(services.get(2).getId(), ServiceSubscriptionStatus.SUBSCRIBED, "스탠다드", 1));
		subscriptions.add(new SubscriptionState(services.get(3).getId(), ServiceSubscriptionStatus.FREE, null, null));
		userSettingService.updateSettings(user.getId(), 30_000, 600, WatchPreset.NORMAL, subscriptions);

		// given: 영화 1편(서비스 0·4·5에서 제공), 드라마 시즌 1·2, 이미 다 본 영화 1편
		var movie = saveUnit(MediaType.MOVIE, BASE_TMDB_ID + 1, 100);
		var tv = titleRepository.save(Title.create(MediaType.TV, BASE_TMDB_ID + 2, "드라마", CHECKED_AT));
		var season1 = watchUnitRepository.save(com.ottnavi.catalog.domain.entity.WatchUnit.createSeason(tv, 1, 7001, "시즌 1",
				"/s.jpg", LocalDate.of(2021, 9, 17), new RuntimeResult(8, 8, 600, 0, false), CHECKED_AT));
		var season2 = watchUnitRepository.save(com.ottnavi.catalog.domain.entity.WatchUnit.createSeason(tv, 2, 7002, "시즌 2",
				"/s.jpg", LocalDate.of(2021, 9, 17), new RuntimeResult(8, 8, 500, 0, false), CHECKED_AT));
		var watchedMovie = saveUnit(MediaType.MOVIE, BASE_TMDB_ID + 3, 90);
		// 영화: 서비스 0·4·5 제공, 나머지 없음(모름 없음)
		saveAllStatuses(movie, Set.of(0, 4, 5), Set.of());
		// 시즌 1: 서비스 1·2·3 제공, 나머지 없음
		saveAllStatuses(season1, Set.of(1, 2, 3), Set.of());
		// 시즌 2: 서비스 1 제공, 서비스 6은 모름, 나머지 없음
		saveAllStatuses(season2, Set.of(1), Set.of(6));
		saveAllStatuses(watchedMovie, Set.of(0), Set.of());
		var movieItem = wishlistItemRepository.save(WishlistItem.create(user.getId(), movie.getId(), WishlistPriority.MUST));
		var s1Item = wishlistItemRepository.save(WishlistItem.create(user.getId(), season1.getId(), WishlistPriority.WANT));
		var s2Item = wishlistItemRepository.save(WishlistItem.create(user.getId(), season2.getId(), WishlistPriority.MAYBE));
		WishlistItem watched = WishlistItem.create(user.getId(), watchedMovie.getId(), WishlistPriority.WANT);
		watched.markWatched(Instant.now());
		wishlistItemRepository.save(watched);

		// when
		PlanInputBundle bundle = planInputService.assemble(user.getId());

		// then: 엔진 입력이 기대값과 같다(상품 p4·p5는 상품 목록과 시청 가능 상품 ID 모두에서 빠진다)
		List<OttProduct> expectedProducts = new ArrayList<>();
		expectedProducts.add(new OttProduct(p0, 10_000, OttProductCondition.NONE));
		expectedProducts.add(new OttProduct(p1, 7_000, OttProductCondition.NONE));
		expectedProducts.add(new OttProduct(p2, 12_000, OttProductCondition.SUBSCRIBED));
		expectedProducts.add(new OttProduct(p3, 4_500, OttProductCondition.FREE));
		List<com.ottnavi.engine.model.WatchUnit> expectedUnits = new ArrayList<>();
		expectedUnits.add(new com.ottnavi.engine.model.WatchUnit(movie.getId(), Priority.MUST, 100, Set.of(p0), false));
		expectedUnits.add(new com.ottnavi.engine.model.WatchUnit(season1.getId(), Priority.WANT, 600, Set.of(p1, p2, p3),
				false, tv.getId(), 1));
		expectedUnits.add(new com.ottnavi.engine.model.WatchUnit(season2.getId(), Priority.MAYBE, 500, Set.of(p1),
				true, tv.getId(), 2));
		assertThat(bundle.planInput()).isEqualTo(new PlanInput(expectedUnits, expectedProducts, 30_000, 600));
		assertThat(bundle.excludedProductIds()).containsExactly(p5);
		assertThat(bundle.productPrices()).doesNotContainKey(p4).doesNotContainKey(p5);

		// then: 가격 출처 4가지(원래 월 요금은 그대로, 무료 이용이어도 수정 금액 우선)
		assertThat(bundle.productPrices().get(p0).priceSource()).isEqualTo(PriceSource.ADMIN);
		assertThat(bundle.productPrices().get(p1).priceSource()).isEqualTo(PriceSource.USER_OVERRIDE);
		assertThat(bundle.productPrices().get(p1).appliedPrice()).isEqualTo(7_000);
		assertThat(bundle.productPrices().get(p2).priceSource()).isEqualTo(PriceSource.ALREADY_PAID);
		assertThat(bundle.productPrices().get(p2).appliedPrice()).isEqualTo(12_000);
		assertThat(bundle.productPrices().get(p3).priceSource()).isEqualTo(PriceSource.FREE);
		assertThat(bundle.productPrices().get(p3).appliedPrice()).isEqualTo(4_500);

		// then: 전체 구독 비용 = p0 30,000 + p1 21,000 + p2(구독 중) 24,000 + p3(무료) 0
		assertThat(bundle.allSubscribeCost()).isEqualTo(75_000);

		// then: 설정 스냅샷, 시작 달, 달 번호, 찜 매핑, 기준 시각
		assertThat(bundle.monthlyBudget()).isEqualTo(30_000);
		assertThat(bundle.monthlyWatchMinutes()).isEqualTo(600);
		assertThat(bundle.watchPreset()).isEqualTo(WatchPreset.NORMAL);
		assertThat(bundle.startMonth()).isEqualTo(TODAY.withDayOfMonth(1));
		assertThat(bundle.monthIndexes()).containsExactly(0, 1, 2);
		assertThat(bundle.wishlistItemIdByUnit()).containsOnlyKeys(movie.getId(), season1.getId(), season2.getId());
		assertThat(bundle.wishlistItemIdByUnit().get(movie.getId())).isEqualTo(movieItem.getId());
		assertThat(bundle.wishlistItemIdByUnit().get(season1.getId())).isEqualTo(s1Item.getId());
		assertThat(bundle.wishlistItemIdByUnit().get(season2.getId())).isEqualTo(s2Item.getId());
		assertThat(bundle.dataAsOf()).isEqualTo(collectDataVersionService.findDataAsOf().orElse(null));
	}

	@Test
	void 설정이_없거나_다_보지_않은_찜이_없으면_400이다() {
		// given: 온보딩 전 사용자
		User user = createUser();

		// when
		// then: 설정 없음
		assertThatThrownBy(() -> planInputService.assemble(user.getId())).isInstanceOf(FieldValidationException.class);

		// given: 설정은 있고 찜이 없다
		userSettingService.updateSettings(user.getId(), 30_000, 600, WatchPreset.NORMAL, new ArrayList<>());

		// when
		// then: 찜 없음
		assertThatThrownBy(() -> planInputService.assemble(user.getId())).isInstanceOf(FieldValidationException.class);
	}

	// 데이터 준비

	private User createUser() {
		String sub = UUID.randomUUID().toString().replace("-", "");
		return userRepository.save(User.create(EMAIL_PREFIX + sub + "@example.com", "테스터", OAuthProvider.GOOGLE, sub));
	}

	/** 단품 상품과 구성, 요금 이력 하나를 저장하고 상품 ID를 돌려준다 */
	private long saveProduct(String name, OttService service, int price, LocalDate effectiveFrom) {
		SubscriptionProduct product = subscriptionProductRepository
				.save(SubscriptionProduct.create(NAME_PREFIX + name, ProductType.SINGLE));
		productComponentRepository.save(ProductComponent.create(product, service, ProviderTier.STANDARD));
		productPriceRepository.save(ProductPrice.create(product, price, effectiveFrom, Instant.now()));
		return product.getId();
	}

	private com.ottnavi.catalog.domain.entity.WatchUnit saveUnit(MediaType mediaType, int tmdbId, int runtimeMin) {
		Title title = titleRepository.save(Title.create(mediaType, tmdbId, "작품 " + tmdbId, CHECKED_AT));
		return watchUnitRepository.save(com.ottnavi.catalog.domain.entity.WatchUnit.createMovie(title,
				new RuntimeResult(1, 1, runtimeMin, 0, false), CHECKED_AT));
	}

	/** 서비스 7개 모두에 제공 상태를 저장한다. 지정한 번호는 있음·모름, 나머지는 없음이다 */
	private void saveAllStatuses(com.ottnavi.catalog.domain.entity.WatchUnit unit, Set<Integer> available, Set<Integer> unknown) {
		for (int i = 0; i < services.size(); i++) {
			AvailabilityStatus status = AvailabilityStatus.NOT_AVAILABLE;
			UnknownReason reason = null;
			if (available.contains(i)) {
				status = AvailabilityStatus.AVAILABLE;
			} else if (unknown.contains(i)) {
				status = AvailabilityStatus.UNKNOWN;
				reason = UnknownReason.NO_KR_DATA;
			}
			availabilityRepository.save(Availability.create(unit, services.get(i), status, reason,
					status == AvailabilityStatus.AVAILABLE, false, CHECKED_AT));
		}
	}
}
