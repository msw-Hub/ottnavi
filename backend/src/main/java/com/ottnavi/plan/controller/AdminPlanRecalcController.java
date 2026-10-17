package com.ottnavi.plan.controller;

import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.plan.batch.PlanRecalcLauncher;
import com.ottnavi.plan.dto.response.PlanRecalcExecutionAcceptedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매월 재계산 수동 실행 관리자 API(Task 069). 자동 실행이 실패했을 때 복구용으로 쓴다.
 * 보호는 BatchTokenAuthenticationFilter(관리자 배치 토큰)와 ADMIN JWT가 맡는다(/api/admin/plans/monthly/**).
 */
@Tag(name = "admin")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/plans/monthly/executions")
public class AdminPlanRecalcController {

	private final PlanRecalcLauncher planRecalcLauncher;

	/** 재계산을 비동기로 시작하고 202와 새 실행 ID를 돌려준다. 재계산이나 수집이 실행 중이면 409 BATCH_ALREADY_RUNNING이다 */
	@Operation(operationId = "createPlanRecalcExecution", summary = "매월 재계산 수동 실행 요청")
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public CommonResponse<PlanRecalcExecutionAcceptedResponse> createPlanRecalcExecution() {
		return CommonResponse.success(new PlanRecalcExecutionAcceptedResponse(planRecalcLauncher.startManual()));
	}
}
