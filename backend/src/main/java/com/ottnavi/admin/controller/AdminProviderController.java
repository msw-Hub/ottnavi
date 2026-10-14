package com.ottnavi.admin.controller;

import com.ottnavi.admin.dto.request.UpdateAdminProviderRequest;
import com.ottnavi.admin.dto.response.AdminProviderListResponse;
import com.ottnavi.admin.dto.response.AdminProviderResponse;
import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.provider.service.AdminProviderService;
import com.ottnavi.provider.service.model.AdminProviderView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TMDB 제공처 매핑 관리자 API.
 * Task 054 전까지는 보호되지 않는다(local 전용). 매핑 변경은 다음 수집부터 반영된다.
 */
@Slf4j
@Tag(name = "admin")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/providers")
public class AdminProviderController {

	private final AdminProviderService adminProviderService;

	/** 수집 중 발견한 TMDB 제공처 전체를 돌려준다 */
	@Operation(operationId = "listAdminProviders", summary = "TMDB 제공처 매핑 목록 조회",
			description = "미매핑을 먼저, 그다음 제공처 번호 순으로 돌려준다. 매핑 변경은 다음 수집부터 반영된다.")
	@GetMapping
	public CommonResponse<AdminProviderListResponse> listAdminProviders() {
		// 1. 서비스가 돌려준 값 묶음을 응답 항목으로 바꾼다
		List<AdminProviderView> views = adminProviderService.listProviders();
		List<AdminProviderResponse> providers = new ArrayList<>();
		for (AdminProviderView view : views) {
			providers.add(AdminProviderResponse.from(view));
		}
		return CommonResponse.success(new AdminProviderListResponse(providers));
	}

	/** 제공처 한 곳의 매핑 상태·연결 서비스·tier를 저장한다 */
	@Operation(operationId = "updateAdminProvider", summary = "제공처 매핑 변경",
			description = "매핑 변경은 다음 수집부터 반영된다. 이미 저장된 제공 상태는 바뀌지 않는다.")
	@PatchMapping("/{providerId}")
	public CommonResponse<AdminProviderResponse> updateAdminProvider(
			@Positive(message = "1 이상이어야 합니다.") @PathVariable Long providerId,
			@Valid @RequestBody UpdateAdminProviderRequest request) {
		AdminProviderView view = adminProviderService.updateProvider(
				providerId, request.mappingState(), request.ottServiceId(), request.tier());
		return CommonResponse.success(AdminProviderResponse.from(view));
	}
}
