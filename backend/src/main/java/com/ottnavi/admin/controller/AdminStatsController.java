package com.ottnavi.admin.controller;

import com.ottnavi.admin.dto.response.AdminUserStatsResponse;
import com.ottnavi.admin.dto.response.AdminVisitorStatsResponse;
import com.ottnavi.admin.service.AdminUserStatsService;
import com.ottnavi.admin.service.AdminVisitorStatsService;
import com.ottnavi.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 모니터링 운영 현황 API. /api/admin/** 이므로 ADMIN·DEMO(조회만)가 호출할 수 있다 */
@Tag(name = "admin")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminStatsController {

	private final AdminUserStatsService adminUserStatsService;
	private final AdminVisitorStatsService adminVisitorStatsService;

	/** 회원 현황(전체·신규·활성 플랜 보유·알림 켠 회원 수)을 돌려준다. 이메일 등 개인정보는 담지 않는다 */
	@Operation(operationId = "getAdminUserStats", summary = "회원 현황 조회")
	@GetMapping("/user-stats")
	public CommonResponse<AdminUserStatsResponse> getAdminUserStats() {
		return CommonResponse.success(AdminUserStatsResponse.from(adminUserStatsService.getUserStats()));
	}

	/** 동의한 방문자 수(오늘·어제·최근 7일·최근 30일)를 돌려준다. 연동이 없거나 조회에 실패해도 200이고 status로 알린다 */
	@Operation(operationId = "getAdminVisitorStats", summary = "방문자 통계 조회")
	@GetMapping("/visitor-stats")
	public CommonResponse<AdminVisitorStatsResponse> getAdminVisitorStats() {
		return CommonResponse.success(AdminVisitorStatsResponse.from(adminVisitorStatsService.getVisitorStats()));
	}
}
