package com.ottnavi.admin.dto.request;

import com.ottnavi.provider.domain.enums.MappingState;
import com.ottnavi.provider.domain.enums.ProviderTier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 제공처 매핑 저장 요청(계약 UpdateAdminProviderRequest).
 *
 * @param mappingState 바꿀 매핑 상태
 * @param ottServiceId 연결할 서비스 ID. MAPPED면 필수이고 UNMAPPED·IGNORED면 null
 * @param tier         요금 tier. UNMAPPED·IGNORED면 STANDARD로 저장된다
 */
public record UpdateAdminProviderRequest(
		@NotNull(message = "매핑 상태는 필수입니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "MAPPED") MappingState mappingState,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "7") Long ottServiceId,
		@NotNull(message = "tier는 필수입니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "STANDARD") ProviderTier tier) {
}
