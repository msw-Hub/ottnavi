package com.ottnavi.product.service;

import com.ottnavi.product.domain.entity.ProductComponent;
import com.ottnavi.product.domain.entity.SubscriptionProduct;
import com.ottnavi.product.domain.repository.ProductComponentRepository;
import com.ottnavi.product.domain.repository.SubscriptionProductRepository;
import com.ottnavi.product.service.model.ProductSummary;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구독상품 요약 일괄 조회(Task 066). 저장된 플랜을 다시 보여 줄 때 상품 이름과 포함 서비스를 채우는 창구다.
 * 판매 종료 상품도 포함한다. 저장된 플랜이 가리키는 상품은 나중에 판매가 끝날 수 있기 때문이다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductSummaryQueryService {

	private final SubscriptionProductRepository subscriptionProductRepository;
	private final ProductComponentRepository productComponentRepository;

	/**
	 * 상품 ID들의 요약을 한 번에 만든다. 없는 상품 ID는 결과에 없다. 쿼리는 상품·구성 각각 한 번이다.
	 * 예: [101, 102] → {101: (넷플릭스 스탠다드, [1]), 102: (...)}
	 */
	public Map<Long, ProductSummary> loadSummaries(Collection<Long> productIds) {
		// 1. 대상이 없으면 쿼리하지 않는다
		Map<Long, ProductSummary> summaries = new HashMap<>();
		if (productIds.isEmpty()) {
			return summaries;
		}

		// 2. 상품 구성(서비스 ID)을 상품별로 모은다
		Map<Long, List<Long>> serviceIdsByProduct = new HashMap<>();
		for (ProductComponent component : productComponentRepository.findByProductIds(productIds)) {
			Long productId = component.getId().getProductId();
			List<Long> serviceIds = serviceIdsByProduct.get(productId);
			if (serviceIds == null) {
				serviceIds = new ArrayList<>();
				serviceIdsByProduct.put(productId, serviceIds);
			}
			serviceIds.add(component.getId().getOttServiceId());
		}

		// 3. 상품 이름과 합친다. 서비스 ID는 오름차순으로 맞춘다
		for (SubscriptionProduct product : subscriptionProductRepository.findAllById(productIds)) {
			List<Long> serviceIds = serviceIdsByProduct.get(product.getId());
			if (serviceIds == null) {
				serviceIds = new ArrayList<>();
			}
			Collections.sort(serviceIds);
			summaries.put(product.getId(), new ProductSummary(product.getId(), product.getName(), serviceIds));
		}
		return summaries;
	}
}
