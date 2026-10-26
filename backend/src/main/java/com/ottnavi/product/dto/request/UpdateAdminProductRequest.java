package com.ottnavi.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OTT 구독상품 수정 요청(계약 UpdateAdminProductRequest). 보낸 필드만 바꾸며 하나 이상 필요하다.
 *
 * @param name      이름. 서버가 앞뒤 공백을 제거한 뒤 1~100자인지 검증한다
 * @param isActive  false 판매 종료 / true 판매 재개
 * @param isLowCost 저가형(광고형·베이직 등) 요금 분류
 */
public record UpdateAdminProductRequest(
		@Schema(example = "넷플릭스 스탠다드") String name,
		@Schema(example = "false") Boolean isActive,
		@Schema(example = "false") Boolean isLowCost) {
}
