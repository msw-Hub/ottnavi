package com.ottnavi.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.domain.enums.MediaType;
import com.ottnavi.catalog.service.WatchUnitQueryService;
import com.ottnavi.catalog.service.model.ServiceStatus;
import com.ottnavi.catalog.service.model.WatchUnitSummaries;
import com.ottnavi.catalog.service.model.WatchUnitSummary;
import com.ottnavi.collect.service.CollectDataVersionService;
import com.ottnavi.engine.model.OttProduct;
import com.ottnavi.engine.model.OttProductCondition;
import com.ottnavi.plan.service.model.PlanInputBundle;
import com.ottnavi.product.domain.enums.PriceSource;
import com.ottnavi.product.domain.enums.ProductType;
import com.ottnavi.product.service.MyServicePriceService;
import com.ottnavi.product.service.model.MyServicePriceView;
import com.ottnavi.user.domain.enums.ServiceSubscriptionStatus;
import com.ottnavi.user.domain.enums.WatchPreset;
import com.ottnavi.user.service.UserSettingService;
import com.ottnavi.user.service.model.UserSettingsState;
import com.ottnavi.wishlist.domain.enums.WishlistPriority;
import com.ottnavi.wishlist.service.WishlistService;
import com.ottnavi.wishlist.service.model.ActiveWishlistEntry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 번들·모드가 들어간 계산 입력 조립(Task 076)을 목(mock) 협력자로 확인하는 단위 테스트(Docker 불필요).
 * 막으려는 사고: 모드와 다른 요금제가 후보가 되는 일, 가격 상한 제외가 후보 선택 뒤에 일어나 서비스가 후보에서 사라지는 일,
 * 번들이 구독 상태를 갖거나 시청 가능 상품·절감액 기준에서 빠지거나 끼는 일.
 * 서비스 1 = 넷플릭스, 2 = 티빙, 3 = 웨이브로 쓴다.
 */
class PlanInputServiceBundleTest {

	private static final long USER_ID = 1L;
	private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

	/** 단품 응답 항목을 만든다. 번들이 아니라 ottServiceId가 구성 서비스 1개와 같다. */
	private static MyServicePriceView single(long productId, long serviceId, boolean isLowCost, int price, ServiceSubscriptionStatus status) {
		return new MyServicePriceView(productId, "단품" + productId, ProductType.SINGLE, serviceId, List.of(serviceId), isLowCost, false,
				price, null, price, PriceSource.ADMIN, status, DAY, Instant.parse("2026-10-01T00:00:00Z"));
	}

	/** 번들 응답 항목을 만든다. 번들은 항상 미구독이다. */
	private static MyServicePriceView bundle(long productId, boolean isLowCost, int price, Long... serviceIds) {
		return new MyServicePriceView(productId, "번들" + productId, ProductType.BUNDLE, null, List.of(serviceIds), isLowCost, false,
				price, null, price, PriceSource.ADMIN, ServiceSubscriptionStatus.NOT_SUBSCRIBED, DAY, Instant.parse("2026-10-01T00:00:00Z"));
	}

	/** 작품(시청 단위) 하나가 지정한 서비스에서만 AVAILABLE인 요약을 만든다(서비스는 1~3). */
	private static WatchUnitSummary summary(long unitId, Long... availableServiceIds) {
		List<ServiceStatus> statuses = new ArrayList<>();
		for (long serviceId = 1; serviceId <= 3; serviceId++) {
			AvailabilityStatus status = AvailabilityStatus.NOT_AVAILABLE;
			if (List.of(availableServiceIds).contains(serviceId)) {
				status = AvailabilityStatus.AVAILABLE;
			}
			statuses.add(new ServiceStatus(serviceId, status));
		}
		return new WatchUnitSummary(unitId, 1000 + unitId, MediaType.MOVIE, (int) unitId, "작품" + unitId, null, null, null, 100, false, statuses);
	}

