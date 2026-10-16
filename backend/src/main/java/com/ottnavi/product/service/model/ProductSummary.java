package com.ottnavi.product.service.model;

import java.util.List;

/**
 * 구독상품 한 건의 요약. 플랜 조회처럼 다른 도메인이 상품 이름과 포함 서비스만 알면 될 때 쓴다.
 *
 * @param id            구독상품 ID
 * @param name          현재 상품 이름(판매 종료 상품도 이름은 남는다)
 * @param ottServiceIds 상품이 포함하는 서비스 ID들(오름차순, 단품은 1개)
 */
public record ProductSummary(long id, String name, List<Long> ottServiceIds) {
}
