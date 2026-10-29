package com.ottnavi.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 알림 설정 저장 요청(계약 UpdateMyNotificationSettingRequest).
 *
 * @param isNotificationEnabled 알림 수신 여부
 */
public record UpdateMyNotificationSettingRequest(
		@NotNull(message = "알림 수신 여부는 필수입니다.")
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true") Boolean isNotificationEnabled) {
}
