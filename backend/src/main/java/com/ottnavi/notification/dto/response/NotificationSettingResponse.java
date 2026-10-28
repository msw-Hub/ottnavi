package com.ottnavi.notification.dto.response;

import com.ottnavi.notification.service.model.NotificationSettingView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 알림 설정 응답(계약 NotificationSetting).
 *
 * @param isNotificationEnabled 알림 수신 여부(기본 꺼짐)
 * @param email                 알림을 받을 이메일(계정 이메일, 수정 불가)
 */
public record NotificationSettingResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false") boolean isNotificationEnabled,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "user@example.com") String email) {

	/** 서비스 계층 값을 응답으로 바꾼다 */
	public static NotificationSettingResponse from(NotificationSettingView view) {
		return new NotificationSettingResponse(view.notificationEnabled(), view.email());
	}
}
