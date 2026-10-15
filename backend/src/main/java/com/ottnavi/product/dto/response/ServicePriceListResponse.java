package com.ottnavi.product.dto.response;

import com.ottnavi.product.service.model.MyServicePriceView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * 판매 중인 상품이 있는 서비스의 요금 목록(계약 ServicePriceList, 서비스 ID 순).
 *
 * @param services 서비스별 요금 이력
 */
public record ServicePriceListResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ServicePriceResponse> services) {

	/** 서비스가 돌려준 값 묶음 목록을 응답으로 바꾼다 */
	public static ServicePriceListResponse from(List<MyServicePriceView> views) {
		List<ServicePriceResponse> services = new ArrayList<>();
		for (MyServicePriceView view : views) {
			services.add(ServicePriceResponse.from(view));
		}
		return new ServicePriceListResponse(services);
	}
}
