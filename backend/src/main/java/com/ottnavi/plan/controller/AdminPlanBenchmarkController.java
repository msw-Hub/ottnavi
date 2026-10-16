package com.ottnavi.plan.controller;

import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.plan.dto.response.PlanBenchmarkResponse;
import com.ottnavi.plan.service.benchmark.PlanBenchmarkRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 완전탐색·그리디 비교 측정 관리자 API(Task 065, FR-13). /api/admin/** 규칙에 따라 ADMIN만 호출할 수 있다(배치 토큰은 허용하지 않는다).
 */
@Tag(name = "admin")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/plan-benchmarks")
public class AdminPlanBenchmarkController {

	private final PlanBenchmarkRunService planBenchmarkRunService;

	/** 정답 세트 전체를 두 알고리즘으로 측정해 calc_run에 저장하고 결과를 돌려준다 */
	@Operation(operationId = "createPlanBenchmark", summary = "완전탐색·그리디 비교 측정 실행",
			description = "정답 세트 전체를 워밍업 1회 뒤 3회 측정해 calc_run에 EXACT·GREEDY 행으로 남긴다. 요청 본문은 없다.")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CommonResponse<PlanBenchmarkResponse> createPlanBenchmark() {
		return CommonResponse.success(PlanBenchmarkResponse.from(planBenchmarkRunService.run()));
	}
}
