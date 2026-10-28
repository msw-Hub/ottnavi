package com.ottnavi.admin.controller;

import com.ottnavi.admin.dto.response.AdminNotificationSummaryResponse;
import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.notification.service.NotificationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 알림 발송 상태 관리자 API. /api/admin/** 이므로 ADMIN 권한이 필요하다 */
@Tag(name = "admin")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

	private final NotificationQueryService notificationQueryService;

	/** 최근 7일 알림의 상태별 건수와 마지막 발송 시각을 돌려준다. 사용자 이메일·본문은 담지 않는다 */
	@Operation(operationId = "getAdminNotificationSummary", summary = "알림 발송 상태 요약 조회")
	@GetMapping("/summary")
	public CommonResponse<AdminNotificationSummaryResponse> getAdminNotificationSummary() {
		return CommonResponse.success(AdminNotificationSummaryResponse.from(notificationQueryService.getSummary()));
	}
}
