package com.ottnavi.plan.controller;

import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.global.security.AuthUser;
import com.ottnavi.plan.dto.response.PlanDraftListResponse;
import com.ottnavi.plan.service.PlanCalculateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 플랜 API. 로그인 사용자 본인의 플랜만 다룬다(/api/me/plan-drafts) */
@Tag(name = "plan")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/plan-drafts")
public class PlanController {

	private final PlanCalculateService planCalculateService;

	/** 내 찜·설정·요금으로 3개월 플랜을 계산해 초안 3종(추천형·절약형·간편형)을 만든다. 기존 초안은 덮어쓴다 */
	@Operation(operationId = "createPlanDrafts", summary = "플랜 계산(초안 3종 생성)")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CommonResponse<PlanDraftListResponse> createPlanDrafts(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser) {
		return CommonResponse.success(PlanDraftListResponse.from(planCalculateService.calculate(authUser.userId())));
	}
}
