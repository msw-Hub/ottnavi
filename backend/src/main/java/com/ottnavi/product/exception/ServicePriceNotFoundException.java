package com.ottnavi.product.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/** 내 월 요금을 고치거나 지우려는 상품이 없거나 판매 종료일 때 던진다. 404 SERVICE_PRICE_NOT_FOUND로 응답한다 */
public class ServicePriceNotFoundException extends BusinessException {

	public ServicePriceNotFoundException(String message) {
		super(message);
	}

	@Override
	public ErrorCode getErrorCode() {
		return ErrorCode.SERVICE_PRICE_NOT_FOUND;
	}
}
