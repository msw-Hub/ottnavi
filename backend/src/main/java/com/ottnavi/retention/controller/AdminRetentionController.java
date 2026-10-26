package com.ottnavi.retention.controller;

import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.retention.dto.response.RetentionExecutionAcceptedResponse;
import com.ottnavi.retention.job.RetentionLauncher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데이터 정리 실행 관리자 API(Task 083). GitHub Actions cron이 주 1회 호출하고 관리자가 수동으로도 쓴다.
 * 보호는 BatchTokenAuthenticationFilter(관리자 배치 토큰)와 ADMIN JWT가 맡는다(/api/admin/retention/**).
 */
@Tag(name = "admin")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/retention/executions")
public class AdminRetentionController {

	private final RetentionLauncher retentionLauncher;

	/** 정리를 비동기로 시작하고 202와 새 실행 ID를 돌려준다. 정리·재계산·수집이 실행 중이면 409 BATCH_ALREADY_RUNNING이다 */
	@Operation(operationId = "createRetentionExecution", summary = "데이터 정리 실행 요청")
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public CommonResponse<RetentionExecutionAcceptedResponse> createRetentionExecution() {
		return CommonResponse.success(new RetentionExecutionAcceptedResponse(retentionLauncher.start()));
	}
}
