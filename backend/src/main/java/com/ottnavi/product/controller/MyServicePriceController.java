package com.ottnavi.product.controller;

import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.global.security.AuthUser;
import com.ottnavi.product.dto.request.SetMyServicePriceRequest;
import com.ottnavi.product.dto.response.ServicePriceListResponse;
import com.ottnavi.product.dto.response.ServicePriceResponse;
import com.ottnavi.product.service.MyServicePriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 내 서비스 요금 API(Task 060). 로그인 사용자 본인의 요금만 다룬다(/api/me/service-prices) */
@Tag(name = "product")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/service-prices")
public class MyServicePriceController {

	private final MyServicePriceService myServicePriceService;

	/** 판매 중인 상품의 기본 요금·내가 고친 금액·적용 금액·이용 상태를 서비스 ID 순으로 조회한다 */
	@Operation(operationId = "listMyServicePrices", summary = "내 서비스별 월 요금 조회")
	@GetMapping
	public CommonResponse<ServicePriceListResponse> listMyServicePrices(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser) {
		return CommonResponse.success(ServicePriceListResponse.from(myServicePriceService.list(authUser.userId())));
	}

	/** 이 상품의 월 요금을 내가 아는 금액으로 고치고 바뀐 항목을 돌려준다 */
	@Operation(operationId = "setMyServicePrice", summary = "내 월 요금 설정")
	@PutMapping("/{productId}")
	public CommonResponse<ServicePriceResponse> setMyServicePrice(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
			@Positive(message = "1 이상이어야 합니다.") @PathVariable Long productId,
			@Valid @RequestBody SetMyServicePriceRequest request) {
		return CommonResponse.success(ServicePriceResponse.from(
				myServicePriceService.set(authUser.userId(), productId, request.monthlyPrice())));
	}

	/** 내가 고친 금액을 지워 기본 요금으로 되돌린다. 고친 금액이 없어도 204다 */
	@Operation(operationId = "resetMyServicePrice", summary = "내 월 요금 지우기(기본 요금으로 복귀)")
	@DeleteMapping("/{productId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resetMyServicePrice(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
			@Positive(message = "1 이상이어야 합니다.") @PathVariable Long productId) {
		myServicePriceService.reset(authUser.userId(), productId);
	}
}
