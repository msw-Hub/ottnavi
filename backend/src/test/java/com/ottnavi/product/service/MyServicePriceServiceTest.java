package com.ottnavi.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ottnavi.product.domain.entity.ProductComponent;
import com.ottnavi.product.domain.entity.ProductComponentId;
import com.ottnavi.product.domain.entity.ProductPrice;
import com.ottnavi.product.domain.entity.SubscriptionProduct;
import com.ottnavi.product.domain.entity.UserPriceOverride;
import com.ottnavi.product.domain.enums.PriceSource;
import com.ottnavi.product.domain.enums.ProductType;
import com.ottnavi.product.domain.repository.ProductComponentRepository;
import com.ottnavi.product.domain.repository.ProductPriceRepository;
import com.ottnavi.product.domain.repository.SubscriptionProductRepository;
import com.ottnavi.product.domain.repository.UserPriceOverrideRepository;
import com.ottnavi.product.exception.ServicePriceNotFoundException;
import com.ottnavi.product.service.model.MyServicePriceView;
import com.ottnavi.user.domain.enums.ServiceSubscriptionStatus;
import com.ottnavi.user.service.UserQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 내 서비스 요금의 적용 금액 결정을 DB 없이 확인한다. 시계는 2026-10-16 KST로 고정한다.
 * 막으려는 사고: 고친 금액이 있는데 관리자 금액이 적용되는 일, 판매 종료 상품의 금액이 저장되는 일,
 * 현재 적용 중인 요금이 아닌 요금 이력(미래 요금 이력)의 금액이 기본 요금으로 나가는 일.
 */
class MyServicePriceServiceTest {

	private static final Long USER_ID = 7L;
	private static final Long PRODUCT_ID = 10L;
	private static final Long SERVICE_ID = 3L;
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T03:00:00Z"), ZoneId.of("Asia/Seoul"));

	private SubscriptionProductRepository productRepository;
	private ProductComponentRepository componentRepository;
	private ProductPriceRepository priceRepository;
	private UserPriceOverrideRepository overrideRepository;
	private UserQueryService userQueryService;
	private MyServicePriceService service;
	private SubscriptionProduct product;

	@BeforeEach
	void setUp() {
		productRepository = mock(SubscriptionProductRepository.class);
		componentRepository = mock(ProductComponentRepository.class);
		priceRepository = mock(ProductPriceRepository.class);
		overrideRepository = mock(UserPriceOverrideRepository.class);
		userQueryService = mock(UserQueryService.class);
		service = new MyServicePriceService(productRepository, componentRepository, priceRepository,
				overrideRepository, userQueryService, CLOCK);

		product = SubscriptionProduct.create("넷플릭스 스탠다드", ProductType.SINGLE);
		ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
		ProductComponent component = mock(ProductComponent.class);
		when(component.getId()).thenReturn(new ProductComponentId(PRODUCT_ID, SERVICE_ID));
		List<ProductComponent> components = new ArrayList<>();
		components.add(component);
		when(componentRepository.findByProductIds(List.of(PRODUCT_ID))).thenReturn(components);
		when(userQueryService.mapStatusByServiceId(USER_ID)).thenReturn(new HashMap<>());
		when(overrideRepository.findByUserIdAndProductIdIn(USER_ID, List.of(PRODUCT_ID))).thenReturn(new ArrayList<>());
	}

	/** 오늘(2026-10-16) 적용 중인 요금 12000(10-02부터)과 미래 요금 이력 15000(11-11부터)을 준비한다 */
	private void givenPrices() {
		List<ProductPrice> prices = new ArrayList<>();
		prices.add(price(1L, 12000, LocalDate.of(2026, 10, 2)));
		prices.add(price(2L, 15000, LocalDate.of(2026, 11, 11)));
		when(priceRepository.findByProductIdsOrderByEffectiveFromAsc(List.of(PRODUCT_ID))).thenReturn(prices);
	}

	private ProductPrice price(long id, int monthlyPrice, LocalDate from) {
		ProductPrice price = ProductPrice.create(product, monthlyPrice, from, Instant.parse("2026-10-01T00:00:00Z"));
		ReflectionTestUtils.setField(price, "id", id);
		return price;
	}

	private void givenActiveProductListed() {
		List<SubscriptionProduct> products = new ArrayList<>();
		products.add(product);
		when(productRepository.findAllByActiveTrueOrderByIdAsc()).thenReturn(products);
	}

	@Test
	void 고친_금액이_없으면_오늘_적용_중인_관리자_금액이_적용된다() {
		// given
		givenActiveProductListed();
		givenPrices();

		// when
		List<MyServicePriceView> views = service.list(USER_ID);

		// then
		assertThat(views).hasSize(1);
		MyServicePriceView view = views.get(0);
		assertThat(view.adminPrice()).isEqualTo(12000);
		assertThat(view.userPrice()).isNull();
		assertThat(view.appliedPrice()).isEqualTo(12000);
		assertThat(view.priceSource()).isEqualTo(PriceSource.ADMIN);
		assertThat(view.effectiveFrom()).isEqualTo(LocalDate.of(2026, 10, 2));
		assertThat(view.subscriptionStatus()).isEqualTo(ServiceSubscriptionStatus.NOT_SUBSCRIBED);
	}

