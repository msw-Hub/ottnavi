package com.ottnavi.product.controller;

import com.ottnavi.global.common.CommonResponse;
import com.ottnavi.product.dto.request.AdminProductPriceRequest;
import com.ottnavi.product.dto.request.CreateAdminProductRequest;
import com.ottnavi.product.dto.request.UpdateAdminProductRequest;
import com.ottnavi.product.dto.response.AdminProductListResponse;
import com.ottnavi.product.dto.response.AdminProductResponse;
import com.ottnavi.product.service.AdminProductService;
import com.ottnavi.product.service.model.AdminProductView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * OTT 구독상품·월 구독요금 관리자 API(Task 059). /api/admin/** 규칙에 따라 ADMIN만 호출할 수 있다.
 */
@Slf4j
@Tag(name = "admin")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/products")
public class AdminProductController {

	private final AdminProductService adminProductService;

	/** 판매 종료 상품을 포함한 전체 상품과 월 구독요금 이력을 돌려준다 */
	@Operation(operationId = "listAdminProducts", summary = "OTT 구독상품·월 구독요금 이력 조회",
			description = "판매 종료 상품을 포함해 전체를, 요금 이력은 적용 시작일이 빠른 순으로 돌려준다.")
	@GetMapping
	public CommonResponse<AdminProductListResponse> listAdminProducts() {
		List<AdminProductResponse> products = new ArrayList<>();
		for (AdminProductView view : adminProductService.listProducts()) {
			products.add(AdminProductResponse.from(view));
		}
		return CommonResponse.success(new AdminProductListResponse(products));
	}

	/** 단품 또는 번들 상품을 첫 월 구독요금과 함께 등록한다 */
	@Operation(operationId = "createAdminProduct", summary = "OTT 구독상품 등록",
			description = "단품(SINGLE)은 서비스 1개, 번들(BUNDLE)은 서로 다른 서비스 2개 이상이고 판매 중 번들은 최대 13개다. "
					+ "번들 구성은 등록할 때만 정한다. 첫 요금 이력의 적용 시작일은 오늘 이하여야 한다.")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CommonResponse<AdminProductResponse> createAdminProduct(
			@Valid @RequestBody CreateAdminProductRequest request) {
		AdminProductView view = adminProductService.createProduct(request.name(), request.productType(),
				request.ottServiceIds(), request.isLowCost(), request.monthlyPrice(), request.effectiveFrom());
		return CommonResponse.success(AdminProductResponse.from(view));
	}

	/** 상품의 이름·판매 여부·저가형 분류를 바꾼다 */
	@Operation(operationId = "updateAdminProduct", summary = "OTT 구독상품 이름·판매 여부·저가형 분류 수정",
			description = "보낸 필드만 바꾼다(하나 이상 필요). 상품은 삭제하지 않는다.")
	@PatchMapping("/{productId}")
	public CommonResponse<AdminProductResponse> updateAdminProduct(
			@Positive(message = "1 이상이어야 합니다.") @PathVariable Long productId,
			@Valid @RequestBody UpdateAdminProductRequest request) {
		AdminProductView view = adminProductService.updateProduct(
				productId, request.name(), request.isActive(), request.isLowCost());
		return CommonResponse.success(AdminProductResponse.from(view));
	}

	/** 월 구독요금 이력을 맨 뒤에 추가한다 */
	@Operation(operationId = "addAdminProductPrice", summary = "월 구독요금 이력 추가",
			description = "적용 시작일은 가장 최근 요금 이력의 시작일보다 늦어야 한다. 응답은 바뀐 상품 전체다.")
	@PostMapping("/{productId}/prices")
	@ResponseStatus(HttpStatus.CREATED)
	public CommonResponse<AdminProductResponse> addAdminProductPrice(
			@Positive(message = "1 이상이어야 합니다.") @PathVariable Long productId,
			@Valid @RequestBody AdminProductPriceRequest request) {
		AdminProductView view = adminProductService.addPrice(productId, request.monthlyPrice(), request.effectiveFrom());
		return CommonResponse.success(AdminProductResponse.from(view));
	}

	/** 마지막 월 구독요금 이력을 고친다 */
	@Operation(operationId = "updateAdminProductPrice", summary = "마지막 월 구독요금 이력 수정",
			description = "가장 최근 요금 이력만 고칠 수 있다. 금액을 고친 것은 확인한 것으로 보아 마지막 확인일도 바뀐다.")
	@PatchMapping("/{productId}/prices/{priceId}")
	public CommonResponse<AdminProductResponse> updateAdminProductPrice(
			@Positive(message = "1 이상이어야 합니다.") @PathVariable Long productId,
			@Positive(message = "1 이상이어야 합니다.") @PathVariable Long priceId,
			@Valid @RequestBody AdminProductPriceRequest request) {
		AdminProductView view = adminProductService.updatePrice(
				productId, priceId, request.monthlyPrice(), request.effectiveFrom());
		return CommonResponse.success(AdminProductResponse.from(view));
	}

	/** 마지막 월 구독요금 이력을 삭제한다(본문 없이 204) */
	@Operation(operationId = "deleteAdminProductPrice", summary = "마지막 월 구독요금 이력 삭제",
			description = "가장 최근 요금 이력만 지울 수 있고, 요금 이력이 하나뿐이면 지울 수 없다.")
	@DeleteMapping("/{productId}/prices/{priceId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteAdminProductPrice(
			@Positive(message = "1 이상이어야 합니다.") @PathVariable Long productId,
			@Positive(message = "1 이상이어야 합니다.") @PathVariable Long priceId) {
		adminProductService.deletePrice(productId, priceId);
	}

	/** [요금 확인함]: 금액은 그대로 두고 마지막 확인일만 현재 시각으로 바꾼다 */
	@Operation(operationId = "verifyAdminProductPrice", summary = "월 구독요금 확인함",
			description = "현재 적용 중인 요금만 확인할 수 있다. 요청 본문은 없다.")
	@PostMapping("/{productId}/prices/{priceId}/verify")
	public CommonResponse<AdminProductResponse> verifyAdminProductPrice(
			@Positive(message = "1 이상이어야 합니다.") @PathVariable Long productId,
			@Positive(message = "1 이상이어야 합니다.") @PathVariable Long priceId) {
		AdminProductView view = adminProductService.verifyPrice(productId, priceId);
		return CommonResponse.success(AdminProductResponse.from(view));
	}
}
