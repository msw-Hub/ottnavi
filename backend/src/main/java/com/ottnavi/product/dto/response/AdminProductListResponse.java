package com.ottnavi.product.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * OTT 구독상품 목록(계약 AdminProductList). 판매 종료 상품을 포함한다.
 *
 * @param products 구독상품 목록(상품 ID 순)
 */
public record AdminProductListResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AdminProductResponse> products) {
}
