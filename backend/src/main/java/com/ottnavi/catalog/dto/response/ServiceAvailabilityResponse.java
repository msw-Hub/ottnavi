package com.ottnavi.catalog.dto.response;

import com.ottnavi.catalog.domain.enums.AvailabilityStatus;
import com.ottnavi.catalog.service.model.ServiceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 서비스 하나의 제공 상태(계약 ServiceAvailability).
 *
 * @param ottServiceId 서비스 내부 ID
 * @param status       제공 상태
 */
public record ServiceAvailabilityResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") long ottServiceId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "AVAILABLE") AvailabilityStatus status) {

	/** 서비스 상태 값을 응답으로 바꾼다 */
	public static ServiceAvailabilityResponse from(ServiceStatus status) {
		return new ServiceAvailabilityResponse(status.ottServiceId(), status.status());
	}
}
