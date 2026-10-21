package com.ottnavi.notification.controller;

import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.global.common.PageResponse;
import com.ottnavi.global.security.AuthUser;
import com.ottnavi.notification.dto.request.UpdateMyNotificationSettingRequest;
import com.ottnavi.notification.dto.response.NotificationHistoryItemResponse;
import com.ottnavi.notification.dto.response.NotificationSettingResponse;
import com.ottnavi.notification.service.NotificationQueryService;
import com.ottnavi.notification.service.NotificationSettingService;
import com.ottnavi.notification.service.model.NotificationHistoryItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 알림 설정·이력 API. 로그인 사용자 본인의 것만 다룬다(/api/me/**) */
@Tag(name = "notification")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me")
public class NotificationController {

	private final NotificationSettingService notificationSettingService;
	private final NotificationQueryService notificationQueryService;

	/** 알림 수신 여부와 수신 이메일을 조회한다. 온보딩 전이어도 200이고 수신 여부는 false다 */
	@Operation(operationId = "getMyNotificationSetting", summary = "알림 설정 조회")
	@GetMapping("/notification-setting")
	public CommonResponse<NotificationSettingResponse> getMyNotificationSetting(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser) {
		return CommonResponse.success(NotificationSettingResponse.from(
				notificationSettingService.getSetting(authUser.userId())));
	}

	/** 알림 수신 여부를 켜거나 끈다. 내 설정을 저장한 적이 없으면 409 ONBOARDING_REQUIRED */
	@Operation(operationId = "updateMyNotificationSetting", summary = "알림 설정 저장")
	@PutMapping("/notification-setting")
	public CommonResponse<NotificationSettingResponse> updateMyNotificationSetting(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
			@Valid @RequestBody UpdateMyNotificationSettingRequest request) {
		return CommonResponse.success(NotificationSettingResponse.from(
				notificationSettingService.updateSetting(authUser.userId(), request.isNotificationEnabled())));
	}

	/** 내 알림 이력을 최신순으로 한 페이지 돌려준다 */
	@Operation(operationId = "listMyNotifications", summary = "알림 발송 이력 조회")
	@GetMapping("/notifications")
	public CommonResponse<PageResponse<NotificationHistoryItemResponse>> listMyNotifications(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
			@RequestParam(defaultValue = "0") @Min(value = 0, message = "0 이상이어야 합니다.") int page,
			@RequestParam(defaultValue = "20")
			@Min(value = 1, message = "1 이상이어야 합니다.")
			@Max(value = 100, message = "100 이하여야 합니다.") int size) {
		// 1. 서비스가 돌려준 값 묶음을 응답 항목으로 바꾼다
		PageResponse<NotificationHistoryItem> found = notificationQueryService.listMine(authUser.userId(), page, size);
		List<NotificationHistoryItemResponse> content = new ArrayList<>();
		for (NotificationHistoryItem item : found.content()) {
			content.add(NotificationHistoryItemResponse.from(item));
		}
		return CommonResponse.success(PageResponse.of(content, found.page(), found.size(), found.totalElements()));
	}
}