	@Test
	void 고친_금액이_있으면_그_금액이_적용되고_관리자_금액도_함께_보인다() {
		// given
		givenActiveProductListed();
		givenPrices();
		List<UserPriceOverride> overrides = new ArrayList<>();
		overrides.add(UserPriceOverride.create(USER_ID, PRODUCT_ID, 9900));
		when(overrideRepository.findByUserIdAndProductIdIn(USER_ID, List.of(PRODUCT_ID))).thenReturn(overrides);
		Map<Long, ServiceSubscriptionStatus> statuses = new HashMap<>();
		statuses.put(SERVICE_ID, ServiceSubscriptionStatus.SUBSCRIBED);
		when(userQueryService.mapStatusByServiceId(USER_ID)).thenReturn(statuses);

		// when
		MyServicePriceView view = service.list(USER_ID).get(0);

		// then
		assertThat(view.adminPrice()).isEqualTo(12000);
		assertThat(view.userPrice()).isEqualTo(9900);
		assertThat(view.appliedPrice()).isEqualTo(9900);
		assertThat(view.priceSource()).isEqualTo(PriceSource.USER_OVERRIDE);
		assertThat(view.subscriptionStatus()).isEqualTo(ServiceSubscriptionStatus.SUBSCRIBED);
	}

	@Test
	void 모든_요금_이력이_미래인_상품은_목록에서_빠진다() {
		// given
		givenActiveProductListed();
		List<ProductPrice> prices = new ArrayList<>();
		prices.add(price(1L, 12000, LocalDate.of(2026, 10, 17)));
		when(priceRepository.findByProductIdsOrderByEffectiveFromAsc(List.of(PRODUCT_ID))).thenReturn(prices);

		// when
		List<MyServicePriceView> views = service.list(USER_ID);

		// then
		assertThat(views).isEmpty();
	}

	@Test
	void 판매_종료_상품은_수정과_되돌리기가_404이고_저장하지_않는다() {
		// given
		product.deactivate();
		when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

		// when
		// then
		assertThatThrownBy(() -> service.set(USER_ID, PRODUCT_ID, 5000)).isInstanceOf(ServicePriceNotFoundException.class);
		assertThatThrownBy(() -> service.reset(USER_ID, PRODUCT_ID)).isInstanceOf(ServicePriceNotFoundException.class);
		verify(overrideRepository, never()).save(any(UserPriceOverride.class));
		verify(overrideRepository, never()).deleteByUserIdAndProductId(any(), any());
	}

	@Test
	void 현재_적용_중인_요금이_없는_판매_중_상품은_되돌리기가_404이고_삭제하지_않는다() {
		// given
		when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
		List<ProductPrice> prices = new ArrayList<>();
		prices.add(price(1L, 12000, LocalDate.of(2026, 10, 17)));
		when(priceRepository.findByProductIdsOrderByEffectiveFromAsc(List.of(PRODUCT_ID))).thenReturn(prices);

		// when
		// then
		assertThatThrownBy(() -> service.reset(USER_ID, PRODUCT_ID)).isInstanceOf(ServicePriceNotFoundException.class);
		verify(overrideRepository, never()).deleteByUserIdAndProductId(any(), any());
	}

	@Test
	void 없는_상품은_수정이_404다() {
		// given
		when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

		// when
		// then
		assertThatThrownBy(() -> service.set(USER_ID, PRODUCT_ID, 5000)).isInstanceOf(ServicePriceNotFoundException.class);
		verify(userQueryService, never()).lockUser(any());
	}

	@Test
	void 고친_금액이_없으면_사용자_행을_잠그고_새로_저장한다() {
		// given
		when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
		givenPrices();
		when(overrideRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());

		// when
		service.set(USER_ID, PRODUCT_ID, 9900);

		// then
		verify(userQueryService).lockUser(USER_ID);
		verify(overrideRepository).save(any(UserPriceOverride.class));
	}

	@Test
	void 고친_금액이_이미_있으면_새로_저장하지_않고_금액만_바꾼다() {
		// given
		when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
		givenPrices();
		UserPriceOverride existing = UserPriceOverride.create(USER_ID, PRODUCT_ID, 9900);
		when(overrideRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(existing));

		// when
		service.set(USER_ID, PRODUCT_ID, 8800);

		// then
		assertThat(existing.getMonthlyPrice()).isEqualTo(8800);
		verify(overrideRepository, never()).save(any(UserPriceOverride.class));
	}
}
