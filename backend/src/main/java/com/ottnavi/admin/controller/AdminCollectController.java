package com.ottnavi.admin.controller;

import com.ottnavi.admin.dto.request.CreateCollectExecutionRequest;
import com.ottnavi.admin.dto.response.CollectExecutionAcceptedResponse;
import com.ottnavi.admin.dto.response.CollectExecutionChangeResponse;
import com.ottnavi.admin.dto.response.CollectExecutionResponse;
import com.ottnavi.admin.service.CollectExecutionQueryService;
import com.ottnavi.admin.service.CollectExecutionService;
import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수집 실행 관리자 API(실행·재시작·복구, 이력·변경 조회).
 * 보호는 BatchTokenAuthenticationFilter(관리자 배치 토큰)가 맡는다.
 */
@Slf4j
@Tag(name = "admin")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/collect/executions")
public class AdminCollectController {

	private final CollectExecutionService collectExecutionService;
	private final CollectExecutionQueryService collectExecutionQueryService;

	/** 수집 실행 이력을 대상 날짜 내림차순으로 한 페이지 돌려준다 */
	@Operation(operationId = "listCollectExecutions", summary = "수집 실행 이력 조회")
	@GetMapping
	public CommonResponse<PageResponse<CollectExecutionResponse>> listCollectExecutions(
			@RequestParam(defaultValue = "0") @Min(value = 0, message = "0 이상이어야 합니다.") int page,
			@RequestParam(defaultValue = "20")
			@Min(value = 1, message = "1 이상이어야 합니다.")
			@Max(value = 100, message = "100 이하여야 합니다.") int size) {
		return CommonResponse.success(collectExecutionQueryService.listExecutions(page, size));
	}

	/** 한 실행에서 제공 상태가 바뀐 작품을 한 페이지 돌려준다 */
	@Operation(operationId = "listCollectExecutionChanges", summary = "실행별 제공 상태가 바뀐 작품 조회")
	@GetMapping("/{executionId}/changes")
	public CommonResponse<PageResponse<CollectExecutionChangeResponse>> listCollectExecutionChanges(
			@Positive(message = "1 이상이어야 합니다.") @PathVariable Long executionId,
			@RequestParam(defaultValue = "0") @Min(value = 0, message = "0 이상이어야 합니다.") int page,
			@RequestParam(defaultValue = "20")
			@Min(value = 1, message = "1 이상이어야 합니다.")
			@Max(value = 100, message = "100 이하여야 합니다.") int size) {
		return CommonResponse.success(collectExecutionQueryService.listChanges(executionId, page, size));
	}

	/** 수집을 비동기로 시작하고 202와 새 실행 ID를 돌려준다 */
	@Operation(operationId = "createCollectExecution", summary = "수집 실행 요청")
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public CommonResponse<CollectExecutionAcceptedResponse> createCollectExecution(
			@Valid @RequestBody CreateCollectExecutionRequest request) {
		long executionId = collectExecutionService.start(request.targetDate(), request.tier());
		return CommonResponse.success(new CollectExecutionAcceptedResponse(executionId));
	}

	/** 중단된 실행을 실패로 복구하고 복구된 실행을 돌려준다 */
	@Operation(operationId = "recoverCollectExecution", summary = "중단된 실행을 실패로 복구")
	@PostMapping("/{executionId}/recover")
	public CommonResponse<CollectExecutionResponse> recoverCollectExecution(
			@Positive(message = "1 이상이어야 합니다.") @PathVariable Long executionId) {
		return CommonResponse.success(collectExecutionService.recover(executionId));
	}

	/** 실패한 실행을 새 실행으로 재시작하고 202와 새 실행 ID를 돌려준다 */
	@Operation(operationId = "restartCollectExecution", summary = "실패한 실행을 새 실행으로 재시작")
	@PostMapping("/{executionId}/restart")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public CommonResponse<CollectExecutionAcceptedResponse> restartCollectExecution(
			@Positive(message = "1 이상이어야 합니다.") @PathVariable Long executionId) {
		long newExecutionId = collectExecutionService.restart(executionId);
		return CommonResponse.success(new CollectExecutionAcceptedResponse(newExecutionId));
	}
}
