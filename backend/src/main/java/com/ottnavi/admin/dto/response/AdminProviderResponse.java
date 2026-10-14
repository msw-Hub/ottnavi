package com.ottnavi.admin.dto.response;

import com.ottnavi.provider.domain.enums.MappingState;
import com.ottnavi.provider.domain.enums.ProviderTier;
import com.ottnavi.provider.service.model.AdminProviderView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * TMDB 제공처 한 곳(계약 AdminProvider).
 *
 * @param providerId   TMDB 제공처 ID(그대로 PK)
 * @param tmdbName     TMDB 원본 이름, 운영자 확인용
 * @param ottServiceId 연결된 서비스 ID. 미매핑·관련 없음이면 null
 * @param tier         요금 tier
 * @param firstSeenAt  처음 발견된 시각
 * @param mappingState 매핑 상태
 */
public record AdminProviderResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1881") Long providerId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Coupang Play") String tmdbName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "7") Long ottServiceId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "STANDARD") ProviderTier tier,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant firstSeenAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "MAPPED") MappingState mappingState) {

	/** 서비스가 돌려준 값 묶음을 응답으로 바꾼다 */
	public static AdminProviderResponse from(AdminProviderView view) {
		return new AdminProviderResponse(
				view.providerId(),
				view.tmdbName(),
				view.ottServiceId(),
				view.tier(),
				view.firstSeenAt(),
				view.mappingState());
	}
}