	/** 목 협력자로 서비스를 만들어 조립 결과를 돌려준다. summaries는 작품 하나당 하나이며 모두 WANT 찜이다. */
	private static PlanInputBundle assemble(boolean lowestPriceMode, List<MyServicePriceView> views, WatchUnitSummary... summaries) {
		UserSettingService userSettingService = mock(UserSettingService.class);
		WishlistService wishlistService = mock(WishlistService.class);
		WatchUnitQueryService watchUnitQueryService = mock(WatchUnitQueryService.class);
		MyServicePriceService myServicePriceService = mock(MyServicePriceService.class);
		CollectDataVersionService collectDataVersionService = mock(CollectDataVersionService.class);
		Clock clock = Clock.fixed(Instant.parse("2026-10-17T00:00:00Z"), ZoneId.of("Asia/Seoul"));

		when(userSettingService.getSettings(USER_ID)).thenReturn(new UserSettingsState(true, List.of(), 30_000, 600, WatchPreset.NORMAL, lowestPriceMode));
		when(myServicePriceService.list(USER_ID)).thenReturn(views);
		List<ActiveWishlistEntry> entries = new ArrayList<>();
		Map<Long, WatchUnitSummary> byUnitId = new java.util.LinkedHashMap<>();
		for (WatchUnitSummary summary : summaries) {
			entries.add(new ActiveWishlistEntry(500 + summary.watchUnitId(), summary.watchUnitId(), WishlistPriority.WANT));
			byUnitId.put(summary.watchUnitId(), summary);
		}
		when(wishlistService.listActiveEntries(USER_ID)).thenReturn(entries);
		when(watchUnitQueryService.loadSummaries(org.mockito.ArgumentMatchers.anyList())).thenReturn(new WatchUnitSummaries(byUnitId, DAY));

		return new PlanInputService(userSettingService, wishlistService, watchUnitQueryService, myServicePriceService,
				collectDataVersionService, clock).assemble(USER_ID);
	}

	/** 넷플릭스 스탠다드(1)·광고형(2), 티빙(3, 구독 중), 웨이브(4), 표준 번들 티빙+웨이브(5), 광고형 번들 넷플릭스+티빙(6) */
	private static List<MyServicePriceView> sampleViews() {
		return List.of(
				single(1, 1, false, 13_500, ServiceSubscriptionStatus.NOT_SUBSCRIBED),
				single(2, 1, true, 7_000, ServiceSubscriptionStatus.NOT_SUBSCRIBED),
				single(3, 2, false, 13_500, ServiceSubscriptionStatus.SUBSCRIBED),
				single(4, 3, false, 10_900, ServiceSubscriptionStatus.NOT_SUBSCRIBED),
				bundle(5, false, 16_000, 2L, 3L),
				bundle(6, true, 18_000, 1L, 2L));
	}

	@Test
	@DisplayName("모드 끔: 서비스마다 표준 단품 1개와 표준 번들만 후보이고 번들은 구성 서비스가 제공되면 시청 가능 상품이다")
	void 모드_끔은_표준_단품과_표준_번들이_후보다() {
		// given: 작품 1은 티빙에서만, 작품 2는 넷플릭스에서만 제공된다
		PlanInputBundle bundle = assemble(false, sampleViews(), summary(1, 2L), summary(2, 1L));

		// then: 후보는 표준 단품 1·3·4와 표준 번들 5이고, 광고형(2)과 광고형 번들(6)은 빠진다. 번들의 구독 상태는 NONE이다
		assertThat(bundle.planInput().products()).containsExactly(
				new OttProduct(1, 13_500, OttProductCondition.NONE, List.of(1L)),
				new OttProduct(3, 13_500, OttProductCondition.SUBSCRIBED, List.of(2L)),
				new OttProduct(4, 10_900, OttProductCondition.NONE, List.of(3L)),
				new OttProduct(5, 16_000, OttProductCondition.NONE, List.of(2L, 3L)));
		assertThat(bundle.productPrices().get(5L).ottServiceIds()).containsExactly(2L, 3L);
		assertThat(bundle.productPrices().get(5L).priceSource()).isEqualTo(PriceSource.ADMIN);
		// then: 티빙에서만 제공되는 작품 1은 티빙 단품(3)과 티빙을 포함한 번들(5)로 볼 수 있다. 넷플릭스 작품 2는 넷플릭스 단품(1)뿐이다
		assertThat(bundle.planInput().units()).filteredOn(unit -> unit.id() == 1).singleElement()
				.satisfies(unit -> assertThat(unit.watchableProductIds()).containsExactlyInAnyOrder(3L, 5L));
		assertThat(bundle.planInput().units()).filteredOn(unit -> unit.id() == 2).singleElement()
				.satisfies(unit -> assertThat(unit.watchableProductIds()).containsExactly(1L));
		// then: 전체 구독 비용은 번들을 빼고 단품 1개씩이다: 티빙(구독 중) 13,500 × 2 + 넷플릭스 스탠다드 13,500 × 3
		assertThat(bundle.allSubscribeCost()).isEqualTo(27_000 + 40_500);
	}

