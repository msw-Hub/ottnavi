package com.ottnavi.product.dto.request;

import com.ottnavi.product.domain.enums.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * OTT 구독상품 등록 요청(계약 CreateAdminProductRequest, 첫 월 구독요금 포함).
 * 번들의 구성은 등록할 때만 정한다(편집 API 없음, 판매 종료 후 새로 등록).
 *
 * @param name          이름. 서버가 앞뒤 공백을 제거한 뒤 1~100자인지 검증한다
 * @param productType   상품 종류. 단품(SINGLE) 또는 번들(BUNDLE)
 * @param ottServiceIds 속한 서비스 ID 목록. 단품은 정확히 1개, 번들은 서로 다른 2개 이상
 * @param isLowCost     저가형(광고형·베이직 등) 요금 분류. false면 표준
 * @param monthlyPrice  첫 월 구독요금(원, 0~1,000,000)
 * @param effectiveFrom 첫 요금 이력의 적용 시작일(오늘 이하)
 */
public record CreateAdminProductRequest(
		@NotBlank(message = "이름은 필수입니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "디즈니+ 프리미엄") String name,
		@NotNull(message = "상품 종류는 필수입니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SINGLE") ProductType productType,
		@NotNull(message = "서비스 목록은 필수입니다.")
		@Size(min = 1, max = 7, message = "서비스는 1~7개여야 합니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
		List<@NotNull(message = "서비스 ID는 비울 수 없습니다.") @Positive(message = "1 이상이어야 합니다.") Long> ottServiceIds,
		@NotNull(message = "저가형 여부는 필수입니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false") Boolean isLowCost,
		@NotNull(message = "월 구독요금은 필수입니다.")
		@Min(value = 0, message = "0 이상이어야 합니다.") @Max(value = 1000000, message = "1,000,000 이하여야 합니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "13900") Integer monthlyPrice,
		@NotNull(message = "적용 시작일은 필수입니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-01") LocalDate effectiveFrom) {
}
