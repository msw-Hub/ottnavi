package com.ottnavi.plan.service;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.service.WatchUnitQueryService;
import com.ottnavi.catalog.service.model.ServiceStatus;
import com.ottnavi.catalog.service.model.WatchUnitSummaries;
import com.ottnavi.catalog.service.model.WatchUnitSummary;
import com.ottnavi.collect.service.CollectDataVersionService;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.engine.model.PlanInput;
import com.ottnavi.engine.model.Priority;
import com.ottnavi.engine.model.WatchUnit;
import com.ottnavi.global.error.FieldValidationException;
import com.ottnavi.plan.service.model.PlanInputBundle;
import com.ottnavi.plan.service.model.ProductPriceInfo;
import com.ottnavi.product.domain.enums.PriceSource;
import com.ottnavi.product.service.MyServicePriceService;
import com.ottnavi.product.service.model.MyServicePriceView;
import com.ottnavi.user.domain.enums.ServiceSubscriptionStatus;
import com.ottnavi.user.service.UserSettingService;
import com.ottnavi.user.service.model.UserSettingsState;
import com.ottnavi.wishlist.service.WishlistService;
import com.ottnavi.wishlist.service.model.ActiveWishlistEntry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랜 계산 입력 수집(Task 063): DB의 설정·찜·제공 상태·요금·구독 상태를 엔진 입력(PlanInput)과 전체 구독 비용으로 바꾼다.
 * 읽기만 하며 계산 실행과 DRAFT 저장은 Task 064가 한다. 다른 도메인의 리포지토리는 쓰지 않고 그 도메인의 서비스를 호출한다.
 * 쿠팡플레이를 따로 제외하지 않는다(R11-13 현행 유지: 제공 상태 "모름"은 시청 가능이 아니라 이미 시청 가능 상품에서 빠진다).
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlanInputService {

	private static final int MONTH_COUNT = 3; // 3개월 롤링 플랜. month_index는 0~2만 만든다

	private final UserSettingService userSettingService;
	private final WishlistService wishlistService;
	private final WatchUnitQueryService watchUnitQueryService;
	private final MyServicePriceService myServicePriceService;
	private final CollectDataVersionService collectDataVersionService;
	private final Clock clock;

	/**
	 * 사용자의 계산 입력을 모은다.
	 * 설정(월 예산·월 시청 시간)이 없거나 "다 봤음"이 아닌 찜이 하나도 없으면 400 VALIDATION_FAILED다(PRD FR-10).
	 */
	public PlanInputBundle assemble(Long userId) {
		// 1. 설정을 읽는다. 온보딩 전이라 예산·시청 시간이 없으면 계산할 수 없다
		UserSettingsState settings = userSettingService.getSettings(userId);
		if (!settings.isOnboardingDone() || settings.monthlyBudget() == null || settings.monthlyWatchMinutes() == null) {
			throw new FieldValidationException("monthlyBudget", "월 예산과 월 시청 시간을 먼저 입력해야 계산할 수 있습니다.");
		}

		// 2. "다 봤음"이 아닌 찜을 읽는다. 하나도 없으면 계산할 수 없다
		List<ActiveWishlistEntry> entries = wishlistService.listActiveEntries(userId);
		if (entries.isEmpty()) {
			throw new FieldValidationException("wishlist", "다 보지 않은 찜이 1개 이상 있어야 계산할 수 있습니다.");
		}

		// 3. 판매 중이고 오늘 적용 중인 요금이 있는 상품을 후보로 만든다(가격 상한 초과는 제외)
		Map<Long, ProductPriceInfo> productPrices = new LinkedHashMap<>();
		Set<Long> excludedProductIds = new HashSet<>();
		Map<Long, List<Long>> productIdsByService = new HashMap<>();
		List<OttProduct> products = buildProducts(userId, productPrices, excludedProductIds, productIdsByService);

		// 4. 찜한 시청 단위의 제공 상태를 한 번에 읽어 엔진 시청 단위로 바꾼다
		List<Long> unitIds = new ArrayList<>();
		for (ActiveWishlistEntry entry : entries) {
			unitIds.add(entry.watchUnitId());
		}
		WatchUnitSummaries summaries = watchUnitQueryService.loadSummaries(unitIds);
		Map<Long, Long> wishlistItemIdByUnit = new HashMap<>();
		Map<Long, WatchUnitSummary> unitSummaries = new LinkedHashMap<>();
		List<WatchUnit> units = buildUnits(entries, summaries, productIdsByService, wishlistItemIdByUnit, unitSummaries);

		// 5. 엔진 입력과 전체 구독 비용을 만든다(PlanInput 생성자가 시청 가능 상품 ID와 상품 목록의 일치를 다시 검증한다)
		PlanInput planInput = new PlanInput(units, products, settings.monthlyBudget(), settings.monthlyWatchMinutes());
		int allSubscribeCost = AllSubscribeCostCalculator.calculate(planInput.units(), planInput.products());

		// 6. 데이터 기준 시각, 시작 달, 달 번호(0~2)를 정한다
		// 완료된 수집이 없으면 현재 시각으로 대체한다(plan.data_as_of가 NOT NULL이라 비워 둘 수 없다)
		Optional<Instant> dataAsOf = collectDataVersionService.findDataAsOf();
		Instant dataAsOfValue = clock.instant();
		if (dataAsOf.isPresent()) {
			dataAsOfValue = dataAsOf.get();
		} else {
			log.warn("완료된 수집 실행이 없어 데이터 기준 시각을 현재 시각으로 대체한다: userId={}", userId);
		}
		LocalDate startMonth = LocalDate.now(clock).withDayOfMonth(1);
		List<Integer> monthIndexes = new ArrayList<>();
		for (int monthIndex = 0; monthIndex < MONTH_COUNT; monthIndex++) {
			monthIndexes.add(monthIndex);
		}

		return new PlanInputBundle(planInput, allSubscribeCost, productPrices, excludedProductIds, wishlistItemIdByUnit,
				unitSummaries, dataAsOfValue, startMonth, monthIndexes, settings.monthlyBudget(), settings.monthlyWatchMinutes(),
				settings.watchPreset());
	}

	/**
	 * 후보 상품을 만든다. 오늘 적용 중인 요금이 없는 상품은 내 서비스 요금 목록에서 이미 빠져 있다.
	 * 가격이 월 상한(100만 원)을 넘는 상품은 경고만 남기고 제외한다(엔진이 예외로 막는 값을 500으로 만들지 않는다).
	 * 가격 출처: 무료 이용 → FREE, 구독 중 → ALREADY_PAID, 그 외 → 사용자 수정 금액이 있으면 USER_OVERRIDE, 없으면 ADMIN.
	 * 어느 출처든 appliedPrice는 원래 월 요금이다. 비용 제외는 엔진의 구독 상태(FREE·SUBSCRIBED)가 처리한다.
	 */
	private List<OttProduct> buildProducts(Long userId, Map<Long, ProductPriceInfo> productPrices,
			Set<Long> excludedProductIds, Map<Long, List<Long>> productIdsByService) {
		List<OttProduct> products = new ArrayList<>();
		for (MyServicePriceView view : myServicePriceService.list(userId)) {
			// 1. 가격 상한을 넘으면 후보에서 뺀다
			if (view.appliedPrice() > OttProduct.MAX_MONTHLY_PRICE) {
				log.warn("월 가격이 상한을 넘어 후보 상품에서 제외한다: productId={}, appliedPrice={}", view.productId(), view.appliedPrice());
				excludedProductIds.add(view.productId());
				continue;
			}

			// 2. 구독 상태와 가격 출처를 정한다
			OttProductCondition condition = OttProductCondition.NONE;
			PriceSource basePriceSource = PriceSource.ADMIN; // 구독 상태와 무관한 기본 출처
			if (view.userPrice() != null) {
				basePriceSource = PriceSource.USER_OVERRIDE;
			}
			PriceSource priceSource = basePriceSource;
			if (view.subscriptionStatus() == ServiceSubscriptionStatus.FREE) {
				condition = OttProductCondition.FREE;
				priceSource = PriceSource.FREE;
			} else if (view.subscriptionStatus() == ServiceSubscriptionStatus.SUBSCRIBED) {
				condition = OttProductCondition.SUBSCRIBED;
				priceSource = PriceSource.ALREADY_PAID;
			}

			// 3. 상품·가격 정보·서비스별 상품 목록에 담는다
			products.add(new OttProduct(view.productId(), view.appliedPrice(), condition));
			productPrices.put(view.productId(),
					new ProductPriceInfo(view.productId(), view.productName(), view.ottServiceId(), view.appliedPrice(),
							priceSource, basePriceSource));
			List<Long> sameService = productIdsByService.get(view.ottServiceId());
			if (sameService == null) {
				sameService = new ArrayList<>();
				productIdsByService.put(view.ottServiceId(), sameService);
			}
			sameService.add(view.productId());
		}
		return products;
	}

	/**
	 * 찜을 엔진 시청 단위로 바꾼다. 제공 상태가 AVAILABLE인 서비스의 (제외되지 않은) 상품만 시청 가능 상품에 넣는다(PRD 5.4 구독 상태 반영).
	 * 제공 상태 "모름"이 있는 서비스가 하나라도 있으면 isProviderUnknown이다. 영화는 시리즈 식별자·시즌 번호가 null이다(PRD 11절 37번).
	 */
	private List<WatchUnit> buildUnits(List<ActiveWishlistEntry> entries, WatchUnitSummaries summaries,
			Map<Long, List<Long>> productIdsByService, Map<Long, Long> wishlistItemIdByUnit,
			Map<Long, WatchUnitSummary> unitSummaries) {
		List<WatchUnit> units = new ArrayList<>();
		for (ActiveWishlistEntry entry : entries) {
			// 1. 시청 단위 요약이 없거나 필요 시간이 0 이하면 계산에 넣을 수 없어 건너뛴다
			WatchUnitSummary summary = summaries.byUnitId().get(entry.watchUnitId());
			if (summary == null) {
				log.warn("찜의 시청 단위를 찾지 못해 계산 입력에서 뺀다: wishlistItemId={}, watchUnitId={}",
						entry.wishlistItemId(), entry.watchUnitId());
				continue;
			}
			if (summary.requiredMin() <= 0) {
				log.warn("필요 시간이 0분 이하라 계산 입력에서 뺀다: wishlistItemId={}, watchUnitId={}, requiredMin={}",
						entry.wishlistItemId(), entry.watchUnitId(), summary.requiredMin());
				continue;
			}

			// 2. 서비스별 제공 상태를 시청 가능 상품 ID와 모름 여부로 바꾼다
			Set<Long> watchableProductIds = new HashSet<>();
			boolean isProviderUnknown = false;
			for (ServiceStatus status : summary.availabilities()) {
				if (status.status() == AvailabilityStatus.UNKNOWN) {
					isProviderUnknown = true;
				}
				if (status.status() != AvailabilityStatus.AVAILABLE) {
					continue;
				}
				List<Long> productIds = productIdsByService.get(status.ottServiceId());
				if (productIds != null) {
					watchableProductIds.addAll(productIds);
				}
			}

			// 3. 영화는 시즌 정보를 비우고 드라마 시즌은 작품 ID·시즌 번호를 채운다
			Long titleId = null;
			if (summary.seasonNumber() != null) {
				titleId = summary.titleId();
			}
			Priority priority = Priority.valueOf(entry.priority().name());
			units.add(new WatchUnit(entry.watchUnitId(), priority, summary.requiredMin(), watchableProductIds,
					isProviderUnknown, titleId, summary.seasonNumber()));
			wishlistItemIdByUnit.put(entry.watchUnitId(), entry.wishlistItemId());
			unitSummaries.put(entry.watchUnitId(), summary); // 응답에 쓸 제목·시즌 정보를 다시 읽지 않도록 그대로 전달한다
		}
		return units;
	}
}