	@Test
	@DisplayName("모드 켬: 서비스마다 가장 싼 단품 1개와 모든 번들이 후보이고 절감액 기준도 가장 싼 단품을 따른다")
	void 모드_켬은_가장_싼_단품과_모든_번들이_후보다() {
		PlanInputBundle bundle = assemble(true, sampleViews(), summary(1, 2L), summary(2, 1L));

		assertThat(bundle.planInput().products()).extracting(OttProduct::id).containsExactly(2L, 3L, 4L, 5L, 6L);
		// 넷플릭스 작품 2의 시청 가능 상품은 광고형 단품(2)과 넷플릭스를 포함한 광고형 번들(6)이다
		assertThat(bundle.planInput().units()).filteredOn(unit -> unit.id() == 2).singleElement()
				.satisfies(unit -> assertThat(unit.watchableProductIds()).containsExactlyInAnyOrder(2L, 6L));
		// 전체 구독 비용: 티빙(구독 중) 13,500 × 2 + 넷플릭스 광고형 7,000 × 3
		assertThat(bundle.allSubscribeCost()).isEqualTo(27_000 + 21_000);
	}

	@Test
	@DisplayName("가격 상한(100만 원) 제외는 후보 선택 전에 한다: 표준 단품이 상한을 넘으면 다음으로 싼 단품이 후보가 된다")
	void 상한_초과_제외가_후보_선택보다_먼저다() {
		// given: 넷플릭스에 상한을 넘는 표준 단품(1), 상한 안의 표준 단품(2, 20,000), 광고형 단품(3, 7,000)
		List<MyServicePriceView> views = List.of(
				single(1, 1, false, OttProduct.MAX_MONTHLY_PRICE + 1, ServiceSubscriptionStatus.NOT_SUBSCRIBED),
				single(2, 1, false, 20_000, ServiceSubscriptionStatus.NOT_SUBSCRIBED),
				single(3, 1, true, 7_000, ServiceSubscriptionStatus.NOT_SUBSCRIBED));

		// when: 모드 끔
		PlanInputBundle off = assemble(false, views, summary(1, 1L));

		// then: 상한을 넘는 1은 제외 기록만 남고, 다음으로 싼 표준 단품 2가 후보다(제외가 선택보다 늦었다면 서비스 전체가 사라진다)
		assertThat(off.excludedProductIds()).containsExactly(1L);
		assertThat(off.planInput().products()).extracting(OttProduct::id).containsExactly(2L);

		// when: 표준 단품이 상한 초과 하나뿐이면 광고형이 대신 후보가 된다(표준이 없으면 저가형)
		List<MyServicePriceView> onlyLowCostLeft = List.of(
				single(1, 1, false, OttProduct.MAX_MONTHLY_PRICE + 1, ServiceSubscriptionStatus.NOT_SUBSCRIBED),
				single(3, 1, true, 7_000, ServiceSubscriptionStatus.NOT_SUBSCRIBED));
		PlanInputBundle fallback = assemble(false, onlyLowCostLeft, summary(1, 1L));
		assertThat(fallback.planInput().products()).extracting(OttProduct::id).containsExactly(3L);
	}
}
