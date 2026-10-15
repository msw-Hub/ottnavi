package com.ottnavi.product.service;

import com.ottnavi.product.domain.entity.ProductComponent;
import com.ottnavi.product.domain.entity.ProductPrice;
import com.ottnavi.product.domain.entity.SubscriptionProduct;
import com.ottnavi.product.domain.entity.UserPriceOverride;
import com.ottnavi.product.domain.enums.PriceSource;
import com.ottnavi.product.domain.repository.ProductComponentRepository;
import com.ottnavi.product.domain.repository.ProductPriceRepository;
import com.ottnavi.product.domain.repository.SubscriptionProductRepository;
import com.ottnavi.product.domain.repository.UserPriceOverrideRepository;
import com.ottnavi.product.domain.rule.ProductPriceRules;
import com.ottnavi.product.exception.ServicePriceNotFoundException;
import com.ottnavi.product.service.model.MyServicePriceView;
import com.ottnavi.user.domain.enums.ServiceSubscriptionStatus;
import com.ottnavi.user.service.UserQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 서비스별 월 요금 조회·수정·되돌리기(Task 060, FR-11). 관리자 요금표는 바꾸지 않고
 * 사용자가 고친 금액(user_price_override)만 상품 단위로 저장한다.
 * 기준일은 오늘(KST)이며 요금 이력 선택은 {@link ProductPriceRules#selectOn}을 쓴다(PRD 11절 40번).
 * 같은 사용자의 동시 수정은 사용자 행 잠금으로 한 줄로 세워, (user_id, product_id) 유니크 위반(500)을 막는다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MyServicePriceService {

	private final SubscriptionProductRepository subscriptionProductRepository;
	private final ProductComponentRepository productComponentRepository;
	private final ProductPriceRepository productPriceRepository;
	private final UserPriceOverrideRepository userPriceOverrideRepository;
	private final UserQueryService userQueryService;
	private final Clock clock;

	/**
	 * 판매 중인 상품의 요금을 서비스 ID 순으로 돌려준다. 현재 적용 중인 요금이 없는 상품(첫 요금 이력이 미래 등)은
	 * 화면에 보여 줄 기본 요금이 없으므로 빼고 경고를 남긴다.
	 */
	public List<MyServicePriceView> list(Long userId) {
		// 1. 판매 중인 상품만 읽는다
		List<SubscriptionProduct> products = subscriptionProductRepository.findAllByActiveTrueOrderByIdAsc();

		// 2. 상품·구성·요금·내 금액·이용 상태를 일괄 조회해 상품마다 값을 만든다
		return buildViews(userId, products);
	}

	/**
	 * 이 상품의 월 요금을 내가 아는 금액으로 고친다. 이미 고친 금액이 있으면 금액만 바꾸고 없으면 만든다.
	 * 판매 중이 아니거나 없는 상품은 404 SERVICE_PRICE_NOT_FOUND다.
	 */
	@Transactional
	public MyServicePriceView set(Long userId, Long productId, int monthlyPrice) {
		// 1. 고칠 수 있는 상품(판매 중)인지 확인한다
		SubscriptionProduct product = findActiveProduct(productId);

		// 2. 사용자 행을 잠가 같은 사용자의 동시 수정을 한 줄로 세운다(없는 행에 대한 동시 INSERT 유니크 위반 방지)
		userQueryService.lockUser(userId);

		// 3. 있으면 금액만 바꾸고(더티 체킹으로 커밋 때 UPDATE), 없으면 새로 만든다
		Optional<UserPriceOverride> existing = userPriceOverrideRepository.findByUserIdAndProductId(userId, productId);
		if (existing.isPresent()) {
			existing.get().changePrice(monthlyPrice);
		} else {
			userPriceOverrideRepository.save(UserPriceOverride.create(userId, productId, monthlyPrice));
		}

		// 4. 바뀐 항목을 목록과 같은 방식으로 만들어 돌려준다
		List<SubscriptionProduct> one = new ArrayList<>();
		one.add(product);
		List<MyServicePriceView> views = buildViews(userId, one);
		if (views.isEmpty()) {
			// 오늘 적용 중인 기본 요금이 없어 목록에도 나오지 않는 상품이다. 응답을 만들 수 없으므로 고칠 수 없는 상품으로 본다
			throw new ServicePriceNotFoundException("오늘 적용 중인 요금이 없는 상품입니다: " + productId);
		}
		log.info("내 요금 수정: userId={}, productId={}, monthlyPrice={}", userId, productId, monthlyPrice);
		return views.get(0);
	}

	/**
	 * 내가 고친 금액을 지워 기본 요금으로 되돌린다. 고친 금액이 없어도 오류 없이 끝난다(멱등).
	 * 판매 중이 아니거나 없는 상품, 현재 적용 중인 요금이 없는 상품은 404 SERVICE_PRICE_NOT_FOUND다.
	 */
	@Transactional
	public void reset(Long userId, Long productId) {
		// 1. 고칠 수 있는 상품(판매 중)인지 확인한다
		SubscriptionProduct product = findActiveProduct(productId);

		// 2. 현재 적용 중인 요금이 없는 상품은 PUT과 같게 404로 본다(목록에도 나오지 않는 상품)
		List<SubscriptionProduct> one = new ArrayList<>();
		one.add(product);
		if (buildViews(userId, one).isEmpty()) {
			throw new ServicePriceNotFoundException("오늘 적용 중인 요금이 없는 상품입니다: " + productId);
		}

		// 3. 본인 행만 벌크 삭제한다(없으면 0건)
		int deleted = userPriceOverrideRepository.deleteByUserIdAndProductId(userId, productId);
		log.info("내 요금 되돌리기: userId={}, productId={}, 삭제 건수={}", userId, productId, deleted);
	}

	/** 판매 중인 상품을 찾는다. 없거나 판매 종료면 404 */
	private SubscriptionProduct findActiveProduct(Long productId) {
		Optional<SubscriptionProduct> found = subscriptionProductRepository.findById(productId);
		if (found.isEmpty() || !found.get().isActive()) {
			throw new ServicePriceNotFoundException("요금을 고칠 수 있는 상품이 아닙니다: " + productId);
		}
		return found.get();
	}

	/**
	 * 상품들의 구성·요금 이력·내 금액·이용 상태를 한 번씩만 읽어 응답 항목을 만든다(상품마다 조회하지 않는다).
	 * 서비스 ID 오름차순, 같은 서비스 안에서는 상품 ID 오름차순이다.
	 */
	private List<MyServicePriceView> buildViews(Long userId, List<SubscriptionProduct> products) {
		List<MyServicePriceView> result = new ArrayList<>();
		if (products.isEmpty()) {
			return result;
		}

		// 1. 상품 ID 목록
		List<Long> productIds = new ArrayList<>();
		for (SubscriptionProduct product : products) {
			productIds.add(product.getId());
		}

		// 2. 상품별 서비스 ID(단품은 구성 1행)
		Map<Long, Long> ottServiceIdByProduct = new HashMap<>();
		for (ProductComponent component : productComponentRepository.findByProductIds(productIds)) {
			ottServiceIdByProduct.put(component.getId().getProductId(), component.getId().getOttServiceId());
		}

		// 3. 상품별 요금 이력
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

		// 4. 상품별 내가 고친 금액과 서비스별 이용 상태
		Map<Long, Integer> overrideByProduct = new HashMap<>();
		for (UserPriceOverride override : userPriceOverrideRepository.findByUserIdAndProductIdIn(userId, productIds)) {
			overrideByProduct.put(override.getProductId(), override.getMonthlyPrice());
		}
		Map<Long, ServiceSubscriptionStatus> statusByService = userQueryService.mapStatusByServiceId(userId);

		// 5. 서비스 ID 순으로 모은다(TreeMap이 키 순서를 유지한다)
		LocalDate today = LocalDate.now(clock);
		Map<Long, List<MyServicePriceView>> viewsByService = new TreeMap<>();
		for (SubscriptionProduct product : products) {
			Long ottServiceId = ottServiceIdByProduct.get(product.getId());
			if (ottServiceId == null) {
				log.warn("구성 서비스가 없는 상품이라 내 요금에서 뺀다: productId={}", product.getId());
				continue;
			}
			List<ProductPrice> prices = pricesByProduct.get(product.getId());
			if (prices == null) {
				prices = new ArrayList<>();
			}

			// 6. 현재 적용 중인 요금이 없으면(모든 요금 이력이 미래) 목록에서 뺀다
			Optional<ProductPrice> current = ProductPriceRules.selectOn(prices, today);
			if (current.isEmpty()) {
				log.warn("현재 적용 중인 요금이 없어 내 요금에서 뺀다: productId={}", product.getId());
				continue;
			}

			// 7. 고친 금액이 있으면 그 값이 적용 금액이다
			ProductPrice price = current.get();
			Integer userPrice = overrideByProduct.get(product.getId());
			int appliedPrice = price.getMonthlyPrice();
			PriceSource priceSource = PriceSource.ADMIN;
			if (userPrice != null) {
				appliedPrice = userPrice;
				priceSource = PriceSource.USER_OVERRIDE;
			}
			ServiceSubscriptionStatus status = statusByService.get(ottServiceId);
			if (status == null) {
				status = ServiceSubscriptionStatus.NOT_SUBSCRIBED; // 미구독은 행을 두지 않는다
			}

			MyServicePriceView view = new MyServicePriceView(product.getId(), product.getName(), ottServiceId,
					price.getMonthlyPrice(), userPrice, appliedPrice, priceSource, status,
					price.getEffectiveFrom(), price.getLastVerifiedAt());
			List<MyServicePriceView> group = viewsByService.get(ottServiceId);
			if (group == null) {
				group = new ArrayList<>();
				viewsByService.put(ottServiceId, group);
			}
			group.add(view);
		}

		// 8. 서비스 순서대로 펼친다
		for (List<MyServicePriceView> group : viewsByService.values()) {
			result.addAll(group);
		}
		return result;
	}
}
