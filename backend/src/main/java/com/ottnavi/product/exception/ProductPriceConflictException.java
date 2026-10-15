package com.ottnavi.product.exception;

import com.ottnavi.global.error.BusinessException;
import com.ottnavi.global.error.ErrorCode;

/**
 * 월 구독요금 이력 규칙을 어겼을 때 던진다. 409이며 오류 코드는 PRICE_* 중 하나다.
 * 코드마다 클래스를 나누면 같은 모양이 6개가 되므로 하나로 두고 코드를 생성자로 받는다
 * (규칙의 "생성자 두 개"에서 벗어나는 이유는 FieldValidationException과 같다).
 */
public class ProductPriceConflictException extends BusinessException {

	private final ErrorCode errorCode;

	public ProductPriceConflictException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	@Override
	public ErrorCode getErrorCode() {
		return errorCode;
	}
}
