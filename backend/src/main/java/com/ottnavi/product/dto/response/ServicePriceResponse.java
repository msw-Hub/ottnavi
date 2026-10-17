package com.ottnavi.product.dto.response;

import com.ottnavi.product.domain.enums.PriceSource;
import com.ottnavi.product.domain.enums.ProductType;
import com.ottnavi.product.service.model.MyServicePriceView;
import com.ottnavi.user.domain.enums.ServiceSubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 상품 하나의 월 요금 이력(계약 ServicePrice, SCR-09). 단품과 번들이 함께 온다.
 *
 * @param ottServiceId       단품이 속한 서비스 내부 ID. 번들이면 null
 * @param productId          구독상품 ID. 수정·되돌리기 경로에 쓴다
 * @param productName        상품(요금제) 이름
 * @param productType        상품 종류(SINGLE 또는 BUNDLE)
 * @param ottServiceIds      상품이 포함하는 서비스 ID 목록(오름차순). 단품은 1개
 * @param isLowCost          저가형(광고형·베이직 등) 요금 분류. false면 표준
 * @param isCalcCandidate    내 설정의 모드 기준으로 이 상품이 플랜 계산 후보인지
 * @param adminPrice         관리자가 등록한 기본 월 요금 중 현재 적용 중인 요금의 금액(원)
 * @param userPrice          내가 고친 월 요금(원). 고치지 않았으면 null
 * @param appliedPrice       계산에 쓰는 금액(userPrice가 있으면 그 값, 없으면 adminPrice)
 * @param priceSource        적용 금액의 출처(ADMIN 또는 USER_OVERRIDE)
 * @param subscriptionStatus 이 서비스의 이용 상태. 번들은 항상 NOT_SUBSCRIBED
 * @param effectiveFrom      adminPrice의 적용 시작일
 * @param lastVerifiedAt     adminPrice 요금 이력을 관리자가 마지막으로 확인한 일시
 */
public record ServicePriceResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "1") Long ottServiceId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "101") Long productId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "넷플릭스 스탠다드") String productName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SINGLE") ProductType productType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> ottServiceIds,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false") boolean isLowCost,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true") boolean isCalcCandidate,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "13500") Integer adminPrice,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "9900") Integer userPrice,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "13500") Integer appliedPrice,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "ADMIN") PriceSource priceSource,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SUBSCRIBED") ServiceSubscriptionStatus subscriptionStatus,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-02") LocalDate effectiveFrom,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant lastVerifiedAt) {

	/** 서비스가 돌려준 값 묶음을 응답으로 바꾼다 */
	public static ServicePriceResponse from(MyServicePriceView view) {
		return new ServicePriceResponse(view.ottServiceId(), view.productId(), view.productName(), view.productType(),
				view.ottServiceIds(), view.isLowCost(), view.isCalcCandidate(), view.adminPrice(), view.userPrice(),
				view.appliedPrice(), view.priceSource(), view.subscriptionStatus(), view.effectiveFrom(),
				view.lastVerifiedAt());
	}
}
