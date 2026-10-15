package com.ottnavi.user.controller;

import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.global.security.AuthUser;
import com.ottnavi.user.dto.request.UpdateMySettingsRequest;
import com.ottnavi.user.dto.request.UserSubscriptionRequest;
import com.ottnavi.user.dto.response.UserSettingsResponse;
import com.ottnavi.user.service.UserSettingService;
import com.ottnavi.user.service.model.SubscriptionState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 설정 API. 로그인 사용자 본인의 설정만 다룬다(/api/me/**) */
@Tag(name = "user")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/settings")
public class UserSettingController {

	private final UserSettingService userSettingService;

	/** 월 예산·월 시청 시간·서비스별 이용 상태를 조회한다. 온보딩 전이면 isOnboardingDone=false다 */
	@Operation(operationId = "getMySettings", summary = "내 설정 조회")
	@GetMapping
	public CommonResponse<UserSettingsResponse> getMySettings(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser) {
		return CommonResponse.success(UserSettingsResponse.from(userSettingService.getSettings(authUser.userId())));
	}

	/** 설정 전체를 교체 저장하고 저장된 설정을 돌려준다 */
	@Operation(operationId = "updateMySettings", summary = "내 설정 저장")
	@PutMapping
	public CommonResponse<UserSettingsResponse> updateMySettings(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
			@Valid @RequestBody UpdateMySettingsRequest request) {
		List<SubscriptionState> subscriptions = new ArrayList<>();
		for (UserSubscriptionRequest item : request.subscriptions()) {
			subscriptions.add(new SubscriptionState(item.ottServiceId(), item.status(), item.planName(), item.billingDay()));
		}
		return CommonResponse.success(UserSettingsResponse.from(userSettingService.updateSettings(
				authUser.userId(), request.monthlyBudget(), request.monthlyWatchMinutes(),
				request.watchPreset(), subscriptions)));
	}
}
