package com.ottnavi.admin.controller;

import com.ottnavi.admin.dto.response.SystemMetricsResponse;
import com.ottnavi.admin.service.SystemMetricsService;
import com.ottnavi.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 시스템 지표 관리자 API. ADMIN만 호출할 수 있다(데모 계정·배치 토큰 제외, SecurityConfig) */
@Tag(name = "admin")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/system-metrics")
public class AdminSystemMetricsController {

	private final SystemMetricsService systemMetricsService;

	/** 서버의 JVM·프로세스·컨테이너 메모리를 지금 시점으로 측정해 돌려준다 */
	@Operation(operationId = "getSystemMetrics", summary = "시스템 메모리 지표 조회")
	@GetMapping
	public CommonResponse<SystemMetricsResponse> getSystemMetrics() {
		return CommonResponse.success(SystemMetricsResponse.from(systemMetricsService.measure()));
	}
}
