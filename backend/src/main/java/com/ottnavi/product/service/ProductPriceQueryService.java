package com.ottnavi.product.service;

import com.ottnavi.product.domain.entity.ProductPrice;
import com.ottnavi.product.domain.repository.ProductPriceRepository;
import com.ottnavi.product.domain.rule.ProductPriceRules;
import com.ottnavi.product.service.model.ProductPriceView;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구독상품의 기준일 월 구독요금 조회. 관리자 [요금 확인함]의 "오늘 적용 중" 판정과
 * 이후 플랜 계산(Task 063)·내 요금(Task 060)이 같은 규칙으로 쓴다(PRD 11절 40번: 결제일 이하 중 가장 늦은 줄).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductPriceQueryService {

	private final ProductPriceRepository productPriceRepository;

	/**
	 * 기준 날짜에 적용되는 월 구독요금 줄을 돌려준다.
	 * 예: 줄이 2026-01-01, 2026-10-02, 2026-11-11이면 2026-10-16 기준 → 2026-10-02 줄, 2025-12-31 기준 → 빈 값.
	 *
	 * @param productId 구독상품 ID
	 * @param date      기준 날짜(한국 날짜. 오늘 또는 사용자 결제일)
	 */
	public Optional<ProductPriceView> priceOn(long productId, LocalDate date) {
		// 1. 상품의 요금 줄을 읽는다(상품당 줄이 적어 한 번에 읽고 규칙으로 고른다)
		List<ProductPrice> prices = productPriceRepository.findByProductIdOrderByEffectiveFromAsc(productId);

		// 2. 기준 날짜 이하 중 가장 늦은 줄을 고른다
		Optional<ProductPrice> selected = ProductPriceRules.selectOn(prices, date);
		if (selected.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(ProductPriceView.from(selected.get()));
	}
}
