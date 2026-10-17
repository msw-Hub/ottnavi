package com.ottnavi.home.controller;

import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.home.dto.response.PopularityExecutionAcceptedResponse;
import com.ottnavi.home.job.PopularityLauncher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인기 순위 수집 실행 관리자 API(Task 107). GitHub Actions cron이 하루 1회 호출하고 관리자가 수동으로도 쓴다.
 * 보호는 BatchTokenAuthenticationFilter(관리자 배치 토큰)와 ADMIN JWT가 맡는다(/api/admin/popularity/**).
 */
@Tag(name = "admin")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/popularity/executions")
public class AdminPopularityController {

	private final PopularityLauncher popularityLauncher;

	/** 인기 수집을 비동기로 시작하고 202와 새 실행 ID를 돌려준다. 인기 수집·정리·재계산·수집이 실행 중이면 409 BATCH_ALREADY_RUNNING이다 */
	@Operation(operationId = "createPopularityExecution", summary = "인기 순위 수집 실행 요청")
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public CommonResponse<PopularityExecutionAcceptedResponse> createPopularityExecution() {
		return CommonResponse.success(new PopularityExecutionAcceptedResponse(popularityLauncher.start()));
	}
}
