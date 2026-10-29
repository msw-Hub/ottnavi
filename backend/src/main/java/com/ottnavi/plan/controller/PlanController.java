package com.ottnavi.plan.controller;

import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.global.security.AuthUser;
import com.ottnavi.plan.dto.request.CreatePlanRequest;
import com.ottnavi.plan.dto.response.CurrentPlanResponse;
import com.ottnavi.plan.dto.response.PlanChangeHistoryListResponse;
import com.ottnavi.plan.dto.response.PlanDraftListResponse;
import com.ottnavi.plan.dto.response.PlanMailPreviewResponse;
import com.ottnavi.plan.dto.response.SavedPlanResponse;
import com.ottnavi.plan.service.PlanActivateService;
import com.ottnavi.plan.service.PlanChangeHistoryService;
import com.ottnavi.plan.service.PlanCalculateService;
import com.ottnavi.plan.service.PlanMailPreviewService;
import com.ottnavi.plan.service.PlanQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 플랜 API. 로그인 사용자 본인의 플랜만 다룬다(/api/me/plan-drafts, /api/me/plans) */
@Tag(name = "plan")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me")
public class PlanController {

	private final PlanCalculateService planCalculateService;
	private final PlanActivateService planActivateService;
	private final PlanQueryService planQueryService;
	private final PlanChangeHistoryService planChangeHistoryService;
	private final PlanMailPreviewService planMailPreviewService;

	/** 내 찜·설정·요금으로 3개월 플랜을 계산해 초안 3종(추천형·절약형·간편형)을 만든다. 기존 초안은 덮어쓴다 */
	@Operation(operationId = "createPlanDrafts", summary = "플랜 계산(초안 3종 생성)")
	@PostMapping("/plan-drafts")
	@ResponseStatus(HttpStatus.CREATED)
	public CommonResponse<PlanDraftListResponse> createPlanDrafts(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser) {
		return CommonResponse.success(PlanDraftListResponse.from(planCalculateService.calculate(authUser.userId())));
	}

	/** 지금 있는 내 초안을 조회한다. 계산 전이거나 저장한 뒤에는 빈 배열이다 */
	@Operation(operationId = "listPlanDrafts", summary = "플랜 초안 조회")
	@GetMapping("/plan-drafts")
	public CommonResponse<PlanDraftListResponse> listPlanDrafts(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser) {
		return CommonResponse.success(PlanDraftListResponse.from(planQueryService.listDrafts(authUser.userId())));
	}

	/** 내 현재 플랜(ACTIVE)을 조회한다. 저장한 플랜이 없으면 200이고 plan이 null이다 */
	@Operation(operationId = "getCurrentPlan", summary = "현재 플랜 조회")
	@GetMapping("/plans/current")
	public CommonResponse<CurrentPlanResponse> getCurrentPlan(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser) {
		return CommonResponse.success(CurrentPlanResponse.from(planQueryService.getCurrent(authUser.userId())));
	}

	/** 데모 계정 전용: 현재 플랜으로 알림 메일 예시(제목·HTML)를 만든다. 메일을 보내거나 outbox에 쌓지 않는다 */
	@Operation(operationId = "getCurrentPlanMailPreview", summary = "알림 메일 예시 조회(데모 계정 전용)")
	@GetMapping("/plans/current/mail-preview")
	public CommonResponse<PlanMailPreviewResponse> getCurrentPlanMailPreview(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser) {
		return CommonResponse.success(PlanMailPreviewResponse.from(planMailPreviewService.preview(authUser.userId())));
	}

	/** 매월 재계산으로 플랜이 바뀐 변경 내역을 최신순 최대 6건 돌려준다. 없으면 200과 빈 배열이다 */
	@Operation(operationId = "listPlanChanges", summary = "플랜 변경 내역 조회")
	@GetMapping("/plan-changes")
	public CommonResponse<PlanChangeHistoryListResponse> listPlanChanges(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser) {
		return CommonResponse.success(PlanChangeHistoryListResponse.from(planChangeHistoryService.listRecent(authUser.userId())));
	}

	/** 고른 초안을 현재 플랜(ACTIVE)으로 저장한다. 기존 ACTIVE는 보관되고 나머지 초안은 삭제된다 */
	@Operation(operationId = "createPlan", summary = "플랜 저장(초안을 현재 플랜으로)")
	@PostMapping("/plans")
	@ResponseStatus(HttpStatus.CREATED)
	public CommonResponse<SavedPlanResponse> createPlan(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
			@Valid @RequestBody CreatePlanRequest request) {
		return CommonResponse.success(SavedPlanResponse.from(planActivateService.activate(authUser.userId(), request.planId())));
	}
}
