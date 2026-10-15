package com.ottnavi.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ottnavi.global.error.ErrorCode;
import com.ottnavi.global.error.FieldValidationException;
import com.ottnavi.global.error.ResourceNotFoundException;
import com.ottnavi.product.domain.entity.ProductComponent;
import com.ottnavi.product.domain.entity.ProductComponentId;
import com.ottnavi.product.domain.entity.ProductPrice;
import com.ottnavi.product.domain.entity.SubscriptionProduct;
import com.ottnavi.product.domain.enums.ProductType;
import com.ottnavi.product.domain.repository.ProductComponentRepository;
import com.ottnavi.product.domain.repository.ProductPriceRepository;
import com.ottnavi.product.domain.repository.SubscriptionProductRepository;
import com.ottnavi.product.exception.ProductPriceConflictException;
import com.ottnavi.product.service.model.AdminProductView;
import com.ottnavi.provider.service.OttServiceQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 관리자 상품 서비스 단위 시험(Task 059): 확인함 허용·거부와 입력 검증. 시계는 2026-10-16 KST로 고정한다 */
class AdminProductServiceTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T03:00:00Z"), KST);

	private final SubscriptionProductRepository productRepository = mock(SubscriptionProductRepository.class);
	private final ProductComponentRepository componentRepository = mock(ProductComponentRepository.class);
	private final ProductPriceRepository priceRepository = mock(ProductPriceRepository.class);
	private final OttServiceQueryService ottServiceQueryService = mock(OttServiceQueryService.class);
	private final ProductPriceQueryService priceQueryService = new ProductPriceQueryService(priceRepository);
	private final AdminProductService service = new AdminProductService(productRepository, componentRepository,
			priceRepository, ottServiceQueryService, priceQueryService, CLOCK);

	private SubscriptionProduct product() {
		SubscriptionProduct product = SubscriptionProduct.create("넷플릭스 스탠다드", ProductType.SINGLE);
		ReflectionTestUtils.setField(product, "id", 10L);
		return product;
	}

	private ProductPrice price(long id, LocalDate effectiveFrom) {
		ProductPrice price = ProductPrice.create(null, 12000, effectiveFrom, Instant.parse("2026-01-01T00:00:00Z"));
		ReflectionTestUtils.setField(price, "id", id);
		return price;
	}

	/** 지난 줄(1), 오늘 적용 중인 줄(2), 미래 줄(3)이 있는 상품을 준비한다 */
	private List<ProductPrice> prepareThreeRows() {
		List<ProductPrice> prices = new ArrayList<>();
		prices.add(price(1L, LocalDate.of(2026, 1, 1)));
		prices.add(price(2L, LocalDate.of(2026, 10, 2)));
		prices.add(price(3L, LocalDate.of(2026, 11, 11)));
		when(productRepository.findById(10L)).thenReturn(Optional.of(product()));
		when(priceRepository.findByProductIdOrderByEffectiveFromAsc(10L)).thenReturn(prices);
		return prices;
	}

	@Test
	void 오늘_적용_중인_줄을_확인하면_확인_일시가_현재_시각으로_바뀐다() {
		// given
		List<ProductPrice> prices = prepareThreeRows();
		ProductComponent component = mock(ProductComponent.class);
		when(component.getId()).thenReturn(new ProductComponentId(10L, 1L));
		when(componentRepository.findByProductIds(List.of(10L))).thenReturn(List.of(component));

		// when
		AdminProductView view = service.verifyPrice(10L, 2L);

		// then
		assertThat(prices.get(1).getLastVerifiedAt()).isEqualTo(Instant.parse("2026-10-16T03:00:00Z"));
		assertThat(view.ottServiceId()).isEqualTo(1L);
	}

	@Test
	void 지난_줄을_확인하면_PRICE_NOT_CURRENT다() {
		// given
		prepareThreeRows();

		// when
		// then
		assertThatThrownBy(() -> service.verifyPrice(10L, 1L))
				.isInstanceOf(ProductPriceConflictException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PRICE_NOT_CURRENT);
	}

	@Test
	void 예정_줄을_확인하면_PRICE_NOT_CURRENT다() {
		// given
		prepareThreeRows();

		// when
		// then
		assertThatThrownBy(() -> service.verifyPrice(10L, 3L))
				.isInstanceOf(ProductPriceConflictException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PRICE_NOT_CURRENT);
	}

	@Test
	void 다른_상품의_줄이거나_없는_줄이면_ResourceNotFoundException이다() {
		// given
		prepareThreeRows();

		// when
		// then
		assertThatThrownBy(() -> service.verifyPrice(10L, 99L)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void 없는_상품이면_ResourceNotFoundException이다() {
		// given
		when(productRepository.findById(404L)).thenReturn(Optional.empty());

		// when
		// then
		assertThatThrownBy(() -> service.updateProduct(404L, "새 이름", null))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void 수정할_값이_하나도_없으면_VALIDATION_FAILED다() {
		// given
		// when
		// then
		assertThatThrownBy(() -> service.updateProduct(10L, null, null))
				.isInstanceOf(FieldValidationException.class);
	}

	@Test
	void 공백만_있는_이름은_name_필드_오류다() {
		// given
		// when
		// then
		assertThatThrownBy(() -> service.updateProduct(10L, "   ", null))
				.isInstanceOf(FieldValidationException.class)
				.extracting("field").isEqualTo("name");
	}

	@Test
	void 등록은_단품이_아니면_productType_필드_오류다() {
		// given
		// when
		// then
		assertThatThrownBy(() -> service.createProduct("번들", ProductType.BUNDLE, 1L, 1000, LocalDate.of(2026, 10, 1)))
				.isInstanceOf(FieldValidationException.class)
				.extracting("field").isEqualTo("productType");
	}

	@Test
	void 등록은_없는_서비스면_ottServiceId_필드_오류다() {
		// given
		when(ottServiceQueryService.findById(99L)).thenReturn(Optional.empty());

		// when
		// then
		assertThatThrownBy(() -> service.createProduct("상품", ProductType.SINGLE, 99L, 1000, LocalDate.of(2026, 10, 1)))
				.isInstanceOf(FieldValidationException.class)
				.extracting("field").isEqualTo("ottServiceId");
	}

}
