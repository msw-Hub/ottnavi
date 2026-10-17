package com.ottnavi.product.service;

import com.ottnavi.global.error.ErrorCode;
import com.ottnavi.global.error.FieldValidationException;
import com.ottnavi.global.error.ResourceNotFoundException;
import com.ottnavi.product.domain.entity.ProductComponent;
import com.ottnavi.product.domain.entity.ProductPrice;
import com.ottnavi.product.domain.entity.SubscriptionProduct;
import com.ottnavi.product.domain.enums.ProductType;
import com.ottnavi.product.domain.repository.ProductComponentRepository;
import com.ottnavi.product.domain.repository.ProductPriceRepository;
import com.ottnavi.product.domain.repository.SubscriptionProductRepository;
import com.ottnavi.product.domain.rule.ProductPriceRules;
import com.ottnavi.product.exception.ProductPriceConflictException;
import com.ottnavi.product.service.model.AdminProductView;
import com.ottnavi.product.service.model.ProductPriceView;
import com.ottnavi.provider.domain.entity.OttService;
import com.ottnavi.provider.domain.enums.ProviderTier;
import com.ottnavi.provider.service.OttServiceQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자의 OTT 구독상품·월 구독요금 관리(Task 059 단품, Task 076 번들·저가형 분류).
 * 요금 이력의 허용·거부 규칙은 {@link ProductPriceRules}가 판단하고, 이 서비스는 조회·저장·트랜잭션을 맡는다.
 * 날짜는 모두 한국(KST) 기준이며 시계는 {@link Clock}으로 주입한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminProductService {

	private static final int NAME_MAX_LENGTH = 100; // subscription_product.name 길이
	private static final int BUNDLE_MIN_SERVICES = 2; // 번들 구성 서비스 최소 개수
	private static final long MAX_ACTIVE_BUNDLES = 13; // 판매 중 번들 상한(후보 조합 수가 커지는 것을 막는다, 사용자 결정)

	private final SubscriptionProductRepository subscriptionProductRepository;
	private final ProductComponentRepository productComponentRepository;
	private final ProductPriceRepository productPriceRepository;
	private final OttServiceQueryService ottServiceQueryService;
	private final ProductPriceQueryService productPriceQueryService;
	private final Clock clock;

	/** 판매 종료 상품을 포함한 전체 상품과 각 상품의 요금 이력(시작일 오름차순)을 돌려준다 */
	public List<AdminProductView> listProducts() {
		// 1. 상품 전체를 읽는다
		List<SubscriptionProduct> products = subscriptionProductRepository.findAllByOrderByIdAsc();
		List<AdminProductView> views = new ArrayList<>();
		if (products.isEmpty()) {
			return views;
		}
		List<Long> productIds = new ArrayList<>();
		for (SubscriptionProduct product : products) {
			productIds.add(product.getId());
		}

		// 2. 구성(서비스 ID)과 요금 이력을 상품 ID별로 묶는다(상품마다 조회하지 않고 한 번씩만 읽는다)
		Map<Long, List<Long>> ottServiceIdsByProduct = groupServiceIds(productComponentRepository.findByProductIds(productIds));
		Map<Long, List<ProductPrice>> pricesByProduct = new HashMap<>();
		for (ProductPrice price : productPriceRepository.findByProductIdsOrderByEffectiveFromAsc(productIds)) {
			Long productId = price.getProduct().getId();
			List<ProductPrice> group = pricesByProduct.get(productId);
			if (group == null) {
				group = new ArrayList<>();
				pricesByProduct.put(productId, group);
			}
			group.add(price);
		}

		// 3. 상품마다 응답용 값 묶음을 만든다
		for (SubscriptionProduct product : products) {
			List<Long> ottServiceIds = ottServiceIdsByProduct.get(product.getId());
			List<ProductPrice> prices = pricesByProduct.get(product.getId());
			if (prices == null) {
				prices = new ArrayList<>();
			}
			views.add(toView(product, ottServiceIds, prices));
		}
		return views;
	}

	/**
	 * 단품 또는 번들 상품을 첫 월 구독요금과 함께 등록한다. 상품·구성(STANDARD)·첫 요금 이력을 한 트랜잭션으로 만든다.
	 * 단품은 서비스 1개, 번들은 서로 다른 서비스 2개 이상이며 판매 중 번들은 최대 13개다.
	 * 번들 구성은 등록할 때만 정한다(편집 API 없음). 첫 요금 이력의 마지막 확인 일시는 현재 시각이고, 시작일은 오늘 이하여야 한다.
	 */
	@Transactional
	public AdminProductView createProduct(String name, ProductType productType, List<Long> ottServiceIds,
			boolean isLowCost, int monthlyPrice, LocalDate effectiveFrom) {
		// 1. 서비스 목록이 종류에 맞는지 확인한다(단품 1개, 번들 서로 다른 2개 이상)
		validateServiceIds(productType, ottServiceIds);

		// 2. 이름과 서비스를 확인한다
		String trimmedName = normalizeName(name);
		List<OttService> services = new ArrayList<>();
		for (Long ottServiceId : ottServiceIds) {
			Optional<OttService> found = ottServiceQueryService.findById(ottServiceId);
			if (found.isEmpty()) {
				throw new FieldValidationException("ottServiceIds", "존재하지 않는 서비스입니다: " + ottServiceId);
			}
			services.add(found.get());
		}

		// 3. 번들이면 판매 중 번들이 상한(13개)을 넘지 않는지 확인한다
		if (productType == ProductType.BUNDLE) {
			validateBundleCapacity("productType");
		}

		// 4. 첫 요금 이력의 시작일이 오늘 이하인지 저장 전에 확인한다
		LocalDate today = LocalDate.now(clock);
		ProductPriceRules.validateStartDate(null, effectiveFrom, today);

		// 5. 상품 → 구성 → 첫 요금 이력 순으로 저장한다(구성은 상품 ID가 필요하다)
		SubscriptionProduct product = subscriptionProductRepository.save(
				SubscriptionProduct.create(trimmedName, productType, isLowCost));
		List<Long> savedServiceIds = new ArrayList<>();
		for (OttService service : services) {
			productComponentRepository.save(ProductComponent.create(product, service, ProviderTier.STANDARD));
			savedServiceIds.add(service.getId());
		}
		Collections.sort(savedServiceIds);
		ProductPrice firstPrice = productPriceRepository.save(
				ProductPrice.create(product, monthlyPrice, effectiveFrom, Instant.now(clock)));

		List<ProductPrice> prices = new ArrayList<>();
		prices.add(firstPrice);
		return toView(product, savedServiceIds, prices);
	}

	/**
	 * 상품의 이름·판매 여부·저가형 분류를 바꾼다. 보낸 값(null이 아닌 값)만 바꾸며 모두 없으면 오류다.
	 * 번들의 판매를 다시 시작할 때도 판매 중 번들은 최대 13개다.
	 */
	@Transactional
	public AdminProductView updateProduct(long productId, String name, Boolean isActive, Boolean isLowCost) {
		// 1. 바꿀 값이 하나는 있어야 한다
		if (name == null && isActive == null && isLowCost == null) {
			throw new FieldValidationException("request", "name, isActive, isLowCost 중 하나는 보내야 합니다.");
		}
		String trimmedName = null;
		if (name != null) {
			trimmedName = normalizeName(name);
		}

		// 2. 상품을 찾는다
		SubscriptionProduct product = findProduct(productId);

		// 3. 판매 중이 아닌 번들을 다시 팔려면 번들 상한(13개)을 넘지 않는지 확인한다
		if (isActive != null && isActive && !product.isActive() && product.getProductType() == ProductType.BUNDLE) {
			validateBundleCapacity("isActive");
		}

		// 4. 값을 바꾼다(영속 상태라 더티 체킹으로 커밋 때 UPDATE 된다)
		if (trimmedName != null) {
			product.rename(trimmedName);
		}
		if (isActive != null) {
			if (isActive) {
				product.activate();
			} else {
				product.deactivate();
			}
		}
		if (isLowCost != null) {
			product.changeLowCost(isLowCost);
		}
		return loadView(product);
	}

	/** 월 구독요금 이력을 맨 뒤에 추가한다. 시작일은 가장 최근 요금 이력보다 늦어야 하고 확인 일시는 현재 시각이다 */
	@Transactional
	public AdminProductView addPrice(long productId, int monthlyPrice, LocalDate effectiveFrom) {
		// 1. 상품과 기존 요금 이력을 읽는다
		SubscriptionProduct product = findProduct(productId);
		List<ProductPrice> prices = productPriceRepository.findByProductIdOrderByEffectiveFromAsc(productId);

		// 2. 시작일이 가장 최근 요금 이력보다 늦은지 확인한다(요금 이력이 없으면 첫 요금 이력 규칙)
		LocalDate lastFrom = null;
		ProductPrice latest = ProductPriceRules.latestOf(prices);
		if (latest != null) {
			lastFrom = latest.getEffectiveFrom();
		}
		ProductPriceRules.validateStartDate(lastFrom, effectiveFrom, LocalDate.now(clock));

		// 3. 새 요금 이력을 저장한다
		productPriceRepository.save(ProductPrice.create(product, monthlyPrice, effectiveFrom, Instant.now(clock)));
		return loadView(product);
	}

	/** 가장 최근 요금 이력만 고친다. 시작일은 바로 앞 요금 이력보다 늦어야 하고(첫 요금 이력이면 오늘 이하), 확인 일시는 현재 시각으로 바뀐다 */
	@Transactional
	public AdminProductView updatePrice(long productId, long priceId, int monthlyPrice, LocalDate effectiveFrom) {
		// 1. 상품과 요금 이력을 찾는다
		SubscriptionProduct product = findProduct(productId);
		List<ProductPrice> prices = productPriceRepository.findByProductIdOrderByEffectiveFromAsc(productId);
		ProductPrice target = findPrice(prices, priceId);

		// 2. 가장 최근 요금 이력이고, 새 시작일이 바로 앞 요금 이력보다 늦은지 확인한다
		ProductPriceRules.validateEditable(prices, target);
		LocalDate previousFrom = ProductPriceRules.previousFromOf(prices, target);
		ProductPriceRules.validateStartDate(previousFrom, effectiveFrom, LocalDate.now(clock));

		// 3. 값을 바꾼다(영속 상태라 더티 체킹으로 커밋 때 UPDATE 된다)
		target.change(monthlyPrice, effectiveFrom, Instant.now(clock));
		return loadView(product);
	}

	/** 가장 최근 요금 이력을 삭제한다. 요금 이력이 하나뿐이면 지울 수 없다 */
	@Transactional
	public void deletePrice(long productId, long priceId) {
		// 1. 상품과 요금 이력을 찾는다
		findProduct(productId);
		List<ProductPrice> prices = productPriceRepository.findByProductIdOrderByEffectiveFromAsc(productId);
		ProductPrice target = findPrice(prices, priceId);

		// 2. 가장 최근 요금 이력이고 유일한 요금 이력이 아닌지 확인한 뒤 지운다
		ProductPriceRules.validateDeletable(prices, target);
		productPriceRepository.delete(target);
	}

	/** [요금 확인함]: 현재(KST) 적용 중인 요금만 허용하고 금액은 그대로 둔 채 마지막 확인 일시를 현재 시각으로 바꾼다 */
	@Transactional
	public AdminProductView verifyPrice(long productId, long priceId) {
		// 1. 상품과 요금 이력을 찾는다
		SubscriptionProduct product = findProduct(productId);
		List<ProductPrice> prices = productPriceRepository.findByProductIdOrderByEffectiveFromAsc(productId);
		ProductPrice target = findPrice(prices, priceId);

		// 2. 현재 적용 중인 요금인지 확인한다(기준일 선택은 계산·내 요금과 같은 조회 서비스를 쓴다)
		Optional<ProductPriceView> current = productPriceQueryService.priceOn(productId, LocalDate.now(clock));
		if (current.isEmpty() || current.get().priceId() != target.getId()) {
			throw new ProductPriceConflictException(ErrorCode.PRICE_NOT_CURRENT);
		}

		// 3. 확인 일시만 바꾼다
		target.verify(Instant.now(clock));
		return loadView(product);
	}

	/** 앞뒤 공백을 제거하고 1~100자인지 확인한다 */
	private String normalizeName(String name) {
		String trimmed = name.trim();
		if (trimmed.isEmpty()) {
			throw new FieldValidationException("name", "이름은 비어 있을 수 없습니다.");
		}
		if (trimmed.length() > NAME_MAX_LENGTH) {
			throw new FieldValidationException("name", "이름은 " + NAME_MAX_LENGTH + "자 이하여야 합니다.");
		}
		return trimmed;
	}

	private SubscriptionProduct findProduct(long productId) {
		Optional<SubscriptionProduct> found = subscriptionProductRepository.findById(productId);
		if (found.isEmpty()) {
			throw new ResourceNotFoundException("해당 구독상품을 찾을 수 없습니다.");
		}
		return found.get();
	}

	/** 읽어 둔 요금 이력 목록에서 ID가 같은 요금 이력을 찾는다. 다른 상품의 요금 이력이면 목록에 없어 404가 된다 */
	private ProductPrice findPrice(List<ProductPrice> prices, long priceId) {
		for (ProductPrice price : prices) {
			if (price.getId() == priceId) {
				return price;
			}
		}
		throw new ResourceNotFoundException("해당 월 구독요금을 찾을 수 없습니다.");
	}

	/** 변경 뒤 상품의 구성·요금 이력을 다시 읽어 응답용 값 묶음을 만든다 */
	private AdminProductView loadView(SubscriptionProduct product) {
		List<Long> productIds = new ArrayList<>();
		productIds.add(product.getId());
		Map<Long, List<Long>> ottServiceIdsByProduct = groupServiceIds(productComponentRepository.findByProductIds(productIds));
		List<ProductPrice> prices = productPriceRepository.findByProductIdOrderByEffectiveFromAsc(product.getId());
		return toView(product, ottServiceIdsByProduct.get(product.getId()), prices);
	}

	/** 구성 행을 상품 ID별 서비스 ID 목록(오름차순)으로 묶는다 */
	private Map<Long, List<Long>> groupServiceIds(List<ProductComponent> components) {
		Map<Long, List<Long>> result = new HashMap<>();
		for (ProductComponent component : components) {
			Long productId = component.getId().getProductId();
			List<Long> group = result.get(productId);
			if (group == null) {
				group = new ArrayList<>();
				result.put(productId, group);
			}
			group.add(component.getId().getOttServiceId());
		}
		for (List<Long> group : result.values()) {
			Collections.sort(group);
		}
		return result;
	}

	/**
	 * 서비스 목록이 상품 종류에 맞는지 확인한다. 단품은 정확히 1개, 번들은 서로 다른 서비스 2개 이상이다.
	 * 단품에 서비스를 여러 개 보내면 거절한다(번들로 등록해야 한다).
	 */
	private void validateServiceIds(ProductType productType, List<Long> ottServiceIds) {
		Set<Long> distinct = new HashSet<>(ottServiceIds);
		if (distinct.size() != ottServiceIds.size()) {
			throw new FieldValidationException("ottServiceIds", "같은 서비스를 중복해서 보낼 수 없습니다.");
		}
		if (productType == ProductType.SINGLE && ottServiceIds.size() != 1) {
			throw new FieldValidationException("ottServiceIds", "단품은 서비스를 정확히 1개 보내야 합니다.");
		}
		if (productType == ProductType.BUNDLE && ottServiceIds.size() < BUNDLE_MIN_SERVICES) {
			throw new FieldValidationException("ottServiceIds", "번들은 서로 다른 서비스를 2개 이상 보내야 합니다.");
		}
	}

	/** 판매 중 번들이 상한(13개)에 이미 닿았으면 새로 등록하거나 판매를 재개할 수 없다(VALIDATION_FAILED) */
	private void validateBundleCapacity(String field) {
		long activeBundles = subscriptionProductRepository.countByProductTypeAndActiveTrue(ProductType.BUNDLE);
		if (activeBundles >= MAX_ACTIVE_BUNDLES) {
			throw new FieldValidationException(field, "판매 중인 번들은 최대 " + MAX_ACTIVE_BUNDLES + "개입니다.");
		}
	}

	private AdminProductView toView(SubscriptionProduct product, List<Long> ottServiceIds, List<ProductPrice> prices) {
		if (ottServiceIds == null || ottServiceIds.isEmpty()) {
			// 상품은 구성이 반드시 1행 이상이다. 없으면 데이터가 깨진 것이라 조용히 넘기지 않는다
			throw new IllegalStateException("상품에 구성 서비스가 없다: productId=" + product.getId());
		}
		List<ProductPriceView> priceViews = new ArrayList<>();
		for (ProductPrice price : prices) {
			priceViews.add(ProductPriceView.from(price));
		}
		return new AdminProductView(product.getId(), product.getName(), product.getProductType(),
				ottServiceIds, product.isLowCost(), product.isActive(), priceViews);
	}
}
